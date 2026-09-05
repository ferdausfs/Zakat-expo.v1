package com.ritesh.cashiro.data.metals

import com.ritesh.cashiro.data.preferences.UserPreferencesRepository
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Converts a metal spot price from USD into the user's base currency.
 *
 * Kept as an interface so tests can substitute a fixed rate and the
 * implementation can be swapped independently of the fetch pipeline.
 * Returning null (instead of the input amount) on failure is deliberate:
 * writing a USD-denominated price into a BDT preference would silently
 * corrupt every zakat figure.
 */
fun interface MetalCurrencyConverter {
    /** @return converted amount, or null when the rate is unavailable. */
    suspend fun convert(amount: BigDecimal, fromCurrency: String, toCurrency: String): BigDecimal?
}

/** Production converter delegating to the app's CurrencyConversionService. */
class DefaultMetalCurrencyConverter @Inject constructor(
    private val conversionService: com.ritesh.cashiro.data.currency.CurrencyConversionService
) : MetalCurrencyConverter {
    override suspend fun convert(
        amount: BigDecimal,
        fromCurrency: String,
        toCurrency: String
    ): BigDecimal? {
        if (fromCurrency.equals(toCurrency, ignoreCase = true)) return amount
        return conversionService.getExchangeRate(fromCurrency, toCurrency)
            ?.let { rate ->
                amount.multiply(rate).setScale(4, RoundingMode.HALF_UP)
            }
    }
}

/** Outcome of one refresh attempt. */
data class MetalRateRefreshResult(
    /** True when the provider returned prices (regardless of override skips). */
    val fetched: Boolean,
    /** True when at least one stored price was updated. */
    val applied: Boolean,
    val fetchedAtMs: Long = 0L,
    /** Gold price was NOT written because the user has a manual override. */
    val goldSkippedManual: Boolean = false,
    /** Silver price was NOT written because the user has a manual override. */
    val silverSkippedManual: Boolean = false,
    /** Gold conversion to the base currency failed (cached value kept). */
    val goldConversionFailed: Boolean = false,
    /** Silver conversion to the base currency failed (cached value kept). */
    val silverConversionFailed: Boolean = false
)

/**
 * Owns the live gold/silver rate lifecycle:
 *  - fetch spot USD/gram from a [MetalRateProvider],
 *  - convert into the user's base currency,
 *  - persist to the shared zakat price preferences,
 *  - respect manual overrides until the user explicitly refreshes,
 *  - auto-refresh at most once per day, never blocking or crashing the UI.
 */
@Singleton
class MetalRateService @Inject constructor(
    private val metalRateProvider: MetalRateProvider,
    private val currencyConverter: MetalCurrencyConverter,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    /**
     * Fetches current spot prices and writes them per the override rules.
     *
     * @param force  true when the user explicitly tapped "Refresh rates"
     *               (manual overrides are overwritten and cleared); false
     *               for the automatic cadence (manual overrides win).
     */
    suspend fun refresh(
        force: Boolean,
        baseCurrency: String,
        nowMs: Long = System.currentTimeMillis()
    ): MetalRateRefreshResult {
        val spot = try {
            metalRateProvider.fetchSpotPrices()
        } catch (e: Exception) {
            null
        } ?: return MetalRateRefreshResult(fetched = false, applied = false)

        // The provider fetch succeeded — record it so the auto cadence
        // stays at most once per day (independent of per-metal writes).
        userPreferencesRepository.setZakatMetalLastFetchAt(spot.fetchedAtMs)

        val goldManual = userPreferencesRepository.zakatGoldPriceIsManual.first()
        val silverManual = userPreferencesRepository.zakatSilverPriceIsManual.first()

        val goldSkip = goldManual && !force
        val silverSkip = silverManual && !force

        var applied = false
        var goldConversionFailed = false
        var silverConversionFailed = false

        if (!goldSkip) {
            val goldInBase = currencyConverter.convert(
                spot.goldUsdPerGram, USD, baseCurrency
            )
            if (goldInBase != null && goldInBase.signum() > 0) {
                userPreferencesRepository.setZakatGoldPriceAuto(
                    goldInBase.stripTrailingZeros().toPlainString(),
                    spot.fetchedAtMs
                )
                applied = true
            } else {
                goldConversionFailed = true
            }
        }

        if (!silverSkip) {
            val silverInBase = currencyConverter.convert(
                spot.silverUsdPerGram, USD, baseCurrency
            )
            if (silverInBase != null && silverInBase.signum() > 0) {
                userPreferencesRepository.setZakatSilverPriceAuto(
                    silverInBase.stripTrailingZeros().toPlainString(),
                    spot.fetchedAtMs
                )
                applied = true
            } else {
                silverConversionFailed = true
            }
        }

        return MetalRateRefreshResult(
            fetched = true,
            applied = applied,
            fetchedAtMs = spot.fetchedAtMs,
            goldSkippedManual = goldSkip,
            silverSkippedManual = silverSkip,
            goldConversionFailed = goldConversionFailed,
            silverConversionFailed = silverConversionFailed
        )
    }

    /**
     * Automatic, once-per-day refresh (called on dashboard open).
     * Returns null when rates are already fresh; never throws.
     */
    suspend fun maybeAutoRefresh(
        baseCurrency: String,
        nowMs: Long = System.currentTimeMillis()
    ): MetalRateRefreshResult? {
        return try {
            val lastFetch = userPreferencesRepository.zakatMetalLastFetchAt.first()
            if (lastFetch != 0L && nowMs - lastFetch < AUTO_REFRESH_INTERVAL_MS) {
                null
            } else {
                refresh(force = false, baseCurrency = baseCurrency, nowMs = nowMs)
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val USD = "USD"

        /** Auto-refresh cadence: once per day. */
        const val AUTO_REFRESH_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}
