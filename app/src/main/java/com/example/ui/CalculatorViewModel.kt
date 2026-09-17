package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.calculator.CalculatorEngine
import com.example.financial.FinancialEngine
import com.example.security.SecretManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CalculatorUiState(
    val expression: String = "",
    val result: String = "0",
    val history: List<String> = emptyList(),
    val currentScreen: Screen = Screen.Calculator,
    val customPrimaryColor: Long = 0xFF6200EE,
    val customSecondaryColor: Long = 0xFF03DAC6,
    val isDarkTheme: Boolean = false,
    val isHapticFeedbackEnabled: Boolean = true
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

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    fun onButtonClick(char: Char) {
        // Feed character to secret manager to detect '2011.'
        if (secretManager.feedInput(char)) {
            _uiState.update { it.copy(currentScreen = Screen.SecretArea, expression = "") }
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
                    if (expr.isNotEmpty()) {
                        // Also sync back with secret manager buffer if dropping characters
                        val remaining = expr.dropLast(1)
                        secretManager.resetBuffer()
                        for (c in remaining) {
                            secretManager.feedInput(c)
                        }
                        state.copy(expression = remaining)
                    } else state
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
                // Feed operator or characters sequentially
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

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isHapticFeedbackEnabled = enabled) }
    }

    fun getFinancialEngine() = financialEngine
}
