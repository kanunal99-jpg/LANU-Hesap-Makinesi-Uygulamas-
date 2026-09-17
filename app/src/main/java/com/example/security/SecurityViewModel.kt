package com.example.security

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SecurityUiState(
  val isSecretUnlocked: Boolean = false,
  val isSecretAreaActive: Boolean = false,
  val secretLogs: List<String> = emptyList(),
  val secretNickname: String = "GizliAjan",
  val secretPhoneNumber: String = "+90 555 000 0000",
  val secretMessages: List<String> = emptyList(),
  val errorMessage: String = ""
)

class SecurityViewModel(application: Application) : AndroidViewModel(application) {
  private val secretManager = SecretManager(application)

  private val _uiState = MutableStateFlow(SecurityUiState())
  val uiState: StateFlow<SecurityUiState> = _uiState.asStateFlow()

  fun feedInput(char: Char): Boolean {
    val triggered = secretManager.feedInput(char)
    if (triggered) {
      _uiState.update { it.copy(isSecretAreaActive = true) }
      secretManager.resetBuffer()
    }
    return triggered
  }

  fun resetBuffer() {
    secretManager.resetBuffer()
  }

  fun verifyPassword(pass: String): Boolean {
    val success = secretManager.verifyMasterPassword(pass)
    if (success) {
      val logs = secretManager.getLogs(pass)
      _uiState.update { it.copy(isSecretUnlocked = true, secretLogs = logs, errorMessage = "") }
    } else {
      _uiState.update { it.copy(errorMessage = "Hatalı Ana Şifre!") }
    }
    return success
  }

  fun updateProfile(nickname: String, phone: String) {
    _uiState.update { it.copy(secretNickname = nickname, secretPhoneNumber = phone) }
  }

  fun logOperation(op: String, res: String) {
    secretManager.logOperation(op, res)
  }

  fun exitSecretArea() {
    _uiState.update { it.copy(isSecretAreaActive = false, isSecretUnlocked = false) }
    secretManager.resetBuffer()
  }
}
