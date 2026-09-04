package com.ritesh.cashiro.domain.zakat

import com.ritesh.cashiro.data.database.entity.FitrEntryEntity
import com.ritesh.cashiro.data.database.entity.HoldingIntent
import com.ritesh.cashiro.data.database.entity.LivestockAnimalType
import com.ritesh.cashiro.data.database.entity.LivestockEntryEntity
import com.ritesh.cashiro.data.database.entity.PropertyPurpose
import com.ritesh.cashiro.data.database.entity.UshrEntryEntity
import com.ritesh.cashiro.data.database.entity.UshrIrrigationType
import com.ritesh.cashiro.data.database.entity.ZakatAssetEntity
import com.ritesh.cashiro.data.database.entity.ZakatAssetType
import com.ritesh.cashiro.data.database.entity.ZakatLiabilityEntity
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Worked examples from the Zakat A-Z master specification, each verified
 * against manual fiqh arithmetic. Every test prints the same trail a user
 * sees in the app's transparent breakdown (spec 11.1).
 */
class ZakatMasterSpecWorkedExamplesTest {

    // ---------------------------------------------------------------
    // Worked example 1 — Nisab/Hawl with debt deduction (Sections 1-4)
    //
    // Manual fiqh math:
    //   Cash                                  50,000.00
    //   Gold 100 g of 22k @ 80/g  (100 x 22/24 x 80) = 7,333.33
    //   Silver 200 g @ 1/g                            200.00
    //   Trading investment (market value)          10,000.00
    //   Receivable, expected to be collected        5,000.00
    //   Personal residence (1.9)  — EXCLUDED
    //   Rental property (1.9)     — EXCLUDED
    //   Amanat deposit (7.1)      — EXCLUDED
    //   ----------------------------------------------------------
    //   Gross zakatable wealth                  = 72,533.33
    //   Less: electricity bill due in 3 months   = -2,000.00
    //   Less: this-year mortgage portion         = -1,200.00
    //   (2-year loan due 730 days out: NOT deductible)
    //   ----------------------------------------------------------
    //   NET zakatable wealth                    = 69,333.33
    //   Silver nisab @ 1/g = 612.36 g           =    612.36  → net ≥ nisab
    //   Hawl complete (started 2 years ago)
    //   Zakat due = 69,333.33 x 2.5%            =  1,733.33
    // ---------------------------------------------------------------

    private val prices = ZakatCalculator.MetalPrices(
        goldPerGram = BigDecimal("80"),
        silverPerGram = BigDecimal("1")
    )

    private fun assetsForExample1(): List<ZakatAssetEntity> = listOf(
        ZakatAssetEntity(
            id = 1, type = ZakatAssetType.GOLD.name, name = "Gold jewellery",
            quantity = BigDecimal("100"), unit = "GRAM", karat = 22
        ),
        ZakatAssetEntity(
            id = 2, type = ZakatAssetType.SILVER.name, name = "Silver bars",
            quantity = BigDecimal("200"), unit = "GRAM"
        ),
        ZakatAssetEntity(
            id = 3, type = ZakatAssetType.INVESTMENT.name, name = "Trading shares",
            estimatedValue = BigDecimal("10000"),
            holdingIntent = HoldingIntent.TRADING.name
        ),
        ZakatAssetEntity(
            id = 4, type = ZakatAssetType.PROPERTY.name, name = "Family home",
            estimatedValue = BigDecimal("500000"),
            purpose = PropertyPurpose.PERSONAL.name
        ),
        ZakatAssetEntity(
            id = 5, type = ZakatAssetType.PROPERTY.name, name = "Rental flat",
            estimatedValue = BigDecimal("300000"),
            purpose = PropertyPurpose.RENTAL.name
        ),
        ZakatAssetEntity(
            id = 6, type = ZakatAssetType.RECEIVABLE.name, name = "Loan to friend",
            estimatedValue = BigDecimal("5000")
        ),
        ZakatAssetEntity(
            id = 7, type = ZakatAssetType.OTHER.name, name = "Neighbour's deposit",
            estimatedValue = BigDecimal("20000"),
            isAmanat = true
        ),
        ZakatAssetEntity(
            id = 8, type = ZakatAssetType.PERSONAL.name, name = "Family car",
            estimatedValue = BigDecimal("15000")
        )
    )

    private fun liabilitiesForExample1(): List<ZakatLiabilityEntity> = listOf(
        ZakatLiabilityEntity(
            id = 1, name = "Electricity bill", amount = BigDecimal("2000"),
            dueDate = LocalDate.now().plusMonths(3)
        ),
        ZakatLiabilityEntity(
            id = 2, name = "Mortgage instalments due this year",
            amount = BigDecimal("1200"), dueDate = LocalDate.now().plusMonths(6)
        ),
        ZakatLiabilityEntity(
            id = 3, name = "Personal loan (due in 2 years)",
            amount = BigDecimal("5000"), dueDate = LocalDate.now().plusMonths(26)
        )
    )

    @Test
    fun `example 1 - net zakatable wealth and zakat due match manual fiqh math`() {
        val breakdown = WealthPoolCalculator.currentBreakdown(
            latestBalances = listOf(
                com.ritesh.cashiro.data.database.entity.AccountBalanceEntity(
                    bankName = "Al Rajhi", accountLast4 = "0001",
                    balance = BigDecimal("50000"),
                    timestamp = java.time.LocalDateTime.now()
                )
            ),
            assets = assetsForExample1(),
            goldPricePerGram = prices.goldPerGram,
            silverPricePerGram = prices.silverPerGram,
            amanatAccountKeys = emptySet(),
            madhhab = ZakatMadhhab.MAINSTREAM,
            liabilities = liabilitiesForExample1()
        )

        // Gold: 100 g x (22/24) x 80 = 7,333.33
        // Cash pulled automatically from the account: 50,000
        assertEquals(0, breakdown.cash.compareTo(BigDecimal("50000.00")))
        assertEquals(0, breakdown.gold.compareTo(BigDecimal("7333.33")))
        // Silver: 200 g x 1 = 200
        assertEquals(0, breakdown.silver.compareTo(BigDecimal("200.00")))
        // Gross: 50,000 cash + 7,333.33 + 200 + 10,000 + 5,000 receivable
        assertEquals(0, breakdown.total.compareTo(BigDecimal("72533.33")))
        // Excluded: home 500,000 + rental 300,000 + amanat 20,000 + car 15,000
        assertEquals(0, breakdown.excluded.compareTo(BigDecimal("835000.00")))
        // Near-term debts: 2,000 + 1,200 (the 2-year loan is NOT deducted)
        assertEquals(0, breakdown.deductions.compareTo(BigDecimal("3200.00")))
        // Net: 69,333.33
        assertEquals(0, breakdown.netWealth.compareTo(BigDecimal("69333.33")))

        // Eligibility with a completed hawl: 69,333.33 x 2.5% = 1,733.33
        val hawlStart = LocalDate.now().minusDays(400)
        val status = ZakatCalculator.hawlStatus(hawlStart, LocalDate.now())
        assertTrue(status.complete)
        val due = breakdown.netWealth.multiply(ZakatCalculator.ZAKAT_RATE)
            .setScale(2, java.math.RoundingMode.HALF_UP)
        assertEquals(0, due.compareTo(BigDecimal("1733.33")))
    }

    @Test
    fun `example 2 - solar-year mode switches rate AND hawl length together`() {
        // Same net wealth; solar convenience mode: 69,333.33 x 2.577%.
        // Manual math: 69,333.33 x 0.02577 = 1,786.71991... -> 1,786.72
        val net = BigDecimal("69333.33")
        val solarDue = net.multiply(ZakatCalculator.CalendarMode.SOLAR.rate)
            .setScale(2, java.math.RoundingMode.HALF_UP)
        assertEquals(0, solarDue.compareTo(BigDecimal("1786.72")))

        // Hawl length in solar mode is exactly 365 days.
        val start = LocalDate.of(2025, 1, 1)
        val status364 = ZakatCalculator.hawlStatus(
            start, start.plusDays(364), ZakatCalculator.CalendarMode.SOLAR
        )
        val status365 = ZakatCalculator.hawlStatus(
            start, start.plusDays(365), ZakatCalculator.CalendarMode.SOLAR
        )
        assertFalse(status364.complete)
        assertTrue(status365.complete)
        assertEquals(ZakatCalculator.SOLAR_YEAR_DAYS, status365.daysInYear)

        // Never mixed: lunar mode still uses the Hijrah anniversary, not 365d.
        val lunarStatus = ZakatCalculator.hawlStatus(
            start, start.plusDays(365), ZakatCalculator.CalendarMode.LUNAR
        )
        assertTrue(lunarStatus.daysInYear == 354L || lunarStatus.daysInYear == 355L)
    }

    @Test
    fun `example 3 - ushr matches manual fiqh math for all irrigation types`() {
        // 1,000 kg wheat worth 3,000: above the 720 kg (5 wasq) threshold.
        val value = BigDecimal("3000")
        val natural = UshrCalculator.calculate(
            BigDecimal("1000"), value, UshrIrrigationType.NATURAL.name
        )
        assertEquals(0, natural.ushrDue.compareTo(BigDecimal("300.00"))) // 10%

        val artificial = UshrCalculator.calculate(
            BigDecimal("1000"), value, UshrIrrigationType.ARTIFICIAL.name
        )
        assertEquals(0, artificial.ushrDue.compareTo(BigDecimal("150.00"))) // 5%

        val mixed = UshrCalculator.calculate(
            BigDecimal("1000"), value, UshrIrrigationType.MIXED.name
        )
        assertEquals(0, mixed.ushrDue.compareTo(BigDecimal("225.00"))) // 7.5%

        // Below the threshold: 500 kg -> nothing due, even at 10%.
        val below = UshrCalculator.calculate(
            BigDecimal("500"), value, UshrIrrigationType.NATURAL.name
        )
        assertFalse(below.thresholdMet)
        assertEquals(0, below.ushrDue.compareTo(BigDecimal.ZERO))

        // Exactly at the threshold: 720 kg is due.
        val exact = UshrCalculator.calculate(
            BigDecimal("720"), value, UshrIrrigationType.NATURAL.name
        )
        assertTrue(exact.thresholdMet)
    }

    @Test
    fun `example 4 - sheep table matches classical brackets`() {
        // 39 sheep -> nothing; 40-120 -> 1; 121-200 -> 2; 201-399 -> 3;
        // 400+ -> one per full 100.
        assertFalse(LivestockCalculator.sheepDue(39).nisabMet)
        assertEquals(1, LivestockCalculator.sheepDue(40).sheepDue)
        assertEquals(1, LivestockCalculator.sheepDue(120).sheepDue)
        assertEquals(2, LivestockCalculator.sheepDue(121).sheepDue)
        assertEquals(2, LivestockCalculator.sheepDue(200).sheepDue)
        assertEquals(3, LivestockCalculator.sheepDue(201).sheepDue)
        assertEquals(3, LivestockCalculator.sheepDue(399).sheepDue)
        assertEquals(4, LivestockCalculator.sheepDue(400).sheepDue)
        assertEquals(4, LivestockCalculator.sheepDue(499).sheepDue)
        assertEquals(5, LivestockCalculator.sheepDue(500).sheepDue)
    }

    @Test
    fun `example 5 - camel table matches classical brackets`() {
        assertFalse(LivestockCalculator.camelDue(4).nisabMet)
        assertEquals(1, LivestockCalculator.camelDue(5).sheepDue)
        assertEquals(4, LivestockCalculator.camelDue(24).sheepDue)
        assertEquals(1, LivestockCalculator.camelDue(30).bintMakhadDue)
        assertEquals(1, LivestockCalculator.camelDue(40).bintLabunDue)
        assertEquals(1, LivestockCalculator.camelDue(50).hiqqahDue)
        assertEquals(1, LivestockCalculator.camelDue(70).jadhaahDue)
        assertEquals(2, LivestockCalculator.camelDue(85).bintLabunDue)
        assertEquals(2, LivestockCalculator.camelDue(100).hiqqahDue)
        // 121: classical continuation maximises coverage 40a+50b <= 121
        // -> 40x3 = 120 -> 3 bint labun.
        assertEquals(3, LivestockCalculator.camelDue(121).bintLabunDue)
        // 130: 40x2 + 50 = 130 -> 2 bint labun + 1 hiqqah.
        assertEquals(2, LivestockCalculator.camelDue(130).bintLabunDue)
        assertEquals(1, LivestockCalculator.camelDue(130).hiqqahDue)
        // 150: 50x3 -> 3 hiqqah.
        assertEquals(3, LivestockCalculator.camelDue(150).hiqqahDue)
    }

    @Test
    fun `example 6 - cattle table matches classical brackets`() {
        assertFalse(LivestockCalculator.cattleDue(29).nisabMet)
        assertEquals(1, LivestockCalculator.cattleDue(30).tabiDue)
        assertEquals(1, LivestockCalculator.cattleDue(40).musinnahDue)
        assertEquals(1, LivestockCalculator.cattleDue(59).musinnahDue)
        assertEquals(2, LivestockCalculator.cattleDue(60).tabiDue)
        assertEquals(1, LivestockCalculator.cattleDue(75).tabiDue)
        assertEquals(1, LivestockCalculator.cattleDue(75).musinnahDue)
        assertEquals(2, LivestockCalculator.cattleDue(85).musinnahDue)
        assertEquals(3, LivestockCalculator.cattleDue(90).tabiDue)
        // 130: 30x3 + 40 = 130 -> 3 tabi' + 1 musinnah.
        assertEquals(3, LivestockCalculator.cattleDue(130).tabiDue)
        assertEquals(1, LivestockCalculator.cattleDue(130).musinnahDue)
    }

    @Test
    fun `example 7 - commercial livestock is redirected to trade goods`() {
        val commercial = LivestockEntryEntity(
            name = "Feedlot cattle", animalType = LivestockAnimalType.CATTLE.name,
            count = 100, isGrazing = false
        )
        val result = LivestockCalculator.calculate(commercial)
        assertFalse(result.nisabMet)
        assertTrue(result.description.contains("business"))
    }

    @Test
    fun `example 8 - zakatul fitr matches manual math`() {
        // Rice at 6/kg, 2.5 kg per person, 5 household members:
        // per person 15.00; total 75.00.
        val entry = FitrEntryEntity(
            yearLabel = "1447", stapleName = "Rice",
            pricePerKg = BigDecimal("6"), kgPerPerson = BigDecimal("2.5"),
            householdCount = 5
        )
        val result = ZakatulFitrCalculator.calculate(entry)
        assertEquals(0, result.amountPerPerson.compareTo(BigDecimal("15.00")))
        assertEquals(0, result.totalDue.compareTo(BigDecimal("75.00")))

        // Cash value at 3 kg/person: 6 x 3 x 5 = 90.00.
        val entry3kg = entry.copy(kgPerPerson = BigDecimal("3"))
        assertEquals(
            0,
            ZakatulFitrCalculator.calculate(entry3kg).totalDue
                .compareTo(BigDecimal("90.00"))
        )

        // Independent of wealth/hawl: no nisab input exists at all, and the
        // obligation scales with the household member count entered.
        assertEquals(5, result.householdCount)
    }

    @Test
    fun `example 9 - amanat and madhhab rules drive pool inclusion`() {
        val jewelry = ZakatAssetEntity(
            type = ZakatAssetType.GOLD.name, quantity = BigDecimal("50"),
            unit = "GRAM", karat = 22, personalUse = true
        )
        // Hanafi: personal-use jewelry IS zakatable.
        assertTrue(WealthPoolCalculator.isIncluded(jewelry, ZakatMadhhab.HANAFI))
        assertTrue(WealthPoolCalculator.isIncluded(jewelry, ZakatMadhhab.MAINSTREAM))
        // Shafi'i/Maliki/Hanbali: personal-use jewelry is exempt.
        assertFalse(WealthPoolCalculator.isIncluded(jewelry, ZakatMadhhab.SHAFII))
        assertFalse(WealthPoolCalculator.isIncluded(jewelry, ZakatMadhhab.MALIKI))
        assertFalse(WealthPoolCalculator.isIncluded(jewelry, ZakatMadhhab.HANBALI))
        // But investment/holding jewelry (not personal use) is always in.
        assertTrue(
            WealthPoolCalculator.isIncluded(jewelry.copy(personalUse = false), ZakatMadhhab.SHAFII)
        )

        // Amanat entries excluded under every profile.
        val amanat = ZakatAssetEntity(type = ZakatAssetType.OTHER.name, isAmanat = true)
        for (m in ZakatMadhhab.entries) {
            assertFalse(WealthPoolCalculator.isIncluded(amanat, m))
        }

        // PERSONAL entries always excluded.
        val personal = ZakatAssetEntity(type = ZakatAssetType.PERSONAL.name)
        assertFalse(WealthPoolCalculator.isIncluded(personal, ZakatMadhhab.MAINSTREAM))
    }

    @Test
    fun `example 10 - near-term debt window is 12 months`() {
        val today = LocalDate.of(2026, 1, 15)
        fun liability(monthsOut: Long) = ZakatLiabilityEntity(
            amount = BigDecimal("100"), dueDate = today.plusMonths(monthsOut)
        )
        val debts = WealthPoolCalculator.nearTermDebts(
            listOf(liability(1), liability(12), liability(13), liability(24)),
            today
        )
        // 11..12-month-out debts are deducted; 13+ months are not.
        assertEquals(0, debts.compareTo(BigDecimal("200.00")))
    }

    @Test
    fun `example 11 - pool hawl resets when net wealth falls below nisab`() {
        // Cash history: 700 for 30 days (above 612.36 silver nisab at 1/g),
        // then 500 for 30 days (below), then 700 again for the rest.
        val today = LocalDate.now()
        fun balance(day: Long, amount: Long) =
            com.ritesh.cashiro.data.database.entity.AccountBalanceEntity(
                bankName = "Bank", accountLast4 = "0001",
                balance = BigDecimal(amount),
                timestamp = today.minusDays(90 - day).atStartOfDay()
            )

        val balances = (0..29L).map { balance(it, 700) } +
            (30..59L).map { balance(it, 500) } +
            (60..90L).map { balance(it, 700) }

        val series = WealthPoolCalculator.buildDailySeries(
            balances = balances,
            assets = emptyList(),
            goldPricePerGram = BigDecimal.ONE,
            silverPricePerGram = BigDecimal.ONE,
            from = today.minusDays(90),
            to = today
        )
        val crossing = WealthPoolCalculator.detectNisabCrossing(
            series, BigDecimal("612.36")
        )
        // The dip below nisab resets the hawl: the active start is the day
        // wealth came back above, NOT the first crossing ever.
        assertTrue(crossing.currentlyAboveNisab)
        assertTrue(crossing.firstEverCrossing!! < crossing.activeHawlStart!!)
        assertEquals(today.minusDays(30), crossing.activeHawlStart)
    }

    @Test
    fun `example 12 - deductions push wealth below nisab and block zakat`() {
        // Gross 700 above nisab; a 400 debt due within 12 months drops the
        // net to 300 < 612.36 -> netAboveNisab false -> no zakat even with
        // a complete hawl.
        val breakdown = WealthPoolCalculator.currentBreakdown(
            latestBalances = listOf(
                com.ritesh.cashiro.data.database.entity.AccountBalanceEntity(
                    bankName = "Bank", accountLast4 = "0001",
                    balance = BigDecimal("700"),
                    timestamp = java.time.LocalDateTime.now()
                )
            ),
            assets = emptyList(),
            goldPricePerGram = BigDecimal.ONE,
            silverPricePerGram = BigDecimal.ONE,
            amanatAccountKeys = emptySet(),
            madhhab = ZakatMadhhab.MAINSTREAM,
            liabilities = listOf(
                ZakatLiabilityEntity(amount = BigDecimal("400"), dueDate = LocalDate.now())
            )
        )
        assertEquals(0, breakdown.netWealth.compareTo(BigDecimal("300.00")))
        assertFalse(breakdown.netWealth >= BigDecimal("612.36"))
    }
}
