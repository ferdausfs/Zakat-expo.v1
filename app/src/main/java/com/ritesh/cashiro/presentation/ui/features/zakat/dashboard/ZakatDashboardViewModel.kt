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
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
        val hasAnyData: Boolean = false
    )

    private data class PoolInputs(
        val balances: List<AccountBalanceEntity>,
        val assets: List<ZakatAssetEntity>,
        val baseCurrency: String,
        val nisabMethod: String
    )

    private data class PriceInputs(
        val goldPrice: String,
        val silverPrice: String,
        val hawlMode: String
    )

    private val combined = combine(
        combine(
            zakatRepository.observeAllBalanceHistory(),
            zakatRepository.observeAssets(),
            userPreferencesRepository.baseCurrency,
            userPreferencesRepository.zakatNisabMethod
        ) { balances, assets, baseCurrency, nisabMethod ->
            PoolInputs(balances, assets, baseCurrency, nisabMethod)
        },
        combine(
            userPreferencesRepository.zakatGoldPricePerGram,
            userPreferencesRepository.zakatSilverPricePerGram,
            userPreferencesRepository.zakatHawlMode
        ) { goldPrice, silverPrice, hawlMode ->
            PriceInputs(goldPrice, silverPrice, hawlMode)
        }
    ) { inputs, prices ->
        buildState(
            inputs.balances, inputs.assets, inputs.baseCurrency,
            parseMethod(inputs.nisabMethod), prices.goldPrice, prices.silverPrice,
            parseMode(prices.hawlMode)
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

    // ----------------------------- Derivation ------------------------------

    private fun buildState(
        balances: List<AccountBalanceEntity>,
        assets: List<ZakatAssetEntity>,
        baseCurrency: String,
        method: ZakatCalculator.NisabMethod,
        goldPriceRaw: String,
        silverPriceRaw: String,
        hawlMode: HawlMode
    ): UiState {
        val goldPrice = parseAmount(goldPriceRaw)
        val silverPrice = parseAmount(silverPriceRaw)
        val today = LocalDate.now()

        val breakdown = WealthPoolCalculator.currentBreakdown(
            latestBalances = latestPerAccount(balances),
            assets = assets,
            goldPricePerGram = goldPrice,
            silverPricePerGram = silverPrice
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
            to = today
        )
        val crossing = WealthPoolCalculator.detectNisabCrossing(series, appliedNisab)

        val hawlStart = crossing.activeHawlStart
        val status = hawlStart?.let { ZakatCalculator.hawlStatus(it, today) }
        val projected = hawlStart?.let { WealthPoolCalculator.projectedCompletionDate(it) }

        // Zakat due with a transparent per-category derivation.
        val eligible = crossing.currentlyAboveNisab && (status?.complete == true)
        val dueLines = if (eligible) {
            listOf(
                DueLine("cash", breakdown.cash, share(breakdown.cash)),
                DueLine("gold", breakdown.gold, share(breakdown.gold)),
                DueLine("silver", breakdown.silver, share(breakdown.silver)),
                DueLine("other", breakdown.otherAssets, share(breakdown.otherAssets))
            ).filter { it.amount.signum() > 0 }
        } else {
            emptyList()
        }
        val totalDue = if (eligible) {
            breakdown.total.multiply(ZakatCalculator.ZAKAT_RATE)
                .setScale(2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO.setScale(2)
        }

        // Per-asset hawl statuses (per-asset mode).
        val perAsset = assets.map {
            WealthPoolCalculator.perAssetHawl(it, goldPrice, silverPrice, today)
        }

        // Cash pseudo-row for per-asset mode: hawl starts on the earliest
        // day any account balance is recorded (data-driven, not manual).
        val cashStart = balances
            .filter { !it.isCreditCard }
            .minOfOrNull { it.timestamp.toLocalDate() }
        val cashHawl = cashStart?.let {
            val s = ZakatCalculator.hawlStatus(it, today)
            val due = if (s.complete && breakdown.cash.signum() > 0) {
                breakdown.cash.multiply(ZakatCalculator.ZAKAT_RATE)
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
            hawlDaysInYear = status?.daysInYear ?: ZakatCalculator.MEAN_LUNAR_YEAR_DAYS,
            projectedCompletionDate = projected,
            hawlMode = hawlMode,
            perAssetStatuses = if (cashHawl != null) listOf(cashHawl) + perAsset else perAsset,
            cashHawlStart = cashStart,
            dueLines = dueLines,
            zakatDue = totalDue,
            hasAnyData = balances.isNotEmpty() || assets.isNotEmpty()
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

    private fun share(amount: BigDecimal): BigDecimal {
        return amount.multiply(ZakatCalculator.ZAKAT_RATE).setScale(2, RoundingMode.HALF_UP)
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
