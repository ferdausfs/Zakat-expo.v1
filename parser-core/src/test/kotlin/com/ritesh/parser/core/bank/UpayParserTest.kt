package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import com.ritesh.parser.core.test.ExpectedTransaction
import com.ritesh.parser.core.test.ParserTestCase
import com.ritesh.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class UpayParserTest {

    private val parser = UpayParser()

    @TestFactory
    fun `upay parser handles BDT formats`(): List<DynamicTest> {
        ParserTestUtils.printTestHeader(
            parserName = "upay (Bangladesh MFS)",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // Example 1: money received
            ParserTestCase(
                name = "upay - money received",
                message = "You have received Tk 500.00 from 01712345678. Fee Tk 0.00. Balance: Tk 1,500.00",
                sender = "UPAY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    accountLast4 = "5678",
                    balance = BigDecimal("1500.00")
                )
            ),

            // Example 2: wallet top-up (add money)
            ParserTestCase(
                name = "upay - add money",
                message = "Dear Customer, Add Money of Tk 2,000.00 was successful. Balance: Tk 3,000.00",
                sender = "UPAYBD",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2000.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("3000.00")
                )
            ),

            // Example 3: merchant payment
            ParserTestCase(
                name = "upay - merchant payment",
                message = "Payment of Tk 250.00 to Aarong was successful. Fee Tk 0.00. Balance: Tk 1,250.00",
                sender = "UPAY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "Aarong",
                    balance = BigDecimal("1250.00")
                )
            ),

            // Example 4: send money with fee
            ParserTestCase(
                name = "upay - send money",
                message = "Send Money of Tk 300.00 to 01812345678 was successful. Fee Tk 5.00. Balance: Tk 950.00",
                sender = "UPAY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("300.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678",
                    balance = BigDecimal("950.00")
                )
            ),

            // Example 5: cash out to agent
            ParserTestCase(
                name = "upay - cash out",
                message = "Cash Out of Tk 500.00 was successful. Fee Tk 9.30. Balance: Tk 440.70",
                sender = "UPAY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("440.70")
                )
            ),

            // Negative: promotional message
            ParserTestCase(
                name = "Promotional message rejected",
                message = "You have won an upay gift! Reply STOP to opt out.",
                sender = "UPAY",
                shouldParse = false
            ),

            // Negative: OTP
            ParserTestCase(
                name = "OTP message rejected",
                message = "Your upay OTP is 123456. Never share this code.",
                sender = "UPAY",
                shouldParse = false
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "UPAY" to true,
            "UPAYBD" to true,
            "Upay" to true,
            "NAGAD" to false,
            "BKASH" to false,
            "ROCKET" to false,
            // Saudi wallet must not be swallowed
            "URPAY" to false,
            "BRACBANK" to false,
            "HDFC" to false
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "upay Parser Tests")
    }
}
