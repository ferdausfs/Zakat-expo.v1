package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Saudi banks and payment rails not covered by the dedicated
 * Saudi parsers (AlRajhi, Alinma, SNB AlAhli, SABB, STC Bank):
 *
 *  - Riyad Bank            (senders like "RiyadBank")
 *  - Arab National Bank    ("ANB")
 *  - Banque Saudi Fransi   ("BSF")
 *  - Bank Albilad          ("AlBilad")
 *  - Bank AlJazira         ("BAJ" / "AlJazira")
 *  - Saudi Investment Bank ("SAIB")
 *  - SARIE instant-payment notifications ("SARIE")
 *
 * Covers the common English SAR debit/credit formats shared by these
 * banks, including mada purchases and SARIE transfers. Currency is SAR.
 */
class SaudiBankParser : BankParser() {

    override fun getBankName() = "Saudi Bank"

    override fun getCurrency() = "SAR"

    override fun canHandle(sender: String): Boolean {
        val s = sender.uppercase().trim()
        return s.contains("RIYAD") ||
                s.contains("SARIE") ||
                s.contains("ALBILAD") ||
                s.contains("AL BLAD") ||
                s.contains("ALJAZIRA") ||
                s.contains("AL JAZIRA") ||
                s == "BAJ" ||
                s.contains("SAIB") ||
                s == "ANB" ||
                s.startsWith("ANB.") ||
                s.contains("BSF")
    }

    override fun extractAmount(message: String): BigDecimal? {
        val patterns = listOf(
            // "SAR 150.00" / "SAR150" / "SAR 1,250.50"
            Regex("""SAR\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            // "50 SAR" style
            Regex("""([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*SAR\b""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val amountStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(amountStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lowerMessage = message.lowercase()
        return when {
            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("deposited") -> TransactionType.INCOME
            lowerMessage.contains("refund") -> TransactionType.INCOME
            lowerMessage.contains("received") -> TransactionType.INCOME
            lowerMessage.contains("inward") -> TransactionType.INCOME
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("withdrawn") -> TransactionType.EXPENSE
            lowerMessage.contains("spent") -> TransactionType.EXPENSE
            lowerMessage.contains("purchase") -> TransactionType.EXPENSE
            lowerMessage.contains("paid") -> TransactionType.EXPENSE
            lowerMessage.contains("payment of") -> TransactionType.EXPENSE
            lowerMessage.contains("outward") -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun isTransactionMessage(message: String): Boolean {
        if (super.isTransactionMessage(message)) return true
        // mada / purchase notifications often lack the base keywords;
        // note: bare "SAR"/"SARIE" mentions are NOT sufficient on their own
        // (payment requests also mention them).
        val lowerMessage = message.lowercase()
        if (lowerMessage.contains("otp") || lowerMessage.contains("verification code")) {
            return false
        }
        return lowerMessage.contains("purchase")
    }

    override fun extractBalance(message: String): BigDecimal? {
        val patterns = listOf(
            Regex("""Bal\.?\s*[:\-]?\s*SAR\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""Balance\s*[:\-]?\s*SAR\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""Avl\.?\s*Bal\.?\s*[:\-]?\s*SAR\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val balanceStr = match.groupValues[1].replace(",", "")
                return try {
                    BigDecimal(balanceStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }
        return super.extractBalance(message)
    }

    override fun extractAccountLast4(message: String): String? {
        val patterns = listOf(
            // "A/C **1234", "Acct *1234", "account XX1234", "Account *4567"
            Regex("""A/?C(?:C?T)?\.?\s*(?:No\.?)?\s*[:\-]?\s*(?:XX|\*{1,4}|x{2})?(\d{4})\b""", RegexOption.IGNORE_CASE),
            Regex("""Account\s*(?:No\.?)?\s*[:\-]?\s*(?:XX|\*{1,4}|x{2})?(\d{4})\b""", RegexOption.IGNORE_CASE),
            // "card **1234" / "Card: *1234"
            Regex("""Card\s*[:\-]?\s*(?:XX|\*{1,4}|x{2})?(\d{4})\b""", RegexOption.IGNORE_CASE),
            // "IBAN **7788" / "IBAN SA03*1234" masked styles
            Regex("""IBAN\s*[:\-]?\s*(?:[A-Z]{2}[0-9A-Z]*)?(?:[X*]{2,4})?(\d{4})\b""")
        )
        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val last4 = extractLast4Digits(match.groupValues[1])
                if (last4 != null && isValidAccountLast4(last4, match.value, message)) {
                    return last4
                }
            }
        }
        return super.extractAccountLast4(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val patterns = listOf(
            // "at AL NAHDI PHARMACY on 01/02" / "at Vendor."
            Regex("""\bat\s+([A-Z][A-Za-z0-9&.'\- ]{2,40}?)(?:\s+on\s|\s+using\s|\s+via\s|[.,]|$)"""),
            // SARIE received: "from AHMED ALI via ..." (before via so the
            // counterparty wins over the bank name)
            Regex("""\bfrom\s+([A-Z][A-Za-z0-9&.'\- ]{2,40}?)(?:\s+via\s|\s+on\s|[.,]|$)"""),
            // "via Samsung Wallet" / "via SADAD"
            Regex("""\bvia\s+([A-Z][A-Za-z0-9&.'\- ]{2,30}?)(?:\s+on\s|[.,]|$)"""),
            // "to VENDOR ..." for outgoing transfers
            Regex("""\bto\s+([A-Z][A-Za-z0-9&.'\- ]{2,40}?)(?:\s+via\s|\s+on\s|[.,]|$)""")
        )
        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }
        }
        return super.extractMerchant(message, sender)
    }

    override fun extractReference(message: String): String? {
        val patterns = listOf(
            Regex("""Ref(?:erence)?\.?(?:\s*No\.?)?\s*[:\-]?\s*([A-Z0-9]{6,20})\b""", RegexOption.IGNORE_CASE),
            Regex("""Trx(?:N)?\.?(?:\s*ID)?\s*[:\-]?\s*([A-Z0-9]{6,20})\b""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1].trim()
            }
        }
        return super.extractReference(message)
    }
}
