package com.example.security

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class MqttTransport(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : Transport {

    companion object {
        private const val TAG = "MqttTransport"
        private const val SECURE_BROKER = "broker.hivemq.com"
        private const val FALLBACK_SECURE_BROKER = "broker.emqx.io"
        private const val SECURE_PORT = 8883 // MQTTS TLS Port
    }

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _statusDetail = MutableStateFlow("Çevrimdışı")
    override val statusDetail: StateFlow<String> = _statusDetail.asStateFlow()

    override var onDataReceived: ((data: String) -> Unit)? = null

    private var activeConfig = ChannelConfig()
    private var sslSocket: SSLSocket? = null
    private var isRunning = false
    private var connectionJob: Job? = null
    private var pingJob: Job? = null
    
    // Unique Client Identity
    val clientId: String = "LANU_" + UUID.randomUUID().toString().take(8)

    // Sliding window of processed message IDs to prevent duplicates
    private val processedMessageIds = ConcurrentHashMap.newKeySet<String>()

    override fun connect(config: ChannelConfig) {
        disconnect()
        activeConfig = config
        isRunning = true

        connectionJob = scope.launch(Dispatchers.IO) {
            connectMqttSecure(config)
        }
    }

    override fun disconnect() {
        isRunning = false
        connectionJob?.cancel()
        connectionJob = null
        pingJob?.cancel()
        pingJob = null
        try {
            sslSocket?.close()
        } catch (_: Exception) {}
        sslSocket = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _statusDetail.value = "Bağlantı kesildi"
    }

    private suspend fun CoroutineScope.connectMqttSecure(config: ChannelConfig) {
        _connectionStatus.value = ConnectionStatus.CONNECTING
        _statusDetail.value = "TLS Güvenli Hatta Bağlanılıyor..."

        var backoff = 2000L
        while (isRunning && isActive) {
            try {
                val broker = if (backoff > 5000L) FALLBACK_SECURE_BROKER else SECURE_BROKER
                _statusDetail.value = "Güvenli bağlantı kuruluyor: $broker"

                // 1. Establish TLS Socket Connection
                val socketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory
                val rawSocket = Socket()
                rawSocket.connect(InetSocketAddress(broker, SECURE_PORT), 10000)
                
                val secureSocket = socketFactory.createSocket(rawSocket, broker, SECURE_PORT, true) as SSLSocket
                secureSocket.startHandshake() // Force immediate TLS handshake
                sslSocket = secureSocket

                val out = secureSocket.getOutputStream()
                val inp = secureSocket.getInputStream()

                _connectionStatus.value = ConnectionStatus.AUTHENTICATING
                _statusDetail.value = "Cihaz kimliği doğrulanıyor..."

                // 2. Send MQTT CONNECT with client credentials (clientId)
                sendMqttConnect(out, clientId)

                // 3. Read CONNACK
                val connAckHeader = inp.read()
                if (connAckHeader != 0x20) throw Exception("Hatalı CONNACK başlığı: $connAckHeader")
                val connAckLen = inp.read()
                inp.read() // Flags
                val connAckCode = inp.read()
                if (connAckCode != 0) throw Exception("MQTT Bağlantısı reddedildi: $connAckCode")

                // 4. Send MQTT SUBSCRIBE to the unique per-conversation topic
                val topic = "lanu/secure/conv/${sanitizeTopic(config.channelId)}"
                sendMqttSubscribe(out, topic)

                // 5. Read SUBACK
                val subAckHeader = inp.read()
                if (subAckHeader != 0x90) throw Exception("Hatalı SUBACK başlığı")
                val subAckLen = inp.read()
                for (i in 0 until subAckLen) {
                    inp.read()
                }

                _connectionStatus.value = ConnectionStatus.CONNECTED
                _statusDetail.value = "🟢 TLS Güvenli Bağlantı Aktif"
                backoff = 2000L // Reset backoff

                // Start Keep-Alive Pings
                startKeepAlive(out)

                // Read incoming packet stream loop
                while (isRunning && isActive && !secureSocket.isClosed) {
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
                        // PUBLISH packet
                        handleIncomingPublish(payloadBuffer)
                    } else if (packetType == 13) {
                        // PINGRESP
                        Log.d(TAG, "PINGRESP received")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "TLS MQTT connection error: ${e.message}")
                if (isRunning) {
                    _connectionStatus.value = ConnectionStatus.RECONNECTING
                    _statusDetail.value = "Hat koptu, yeniden deneniyor..."
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(15000L)
                }
            } finally {
                pingJob?.cancel()
                try {
                    sslSocket?.close()
                } catch (_: Exception) {}
                sslSocket = null
            }
        }
    }

    override fun sendData(data: String): Boolean {
        return try {
            val socket = sslSocket ?: return false
            val out = socket.getOutputStream() ?: return false
            val topic = "lanu/secure/conv/${sanitizeTopic(activeConfig.channelId)}"
            sendMqttPublish(out, topic, data)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Görüşme verisi gönderilemedi: ${e.message}")
            false
        }
    }

    private fun startKeepAlive(out: OutputStream) {
        pingJob?.cancel()
        pingJob = scope.launch(Dispatchers.IO) {
            while (isRunning && isActive) {
                delay(20000) // Send ping every 20 seconds
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

    private fun handleIncomingPublish(buffer: ByteArray) {
        try {
            if (buffer.size < 2) return
            val topicLen = ((buffer[0].toInt() and 0xFF) shl 8) or (buffer[1].toInt() and 0xFF)
            val payloadOffset = 2 + topicLen
            if (payloadOffset > buffer.size) return

            val payloadStr = String(buffer, payloadOffset, buffer.size - payloadOffset, Charsets.UTF_8)
            
            // Duplicate Check
            val json = JSONObject(payloadStr)
            val messageId = json.optString("msgId")
            if (messageId.isNotEmpty()) {
                if (processedMessageIds.contains(messageId)) {
                    // Filter duplicate message
                    return
                }
                processedMessageIds.add(messageId)
                if (processedMessageIds.size > 200) {
                    processedMessageIds.clear() // Prevent memory leak
                }
            }

            onDataReceived?.invoke(payloadStr)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming PUBLISH payload: ${e.message}")
        }
    }

    // MQTT protocol low-level writers
    private fun sendMqttConnect(out: OutputStream, clientId: String) {
        val clientBytes = clientId.toByteArray(Charsets.UTF_8)
        val variableHeader = byteArrayOf(
            0x00, 0x04, 'M'.toByte(), 'Q'.toByte(), 'T'.toByte(), 'T'.toByte(),
            0x04, // Protocol Level 4 (MQTT 3.1.1)
            0x02, // Connect Flags (Clean Session only)
            0x00, 0x3C // Keep-Alive 60 seconds
        )

        val payload = ByteArray(2 + clientBytes.size)
        payload[0] = ((clientBytes.size ushr 8) and 0xFF).toByte()
        payload[1] = (clientBytes.size and 0xFF).toByte()
        System.arraycopy(clientBytes, 0, payload, 2, clientBytes.size)

        val remainingLength = variableHeader.size + payload.size
        synchronized(out) {
            out.write(0x10) // CONNECT Header
            writeRemainingLength(out, remainingLength)
            out.write(variableHeader)
            out.write(payload)
            out.flush()
        }
    }

    private fun sendMqttSubscribe(out: OutputStream, topic: String) {
        val topicBytes = topic.toByteArray(Charsets.UTF_8)
        val variableHeader = byteArrayOf(0x00, 0x01) // Message ID 1

        val payload = ByteArray(2 + topicBytes.size + 1)
        payload[0] = ((topicBytes.size ushr 8) and 0xFF).toByte()
        payload[1] = (topicBytes.size and 0xFF).toByte()
        System.arraycopy(topicBytes, 0, payload, 2, topicBytes.size)
        payload[payload.size - 1] = 0x00 // QoS 0

        val remainingLength = variableHeader.size + payload.size
        synchronized(out) {
            out.write(0x82) // SUBSCRIBE Header (QoS 1)
            writeRemainingLength(out, remainingLength)
            out.write(variableHeader)
            out.write(payload)
            out.flush()
        }
    }

    private fun sendMqttPublish(out: OutputStream, topic: String, message: String) {
        val topicBytes = topic.toByteArray(Charsets.UTF_8)
        val msgBytes = message.toByteArray(Charsets.UTF_8)

        val remainingLength = 2 + topicBytes.size + msgBytes.size
        val header = ByteArray(2 + topicBytes.size)
        header[0] = ((topicBytes.size ushr 8) and 0xFF).toByte()
        header[1] = (topicBytes.size and 0xFF).toByte()
        System.arraycopy(topicBytes, 0, header, 2, topicBytes.size)

        synchronized(out) {
            out.write(0x30) // PUBLISH QoS 0, No Retain
            writeRemainingLength(out, remainingLength)
            out.write(header)
            out.write(msgBytes)
            out.flush()
        }
    }

    private fun writeRemainingLength(out: OutputStream, length: Int) {
        var len = length
        do {
            var digit = len % 128
            len /= 128
            if (len > 0) {
                digit = digit or 0x80
            }
            out.write(digit)
        } while (len > 0)
    }

    private fun readRemainingLength(inp: InputStream): Int {
        var multiplier = 1
        var value = 0
        do {
            val digit = inp.read()
            if (digit == -1) throw Exception("Stream closed unexpectedly")
            value += (digit and 127) * multiplier
            multiplier *= 128
        } while ((digit and 128) != 0)
        return value
    }

    private fun sanitizeTopic(channelId: String): String {
        return channelId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
    }
}
