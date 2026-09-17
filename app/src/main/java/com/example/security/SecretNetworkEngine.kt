package com.example.security

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.text.format.Formatter
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class NetworkMode {
  CLOUD_MQTT,
  LOCAL_P2P
}

enum class ConnectionStatus {
  DISCONNECTED,
  CONNECTING,
  CONNECTED,
  ERROR
}

data class ChannelConfig(
  val channelId: String = "LANU-777",
  val encryptionKey: String = "admin2011",
  val networkMode: NetworkMode = NetworkMode.CLOUD_MQTT,
  val localPort: Int = 8999,
  val targetHostIp: String = "",
  val isLocalHost: Boolean = false
)

data class IncomingCallSignal(
  val callerName: String,
  val callerPhone: String,
  val isVideo: Boolean,
  val timestamp: Long
)

class SecretNetworkEngine(
  private val context: Context,
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
  companion object {
    private const val TAG = "SecretNetworkEngine"
    private const val DEFAULT_BROKER = "broker.hivemq.com"
    private const val FALLBACK_BROKER = "broker.emqx.io"
    private const val BROKER_PORT = 1883
  }

  private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
  val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

  private val _statusDetail = MutableStateFlow("Çevrimdışı")
  val statusDetail: StateFlow<String> = _statusDetail.asStateFlow()

  private val _incomingCall = MutableStateFlow<IncomingCallSignal?>(null)
  val incomingCall: StateFlow<IncomingCallSignal?> = _incomingCall.asStateFlow()

  private val _callAnswered = MutableStateFlow<Boolean?>(null)
  val callAnswered: StateFlow<Boolean?> = _callAnswered.asStateFlow()

  private val _callEnded = MutableStateFlow(false)
  val callEnded: StateFlow<Boolean> = _callEnded.asStateFlow()

  private var activeConfig = ChannelConfig()
  private var clientSocket: Socket? = null
  private var serverSocket: ServerSocket? = null
  private var isRunning = false
  private var connectionJob: Job? = null
  private var pingJob: Job? = null

  // Callback listener for incoming messages
  var onMessageReceivedListener: ((sender: String, phone: String, text: String, timestamp: Long) -> Unit)? = null

  val clientId: String = "LANU_" + UUID.randomUUID().toString().take(8)

  /**
   * Derives a 256-bit AES key from the passphrase using SHA-256
   */
  private fun getSecretKey(passphrase: String): SecretKeySpec {
    val digest = MessageDigest.getInstance("SHA-256")
    val keyBytes = digest.digest(passphrase.toByteArray(Charsets.UTF_8))
    return SecretKeySpec(keyBytes, "AES")
  }

  /**
   * End-to-End Encryption: Encrypts text using AES/CBC/PKCS5Padding with 16-byte random IV
   */
  fun encrypt(plainText: String, passphrase: String): String {
    return try {
      val secretKey = getSecretKey(passphrase)
      val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
      val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
      val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

      val combined = ByteArray(iv.size + encryptedBytes.size)
      System.arraycopy(iv, 0, combined, 0, iv.size)
      System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

      base64Encode(combined)
    } catch (e: Exception) {
      Log.e(TAG, "Encryption error: ${e.message}")
      plainText
    }
  }

  /**
   * End-to-End Encryption: Decrypts Base64 ciphertext using AES/CBC/PKCS5Padding
   */
  fun decrypt(cipherText: String, passphrase: String): String {
    return try {
      val combined = base64Decode(cipherText)
      if (combined.size < 17) return "[Bozuk veya Şifresiz Mesaj]"

      val iv = ByteArray(16)
      val encryptedBytes = ByteArray(combined.size - 16)
      System.arraycopy(combined, 0, iv, 0, 16)
      System.arraycopy(combined, 16, encryptedBytes, 0, encryptedBytes.size)

      val secretKey = getSecretKey(passphrase)
      val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
      cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
      val decryptedBytes = cipher.doFinal(encryptedBytes)
      String(decryptedBytes, Charsets.UTF_8)
    } catch (e: Exception) {
      Log.w(TAG, "Decryption failed (mismatched key or corrupted): ${e.message}")
      "[Şifre Çözülemedi - Anahtar Uyuşmazlığı]"
    }
  }

  private fun base64Encode(bytes: ByteArray): String {
    return try {
      android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    } catch (_: Throwable) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        java.util.Base64.getEncoder().encodeToString(bytes)
      } else {
        ""
      }
    }
  }

  private fun base64Decode(str: String): ByteArray {
    return try {
      android.util.Base64.decode(str, android.util.Base64.NO_WRAP)
    } catch (_: Throwable) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        java.util.Base64.getDecoder().decode(str)
      } else {
        ByteArray(0)
      }
    }
  }

  /**
   * Connects to the real-time channel based on current configuration
   */
  fun connect(config: ChannelConfig) {
    disconnect()
    activeConfig = config
    isRunning = true

    connectionJob = scope.launch {
      if (config.networkMode == NetworkMode.CLOUD_MQTT) {
        connectMqttRelay(config)
      } else {
        connectLocalP2P(config)
      }
    }
  }

  /**
   * Cloud MQTT Relay transport - Connects to HiveMQ / EMQX public brokers
   */
  private suspend fun CoroutineScope.connectMqttRelay(config: ChannelConfig) {
    _connectionStatus.value = ConnectionStatus.CONNECTING
    _statusDetail.value = "Bulut Canlı Hatta Bağlanıyor (${config.channelId})..."

    var backoff = 2000L
    while (isRunning && isActive) {
      try {
        val broker = if (backoff > 5000L) FALLBACK_BROKER else DEFAULT_BROKER
        val socket = Socket()
        socket.connect(InetSocketAddress(broker, BROKER_PORT), 10000)
        clientSocket = socket

        val out = socket.getOutputStream()
        val inp = socket.getInputStream()

        // 1. Send MQTT CONNECT
        sendMqttConnect(out, clientId)

        // 2. Read CONNACK
        val connAckHeader = inp.read()
        if (connAckHeader != 0x20) throw Exception("Invalid CONNACK header: $connAckHeader")
        val connAckLen = inp.read()
        val connAckFlags = inp.read()
        val connAckCode = inp.read()
        if (connAckCode != 0) throw Exception("MQTT connection refused with code: $connAckCode")

        // 3. Send MQTT SUBSCRIBE to topic
        val topic = "lanu/vault/${sanitizeTopic(config.channelId)}"
        sendMqttSubscribe(out, topic)

        // 4. Read SUBACK
        val subAckHeader = inp.read()
        if (subAckHeader != 0x90) throw Exception("Invalid SUBACK header: $subAckHeader")
        val subAckLen = inp.read()
        for (i in 0 until subAckLen) {
          inp.read()
        }

        _connectionStatus.value = ConnectionStatus.CONNECTED
        _statusDetail.value = "🟢 Canlı Hat Aktif: ${config.channelId} (Bulut)"
        backoff = 2000L

        // Start Keep-Alive Ping every 25 seconds
        startKeepAlive(out)

        // Read incoming packets loop
        while (isRunning && isActive && !socket.isClosed) {
          val firstByte = inp.read()
          if (firstByte == -1) break

          val packetType = (firstByte and 0xF0) ushr 4
          val remainingLength = readRemainingLength(inp)
          val payloadBuffer = ByteArray(remainingLength)
          var readTotal = 0
          while (readTotal < remainingLength) {
            val count = inp.read(payloadBuffer, readTotal, remainingLength - readTotal)
            if (count == -1) break
            readTotal += count
          }

          if (packetType == 3) {
            // PUBLISH packet (QoS 0)
            handleIncomingPublish(payloadBuffer, config.encryptionKey)
          } else if (packetType == 13) {
            // PINGRESP
            Log.d(TAG, "PINGRESP received")
          }
        }
      } catch (e: Exception) {
        Log.w(TAG, "MQTT connection error: ${e.message}")
        if (isRunning) {
          _connectionStatus.value = ConnectionStatus.ERROR
          _statusDetail.value = "Bağlantı koptu, yeniden deneniyor..."
          delay(backoff)
          backoff = (backoff * 2).coerceAtMost(15000L)
        }
      } finally {
        pingJob?.cancel()
        clientSocket?.close()
        clientSocket = null
      }
    }
  }

  /**
   * Local P2P Wi-Fi / Hotspot transport - Direct TCP communication between 2 devices
   */
  private suspend fun CoroutineScope.connectLocalP2P(config: ChannelConfig) {
    _connectionStatus.value = ConnectionStatus.CONNECTING
    if (config.isLocalHost) {
      val ip = getLocalIpAddress()
      _statusDetail.value = "Sunucu Bekleniyor (IP: $ip:${config.localPort})..."
      try {
        val server = ServerSocket(config.localPort)
        serverSocket = server
        _connectionStatus.value = ConnectionStatus.CONNECTING

        val socket = server.accept()
        clientSocket = socket
        _connectionStatus.value = ConnectionStatus.CONNECTED
        _statusDetail.value = "🟢 Eşleşti: ${socket.inetAddress.hostAddress}"

        val inp = socket.getInputStream()
        while (isRunning && isActive && !socket.isClosed) {
          val line = readLineFromStream(inp) ?: break
          handlePayloadJson(line, config.encryptionKey)
        }
      } catch (e: Exception) {
        _connectionStatus.value = ConnectionStatus.ERROR
        _statusDetail.value = "Yerel sunucu hatası: ${e.message}"
      } finally {
        serverSocket?.close()
        serverSocket = null
      }
    } else {
      _statusDetail.value = "Hedefe Bağlanılıyor (${config.targetHostIp}:${config.localPort})..."
      try {
        val socket = Socket()
        socket.connect(InetSocketAddress(config.targetHostIp, config.localPort), 10000)
        clientSocket = socket
        _connectionStatus.value = ConnectionStatus.CONNECTED
        _statusDetail.value = "🟢 Bağlandı: ${config.targetHostIp}"

        val inp = socket.getInputStream()
        while (isRunning && isActive && !socket.isClosed) {
          val line = readLineFromStream(inp) ?: break
          handlePayloadJson(line, config.encryptionKey)
        }
      } catch (e: Exception) {
        _connectionStatus.value = ConnectionStatus.ERROR
        _statusDetail.value = "Hedefe ulaşılamadı: ${e.message}"
      }
    }
  }

  private fun startKeepAlive(out: OutputStream) {
    pingJob?.cancel()
    pingJob = scope.launch {
      while (isRunning && isActive) {
        delay(25000)
        try {
          synchronized(out) {
            out.write(byteArrayOf(0xC0.toByte(), 0x00))
            out.flush()
          }
        } catch (_: Exception) {
          break
        }
      }
    }
  }

  /**
   * Broadcasts an encrypted chat message over the live channel
   */
  fun sendChatMessage(
    senderName: String,
    phoneNumber: String,
    messageText: String
  ): Boolean {
    val encryptedText = encrypt(messageText, activeConfig.encryptionKey)
    val json = JSONObject().apply {
      put("type", "CHAT")
      put("senderId", clientId)
      put("sender", senderName)
      put("phone", phoneNumber)
      put("payload", encryptedText)
      put("time", System.currentTimeMillis())
    }
    return publishRaw(json.toString())
  }

  /**
   * Sends a call signaling request (Offer, Answer, Hangup)
   */
  fun sendCallSignal(type: String, isVideo: Boolean = false, callerName: String = "", callerPhone: String = "", accepted: Boolean = true): Boolean {
    val json = JSONObject().apply {
      put("type", type)
      put("senderId", clientId)
      put("callerName", callerName)
      put("callerPhone", callerPhone)
      put("isVideo", isVideo)
      put("accepted", accepted)
      put("time", System.currentTimeMillis())
    }
    return publishRaw(json.toString())
  }

  fun clearIncomingCall() {
    _incomingCall.value = null
  }

  fun resetCallSignals() {
    _incomingCall.value = null
    _callAnswered.value = null
    _callEnded.value = false
  }

  private fun publishRaw(message: String): Boolean {
    return try {
      val socket = clientSocket ?: return false
      val out = socket.getOutputStream() ?: return false

      if (activeConfig.networkMode == NetworkMode.CLOUD_MQTT) {
        val topic = "lanu/vault/${sanitizeTopic(activeConfig.channelId)}"
        sendMqttPublish(out, topic, message)
      } else {
        synchronized(out) {
          out.write((message + "\n").toByteArray(Charsets.UTF_8))
          out.flush()
        }
      }
      true
    } catch (e: Exception) {
      Log.e(TAG, "Send error: ${e.message}")
      false
    }
  }

  private fun handleIncomingPublish(buffer: ByteArray, encryptionKey: String) {
    try {
      if (buffer.size < 2) return
      val topicLen = ((buffer[0].toInt() and 0xFF) shl 8) or (buffer[1].toInt() and 0xFF)
      val payloadOffset = 2 + topicLen
      if (payloadOffset > buffer.size) return

      val jsonStr = String(buffer, payloadOffset, buffer.size - payloadOffset, Charsets.UTF_8)
      handlePayloadJson(jsonStr, encryptionKey)
    } catch (e: Exception) {
      Log.e(TAG, "Error handling MQTT packet: ${e.message}")
    }
  }

  fun handlePayloadJson(jsonStr: String, encryptionKey: String) {
    try {
      val json = JSONObject(jsonStr)
      val senderId = json.optString("senderId")
      if (senderId == clientId) {
        // Ignore loopback message from self
        return
      }

      val type = json.optString("type")
      when (type) {
        "CHAT" -> {
          val sender = json.optString("sender", "Gizli Kişi")
          val phone = json.optString("phone", "+90 000 000 0000")
          val encryptedPayload = json.optString("payload")
          val time = json.optLong("time", System.currentTimeMillis())

          val decryptedText = decrypt(encryptedPayload, encryptionKey)
          scope.launch(Dispatchers.Main) {
            onMessageReceivedListener?.invoke(sender, phone, decryptedText, time)
          }
        }
        "CALL_OFFER" -> {
          val callerName = json.optString("callerName", "Gizli Bağlantı")
          val callerPhone = json.optString("callerPhone", "+90 000 000 0000")
          val isVideo = json.optBoolean("isVideo", false)
          val time = json.optLong("time", System.currentTimeMillis())

          _incomingCall.value = IncomingCallSignal(callerName, callerPhone, isVideo, time)
        }
        "CALL_ANSWER" -> {
          val accepted = json.optBoolean("accepted", true)
          _callAnswered.value = accepted
        }
        "CALL_HANGUP" -> {
          _incomingCall.value = null
          _callEnded.value = true
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error parsing json payload: ${e.message}")
    }
  }

  fun disconnect() {
    isRunning = false
    connectionJob?.cancel()
    pingJob?.cancel()
    try {
      clientSocket?.close()
      serverSocket?.close()
    } catch (_: Exception) {}
    clientSocket = null
    serverSocket = null
    _connectionStatus.value = ConnectionStatus.DISCONNECTED
    _statusDetail.value = "Çevrimdışı"
  }

  // --- MQTT Protocol Helpers ---

  private fun sendMqttConnect(out: OutputStream, clientId: String) {
    val cidBytes = clientId.toByteArray(Charsets.UTF_8)
    val varHeader = byteArrayOf(
      0x00, 0x04, 'M'.code.toByte(), 'Q'.code.toByte(), 'T'.code.toByte(), 'T'.code.toByte(),
      0x04, // Level 3.1.1
      0x02, // Clean Session
      0x00, 0x3C // Keepalive 60s
    )
    val payload = ByteArray(2 + cidBytes.size)
    payload[0] = ((cidBytes.size shr 8) and 0xFF).toByte()
    payload[1] = (cidBytes.size and 0xFF).toByte()
    System.arraycopy(cidBytes, 0, payload, 2, cidBytes.size)

    val body = varHeader + payload
    val header = byteArrayOf(0x10.toByte()) + encodeRemainingLength(body.size)

    synchronized(out) {
      out.write(header + body)
      out.flush()
    }
  }

  private fun sendMqttSubscribe(out: OutputStream, topic: String) {
    val topicBytes = topic.toByteArray(Charsets.UTF_8)
    val body = ByteArrayOutputStream()
    // Packet ID = 1
    body.write(0x00)
    body.write(0x01)
    // Topic
    body.write(((topicBytes.size shr 8) and 0xFF))
    body.write((topicBytes.size and 0xFF))
    body.write(topicBytes)
    // QoS = 0
    body.write(0x00)

    val bodyBytes = body.toByteArray()
    val header = byteArrayOf(0x82.toByte()) + encodeRemainingLength(bodyBytes.size)

    synchronized(out) {
      out.write(header + bodyBytes)
      out.flush()
    }
  }

  private fun sendMqttPublish(out: OutputStream, topic: String, message: String) {
    val topicBytes = topic.toByteArray(Charsets.UTF_8)
    val msgBytes = message.toByteArray(Charsets.UTF_8)

    val body = ByteArrayOutputStream()
    body.write(((topicBytes.size shr 8) and 0xFF))
    body.write((topicBytes.size and 0xFF))
    body.write(topicBytes)
    body.write(msgBytes)

    val bodyBytes = body.toByteArray()
    val header = byteArrayOf(0x30.toByte()) + encodeRemainingLength(bodyBytes.size)

    synchronized(out) {
      out.write(header + bodyBytes)
      out.flush()
    }
  }

  private fun encodeRemainingLength(length: Int): ByteArray {
    var num = length
    val bytes = mutableListOf<Byte>()
    do {
      var digit = (num % 128).toByte()
      num /= 128
      if (num > 0) {
        digit = (digit.toInt() or 0x80).toByte()
      }
      bytes.add(digit)
    } while (num > 0)
    return bytes.toByteArray()
  }

  private fun readRemainingLength(input: InputStream): Int {
    var multiplier = 1
    var value = 0
    var digit: Int
    do {
      digit = input.read()
      if (digit == -1) throw Exception("End of stream")
      value += (digit and 127) * multiplier
      multiplier *= 128
    } while ((digit and 128) != 0)
    return value
  }

  private fun readLineFromStream(inp: InputStream): String? {
    val baos = ByteArrayOutputStream()
    var b: Int
    while (true) {
      b = inp.read()
      if (b == -1) {
        if (baos.size() == 0) return null
        break
      }
      if (b == '\n'.code) break
      baos.write(b)
    }
    return baos.toString("UTF-8")
  }

  private fun sanitizeTopic(channelId: String): String {
    return channelId.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_").lowercase()
  }

  fun getLocalIpAddress(): String {
    return try {
      val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
      @Suppress("DEPRECATION")
      val ip = wifiManager?.connectionInfo?.ipAddress ?: 0
      if (ip != 0) {
        @Suppress("DEPRECATION")
        Formatter.formatIpAddress(ip)
      } else {
        "127.0.0.1"
      }
    } catch (_: Exception) {
      "127.0.0.1"
    }
  }
}
