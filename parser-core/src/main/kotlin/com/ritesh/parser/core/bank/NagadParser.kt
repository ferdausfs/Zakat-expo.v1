package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Nagad (Bangladesh postal-arm mobile financial service).
 *
 * Handles the standard Nagad notification formats:
 *  - "You have received BDT 500.00 from 01712345678. Fee: BDT 0.00, Balance: BDT 1500.00"
 *  - "Payment BDT 200.00 to Aarong (01712345678) successful. Fee: BDT 0.00, Balance: BDT 1300.00"
 *  - "Cash Out BDT 1000.00 from Agent 01712345678. Fee: BDT 14.90, Balance: BDT 985.10"
 *  - "Send Money BDT 500.00 to 01812345678 successful. Balance: BDT 480.00"
 *
 * Currency is BDT; amounts use "BDT" (sometimes "Tk") notation.
 */
class NagadParser : BankParser() {

    override fun getBankName() = "Nagad"

    override fun getCurrency() = "BDT"

    override fun canHandle(sender: String): Boolean {
        return sender.uppercase().contains("NAGAD")
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        if (lowerMessage.contains("otp") || lowerMessage.contains("verification code") ||
            lowerMessage.contains("one time password")
        ) {
            return false
        }

        if (lowerMessage.contains("you have won") || lowerMessage.contains("gift") ||
            lowerMessage.contains("quiz") || lowerMessage.contains("lucky") ||
            lowerMessage.contains("offer") || lowerMessage.contains("discount") ||
            lowerMessage.contains("cashback offer")
        ) {
            return false
        }

        val keywords = listOf(
            "payment received", "received", "add money", "cash in", "credited",
            "refund", "reversal", "payment sent", "payment", "cash out",
            "send money", "debited", "purchase", "paid"
        )
        return keywords.any { lowerMessage.contains(it) }
    }

    override fun extractAmount(message: String): BigDecimal? {
        val patterns = listOf(
            Regex("""(?:BDT|Tk)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:BDT|Tk)\b""", RegexOption.IGNORE_CASE)
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
            lowerMessage.contains("payment received") -> TransactionType.INCOME
            lowerMessage.contains("you have received") -> TransactionType.INCOME
            lowerMessage.contains("received") -> TransactionType.INCOME
            lowerMessage.contains("add money") -> TransactionType.INCOME
            lowerMessage.contains("cash in") -> TransactionType.INCOME
            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("refund") -> TransactionType.INCOME
            lowerMessage.contains("reversal") -> TransactionType.INCOME

            lowerMessage.contains("cash out") -> TransactionType.EXPENSE
            lowerMessage.contains("send money") -> TransactionType.EXPENSE
            lowerMessage.contains("payment sent") -> TransactionType.EXPENSE
            lowerMessage.contains("payment") -> TransactionType.EXPENSE
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("purchase") -> TransactionType.EXPENSE
            lowerMessage.contains("paid") -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractBalance(message: String): BigDecimal? {
        val patterns = listOf(
            Regex("""Balance\s*:?\s*(?:BDT|Tk)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""Bal\s*:?\s*(?:BDT|Tk)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
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
        val walletPattern = Regex("""01[3-9][0-9]{8}""")
        walletPattern.find(message)?.let { match ->
            return extractLast4Digits(match.value)
        }
        return super.extractAccountLast4(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val patterns = listOf(
            Regex("""\bto\s+([A-Za-z][A-Za-z0-9&.'\-/ ]*?)\s*(?:\(|,|\.|;|\sFee\s|\sBalance|$)"""),
            Regex("""\bfrom\s+(?:Agent\s+)?([A-Za-z][A-Za-z0-9&.'\-/ ]*?)\s*(?:\(|,|\.|;|\sFee\s|\sBalance|\sRef|$)""")
        )
        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                // Reject MSISDN capture artifacts (agent/wallet numbers)
                if (merchant.contains(Regex("""01[3-9][0-9]{8}"""))) {
                    return@let
                }
                if (isValidMerchantName(merchant)) {
                    return merchant
                }
            }
        }
        return super.extractMerchant(message, sender)
    }

    override fun extractReference(message: String): String? {
        val patterns = listOf(
            Regex("""Trx\s?ID\s*[:\-]?\s*([A-Za-z0-9]{6,20})\b""", RegexOption.IGNORE_CASE),
            Regex("""Ref(?:erence)?\s*[:\-]?\s*([A-Za-z0-9]{6,20})\b""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1].trim()
            }
        }
        return super.extractReference(message)
    }
}
