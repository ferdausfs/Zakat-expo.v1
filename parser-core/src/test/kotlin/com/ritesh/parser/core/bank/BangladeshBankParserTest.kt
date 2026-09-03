package com.ritesh.parser.core.bank

import com.ritesh.parser.core.TransactionType
import com.ritesh.parser.core.test.ExpectedTransaction
import com.ritesh.parser.core.test.ParserTestCase
import com.ritesh.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class BangladeshBankParserTest {

    private val parser = BangladeshBankParser()

    @TestFactory
    fun `Bangladesh bank parser handles BDT formats`(): List<DynamicTest> {
        ParserTestUtils.printTestHeader(
            parserName = "Bangladesh Banks (BRAC/City/EBL/IBBL/UCB/MTB/...)",
            bankName = parser.getBankName(),
            currency = parser.getCurrency()
        )

        val testCases = listOf(
            // Example 1: BRAC Bank debit-card style purchase
            ParserTestCase(
                name = "BRAC Bank - online purchase debit",
                message = "BRAC Bank: Your A/C **5678 is debited with BDT 1,500.00 on 01-Feb-2026 10:30 for Online Purchase at Daraz on POS. Avl Bal: BDT 12,345.67",
                sender = "BRACBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1500.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "Daraz",
                    accountLast4 = "5678",
                    balance = BigDecimal("12345.67")
                )
            ),

            // Example 2: City Bank credit
            ParserTestCase(
                name = "City Bank - credit",
                message = "City Bank: Tk 5,000.00 credited to your A/C XX1234 on 01/02/2026 at 14:22. Avl Bal: Tk 25,000.00",
                sender = "CITYBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    accountLast4 = "1234",
                    balance = BigDecimal("25000.00")
                )
            ),

            // Example 3: EBL ATM withdrawal
            ParserTestCase(
                name = "EBL - ATM withdrawal",
                message = "EBL: You have withdrawn BDT 3,000.00 from ATM GULSHAN BRANCH on 02/02/2026. Avl Bal: BDT 8,500.00",
                sender = "EBL",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3000.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    balance = BigDecimal("8500.00")
                )
            ),

            // Example 4: MTB internet-banking transfer (no base keyword)
            ParserTestCase(
                name = "MTB - internet banking transfer",
                message = "MTB: Trx for BDT 250.00 to DARAZ using Internet Banking from A/C **4321 on 03/02/2026. Ref: MTB12345678",
                sender = "MTB",
                expected = ExpectedTransaction(
                    amount = BigDecimal("250.00"),
                    currency = "BDT",
                    type = TransactionType.EXPENSE,
                    merchant = "DARAZ",
                    accountLast4 = "4321",
                    reference = "MTB12345678"
                )
            ),

            // Example 5: IBBL credit with full account word
            ParserTestCase(
                name = "IBBL - credit",
                message = "IBBL: BDT 15,000.00 credited to your account XX7788 on 05/02/2026. Balance: BDT 40,000.00",
                sender = "IBBL",
                expected = ExpectedTransaction(
                    amount = BigDecimal("15000.00"),
                    currency = "BDT",
                    type = TransactionType.INCOME,
                    accountLast4 = "7788",
                    balance = BigDecimal("40000.00")
                )
            ),

            // Negative: bill reminder without transaction keywords
            ParserTestCase(
                name = "Bill reminder rejected",
                message = "IBBL: Pay your electricity bill before 10 Feb to avoid late fee.",
                sender = "IBBL",
                shouldParse = false
            ),

            // Negative: OTP
            ParserTestCase(
                name = "OTP message rejected",
                message = "BRAC Bank: OTP 445566 for your online transaction. Do not share.",
                sender = "BRACBANK",
                shouldParse = false
            )
        )

        val handleCases: List<Pair<String, Boolean>> = listOf(
            "BRACBANK" to true,
            "BRAC" to true,
            "CITYBANK" to true,
            "City Bank" to true,
            "EBL" to true,
            "IBBL" to true,
            "UCB" to true,
            "MTB" to true,
            "PRIMEBANK" to true,
            "DUTCHBANG" to true,
            // MFS and foreign senders stay with their own parsers
            "bKash" to false,
            "NAGAD" to false,
            "ROCKET" to false,
            "RiyadBank" to false,
            "Alinma" to false,
            "HDFC" to false
        )

        return ParserTestUtils.runTestSuite(parser, testCases, handleCases, "Bangladesh Bank Parser Tests")
    }
}
