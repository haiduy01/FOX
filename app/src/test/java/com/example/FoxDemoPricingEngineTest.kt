package com.example

import com.example.data.repository.FoxDemoPricingEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class FoxDemoPricingEngineTest {

  @Test
  fun ridePricing_calculatesExactLongVndAndCapsVoucher() {
    val quote = FoxDemoPricingEngine.estimateRide("FOX Car 4", 10, "FOXRIDE25")
    // 24,000 + 11,000 * 10 = 134,000; 25% = 33,500 -> capped at 30,000 -> total 104,000
    assertEquals(134_000L, quote.subtotalVnd)
    assertEquals(30_000L, quote.discountVnd)
    assertEquals(104_000L, quote.totalVnd)
  }

  @Test
  fun deliveryPricing_calculatesSpeedWeightAndVoucher() {
    val quote = FoxDemoPricingEngine.estimateDelivery("2–5 kg", "Nhanh", "FOXSHIP15")
    // 32,000 + 8,000 = 40,000; discount 10,000 -> total 30,000
    assertEquals(40_000L, quote.subtotalVnd)
    assertEquals(10_000L, quote.discountVnd)
    assertEquals(30_000L, quote.totalVnd)
  }

  @Test
  fun foodPricing_appliesVoucherOnlyWhenMeetingMinOrder() {
    val belowMin = FoxDemoPricingEngine.estimateFood(65_000L, "FOXFOOD30")
    assertEquals(0L, belowMin.discountVnd)
    assertEquals(80_000L, belowMin.totalVnd)

    val aboveMin = FoxDemoPricingEngine.estimateFood(100_000L, "FOXFOOD30")
    assertEquals(30_000L, aboveMin.discountVnd)
    assertEquals(85_000L, aboveMin.totalVnd)
  }

  @Test
  fun shoppingPricing_appliesFreeshipThresholdAndVoucher() {
    val quote = FoxDemoPricingEngine.estimateShopping(350_000L, "FOXSHOP20")
    assertEquals(0L, quote.feeVnd)
    assertEquals(20_000L, quote.discountVnd)
    assertEquals(330_000L, quote.totalVnd)
  }
}
