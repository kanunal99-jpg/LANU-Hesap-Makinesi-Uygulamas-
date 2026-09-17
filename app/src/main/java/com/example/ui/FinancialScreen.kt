package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialScreen(
  viewModel: CalculatorViewModel,
  onBack: () -> Unit
) {
  val financialEngine = viewModel.getFinancialEngine()

  var tabIndex by remember { mutableStateOf(0) }
  val tabs = listOf("Kâr/Zarar", "İndirim", "KDV", "Marj/Markup", "Ters Hesap")

  var costInput by remember { mutableStateOf("100") }
  var sellingInput by remember { mutableStateOf("150") }
  var priceInput by remember { mutableStateOf("200") }
  var discountInput by remember { mutableStateOf("20") }
  var vatInput by remember { mutableStateOf("18") }

  var resultText by remember { mutableStateOf("Hesaplama bekleniyor...") }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Finansal Hesap Makinesi") },
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
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      ScrollableTabRow(selectedTabIndex = tabIndex) {
        tabs.forEachIndexed { index, title ->
          Tab(
            selected = tabIndex == index,
            onClick = { tabIndex = index },
            text = { Text(title) }
          )
        }
      }

      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          when (tabIndex) {
            0 -> {
              Text("Kâr / Zarar Hesaplama", fontWeight = FontWeight.Bold, fontSize = 18.sp)
              OutlinedTextField(
                value = costInput,
                onValueChange = { costInput = it },
                label = { Text("Maliyet (TL)") },
                modifier = Modifier.fillMaxWidth()
              )
              OutlinedTextField(
                value = sellingInput,
                onValueChange = { sellingInput = it },
                label = { Text("Satış Fiyatı (TL)") },
                modifier = Modifier.fillMaxWidth()
              )
              Button(
                onClick = {
                  val c = costInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
                  val s = sellingInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
                  if (s >= c) {
                    val res = financialEngine.calculateProfit(c, s)
                    resultText = "Kâr Tutarı: ${res.profitAmount} TL\nKâr Yüzdesi: %${res.profitPercentage}\nMaliyet Oranı: %${res.costRate}\nMarj: %${res.margin}"
                  } else {
                    val res = financialEngine.calculateLoss(c, s)
                    resultText = "Zarar Tutarı: ${res.lossAmount} TL\nZarar Yüzdesi: %${res.lossPercentage}"
                  }
                },
                modifier = Modifier.fillMaxWidth()
              ) {
                Text("Hesapla")
              }
            }
            1 -> {
              Text("İndirim Hesaplama", fontWeight = FontWeight.Bold, fontSize = 18.sp)
              OutlinedTextField(
                value = priceInput,
                onValueChange = { priceInput = it },
                label = { Text("İlk Fiyat (TL)") },
                modifier = Modifier.fillMaxWidth()
              )
              OutlinedTextField(
                value = discountInput,
                onValueChange = { discountInput = it },
                label = { Text("İndirim Oranı (%)") },
                modifier = Modifier.fillMaxWidth()
              )
              Button(
                onClick = {
                  val p = priceInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
                  val d = discountInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
                  val res = financialEngine.calculateDiscount(p, d)
                  resultText = "İndirim Tutarı: ${res.discountAmount} TL\nİndirimli Fiyat: ${res.discountedPrice} TL"
                },
                modifier = Modifier.fillMaxWidth()
              ) {
                Text("Hesapla")
              }
            }
            2 -> {
              Text("KDV Hesaplama", fontWeight = FontWeight.Bold, fontSize = 18.sp)
              OutlinedTextField(
                value = priceInput,
                onValueChange = { priceInput = it },
                label = { Text("Fiyat (TL)") },
                modifier = Modifier.fillMaxWidth()
              )
              OutlinedTextField(
                value = vatInput,
                onValueChange = { vatInput = it },
                label = { Text("KDV Oranı (%)") },
                modifier = Modifier.fillMaxWidth()
              )
              Button(
                onClick = {
                  val p = priceInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
                  val v = vatInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
                  val resExcl = financialEngine.calculateVat(p, v, false)
                  val resIncl = financialEngine.calculateVat(p, v, true)
                  resultText = "KDV Hariçten:\nKDV Tutarı: ${resExcl.vatAmount} TL\nKDV Dahil: ${resExcl.priceWithVat} TL\n\nKDV Dahilden (Ters):\nKDV Hariç: ${resIncl.priceWithoutVat} TL\nKDV Tutarı: ${resIncl.vatAmount} TL"
                },
                modifier = Modifier.fillMaxWidth()
              ) {
                Text("Hesapla")
              }
            }
            3 -> {
              Text("Marj ve Markup", fontWeight = FontWeight.Bold, fontSize = 18.sp)
              OutlinedTextField(
                value = costInput,
                onValueChange = { costInput = it },
                label = { Text("Maliyet (TL)") },
                modifier = Modifier.fillMaxWidth()
              )
              OutlinedTextField(
                value = sellingInput,
                onValueChange = { sellingInput = it },
                label = { Text("Satış Fiyatı (TL)") },
                modifier = Modifier.fillMaxWidth()
              )
              Button(
                onClick = {
                  val c = costInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
                  val s = sellingInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
                  val res = financialEngine.calculateMarginMarkup(c, s)
                  resultText = "Brüt Kâr: ${res.grossProfit} TL\nMarj (Margin): %${res.margin}\nMarkup: %${res.markup}"
                },
                modifier = Modifier.fillMaxWidth()
              ) {
                Text("Hesapla")
              }
            }
            4 -> {
              Text("Ters Hesaplamalar", fontWeight = FontWeight.Bold, fontSize = 18.sp)
              OutlinedTextField(
                value = costInput,
                onValueChange = { costInput = it },
                label = { Text("Hedef Marj (%) / Maliyet") },
                modifier = Modifier.fillMaxWidth()
              )
              Button(
                onClick = {
                  val c = costInput.toBigDecimalOrNull() ?: BigDecimal.ZERO
                  val targetMargin = BigDecimal(20)
                  try {
                    val requiredSelling = financialEngine.calculateSellingPriceFromMargin(c, targetMargin)
                    resultText = "%20 Marj ile Satış Fiyatı: $requiredSelling TL"
                  } catch (e: Exception) {
                    resultText = "Hata: ${e.message}"
                  }
                },
                modifier = Modifier.fillMaxWidth()
              ) {
                Text("Hedef Marj Fiyatı Hesapla")
              }
            }
          }
        }
      }

      Card(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text("Sonuçlar", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
          Spacer(modifier = Modifier.height(8.dp))
          Text(text = resultText, fontSize = 16.sp)
        }
      }
    }
  }
}
