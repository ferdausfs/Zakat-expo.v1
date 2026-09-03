package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import com.ritesh.parser.core.test.ExpectedTransaction
import com.ritesh.parser.core.test.ParserTestCase
import com.ritesh.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class BkashParserTest {

    private val parser = BkashParser()

    @TestFactory
    fun `bKash parser handles BDT formats`(): List<DynamicTest> {
        ParserTestUtils.printTestHeader(
            parserName = "bKash (Bangladesh MFS)",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // Example 1: money received
            ParserTestCase(
                name = "bKash - money received",
                message = "You have received Tk 1000.00 from 01712345678. Fee Tk 0.00. Balance: Tk 5000.00.",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    accountLast4 = "5678",
                    balance = BigDecimal("5000.00")
                )
            ),

            // Example 2: merchant payment
            ParserTestCase(
                name = "bKash - merchant payment",
                message = "Payment Tk 250.00 to Aarong (01712345678) successful. Fee Tk 0.00. Balance: Tk 950.00.",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "Aarong",
                    accountLast4 = "5678",
                    balance = BigDecimal("950.00")
                )
            ),

            // Example 3: cash out to agent
            ParserTestCase(
                name = "bKash - cash out",
                message = "Cash Out Tk 500.00 to 01712345678 successful. Fee Tk 9.30. Balance: Tk 440.70.",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678",
                    balance = BigDecimal("440.70")
                )
            ),

            // Example 4: send money
            ParserTestCase(
                name = "bKash - send money",
                message = "Send Money Tk 300.00 to 01812345678 successful. Fee Tk 5.00. Balance: Tk 145.00.",
                sender = "bKash",
                expected = ExpectedTransaction(
                    amount = BigDecimal("300.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678",
                    balance = BigDecimal("145.00")
                )
            ),

            // Example 5: add money (top-up)
            ParserTestCase(
                name = "bKash - add money",
                message = "Add Money Tk 2000.00 successful. Balance: Tk 7000.00.",
                sender = "BKASH",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2000.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    balance = BigDecimal("7000.00")
                )
            ),

            // Negative: promotional message
            ParserTestCase(
                name = "Promotional message rejected",
                message = "You have won a bKash gift! Reply STOP to opt out.",
                sender = "bKash",
                shouldParse = false
            ),

            // Negative: OTP
            ParserTestCase(
                name = "OTP message rejected",
                message = "Your bKash OTP is 123456. Never share this code.",
                sender = "bKash",
                shouldParse = false
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "bKash" to true,
            "BKASH" to true,
            "Nagad" to false,
            "ROCKET" to false,
            "DBBL" to false,
            "BRACBANK" to false,
            "HDFC" to false
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "bKash Parser Tests")
    }
}
