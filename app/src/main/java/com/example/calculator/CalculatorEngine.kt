package com.example.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class CalculatorEngine {
  private val mc = MathContext(10, RoundingMode.HALF_UP)

  fun evaluate(expression: String): String {
    try {
      if (expression.isBlank()) return "0"
      val cleaned = expression.replace("×", "*").replace("÷", "/")
      val result = evalTokens(tokenize(cleaned))
      return formatResult(result)
    } catch (e: Exception) {
      return "Hata"
    }
  }

  private data class HistoryItem(val expression: String, val result: String, val timestamp: Long = System.currentTimeMillis())

  private fun tokenize(expr: String): List<String> {
    val tokens = mutableListOf<String>()
    var i = 0
    while (i < expr.length) {
      val c = expr[i]
      if (c.isWhitespace()) {
        i++
        continue
      }
      if (c.isDigit() || c == '.') {
        val sb = StringBuilder()
        while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
          sb.append(expr[i])
          i++
        }
        tokens.add(sb.toString())
      } else if (c == '-' && (i == 0 || expr[i - 1] in "+-*/(×÷")) {
        // Unary minus
        i++
        val sb = StringBuilder("-")
        while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) {
          sb.append(expr[i])
          i++
        }
        tokens.add(sb.toString())
      } else {
        tokens.add(c.toString())
        i++
      }
    }
    return tokens
  }

  private fun evalTokens(tokens: List<String>): BigDecimal {
    if (tokens.isEmpty()) return BigDecimal.ZERO
    // Simple Shunting-Yard / Recursive Descent parser for +, -, *, /, %
    val outputQueue = mutableListOf<String>()
    val opStack = mutableListOf<String>()

    val precedence = mapOf("+" to 1, "-" to 1, "*" to 2, "/" to 2, "%" to 2)

    for (token in tokens) {
      if (token.toDoubleOrNull() != null) {
        outputQueue.add(token)
      } else if (token in precedence.keys) {
        while (opStack.isNotEmpty() && opStack.last() != "(" &&
          (precedence[opStack.last()] ?: 0) >= (precedence[token] ?: 0)) {
          outputQueue.add(opStack.removeAt(opStack.size - 1))
        }
        opStack.add(token)
      } else if (token == "(") {
        opStack.add(token)
      } else if (token == ")") {
        while (opStack.isNotEmpty() && opStack.last() != "(") {
          outputQueue.add(opStack.removeAt(opStack.size - 1))
        }
        if (opStack.isNotEmpty() && opStack.last() == "(") {
          opStack.removeAt(opStack.size - 1)
        }
      }
    }
    while (opStack.isNotEmpty()) {
      outputQueue.add(opStack.removeAt(opStack.size - 1))
    }

    val evalStack = mutableListOf<BigDecimal>()
    for (token in outputQueue) {
      if (token.toDoubleOrNull() != null) {
        evalStack.add(BigDecimal(token))
      } else {
        if (evalStack.size < 2) throw IllegalArgumentException("Invalid expression")
        val b = evalStack.removeAt(evalStack.size - 1)
        val a = evalStack.removeAt(evalStack.size - 1)
        val res = when (token) {
          "+" -> a.add(b, mc)
          "-" -> a.subtract(b, mc)
          "*" -> a.multiply(b, mc)
          "/" -> {
            if (b.compareTo(BigDecimal.ZERO) == 0) throw ArithmeticException("Division by zero")
            a.divide(b, 10, RoundingMode.HALF_UP)
          }
          "%" -> a.multiply(b).divide(BigDecimal(100), mc)
          else -> throw IllegalArgumentException("Unknown operator")
        }
        evalStack.add(res)
      }
    }
    return if (evalStack.isNotEmpty()) evalStack.last() else BigDecimal.ZERO
  }

  private fun formatResult(bd: BigDecimal): String {
    val stripped = bd.stripTrailingZeros()
    return if (stripped.scale() <= 0) {
      stripped.toBigInteger().toString()
    } else {
      stripped.toPlainString()
    }
  }

  fun calculateAdvanced(func: String, value: BigDecimal): BigDecimal {
    return when (func) {
      "square" -> value.pow(2, mc)
      "sqrt" -> {
        if (value.compareTo(BigDecimal.ZERO) < 0) throw IllegalArgumentException("Negative sqrt")
        value.sqrt(mc)
      }
      "inv" -> {
        if (value.compareTo(BigDecimal.ZERO) == 0) throw ArithmeticException("Division by zero")
        BigDecimal.ONE.divide(value, mc)
      }
      "sin" -> BigDecimal(Math.sin(value.toDouble()), mc)
      "cos" -> BigDecimal(Math.cos(value.toDouble()), mc)
      "tan" -> BigDecimal(Math.tan(value.toDouble()), mc)
      "log" -> {
        if (value.compareTo(BigDecimal.ZERO) <= 0) throw IllegalArgumentException("Log non-positive")
        BigDecimal(Math.log10(value.toDouble()), mc)
      }
      "ln" -> {
        if (value.compareTo(BigDecimal.ZERO) <= 0) throw IllegalArgumentException("Ln non-positive")
        BigDecimal(Math.log(value.toDouble()), mc)
      }
      "factorial" -> {
        val n = value.toInt()
        if (n < 0 || n > 50) throw IllegalArgumentException("Invalid factorial")
        var fact = BigDecimal.ONE
        for (i in 1..n) {
          fact = fact.multiply(BigDecimal(i))
        }
        fact
      }
      else -> value
    }
  }
}
