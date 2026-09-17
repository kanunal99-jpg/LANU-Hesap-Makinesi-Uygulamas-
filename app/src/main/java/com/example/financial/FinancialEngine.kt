package com.example.financial

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

data class ProfitResult(
  val profitAmount: BigDecimal,
  val profitPercentage: BigDecimal,
  val costRate: BigDecimal,
  val margin: BigDecimal
)

data class LossResult(
  val lossAmount: BigDecimal,
  val lossPercentage: BigDecimal
)

data class DiscountResult(
  val discountAmount: BigDecimal,
  val discountedPrice: BigDecimal
)

data class VatResult(
  val vatAmount: BigDecimal,
  val priceWithVat: BigDecimal,
  val priceWithoutVat: BigDecimal
)

data class MarginMarkupResult(
  val cost: BigDecimal,
  val sellingPrice: BigDecimal,
  val grossProfit: BigDecimal,
  val margin: BigDecimal,
  val markup: BigDecimal
)

class FinancialEngine {
  private val mc = MathContext(10, RoundingMode.HALF_UP)

  fun calculateProfit(cost: BigDecimal, sellingPrice: BigDecimal): ProfitResult {
    val profitAmount = sellingPrice.subtract(cost)
    val profitPercentage = if (cost.compareTo(BigDecimal.ZERO) != 0) {
      profitAmount.divide(cost, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100))
    } else BigDecimal.ZERO
    val costRate = if (sellingPrice.compareTo(BigDecimal.ZERO) != 0) {
      cost.divide(sellingPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100))
    } else BigDecimal.ZERO
    val margin = if (sellingPrice.compareTo(BigDecimal.ZERO) != 0) {
      profitAmount.divide(sellingPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100))
    } else BigDecimal.ZERO

    return ProfitResult(profitAmount, profitPercentage, costRate, margin)
  }

  fun calculateLoss(cost: BigDecimal, sellingPrice: BigDecimal): LossResult {
    val lossAmount = cost.subtract(sellingPrice)
    val lossPercentage = if (cost.compareTo(BigDecimal.ZERO) != 0) {
      lossAmount.divide(cost, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100))
    } else BigDecimal.ZERO
    return LossResult(lossAmount, lossPercentage)
  }

  fun calculateDiscount(initialPrice: BigDecimal, discountPercent: BigDecimal): DiscountResult {
    val discountAmount = initialPrice.multiply(discountPercent).divide(BigDecimal(100), mc)
    val discountedPrice = initialPrice.subtract(discountAmount)
    return DiscountResult(discountAmount, discountedPrice)
  }

  fun calculateSuccessiveDiscount(initialPrice: BigDecimal, discounts: List<BigDecimal>): DiscountResult {
    var currentPrice = initialPrice
    for (d in discounts) {
      val amt = currentPrice.multiply(d).divide(BigDecimal(100), mc)
      currentPrice = currentPrice.subtract(amt)
    }
    val totalDiscountAmount = initialPrice.subtract(currentPrice)
    return DiscountResult(totalDiscountAmount, currentPrice)
  }

  fun calculateVat(priceWithoutVat: BigDecimal, vatRate: BigDecimal, isInclusive: Boolean = false): VatResult {
    return if (!isInclusive) {
      val vatAmount = priceWithoutVat.multiply(vatRate).divide(BigDecimal(100), mc)
      val priceWithVat = priceWithoutVat.add(vatAmount)
      VatResult(vatAmount, priceWithVat, priceWithoutVat)
    } else {
      // Reverse VAT from priceWithVat
      val divisor = BigDecimal.ONE.add(vatRate.divide(BigDecimal(100), mc))
      val priceWithoutVatCalc = priceWithoutVat.divide(divisor, mc)
      val vatAmount = priceWithoutVat.subtract(priceWithoutVatCalc)
      VatResult(vatAmount, priceWithoutVat, priceWithoutVatCalc)
    }
  }

  fun calculateMarginMarkup(cost: BigDecimal, sellingPrice: BigDecimal): MarginMarkupResult {
    val grossProfit = sellingPrice.subtract(cost)
    val margin = if (sellingPrice.compareTo(BigDecimal.ZERO) != 0) {
      grossProfit.divide(sellingPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100))
    } else BigDecimal.ZERO
    val markup = if (cost.compareTo(BigDecimal.ZERO) != 0) {
      grossProfit.divide(cost, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100))
    } else BigDecimal.ZERO

    return MarginMarkupResult(cost, sellingPrice, grossProfit, margin, markup)
  }

  fun calculateSellingPriceFromMargin(cost: BigDecimal, targetMarginPercent: BigDecimal): BigDecimal {
    // SellingPrice = Cost / (1 - Margin/100)
    val marginFactor = BigDecimal.ONE.subtract(targetMarginPercent.divide(BigDecimal(100), mc))
    if (marginFactor.compareTo(BigDecimal.ZERO) <= 0) throw IllegalArgumentException("Margin >= 100%")
    return cost.divide(marginFactor, mc)
  }

  fun calculateInitialPriceFromDiscounted(discountedPrice: BigDecimal, discountPercent: BigDecimal): BigDecimal {
    // InitialPrice = DiscountedPrice / (1 - Discount%/100)
    val factor = BigDecimal.ONE.subtract(discountPercent.divide(BigDecimal(100), mc))
    if (factor.compareTo(BigDecimal.ZERO) <= 0) throw IllegalArgumentException("Discount >= 100%")
    return discountedPrice.divide(factor, mc)
  }
}
