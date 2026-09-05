package com.ritesh.cashiro.data.metals

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Parses the real gold-api.com / goldprice.org payloads (via Ktor MockEngine)
 * and verifies the troy-ounce -> gram normalization that feeds the zakat
 * nisab and wealth-pool math.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoldApiProviderTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType to listOf("application/json"))

    private fun provider(engine: MockEngine) = GoldApiProvider(HttpClient(engine))

    @Test
    fun `parses gold-api com primary endpoints into usd per gram`() = runBlocking {
        val engine = MockEngine { request ->
            val url = request.url.toString()
            val price = when {
                url.endsWith("XAU") -> "2000.00"
                url.endsWith("XAG") -> "31.1034768"
                else -> error("unexpected url $url")
            }
            respond(
                content = ByteReadChannel(
                    """{"name":"test","price":$price,"symbol":"X","updatedAt":"2026-01-01"}"""
                ),
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }

        val spot = provider(engine).fetchSpotPrices()

        assertNotNull(spot)
        val expectedGold = BigDecimal("2000.00")
            .divide(BigDecimal("31.1034768"), 4, RoundingMode.HALF_UP)
        val expectedSilver = BigDecimal("31.1034768")
            .divide(BigDecimal("31.1034768"), 4, RoundingMode.HALF_UP)
        assertEquals(expectedGold, spot!!.goldUsdPerGram)
        assertEquals(expectedSilver, spot.silverUsdPerGram)
        assertTrue(spot.fetchedAtMs > 0)
        assertEquals("gold-api.com", provider(engine).getProviderName())
    }

    @Test
    fun `falls back to goldprice org feed when primary host fails`() = runBlocking {
        val engine = MockEngine { request ->
            val url = request.url.toString()
            if (url.contains("gold-api.com")) {
                respond(
                    content = ByteReadChannel("server error"),
                    status = HttpStatusCode.InternalServerError,
                    headers = jsonHeaders
                )
            } else {
                respond(
                    content = ByteReadChannel(
                        """{"items":[{"curr":"USD","xauPrice":2488.2714,"xagPrice":28.4021}]}"""
                    ),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders
                )
            }
        }

        val spot = provider(engine).fetchSpotPrices()

        assertNotNull(spot)
        val expectedGold = BigDecimal("2488.2714")
            .divide(BigDecimal("31.1034768"), 4, RoundingMode.HALF_UP)
        assertEquals(expectedGold, spot!!.goldUsdPerGram)
    }

    @Test
    fun `returns null when both providers fail`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("down"),
                status = HttpStatusCode.ServiceUnavailable,
                headers = jsonHeaders
            )
        }

        assertNull(provider(engine).fetchSpotPrices())
    }

    @Test
    fun `returns null on malformed payloads`() = runBlocking {
        var call = 0
        val engine = MockEngine {
            call++
            val body = if (call % 2 == 1) {
                """{"unexpected":"shape"}"""
            } else {
                "not json at all"
            }
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = jsonHeaders
            )
        }

        assertNull(provider(engine).fetchSpotPrices())
    }
}
