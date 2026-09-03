package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Generic parser for Bangladeshi commercial banks.
 *
 * Covers the common English debit/credit SMS formats shared by BD banks:
 *  - BRAC Bank, City Bank, EBL, IBBL, UCB, MTB, Pubali, Prime Bank,
 *    Bank Asia, Southeast Bank, Trust Bank, Dutch-Bangla Bank (bank
 *    accounts), Jamuna Bank, NCC Bank, Shahjalal Bank, Al-Arafah Bank,
 *    Midland Bank, Dhaka Bank.
 *
 * Typical formats:
 *  - "Your A/C **1234 is debited with BDT 1,500.00 on 01-Feb-2026 ... Avl Bal: BDT 12,345.67"
 *  - "Tk 5,000.00 credited to your A/C XX1234 on 01/02/2026"
 *  - "You have withdrawn BDT 3,000.00 from ATM ..."
 *  - "Trx for BDT 250.00 to DARAZ using Internet Banking"
 *
 * Currency is BDT; amounts use "BDT" or "Tk" notation. Sender tokens are
 * kept specific (e.g. "PRIMEBANK", not bare "PRIME") so existing parsers
 * for other countries keep priority.
 */
class BangladeshBankParser : BankParser() {

    override fun getBankName() = "Bangladesh Bank"

    override fun getCurrency() = "BDT"

    override fun canHandle(sender: String): Boolean {
        val s = sender.uppercase()
        return s.contains("BRAC") ||
                s.contains("CITYBANK") || s.contains("CITY BANK") || s == "CITY" ||
                s == "EBL" || s.contains("EBLBD") ||
                s.contains("IBBL") ||
                s == "UCB" || s.contains("UCBBANK") ||
                s.contains("MTB") ||
                s.contains("PUBLALI") ||
                s.contains("PRIMEBANK") || s.contains("PRIME BANK") ||
                s.contains("BANKASIA") || s.contains("BANK ASIA") ||
                s.contains("SOUTHEAST") ||
                s.contains("TRUSTBANK") || s.contains("TRUST BANK") ||
                s.contains("DUTCHBANG") || s.contains("DUTCH-BANG") || s.contains("DUTCH BANG") ||
                s.contains("JAMUNA") ||
                s.contains("NCC") ||
                s.contains("SHAHJAL") ||
                s.contains("ALARAFAH") || s.contains("AL-ARAFAH") || s.contains("AL ARAFAH") ||
                s.contains("MIDLAND") ||
                s.contains("DHAKABANK") || s.contains("DHAKA BANK")
    }

    override fun isTransactionMessage(message: String): Boolean {
        if (super.isTransactionMessage(message)) return true
        val lowerMessage = message.lowercase()
        if (lowerMessage.contains("otp") || lowerMessage.contains("verification code")) {
            return false
        }
        // BD-specific phrasing without the base keywords
        return lowerMessage.contains("trx") || lowerMessage.contains("purchase") ||
                lowerMessage.contains("payment of") || lowerMessage.contains("internet banking")
    }

    override fun extractAmount(message: String): BigDecimal? {
        val patterns = listOf(
            // "BDT 1,500.00" / "Tk 5000" / "Tk.500.00" / "Taka 1,000.50"
            Regex("""(?:BDT|Tk\.?|Taka)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            // "1,500.00 BDT" / "500 Tk"
            Regex("""([0-9][0-9,]*(?:\.[0-9]{1,2})?)\s*(?:BDT|Tk|Taka)\b""", RegexOption.IGNORE_CASE)
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
            lowerMessage.contains("received") -> TransactionType.INCOME
            lowerMessage.contains("refund") -> TransactionType.INCOME

            lowerMessage.contains("debited") -> TransactionType.EXPENSE
            lowerMessage.contains("withdrawn") -> TransactionType.EXPENSE
            lowerMessage.contains("withdrawal") -> TransactionType.EXPENSE
            lowerMessage.contains("purchase") -> TransactionType.EXPENSE
            lowerMessage.contains("paid") -> TransactionType.EXPENSE
            lowerMessage.contains("payment") -> TransactionType.EXPENSE
            lowerMessage.contains("trx") -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractBalance(message: String): BigDecimal? {
        val patterns = listOf(
            Regex("""Avl\.?\s*Bal(?:ance)?\s*[:\-]?\s*(?:BDT|Tk\.?)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""Available\s+Bal(?:ance)?\s*[:\-]?\s*(?:BDT|Tk\.?)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
            Regex("""(?:New\s+)?Bal(?:ance)?\s*[:\-]?\s*(?:BDT|Tk\.?)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
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
            // "A/C **1234", "A/C XX1234", "A/C No. 12345678" (last 4 used)
            Regex("""A/?C\.?\s*(?:No\.?)?\s*[:\-]?\s*(?:XX|\*{2,}|x{2})?(\d{4})\b""", RegexOption.IGNORE_CASE),
            Regex("""Account\s*(?:No\.?)?\s*[:\-]?\s*(?:XX|\*{2,}|x{2})?(\d{4})\b""", RegexOption.IGNORE_CASE),
            Regex("""Card\s*(?:No\.?)?\s*[:\-]?\s*(?:XX|\*{2,}|x{2})?(\d{4})\b""", RegexOption.IGNORE_CASE)
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
            // "at DARAZ.COM.BD on 01-Feb" / "at ATM Gulshan"
            Regex("""\bat\s+([A-Za-z][A-Za-z0-9&.'\-/ ]{2,40}?)(?:\s+on\s|\s+using\s|[.,]|$)"""),
            // "to DARAZ using Internet Banking" / "to M/S Rahman Traders"
            Regex("""\bto\s+(?:M/S\.?\s*)?([A-Za-z][A-Za-z0-9&.'\-/ ]{2,40}?)(?:\s+using\s|\s+on\s|[.,]|$)"""),
            // "for Online Purchase at Daraz"
            Regex("""\bpurchase\s+at\s+([A-Za-z][A-Za-z0-9&.'\-/ ]{2,40}?)(?:\s+on\s|[.,]|$)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val merchant = cleanMerchantName(match.groupValues[1].trim())
                // Reject capture artifacts like "your A/C XX1234" or
                // "your account 7788" picked up from credit messages.
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
            Regex("""Txn\s*ID\s*[:\-]?\s*([A-Za-z0-9]{6,20})\b""", RegexOption.IGNORE_CASE),
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
