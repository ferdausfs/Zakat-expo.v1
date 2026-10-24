package com.ritesh.cashiro.utils

import java.math.BigDecimal

/**
 * Strict, locale-tolerant parsing of user-entered decimal amounts (Z-17).
 *
 * The rules are deliberately conservative: an ambiguous string must NEVER
 * be silently reinterpreted into a different value. The motivating bug:
 * a comma-grouped amount such as "1,500" (one thousand five hundred) was
 * silently coerced into 1.5, corrupting zakat figures.
 *
 * Accepted forms (after Bengali-digit normalization and trimming):
 *  - plain integers            "1500", "0"
 *  - dot decimals              "12.5", ".5", "85.50"
 *  - trailing decimal COMMA    "2,5" -> 2.5, "85,50" -> 85.50
 *    (a comma is a decimal separator ONLY when it appears exactly once,
 *    is followed by exactly 1-2 digits, and those digits end the input)
 *
 * Everything else is INVALID and yields null — never a guessed value:
 *  - grouped amounts           "1,500", "1,50,000"  (comma not followed by 1-2 digits)
 *  - several separators        "1,5,0", "1.2.3", "1,500.25"
 *  - signs and stray chars     "-5", "+5", "12 ", "12,5 kg"
 *  - negative amounts in any shape (a valuation can never be negative)
 *
 * [isValidAmountInput] is the matching live-typing filter: it additionally
 * allows unfinished intermediate states ("12," while typing "12,50") so
 * users can enter decimal commas, while still blocking a second separator
 * or a third decimal digit — the shapes that make an amount ambiguous.
 */
object DecimalParsing {

    /** Plain integer or dot decimal; at least one digit overall. */
    private val PLAIN_DECIMAL = Regex("""\d+(\.\d+)?|\.\d+""")

    /** One comma acting as a decimal separator: 1-2 trailing digits only. */
    private val COMMA_DECIMAL = Regex("""\d+,\d{1,2}""")

    /** Live-typing shape: digits with at most one separator and 0-2 decimals. */
    private val INPUT_SHAPE = Regex("""\d*(?:[.,]\d{0,2})?""")

    /** Bengali digits ০১২৩৪৫৬৭৮৯ mapped onto 0-9. */
    private val BENGALI_ZERO = '০'

    /** Maps Bengali digits to their ASCII equivalents; other chars pass through. */
    fun normalizeBengaliDigits(raw: String): String = buildString(raw.length) {
        for (ch in raw) {
            append(if (ch in BENGALI_ZERO..'৯') ('0' + (ch - BENGALI_ZERO)) else ch)
        }
    }

    /**
     * Parses a strictly valid, non-negative user-entered decimal.
     *
     * @return the parsed [BigDecimal], or null when [raw] is blank or not
     *         unambiguously valid (see the class documentation). Callers
     *         must treat null as "field invalid" — never fall back to a
     *         default value silently.
     */
    fun parseUserDecimal(raw: String?): BigDecimal? {
        if (raw.isNullOrBlank()) return null
        val text = normalizeBengaliDigits(raw).trim()
        val value = when {
            text.contains(',') -> {
                if (!COMMA_DECIMAL.matches(text)) return null
                text.replace(',', '.')
            }
            else -> {
                if (!PLAIN_DECIMAL.matches(text)) return null
                text
            }
        }
        return try {
            val parsed = BigDecimal(value)
            if (parsed.signum() < 0) null else parsed
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * Live-typing filter for amount fields. Accepts every prefix of a
     * valid amount (including "12," and "12,5") and rejects anything a
     * later keystroke could not turn into an unambiguous amount.
     */
    fun isValidAmountInput(raw: String): Boolean = INPUT_SHAPE.matches(raw)
}
