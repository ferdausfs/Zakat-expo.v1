package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import com.ritesh.parser.core.test.ExpectedTransaction
import com.ritesh.parser.core.test.ParserTestCase
import com.ritesh.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class RocketParserTest {

    private val parser = RocketParser()

    @TestFactory
    fun `Rocket parser handles BDT formats`(): List<DynamicTest> {
        ParserTestUtils.printTestHeader(
            parserName = "Rocket / DBBL mobile banking (Bangladesh MFS)",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // Example 1: cash out to agent
            ParserTestCase(
                name = "Rocket - cash out",
                message = "You have debited Tk 500.00 for Cash Out to 01712345678. Fee: Tk 9.25. New Bal: Tk 2500.75",
                sender = "ROCKET",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "Cash Out",
                    accountLast4 = "5678",
                    balance = BigDecimal("2500.75")
                )
            ),

            // Example 2: money received
            ParserTestCase(
                name = "Rocket - money received",
                message = "You have received Tk 1000.00 from 01812345678. Fee Tk 0.00. New Bal Tk 3500.75",
                sender = "Rocket",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    accountLast4 = "5678",
                    balance = BigDecimal("3500.75")
                )
            ),

            // Example 3: payment to merchant
            ParserTestCase(
                name = "Rocket - merchant payment",
                message = "You have debited Tk 100.00 for Payment to Aarong. Fee Tk 0.00.",
                sender = "DBBL",
                expected = ExpectedTransaction(
                    amount = BigDecimal("100.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "Aarong"
                )
            ),

            // Example 4: account debited phrasing
            ParserTestCase(
                name = "Rocket - account debited",
                message = "Your Rocket Account 01712345678 is debited by Tk 200.00 for Payment. New Bal: Tk 800.00",
                sender = "ROCKET",
                expected = ExpectedTransaction(
                    amount = BigDecimal("200.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "Payment",
                    accountLast4 = "5678",
                    balance = BigDecimal("800.00")
                )
            ),

            // Negative: promotional
            ParserTestCase(
                name = "Promotional message rejected",
                message = "ROCKET: Enjoy 25% discount on Add Money this week!",
                sender = "ROCKET",
                shouldParse = false
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "ROCKET" to true,
            "Rocket" to true,
            "DBBL" to true,
            "bKash" to false,
            "NAGAD" to false,
            "BRACBANK" to false
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Rocket Parser Tests")
    }
}
