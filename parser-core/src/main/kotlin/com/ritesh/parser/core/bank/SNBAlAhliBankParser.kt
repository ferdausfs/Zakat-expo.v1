package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Saudi National Bank (SNB / AlAhli) — Saudi Arabia.
 *
 * Handles both Arabic and English notification formats:
 *  - "خصم\nحساب:*1234\nمبلغ SAR 350.00\nالتاجر: JARIR\nالرصيد المتاح SAR 6,500.00"
 *  - "SNB: SAR 89.00 debited from account *1234 for purchase at JARIR. Avail. Bal: SAR 6,411.00"
 *
 * Senders: SNBAHLI, SNB, AlAhli, AlAhliBank, الأهلي ...
 *
 * Extraction delegates to [SaudiSmsSupport] so keyword/regex handling stays
 * consistent across all Saudi parsers.
 */
class SNBAlAhliBankParser : BankParser() {

    override fun getBankName() = "SNB AlAhli"

    override fun getCurrency() = "SAR"

    override fun canHandle(sender: String): Boolean {
        val upperSender = sender.uppercase()
        return upperSender.contains("SNB") || upperSender.contains("ALAHLI") ||
                sender.contains("الأهلي")
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
