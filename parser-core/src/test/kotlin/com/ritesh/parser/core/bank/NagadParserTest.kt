package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import com.ritesh.parser.core.test.ExpectedTransaction
import com.ritesh.parser.core.test.ParserTestCase
import com.ritesh.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class NagadParserTest {

    private val parser = NagadParser()

    @TestFactory
    fun `Nagad parser handles BDT formats`(): List<DynamicTest> {
        ParserTestUtils.printTestHeader(
            parserName = "Nagad (Bangladesh MFS)",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // Example 1: money received
            ParserTestCase(
                name = "Nagad - money received",
                message = "You have received BDT 500.00 from 01712345678. Fee: BDT 0.00, Balance: BDT 1500.00",
                sender = "NAGAD",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    accountLast4 = "5678",
                    balance = BigDecimal("1500.00")
                )
            ),

            // Example 2: merchant payment
            ParserTestCase(
                name = "Nagad - merchant payment",
                message = "Payment BDT 200.00 to Aarong (01712345678) successful. Fee: BDT 0.00, Balance: BDT 1300.00",
                sender = "Nagad",
                expected = ExpectedTransaction(
                    amount = BigDecimal("200.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "Aarong",
                    accountLast4 = "5678",
                    balance = BigDecimal("1300.00")
                )
            ),

            // Example 3: cash out from agent
            ParserTestCase(
                name = "Nagad - cash out",
                message = "Cash Out BDT 1000.00 from Agent 01712345678. Fee: BDT 14.90, Balance: BDT 985.10",
                sender = "NAGAD",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678",
                    balance = BigDecimal("985.10")
                )
            ),

            // Example 4: send money
            ParserTestCase(
                name = "Nagad - send money",
                message = "Send Money BDT 500.00 to 01812345678 successful. Balance: BDT 480.00",
                sender = "NAGAD",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    accountLast4 = "5678",
                    balance = BigDecimal("480.00")
                )
            ),

            // Negative: promotional
            ParserTestCase(
                name = "Promotional message rejected",
                message = "Nagad: Get exciting offers on your next payment! T&C apply.",
                sender = "NAGAD",
                shouldParse = false
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "NAGAD" to true,
            "Nagad" to true,
            "bKash" to false,
            "ROCKET" to false,
            "BRACBANK" to false,
            "HDFC" to false
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Nagad Parser Tests")
    }
}
