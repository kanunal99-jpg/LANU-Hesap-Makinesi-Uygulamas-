package com.example.security

import android.content.Context
import android.util.Log
import com.example.database.AppDatabase
import com.example.database.SecretMessageEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.util.UUID

/**
 * CommunicationRepository encapsulates message history database integration, transport switching,
 * call signaling, message state machine, and robust connection/reconnection retry pipelines.
 */
class CommunicationRepository(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    companion object {
        private const val TAG = "CommunicationRepository"
    }

    private val db = AppDatabase.getDatabase(context)
    private val messageDao = db.secretMessageDao()
    
    // Transports
    val mqttTransport = MqttTransport(scope)
    val p2pService = NearbyConnectionService(context)

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _statusDetail = MutableStateFlow("Çevrimdışı")
    val statusDetail: StateFlow<String> = _statusDetail.asStateFlow()

    // Call signaling states
    private val _incomingCall = MutableStateFlow<IncomingCallSignal?>(null)
    val incomingCall: StateFlow<IncomingCallSignal?> = _incomingCall.asStateFlow()

    private val _callAnswered = MutableStateFlow<Boolean?>(null)
    val callAnswered: StateFlow<Boolean?> = _callAnswered.asStateFlow()

    private val _callEnded = MutableStateFlow(false)
    val callEnded: StateFlow<Boolean> = _callEnded.asStateFlow()

    // Live state-driven configuration
    private var currentConfig = ChannelConfig()

    // Reactive flow of messages
    private val _messagesFlow = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messagesFlow: StateFlow<List<ChatMessage>> = _messagesFlow.asStateFlow()

    // Map to track local sending state of messages (id -> State)
    private val _messageStates = MutableStateFlow<Map<String, MessageState>>(emptyMap())
    val messageStates: StateFlow<Map<String, MessageState>> = _messageStates.asStateFlow()

    init {
        // Observe Mqtt Connection Status
        scope.launch {
            mqttTransport.connectionStatus.collect { status ->
                if (currentConfig.networkMode == NetworkMode.CLOUD_MQTT) {
                    _connectionStatus.value = status
                }
            }
        }

        scope.launch {
            mqttTransport.statusDetail.collect { detail ->
                if (currentConfig.networkMode == NetworkMode.CLOUD_MQTT) {
                    _statusDetail.value = detail
                }
            }
        }

        // Handle Incoming MQTT payloads
        mqttTransport.onDataReceived = { payload ->
            handleIncomingPayload(payload)
        }

        // Handle Incoming P2P payloads
        p2pService.onMessageReceived = { sender, phone, text, timestamp ->
            saveAndEmitMessage(sender, phone, text, timestamp, isMe = false)
        }

        // Load messages for initial config
        loadChannelMessages(currentConfig.channelId)
    }

    fun configureChannel(config: ChannelConfig) {
        // Disconnect old
        disconnect()

        currentConfig = config
        p2pService.encryptionKey = config.encryptionKey

        if (config.networkMode == NetworkMode.CLOUD_MQTT) {
            mqttTransport.connect(config)
        } else {
            _connectionStatus.value = ConnectionStatus.DISCONNECTED
            _statusDetail.value = "Yerel P2P Hazır"
        }

        loadChannelMessages(config.channelId)
    }

    fun disconnect() {
        mqttTransport.disconnect()
        p2pService.stopAll()
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        _statusDetail.value = "Bağlantı kesildi"
    }

    private fun loadChannelMessages(channelId: String) {
        scope.launch {
            val saved = messageDao.getMessagesForChannel(channelId)
            val mapped = saved.map {
                ChatMessage(
                    id = it.id.toString(),
                    senderName = it.senderName,
                    phoneNumber = it.phoneNumber,
                    message = it.message,
                    timestamp = it.timestamp,
                    isMe = it.isMe
                )
            }
            _messagesFlow.value = mapped
        }
    }

    /**
     * Sends Chat Message securely using GCM (AEAD).
     * Strictly manages message state: PENDING -> SENDING -> SENT / FAILED.
     */
    fun sendMessage(senderName: String, phone: String, text: String) {
        if (text.isBlank()) return

        val msgId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // Create temporary local message in PENDING state
        val tempMsg = ChatMessage(id = msgId, senderName = senderName, phoneNumber = phone, message = text, timestamp = now, isMe = true)
        _messagesFlow.update { it + tempMsg }
        updateMessageState(msgId, MessageState.PENDING)

        scope.launch {
            updateMessageState(msgId, MessageState.SENDING)

            val success = if (currentConfig.networkMode == NetworkMode.LOCAL_P2P) {
                p2pService.sendChatMessage(senderName, phone, text)
                true // Nearby uses fire-and-forget success
            } else {
                // Cloud MQTT secure transmit
                try {
                    val secretKey = AEADEngine.deriveKey(currentConfig.encryptionKey)
                    val encrypted = AEADEngine.encrypt(text, secretKey)
                    
                    val json = JSONObject().apply {
                        put("msgId", msgId)
                        put("type", "CHAT")
                        put("senderId", mqttTransport.clientId)
                        put("sender", senderName)
                        put("phone", phone)
                        put("payload", encrypted)
                        put("time", now)
                    }
                    mqttTransport.sendData(json.toString())
                } catch (e: Exception) {
                    Log.e(TAG, "Encryption or publish failed: ${e.message}")
                    false
                }
            }

            if (success) {
                updateMessageState(msgId, MessageState.SENT)
                // Persist securely to SQLite Room
                withContext(Dispatchers.IO) {
                    val entity = SecretMessageEntity(
                        channelId = currentConfig.channelId,
                        senderName = senderName,
                        phoneNumber = phone,
                        message = text,
                        timestamp = now,
                        isMe = true
                    )
                    messageDao.insertMessage(entity)
                }
            } else {
                updateMessageState(msgId, MessageState.FAILED)
            }
        }
    }

    /**
     * Transmits call signaling packet securely.
     */
    fun sendCallSignal(type: String, isVideo: Boolean, callerName: String, callerPhone: String, accepted: Boolean = true) {
        val now = System.currentTimeMillis()
        val json = JSONObject().apply {
            put("msgId", UUID.randomUUID().toString())
            put("type", type)
            put("senderId", mqttTransport.clientId)
            put("callerName", callerName)
            put("callerPhone", callerPhone)
            put("isVideo", isVideo)
            put("accepted", accepted)
            put("time", now)
        }

        scope.launch {
            if (currentConfig.networkMode == NetworkMode.CLOUD_MQTT) {
                mqttTransport.sendData(json.toString())
            }
        }
    }

    private fun handleIncomingPayload(payload: String) {
        try {
            val json = JSONObject(payload)
            val senderId = json.optString("senderId")
            if (senderId == mqttTransport.clientId) {
                return // Ignore loopback self
            }

            val type = json.optString("type")
            when (type) {
                "CHAT" -> {
                    val sender = json.optString("sender", "Gizli Kişi")
                    val phone = json.optString("phone", "+90 000 000 0000")
                    val encryptedPayload = json.optString("payload")
                    val time = json.optLong("time", System.currentTimeMillis())

                    // Decrypt. Plaintext fallback is forbidden!
                    val decrypted = try {
                        val secretKey = AEADEngine.deriveKey(currentConfig.encryptionKey)
                        AEADEngine.decrypt(encryptedPayload, secretKey)
                    } catch (e: Exception) {
                        Log.e(TAG, "Decryption error: ${e.message}")
                        "[Şifre Çözülemedi - Anahtar Uyuşmazlığı]"
                    }

                    saveAndEmitMessage(sender, phone, decrypted, time, isMe = false)
                }
                "CALL_OFFER" -> {
                    val name = json.optString("callerName", "Gizli Bağlantı")
                    val phone = json.optString("callerPhone", "+90 000 000 0000")
                    val isVideo = json.optBoolean("isVideo", false)
                    val time = json.optLong("time", System.currentTimeMillis())

                    _incomingCall.value = IncomingCallSignal(name, phone, isVideo, time)
                }
                "CALL_ANSWER" -> {
                    val accepted = json.optBoolean("accepted", false)
                    _callAnswered.value = accepted
                }
                "CALL_HANGUP" -> {
                    _callEnded.value = true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming payload: ${e.message}")
        }
    }

    private fun saveAndEmitMessage(sender: String, phone: String, text: String, timestamp: Long, isMe: Boolean) {
        val msgId = UUID.randomUUID().toString()
        val chatMsg = ChatMessage(id = msgId, senderName = sender, phoneNumber = phone, message = text, timestamp = timestamp, isMe = isMe)
        
        _messagesFlow.update { it + chatMsg }

        // Save to Database
        scope.launch(Dispatchers.IO) {
            val entity = SecretMessageEntity(
                channelId = currentConfig.channelId,
                senderName = sender,
                phoneNumber = phone,
                message = text,
                timestamp = timestamp,
                isMe = isMe
            )
            messageDao.insertMessage(entity)
        }
    }

    fun clearChatHistory() {
        scope.launch(Dispatchers.IO) {
            messageDao.clearMessages(currentConfig.channelId)
            _messagesFlow.value = emptyList()
        }
    }

    fun clearIncomingCall() {
        _incomingCall.value = null
    }

    fun resetCallSignals() {
        _incomingCall.value = null
        _callAnswered.value = null
        _callEnded.value = false
    }

    private fun updateMessageState(msgId: String, state: MessageState) {
        _messageStates.update { current ->
            current.toMutableMap().apply { this[msgId] = state }
        }
    }
}
