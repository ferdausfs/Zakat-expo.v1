package com.ritesh.parser.core.bank

import com.ritesh.parser.core.ParsedTransaction
import com.ritesh.parser.core.TransactionType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.math.BigDecimal

/**
 * End-to-end coverage proof for the Saudi bank/wallet SMS support requested
 * by the user. Every case is routed through [BankParserFactory.getParsers]
 * + firstNotNullOfOrNull — the EXACT same routing the manual-scan pipeline
 * (OptimizedSmsReaderWorker.parseMessage) uses, so a pass here proves the
 * message is recognized during a real scan, not just by the parser class.
 */
class SaudiBankCoverageTest {

    /** Mirrors OptimizedSmsReaderWorker.parseMessage routing. */
    private fun scanRoute(sender: String, body: String): ParsedTransaction? {
        val parsers = BankParserFactory.getParsers(sender)
        assertTrue(
            parsers.isNotEmpty(),
            "NO PARSER CLAIMS SENDER '$sender' — message would be dropped as 'No parser found'"
        )
        return parsers.firstNotNullOfOrNull { it.parse(body, sender, 1_740_000_000_000) }
    }

    private data class Case(
        val bank: String,
        val sender: String,
        val body: String,
        val expectedAmount: BigDecimal,
        val expectedType: TransactionType,
        val expectedBankNameContains: List<String> = emptyList()
    )

    private fun assertCase(case: Case) {
        val parsed = scanRoute(case.sender, case.body)
        assertNotNull(
            parsed,
            "${case.bank}: message was NOT parsed (silently dropped during scan)\n    sender=${case.sender}\n    body=${case.body.take(120)}"
        )
        parsed!!
        assertEquals(case.expectedAmount, parsed.amount, "${case.bank}: wrong amount")
        assertEquals(case.expectedType, parsed.type, "${case.bank}: wrong type")
        assertEquals("SAR", parsed.currency, "${case.bank}: wrong currency")
        if (case.expectedBankNameContains.isNotEmpty()) {
            assertTrue(
                case.expectedBankNameContains.any { parsed.bankName.contains(it, ignoreCase = true) },
                "${case.bank}: bankName was '${parsed.bankName}'"
            )
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 1. Al Rajhi Bank
    // ════════════════════════════════════════════════════════════════════
    @Test
    fun `alRajhi - mada purchase English`() = assertCase(
        Case(
            bank = "Al Rajhi Bank", sender = "AlRajhiBank",
            body = "Al Rajhi Bank: Purchase of SAR 125.50 with mada card 4567* at PANDA MARKET on 12/03. Avail. Bal: SAR 4,200.00",
            expectedAmount = BigDecimal("125.50"), expectedType = TransactionType.EXPENSE,
            expectedBankNameContains = listOf("Al Rajhi")
        )
    )

    @Test
    fun `alRajhi - Arabic purchase`() = assertCase(
        Case(
            bank = "Al Rajhi Bank", sender = "AlRajhi",
            body = "AlRajhi\nبطاقة:4567*شراء\nمبلغ:SAR 25.00\nالتاجر: PANDA\nالرصيد:SAR 4,200.00",
            expectedAmount = BigDecimal("25.00"), expectedType = TransactionType.EXPENSE
        )
    )

    @Test
    fun `alRajhi - Arabic salary credit`() = assertCase(
        Case(
            bank = "Al Rajhi Bank", sender = "AlRajhiBank",
            body = "AlRajhi\nحوالة واردة\nمبلغ:SAR 8,500.00\nمن: MINISTRY OF FINANCE\nالرصيد:SAR 12,750.00",
            expectedAmount = BigDecimal("8500.00"), expectedType = TransactionType.INCOME
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // 2. Saudi National Bank (SNB / AlAhli)
    // ════════════════════════════════════════════════════════════════════
    @Test
    fun `snb - Arabic debit`() = assertCase(
        Case(
            bank = "Saudi National Bank", sender = "SNBAHLI",
            body = "خصم\nحساب:*1234\nمبلغ SAR 350.00\nالتاجر: JARIR BOOKSTORE\nالرصيد المتاح SAR 6,500.00",
            expectedAmount = BigDecimal("350.00"), expectedType = TransactionType.EXPENSE,
            expectedBankNameContains = listOf("SNB")
        )
    )

    @Test
    fun `snb - English debit`() = assertCase(
        Case(
            bank = "Saudi National Bank", sender = "SNB",
            body = "SNB: SAR 89.00 debited from account *1234 for purchase at JARIR. Avail. Bal: SAR 6,411.00",
            expectedAmount = BigDecimal("89.00"), expectedType = TransactionType.EXPENSE,
            expectedBankNameContains = listOf("SNB")
        )
    )

    @Test
    fun `snb - Arabic credit`() = assertCase(
        Case(
            bank = "Saudi National Bank", sender = "AlAhli",
            body = "إيداع\nحساب:*1234\nمبلغ SAR 2,000.00\nمن: AHMED SALEH\nالرصيد المتاح SAR 8,500.00",
            expectedAmount = BigDecimal("2000.00"), expectedType = TransactionType.INCOME
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // 3. Riyad Bank
    // ════════════════════════════════════════════════════════════════════
    @Test
    fun `riyad - mada POS purchase`() = assertCase(
        Case(
            bank = "Riyad Bank", sender = "RiyadBank",
            body = "Riyad Bank: A purchase of SAR 150.00 using mada card **1234 at AL NAHDI PHARMACY on 01/02/2026. Acct **5678. Bal SAR 5,500.75",
            expectedAmount = BigDecimal("150.00"), expectedType = TransactionType.EXPENSE,
            expectedBankNameContains = listOf("Riyad")
        )
    )

    @Test
    fun `riyad - Arabic debit`() = assertCase(
        Case(
            bank = "Riyad Bank", sender = "RiyadBank",
            body = "تم خصم مبلغ SAR 75.00 من حسابك *5678 شراء من متجر\nالرصيد المتاح: SAR 5,425.75",
            expectedAmount = BigDecimal("75.00"), expectedType = TransactionType.EXPENSE
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // 4. SABB
    // ════════════════════════════════════════════════════════════════════
    @Test
    fun `sabb - English card spend`() = assertCase(
        Case(
            bank = "SABB", sender = "SABB",
            body = "SABB: SAR 55.25 spent using debit card ending 1234 at DUNKIN DONUTS on 05-03. Available balance SAR 890.75",
            expectedAmount = BigDecimal("55.25"), expectedType = TransactionType.EXPENSE,
            expectedBankNameContains = listOf("SABB")
        )
    )

    @Test
    fun `sabb - Arabic purchase`() = assertCase(
        Case(
            bank = "SABB", sender = "SABB",
            body = "SABB\nشراء\nبطاقة *1234\nمبلغ SAR 55.25\nالتاجر: DUNKIN DONUTS\nالرصيد SAR 835.50",
            expectedAmount = BigDecimal("55.25"), expectedType = TransactionType.EXPENSE
        )
    )

    @Test
    fun `sabb - Arabic credit`() = assertCase(
        Case(
            bank = "SABB", sender = "SABB",
            body = "SABB\nإيداع\nحساب *1234\nمبلغ SAR 3,000.00\nالرصيد المتاح SAR 3,835.50",
            expectedAmount = BigDecimal("3000.00"), expectedType = TransactionType.INCOME
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // 5. Alinma Bank
    // ════════════════════════════════════════════════════════════════════
    @Test
    fun `alinma - Arabic purchase`() = assertCase(
        Case(
            bank = "Alinma Bank", sender = "AlinmaBank",
            body = "شراء\nبطاقة مدى*1234\nبمبلغ: 75.50 SAR\nلدى: TAMIMI MARKETS\nالرصيد: 4,500.00 SAR",
            expectedAmount = BigDecimal("75.50"), expectedType = TransactionType.EXPENSE,
            expectedBankNameContains = listOf("Alinma")
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // 6. Banque Saudi Fransi
    // ════════════════════════════════════════════════════════════════════
    @Test
    fun `bsf - English debit`() = assertCase(
        Case(
            bank = "Banque Saudi Fransi", sender = "BSF",
            body = "BSF: SAR 1,200.00 debited from your account for online purchase on Amazon. Card: **9911. Bal SAR 3,300.00",
            expectedAmount = BigDecimal("1200.00"), expectedType = TransactionType.EXPENSE
        )
    )

    @Test
    fun `bsf - Arabic debit`() = assertCase(
        Case(
            bank = "Banque Saudi Fransi", sender = "BSF",
            body = "BSF\nتم خصم SAR 120.00 من حسابك *5678\nالرصيد المتاح SAR 2,000.00",
            expectedAmount = BigDecimal("120.00"), expectedType = TransactionType.EXPENSE
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // 7. Arab National Bank
    // ════════════════════════════════════════════════════════════════════
    @Test
    fun `anb - English debit`() = assertCase(
        Case(
            bank = "Arab National Bank", sender = "ANB",
            body = "Dear Customer, Your account ANB *4567 is debited with SAR 89.99 on 03-02-2026 for purchase at STORE XYZ. Avl Bal: SAR 910.01",
            expectedAmount = BigDecimal("89.99"), expectedType = TransactionType.EXPENSE
        )
    )

    @Test
    fun `anb - Arabic debit dotted sender`() = assertCase(
        Case(
            bank = "Arab National Bank", sender = "ANB.9200",
            body = "ANB: تم خصم مبلغ SAR 300.00 من حسابك *4567\nالرصيد: SAR 610.01",
            expectedAmount = BigDecimal("300.00"), expectedType = TransactionType.EXPENSE
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // 8. Bank Albilad
    // ════════════════════════════════════════════════════════════════════
    @Test
    fun `albilad - English debit`() = assertCase(
        Case(
            bank = "Bank Albilad", sender = "AlBilad",
            body = "Bank Albilad: SAR 45.00 debited from account *8899 for purchase at HUNGERSTATION. Avl Bal SAR 1,055.00",
            expectedAmount = BigDecimal("45.00"), expectedType = TransactionType.EXPENSE
        )
    )

    @Test
    fun `albilad - Arabic purchase`() = assertCase(
        Case(
            bank = "Bank Albilad", sender = "ALBILAD",
            body = "بنك البلياد\nتم شراء SAR 75.00 ببطاقة *8899\nالتاجر: HUNGERSTATION\nالرصيد المتاح SAR 980.00",
            expectedAmount = BigDecimal("75.00"), expectedType = TransactionType.EXPENSE
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // 9+10. Saudi Investment Bank (SAIB)
    // ════════════════════════════════════════════════════════════════════
    @Test
    fun `saib - English debit`() = assertCase(
        Case(
            bank = "Saudi Investment Bank", sender = "SAIB",
            body = "SAIB: SAR 210.00 debited from A/C *3344 for purchase at EXTRA STORES. Bal SAR 2,790.00",
            expectedAmount = BigDecimal("210.00"), expectedType = TransactionType.EXPENSE
        )
    )

    @Test
    fun `saib - Arabic credit`() = assertCase(
        Case(
            bank = "Saudi Investment Bank", sender = "SAIB",
            body = "SAIB\nإيداع مبلغ SAR 1,500.00 في حسابك *3344\nالرصيد SAR 4,290.00",
            expectedAmount = BigDecimal("1500.00"), expectedType = TransactionType.INCOME
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // 11. mada Pay (wallet)
    // ════════════════════════════════════════════════════════════════════
    @Test
    fun `madaPay - purchase`() = assertCase(
        Case(
            bank = "mada Pay", sender = "MadaPay",
            body = "mada Pay: Purchase of SAR 30.00 at JARIR using card **1234. Avl. Balance: SAR 470.00",
            expectedAmount = BigDecimal("30.00"), expectedType = TransactionType.EXPENSE
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // 12. Alinma Pay (wallet)
    // ════════════════════════════════════════════════════════════════════
    @Test
    fun `alinmaPay - send money`() = assertCase(
        Case(
            bank = "Alinma Pay", sender = "AlinmaPay",
            body = "Alinma Pay: You have sent SAR 150.00 to MOHAMMED ALI. New Balance: SAR 320.00",
            expectedAmount = BigDecimal("150.00"), expectedType = TransactionType.EXPENSE
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // 13. STC Pay / STC Bank
    // ════════════════════════════════════════════════════════════════════
    @Test
    fun `stcPay - English payment`() = assertCase(
        Case(
            bank = "STC Bank", sender = "STCPay",
            body = "STC Pay: SAR 60.00 paid to SALAMA INSURANCE using wallet 1234. Available balance SAR 240.00",
            expectedAmount = BigDecimal("60.00"), expectedType = TransactionType.EXPENSE
        )
    )

    @Test
    fun `stcPay - Arabic payment`() = assertCase(
        Case(
            bank = "STC Bank", sender = "STCPay",
            body = "STC Pay\nمدفوع\nمبلغ SAR 60.00\nالتاجر: SALAMA\nالرصيد المتاح SAR 180.00",
            expectedAmount = BigDecimal("60.00"), expectedType = TransactionType.EXPENSE
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // 14. Urpay (wallet)
    // ════════════════════════════════════════════════════════════════════
    @Test
    fun `urpay - purchase`() = assertCase(
        Case(
            bank = "urpay", sender = "urpay",
            body = "urpay: Purchase of SAR 45.50 at NOON using card **5678. Avl. Bal: SAR 954.50",
            expectedAmount = BigDecimal("45.50"), expectedType = TransactionType.EXPENSE
        )
    )

    // ════════════════════════════════════════════════════════════════════
    // Bonus rails: SARIE instant payments + Bank AlJazira (must keep working)
    // ════════════════════════════════════════════════════════════════════
    @Test
    fun `sarie - instant payment received`() = assertCase(
        Case(
            bank = "SARIE", sender = "SARIE",
            body = "SARIE: You have received SAR 2,500.00 from AHMED ALI via RiyadBank. IBAN **7788. Ref 2026090112345. Balance: SAR 12,000.00",
            expectedAmount = BigDecimal("2500.00"), expectedType = TransactionType.INCOME
        )
    )

    @Test
    fun `alJazira - Arabic debit`() = assertCase(
        Case(
            bank = "Bank AlJazira", sender = "BAJ",
            body = "خصم\nحساب *7788\nمبلغ SAR 199.00\nالتاجر: EXTRA\nالرصيد المتاح SAR 1,801.00",
            expectedAmount = BigDecimal("199.00"), expectedType = TransactionType.EXPENSE
        )
    )
}
