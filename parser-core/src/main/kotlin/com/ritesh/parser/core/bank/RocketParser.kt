package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Rocket (Dutch-Bangla Bank's mobile financial service).
 *
 * Handles the standard Rocket notification formats:
 *  - "You have debited Tk 500.00 for Cash Out to 01712345678. Fee: Tk 9.25. New Bal: Tk 2500.75"
 *  - "You have received Tk 1000.00 from 01812345678. Fee Tk 0.00. New Bal Tk 3500.75"
 *  - "You have debited Tk 100.00 for Payment to Aarong. Fee Tk 0.00."
 *  - "Your Rocket Account 01712345678 is debited by Tk 200.00 for Payment"
 *
 * Registered before [BangladeshBankParser] so the "DBBL" sender resolves to
 * this MFS parser first; unrecognised DBBL bank-account SMS fall through.
 * Currency is BDT.
 */
class RocketParser : BankParser() {

    override fun getBankName() = "Rocket"

    override fun getCurrency() = "BDT"

    override fun canHandle(sender: String): Boolean {
        val s = sender.uppercase()
        return s.contains("ROCKET") || s.contains("DBBL")
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        if (lowerMessage.contains("otp") || lowerMessage.contains("verification code") ||
            lowerMessage.contains("one time password")
        ) {
            return false
        }

        if (lowerMessage.contains("you have won") || lowerMessage.contains("offer") ||
            lowerMessage.contains("discount")
        ) {
            return false
        }

        val keywords = listOf(
            "received", "credited", "add money", "refund", "reversal",
            "debited", "cash out", "send money", "payment", "purchase", "paid"
        )
        return keywords.any { lowerMessage.contains(it) }
    }

    override fun extractAmount(message: String): BigDecimal? {
        val patterns = listOf(
            Regex("""(?:Tk|BDT)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""debited by\s*(?:Tk|BDT)?\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:Tk|BDT)\b""", RegexOption.IGNORE_CASE)
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
            lowerMessage.contains("you have received") -> TransactionType.INCOME
            lowerMessage.contains("received") -> TransactionType.INCOME
            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("add money") -> TransactionType.INCOME
            lowerMessage.contains("refund") -> TransactionType.INCOME
            lowerMessage.contains("reversal") -> TransactionType.INCOME

            lowerMessage.contains("cash out") -> TransactionType.EXPENSE
            lowerMessage.contains("send money") -> TransactionType.EXPENSE
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("payment") -> TransactionType.EXPENSE
            lowerMessage.contains("purchase") -> TransactionType.EXPENSE
            lowerMessage.contains("paid") -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractBalance(message: String): BigDecimal? {
        val patterns = listOf(
            Regex("""New\s+Bal(?:ance)?\s*:?\s*(?:Tk|BDT)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""Balance\s*:?\s*(?:Tk|BDT)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""Bal\s*:?\s*(?:Tk|BDT)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
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
            Regex("""\bto\s+([A-Za-z][A-Za-z0-9&.'\-/ ]*?)\s*(?:\(|,|\.|;|\sFee\s|\sNew\s|\sBalance|$)"""),
            Regex("""\bfrom\s+([A-Za-z][A-Za-z0-9&.'\-/ ]*?)\s*(?:\(|,|\.|;|\sFee\s|\sNew\s|\sRef|$)"""),
            // "debited ... for Cash Out to 01712345678" -> describe as Cash Out
            Regex("""\bfor\s+(Cash Out|Send Money|Payment|ATM Withdraw)\b""", RegexOption.IGNORE_CASE)
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
