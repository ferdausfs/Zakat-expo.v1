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
 * - Nisab: 85 g of gold OR 595 g of silver, valued at the supplied gram
 *   prices. The caller selects which standard to apply.
 * - Zakatable wealth: cash + gold value + silver value + investments,
 *   minus debts the user owes to others.
 * - Zakat due: 2.5% of net zakatable wealth when the wealth meets or
 *   exceeds the applied nisab AND the hawl (one lunar year) is complete.
 * - Hawl: measured on the Hijrah (Islamic) calendar. The anniversary of
 *   the hawl start date one lunar year later marks completion; the length
 *   of that specific lunar year (354 or 355 days) is reported for progress
 *   display.
 */
object ZakatCalculator {

    /** Zakat rate: 2.5% of net zakatable wealth. */
    val ZAKAT_RATE: BigDecimal = BigDecimal("0.025")

    /** Nisab in grams of pure gold (20 mithqal). */
    const val GOLD_NISAB_GRAMS: Double = 85.0

    /** Nisab in grams of pure silver (200 dirhams). */
    const val SILVER_NISAB_GRAMS: Double = 595.0

    /** Fallback lunar-year length in days (mean Islamic year). */
    const val MEAN_LUNAR_YEAR_DAYS: Long = 355L

    /** Which metal's nisab standard is applied. */
    enum class NisabMethod { GOLD, SILVER }

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
        val zakatDue: BigDecimal
    )

    fun calculate(
        wealth: Wealth,
        prices: MetalPrices,
        method: NisabMethod,
        hawl: Hawl
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

        val status = hawlStatus(hawl.startDate, hawl.today)

        val meetsNisab = netWealth >= appliedNisab
        val eligible = meetsNisab && status.complete
        val zakatDue = if (eligible) {
            netWealth.multiply(ZAKAT_RATE).setScale(2, RoundingMode.HALF_UP)
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
            zakatDue = zakatDue
        )
    }

    /** Nisab value for one metal: grams x price per gram, 2-dp. */
    fun nisabValue(pricePerGram: BigDecimal, grams: Double): BigDecimal {
        return pricePerGram.multiply(BigDecimal.valueOf(grams)).setScale(2, RoundingMode.HALF_UP)
    }

    /**
     * Measures hawl progress on the Hijrah calendar.
     *
     * The hawl completes on the Hijrah anniversary of the start date (one
     * lunar year later). Because lunar years alternate between 354 and 355
     * days, the exact day count of the current hawl year is derived from the
     * calendar itself. Dates outside the representable Hijrah range fall back
     * to the mean lunar-year approximation.
     */
    fun hawlStatus(startDate: LocalDate, today: LocalDate): HawlStatus {
        val effectiveToday = if (today.isBefore(startDate)) startDate else today
        val daysElapsed = ChronoUnit.DAYS.between(startDate, effectiveToday)

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
