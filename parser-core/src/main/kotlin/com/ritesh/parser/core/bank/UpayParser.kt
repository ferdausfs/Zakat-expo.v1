package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for upay (Bangladesh mobile financial service, operated by
 * United Commercial Bank PLC group).
 *
 * Handles the standard upay notification formats:
 *  - "Dear Customer, Tk 500.00 has been added to your upay account 01712345678 successfully. Fee Tk 0.00. Balance: Tk 1,500.00"
 *  - "You have received Tk 500.00 from 01712345678. Fee Tk 0.00. Balance: Tk 1,500.00"
 *  - "Payment of Tk 250.00 to Aarong was successful. Fee Tk 0.00. Balance: Tk 1,250.00"
 *  - "Send Money of Tk 300.00 to 01812345678 was successful. Fee Tk 5.00. Balance: Tk 950.00"
 *  - "Cash Out of Tk 500.00 was successful. Fee Tk 9.30. Balance: Tk 440.70"
 *  - "Add Money of Tk 2,000.00 was successful. Balance: Tk 3,000.00"
 *
 * Currency is BDT; amounts use the "Tk" (taka) / "BDT" notation. Wallet
 * MSISDN digits act as the account last4, mirroring the bKash/Nagad
 * parsers. Registered at the end of BankParserFactory so no existing
 * parser's behaviour changes.
 */
class UpayParser : BankParser() {

    override fun getBankName() = "upay"

    override fun getCurrency() = "BDT"

    override fun canHandle(sender: String): Boolean {
        // "UPAY", "UPAYBD", "UPAYWALLET", ... but not "URPAY" (Saudi wallet,
        // handled by SaudiWalletParser — "URPAY" does not contain "UPAY").
        return sender.uppercase().contains("UPAY")
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lowerMessage = message.lowercase()

        // Skip OTP / verification
        if (lowerMessage.contains("otp") || lowerMessage.contains("verification code") ||
            lowerMessage.contains("one time password")
        ) {
            return false
        }

        // Skip promotional messages
        if (lowerMessage.contains("you have won") || lowerMessage.contains("lucky") ||
            lowerMessage.contains("offer") || lowerMessage.contains("cashback offer")
        ) {
            return false
        }

        val keywords = listOf(
            "has been added", "has been credited", "you have received",
            "received", "add money", "cash in", "credited", "refund",
            "payment of", "you have paid", "payment", "paid",
            "send money", "cash out", "debited", "purchase"
        )
        return keywords.any { lowerMessage.contains(it) }
    }

    override fun extractAmount(message: String): BigDecimal? {
        val patterns = listOf(
            // "Tk 500.00" / "Tk.500" / "BDT 500.00" / "Taka 1,000.50"
            Regex("""(?:Tk\.?|BDT|Taka)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            // "500.00 Tk" / "500 BDT"
            Regex("""([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:Tk\.?|BDT|Taka)\b""", RegexOption.IGNORE_CASE)
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
            lowerMessage.contains("has been added") -> TransactionType.INCOME
            lowerMessage.contains("has been credited") -> TransactionType.INCOME
            lowerMessage.contains("you have received") -> TransactionType.INCOME
            lowerMessage.contains("received") -> TransactionType.INCOME
            lowerMessage.contains("add money") -> TransactionType.INCOME
            lowerMessage.contains("cash in") -> TransactionType.INCOME
            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("refund") -> TransactionType.INCOME

            lowerMessage.contains("payment of") -> TransactionType.EXPENSE
            lowerMessage.contains("you have paid") -> TransactionType.EXPENSE
            lowerMessage.contains("send money") -> TransactionType.EXPENSE
            lowerMessage.contains("cash out") -> TransactionType.EXPENSE
            lowerMessage.contains("payment") -> TransactionType.EXPENSE
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("purchase") -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractBalance(message: String): BigDecimal? {
        val patterns = listOf(
            Regex("""Balance\s*[:\-]?\s*(?:Tk\.?|BDT|Taka)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""New\s+Balance\s+(?:is\s+)?(?:Tk\.?|BDT|Taka)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""(?:Avl\.?|Available)\s*Bal(?:ance)?\s*[:\-]?\s*(?:Tk\.?|BDT|Taka)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
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
            // "your upay account 01712345678" — wallet MSISDN
            Regex("""account\s*(?:no\.?|number)?\s*[:\-]?\s*(01[3-9]\d{8})""", RegexOption.IGNORE_CASE),
            // "from 01712345678" / "to 01812345678" — counterparty MSISDN
            Regex("""(?:from|to)\s+(01[3-9]\d{8})""", RegexOption.IGNORE_CASE)
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
            // "Payment of Tk 250.00 to Aarong was successful"
            Regex("""\bto\s+([A-Za-z][A-Za-z0-9&.'\-/ ]{2,40}?)(?:\s+was\s|\s+successful\s|[.,]|$)""", RegexOption.IGNORE_CASE),
            // "You have paid Tk 250.00 to MERCHANT ..."
            Regex("""\bpaid\s+(?:Tk\.?|BDT|Taka)\s*[0-9][0-9,]*(?:\.[0-9]{1,2})?\s+to\s+([A-Za-z][A-Za-z0-9&.'\-/ ]{2,40}?)(?:[.,]|\s+Fee\b|$)""", RegexOption.IGNORE_CASE),
            // "received Tk 500.00 from RAHIM"
            Regex("""\bfrom\s+([A-Za-z][A-Za-z0-9&.'\-/ ]{2,40}?)(?:[.,]|\s+Fee\b|$)""", RegexOption.IGNORE_CASE)
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
            Regex("""Trx\.?\s*ID\s*[:\-]?\s*([A-Za-z0-9]{6,20})\b""", RegexOption.IGNORE_CASE),
            Regex("""Txn\.?\s*ID\s*[:\-]?\s*([A-Za-z0-9]{6,20})\b""", RegexOption.IGNORE_CASE),
            Regex("""Ref(?:erence)?\s*(?:No\.?|ID)?\s*[:\-]?\s*([A-Za-z0-9]{6,20})\b""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                return match.groupValues[1].trim()
            }
        }
        return super.extractReference(message)
    }
}
