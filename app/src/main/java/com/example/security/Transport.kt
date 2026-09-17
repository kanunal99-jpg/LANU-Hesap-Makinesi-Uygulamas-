package com.example.security

import kotlinx.coroutines.flow.StateFlow

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

enum class MessageState {
    PENDING,
    SENDING,
    SENT,
    DELIVERED,
    FAILED
}

enum class NetworkMode {
    CLOUD_MQTT,
    LOCAL_P2P
}

data class ChannelConfig(
    val channelId: String = "LANU-SECURE-777",
    val encryptionKey: String = "Ajan_Secure_Pass", // Real E2E Secret Key
    val networkMode: NetworkMode = NetworkMode.CLOUD_MQTT,
    val localPort: Int = 8999,
    val targetHostIp: String = "",
    val isLocalHost: Boolean = false
)

data class ChatMessage(
    val id: String,
    val senderName: String,
    val phoneNumber: String,
    val message: String,
    val timestamp: Long,
    val isMe: Boolean
)

data class IncomingCallSignal(
    val callerName: String,
    val callerPhone: String,
    val isVideo: Boolean,
    val timestamp: Long
)

interface Transport {
    val connectionStatus: StateFlow<ConnectionStatus>
    val statusDetail: StateFlow<String>
    
    fun connect(config: ChannelConfig)
    fun disconnect()
    fun sendData(data: String): Boolean
    
    var onDataReceived: ((data: String) -> Unit)?
}
