package com.ritesh.cashiro.data.model

import com.ritesh.cashiro.data.currency.model.CurrencySymbols
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for SAR/BDT currency support: both currencies must stay
 * selectable app-side (not only parseable in SMS), matching the parsers in
 * :parser-core.
 */
class CurrencySupportTest {

    @Test
    fun `BDT is a supported account currency`() {
        val bdt = Currency.getByCode("BDT")
        assertNotNull("BDT missing from SUPPORTED_CURRENCIES", bdt)
        assertEquals("Bangladeshi Taka", bdt?.name)
        assertEquals("৳", bdt?.symbol)
    }

    @Test
    fun `SAR is a supported account currency`() {
        val sar = Currency.getByCode("SAR")
        assertNotNull("SAR missing from SUPPORTED_CURRENCIES", sar)
        assertEquals("Saudi Riyal", sar?.name)
        assertEquals("﷼", sar?.symbol)
    }

    @Test
    fun `BDT and SAR symbols resolve through CurrencySymbols`() {
        assertEquals("৳", CurrencySymbols.getSymbol("BDT"))
        assertEquals("﷼", CurrencySymbols.getSymbol("SAR"))
    }

    @Test
    fun `currency lookup is case insensitive`() {
        assertEquals("BDT", Currency.getByCode("bdt")?.code)
        assertEquals("SAR", Currency.getByCode("sar")?.code)
    }

    @Test
    fun `supported currency list has no duplicate codes`() {
        val codes = Currency.SUPPORTED_CURRENCIES.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.containsAll(listOf("SAR", "BDT", "INR", "USD")))
    }

    @Test
    fun `app default currency is SAR`() {
        // Currency-consistency fix: the app-wide default must be SAR
        // (user-changeable). Guards against template-leftover defaults
        // (e.g. INR) sneaking back into UI states or preferences.
        assertEquals("SAR", Currency.DEFAULT_CURRENCY_CODE)
    }
}
