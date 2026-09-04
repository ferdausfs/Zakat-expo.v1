package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import com.ritesh.parser.core.test.ExpectedTransaction
import com.ritesh.parser.core.test.ParserTestCase
import com.ritesh.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class SaudiWalletParserTest {

    private val parser = SaudiWalletParser()

    @TestFactory
    fun `Saudi wallet parser handles SAR formats`(): List<DynamicTest> {
        ParserTestUtils.printTestHeader(
            parserName = "Saudi Wallets (mada Pay / urpay / Alinma Pay)",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // Example 1: mada Pay purchase
            ParserTestCase(
                name = "mada Pay - purchase",
                message = "mada Pay: Purchase of SAR 45.50 at STARBUCKS RIYADH on 01/02. Avl Bal: SAR 1,234.56",
                sender = "MADAPAY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("45.50"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "STARBUCKS RIYADH",
                    balance = BigDecimal("1234.56")
                )
            ),

            // Example 2: urpay send money
            ParserTestCase(
                name = "urpay - send money",
                message = "urpay: You have sent SAR 75.00 to AHMED. New balance: SAR 300.00",
                sender = "URPAY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("75.00"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "AHMED",
                    balance = BigDecimal("300.00")
                )
            ),

            // Example 3: urpay received
            ParserTestCase(
                name = "urpay - received",
                message = "You received SAR 500.00 from SALEM via urpay. Balance: SAR 800.00",
                sender = "URPAY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    merchant = "SALEM",
                    balance = BigDecimal("800.00")
                )
            ),

            // Example 4: Alinma Pay payment
            ParserTestCase(
                name = "Alinma Pay - payment",
                message = "alinma pay: Payment of SAR 100.00 to TOKEN STUDIO. Available balance SAR 900.00",
                sender = "ALINMAPAY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "SAR",
                    type = TransactionType.EXPENSE,
                    merchant = "TOKEN STUDIO",
                    balance = BigDecimal("900.00")
                )
            ),

            // Example 5: wallet top-up with card last4
            ParserTestCase(
                name = "mada Pay - top-up",
                message = "mada Pay: Wallet topped up with SAR 200.00 from card XX4321. Balance: SAR 700.00",
                sender = "MADA PAY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("200.00"),
                    currency = "SAR",
                    type = TransactionType.INCOME,
                    accountLast4 = "4321",
                    balance = BigDecimal("700.00")
                )
            ),

            // Negative: OTP
            ParserTestCase(
                name = "OTP message rejected",
                message = "urpay: Your OTP is 998877. Never share this code.",
                sender = "URPAY",
                shouldParse = false
            ),

            // Negative: advertisement
            ParserTestCase(
                name = "Promotion rejected",
                message = "mada Pay: Enjoy special offers this weekend at participating stores!",
                sender = "MADAPAY",
                shouldParse = false
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "MADAPAY" to true,
            "MADA PAY" to true,
            "URPAY" to true,
            "ALINMAPAY" to true,
            "Alinma Pay" to true,
            // Bank senders stay with their own parsers
            "ALRAJHI" to false,
            "RiyadBank" to false,
            "SNB" to false,
            "UPAY" to false,
            "HDFC" to false
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Saudi Wallet Parser Tests")
    }
}
