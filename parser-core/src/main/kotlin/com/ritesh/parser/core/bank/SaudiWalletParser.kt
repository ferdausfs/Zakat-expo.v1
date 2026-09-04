package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Saudi mobile payment wallet apps that do not belong to a
 * dedicated bank parser:
 *  - mada Pay (sender IDs like "MADAPAY" / "MADA PAY")
 *  - urpay (Riyad Bank wallet, sender "URPAY")
 *  - Alinma Pay (sender "ALINMAPAY" — note: senders containing "ALINMA"
 *    are currently claimed by AlinmaBankParser which handles them first;
 *    this parser additionally matches the wallet sender and formats so
 *    wallet notifications never fall through the cracks)
 *
 * Typical formats:
 *  - "mada Pay: Purchase of SAR 45.50 at STARBUCKS RIYADH on 01/02. Avl Bal: SAR 1,234.56"
 *  - "urpay: You have sent SAR 75.00 to AHMED. New balance: SAR 300.00"
 *  - "You received SAR 500.00 from SALEM via urpay. Balance: SAR 800.00"
 *  - "alinma pay: Payment of SAR 100.00 to TOKEN STUDIO. Available balance SAR 900.00"
 *
 * Currency is SAR. Registered at the end of BankParserFactory so no
 * existing parser's behaviour changes.
 */
class SaudiWalletParser : BankParser() {

    override fun getBankName() = "Saudi Wallet"

    override fun getCurrency() = "SAR"

    override fun canHandle(sender: String): Boolean {
        val s = sender.uppercase()
        return s.contains("MADAPAY") || s.contains("MADA PAY") ||
                s.contains("URPAY") ||
                s.contains("ALINMAPAY") || s.contains("ALINMA PAY")
    }

    override fun isTransactionMessage(message: String): Boolean {
        if (super.isTransactionMessage(message)) return true
        val lowerMessage = message.lowercase()
        if (lowerMessage.contains("otp") || lowerMessage.contains("verification code")) {
            return false
        }
        // Wallet-specific phrasing without the base keywords
        return lowerMessage.contains("purchase of") || lowerMessage.contains("you have sent") ||
                lowerMessage.contains("you received") || lowerMessage.contains("topped up") ||
                lowerMessage.contains("top-up") || lowerMessage.contains("payment of") ||
                lowerMessage.contains("send money")
    }

    override fun extractAmount(message: String): BigDecimal? {
        val patterns = listOf(
            // "SAR 45.50" / "SAR 1,234.56"
            Regex("""SAR\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            // "45.50 SAR"
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
            lowerMessage.contains("you received") -> TransactionType.INCOME
            lowerMessage.contains("received") -> TransactionType.INCOME
            lowerMessage.contains("credited") -> TransactionType.INCOME
            lowerMessage.contains("refund") -> TransactionType.INCOME
            lowerMessage.contains("topped up") || lowerMessage.contains("top-up") ||
                lowerMessage.contains("top up") -> TransactionType.INCOME

            lowerMessage.contains("purchase") -> TransactionType.EXPENSE
            lowerMessage.contains("you have sent") -> TransactionType.EXPENSE
            lowerMessage.contains("sent") -> TransactionType.EXPENSE
            lowerMessage.contains("payment") -> TransactionType.EXPENSE
            lowerMessage.contains("paid") -> TransactionType.EXPENSE
            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractBalance(message: String): BigDecimal? {
        val patterns = listOf(
            Regex("""(?:Avl\.?\s*Bal(?:ance)?|Available\s+Balance|New\s+Balance|Balance)\s*[:\-]?\s*(?:SAR)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""(?:SAR)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:is\s+your|available)""", RegexOption.IGNORE_CASE)
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
            // "card XX1234" / "card ending 1234"
            Regex("""Card\s*(?:No\.?)?\s*(?:XX|\*{2,}|x{2}|ending\s)?(\d{4})\b""", RegexOption.IGNORE_CASE),
            // "mada Pay (1234)" / "wallet 1234"
            Regex("""(?:mada\s*Pay|urpay|alinma\s*Pay|Wallet)\s*(?:\()?(\d{4})(?:\))?""", RegexOption.IGNORE_CASE)
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
            // "Purchase of SAR 45.50 at STARBUCKS RIYADH on 01/02"
            Regex("""\bat\s+([A-Za-z][A-Za-z0-9&.'\-/ ]{2,40}?)(?:\s+on\s|\s+using\s|[.,]|$)""", RegexOption.IGNORE_CASE),
            // "sent SAR 75.00 to AHMED" / "Payment of SAR 100.00 to TOKEN STUDIO"
            Regex("""\bto\s+([A-Za-z][A-Za-z0-9&.'\-/ ]{2,40}?)(?:[.,]|\s+on\s|\s+New\b|\s+Avl\b|\s+Balance\b|$)""", RegexOption.IGNORE_CASE),
            // "received SAR 500.00 from SALEM"
            Regex("""\bfrom\s+([A-Za-z][A-Za-z0-9&.'\-/ ]{2,40}?)(?:[.,]|\s+via\b|\s+Balance\b|$)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                val lowerMerchant = merchant.lowercase()
                if (!lowerMerchant.startsWith("your") && !lowerMerchant.contains("a/c") &&
                    !lowerMerchant.contains("account") && isValidMerchantName(merchant)
                ) {
                    return merchant
                }
            }
        }
        return super.extractMerchant(message, sender)
    }

    override fun extractReference(message: String): String? {
        val patterns = listOf(
            Regex("""Trx\.?\s*ID\s*[:\-]?\s*([A-Za-z0-9]{6,20})\b""", RegexOption.IGNORE_CASE),
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
