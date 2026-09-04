package com.ritesh.cashiro.domain.zakat

import com.ritesh.cashiro.data.database.entity.AccountBalanceEntity
import com.ritesh.cashiro.data.database.entity.HoldingIntent
import com.ritesh.cashiro.data.database.entity.PropertyPurpose
import com.ritesh.cashiro.data.database.entity.ZakatAssetEntity
import com.ritesh.cashiro.data.database.entity.ZakatAssetType
import com.ritesh.cashiro.data.database.entity.ZakatAssetUnit
import com.ritesh.cashiro.data.database.entity.ZakatLiabilityEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoUnit

/**
 * Madhhab profile (spec 10.1). The only place a genuine, implementable
 * difference exists today is personal-use jewelry: Hanafi treats ALL
 * gold/silver jewelry as zakatable, while the Shafi'i/Maliki/Hanbali
 * positions exempt jewelry worn for personal use. MAINSTREAM keeps the
 * conservative default (all jewelry zakatable — overpaying is permitted,
 * underpaying is not). Where no meaningful difference exists for a
 * calculation, the setting has no effect.
 */
enum class ZakatMadhhab {
    MAINSTREAM, HANAFI, SHAFII, MALIKI, HANBALI;

    /** Whether personal-use gold/silver jewelry is exempt under this profile. */
    val exemptsPersonalJewelry: Boolean
        get() = this == SHAFII || this == MALIKI || this == HANBALI
}

/**
 * Pure domain layer for the combined zakatable wealth pool (Phase 2b).
 *
 * The pool combines:
 *  (a) current cash balances from the existing Accounts system, pulled
 *      automatically from [AccountBalanceEntity] history — no manual
 *      cash entry required;
 *  (b) current values of all [ZakatAssetEntity] entries — metals valued
 *      at the user-maintained market price per gram, non-metals at their
 *      user-entered value.
 *
 * It also derives, from daily history of the combined total, the date
 * wealth first crossed the applied nisab and stayed above it — the
 * pool-level hawl start date — plus per-asset hawl status driven by each
 * asset's acquisition date.
 *
 * All functions are pure and side-effect free so they can be unit tested
 * without Android dependencies.
 */
object WealthPoolCalculator {

    /** Currency scale used when deriving metal values and totals. */
    private const val SCALE = 2

    /** A daily snapshot of the combined zakatable pool. */
    data class DatedWealth(
        val date: LocalDate,
        val cash: BigDecimal,
        val gold: BigDecimal,
        val silver: BigDecimal,
        val otherAssets: BigDecimal,
        val total: BigDecimal
    )

    /** Category breakdown of the pool at a point in time. */
    data class PoolBreakdown(
        val cash: BigDecimal,
        val gold: BigDecimal,
        val silver: BigDecimal,
        val otherAssets: BigDecimal,
        val total: BigDecimal,
        /** Value of asset entries EXCLUDED by amanat/purpose/madhhab rules. */
        val excluded: BigDecimal = BigDecimal.ZERO,
        /** Near-term deductible debts (spec 2.1). */
        val deductions: BigDecimal = BigDecimal.ZERO,
        /** total − deductions, floored at zero — compared to nisab & taxed. */
        val netWealth: BigDecimal = total
    )

    /**
     * Sum of deductible debt entries due within the next 12 months
     * (spec 2.1): the user records liabilities with due dates; only the
     * near-term portion is deducted from gross zakatable wealth. Long-term
     * debts are handled by entering the coming year's portion as its own
     * liability row.
     */
    fun nearTermDebts(
        liabilities: List<ZakatLiabilityEntity>,
        today: LocalDate = LocalDate.now()
    ): BigDecimal {
        val limit = today.plusMonths(12)
        return liabilities
            .filter { !it.dueDate.isAfter(limit) && it.amount.signum() > 0 }
            .map { it.amount }
            .fold(BigDecimal.ZERO) { acc, a -> acc.add(a) }
            .setScale(SCALE, RoundingMode.HALF_UP)
    }

    /**
     * Inclusion rule for one asset entry, encoding spec sections 1.5, 1.9,
     * 1.10 and 7.1:
     *  - Amanat entries are excluded entirely (7.1);
     *  - PERSONAL entries (personal-use items) are excluded (1.10);
     *  - PROPERTY is included only when purpose == RESALE (1.9);
     *  - INVESTMENT is included only when holdingIntent == TRADING (1.5);
     *    long-term holdings are excluded (flagged informational — no
     *    forced look-through calculation);
     *  - metals marked personal-use are excluded only under madhhabs that
     *    exempt personal jewelry (10.1).
     */
    fun isIncluded(
        asset: ZakatAssetEntity,
        madhhab: ZakatMadhhab = ZakatMadhhab.MAINSTREAM
    ): Boolean {
        if (asset.isAmanat) return false
        val type = runCatching { ZakatAssetType.valueOf(asset.type) }
            .getOrDefault(ZakatAssetType.OTHER)
        return when (type) {
            ZakatAssetType.PERSONAL -> false
            ZakatAssetType.PROPERTY ->
                runCatching { PropertyPurpose.valueOf(asset.purpose.uppercase()) }
                    .getOrDefault(PropertyPurpose.RESALE) == PropertyPurpose.RESALE
            ZakatAssetType.INVESTMENT ->
                runCatching { HoldingIntent.valueOf(asset.holdingIntent.uppercase()) }
                    .getOrDefault(HoldingIntent.TRADING) == HoldingIntent.TRADING
            ZakatAssetType.GOLD, ZakatAssetType.SILVER ->
                !(asset.personalUse && madhhab.exemptsPersonalJewelry)
            else -> true
        }
    }

    /** Result of scanning history for nisab crossings. */
    data class NisabCrossing(
        /**
         * Start of the currently active hawl: the most recent date wealth
         * crossed above nisab and has not dropped below it since. Null
         * when wealth is currently below nisab.
         */
        val activeHawlStart: LocalDate?,
        /** Whether wealth is currently at or above nisab. */
        val currentlyAboveNisab: Boolean,
        /** First date wealth ever reached nisab within the history window. */
        val firstEverCrossing: LocalDate?,
        /** Every detected above-nisab segment, oldest first. */
        val segments: List<Segment>
    ) {
        /** One continuous period during which wealth stayed at/above nisab. */
        data class Segment(val start: LocalDate, val end: LocalDate)
    }

    /** Hawl status of a single asset (per-asset hawl mode). */
    data class AssetHawlStatus(
        val asset: ZakatAssetEntity,
        val value: BigDecimal,
        val hawlComplete: Boolean,
        val daysElapsed: Long,
        val daysInYear: Long,
        val completionDate: LocalDate?,
        val zakatDue: BigDecimal
    )

    // ------------------------------------------------------------------
    // Unit conversion (Bangladeshi jeweller's system, Phase 2a-compatible)
    // ------------------------------------------------------------------

    /** Converts a metal quantity in [unit] units to grams. */
    fun toGrams(quantity: BigDecimal, unit: ZakatAssetUnit): BigDecimal {
        val factor = unit.grams ?: return BigDecimal.ZERO
        return quantity.multiply(factor)
    }

    /**
     * Purity factor for a gold karat grade (24k = 1.0). Computed at 10
     * decimal places so purity-adjusted weights match hand calculation to
     * the paisa (e.g. 1 vori of 22k = 11.664 x 22/24 = exactly 10.692 g
     * pure gold) — an earlier 6-dp scale drifted by a few hundredths on
     * typical jewellery weights.
     */
    fun karatPurity(karat: Int?): BigDecimal {
        if (karat == null || karat <= 0 || karat > 24) return BigDecimal.ONE
        return BigDecimal(karat).divide(BigDecimal(24), 10, RoundingMode.HALF_UP)
    }

    /**
     * Current value of one asset entry.
     *
     * Metals: grams x purity (gold) x current price per gram. Silver uses
     * the silver price with no karat adjustment. Non-metals: the
     * user-entered estimated value. The result is in the asset's own
     * currency; the pool assumes assets are denominated in the base
     * currency (Cashiro is single-base-currency in practice).
     */
    fun assetValue(
        asset: ZakatAssetEntity,
        goldPricePerGram: BigDecimal,
        silverPricePerGram: BigDecimal
    ): BigDecimal {
        val type = runCatching { ZakatAssetType.valueOf(asset.type) }
            .getOrDefault(ZakatAssetType.OTHER)
        if (!type.isMetal) {
            return asset.estimatedValue ?: BigDecimal.ZERO
        }
        val unit = runCatching { ZakatAssetUnit.valueOf(asset.unit) }
            .getOrDefault(ZakatAssetUnit.GRAM)
        val grams = toGrams(asset.quantity, unit)
        return when (type) {
            ZakatAssetType.GOLD ->
                grams.multiply(karatPurity(asset.karat)).multiply(goldPricePerGram)
            ZakatAssetType.SILVER ->
                grams.multiply(silverPricePerGram)
            else -> asset.estimatedValue ?: BigDecimal.ZERO
        }.setScale(SCALE, RoundingMode.HALF_UP)
    }

    // ------------------------------------------------------------------
    // Current breakdown
    // ------------------------------------------------------------------

    /**
     * Latest cash balance per non-credit-card account, floored at zero so
     * overdrafts (which are debts, handled separately in later phases) do
     * not silently reduce the reported cash figure below what is held.
     */
    fun currentCash(latestBalances: List<AccountBalanceEntity>): BigDecimal {
        return latestBalances
            .filter { !it.isCreditCard }
            .map { it.balance.max(BigDecimal.ZERO) }
            .fold(BigDecimal.ZERO) { acc, b -> acc.add(b) }
            .setScale(SCALE, RoundingMode.HALF_UP)
    }

    /** Current values split by pool category. */
    fun currentBreakdown(
        latestBalances: List<AccountBalanceEntity>,
        assets: List<ZakatAssetEntity>,
        goldPricePerGram: BigDecimal,
        silverPricePerGram: BigDecimal
    ): PoolBreakdown = currentBreakdown(
        latestBalances = latestBalances,
        assets = assets,
        goldPricePerGram = goldPricePerGram,
        silverPricePerGram = silverPricePerGram,
        amanatAccountKeys = emptySet(),
        madhhab = ZakatMadhhab.MAINSTREAM,
        liabilities = emptyList()
    )

    /**
     * Full pool breakdown with the spec's inclusion rules and debt
     * deduction: cash excludes Amanat-tagged accounts; assets are filtered
     * through [isIncluded]; near-term liabilities are subtracted to give
     * the NET zakatable wealth that is compared to nisab and taxed (2.2).
     */
    fun currentBreakdown(
        latestBalances: List<AccountBalanceEntity>,
        assets: List<ZakatAssetEntity>,
        goldPricePerGram: BigDecimal,
        silverPricePerGram: BigDecimal,
        amanatAccountKeys: Set<String>,
        madhhab: ZakatMadhhab,
        liabilities: List<ZakatLiabilityEntity>
    ): PoolBreakdown {
        // Cash: same as before, but Amanat-tagged accounts are excluded.
        val cash = latestBalances
            .filter { !it.isCreditCard }
            .filter { "${it.bankName}|${it.accountLast4}" !in amanatAccountKeys }
            .map { it.balance.max(BigDecimal.ZERO) }
            .fold(BigDecimal.ZERO) { acc, b -> acc.add(b) }
            .setScale(SCALE, RoundingMode.HALF_UP)
        // Amanat cash is reported as excluded so the user sees it is
        // deliberately left out of the pool (7.1).
        val amanatCashValue = latestBalances
            .filter { !it.isCreditCard }
            .filter { "${it.bankName}|${it.accountLast4}" in amanatAccountKeys }
            .map { it.balance.max(BigDecimal.ZERO) }
            .fold(BigDecimal.ZERO) { acc, b -> acc.add(b) }
        var gold = BigDecimal.ZERO
        var silver = BigDecimal.ZERO
        var other = BigDecimal.ZERO
        var excluded = amanatCashValue
        for (asset in assets) {
            val value = assetValue(asset, goldPricePerGram, silverPricePerGram)
            if (!isIncluded(asset, madhhab)) {
                excluded = excluded.add(value)
                continue
            }
            when (runCatching { ZakatAssetType.valueOf(asset.type) }
                .getOrDefault(ZakatAssetType.OTHER)) {
                ZakatAssetType.GOLD -> gold = gold.add(value)
                ZakatAssetType.SILVER -> silver = silver.add(value)
                else -> other = other.add(value)
            }
        }
        val total = cash.add(gold).add(silver).add(other)
        val deductions = nearTermDebts(liabilities)
        val net = total.subtract(deductions).max(BigDecimal.ZERO)
        return PoolBreakdown(
            cash = cash,
            gold = gold.setScale(SCALE, RoundingMode.HALF_UP),
            silver = silver.setScale(SCALE, RoundingMode.HALF_UP),
            otherAssets = other.setScale(SCALE, RoundingMode.HALF_UP),
            total = total.setScale(SCALE, RoundingMode.HALF_UP),
            excluded = excluded.setScale(SCALE, RoundingMode.HALF_UP),
            deductions = deductions,
            netWealth = net.setScale(SCALE, RoundingMode.HALF_UP)
        )
    }

    // ------------------------------------------------------------------
    // Daily history
    // ------------------------------------------------------------------

    /**
     * Builds the daily combined-wealth series over [from, to].
     *
     * Cash per day: for each account (bank name + last 4), the latest
     * balance recorded on or before that day, floored at zero, credit
     * cards excluded — derived from the full [AccountBalanceEntity]
     * history, which the Accounts system already maintains on every
     * transaction/balance update.
     *
     * Assets per day: each entry contributes its current value from its
     * acquisition date onward. (Metal values use the current maintained
     * price; historical price drift within the window is out of scope.)
     *
     * Days before any data exists contribute zero.
     */
    fun buildDailySeries(
        balances: List<AccountBalanceEntity>,
        assets: List<ZakatAssetEntity>,
        goldPricePerGram: BigDecimal,
        silverPricePerGram: BigDecimal,
        from: LocalDate,
        to: LocalDate
    ): List<DatedWealth> = buildDailySeries(
        balances = balances,
        assets = assets,
        goldPricePerGram = goldPricePerGram,
        silverPricePerGram = silverPricePerGram,
        from = from,
        to = to,
        amanatAccountKeys = emptySet(),
        madhhab = ZakatMadhhab.MAINSTREAM,
        dailyDeduction = BigDecimal.ZERO
    )

    /**
     * Daily series with the spec's inclusion rules applied. Assets excluded
     * by [isIncluded] never contribute; Amanat accounts never contribute to
     * cash; [dailyDeduction] (the current near-term debt level) is applied
     * uniformly to every day, floored at zero — a documented simplification,
     * since liabilities are recorded with due dates rather than a full
     * history. Hawl resets therefore track the net figure as closely as the
     * recorded data allows (spec 3.4/4.1).
     */
    fun buildDailySeries(
        balances: List<AccountBalanceEntity>,
        assets: List<ZakatAssetEntity>,
        goldPricePerGram: BigDecimal,
        silverPricePerGram: BigDecimal,
        from: LocalDate,
        to: LocalDate,
        amanatAccountKeys: Set<String>,
        madhhab: ZakatMadhhab,
        dailyDeduction: BigDecimal
    ): List<DatedWealth> {
        if (to.isBefore(from)) return emptyList()

        // Group balances per account, sorted ascending by timestamp.
        data class AccountState(var balance: BigDecimal?)

        val accounts = HashMap<Pair<String, String>, AccountState>()
        for (b in balances) {
            if (b.isCreditCard) continue
            if ("${b.bankName}|${b.accountLast4}" in amanatAccountKeys) continue
            val key = b.bankName to b.accountLast4
            accounts.getOrPut(key) { AccountState(null) }
        }
        val sortedBalances = balances
            .filter { !it.isCreditCard }
            .filter { "${it.bankName}|${it.accountLast4}" !in amanatAccountKeys }
            .sortedWith(compareBy({ it.timestamp }, { it.id }))

        // Excluded assets never enter the running totals.
        val includedAssets = assets.filter { isIncluded(it, madhhab) }

        // Assets grouped by acquisition date, values precomputed, so the
        // daily loop advances running totals in O(assets) overall.
        var goldRunning = BigDecimal.ZERO
        var silverRunning = BigDecimal.ZERO
        var otherRunning = BigDecimal.ZERO
        data class Categorized(val type: ZakatAssetType, val value: BigDecimal)

        val contributionsByDate = includedAssets
            .map { asset ->
                Triple(
                    asset.acquisitionDate,
                    runCatching { ZakatAssetType.valueOf(asset.type) }
                        .getOrDefault(ZakatAssetType.OTHER),
                    assetValue(asset, goldPricePerGram, silverPricePerGram)
                )
            }
            .groupBy({ it.first }) { Categorized(it.second, it.third) }
            .toSortedMap()

        val series = ArrayList<DatedWealth>((ChronoUnit.DAYS.between(from, to) + 1).toInt() + 1)
        var balanceIndex = 0
        var day = from
        var lastCash = BigDecimal.ZERO

        while (!day.isAfter(to)) {
            val endOfDay = day.plusDays(1).atStartOfDay()

            // Advance balance history to end of this day.
            while (balanceIndex < sortedBalances.size &&
                sortedBalances[balanceIndex].timestamp.isBefore(endOfDay)
            ) {
                val b = sortedBalances[balanceIndex]
                val key = b.bankName to b.accountLast4
                accounts[key]?.balance = b.balance.max(BigDecimal.ZERO)
                balanceIndex++
            }
            var cash = BigDecimal.ZERO
            for (state in accounts.values) {
                val v = state.balance
                if (v != null) cash = cash.add(v)
            }
            lastCash = cash

            // Advance asset contributions acquired on or before today.
            while (contributionsByDate.isNotEmpty() &&
                contributionsByDate.firstKey() <= day
            ) {
                val date = contributionsByDate.pollFirstEntry()
                for (c in date.value) {
                    when (c.type) {
                        ZakatAssetType.GOLD -> goldRunning = goldRunning.add(c.value)
                        ZakatAssetType.SILVER -> silverRunning = silverRunning.add(c.value)
                        else -> otherRunning = otherRunning.add(c.value)
                    }
                }
            }

            val grossTotal = lastCash.add(goldRunning).add(silverRunning).add(otherRunning)
            val total = if (dailyDeduction.signum() > 0) {
                grossTotal.subtract(dailyDeduction).max(BigDecimal.ZERO)
            } else {
                grossTotal
            }
            series.add(
                DatedWealth(
                    date = day,
                    cash = lastCash.setScale(SCALE, RoundingMode.HALF_UP),
                    gold = goldRunning.setScale(SCALE, RoundingMode.HALF_UP),
                    silver = silverRunning.setScale(SCALE, RoundingMode.HALF_UP),
                    otherAssets = otherRunning.setScale(SCALE, RoundingMode.HALF_UP),
                    total = total.setScale(SCALE, RoundingMode.HALF_UP)
                )
            )
            day = day.plusDays(1)
        }
        return series
    }

    // ------------------------------------------------------------------
    // Nisab-crossing detection
    // ------------------------------------------------------------------

    /**
     * Scans the daily series for nisab crossings.
     *
     * The active hawl start is the beginning of the last continuous run of
     * days at/above nisab. Any day below nisab resets the hawl: it restarts
     * on the next day wealth is back at/above nisab. Days missing from the
     * series (before any data existed) count as zero wealth.
     */
    fun detectNisabCrossing(
        series: List<DatedWealth>,
        nisabValue: BigDecimal
    ): NisabCrossing {
        val segments = ArrayList<NisabCrossing.Segment>()
        var segmentStart: LocalDate? = null
        var firstEver: LocalDate? = null

        for (point in series) {
            val above = point.total >= nisabValue
            if (above) {
                if (segmentStart == null) {
                    segmentStart = point.date
                    if (firstEver == null) firstEver = point.date
                }
            } else if (segmentStart != null) {
                // Day below nisab closes the open segment with the previous day.
                segments.add(NisabCrossing.Segment(segmentStart, point.date.minusDays(1)))
                segmentStart = null
            }
        }
        segmentStart?.let { segments.add(NisabCrossing.Segment(it, series.last().date)) }

        val currentlyAbove = series.lastOrNull()?.let { it.total >= nisabValue } ?: false
        return NisabCrossing(
            activeHawlStart = if (currentlyAbove) segmentStart else null,
            currentlyAboveNisab = currentlyAbove,
            firstEverCrossing = firstEver,
            segments = segments
        )
    }

    // ------------------------------------------------------------------
    // Hawl helpers
    // ------------------------------------------------------------------

    /**
     * Projected hawl-completion date: the Hijrah anniversary of [start],
     * one lunar year later. Falls back to the mean lunar year when the
     * date lies outside the supported Hijrah range — mirroring
     * [ZakatCalculator.hawlStatus].
     */
    fun projectedCompletionDate(start: LocalDate): LocalDate? {
        return try {
            LocalDate.from(HijrahDate.from(start).plus(1, ChronoUnit.YEARS))
        } catch (e: java.time.DateTimeException) {
            null
        }
    }

    /** Per-asset hawl status driven by the asset's acquisition date. */
    fun perAssetHawl(
        asset: ZakatAssetEntity,
        goldPricePerGram: BigDecimal,
        silverPricePerGram: BigDecimal,
        today: LocalDate
    ): AssetHawlStatus {
        val status = ZakatCalculator.hawlStatus(asset.acquisitionDate, today)
        val value = assetValue(asset, goldPricePerGram, silverPricePerGram)
        val due = if (status.complete && value.signum() > 0) {
            value.multiply(ZakatCalculator.ZAKAT_RATE).setScale(SCALE, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO.setScale(SCALE)
        }
        return AssetHawlStatus(
            asset = asset,
            value = value,
            hawlComplete = status.complete,
            daysElapsed = status.daysElapsed,
            daysInYear = status.daysInYear,
            completionDate = if (status.complete) {
                null
            } else {
                projectedCompletionDate(asset.acquisitionDate)
            },
            zakatDue = due
        )
    }
}
