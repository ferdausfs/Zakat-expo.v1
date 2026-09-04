package com.ritesh.cashiro.presentation.ui.features.zakat.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ritesh.cashiro.data.database.entity.AccountBalanceEntity
import com.ritesh.cashiro.data.database.entity.ZakatAssetEntity
import com.ritesh.cashiro.data.model.Currency
import com.ritesh.cashiro.data.preferences.UserPreferencesRepository
import com.ritesh.cashiro.data.repository.ZakatRepository
import com.ritesh.cashiro.domain.zakat.WealthPoolCalculator
import com.ritesh.cashiro.domain.zakat.ZakatCalculator
import com.ritesh.cashiro.domain.zakat.ZakatMadhhab
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Zakat dashboard (Phase 2b).
 *
 * Combines, automatically and without manual re-entry:
 *  - cash balances from the existing Accounts system (full balance
 *    history, for nisab-crossing detection),
 *  - all zakat asset entries (metals valued at the user-maintained
 *    market price; non-metals at their entered value),
 *  - zakat settings (nisab method, metal prices, hawl mode, base
 *    currency) from preferences.
 *
 * The pool history is rebuilt on every upstream change, so the
 * nisab-crossing date, hawl progress and projected completion date are
 * always derived from the latest data.
 */
@HiltViewModel
class ZakatDashboardViewModel @Inject constructor(
    private val zakatRepository: ZakatRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    enum class HawlMode { POOL, PER_ASSET }

    /** One line of the transparent zakat-due derivation. */
    data class DueLine(
        val label: String,
        val amount: BigDecimal,
        val share: BigDecimal
    )

    data class UiState(
        val currencyCode: String = Currency.DEFAULT_CURRENCY_CODE,
        val loading: Boolean = true,
        val breakdown: WealthPoolCalculator.PoolBreakdown =
            WealthPoolCalculator.PoolBreakdown(
                cash = BigDecimal.ZERO,
                gold = BigDecimal.ZERO,
                silver = BigDecimal.ZERO,
                otherAssets = BigDecimal.ZERO,
                total = BigDecimal.ZERO
            ),
        val nisabMethod: ZakatCalculator.NisabMethod = ZakatCalculator.NisabMethod.SILVER,
        val appliedNisabValue: BigDecimal = BigDecimal.ZERO,
        val goldNisabValue: BigDecimal = BigDecimal.ZERO,
        val silverNisabValue: BigDecimal = BigDecimal.ZERO,
        val goldPricePerGram: String = "",
        val silverPricePerGram: String = "",
        val aboveNisab: Boolean = false,
        /** Auto-detected start of the current hawl (pool mode); null when below nisab. */
        val crossingDate: LocalDate? = null,
        /** First date wealth ever reached nisab inside the history window. */
        val firstEverCrossingDate: LocalDate? = null,
        val hawlComplete: Boolean = false,
        val hawlDaysElapsed: Long = 0,
        val hawlDaysInYear: Long = 0,
        val projectedCompletionDate: LocalDate? = null,
        val hawlMode: HawlMode = HawlMode.POOL,
        val perAssetStatuses: List<WealthPoolCalculator.AssetHawlStatus> = emptyList(),
        val cashHawlStart: LocalDate? = null,
        val dueLines: List<DueLine> = emptyList(),
        val zakatDue: BigDecimal = BigDecimal.ZERO,
        val hasAnyData: Boolean = false,
        /** Calendar convention: LUNAR (2.5%, Hijrah hawl) or SOLAR (2.577%, 365d). */
        val calendarMode: ZakatCalculator.CalendarMode = ZakatCalculator.CalendarMode.LUNAR,
        /** Madhhab profile (affects personal-use jewelry handling). */
        val madhhab: ZakatMadhhab = ZakatMadhhab.MAINSTREAM,
        /** Rate applied this assessment: 2.5% or 2.577%. */
        val appliedRate: BigDecimal = ZakatCalculator.ZAKAT_RATE,
        /** Near-term deductible debts (gross − deductions = net, spec 2.2). */
        val deductions: BigDecimal = BigDecimal.ZERO,
        /** Accounts tagged as holding Amanat money (excluded from pool). */
        val amanatAccountKeys: Set<String> = emptySet(),
        /** All known accounts, so Amanat tagging UI can list them. */
        val knownAccounts: List<Pair<String, String>> = emptyList()
    )

    private data class PoolInputs(
        val balances: List<AccountBalanceEntity>,
        val assets: List<ZakatAssetEntity>,
        val baseCurrency: String,
        val nisabMethod: String,
        val liabilities: List<com.ritesh.cashiro.data.database.entity.ZakatLiabilityEntity>
    )

    private data class PriceInputs(
        val goldPrice: String,
        val silverPrice: String,
        val hawlMode: String,
        val calendarMode: String,
        val madhhab: String,
        val amanatAccounts: Set<String>
    )

    private val combined = combine(
        combine(
            zakatRepository.observeAllBalanceHistory(),
            zakatRepository.observeAssets(),
            userPreferencesRepository.baseCurrency,
            userPreferencesRepository.zakatNisabMethod,
            zakatRepository.observeLiabilities()
        ) { balances, assets, baseCurrency, nisabMethod, liabilities ->
            PoolInputs(balances, assets, baseCurrency, nisabMethod, liabilities)
        },
        combine(
            userPreferencesRepository.zakatGoldPricePerGram,
            userPreferencesRepository.zakatSilverPricePerGram,
            userPreferencesRepository.zakatHawlMode,
            userPreferencesRepository.zakatCalendarMode,
            userPreferencesRepository.zakatAmanatAccounts
        ) { goldPrice, silverPrice, hawlMode, calendarMode, amanatAccounts ->
            PriceInputs(goldPrice, silverPrice, hawlMode, calendarMode, "", amanatAccounts)
        },
        userPreferencesRepository.zakatMadhhab
    ) { inputs, prices, madhhabRaw ->
        buildState(
            inputs.balances, inputs.assets, inputs.baseCurrency,
            parseMethod(inputs.nisabMethod), prices.goldPrice, prices.silverPrice,
            parseMode(prices.hawlMode), inputs.liabilities,
            parseCalendar(prices.calendarMode),
            parseMadhhab(madhhabRaw),
            prices.amanatAccounts
        )
    }

    val uiState: StateFlow<UiState> = combined
        // The wealth-history rebuild (up to 730 daily points) and nisab
        // scanning inside buildState are CPU-bound; keep them off Main.
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState()
        )

    // ------------- Settings mutations (shared with the calculator) -------------

    fun setNisabMethod(method: ZakatCalculator.NisabMethod) {
        viewModelScope.launch {
            userPreferencesRepository.setZakatNisabMethod(method.name)
        }
    }

    fun setHawlMode(mode: HawlMode) {
        viewModelScope.launch {
            userPreferencesRepository.setZakatHawlMode(mode.name)
        }
    }

    /** LUNAR/SOLAR — rate and hawl length switch together (spec 4.3/8.2). */
    fun setCalendarMode(mode: ZakatCalculator.CalendarMode) {
        viewModelScope.launch {
            userPreferencesRepository.setZakatCalendarMode(mode.name)
        }
    }

    fun setMadhhab(madhhab: ZakatMadhhab) {
        viewModelScope.launch {
            userPreferencesRepository.setZakatMadhhab(madhhab.name)
        }
    }

    fun toggleAmanatAccount(key: String) {
        viewModelScope.launch {
            val current = userPreferencesRepository.zakatAmanatAccounts.first()
            val updated = if (key in current) current - key else current + key
            userPreferencesRepository.setZakatAmanatAccounts(updated)
        }
    }

    // ----------------------------- Derivation ------------------------------

    private fun buildState(
        balances: List<AccountBalanceEntity>,
        assets: List<ZakatAssetEntity>,
        baseCurrency: String,
        method: ZakatCalculator.NisabMethod,
        goldPriceRaw: String,
        silverPriceRaw: String,
        hawlMode: HawlMode,
        liabilities: List<com.ritesh.cashiro.data.database.entity.ZakatLiabilityEntity>,
        calendarMode: ZakatCalculator.CalendarMode,
        madhhab: ZakatMadhhab,
        amanatAccountKeys: Set<String>
    ): UiState {
        val goldPrice = parseAmount(goldPriceRaw)
        val silverPrice = parseAmount(silverPriceRaw)
        val today = LocalDate.now()

        val breakdown = WealthPoolCalculator.currentBreakdown(
            latestBalances = latestPerAccount(balances),
            assets = assets,
            goldPricePerGram = goldPrice,
            silverPricePerGram = silverPrice,
            amanatAccountKeys = amanatAccountKeys,
            madhhab = madhhab,
            liabilities = liabilities
        )

        val goldNisab = ZakatCalculator.nisabValue(
            goldPrice.coerceAtLeast(BigDecimal.ONE), ZakatCalculator.GOLD_NISAB_GRAMS
        )
        val silverNisab = ZakatCalculator.nisabValue(
            silverPrice.coerceAtLeast(BigDecimal.ONE), ZakatCalculator.SILVER_NISAB_GRAMS
        )
        val appliedNisab = when (method) {
            ZakatCalculator.NisabMethod.GOLD -> goldNisab
            ZakatCalculator.NisabMethod.SILVER -> silverNisab
        }

        // Daily series window: from the earliest data point (capped at
        // two years back, comfortably more than one lunar year) to today.
        val earliest = listOfNotNull(
            balances.minOfOrNull { it.timestamp.toLocalDate() },
            assets.minOfOrNull { it.acquisitionDate }
        ).minOrNull()
        val windowStart = earliest
            ?.coerceAtLeast(today.minusDays(HISTORY_WINDOW_DAYS))
            ?.coerceAtMost(today) ?: today.minusDays(HISTORY_WINDOW_DAYS)

        val series = WealthPoolCalculator.buildDailySeries(
            balances = balances,
            assets = assets,
            goldPricePerGram = goldPrice,
            silverPricePerGram = silverPrice,
            from = windowStart,
            to = today,
            amanatAccountKeys = amanatAccountKeys,
            madhhab = madhhab,
            dailyDeduction = breakdown.deductions
        )
        val crossing = WealthPoolCalculator.detectNisabCrossing(series, appliedNisab)

        val hawlStart = crossing.activeHawlStart
        val status = hawlStart?.let { ZakatCalculator.hawlStatus(it, today, calendarMode) }
        val projected = hawlStart?.let {
            if (calendarMode == ZakatCalculator.CalendarMode.SOLAR) {
                it.plusDays(ZakatCalculator.SOLAR_YEAR_DAYS)
            } else {
                WealthPoolCalculator.projectedCompletionDate(it)
            }
        }

        // NET zakatable wealth (spec 2.2) is what is compared to nisab and
        // taxed. Eligibility: currently at/above nisab on the net series,
        // hawl complete, and net wealth ≥ applied nisab right now.
        val netAboveNisab = breakdown.netWealth >= appliedNisab
        val rate = calendarMode.rate
        val eligible = crossing.currentlyAboveNisab &&
            (status?.complete == true) && netAboveNisab

        // Transparent derivation (spec 11.1): every included category, the
        // deduction, and the net figure the rate is applied to.
        val dueLines = if (eligible) {
            val lines = mutableListOf(
                DueLine("cash", breakdown.cash, share(breakdown.cash, rate)),
                DueLine("gold", breakdown.gold, share(breakdown.gold, rate)),
                DueLine("silver", breakdown.silver, share(breakdown.silver, rate)),
                DueLine("other", breakdown.otherAssets, share(breakdown.otherAssets, rate)),
                DueLine("deductions", breakdown.deductions.negate(),
                    share(breakdown.deductions, rate).negate()),
                DueLine("net", breakdown.netWealth, breakdown.netWealth.multiply(rate)
                    .setScale(2, RoundingMode.HALF_UP))
            )
            lines
        } else {
            emptyList()
        }
        val totalDue = if (eligible) {
            breakdown.netWealth.multiply(rate).setScale(2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO.setScale(2)
        }

        // Per-asset hawl statuses (per-asset mode). Excluded assets
        // (amanat, personal residence, long-term holdings, exempt jewelry)
        // carry no zakat due in this mode either.
        val perAsset = assets.filter { WealthPoolCalculator.isIncluded(it, madhhab) }
            .map { asset ->
                val base = WealthPoolCalculator.perAssetHawl(asset, goldPrice, silverPrice, today)
                val due = if (base.hawlComplete && base.value.signum() > 0) {
                    base.value.multiply(rate).setScale(2, RoundingMode.HALF_UP)
                } else {
                    BigDecimal.ZERO.setScale(2)
                }
                base.copy(zakatDue = due)
            }

        // Cash pseudo-row for per-asset mode: hawl starts on the earliest
        // day any (non-Amanat) account balance is recorded (data-driven).
        val cashStart = balances
            .filter { !it.isCreditCard }
            .filter { "${it.bankName}|${it.accountLast4}" !in amanatAccountKeys }
            .minOfOrNull { it.timestamp.toLocalDate() }
        val cashHawl = cashStart?.let {
            val s = ZakatCalculator.hawlStatus(it, today, calendarMode)
            val due = if (s.complete && breakdown.cash.signum() > 0) {
                breakdown.cash.multiply(rate)
                    .setScale(2, RoundingMode.HALF_UP)
            } else BigDecimal.ZERO.setScale(2)
            WealthPoolCalculator.AssetHawlStatus(
                asset = pseudoCashAsset(baseCurrency, cashStart),
                value = breakdown.cash,
                hawlComplete = s.complete,
                daysElapsed = s.daysElapsed,
                daysInYear = s.daysInYear,
                completionDate = if (s.complete) {
                    null
                } else {
                    WealthPoolCalculator.projectedCompletionDate(it)
                },
                zakatDue = due
            )
        }

        val knownAccounts = balances
            .filter { !it.isCreditCard }
            .map { it.bankName to it.accountLast4 }
            .distinct()
            .sortedWith(compareBy({ it.first }, { it.second }))

        return UiState(
            currencyCode = baseCurrency,
            loading = false,
            breakdown = breakdown,
            nisabMethod = method,
            appliedNisabValue = appliedNisab,
            goldNisabValue = goldNisab,
            silverNisabValue = silverNisab,
            goldPricePerGram = goldPriceRaw,
            silverPricePerGram = silverPriceRaw,
            aboveNisab = crossing.currentlyAboveNisab,
            crossingDate = hawlStart,
            firstEverCrossingDate = crossing.firstEverCrossing,
            hawlComplete = status?.complete == true,
            hawlDaysElapsed = status?.daysElapsed ?: 0,
            hawlDaysInYear = status?.daysInYear
                ?: calendarMode.fallbackYearDays,
            projectedCompletionDate = projected,
            hawlMode = hawlMode,
            perAssetStatuses = if (cashHawl != null) listOf(cashHawl) + perAsset else perAsset,
            cashHawlStart = cashStart,
            dueLines = dueLines,
            zakatDue = totalDue,
            hasAnyData = balances.isNotEmpty() || assets.isNotEmpty(),
            calendarMode = calendarMode,
            madhhab = madhhab,
            appliedRate = rate,
            deductions = breakdown.deductions,
            amanatAccountKeys = amanatAccountKeys,
            knownAccounts = knownAccounts
        )
    }

    /** Reduces the full balance history to the latest row per account. */
    private fun latestPerAccount(
        balances: List<AccountBalanceEntity>
    ): List<AccountBalanceEntity> {
        return balances
            .groupBy { it.bankName to it.accountLast4 }
            .map { (_, rows) -> rows.maxByOrNull { it.timestamp }!! }
    }

    private fun share(amount: BigDecimal, rate: BigDecimal = ZakatCalculator.ZAKAT_RATE): BigDecimal {
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP)
    }

    private fun pseudoCashAsset(currency: String, start: LocalDate): ZakatAssetEntity {
        return ZakatAssetEntity(
            id = CASH_PSEUDO_ID,
            name = "cash",
            currency = currency,
            acquisitionDate = start
        )
    }

    private fun parseMethod(raw: String): ZakatCalculator.NisabMethod =
        try {
            ZakatCalculator.NisabMethod.valueOf(raw.trim().uppercase())
        } catch (e: IllegalArgumentException) {
            ZakatCalculator.NisabMethod.SILVER
        }

    private fun parseMode(raw: String): HawlMode =
        try {
            HawlMode.valueOf(raw.trim().uppercase())
        } catch (e: IllegalArgumentException) {
            HawlMode.POOL
        }

    private fun parseCalendar(raw: String): ZakatCalculator.CalendarMode =
        try {
            ZakatCalculator.CalendarMode.valueOf(raw.trim().uppercase())
        } catch (e: IllegalArgumentException) {
            ZakatCalculator.CalendarMode.LUNAR
        }

    private fun parseMadhhab(raw: String): ZakatMadhhab =
        try {
            ZakatMadhhab.valueOf(raw.trim().uppercase())
        } catch (e: IllegalArgumentException) {
            ZakatMadhhab.MAINSTREAM
        }

    private fun parseAmount(raw: String): BigDecimal {
        if (raw.isBlank()) return BigDecimal.ZERO
        return try {
            BigDecimal(raw.trim()).max(BigDecimal.ZERO)
        } catch (e: NumberFormatException) {
            BigDecimal.ZERO
        }
    }

    companion object {
        /** Row id used for the synthetic cash row in per-asset mode. */
        const val CASH_PSEUDO_ID = -1L

        /** History window: two years covers hawl resets comfortably. */
        const val HISTORY_WINDOW_DAYS = 730L
    }
}
