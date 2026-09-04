package com.ritesh.cashiro.domain.zakat

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoUnit

/**
 * Pure domain logic for Zakat assessment.
 *
 * All monetary values are expressed in a single currency unit chosen by the
 * caller (the user's base currency, e.g. SAR or BDT). The calculator itself
 * is currency-agnostic: nisab thresholds are derived from gold/silver gram
 * prices supplied in that same currency unit, so the result is correct for
 * any currency without internal conversion.
 *
 * Rules implemented (deliberately minimal, no fiqh extrapolation):
 * - Nisab: 87.48 g of pure gold (20 mithqal) OR 612.36 g of pure silver
 *   (200 dirhams), valued at the supplied gram prices. The caller selects
 *   which standard to apply.
 * - Zakatable wealth: cash + gold value + silver value + investments,
 *   minus deductible debts the user owes to others. The NET figure — not
 *   the gross figure — is compared against nisab and taxed.
 * - Zakat due: 2.5% of net zakatable wealth when the wealth meets or
 *   exceeds the applied nisab AND the hawl is complete.
 * - Hawl: one LUNAR year by default, measured on the Hijrah calendar
 *   (354/355 days). When the user selects the solar-year convenience mode
 *   the hawl length becomes ~365 days AND the rate adjusts to 2.577% so the
 *   effective obligation over time matches the lunar standard — the two
 *   always switch together, never mixed.
 */
object ZakatCalculator {

    /** Lunar-year rate: 2.5% of net zakatable wealth. */
    val ZAKAT_RATE: BigDecimal = BigDecimal("0.025")

    /** Solar-year convenience rate: 2.5% × (354.367/365) ≈ 2.577%. */
    val SOLAR_ZAKAT_RATE: BigDecimal = BigDecimal("0.02577")

    /** Nisab in grams of pure gold (20 mithqal ≈ 87.48 g). */
    const val GOLD_NISAB_GRAMS: Double = 87.48

    /** Nisab in grams of pure silver (200 dirhams ≈ 612.36 g). */
    const val SILVER_NISAB_GRAMS: Double = 612.36

    /** Fallback lunar-year length in days (mean Islamic year). */
    const val MEAN_LUNAR_YEAR_DAYS: Long = 355L

    /** Solar-year hawl length in days (Gregorian year). */
    const val SOLAR_YEAR_DAYS: Long = 365L

    /** Which metal's nisab standard is applied. */
    enum class NisabMethod { GOLD, SILVER }

    /**
     * Calendar convention for the hawl and the rate. LUNAR is the fiqh
     * default (354/355-day hawl, 2.5%); SOLAR is a convenience mode
     * (365-day hawl, 2.577%). They always switch TOGETHER (spec 4.3/8.2).
     */
    enum class CalendarMode(val rate: BigDecimal, val fallbackYearDays: Long) {
        LUNAR(ZAKAT_RATE, MEAN_LUNAR_YEAR_DAYS),
        SOLAR(SOLAR_ZAKAT_RATE, SOLAR_YEAR_DAYS)
    }

    /** Zakatable assets and liabilities, all in the user's currency unit. */
    data class Wealth(
        val cash: BigDecimal,
        val goldGrams: BigDecimal,
        val silverGrams: BigDecimal,
        val investments: BigDecimal,
        /** Debts owed to others; deducted before eligibility check. */
        val debtsOwed: BigDecimal
    )

    /** Spot prices per gram of each metal, in the user's currency unit. */
    data class MetalPrices(
        val goldPerGram: BigDecimal,
        val silverPerGram: BigDecimal
    )

    /** Hawl tracking inputs. */
    data class Hawl(
        /** First day the wealth was held at or above nisab. */
        val startDate: LocalDate,
        /** Reference day used to measure progress (usually today). */
        val today: LocalDate
    )

    /** Hawl progress measured on the Hijrah calendar. */
    data class HawlStatus(
        val complete: Boolean,
        val daysElapsed: Long,
        val daysInYear: Long
    )

    /** Full assessment result; every amount is in the user's currency unit. */
    data class Assessment(
        val goldValue: BigDecimal,
        val silverValue: BigDecimal,
        val totalWealth: BigDecimal,
        val netWealth: BigDecimal,
        val goldNisabValue: BigDecimal,
        val silverNisabValue: BigDecimal,
        val appliedNisabValue: BigDecimal,
        val method: NisabMethod,
        val hawlComplete: Boolean,
        val hawlDaysElapsed: Long,
        val hawlDaysInYear: Long,
        val eligible: Boolean,
        val zakatDue: BigDecimal,
        /** Rate applied (2.5% lunar or 2.577% solar convenience mode). */
        val appliedRate: BigDecimal = ZAKAT_RATE,
        /** Calendar convention used for the hawl. */
        val calendarMode: CalendarMode = CalendarMode.LUNAR
    )

    fun calculate(
        wealth: Wealth,
        prices: MetalPrices,
        method: NisabMethod,
        hawl: Hawl
    ): Assessment = calculate(wealth, prices, method, hawl, CalendarMode.LUNAR)

    fun calculate(
        wealth: Wealth,
        prices: MetalPrices,
        method: NisabMethod,
        hawl: Hawl,
        calendarMode: CalendarMode
    ): Assessment {
        val goldValue = wealth.goldGrams.multiply(prices.goldPerGram)
        val silverValue = wealth.silverGrams.multiply(prices.silverPerGram)
        val totalWealth = wealth.cash.add(goldValue).add(silverValue).add(wealth.investments)
        val netWealth = totalWealth.subtract(wealth.debtsOwed).max(BigDecimal.ZERO)

        val goldNisabValue = nisabValue(prices.goldPerGram, GOLD_NISAB_GRAMS)
        val silverNisabValue = nisabValue(prices.silverPerGram, SILVER_NISAB_GRAMS)
        val appliedNisab = when (method) {
            NisabMethod.GOLD -> goldNisabValue
            NisabMethod.SILVER -> silverNisabValue
        }

        val status = hawlStatus(hawl.startDate, hawl.today, calendarMode)

        val meetsNisab = netWealth >= appliedNisab
        val eligible = meetsNisab && status.complete
        val rate = calendarMode.rate
        val zakatDue = if (eligible) {
            netWealth.multiply(rate).setScale(2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO.setScale(2)
        }

        return Assessment(
            goldValue = goldValue,
            silverValue = silverValue,
            totalWealth = totalWealth,
            netWealth = netWealth,
            goldNisabValue = goldNisabValue,
            silverNisabValue = silverNisabValue,
            appliedNisabValue = appliedNisab,
            method = method,
            hawlComplete = status.complete,
            hawlDaysElapsed = status.daysElapsed,
            hawlDaysInYear = status.daysInYear,
            eligible = eligible,
            zakatDue = zakatDue,
            appliedRate = rate,
            calendarMode = calendarMode
        )
    }

    /** Nisab value for one metal: grams x price per gram, 2-dp. */
    fun nisabValue(pricePerGram: BigDecimal, grams: Double): BigDecimal {
        return pricePerGram.multiply(BigDecimal.valueOf(grams)).setScale(2, RoundingMode.HALF_UP)
    }

    /**
     * Measures hawl progress on the Hijrah calendar (lunar mode).
     *
     * The hawl completes on the Hijrah anniversary of the start date (one
     * lunar year later). Because lunar years alternate between 354 and 355
     * days, the exact day count of the current hawl year is derived from the
     * calendar itself. Dates outside the representable Hijrah range fall back
     * to the mean lunar-year approximation.
     *
     * In SOLAR mode the hawl is a fixed 365-day Gregorian year — the paired
     * convenience rate (2.577%) is applied by the caller.
     */
    fun hawlStatus(startDate: LocalDate, today: LocalDate): HawlStatus =
        hawlStatus(startDate, today, CalendarMode.LUNAR)

    fun hawlStatus(startDate: LocalDate, today: LocalDate, mode: CalendarMode): HawlStatus {
        val effectiveToday = if (today.isBefore(startDate)) startDate else today
        val daysElapsed = ChronoUnit.DAYS.between(startDate, effectiveToday)

        if (mode == CalendarMode.SOLAR) {
            return HawlStatus(
                complete = daysElapsed >= SOLAR_YEAR_DAYS,
                daysElapsed = daysElapsed,
                daysInYear = SOLAR_YEAR_DAYS
            )
        }

        return try {
            val startHijri = HijrahDate.from(startDate)
            val anniversaryHijri = startHijri.plus(1, ChronoUnit.YEARS)
            val anniversary = LocalDate.from(anniversaryHijri)
            val daysInYear = ChronoUnit.DAYS.between(startDate, anniversary)
            HawlStatus(
                complete = !effectiveToday.isBefore(anniversary),
                daysElapsed = daysElapsed,
                daysInYear = daysInYear
            )
        } catch (e: java.time.DateTimeException) {
            // Start date outside the Hijrah-chronology supported range:
            // approximate with the mean lunar year instead of failing.
            HawlStatus(
                complete = daysElapsed >= MEAN_LUNAR_YEAR_DAYS,
                daysElapsed = daysElapsed,
                daysInYear = MEAN_LUNAR_YEAR_DAYS
            )
        }
    }
}
