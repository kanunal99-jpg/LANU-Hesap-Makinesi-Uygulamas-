package com.example.security

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State representation for the Peer-to-Peer direct connection.
 */
enum class P2PState {
    IDLE,
    ADVERTISING,
    DISCOVERING,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Model representing a discovered nearby P2P device.
 */
data class P2PDevice(
    val endpointId: String,
    val endpointName: String
)

/**
 * Interface defining the Google Nearby Connections P2P service layer.
 */
interface SecretP2PManager {
    val p2pState: StateFlow<P2PState>
    val statusMessage: StateFlow<String>
    val discoveredDevices: StateFlow<List<P2PDevice>>
    val activeConnections: StateFlow<List<P2PDevice>>

    fun startAdvertising(localEndpointName: String, serviceId: String)
    fun startDiscovery(serviceId: String)
    fun stopAll()
    fun connectToEndpoint(endpointId: String, localEndpointName: String)
    fun sendBytes(endpointId: String, data: ByteArray)
    fun broadcastBytes(data: ByteArray)
    fun disconnectFromEndpoint(endpointId: String)
    fun registerPayloadListener(listener: (endpointId: String, data: ByteArray) -> Unit)
}

/**
 * Production-grade implementation of [SecretP2PManager] using Google Nearby Connections API.
 * This ensures direct high-speed, secure local communication without an internet connection.
 */
class NearbyConnectionsManager(
    private val context: Context
) : SecretP2PManager {

    companion object {
        private const val TAG = "NearbyConnectionsMgr"
        private val STRATEGY = Strategy.P2P_CLUSTER // Supports multi-device ad-hoc networks
    }

    private val connectionsClient = Nearby.getConnectionsClient(context)

    private val _p2pState = MutableStateFlow(P2PState.IDLE)
    override val p2pState: StateFlow<P2PState> = _p2pState.asStateFlow()

    private val _statusMessage = MutableStateFlow("Çevrimdışı (P2P Aktif Değil)")
    override val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<P2PDevice>>(emptyList())
    override val discoveredDevices: StateFlow<List<P2PDevice>> = _discoveredDevices.asStateFlow()

    private val _activeConnections = MutableStateFlow<List<P2PDevice>>(emptyList())
    override val activeConnections: StateFlow<List<P2PDevice>> = _activeConnections.asStateFlow()

    private var payloadListener: ((String, ByteArray) -> Unit)? = null

    // Callbacks for receiving payloads (data packets)
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                payload.asBytes()?.let { bytes ->
                    payloadListener?.invoke(endpointId, bytes)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Can be used to track progress of large files/messages
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                Log.d(TAG, "Payload successfully transferred to/from $endpointId")
            }
        }
    }

    // Callbacks for connections to other devices
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "Connection initiated with $endpointId (${connectionInfo.endpointName})")
            _p2pState.value = P2PState.CONNECTING
            _statusMessage.value = "Bağlantı kuruluyor: ${connectionInfo.endpointName}..."
            
            // Automatically accept the connection on both sides
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnSuccessListener {
                    Log.d(TAG, "Successfully accepted connection from $endpointId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to accept connection from $endpointId", e)
                    _p2pState.value = P2PState.ERROR
                    _statusMessage.value = "Bağlantı kabul edilemedi"
                }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d(TAG, "Connected successfully to $endpointId")
                    _p2pState.value = P2PState.CONNECTED
                    _statusMessage.value = "P2P Bağlantı Aktif"
                    
                    // Add to active connections list
                    val currentList = _activeConnections.value.toMutableList()
                    // Check if already exists
                    if (currentList.none { it.endpointId == endpointId }) {
                        // Find matching device from discovered list to keep name
                        val discovered = _discoveredDevices.value.find { it.endpointId == endpointId }
                        val name = discovered?.endpointName ?: "Bilinmeyen Cihaz"
                        currentList.add(P2PDevice(endpointId, name))
                        _activeConnections.value = currentList
                    }
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.d(TAG, "Connection rejected by $endpointId")
                    _p2pState.value = P2PState.IDLE
                    _statusMessage.value = "Bağlantı reddedildi"
                }
                else -> {
                    Log.d(TAG, "Connection failed with status code: ${result.status.statusCode}")
                    _p2pState.value = P2PState.ERROR
                    _statusMessage.value = "Bağlantı başarısız oldu"
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from endpoint: $endpointId")
            val currentList = _activeConnections.value.toMutableList()
            currentList.removeAll { it.endpointId == endpointId }
            _activeConnections.value = currentList

            if (currentList.isEmpty()) {
                _p2pState.value = P2PState.IDLE
                _statusMessage.value = "Bağlantı kesildi"
            } else {
                _statusMessage.value = "Aktif P2P Cihaz Sayısı: ${currentList.size}"
            }
        }
    }

    // Callbacks for finding other devices
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

    override fun startAdvertising(localEndpointName: String, serviceId: String) {
        stopAll()
        Log.d(TAG, "Starting advertising as $localEndpointName with service ID: $serviceId")
        _p2pState.value = P2PState.ADVERTISING
        _statusMessage.value = "Yakındaki cihazlar için aranabilir durumda..."

        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startAdvertising(
            localEndpointName,
            serviceId,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Advertising started successfully")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to start advertising", e)
            _p2pState.value = P2PState.ERROR
            _statusMessage.value = "Yayınlama başlatılamadı: ${e.localizedMessage}"
        }
    }

    override fun startDiscovery(serviceId: String) {
        stopAll()
        Log.d(TAG, "Starting discovery with service ID: $serviceId")
        _p2pState.value = P2PState.DISCOVERING
        _statusMessage.value = "Yakındaki cihazlar aranıyor..."
        _discoveredDevices.value = emptyList()

        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        connectionsClient.startDiscovery(
            serviceId,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Discovery started successfully")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to start discovery", e)
            _p2pState.value = P2PState.ERROR
            _statusMessage.value = "Cihaz tarama başlatılamadı"
        }
    }

    override fun connectToEndpoint(endpointId: String, localEndpointName: String) {
        Log.d(TAG, "Connecting to endpoint: $endpointId from $localEndpointName")
        connectionsClient.requestConnection(
            localEndpointName,
            endpointId,
            connectionLifecycleCallback
        ).addOnSuccessListener {
            Log.d(TAG, "Connection requested successfully")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to request connection", e)
            _p2pState.value = P2PState.ERROR
            _statusMessage.value = "Bağlantı isteği gönderilemedi"
        }
    }

    override fun sendBytes(endpointId: String, data: ByteArray) {
        val payload = Payload.fromBytes(data)
        connectionsClient.sendPayload(endpointId, payload)
            .addOnSuccessListener {
                Log.d(TAG, "Payload scheduled to send to $endpointId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send payload to $endpointId", e)
            }
    }

    override fun broadcastBytes(data: ByteArray) {
        val endpointIds = _activeConnections.value.map { it.endpointId }
        if (endpointIds.isNotEmpty()) {
            val payload = Payload.fromBytes(data)
            connectionsClient.sendPayload(endpointIds, payload)
                .addOnSuccessListener {
                    Log.d(TAG, "Payload broadcasted to ${endpointIds.size} endpoints")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to broadcast payload", e)
                }
        }
    }

    override fun disconnectFromEndpoint(endpointId: String) {
        Log.d(TAG, "Disconnecting from endpoint: $endpointId")
        connectionsClient.disconnectFromEndpoint(endpointId)
        val currentList = _activeConnections.value.toMutableList()
        currentList.removeAll { it.endpointId == endpointId }
        _activeConnections.value = currentList
        
        if (currentList.isEmpty()) {
            _p2pState.value = P2PState.IDLE
            _statusMessage.value = "Bağlantı sonlandırıldı"
        }
    }

    override fun stopAll() {
        Log.d(TAG, "Stopping all advertising, discovery, and connections")
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        _p2pState.value = P2PState.IDLE
        _statusMessage.value = "Çevrimdışı (P2P Aktif Değil)"
        _discoveredDevices.value = emptyList()
        _activeConnections.value = emptyList()
    }

    override fun registerPayloadListener(listener: (endpointId: String, data: ByteArray) -> Unit) {
        this.payloadListener = listener
    }
}
