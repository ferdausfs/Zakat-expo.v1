package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Shared Arabic + English SMS pattern helpers for Saudi banks and wallets.
 *
 * Saudi banks send both Arabic-first templates and English ones. The
 * shared keyword/regex sets below cover the common vocabulary across
 * Saudi banks (SNB/AlAhli, SABB, STC Bank, Riyad, ANB, BSF, Albilad,
 * SAIB, Al Rajhi ...) so each dedicated parser stays thin and consistent:
 *
 *  - expense: خصم (deducted), شراء (purchase), سحب (withdrawal),
 *    مدفوع (paid), دفع (pay), صادرة (outgoing), سداد (settlement)
 *  - income : إيداع (deposit), إضافة/اضاف (added), حوالة واردة
 *    (incoming transfer), واردة (incoming), استلمت (received)
 *
 * Amount extraction is balance-aware: numbers that belong to a
 * balance/available-balance clause ("Avl. Bal: SAR X" / "الرصيد المتاح
 * SAR X") are never returned as the transaction amount.
 */
internal object SaudiSmsSupport {

    private val OTP_HINTS = listOf(
        "otp", "one time password", "verification code",
        "رمز", "كلمة المرور", "رمز التحقق"
    )

    private val PROMO_HINTS = listOf(
        "offer", "discount", "win ", "cashback offer",
        "عرض", "جائزة", "اشترك"
    )

    private val EXPENSE_KEYWORDS = listOf(
        // Arabic
        "خصم", "شراء", "سحب", "مدفوع", "دفع", "صادرة", "سداد",
        // English
        "debited", "withdrawn", "spent", "purchase", "paid",
        "payment of", "outward", "have sent", "you sent"
    )

    private val INCOME_KEYWORDS = listOf(
        // Arabic
        "إيداع", "اضاف", "إضافة", "حوالة واردة", "واردة", "استلمت",
        // English
        "credited", "deposited", "refund", "received", "inward",
        "added to your", "topped up"
    )

    /** True when the sender SMS body is a bank/wallet transaction message. */
    fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()
        if (OTP_HINTS.any { lowerMessage.contains(it) }) return false
        if (PROMO_HINTS.any { lowerMessage.contains(it) }) return false
        val lower = lowerMessage
        return EXPENSE_KEYWORDS.any { lower.contains(it) } ||
                INCOME_KEYWORDS.any { lower.contains(it) }
    }

    /** INCOME / EXPENSE classification across Arabic + English vocabulary. */
    fun transactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()
        return when {
            INCOME_KEYWORDS.any { lowerMessage.contains(it) } -> TransactionType.INCOME
            EXPENSE_KEYWORDS.any { lowerMessage.contains(it) } -> TransactionType.EXPENSE
            else -> null
        }
    }

    // ── Amount ───────────────────────────────────────────────────────────
    // Ordered: Arabic-labelled forms first, then generic SAR forms. Generic
    // matches that belong to a balance clause are skipped.
    private val AMOUNT_PATTERNS = listOf(
        // "مبلغ SAR 350.00" / "مبلغ: SAR 350.00" / "مبلغ:SAR350"
        Regex("""مبلغ\s*[:\-]?\s*SAR\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        // "بمبلغ: 75.50 SAR" (Alinma style)
        Regex("""بمبلغ\s*[:\-]?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*SAR\b""", RegexOption.IGNORE_CASE),
        // "بـSAR 5.75" (Al Rajhi style)
        Regex("""بـSAR\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        // "القسط: 2304.58 SAR" (loan installment)
        Regex("""القسط\s*[:\-]?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*SAR\b""", RegexOption.IGNORE_CASE),
        // "SAR 125.50" / "SAR125.50"
        Regex("""SAR\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        // "125.50 SAR"
        Regex("""([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*SAR\b""", RegexOption.IGNORE_CASE)
    )

    private val BALANCE_CONTEXT_MARKERS = listOf(
        "bal", "balance", "الرصيد", "المتبقي", "متاح"
    )

    private fun firstNonBalanceMatch(message: String, regex: Regex): MatchResult? =
        regex.findAll(message).firstOrNull { m ->
            val before = message.substring(maxOf(0, m.range.first - 16), m.range.first).lowercase()
            BALANCE_CONTEXT_MARKERS.none { before.contains(it) }
        }

    /** Extracts the TRANSACTION amount (never the balance figure). */
    fun extractAmount(message: String): BigDecimal? {
        for (pattern in AMOUNT_PATTERNS) {
            firstNonBalanceMatch(message, pattern)?.let { match ->
                val cleaned = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(cleaned)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }
        return null
    }

    // ── Balance ──────────────────────────────────────────────────────────
    private val BALANCE_PATTERNS = listOf(
        // "الرصيد المتاح SAR 6,500.00" / "الرصيد:SAR 835.50" / "الرصيد 1,200.00 ريال"
        Regex("""الرصيد\s*(?:المتاح)?\s*[:\-]?\s*(?:SAR)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""),
        // "Avl. Bal: SAR 4,200.00" / "Available balance SAR 890.75"
        Regex("""Avl\.?\s*Bal(?:ance)?\.?\s*[:\-]?\s*(?:SAR)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        // "Balance: SAR 12,000.00" / "New Balance: 320.00"
        Regex("""Balance\s*[:\-]?\s*(?:SAR)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    )

    /** Extracts the available/remaining balance figure, if stated. */
    fun extractBalance(message: String): BigDecimal? {
        for (pattern in BALANCE_PATTERNS) {
            pattern.find(message)?.let { match ->
                val cleaned = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(cleaned)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }
        return null
    }

    // ── Account / card last-4 candidates ─────────────────────────────────
    private val ACCOUNT_PATTERNS = listOf(
        // "حساب:*1234" / "حساب *1234" / "حسابك المنتهي بـ1234"
        Regex("""حساب(?:ك)?\s*(?:رقم)?\s*[:\-]?\s*(?:[X*]{2,4})?(\d{4})\b"""),
        // "بطاقة *1234" / "بطاقة مدى*1234"
        Regex("""بطاقة\s*(?:مدى)?\s*[:\-]?\s*(?:[X*]{1,4})?(\d{4})\b"""),
        // "card ending 1234" / "card **1234" / "mada card 4567*"
        Regex("""card\s*(?:ending\s)?(?:[X*]{2,4})?(\d{4})\*?""", RegexOption.IGNORE_CASE),
        // "account *1234" / "A/C **1234" / "Acct *4567"
        Regex("""A/?C(?:C?T)?\.?\s*[:\-]?\s*(?:[X*]{2,4})?(\d{4})\b""", RegexOption.IGNORE_CASE),
        // "wallet 1234"
        Regex("""wallet\s*[:\-]?\s*(\d{4})\b""", RegexOption.IGNORE_CASE)
    )

    /** Raw 4-digit account/card candidates; caller validates via base helper. */
    fun accountLast4Candidates(message: String): List<String> =
        ACCOUNT_PATTERNS.flatMap { pattern ->
            pattern.findAll(message).mapNotNull { m ->
                val d = m.groupValues[1]
                if (d.length == 4) d else null
            }
        }.distinct()

    // ── Merchant candidates ──────────────────────────────────────────────
    // Returns raw candidates in priority order; caller validates via the
    // base parser's cleanMerchantName/isValidMerchantName.
    private val MERCHANT_PATTERNS = listOf(
        // "التاجر: JARIR BOOKSTORE"
        Regex("""التاجر\s*[:\-]\s*([^\n]+?)(?:\n|$)"""),
        // "لدى: TAMIMI MARKETS"
        Regex("""لدى\s*[:\-]\s*([^\n]+?)(?:\n|$)"""),
        // "مكان السحب:LOCATION"
        Regex("""مكان السحب\s*[:\-]\s*([^\n]+?)(?:\n|$)"""),
        // "at PANDA MARKET on 12/03" / "at STORE XYZ."
        Regex("""\bat\s+([A-Za-z][A-Za-z0-9&.'\-/ ]{2,40}?)(?:\s+on\s|\s+using\s|\s+via\s|[.,\n]|$)""", RegexOption.IGNORE_CASE),
        // "to MOHAMMED ALI." / "from AHMED SALEH"
        Regex("""\b(?:to|from)\s+([A-Za-z][A-Za-z0-9&.'\-/ ]{2,40}?)(?:\s+on\s|\s+via\s|[.,\n]|$)""", RegexOption.IGNORE_CASE)
    )

    fun merchantCandidates(message: String): List<String> =
        MERCHANT_PATTERNS.mapNotNull { pattern ->
            pattern.find(message)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        }
}
