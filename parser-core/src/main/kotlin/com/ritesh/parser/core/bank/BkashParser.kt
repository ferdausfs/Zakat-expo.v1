package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for bKash (Bangladesh's largest mobile financial service).
 *
 * Handles the standard bKash notification formats:
 *  - "You have received Tk 1000.00 from 01712345678. Fee Tk 0.00. Balance: Tk 5000.00."
 *  - "Payment Tk 250.00 to Aarong (01712345678) successful. Fee Tk 0.00. Balance: Tk 950.00."
 *  - "Cash Out Tk 500.00 to 01712345678 successful. Fee Tk 9.30. Balance: Tk 440.70."
 *  - "Send Money Tk 300.00 to 01812345678 successful. Fee Tk 5.00. Balance: Tk 145.00."
 *  - "Add Money Tk 2000.00 successful. Balance: Tk 7000.00."
 *
 * Currency is BDT; amounts use the "Tk" (taka) notation.
 */
class BkashParser : BankParser() {

    override fun getBankName() = "bKash"

    override fun getCurrency() = "BDT"

    override fun canHandle(sender: String): Boolean {
        return sender.uppercase().contains("BKASH")
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip OTP / verification
        if (lowerMessage.contains("otp") || lowerMessage.contains("verification code") ||
            lowerMessage.contains("one time password")
        ) {
            return false
        }

        // Skip promotional bKash messages
        if (lowerMessage.contains("you have won") || lowerMessage.contains("gift") ||
            lowerMessage.contains("quiz") || lowerMessage.contains("lucky") ||
            lowerMessage.contains("offer") || lowerMessage.contains("discount") ||
            lowerMessage.contains("cashback offer")
        ) {
            return false
        }

        // bKash transaction keywords
        val keywords = listOf(
            "payment received", "received", "add money", "cash in", "credited",
            "refund", "reversal", "payment sent", "payment", "cash out",
            "send money", "debited", "purchase", "paid", "salary"
        )
        return keywords.any { lowerMessage.contains(it) }
    }

    override fun extractAmount(message: String): BigDecimal? {
        // bKash lists the transaction amount before "Fee Tk ..." in every
        // standard format, so the first match is the transaction amount.
        val patterns = listOf(
            Regex("""(?:Tk|BDT)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
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
            // income checks must run first: "payment received" contains "payment"
            lowerMessage.contains("payment received") -> TransactionType.INCOME
            lowerMessage.contains("you have received") -> TransactionType.INCOME
            lowerMessage.contains("received") -> TransactionType.INCOME
            lowerMessage.contains("add money") -> TransactionType.INCOME
            lowerMessage.contains("cash in") -> TransactionType.INCOME
            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("refund") -> TransactionType.INCOME
            lowerMessage.contains("reversal") -> TransactionType.INCOME
            lowerMessage.contains("salary") -> TransactionType.INCOME

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
            Regex("""Balance\s*:?\s*(?:Tk|BDT)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""Bal\s*:?\s*(?:Tk|BDT)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""New\s+Balance\s*:?\s*(?:Tk|BDT)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
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
        // bKash wallet numbers are MSISDNs: 01[3-9]XXXXXXXX
        val walletPattern = Regex("""01[3-9][0-9]{8}""")
        walletPattern.find(message)?.let { match ->
            return extractLast4Digits(match.value)
        }
        return super.extractAccountLast4(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val patterns = listOf(
            // "Payment Tk 250.00 to Aarong (01712345678)"
            Regex("""\bto\s+([A-Za-z][A-Za-z0-9&.'\-/ ]*?)\s*(?:\(|,|\.|;|\sFee\s|\sBalance|$)"""),
            // "You have received Tk 1000.00 from Karim / from 01712345678"
            Regex("""\bfrom\s+([A-Za-z][A-Za-z0-9&.'\-/ ]*?)\s*(?:\(|,|\.|;|\sFee\s|\sBalance|\sRef|$)""")
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
            Regex("""TrxID\s*[:\-]?\s*([A-Za-z0-9]{6,20})\b""", RegexOption.IGNORE_CASE),
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
