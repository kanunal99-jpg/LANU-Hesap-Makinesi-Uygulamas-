package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeCustomizerScreen(
  viewModel: CalculatorViewModel,
  onBack: () -> Unit
) {
  val state by viewModel.uiState.collectAsState()
  val palettes = listOf(
    Pair("Klasik Mor", Pair(0xFF6650a4L, 0xFF625b71L)),
    Pair("Zümrüt Yeşil", Pair(0xFF006837L, 0xFF4E9F3dL)),
    Pair("Okyanus Mavi", Pair(0xFF005b96L, 0xFF6497b1L)),
    Pair("Gün Batımı", Pair(0xFFD35400L, 0xFFE67E22L)),
    Pair("Gece Siyahı", Pair(0xFF2C3E50L, 0xFF4F5D73L))
  )

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Tema & Tercihler") },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
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
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Text("Kullanıcı Deneyimi & Geri Bildirim", fontWeight = FontWeight.Bold, fontSize = 18.sp)

      Card(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("haptic_feedback_card"),
        shape = RoundedCornerShape(12.dp)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Tuş Titreşimi (Haptik)",
              fontWeight = FontWeight.SemiBold,
              fontSize = 16.sp
            )
            Text(
              text = "Hesap makinesi tuşlarına basıldığında hafif haptik titreşim",
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Switch(
            checked = state.isHapticFeedbackEnabled,
            onCheckedChange = { viewModel.setHapticFeedbackEnabled(it) },
            modifier = Modifier.testTag("haptic_feedback_switch")
          )
        }
      }

      HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

      Text("Hazır Renk Paletleri", fontWeight = FontWeight.Bold, fontSize = 18.sp)

      palettes.forEach { (name, colors) ->
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .clickable {
              viewModel.updateCustomColors(colors.first, colors.second)
            },
          shape = RoundedCornerShape(12.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(text = name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Box(
                modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(Color(colors.first))
              )
              Box(
                modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(Color(colors.second))
              )
            }
          }
        }
      }
    }
  }
}
