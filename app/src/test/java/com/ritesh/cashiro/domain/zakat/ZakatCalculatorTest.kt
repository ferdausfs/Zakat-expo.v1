package com.ritesh.cashiro.domain.zakat

import java.math.BigDecimal
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ZakatCalculator]: nisab thresholds, eligibility,
 * hawl tracking on the Hijrah calendar, and currency-unit independence.
 */
class ZakatCalculatorTest {

    private val goldPrice = BigDecimal("2400.00") // e.g. SAR per gram
    private val silverPrice = BigDecimal("30.00") // e.g. SAR per gram

    private fun wealth(
        cash: String = "0",
        goldGrams: String = "0",
        silverGrams: String = "0",
        investments: String = "0",
        debts: String = "0"
    ) = ZakatCalculator.Wealth(
        cash = BigDecimal(cash),
        goldGrams = BigDecimal(goldGrams),
        silverGrams = BigDecimal(silverGrams),
        investments = BigDecimal(investments),
        debtsOwed = BigDecimal(debts)
    )

    private fun hawl(
        start: LocalDate = LocalDate.of(2024, 1, 1),
        today: LocalDate = LocalDate.of(2026, 1, 1) // more than one lunar year later
    ) = ZakatCalculator.Hawl(start, today)

    // ---------- Nisab thresholds ----------

    @Test
    fun `gold nisab equals 85 grams times gold price`() {
        val expected = goldPrice.multiply(BigDecimal.valueOf(85.0)).setScale(2)
        assertEquals(expected, ZakatCalculator.nisabValue(goldPrice, ZakatCalculator.GOLD_NISAB_GRAMS))
        assertEquals(0, expected.compareTo(BigDecimal("204000.00")))
    }

    @Test
    fun `silver nisab equals 595 grams times silver price`() {
        val expected = silverPrice.multiply(BigDecimal.valueOf(595.0)).setScale(2)
        assertEquals(expected, ZakatCalculator.nisabValue(silverPrice, ZakatCalculator.SILVER_NISAB_GRAMS))
        assertEquals(0, expected.compareTo(BigDecimal("17850.00")))
    }

    // ---------- Eligibility and zakat due ----------

    @Test
    fun `wealth above silver nisab with complete hawl yields 2_5 percent`() {
        // Net wealth 20000 > silver nisab 17850, below gold nisab 204000.
        val result = ZakatCalculator.calculate(
            wealth = wealth(cash = "20000"),
            prices = ZakatCalculator.MetalPrices(goldPrice, silverPrice),
            method = ZakatCalculator.NisabMethod.SILVER,
            hawl = hawl()
        )
        assertTrue(result.eligible)
        assertTrue(result.hawlComplete)
        assertEquals(0, result.zakatDue.compareTo(BigDecimal("500.00")))
    }

    @Test
    fun `wealth below nisab yields no zakat`() {
        val result = ZakatCalculator.calculate(
            wealth = wealth(cash = "10000"),
            prices = ZakatCalculator.MetalPrices(goldPrice, silverPrice),
            method = ZakatCalculator.NisabMethod.SILVER,
            hawl = hawl()
        )
        assertFalse(result.eligible)
        assertEquals(0, result.zakatDue.compareTo(BigDecimal.ZERO))
    }

    @Test
    fun `wealth above nisab but incomplete hawl yields no zakat`() {
        val today = LocalDate.of(2024, 6, 1) // only a few months into the hawl
        val result = ZakatCalculator.calculate(
            wealth = wealth(cash = "50000"),
            prices = ZakatCalculator.MetalPrices(goldPrice, silverPrice),
            method = ZakatCalculator.NisabMethod.SILVER,
            hawl = hawl(start = LocalDate.of(2024, 1, 1), today = today)
        )
        assertFalse(result.hawlComplete)
        assertFalse(result.eligible)
        assertEquals(0, result.zakatDue.compareTo(BigDecimal.ZERO))
    }

    @Test
    fun `debts owed are deducted before eligibility check`() {
        // Gross 20000, debts 4000 -> net 16000 < silver nisab 17850.
        val result = ZakatCalculator.calculate(
            wealth = wealth(cash = "20000", debts = "4000"),
            prices = ZakatCalculator.MetalPrices(goldPrice, silverPrice),
            method = ZakatCalculator.NisabMethod.SILVER,
            hawl = hawl()
        )
        assertFalse(result.eligible)
        assertEquals(0, result.netWealth.compareTo(BigDecimal("16000.00")))

        // Gross 20000, debts 1000 -> net 19000 >= silver nisab.
        val eligibleResult = ZakatCalculator.calculate(
            wealth = wealth(cash = "20000", debts = "1000"),
            prices = ZakatCalculator.MetalPrices(goldPrice, silverPrice),
            method = ZakatCalculator.NisabMethod.SILVER,
            hawl = hawl()
        )
        assertTrue(eligibleResult.eligible)
        assertEquals(0, eligibleResult.zakatDue.compareTo(BigDecimal("475.00")))
    }

    @Test
    fun `gold and silver grams are valued with supplied prices`() {
        val result = ZakatCalculator.calculate(
            wealth = wealth(goldGrams = "10", silverGrams = "100"),
            prices = ZakatCalculator.MetalPrices(goldPrice, silverPrice),
            method = ZakatCalculator.NisabMethod.SILVER,
            hawl = hawl()
        )
        assertEquals(0, result.goldValue.compareTo(BigDecimal("24000.00")))
        assertEquals(0, result.silverValue.compareTo(BigDecimal("3000.00")))
        assertEquals(0, result.totalWealth.compareTo(BigDecimal("27000.00")))
    }

    @Test
    fun `gold method uses gold nisab and silver method uses silver nisab`() {
        // Net wealth 190000: below gold nisab (204000), above silver (17850).
        val silverAssessment = ZakatCalculator.calculate(
            wealth = wealth(cash = "190000"),
            prices = ZakatCalculator.MetalPrices(goldPrice, silverPrice),
            method = ZakatCalculator.NisabMethod.SILVER,
            hawl = hawl()
        )
        val goldAssessment = ZakatCalculator.calculate(
            wealth = wealth(cash = "190000"),
            prices = ZakatCalculator.MetalPrices(goldPrice, silverPrice),
            method = ZakatCalculator.NisabMethod.GOLD,
            hawl = hawl()
        )
        assertTrue(silverAssessment.eligible)
        assertFalse(goldAssessment.eligible)
    }

    // ---------- Hawl (Hijrah calendar) ----------

    @Test
    fun `hawl completes on the hijrah anniversary of the start date`() {
        val start = LocalDate.of(2025, 2, 1)
        val anniversary = LocalDate.from(HijrahDate.from(start).plus(1, ChronoUnit.YEARS))

        val dayBefore = ZakatCalculator.hawlStatus(start, anniversary.minusDays(1))
        assertFalse(dayBefore.complete)
        assertTrue(dayBefore.daysInYear == 354L || dayBefore.daysInYear == 355L)
        assertEquals(dayBefore.daysInYear - 1, dayBefore.daysElapsed)

        val onTheDay = ZakatCalculator.hawlStatus(start, anniversary)
        assertTrue(onTheDay.complete)
        assertEquals(dayBefore.daysInYear, onTheDay.daysInYear)
    }

    @Test
    fun `hawl progress is clamped for a future start date`() {
        val start = LocalDate.of(2030, 1, 1)
        val status = ZakatCalculator.hawlStatus(start, LocalDate.of(2026, 1, 1))
        assertEquals(0L, status.daysElapsed)
        assertFalse(status.complete)
    }

    @Test
    fun `hawl status outside hijrah range falls back to mean lunar year`() {
        val start = LocalDate.of(2175, 1, 1) // beyond HijrahDate supported range
        val status = ZakatCalculator.hawlStatus(start, start.plusDays(400))
        assertEquals(ZakatCalculator.MEAN_LUNAR_YEAR_DAYS, status.daysInYear)
        assertTrue(status.complete)
    }

    // ---------- Currency-unit independence ----------

    @Test
    fun `calculation scales consistently across currency units`() {
        // Same wealth expressed in SAR (1x) and a BDT-like scale (x32.5).
        val sarBelow = ZakatCalculator.calculate(
            wealth = wealth(cash = "1000"),
            prices = ZakatCalculator.MetalPrices(BigDecimal("2400"), BigDecimal("30")),
            method = ZakatCalculator.NisabMethod.SILVER,
            hawl = hawl()
        )
        val bdtBelow = ZakatCalculator.calculate(
            wealth = wealth(cash = "32500"),
            prices = ZakatCalculator.MetalPrices(BigDecimal("78000"), BigDecimal("975")),
            method = ZakatCalculator.NisabMethod.SILVER,
            hawl = hawl()
        )
        assertFalse(sarBelow.eligible)
        assertFalse(bdtBelow.eligible)

        // Above the threshold in both units -> identical rate applied.
        val sarDue = ZakatCalculator.calculate(
            wealth = wealth(cash = "20000"),
            prices = ZakatCalculator.MetalPrices(BigDecimal("2400"), BigDecimal("30")),
            method = ZakatCalculator.NisabMethod.SILVER,
            hawl = hawl()
        ).zakatDue
        val bdtDue = ZakatCalculator.calculate(
            wealth = wealth(cash = "650000"),
            prices = ZakatCalculator.MetalPrices(BigDecimal("78000"), BigDecimal("975")),
            method = ZakatCalculator.NisabMethod.SILVER,
            hawl = hawl()
        ).zakatDue
        assertEquals(0, sarDue.multiply(BigDecimal("32.5")).compareTo(bdtDue))
    }
}
