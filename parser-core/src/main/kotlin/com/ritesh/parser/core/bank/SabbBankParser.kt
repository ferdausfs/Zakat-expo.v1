package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import java.math.BigDecimal

/**
 * SABB (Saudi Awwal Bank, formerly HSBC Saudi) — Saudi Arabia.
 *
 * Handles both Arabic and English notification formats:
 *  - "SABB\nشراء\nبطاقة *1234\nمبلغ SAR 55.25\nالتاجر: DUNKIN DONUTS\nالرصيد SAR 835.50"
 *  - "SABB: SAR 55.25 spent using debit card ending 1234 at DUNKIN DONUTS. Available balance SAR 890.75"
 *
 * Senders: SABB, SABB-1, SaudiAwwal ...
 *
 * Extraction delegates to [SaudiSmsSupport] so keyword/regex handling stays
 * consistent across all Saudi parsers.
 */
class SabbBankParser : BankParser() {

    override fun getBankName() = "SABB Bank"

    override fun getCurrency() = "SAR"

    override fun canHandle(sender: String): Boolean {
        return sender.uppercase().contains("SABB") ||
                sender.uppercase().contains("AWWAL")
    }

    override fun isTransactionMessage(message: String): Boolean =
        SaudiSmsSupport.isTransactionMessage(message)

    override fun extractAmount(message: String): BigDecimal? =
        SaudiSmsSupport.extractAmount(message)

    override fun extractTransactionType(message: String): TransactionType? =
        SaudiSmsSupport.transactionType(message)

    override fun extractBalance(message: String): BigDecimal? =
        SaudiSmsSupport.extractBalance(message)
            ?: super.extractBalance(message)

    override fun extractAccountLast4(message: String): String? {
        for (candidate in SaudiSmsSupport.accountLast4Candidates(message)) {
            val last4 = extractLast4Digits(candidate)
            if (last4 != null) return last4
        }
        return super.extractAccountLast4(message)
    }

    override fun extractMerchant(message: String, sender: String): String? {
        for (candidate in SaudiSmsSupport.merchantCandidates(message)) {
            val merchant = cleanMerchantName(candidate)
            if (isValidMerchantName(merchant)) return merchant
        }
        return super.extractMerchant(message, sender)
    }
}
