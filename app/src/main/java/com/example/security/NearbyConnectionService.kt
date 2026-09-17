package com.example.security

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class NearbyConnectionStatus {
    DISCONNECTED,
    ADVERTISING,
    DISCOVERING,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class P2PDevice(
    val endpointId: String,
    val endpointName: String,
    val authCode: String = "" // Handshake verification code
)

/**
 * Production-grade secure P2P connection service using Google Nearby Connections API.
 * Uses AES-GCM AEAD encryption for all message payloads with zero plaintext fallback.
 * Eliminates auto-blind acceptance: provides strict verification dialog callbacks with security handshake digits.
 */
class NearbyConnectionService(private val context: Context) {

    companion object {
        private const val TAG = "NearbyConnectionService"
        private val STRATEGY = Strategy.P2P_CLUSTER // Ad-hoc cluster topology
    }

    private val connectionsClient = Nearby.getConnectionsClient(context)

    private val _status = MutableStateFlow(NearbyConnectionStatus.DISCONNECTED)
    val status: StateFlow<NearbyConnectionStatus> = _status.asStateFlow()

    private val _statusDetail = MutableStateFlow("P2P Pasif")
    val statusDetail: StateFlow<String> = _statusDetail.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<P2PDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<P2PDevice>> = _discoveredDevices.asStateFlow()

    private val _connectedDevices = MutableStateFlow<List<P2PDevice>>(emptyList())
    val connectedDevices: StateFlow<List<P2PDevice>> = _connectedDevices.asStateFlow()

    // Holds pending connection requests to be accepted/rejected by the user after manual digit verification
    private val _pendingRequest = MutableStateFlow<P2PDevice?>(null)
    val pendingRequest: StateFlow<P2PDevice?> = _pendingRequest.asStateFlow()

    // Callback for incoming messages
    var onMessageReceived: ((senderName: String, phoneNumber: String, message: String, timestamp: Long) -> Unit)? = null

    // E2E AEAD Passphrase
    var encryptionKey: String = "Ajan_Secure_Pass"

    /**
     * PayloadCallback handles incoming data transfers.
     */
    inner class ServicePayloadCallback : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                payload.asBytes()?.let { bytes ->
                    handleIncomingBytes(endpointId, bytes)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                Log.d(TAG, "Payload transfer completed successfully with $endpointId")
            }
        }
    }

    /**
     * ConnectionLifecycleCallback handles initiation, result, and termination of connections.
     * Eliminates "auto-accept" (automatic blind accept). Requires user verification.
     */
    inner class ServiceConnectionLifecycleCallback : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "Connection initiated with: $endpointId (${connectionInfo.endpointName})")
            _status.value = NearbyConnectionStatus.CONNECTING
            _statusDetail.value = "Güvenli el sıkışma kodu doğrulanıyor..."

            val authCode = connectionInfo.authenticationToken
            val device = P2PDevice(endpointId, connectionInfo.endpointName, authCode)
            
            // Present the pending connection request to UI (with verification digits)
            _pendingRequest.value = device
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            _pendingRequest.value = null // Clear pending
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Successfully connected to: $endpointId")
                    _status.value = NearbyConnectionStatus.CONNECTED
                    _statusDetail.value = "P2P Güvenli Bağlantı Başarılı"

                    val currentList = _connectedDevices.value.toMutableList()
                    if (currentList.none { it.endpointId == endpointId }) {
                        val name = _discoveredDevices.value.find { it.endpointId == endpointId }?.endpointName ?: "Gizli Ajan"
                        currentList.add(P2PDevice(endpointId, name))
                        _connectedDevices.value = currentList
                    }
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.d(TAG, "Connection was rejected by $endpointId")
                    _status.value = NearbyConnectionStatus.DISCONNECTED
                    _statusDetail.value = "Bağlantı reddedildi"
                }
                else -> {
                    Log.e(TAG, "Connection failed: ${result.status.statusCode}")
                    _status.value = NearbyConnectionStatus.ERROR
                    _statusDetail.value = "Bağlantı Hatası (Kod: ${result.status.statusCode})"
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from: $endpointId")
            val currentList = _connectedDevices.value.toMutableList()
            currentList.removeAll { it.endpointId == endpointId }
            _connectedDevices.value = currentList

            if (currentList.isEmpty()) {
                _status.value = NearbyConnectionStatus.DISCONNECTED
                _statusDetail.value = "P2P Bağlantısı Kesildi"
            } else {
                _statusDetail.value = "Bağlı Cihaz: ${currentList.size}"
            }
        }
    }

    private val connectionLifecycleCallback = ServiceConnectionLifecycleCallback()

    /**
     * EndpointDiscoveryCallback handles discovering other nearby devices.
     */
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Endpoint found: $endpointId (${info.endpointName})")
            val currentList = _discoveredDevices.value.toMutableList()
            if (currentList.none { it.endpointId == endpointId }) {
                currentList.add(P2PDevice(endpointId, info.endpointName))
                _discoveredDevices.value = currentList
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
            val currentList = _discoveredDevices.value.toMutableList()
            currentList.removeAll { it.endpointId == endpointId }
            _discoveredDevices.value = currentList
        }
    }

    /**
     * Explicit User Acceptance of the Secure Handshake connection request.
     */
    fun acceptConnection(endpointId: String) {
        connectionsClient.acceptConnection(endpointId, ServicePayloadCallback())
            .addOnSuccessListener {
                Log.d(TAG, "Successfully accepted connection request for $endpointId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to accept connection", e)
                _status.value = NearbyConnectionStatus.ERROR
                _statusDetail.value = "Kabul işlemi başarısız: ${e.localizedMessage}"
            }
    }

    /**
     * Explicit User Rejection of the connection request.
     */
    fun rejectConnection(endpointId: String) {
        connectionsClient.rejectConnection(endpointId)
            .addOnSuccessListener {
                Log.d(TAG, "Successfully rejected connection for $endpointId")
                _pendingRequest.value = null
                _status.value = NearbyConnectionStatus.DISCONNECTED
                _statusDetail.value = "Bağlantı reddedildi"
            }
    }

    /**
     * Starts advertising.
     */
    fun startAdvertising(localNickname: String, serviceId: String) {
        stopAll()
        _status.value = NearbyConnectionStatus.ADVERTISING
        _statusDetail.value = "Yayınlanıyor, bağlantı bekleniyor..."

        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(
            localNickname,
            serviceId,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Advertising started for $serviceId")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed advertising", e)
            _status.value = NearbyConnectionStatus.ERROR
            _statusDetail.value = "Yayınlanamadı: ${e.localizedMessage}"
        }
    }

    /**
     * Starts discovery.
     */
    fun startDiscovery(serviceId: String) {
        stopAll()
        _status.value = NearbyConnectionStatus.DISCOVERING
        _statusDetail.value = "Ajanlar taranıyor..."
        _discoveredDevices.value = emptyList()

        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(
            serviceId,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Discovery started for $serviceId")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed discovery", e)
            _status.value = NearbyConnectionStatus.ERROR
            _statusDetail.value = "Tarama başlatılamadı"
        }
    }

    fun connectToDevice(endpointId: String, localNickname: String) {
        _status.value = NearbyConnectionStatus.CONNECTING
        _statusDetail.value = "Bağlantı isteği gönderiliyor..."

        connectionsClient.requestConnection(
            localNickname,
            endpointId,
            connectionLifecycleCallback
        ).addOnSuccessListener {
            Log.d(TAG, "Connection request dispatched to $endpointId")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed requesting connection", e)
            _status.value = NearbyConnectionStatus.ERROR
            _statusDetail.value = "İstek başarısız oldu"
        }
    }

    /**
     * Encrypts and transmits the chat message over Nearby Connections using AES-GCM (AEAD).
     * STRICTLY FORBIDS PLAINTEXT FALLBACK.
     */
    fun sendChatMessage(senderName: String, phoneNumber: String, messageText: String) {
        val activeIds = _connectedDevices.value.map { it.endpointId }
        if (activeIds.isEmpty()) return

        try {
            // 1. Derive key securely from encryption key phrase
            val secretKey = AEADEngine.deriveKey(encryptionKey)
            
            // 2. Strong AEAD Encryption of message content
            val encryptedMessage = AEADEngine.encrypt(messageText, secretKey)

            // 3. Serialize metadata and ciphertext
            val json = JSONObject().apply {
                put("type", "CHAT")
                put("sender", senderName)
                put("phone", phoneNumber)
                put("payload", encryptedMessage) // AES-GCM Encrypted payload
                put("timestamp", System.currentTimeMillis())
            }

            val payloadBytes = json.toString().toByteArray(Charsets.UTF_8)
            val payload = Payload.fromBytes(payloadBytes)

            connectionsClient.sendPayload(activeIds, payload)
                .addOnSuccessListener {
                    Log.d(TAG, "Dispatched AEAD encrypted payload to $activeIds")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Payload dispatch failed", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Encryption/packaging error: ${e.message}")
        }
    }

    /**
     * Decrypts GCM-encrypted incoming payloads with strict tag verification.
     */
    private fun handleIncomingBytes(endpointId: String, data: ByteArray) {
        try {
            val rawString = String(data, Charsets.UTF_8)
            val json = JSONObject(rawString)
            val type = json.optString("type")

            if (type == "CHAT") {
                val sender = json.optString("sender", "Bilinmeyen Ajan")
                val phone = json.optString("phone", "+90 000 000 0000")
                val encryptedPayload = json.optString("payload")
                val time = json.optLong("timestamp", System.currentTimeMillis())

                // 1. Derive key
                val secretKey = AEADEngine.deriveKey(encryptionKey)

                // 2. Attempt Decrypt. Plaintext fallback is forbidden!
                val decryptedText = try {
                    AEADEngine.decrypt(encryptedPayload, secretKey)
                } catch (e: Exception) {
                    Log.e(TAG, "Decryption failure: ${e.message}")
                    "[Şifre Çözülemedi - Anahtar Uyuşmazlığı]"
                }

                onMessageReceived?.invoke(sender, phone, decryptedText, time)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed parsing P2P payload: ${e.message}")
        }
    }

    fun stopAll() {
        Log.d(TAG, "Resetting service")
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        _status.value = NearbyConnectionStatus.DISCONNECTED
        _statusDetail.value = "P2P Pasif"
        _discoveredDevices.value = emptyList()
        _connectedDevices.value = emptyList()
        _pendingRequest.value = null
    }
}
