package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.security.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CommunicationUiState(
    val channelConfig: ChannelConfig = ChannelConfig(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val statusDetail: String = "Çevrimdışı",
    val messages: List<ChatMessage> = emptyList(),
    val messageStates: Map<String, MessageState> = emptyMap(),
    
    // Call signaling state
    val isCallActive: Boolean = false,
    val callType: CallType = CallType.None,
    val incomingCall: IncomingCallSignal? = null,
    
    // P2P states
    val p2pStatus: NearbyConnectionStatus = NearbyConnectionStatus.DISCONNECTED,
    val p2pStatusDetail: String = "P2P Pasif",
    val discoveredDevices: List<P2PDevice> = emptyList(),
    val connectedDevices: List<P2PDevice> = emptyList(),
    val pendingP2PRequest: P2PDevice? = null,
    
    // Profiles
    val secretNickname: String = "Ajan_Secure",
    val secretPhoneNumber: String = "+90 555 000 1234"
)

enum class CallType { None, Audio, Video }

class CommunicationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CommunicationRepository(application, viewModelScope)

    private val _uiState = MutableStateFlow(CommunicationUiState())
    val uiState: StateFlow<CommunicationUiState> = _uiState.asStateFlow()

    init {
        // Generate dynamic secure profile tags
        val randomId = (1000..9999).random()
        _uiState.update {
            it.copy(
                secretNickname = "Ajan_$randomId",
                secretPhoneNumber = "+90 555 000 $randomId"
            )
        }

        // Configure default channel initial connection
        repository.configureChannel(_uiState.value.channelConfig)

        // Bind flows from Repository to UI State
        viewModelScope.launch {
            repository.connectionStatus.collect { status ->
                _uiState.update { it.copy(connectionStatus = status) }
            }
        }

        viewModelScope.launch {
            repository.statusDetail.collect { detail ->
                _uiState.update { it.copy(statusDetail = detail) }
            }
        }

        viewModelScope.launch {
            repository.messagesFlow.collect { msgs ->
                _uiState.update { it.copy(messages = msgs) }
            }
        }

        viewModelScope.launch {
            repository.messageStates.collect { states ->
                _uiState.update { it.copy(messageStates = states) }
            }
        }

        viewModelScope.launch {
            repository.incomingCall.collect { call ->
                _uiState.update { it.copy(incomingCall = call) }
            }
        }

        viewModelScope.launch {
            repository.callAnswered.collect { answered ->
                if (answered == true) {
                    _uiState.update { it.copy(isCallActive = true) }
                } else if (answered == false) {
                    _uiState.update { it.copy(isCallActive = false, callType = CallType.None) }
                }
            }
        }

        viewModelScope.launch {
            repository.callEnded.collect { ended ->
                if (ended) {
                    _uiState.update { it.copy(isCallActive = false, callType = CallType.None) }
                }
            }
        }

        // Bind Nearby Connections Service
        viewModelScope.launch {
            repository.p2pService.status.collect { status ->
                _uiState.update { it.copy(p2pStatus = status) }
            }
        }

        viewModelScope.launch {
            repository.p2pService.statusDetail.collect { detail ->
                _uiState.update { it.copy(p2pStatusDetail = detail) }
            }
        }

        viewModelScope.launch {
            repository.p2pService.discoveredDevices.collect { devices ->
                _uiState.update { it.copy(discoveredDevices = devices) }
            }
        }

        viewModelScope.launch {
            repository.p2pService.connectedDevices.collect { devices ->
                _uiState.update { it.copy(connectedDevices = devices) }
            }
        }

        viewModelScope.launch {
            repository.p2pService.pendingRequest.collect { request ->
                _uiState.update { it.copy(pendingP2PRequest = request) }
            }
        }
    }

    fun configureChannel(config: ChannelConfig) {
        _uiState.update { it.copy(channelConfig = config) }
        repository.configureChannel(config)
    }

    fun sendMessage(text: String) {
        val state = _uiState.value
        repository.sendMessage(state.secretNickname, state.secretPhoneNumber, text)
    }

    fun updateProfile(nickname: String, phone: String) {
        _uiState.update { it.copy(secretNickname = nickname, secretPhoneNumber = phone) }
    }

    // Call Signaling
    fun startCall(type: CallType) {
        val state = _uiState.value
        _uiState.update { it.copy(isCallActive = true, callType = type) }
        repository.sendCallSignal(
            type = "CALL_OFFER",
            isVideo = (type == CallType.Video),
            callerName = state.secretNickname,
            callerPhone = state.secretPhoneNumber
        )
    }

    fun endCall() {
        _uiState.update { it.copy(isCallActive = false, callType = CallType.None) }
        repository.sendCallSignal("CALL_HANGUP", false, "", "")
        repository.resetCallSignals()
    }

    fun acceptIncomingCall() {
        val call = _uiState.value.incomingCall ?: return
        val type = if (call.isVideo) CallType.Video else CallType.Audio
        _uiState.update { it.copy(isCallActive = true, callType = type, incomingCall = null) }
        repository.sendCallSignal("CALL_ANSWER", call.isVideo, "", "", accepted = true)
        repository.clearIncomingCall()
    }

    fun rejectIncomingCall() {
        val call = _uiState.value.incomingCall ?: return
        repository.sendCallSignal("CALL_ANSWER", call.isVideo, "", "", accepted = false)
        repository.clearIncomingCall()
        _uiState.update { it.copy(incomingCall = null) }
    }

    // Nearby P2P Operations
    fun startP2PAdvertising() {
        val state = _uiState.value
        repository.p2pService.startAdvertising(state.secretNickname, state.channelConfig.channelId)
    }

    fun startP2PDiscovery() {
        val state = _uiState.value
        repository.p2pService.startDiscovery(state.channelConfig.channelId)
    }

    fun connectToP2PDevice(endpointId: String) {
        val state = _uiState.value
        repository.p2pService.connectToDevice(endpointId, state.secretNickname)
    }

    fun acceptP2PConnection(endpointId: String) {
        repository.p2pService.acceptConnection(endpointId)
    }

    fun rejectP2PConnection(endpointId: String) {
        repository.p2pService.rejectConnection(endpointId)
    }

    fun stopP2P() {
        repository.p2pService.stopAll()
    }

    fun clearChatHistory() {
        repository.clearChatHistory()
    }

    override fun onCleared() {
        super.onCleared()
        repository.disconnect()
    }
}
