package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import com.ritesh.parser.core.test.ExpectedTransaction
import com.ritesh.parser.core.test.ParserTestCase
import com.ritesh.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class SaudiBankParserTest {

    private val parser = SaudiBankParser()

    @TestFactory
    fun `saudi bank parser handles SAR formats`(): List<DynamicTest> {
        ParserTestUtils.printTestHeader(
            parserName = "Saudi Bank (Riyad/ANB/BSF/Albilad/AlJazira/SAIB/SARIE)",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // Example 1: Riyad Bank mada POS purchase
            ParserTestCase(
                name = "Riyad Bank - mada POS purchase",
                message = """Riyad Bank: A purchase of SAR 150.00 using mada card **1234 at AL NAHDI PHARMACY on 01/02/2026. Acct **5678. Bal SAR 5,500.75""",
                sender = "RiyadBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("150.00"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "AL NAHDI PHARMACY",
                    accountLast4 = "5678",
                    balance = BigDecimal("5500.75")
                )
            ),

            // Example 2: SARIE instant payment received
            ParserTestCase(
                name = "SARIE - instant payment received",
                message = """SARIE: You have received SAR 2,500.00 from AHMED ALI via RiyadBank. IBAN **7788. Ref 2026090112345. Balance: SAR 12,000.00""",
                sender = "SARIE",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2500.00"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "AHMED ALI",
                    accountLast4 = "7788",
                    reference = "2026090112345",
                    balance = BigDecimal("12000.00")
                )
            ),

            // Example 3: ANB account debit
            ParserTestCase(
                name = "ANB - account debit",
                message = """Dear Customer, Your account ANB *4567 is debited with SAR 89.99 on 03-02-2026 for purchase at STORE XYZ. Avl Bal: SAR 910.01""",
                sender = "ANB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("89.99"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "STORE XYZ",
                    accountLast4 = "4567",
                    balance = BigDecimal("910.01")
                )
            ),

            // Example 4: Banque Saudi Fransi online purchase
            ParserTestCase(
                name = "BSF - online purchase debit",
                message = """BSF: SAR 1,200.00 debited from your account for online purchase on Amazon. Card: **9911. Bal SAR 3,300.00""",
                sender = "BSF",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1200.00"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "9911",
                    balance = BigDecimal("3300.00")
                )
            ),

            // Example 5: SAIB salary credit
            ParserTestCase(
                name = "SAIB - salary credit",
                message = """SAIB: Salary SAR 15,000.00 credited to account **2211 on 01/02/2026. Balance SAR 20,000.00""",
                sender = "SAIB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15000.00"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    accountLast4 = "2211",
                    balance = BigDecimal("20000.00")
                )
            ),

            // Example 6: Bank Albilad mada purchase
            ParserTestCase(
                name = "Bank Albilad - mada purchase",
                message = """AlBilad: SAR 62.50 spent on mada card 8899 at ALBAIK RIYADH on 05/02/2026. Avl Bal: SAR 1,470.25""",
                sender = "AlBilad",
                expected = ExpectedTransaction(
                    amount = BigDecimal("62.50"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "ALBAIK RIYADH",
                    balance = BigDecimal("1470.25")
                )
            ),

            // Example 7: Bank AlJazira (BAJ) debit
            ParserTestCase(
                name = "Bank AlJazira - account debit",
                message = """BAJ: SAR 310.00 debited from your account 5566 for purchase at JARIR BOOKSTORE. Balance: SAR 2,010.00""",
                sender = "BAJ",
                expected = ExpectedTransaction(
                    amount = BigDecimal("310.00"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5566",
                    balance = BigDecimal("2010.00")
                )
            ),

            // Example 8: Riyad Bank "Avail. Balance" wording variant
            ParserTestCase(
                name = "Riyad Bank - Avail. Balance wording",
                message = """RiyadBank: SAR 45.00 purchase using mada 7712 at DUNKIN DONUTS. Avail. Balance: SAR 890.00""",
                sender = "RiyadBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("45.00"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("890.00")
                )
            ),

            // Example 9: Arabic-language debit (خصم) — Arabic-only SMS must parse
            ParserTestCase(
                name = "Arabic - debit (خصم)",
                message = """بنك الرياض: تم خصم SAR 75.50 من حسابك 4433 لعملية شراء في هايبر باندا. الرصيد المتاح: SAR 1,530.00""",
                sender = "RiyadBank",
                expected = ExpectedTransaction(
                    amount = BigDecimal("75.50"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "4433",
                    balance = BigDecimal("1530.00")
                )
            ),

            // Example 10: Arabic-language incoming transfer (حوالة واردة)
            ParserTestCase(
                name = "Arabic - incoming transfer (حوالة واردة)",
                message = """SAIB: تم استلام حوالة واردة بمبلغ SAR 1,000.00 في حسابك 6677. الرصيد: SAR 4,500.00""",
                sender = "SAIB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    accountLast4 = "6677",
                    balance = BigDecimal("4500.00")
                )
            ),

            // Example 11: Arabic ANB purchase (شراء)
            ParserTestCase(
                name = "Arabic - purchase (شراء)",
                message = """البنك العربي الوطني: عملية شراء بقيمة SAR 120.00 من متجر اكسترا. الرصيد المتاح SAR 880.00""",
                sender = "ANB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("120.00"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("880.00")
                )
            ),

            // Negative: OTP message must be rejected
            ParserTestCase(
                name = "OTP message rejected",
                message = "Your OTP for Riyad Bank login is 123456. Do not share. SAR 0.00",
                sender = "RiyadBank",
                shouldParse = false
            ),

            // Negative: payment request must be rejected
            ParserTestCase(
                name = "Payment request rejected",
                message = "SARIE: User KHALED has requested SAR 300.00. Ignore if already paid.",
                sender = "SARIE",
                shouldParse = false
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "RiyadBank" to true,
            "RIYAD" to true,
            "ANB" to true,
            "ANB.9200" to true,
            "BSF" to true,
            "SAUDI FRANSI" to true,
            "AlBilad" to true,
            "BILAD" to true,
            "SAIB" to true,
            "SARIE" to true,
            "BAJ" to true,
            "AlJazira" to true,
            "ALJAZEERA" to true,
            "GIBSA" to true,
            "ARAB NATIONAL" to true,
            // dedicated Saudi parsers keep priority for their senders
            "SABB" to false,
            "Alinma" to false,
            "AlRajhi" to false,
            "SNB" to false,
            "STC" to false,
            "MADAPAY" to false,
            "URPAY" to false,
            "HDFC" to false,
            "BKASH" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Saudi Bank Parser Tests")
    }

    @org.junit.jupiter.api.Test
    fun `bank name resolves per registered bank`() {
        val p = SaudiBankParser()
        // Stateless per-sender resolution through parse();
        // generic default when not parseable / not resolved.
        org.junit.jupiter.api.Assertions.assertEquals("Saudi Bank", p.getBankName())

        val parsedRiyad = p.parse(
            "Riyad Bank: A purchase of SAR 10.00 using mada card 1234 at STORE on 01/02/2026. Bal SAR 500.00",
            "RiyadBank", 0L
        )
        org.junit.jupiter.api.Assertions.assertEquals("Riyad Bank", parsedRiyad?.bankName)

        val parsedAlbilad = p.parse(
            "AlBilad: SAR 62.50 spent on mada card 8899 at ALBAIK on 05/02/2026. Avl Bal: SAR 1,470.25",
            "AlBilad", 0L
        )
        org.junit.jupiter.api.Assertions.assertEquals("Bank Albilad", parsedAlbilad?.bankName)

        val parsedBAJ = p.parse(
            "BAJ: SAR 310.00 debited from your account 5566 for purchase at JARIR. Balance: SAR 2,010.00",
            "BAJ", 0L
        )
        org.junit.jupiter.api.Assertions.assertEquals("Bank AlJazira", parsedBAJ?.bankName)

        val parsedANB = p.parse(
            "Dear Customer, Your account ANB *4567 is debited with SAR 89.99 on 03-02-2026 for purchase at STORE XYZ. Avl Bal: SAR 910.01",
            "ANB", 0L
        )
        org.junit.jupiter.api.Assertions.assertEquals("Arab National Bank (ANB)", parsedANB?.bankName)
    }

    @org.junit.jupiter.api.Test
    fun `registry covers all banks from the prompt list`() {
        val names = SaudiBankRegistry.BANKS.map { it.name }
        listOf(
            "Al Rajhi Bank", "Saudi National Bank (SNB)", "SABB", "Alinma Bank",
            "Riyad Bank", "Bank Albilad", "Bank AlJazira",
            "Saudi Investment Bank (SAIB)", "Banque Saudi Fransi",
            "Arab National Bank (ANB)", "Bank Muscat (KSA branch)",
            "STC Bank / stc pay"
        ).forEach { required ->
            org.junit.jupiter.api.Assertions.assertTrue(
                names.contains(required),
                "Saudi bank registry must cover: $required"
            )
        }
    }
}
