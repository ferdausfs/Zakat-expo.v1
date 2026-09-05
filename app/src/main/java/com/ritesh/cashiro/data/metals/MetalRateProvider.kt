package com.ritesh.cashiro.data.metals

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Spot prices for the zakat metals, expressed in USD per GRAM.
 *
 * Providers quote per troy ounce; the per-gram normalization happens in
 * the provider so the rest of the app only ever deals with grams.
 */
data class MetalSpotPrices(
    val goldUsdPerGram: BigDecimal,
    val silverUsdPerGram: BigDecimal,
    /** Wall-clock time of the successful fetch (epoch millis). */
    val fetchedAtMs: Long
)

/**
 * Clean abstraction over metals-price sources so the provider can be
 * swapped without touching the app (live gold/silver rate requirement).
 */
interface MetalRateProvider {
    /** @return spot prices, or null when the source is unreachable/malformed. */
    suspend fun fetchSpotPrices(): MetalSpotPrices?

    fun getProviderName(): String
}

/**
 * Default provider: the free, key-less gold-api.com spot endpoints
 * (https://api.gold-api.com/price/XAU and /price/XAG), with the
 * goldprice.org public feed as a fallback host.
 *
 * Both quote USD per TROY OUNCE; ounce -> gram uses 31.1034768 g.
 *
 * Constructed via [MetalRateProviderFactory]; tests may inject a
 * pre-built HttpClient (e.g. backed by a MockEngine) through the
 * optional constructor parameter.
 */
class GoldApiProvider(
    injectedClient: HttpClient? = null
) : MetalRateProvider {

    private val client: HttpClient =
        injectedClient ?: HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                })
            }
        }

    private companion object {
        const val PRIMARY_XAU = "https://api.gold-api.com/price/XAU"
        const val PRIMARY_XAG = "https://api.gold-api.com/price/XAG"

        /** Public feed used by goldprice.org widgets (no key, USD base). */
        const val FALLBACK_URL = "https://data-asg.goldprice.org/dbXRates/USD"

        /** One troy ounce in grams (exact by definition). */
        val TROY_OUNCE_GRAMS = BigDecimal("31.1034768")

        /** Per-gram precision kept in preferences. */
        const val PRICE_SCALE = 4
    }

    override suspend fun fetchSpotPrices(): MetalSpotPrices? {
        return try {
            withContext(Dispatchers.IO) { fetchInternal() }
        } catch (e: Exception) {
            // Never propagate network failures to callers — the service
            // falls back to the last cached prices.
            null
        }
    }

    private suspend fun fetchInternal(): MetalSpotPrices? {
        val fetchedAt = System.currentTimeMillis()

        fetchFromPrimary()?.let { (goldOz, silverOz) ->
            return toSpotPrices(goldOz, silverOz, fetchedAt)
        }
        fetchFromFallback()?.let { (goldOz, silverOz) ->
            return toSpotPrices(goldOz, silverOz, fetchedAt)
        }
        return null
    }

    /** @return Pair(goldUsdPerOunce, silverUsdPerOunce) or null. */
    private suspend fun fetchFromPrimary(): Pair<BigDecimal, BigDecimal>? {
        val gold = fetchPrimaryPrice(PRIMARY_XAU) ?: return null
        val silver = fetchPrimaryPrice(PRIMARY_XAG) ?: return null
        return gold to silver
    }

    private suspend fun fetchPrimaryPrice(url: String): BigDecimal? {
        return try {
            val response = client.get(url) {
                header("User-Agent", "Zakat/2.1")
            }
            if (response.status.value !in 200..299) return null
            val body: String = response.body()
            val price = Json.parseToJsonElement(body)
                .jsonObject["price"]?.jsonPrimitive?.content ?: return null
            val value = BigDecimal(price)
            if (value.signum() <= 0) null else value
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun fetchFromFallback(): Pair<BigDecimal, BigDecimal>? {
        return try {
            val response = client.get(FALLBACK_URL) {
                header("User-Agent", "Zakat/2.1")
            }
            if (response.status.value !in 200..299) return null
            val body: String = response.body()
            val item = Json.parseToJsonElement(body)
                .jsonObject["items"]?.jsonArray
                ?.firstOrNull()?.jsonObject ?: return null
            val gold = BigDecimal(item["xauPrice"]?.jsonPrimitive?.content ?: return null)
            val silver = BigDecimal(item["xagPrice"]?.jsonPrimitive?.content ?: return null)
            if (gold.signum() <= 0 || silver.signum() <= 0) null else gold to silver
        } catch (e: Exception) {
            null
        }
    }

    private fun toSpotPrices(
        goldUsdPerOunce: BigDecimal,
        silverUsdPerOunce: BigDecimal,
        fetchedAtMs: Long
    ): MetalSpotPrices {
        return MetalSpotPrices(
            goldUsdPerGram = goldUsdPerOunce.divide(
                TROY_OUNCE_GRAMS, PRICE_SCALE, RoundingMode.HALF_UP
            ),
            silverUsdPerGram = silverUsdPerOunce.divide(
                TROY_OUNCE_GRAMS, PRICE_SCALE, RoundingMode.HALF_UP
            ),
            fetchedAtMs = fetchedAtMs
        )
    }

    override fun getProviderName(): String = "gold-api.com"
}

/** Factory so the provider can be swapped in one place. */
object MetalRateProviderFactory {
    fun createProvider(): MetalRateProvider = GoldApiProvider()
}
