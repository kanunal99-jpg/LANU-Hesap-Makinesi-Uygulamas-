package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(
  viewModel: CalculatorViewModel,
  onNavigateFinancial: () -> Unit,
  onNavigateTheme: () -> Unit
) {
  val state by viewModel.uiState.collectAsState()
  val hapticHelper = rememberKeyHapticHelper()

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("LANU Hesap Makinesi", fontWeight = FontWeight.Bold) },
        actions = {
          IconButton(onClick = onNavigateFinancial) {
            Icon(Icons.Default.AttachMoney, contentDescription = "Finansal Hesaplar")
          }
          IconButton(onClick = onNavigateTheme) {
            Icon(Icons.Default.Palette, contentDescription = "Tema Ayarları")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
      )
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(16.dp),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      // Display Area
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .weight(0.25f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
      ) {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
          verticalArrangement = Arrangement.Bottom,
          horizontalAlignment = Alignment.End
        ) {
          Text(
            text = state.expression.ifEmpty { "0" },
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 2,
            textAlign = TextAlign.End
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = state.result,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.End
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Keypad Area
      val buttons = listOf(
        listOf("AC", "C", "(", ")"),
        listOf("7", "8", "9", "÷"),
        listOf("4", "5", "6", "×"),
        listOf("1", "2", "3", "-"),
        listOf("0", ".", "⌫", "+"),
        listOf("+/-", "%", "=")
      )

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .weight(0.7f),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        buttons.forEach { row ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            row.forEach { btn ->
              val isOperator = btn in listOf("÷", "×", "-", "+", "=", "%")
              val isAction = btn in listOf("AC", "C", "⌫")
              val buttonColor = when {
                btn == "=" -> MaterialTheme.colorScheme.primary
                isOperator -> MaterialTheme.colorScheme.secondaryContainer
                isAction -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surface
              }
              val textColor = when {
                btn == "=" -> MaterialTheme.colorScheme.onPrimary
                isOperator -> MaterialTheme.colorScheme.onSecondaryContainer
                isAction -> MaterialTheme.colorScheme.onTertiaryContainer
                else -> MaterialTheme.colorScheme.onSurface
              }

              Button(
                onClick = {
                  if (state.isHapticFeedbackEnabled) {
                    hapticHelper.performKeyClickHaptic()
                  }
                  viewModel.onAction(btn)
                },
                modifier = Modifier
                  .weight(1f)
                  .fillMaxHeight()
                  .testTag("calc_key_$btn"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor)
              ) {
                Text(
                  text = btn,
                  fontSize = 20.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = textColor
                )
              }
            }
          }
        }
      }
    }
  }
}
