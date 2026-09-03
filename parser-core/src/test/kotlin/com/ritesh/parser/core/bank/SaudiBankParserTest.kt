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
            "BSF" to true,
            "AlBilad" to true,
            "SAIB" to true,
            "SARIE" to true,
            "BAJ" to true,
            "AlJazira" to true,
            // dedicated Saudi parsers keep priority for their senders
            "SABB" to false,
            "Alinma" to false,
            "AlRajhi" to false,
            "SNB" to false,
            "STC" to false,
            "HDFC" to false,
            "" to false
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Saudi Bank Parser Tests")
    }
}
