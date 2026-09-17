package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.CalculatorScreen
import com.example.ui.CalculatorViewModel
import com.example.ui.FinancialScreen
import com.example.ui.Screen
import com.example.ui.SecretAreaScreen
import com.example.ui.ThemeCustomizerScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  private val viewModel: CalculatorViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val state by viewModel.uiState.collectAsState()

      MyApplicationTheme(
        customPrimary = state.customPrimaryColor,
        customSecondary = state.customSecondaryColor
      ) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background
        ) {
          when (state.currentScreen) {
            is Screen.Calculator -> CalculatorScreen(
              viewModel = viewModel,
              onNavigateFinancial = { viewModel.navigateTo(Screen.Financial) },
              onNavigateTheme = { viewModel.navigateTo(Screen.ThemeCustomizer) }
            )
            is Screen.Financial -> FinancialScreen(
              viewModel = viewModel,
              onBack = { viewModel.navigateTo(Screen.Calculator) }
            )
            is Screen.ThemeCustomizer -> ThemeCustomizerScreen(
              viewModel = viewModel,
              onBack = { viewModel.navigateTo(Screen.Calculator) }
            )
            is Screen.SecretArea -> SecretAreaScreen(
              calculatorViewModel = viewModel,
              onBack = { viewModel.navigateTo(Screen.Calculator) }
            )
          }
        }
      }
    }
  }
}
