package com.example.ui

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.calculator.CalculatorEngine
import com.example.database.AppDatabase
import com.example.database.SecretMessageEntity
import com.example.financial.FinancialEngine
import com.example.security.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(
  val id: String = java.util.UUID.randomUUID().toString(),
  val senderName: String,
  val phoneNumber: String,
  val message: String,
  val timestamp: Long = System.currentTimeMillis(),
  val isMe: Boolean = false
)

enum class CallType { None, Audio, Video }

data class CalculatorUiState(
  val expression: String = "",
  val result: String = "0",
  val history: List<String> = emptyList(),
  val currentScreen: Screen = Screen.Calculator,
  val isSecretUnlocked: Boolean = false,
  val secretLogs: List<String> = emptyList(),
  val customPrimaryColor: Long = 0xFF6200EE,
  val customSecondaryColor: Long = 0xFF03DAC6,
  val isDarkTheme: Boolean = false,
  val secretNickname: String = "GizliAjan",
  val secretPhoneNumber: String = "+90 555 000 0000",
  val secretMessages: List<ChatMessage> = listOf(
    ChatMessage(senderName = "Sistem", phoneNumber = "+90 000 000 0000", message = "Şifreli Güvenli Hat Hazır.", isMe = false)
  ),
  val isCallActive: Boolean = false,
  val callType: CallType = CallType.None,
  val isHapticFeedbackEnabled: Boolean = true,
  val channelConfig: ChannelConfig = ChannelConfig(),
  val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
  val statusDetail: String = "Çevrimdışı",
  val incomingCall: IncomingCallSignal? = null
)

sealed class Screen {
  object Calculator : Screen()
  object Financial : Screen()
  object ThemeCustomizer : Screen()
  object SecretArea : Screen()
}

class CalculatorViewModel(application: Application) : AndroidViewModel(application) {
  private val calculatorEngine = CalculatorEngine()
  private val financialEngine = FinancialEngine()
  private val secretManager = SecretManager(application)
  private val secretNetworkEngine = SecretNetworkEngine(application, viewModelScope)
  private val secretMessageDao = AppDatabase.getDatabase(application).secretMessageDao()

  private val _uiState = MutableStateFlow(CalculatorUiState())
  val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

  init {
    // Collect Network Status
    viewModelScope.launch {
      secretNetworkEngine.connectionStatus.collect { status ->
        _uiState.update { it.copy(connectionStatus = status) }
      }
    }

    // Collect Status Detail
    viewModelScope.launch {
      secretNetworkEngine.statusDetail.collect { detail ->
        _uiState.update { it.copy(statusDetail = detail) }
      }
    }

    // Collect Incoming Call Signals
    viewModelScope.launch {
      secretNetworkEngine.incomingCall.collect { signal ->
        _uiState.update { it.copy(incomingCall = signal) }
      }
    }

    // Collect Remote Call Answers
    viewModelScope.launch {
      secretNetworkEngine.callAnswered.collect { answered ->
        if (answered == true) {
          _uiState.update { it.copy(isCallActive = true) }
        } else if (answered == false) {
          _uiState.update { it.copy(isCallActive = false, callType = CallType.None) }
        }
      }
    }

    // Collect Remote Call Hangup
    viewModelScope.launch {
      secretNetworkEngine.callEnded.collect { ended ->
        if (ended) {
          _uiState.update { it.copy(isCallActive = false, callType = CallType.None) }
        }
      }
    }

    // Setup Incoming Chat Message Listener
    secretNetworkEngine.onMessageReceivedListener = { sender, phone, text, timestamp ->
      val chatMsg = ChatMessage(
        senderName = sender,
        phoneNumber = phone,
        message = text,
        timestamp = timestamp,
        isMe = false
      )
      _uiState.update { it.copy(secretMessages = it.secretMessages + chatMsg) }

      // Persist in Room DB
      viewModelScope.launch(Dispatchers.IO) {
        val entity = SecretMessageEntity(
          channelId = _uiState.value.channelConfig.channelId,
          senderName = sender,
          phoneNumber = phone,
          message = text,
          timestamp = timestamp,
          isMe = false
        )
        secretMessageDao.insertMessage(entity)
      }
    }

    // Load initial messages from Room DB for current channel
    loadChannelMessages(_uiState.value.channelConfig.channelId)
  }

  private fun loadChannelMessages(channelId: String) {
    viewModelScope.launch(Dispatchers.IO) {
      val saved = secretMessageDao.getMessagesForChannel(channelId)
      if (saved.isNotEmpty()) {
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
        _uiState.update { it.copy(secretMessages = mapped) }
      }
    }
  }

  fun onButtonClick(char: Char) {
    // Check secret sequence '2011.'
    if (secretManager.feedInput(char)) {
      _uiState.update { it.copy(currentScreen = Screen.SecretArea, isSecretUnlocked = true) }
      secretManager.resetBuffer()
      return
    }

    _uiState.update { state ->
      val newExpr = if (state.result != "0" && state.expression.isEmpty() && char.isDigit()) {
        char.toString()
      } else {
        state.expression + char
      }
      state.copy(expression = newExpr)
    }
  }

  fun onAction(action: String) {
    when (action) {
      "AC" -> {
        secretManager.resetBuffer()
        _uiState.update { it.copy(expression = "", result = "0") }
      }
      "C" -> {
        secretManager.resetBuffer()
        _uiState.update { it.copy(expression = "") }
      }
      "⌫" -> {
        _uiState.update { state ->
          val expr = state.expression
          if (expr.isNotEmpty()) state.copy(expression = expr.dropLast(1)) else state
        }
      }
      "=" -> {
        val expr = _uiState.value.expression
        if (expr.isNotBlank()) {
          val res = calculatorEngine.evaluate(expr)
          secretManager.logOperation(expr, res)
          _uiState.update { state ->
            state.copy(
              result = res,
              history = listOf("$expr = $res") + state.history,
              expression = res
            )
          }
        }
      }
      else -> {
        // Evaluate character or operator
        for (c in action) {
          onButtonClick(c)
        }
      }
    }
  }

  fun navigateTo(screen: Screen) {
    _uiState.update { it.copy(currentScreen = screen) }
  }

  fun updateCustomColors(primary: Long, secondary: Long) {
    _uiState.update { it.copy(customPrimaryColor = primary, customSecondaryColor = secondary) }
  }

  fun verifySecretPassword(pass: String): Boolean {
    val ok = secretManager.verifyMasterPassword(pass)
    if (ok) {
      val logs = secretManager.getLogs(pass)
      _uiState.update { it.copy(secretLogs = logs, isSecretUnlocked = true) }
      connectSecretChannel()
    }
    return ok
  }

  fun updateSecretProfile(nickname: String, phone: String) {
    _uiState.update { it.copy(secretNickname = nickname, secretPhoneNumber = phone) }
  }

  fun sendSecretMessage(text: String) {
    val state = _uiState.value
    if (text.isNotBlank()) {
      val sender = state.secretNickname.ifBlank { "GizliAjan" }
      val phone = state.secretPhoneNumber.ifBlank { "+90 555 000 0000" }
      val now = System.currentTimeMillis()
      val newMsg = ChatMessage(
        senderName = sender,
        phoneNumber = phone,
        message = text,
        timestamp = now,
        isMe = true
      )
      _uiState.update { it.copy(secretMessages = it.secretMessages + newMsg) }

      // Save to Room DB
      viewModelScope.launch(Dispatchers.IO) {
        val entity = SecretMessageEntity(
          channelId = state.channelConfig.channelId,
          senderName = sender,
          phoneNumber = phone,
          message = text,
          timestamp = now,
          isMe = true
        )
        secretMessageDao.insertMessage(entity)
      }

      // Transmit in real-time to the other device via SecretNetworkEngine
      secretNetworkEngine.sendChatMessage(sender, phone, text)
    }
  }

  fun startCall(type: CallType) {
    val state = _uiState.value
    _uiState.update { it.copy(isCallActive = true, callType = type) }
    secretNetworkEngine.sendCallSignal(
      type = "CALL_OFFER",
      isVideo = (type == CallType.Video),
      callerName = state.secretNickname.ifBlank { "Gizli Bağlantı" },
      callerPhone = state.secretPhoneNumber.ifBlank { "+90 555 000 0000" }
    )
  }

  fun endCall() {
    _uiState.update { it.copy(isCallActive = false, callType = CallType.None) }
    secretNetworkEngine.sendCallSignal("CALL_HANGUP")
    secretNetworkEngine.resetCallSignals()
  }

  fun acceptIncomingCall() {
    val signal = _uiState.value.incomingCall
    val callType = if (signal?.isVideo == true) CallType.Video else CallType.Audio
    _uiState.update { it.copy(isCallActive = true, callType = callType, incomingCall = null) }
    secretNetworkEngine.sendCallSignal("CALL_ANSWER", accepted = true)
    secretNetworkEngine.clearIncomingCall()
  }

  fun rejectIncomingCall() {
    secretNetworkEngine.sendCallSignal("CALL_ANSWER", accepted = false)
    secretNetworkEngine.clearIncomingCall()
    _uiState.update { it.copy(incomingCall = null) }
  }

  fun connectSecretChannel() {
    secretNetworkEngine.connect(_uiState.value.channelConfig)
  }

  fun disconnectSecretChannel() {
    secretNetworkEngine.disconnect()
  }

  fun updateChannelConfig(newConfig: ChannelConfig) {
    _uiState.update { it.copy(channelConfig = newConfig) }
    secretNetworkEngine.connect(newConfig)
    loadChannelMessages(newConfig.channelId)
  }

  fun clearChatHistory() {
    val channelId = _uiState.value.channelConfig.channelId
    viewModelScope.launch(Dispatchers.IO) {
      secretMessageDao.clearMessages(channelId)
    }
    _uiState.update {
      it.copy(
        secretMessages = listOf(
          ChatMessage(senderName = "Sistem", phoneNumber = "+90 000 000 0000", message = "Sohbet geçmişi temizlendi.", isMe = false)
        )
      )
    }
  }

  fun setHapticFeedbackEnabled(enabled: Boolean) {
    _uiState.update { it.copy(isHapticFeedbackEnabled = enabled) }
  }

  override fun onCleared() {
    super.onCleared()
    secretNetworkEngine.disconnect()
  }

  fun getFinancialEngine() = financialEngine
  val secretMgr = secretManager
  val networkEngine = secretNetworkEngine
}
