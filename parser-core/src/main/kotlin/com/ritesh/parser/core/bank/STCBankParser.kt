package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import java.math.BigDecimal

/**
 * STC Bank / stc pay — Saudi Arabia digital bank + wallet.
 *
 * Handles both Arabic and English notification formats:
 *  - "STC Pay\nمدفوع\nمبلغ SAR 60.00\nالتاجر: SALAMA\nالرصيد المتاح SAR 180.00"
 *  - "STC Pay: SAR 60.00 paid to SALAMA INSURANCE using wallet 1234. Available balance SAR 240.00"
 *
 * Senders: STCPay, STC, STCBank ...
 *
 * Extraction delegates to [SaudiSmsSupport] so keyword/regex handling stays
 * consistent across all Saudi parsers.
 */
class STCBankParser : BankParser() {

    override fun getBankName() = "STC Bank"

    override fun getCurrency() = "SAR"

    override fun canHandle(sender: String): Boolean {
        return sender.uppercase().contains("STC")
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
