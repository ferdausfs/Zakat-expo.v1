package com.ritesh.cashiro.utils

import com.ritesh.cashiro.presentation.ui.features.zakat.dashboard.ZakatDashboardViewModel
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Z-17: strict numeric-input rules.
 *
 * The old parsing silently mangled ambiguous input ("1,500" became 1.5 or
 * 0 depending on the path). The rules under test:
 *  - a comma is a decimal separator ONLY when it appears exactly once,
 *    is followed by exactly 1-2 digits, and those digits end the input;
 *  - anything else containing a comma is INVALID (null) — never a
 *    silently different value;
 *  - negatives, signs and stray characters are invalid;
 *  - Bengali digits are supported;
 *  - the dashboard maps an invalid stored price to ZERO, which the
 *    Z-02 "price not set" state treats as missing (never a fake nisab).
 */
class NumericInputTest {

    // ------------------------------------------------------------------
    // DecimalParsing.parseUserDecimal — accepted forms
    // ------------------------------------------------------------------

    @Test
    fun `plain integers parse as-is`() {
        assertParses("1500", "1500")
        assertParses("0", "0")
        assertParses("85", "85")
    }

    @Test
    fun `dot decimals parse as-is`() {
        assertParses("12.5", "12.5")
        assertParses(".5", "0.5")
        assertParses("85.50", "85.50")
        assertParses("12.345", "12.345")
    }

    @Test
    fun `trailing decimal comma with one or two digits is a decimal separator`() {
        assertParses("2,5", "2.5")
        assertParses("85,50", "85.50")
        assertParses("1,5", "1.5")
        assertParses("0,05", "0.05")
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        assertParses(" 12,5 ", "12.5")
        assertParses(" 1500 ", "1500")
    }

    @Test
    fun `bengali digits are supported`() {
        assertParses("১২.৫", "12.5")
        assertParses("১,৫", "1.5")
        assertParses("৮৫,৫০", "85.50")
        assertParses("১৫০০", "1500")
    }

    // ------------------------------------------------------------------
    // DecimalParsing.parseUserDecimal — invalid forms (must yield null)
    // ------------------------------------------------------------------

    @Test
    fun `grouped thousands are never silently converted`() {
        // The motivating bug: "1,500" must not become 1.5 (or anything else).
        assertNull(DecimalParsing.parseUserDecimal("1,500"))
        assertNull(DecimalParsing.parseUserDecimal("1,50,000"))
        assertNull(DecimalParsing.parseUserDecimal("১,৫০০"))
    }

    @Test
    fun `multiple commas are invalid`() {
        assertNull(DecimalParsing.parseUserDecimal("1,5,0"))
        assertNull(DecimalParsing.parseUserDecimal("1,50,000"))
    }

    @Test
    fun `mixed separators are invalid`() {
        assertNull(DecimalParsing.parseUserDecimal("1,500.25"))
        assertNull(DecimalParsing.parseUserDecimal("1.234,50"))
    }

    @Test
    fun `dangling separators are invalid`() {
        assertNull(DecimalParsing.parseUserDecimal("5,"))
        assertNull(DecimalParsing.parseUserDecimal(",5"))
        assertNull(DecimalParsing.parseUserDecimal("12."))
    }

    @Test
    fun `signs are invalid - valuations can never be negative`() {
        assertNull(DecimalParsing.parseUserDecimal("-5"))
        assertNull(DecimalParsing.parseUserDecimal("+5"))
        assertNull(DecimalParsing.parseUserDecimal("-2,5"))
    }

    @Test
    fun `non numeric and blank input is invalid`() {
        assertNull(DecimalParsing.parseUserDecimal(""))
        assertNull(DecimalParsing.parseUserDecimal("   "))
        assertNull(DecimalParsing.parseUserDecimal(null))
        assertNull(DecimalParsing.parseUserDecimal("abc"))
        assertNull(DecimalParsing.parseUserDecimal("12,5 kg"))
        assertNull(DecimalParsing.parseUserDecimal("১২ টাকা"))
    }

    // ------------------------------------------------------------------
    // DecimalParsing.isValidAmountInput — live-typing filter
    // ------------------------------------------------------------------

    @Test
    fun `typing filter allows prefixes of valid amounts`() {
        assertTrue(DecimalParsing.isValidAmountInput(""))
        assertTrue(DecimalParsing.isValidAmountInput("1"))
        assertTrue(DecimalParsing.isValidAmountInput("12,"))
        assertTrue(DecimalParsing.isValidAmountInput("12,5"))
        assertTrue(DecimalParsing.isValidAmountInput("12.50"))
        assertTrue(DecimalParsing.isValidAmountInput(".5"))
    }

    @Test
    fun `typing filter blocks ambiguous shapes`() {
        // A second comma can never become valid.
        assertFalse(DecimalParsing.isValidAmountInput("1,5,0"))
        // A third decimal digit rules out the decimal-comma reading.
        assertFalse(DecimalParsing.isValidAmountInput("1,500"))
        assertFalse(DecimalParsing.isValidAmountInput("12,345"))
        assertFalse(DecimalParsing.isValidAmountInput("12.345"))
        assertFalse(DecimalParsing.isValidAmountInput("-5"))
        assertFalse(DecimalParsing.isValidAmountInput("abc"))
    }

    // ------------------------------------------------------------------
    // ZakatDashboardViewModel.parseAmount — dashboard price path (Z-02 glue)
    // ------------------------------------------------------------------

    @Test
    fun `dashboard price path keeps valid decimals`() {
        assertVmParses("1500", "1500")
        assertVmParses("2,5", "2.5")
        assertVmParses("85,50", "85.50")
        assertVmParses("12.5", "12.5")
    }

    @Test
    fun `dashboard price path maps invalid input to zero - never a guessed value`() {
        // ZERO is the dashboard's explicit "price not set" state (Z-02):
        // the nisab is withheld and a set-price prompt is shown, instead of
        // computing a fake nisab or silently reinterpreting the input.
        val zero = BigDecimal.ZERO
        assertEquals(zero, ZakatDashboardViewModel.parseAmount("1,500"))
        assertEquals(zero, ZakatDashboardViewModel.parseAmount("1,50,000"))
        assertEquals(zero, ZakatDashboardViewModel.parseAmount(""))
        assertEquals(zero, ZakatDashboardViewModel.parseAmount("abc"))
        assertEquals(zero, ZakatDashboardViewModel.parseAmount("-5"))
    }

    // ------------------------------------------------------------------

    private fun assertParses(raw: String?, expected: String) {
        val parsed = DecimalParsing.parseUserDecimal(raw)
        assertTrue("expected '$raw' to parse, got null", parsed != null)
        assertEquals(0, parsed!!.compareTo(BigDecimal(expected)))
    }

    private fun assertVmParses(raw: String, expected: String) {
        val parsed = ZakatDashboardViewModel.parseAmount(raw)
        assertEquals(0, parsed.compareTo(BigDecimal(expected)))
    }
}
