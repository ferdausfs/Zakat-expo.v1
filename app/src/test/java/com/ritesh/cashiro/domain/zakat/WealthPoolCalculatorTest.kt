package com.ritesh.cashiro.domain.zakat

import com.ritesh.cashiro.data.database.entity.ZakatAssetEntity
import com.ritesh.cashiro.data.database.entity.ZakatAssetType
import com.ritesh.cashiro.data.database.entity.ZakatAssetUnit
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [WealthPoolCalculator] (Phase 2b): unit conversion,
 * asset valuation, combined daily series, nisab-crossing detection with
 * hawl reset, and per-asset hawl status.
 */
class WealthPoolCalculatorTest {

    private val goldPrice = BigDecimal("12000.00") // BDT per gram, e.g.
    private val silverPrice = BigDecimal("150.00")

    private fun bd(v: String) = BigDecimal(v)

    private fun balance(
        bank: String,
        last4: String,
        amount: String,
        at: LocalDateTime,
        creditCard: Boolean = false
    ) = com.ritesh.cashiro.data.database.entity.AccountBalanceEntity(
        bankName = bank,
        accountLast4 = last4,
        balance = bd(amount),
        timestamp = at,
        isCreditCard = creditCard
    )

    private fun asset(
        type: ZakatAssetType,
        quantity: String = "1",
        unit: ZakatAssetUnit = ZakatAssetUnit.GRAM,
        karat: Int? = null,
        value: String? = null,
        acquiredOn: LocalDate = LocalDate.of(2026, 1, 1)
    ) = ZakatAssetEntity(
        type = type.name,
        name = "test",
        quantity = bd(quantity),
        unit = unit.name,
        karat = karat,
        currency = "BDT",
        acquisitionDate = acquiredOn,
        estimatedValue = value?.let { bd(it) }
    )

    // ---------------- Unit conversion ----------------

    @Test
    fun `vori ana ratti convert to grams using Bangladeshi standard`() {
        assertEquals(0, bd("11.664").compareTo(WealthPoolCalculator.toGrams(bd("1"), ZakatAssetUnit.VORI)))
        assertEquals(0, bd("0.729").compareTo(WealthPoolCalculator.toGrams(bd("1"), ZakatAssetUnit.ANA)))
        assertEquals(0, bd("0.243").compareTo(WealthPoolCalculator.toGrams(bd("1"), ZakatAssetUnit.RATTI)))
        assertEquals(0, bd("5").compareTo(WealthPoolCalculator.toGrams(bd("5"), ZakatAssetUnit.GRAM)))
    }

    @Test
    fun `karat purity matches karat over 24`() {
        assertEquals(0, bd("1").compareTo(WealthPoolCalculator.karatPurity(24).setScale(10)))
        assertEquals(0, bd("0.9166666667").compareTo(WealthPoolCalculator.karatPurity(22)))
        assertEquals(0, bd("1").compareTo(WealthPoolCalculator.karatPurity(null)))
    }

    // ---------------- Asset valuation ----------------

    @Test
    fun `gold value uses grams purity and price`() {
        // 1 vori 22k gold at 12000/gram:
        // 11.664 g x (22/24) x 12000 = exactly 128304.00
        val gold = asset(ZakatAssetType.GOLD, "1", ZakatAssetUnit.VORI, 22)
        val value = WealthPoolCalculator.assetValue(gold, goldPrice, silverPrice)
        assertEquals(0, bd("128304.00").compareTo(value))
    }

    @Test
    fun `silver value uses grams and silver price`() {
        val silver = asset(ZakatAssetType.SILVER, "100", ZakatAssetUnit.GRAM)
        val value = WealthPoolCalculator.assetValue(silver, goldPrice, silverPrice)
        assertEquals(0, bd("15000.00").compareTo(value))
    }

    @Test
    fun `non-metal uses user entered value`() {
        val property = asset(ZakatAssetType.PROPERTY, value = "2500000.50")
        val value = WealthPoolCalculator.assetValue(property, goldPrice, silverPrice)
        assertEquals(0, bd("2500000.50").compareTo(value))
    }

    // ---------------- Cash ----------------

    @Test
    fun `cash excludes credit cards and floors negatives`() {
        val balances = listOf(
            balance("BankA", "1234", "5000", LocalDateTime.now()),
            balance("BankB", "5678", "-300", LocalDateTime.now()),
            balance("CardC", "9999", "80000", LocalDateTime.now(), creditCard = true)
        )
        assertEquals(0, bd("5000").compareTo(WealthPoolCalculator.currentCash(balances)))
    }

    // ---------------- Daily series ----------------

    @Test
    fun `series includes cash from acquisition date and excludes it before`() {
        val day1 = LocalDate.of(2026, 3, 1)
        val day3 = LocalDate.of(2026, 3, 3)
        val balances = listOf(
            balance("BankA", "1234", "1000", day3.atTime(10, 0))
        )
        val series = WealthPoolCalculator.buildDailySeries(
            balances = balances,
            assets = emptyList(),
            goldPricePerGram = goldPrice,
            silverPricePerGram = silverPrice,
            from = day1,
            to = day3
        )
        assertEquals(3, series.size)
        assertEquals(0, bd("0").compareTo(series[0].cash))
        assertEquals(0, bd("0").compareTo(series[1].cash))
        assertEquals(0, bd("1000").compareTo(series[2].cash))
    }

    @Test
    fun `series combines cash gold silver and other assets`() {
        val day1 = LocalDate.of(2026, 3, 1)
        val balances = listOf(
            balance("BankA", "1234", "6000", day1.atTime(9, 0))
        )
        val assets = listOf(
            asset(ZakatAssetType.GOLD, "10", ZakatAssetUnit.GRAM, 24, acquiredOn = day1),
            asset(ZakatAssetType.SILVER, "100", ZakatAssetUnit.GRAM, acquiredOn = day1),
            asset(ZakatAssetType.PROPERTY, value = "100000", acquiredOn = day1)
        )
        val series = WealthPoolCalculator.buildDailySeries(
            balances, assets, goldPrice, silverPrice, day1, day1
        )
        assertEquals(1, series.size)
        val day = series[0]
        assertEquals(0, bd("6000").compareTo(day.cash))
        assertEquals(0, bd("120000").compareTo(day.gold)) // 10g x 12000
        assertEquals(0, bd("15000").compareTo(day.silver)) // 100g x 150
        assertEquals(0, bd("100000").compareTo(day.otherAssets))
        assertEquals(0, bd("241000").compareTo(day.total))
    }

    // ---------------- Nisab crossing detection ----------------

    private fun seriesOf(vararg points: Pair<LocalDate, String>): List<WealthPoolCalculator.DatedWealth> {
        return points.map { (date, total) ->
            WealthPoolCalculator.DatedWealth(
                date = date,
                cash = BigDecimal.ZERO,
                gold = BigDecimal.ZERO,
                silver = BigDecimal.ZERO,
                otherAssets = BigDecimal.ZERO,
                total = bd(total)
            )
        }
    }

    @Test
    fun `crossing date is when wealth first reached nisab and stayed above`() {
        // Simulated scenario: nisab 59500 (silver), wealth crosses on Mar 10.
        val nisab = bd("59500")
        val mar8 = LocalDate.of(2026, 3, 8)
        val mar9 = LocalDate.of(2026, 3, 9)
        val mar10 = LocalDate.of(2026, 3, 10)
        val mar11 = LocalDate.of(2026, 3, 11)
        val mar12 = LocalDate.of(2026, 3, 12)
        val series = seriesOf(
            mar8 to "50000",
            mar9 to "58000",
            mar10 to "59500", // crossing day: exactly at nisab counts
            mar11 to "60000",
            mar12 to "70000"
        )
        val crossing = WealthPoolCalculator.detectNisabCrossing(series, nisab)
        assertTrue(crossing.currentlyAboveNisab)
        assertEquals(mar10, crossing.activeHawlStart)
        assertEquals(mar10, crossing.firstEverCrossing)
        assertEquals(1, crossing.segments.size)
    }

    @Test
    fun `hawl resets when wealth dips below nisab and restarts at next crossing`() {
        val nisab = bd("59500")
        val jan1 = LocalDate.of(2026, 1, 1)
        val jan5 = LocalDate.of(2026, 1, 5)
        val jan6 = LocalDate.of(2026, 1, 6) // dip day
        val feb1 = LocalDate.of(2026, 2, 1)
        val series = seriesOf(
            jan1 to "60000", // crossed, hawl starts
            jan5 to "60000",
            jan6 to "50000", // dipped below: reset
            feb1 to "65000" // re-cross: new hawl start
        )
        val crossing = WealthPoolCalculator.detectNisabCrossing(series, nisab)
        assertTrue(crossing.currentlyAboveNisab)
        assertEquals(feb1, crossing.activeHawlStart)
        assertEquals(jan1, crossing.firstEverCrossing)
        assertEquals(2, crossing.segments.size)
        assertEquals(jan5, crossing.segments[0].end)
    }

    @Test
    fun `currently below nisab means no active hawl`() {
        val nisab = bd("59500")
        val series = seriesOf(
            LocalDate.of(2026, 1, 1) to "70000",
            LocalDate.of(2026, 1, 2) to "40000"
        )
        val crossing = WealthPoolCalculator.detectNisabCrossing(series, nisab)
        assertFalse(crossing.currentlyAboveNisab)
        assertNull(crossing.activeHawlStart)
        assertNotNull(crossing.firstEverCrossing)
    }

    @Test
    fun `empty history yields no crossings`() {
        val crossing = WealthPoolCalculator.detectNisabCrossing(emptyList(), bd("100"))
        assertFalse(crossing.currentlyAboveNisab)
        assertNull(crossing.activeHawlStart)
        assertNull(crossing.firstEverCrossing)
    }

    // ---------------- Per-asset hawl ----------------

    @Test
    fun `per asset hawl uses acquisition date`() {
        val old = asset(
            ZakatAssetType.GOLD, "10", ZakatAssetUnit.GRAM, 24,
            acquiredOn = LocalDate.now().minusDays(400)
        )
        val recent = asset(
            ZakatAssetType.GOLD, "5", ZakatAssetUnit.GRAM, 24,
            acquiredOn = LocalDate.now().minusDays(30)
        )
        val oldStatus = WealthPoolCalculator.perAssetHawl(old, goldPrice, silverPrice, LocalDate.now())
        val recentStatus = WealthPoolCalculator.perAssetHawl(recent, goldPrice, silverPrice, LocalDate.now())
        assertTrue(oldStatus.hawlComplete)
        assertEquals(0, bd("120000.00").compareTo(oldStatus.value))
        assertEquals(0, bd("3000.00").compareTo(oldStatus.zakatDue)) // 2.5% of 120000
        assertFalse(recentStatus.hawlComplete)
        assertEquals(0, bd("0").compareTo(recentStatus.zakatDue))
        assertNotNull(recentStatus.completionDate)
    }
}
