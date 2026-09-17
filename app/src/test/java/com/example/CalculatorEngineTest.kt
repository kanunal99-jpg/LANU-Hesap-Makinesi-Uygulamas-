package com.example

import com.example.calculator.CalculatorEngine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class CalculatorEngineTest {
  private val engine = CalculatorEngine()

  @Test
  fun testBasicOperations() {
    assertEquals("2", engine.evaluate("1+1"))
    assertEquals("6", engine.evaluate("10-4"))
    assertEquals("25", engine.evaluate("5*5"))
    assertEquals("5", engine.evaluate("20/4"))
  }

  @Test
  fun testOperatorPrecedenceAndParentheses() {
    assertEquals("14", engine.evaluate("2+3*4"))
    assertEquals("20", engine.evaluate("(2+3)*4"))
  }

  @Test
  fun testDecimalsAndNegatives() {
    assertEquals("0.3", engine.evaluate("0.1+0.2"))
    assertEquals("-5", engine.evaluate("-10+5"))
  }

  @Test
  fun testDivisionByZero() {
    assertEquals("Hata", engine.evaluate("10/0"))
  }
}
