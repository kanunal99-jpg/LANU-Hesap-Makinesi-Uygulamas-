package com.example

import com.example.financial.FinancialEngine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class FinancialEngineTest {
  private val engine = FinancialEngine()

  @Test
  fun testProfitCalculation() {
    val result = engine.calculateProfit(BigDecimal("100"), BigDecimal("150"))
    assertEquals(0, result.profitAmount.compareTo(BigDecimal("50")))
    assertEquals(0, result.profitPercentage.compareTo(BigDecimal("50")))
  }

  @Test
  fun testDiscountCalculation() {
    val result = engine.calculateDiscount(BigDecimal("200"), BigDecimal("20"))
    assertEquals(0, result.discountAmount.compareTo(BigDecimal("40")))
    assertEquals(0, result.discountedPrice.compareTo(BigDecimal("160")))
  }

  @Test
  fun testSuccessiveDiscount() {
    val result = engine.calculateSuccessiveDiscount(BigDecimal("100"), listOf(BigDecimal("10"), BigDecimal("20")))
    // 100 - 10% = 90; 90 - 20% = 72. Total discount = 28.
    assertEquals(0, result.discountedPrice.compareTo(BigDecimal("72")))
  }

  @Test
  fun testVatCalculation() {
    val result = engine.calculateVat(BigDecimal("100"), BigDecimal("18"), false)
    assertEquals(0, result.vatAmount.compareTo(BigDecimal("18")))
    assertEquals(0, result.priceWithVat.compareTo(BigDecimal("118")))
  }
}
