package com.ritesh.cashiro.data.metals

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ritesh.cashiro.data.preferences.UserPreferencesRepository
import java.math.BigDecimal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the live gold/silver rate lifecycle end-to-end against the real
 * DataStore preferences: USD->base conversion, manual-override semantics
 * (auto refresh respects, explicit refresh overwrites), graceful network
 * failure fallback, and the once-per-day auto cadence.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MetalRateServiceTest {

    private lateinit var preferences: UserPreferencesRepository
    private lateinit var context: Context

    /** Provider that always returns a known spot: gold 100 USD/g, silver 1 USD/g. */
    private val onlineProvider = object : MetalRateProvider {
        override suspend fun fetchSpotPrices(): MetalSpotPrices? =
            MetalSpotPrices(
                goldUsdPerGram = BigDecimal("100"),
                silverUsdPerGram = BigDecimal("1"),
                fetchedAtMs = NOW
            )

        override fun getProviderName(): String = "test-online"
    }

    private val offlineProvider = object : MetalRateProvider {
        override suspend fun fetchSpotPrices(): MetalSpotPrices? = null
        override fun getProviderName(): String = "test-offline"
    }

    private val usdOnlyConverter = MetalCurrencyConverter { amount, _, _ -> amount }
    private val rate110Converter = MetalCurrencyConverter { amount, from, to ->
        if (from == "USD" && to == "BDT") amount.multiply(BigDecimal("110")) else amount
    }
    private val failingConverter = MetalCurrencyConverter { _, _, _ -> null }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = UserPreferencesRepository(context)
    }

    @Test
    fun `refresh converts usd spot to base currency and stores timestamps`() = runBlocking {
        val service = MetalRateService(onlineProvider, rate110Converter, preferences)
        val base = T1

        val result = service.refresh(force = true, baseCurrency = "BDT")

        assertTrue(result.fetched)
        assertTrue(result.applied)
        // 100 USD/g * 110 = 11000 BDT/g; 1 USD/g * 110 = 110 BDT/g.
        assertEquals("11000", preferences.zakatGoldPricePerGram.first())
        assertEquals("110", preferences.zakatSilverPricePerGram.first())
        assertEquals(base, preferences.zakatGoldPriceUpdatedAt.first())
        assertEquals(base, preferences.zakatMetalLastFetchAt.first())
        assertFalse(preferences.zakatGoldPriceIsManual.first())
        assertFalse(preferences.zakatSilverPriceIsManual.first())
    }

    @Test
    fun `auto refresh respects manual override until explicit refresh`() = runBlocking {
        val service = MetalRateService(onlineProvider, usdOnlyConverter, preferences)
        val base = T2

        // User types a manual gold price (e.g. their local bazaar rate).
        preferences.setZakatGoldPriceManual("7777", base - 1000)

        // Later, the once-per-day auto refresh runs:
        val auto = service.maybeAutoRefresh(baseCurrency = "USD", nowMs = base)

        assertNotNull(auto)
        assertTrue(auto!!.fetched)
        assertTrue(auto.goldSkippedManual)
        assertFalse(auto.silverSkippedManual)
        // Manual gold price is preserved; silver was refreshed.
        assertEquals("7777", preferences.zakatGoldPricePerGram.first())
        assertTrue(preferences.zakatGoldPriceIsManual.first())
        assertEquals("1", preferences.zakatSilverPricePerGram.first())

        // User taps "Refresh rates" — the explicit action DOES overwrite.
        val forced = service.refresh(force = true, baseCurrency = "USD")
        assertTrue(forced.fetched)
        assertEquals("100", preferences.zakatGoldPricePerGram.first())
        assertFalse(preferences.zakatGoldPriceIsManual.first())
    }

    @Test
    fun `network failure keeps cached prices and never writes`() = runBlocking {
        val service = MetalRateService(offlineProvider, usdOnlyConverter, preferences)
        val base = T3

        preferences.setZakatGoldPriceAuto("9999", base - 5000)
        val result = service.refresh(force = true, baseCurrency = "USD")

        assertFalse(result.fetched)
        assertFalse(result.applied)
        assertEquals("9999", preferences.zakatGoldPricePerGram.first())
        assertEquals(base - 5000, preferences.zakatGoldPriceUpdatedAt.first())
    }

    @Test
    fun `conversion failure keeps the cached price for that metal`() = runBlocking {
        val service = MetalRateService(onlineProvider, failingConverter, preferences)
        val base = T4

        preferences.setZakatGoldPriceAuto("9999", base - 5000)
        val result = service.refresh(force = true, baseCurrency = "BDT")

        assertTrue(result.fetched)
        assertTrue(result.goldConversionFailed)
        assertTrue(result.silverConversionFailed)
        // The gold value that could not be converted is untouched.
        assertEquals("9999", preferences.zakatGoldPricePerGram.first())
        assertEquals(base - 5000, preferences.zakatGoldPriceUpdatedAt.first())
    }

    @Test
    fun `auto cadence runs at most once per day`() = runBlocking {
        val base = T5
        // Dedicated provider whose fetch timestamp follows THIS test's
        // timeline (the shared onlineProvider stamps a fixed instant).
        val provider = object : MetalRateProvider {
            override suspend fun fetchSpotPrices(): MetalSpotPrices? =
                MetalSpotPrices(
                    goldUsdPerGram = BigDecimal("100"),
                    silverUsdPerGram = BigDecimal("1"),
                    fetchedAtMs = base
                )

            override fun getProviderName(): String = "test-online-timeline"
        }
        val service = MetalRateService(provider, usdOnlyConverter, preferences)

        // First auto refresh succeeds and records the fetch time.
        assertNotNull(service.maybeAutoRefresh("USD", nowMs = base))

        // Within 24h: skipped entirely (no provider call, no writes).
        assertNull(service.maybeAutoRefresh("USD", nowMs = base + 60_000))
        assertNull(
            service.maybeAutoRefresh("USD", nowMs = base + MetalRateService.AUTO_REFRESH_INTERVAL_MS - 1)
        )

        // After 24h: runs again.
        assertNotNull(
            service.maybeAutoRefresh("USD", nowMs = base + MetalRateService.AUTO_REFRESH_INTERVAL_MS + 1)
        )
    }

    private companion object {
        const val NOW = 1_760_000_000_000L

        // Robolectric shares one JVM/classloader per sandbox, so the
        // DataStore behind UserPreferencesRepository persists between test
        // methods. Every test gets its own timeline (> one cadence interval
        // apart) so the once-per-day gate can never suppress another test.
        const val T1 = NOW
        const val T2 = NOW + 10 * MetalRateService.AUTO_REFRESH_INTERVAL_MS
        const val T3 = NOW + 20 * MetalRateService.AUTO_REFRESH_INTERVAL_MS
        const val T4 = NOW + 30 * MetalRateService.AUTO_REFRESH_INTERVAL_MS
        const val T5 = NOW + 40 * MetalRateService.AUTO_REFRESH_INTERVAL_MS
    }
}
