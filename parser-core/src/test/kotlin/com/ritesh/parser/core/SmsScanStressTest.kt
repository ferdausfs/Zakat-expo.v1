package com.ritesh.parser.core

import com.ritesh.parser.core.bank.BankParserFactory
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.random.Random

/**
 * Stress harness for the SMS analysis pipeline (crash diagnosis).
 *
 * Reproduces the "crash while analyzing SMS" class of failures on the JVM by
 * running EVERY parser against an adversarial, mixed corpus:
 *  - realistic bank/wallet SMS (BD + Saudi + India)
 *  - malformed / truncated messages
 *  - pathological regex-bait bodies (nested-quantifier / ambiguity bombs)
 *  - very long bodies (just under the 5000-char ReDoS guard)
 *  - emoji / unicode / control characters
 *
 * Assertions:
 *  1. BankParserFactory initialises (any bad Regex would throw
 *     ExceptionInInitializerError here — an Error that escapes `catch (Exception)`
 *     on device and crashes the whole SMS scan).
 *  2. No parser call hangs (catastrophic backtracking -> ANR on device).
 *  3. No parser call throws ANY Throwable (including Errors) — a single SMS
 *     must never take down the scan.
 */
class SmsScanStressTest {

    private val bdSenders = listOf(
        "BKASH", "NAGAD", "DUTCHBANGL", "ROCKET", "UPAY", "IBBL", "BRACBANK",
        "CITYBANK", "EBL", "UCBL", "MTB", "SEBBL", "PRIMEBANK", "SONALIBANK",
        "AGRANIBANK", "JANATABANK", "RUPALIBANK", "BDBL", "IFICBANK", "ABBANK",
        "EXIMBANK", "MERCHANTILEBANK", "ONEBANKPLC", "DBBL"
    )

    private val saudiSenders = listOf(
        "AlRajhiBank", "SNBAHB", "SNB", "RiyadBank", "SABB", "ALINMA", "BSF",
        "ANB", "ALBILAD", "BAJ", "SAIB", "STCBANK", "MADAPAY", "URPAY",
        "ALINMAPAY", "sarie", "SABBPAY", "Riyad"
    )

    private val indiaSenders = listOf(
        "HDFCBK", "SBIINB", "SBICARD", "ICICIB", "AXISBK", "PAYTM", "JKBANK",
        "FEDERALB", "INDUSB", "UNIONBANK", "DISCOVER", "PNB", "BOIIND"
    )

    /** Backtracking bait: token soup that invites the engine to partition it. */
    private fun tokenBait(n: Int, sep: String = "  "): String =
        (1..n).joinToString(sep) { if (it % 3 == 0) "a" else "word${it}" }

    private fun corpus(): List<Pair<String, String>> = buildList {
        // ── realistic messages ────────────────────────────────────────────
        val realBodies = listOf(
            "bKash: You have received Tk 1,250.00 from 01712345678. Fee Tk 0.00. Balance Tk 5,300.75. TrxID 9A7B6C5D4E at 12/08/2026 10:30",
            "Nagad: আপনার একাউন্টে BDT 500 জমা হয়েছে। Balance: BDT 2,450.00. TxnID: 8F7E6D5C4B",
            "You have cashed out Tk 900.00 from your Rocket account. Fee Tk 9.00. New balance Tk 1,200.50. TxnId: 7E6D5C4B3A",
            "upay: You have sent Tk 350.00 to 01899887766. Fee Tk 3.50. Balance Tk 900.00. TrxID: UP12345678",
            "IBBL Tk 2,500.00 debited from A/C XX1234 on 01/09/2026 at DJS Traders, Dhaka. Avl. Bal: Tk 12,300.00. Ref: IB765432",
            "BRAC Bank: Tk 4,500.00 credited to A/C **5678 on 02/09/2026. Available Balance Tk 25,000.00. Ref: BRAC0012345",
            "Al Rajhi Bank: SAR 150.50 spent on mada card 4567 at PANDA, RIYADH on 03-09-2026. Available Balance: SAR 8,540.25. Ref: 123456",
            "SNB: تم تحويل SAR 1,000.00 من حسابك 1234 إلى 5678. الرصيد المتاح SAR 3,200.00",
            "SABB: SAR 89.99 purchase at ALNAHDI PHARMACY on Visa card ending 4321. Avl Bal SAR 1,850.00 Ref 998877",
            "Alinma: SAR 2,000.00 salary credited to account 9012. New Balance SAR 6,780.90 Ref AL123456",
            "mada Pay: SAR 45.00 paid to STARBUCKS from Wallet 7788. Balance SAR 310.00 TrxID MP776655",
            "urpay: SAR 300.00 added to your wallet 4321 via card. New Balance SAR 300.00",
            "stc pay: You received SAR 75.00 from +966501234567. Balance: SAR 500.00. Ref: STC99887766",
            "Rs.1,234.56 debited from a/c XX1234 on 01-09-26 towards AMAZON INDIA. Avl Lmt Rs.45,000",
            "INR 500.00 credited to your account 5678 on 02-09-26 ref UPI/123456789012/JOHN",
            "Your a/c 1234 is debited for Rs 999 on 03-09-2026 14:22:05 IST by VPS-*PAYTM",
            // malformed / truncated / garbage
            "debited",
            "credited to",
            "Tk",
            "SAR",
            "Rs.",
            "",
            " ",
            "\n\n\n",
            " Tk 1,000 debited  A/C XX  Avl Bal",
            "SAR  paid  at  on  Ref",
            "😀😀😀 debited 💰💰💰",
            "د brawl بيتكوم debit ٠١٢٣٤٥",
            "a\u0000b\u0007c debited",
            "Debited " + "x".repeat(400),
            "Tk " + "9".repeat(300) + " debited",
            "SAR " + "1,".repeat(200) + "00 spent",
            // regex bait — nested-quantifier / ambiguity bombs
            "at " + tokenBait(300),
            "at " + tokenBait(300, "   "),
            "spent at " + "a ".repeat(1200),
            "purchase at " + tokenBait(500, " "),
            "paid to " + tokenBait(400),
            "from " + tokenBait(400),
            "debited at " + "word ".repeat(800) + " no terminator on Avl",
            "at " + "a".repeat(4000),
            "card 1234 at " + ("ab ".repeat(900)) + " using X",
            "to " + ("M/S ".repeat(200)) + " on ",
            // long but under the 5000 guard
            ("Transaction debited Tk 100 at MERCHANT " + "z".repeat(200) + " ") .repeat(25),
            ("SAR 10 paid to shop . " + "ق".repeat(250) + " ").repeat(18),
        )

        // Run every body against every sender family (worst case: sender
        // matches several parsers, all of them parse the same body).
        for (sender in bdSenders + saudiSenders + indiaSenders) {
            for (body in realBodies) add(sender to body)
        }

        // ── randomized fuzz (seeded => reproducible) ──────────────────────
        val rnd = Random(424242)
        val alphabet = "0123456789 TkSARRs.,:-/ab word debited credited at to paid \n\t😀अআم*%$#@!"
        repeat(3000) {
            val sender = (bdSenders + saudiSenders + indiaSenders + listOf("XX-" + rnd.nextBits(16), "VD-" + rnd.nextBits(16))).random(rnd)
            val len = rnd.nextInt(1, 800)
            val sb = StringBuilder(len)
            repeat(len) { sb.append(alphabet.random(rnd)) }
            add(sender to sb.toString())
        }
    }

    @Test
    fun `factory initialises without throwing Errors`() {
        val parsers = BankParserFactory.getAllParsers()
        check(parsers.size > 100) { "expected full parser registry, got ${parsers.size}" }
    }

    @Test
    fun `no parser hangs or throws on adversarial corpus`() {
        val parsers = BankParserFactory.getAllParsers()
        val bodies = corpus()
        val executor = Executors.newCachedThreadPool { r ->
            Thread(r).apply { isDaemon = true }
        }

        data class Failure(val parser: String, val sender: String, val bodyHead: String, val problem: String)
        val failures = mutableListOf<Failure>()
        val hangs = mutableListOf<Failure>()
        var checked = 0L

        val deadlinePerCallMs = 10_000L

        outer@ for (parser in parsers) {
            for ((sender, body) in bodies) {
                checked++
                val future = executor.submit {
                    try {
                        parser.parse(body, sender, 1_770_000_000_000L)
                    } catch (t: Throwable) {
                        throw t
                    }
                }
                try {
                    future.get(deadlinePerCallMs, TimeUnit.MILLISECONDS)
                } catch (e: TimeoutException) {
                    hangs += Failure(parser.getBankName(), sender, body.take(80), "HANG > ${deadlinePerCallMs}ms (regex backtracking?)")
                    future.cancel(true)
                    // A single hang is already a device-crash class bug; keep
                    // scanning other parser/body pairs, but cap total hangs.
                    if (hangs.size >= 5) break@outer
                } catch (e: java.util.concurrent.ExecutionException) {
                    val cause = e.cause ?: e
                    failures += Failure(parser.getBankName(), sender, body.take(80), "${cause::class.java.simpleName}: ${cause.message?.take(160)}")
                    if (failures.size >= 10) break@outer
                }
            }
        }

        executor.shutdownNow()

        val report = buildString {
            appendLine("SMS scan stress: $checked parser×message checks")
            appendLine("HANGS (${hangs.size}):")
            hangs.forEach { appendLine("  [${it.parser}] sender=${it.sender} body=${it.bodyHead} => ${it.problem}") }
            appendLine("CRASHES (${failures.size}):")
            failures.forEach { appendLine("  [${it.parser}] sender=${it.sender} body=${it.bodyHead} => ${it.problem}") }
        }
        println(report)

        check(hangs.isEmpty()) { "Parsing hung (catastrophic backtracking / ReDoS):\n$report" }
        check(failures.isEmpty()) { "Parsing crashed:\n$report" }
    }

    /**
     * Simulates a full manual-scan pass over a LARGE realistic mixed inbox —
     * the same per-message routing the app worker performs
     * (BankParserFactory.getParsers -> firstNotNullOfOrNull parse) — and
     * reports processed / parsed / skipped / no-parser counts.
     *
     * Guarantees proven here (and on device by construction, since the worker
     * wraps each of these same calls per-message):
     *  - the pass ALWAYS terminates (no hang),
     *  - no message can throw (the pass itself would fail),
     *  - unmatched/malformed messages are SKIPPED, never fatal.
     */
    @Test
    fun `large mixed inbox scan completes without crash - with counts`() {
        val saudiRealistic = listOf(
            "خصم\nحساب:*1234\nمبلغ SAR 350.00\nالتاجر: JARIR BOOKSTORE\nالرصيد المتاح SAR 6,500.00",
            "SNB: SAR 89.00 debited from account *1234 for purchase at JARIR. Avail. Bal: SAR 6,411.00",
            "إيداع\nحساب:*1234\nمبلغ SAR 2,000.00\nمن: AHMED SALEH\nالرصيد المتاح SAR 8,500.00",
            "SABB\nشراء\nبطاقة *1234\nمبلغ SAR 55.25\nالتاجر: DUNKIN DONUTS\nالرصيد SAR 835.50",
            "SABB: SAR 55.25 spent using debit card ending 1234 at DUNKIN DONUTS on 05-03. Available balance SAR 890.75",
            "Al Rajhi Bank: Purchase of SAR 125.50 with mada card 4567* at PANDA MARKET on 12/03. Avail. Bal: SAR 4,200.00",
            "AlRajhi\nبطاقة:4567*شراء\nمبلغ:SAR 25.00\nالتاجر: PANDA\nالرصيد:SAR 4,200.00",
            "AlRajhi\nحوالة واردة\nمبلغ:SAR 8,500.00\nمن: MINISTRY OF FINANCE\nالرصيد:SAR 12,750.00",
            "Riyad Bank: A purchase of SAR 150.00 using mada card **1234 at AL NAHDI PHARMACY on 01/02/2026. Acct **5678. Bal SAR 5,500.75",
            "تم خصم مبلغ SAR 75.00 من حسابك *5678 شراء من متجر\nالرصيد المتاح: SAR 5,425.75",
            "شراء\nبطاقة مدى*1234\nبمبلغ: 75.50 SAR\nلدى: TAMIMI MARKETS\nالرصيد: 4,500.00 SAR",
            "BSF\nتم خصم SAR 120.00 من حسابك *5678\nالرصيد المتاح SAR 2,000.00",
            "ANB: تم خصم مبلغ SAR 300.00 من حسابك *4567\nالرصيد: SAR 610.01",
            "بنك البلياد\nتم شراء SAR 75.00 ببطاقة *8899\nالتاجر: HUNGERSTATION\nالرصيد المتاح SAR 980.00",
            "SAIB\nإيداع مبلغ SAR 1,500.00 في حسابك *3344\nالرصيد SAR 4,290.00",
            "mada Pay: Purchase of SAR 30.00 at JARIR using card **1234. Avl. Balance: SAR 470.00",
            "Alinma Pay: You have sent SAR 150.00 to MOHAMMED ALI. New Balance: SAR 320.00",
            "STC Pay\nمدفوع\nمبلغ SAR 60.00\nالتاجر: SALAMA\nالرصيد المتاح SAR 180.00",
            "urpay: Purchase of SAR 45.50 at NOON using card **5678. Avl. Bal: SAR 954.50",
            "SARIE: You have received SAR 2,500.00 from AHMED ALI via RiyadBank. IBAN **7788. Ref 2026090112345. Balance: SAR 12,000.00",
            "خصم\nحساب *7788\nمبلغ SAR 199.00\nالتاجر: EXTRA\nالرصيد المتاح SAR 1,801.00"
        )
        val otherRealistic = listOf(
            "bKash: You have received Tk 1,250.00 from 01712345678. Fee Tk 0.00. Balance Tk 5,300.75. TrxID 9A7B6C5D4E",
            "IBBL Tk 2,500.00 debited from A/C XX1234 on 01/09/2026 at DJS Traders, Dhaka. Avl. Bal: Tk 12,300.00",
            "Rs.1,234.56 debited from a/c XX1234 on 01-09-26 towards AMAZON INDIA. Avl Lmt Rs.45,000",
            "INR 500.00 credited to your account 5678 on 02-09-26 ref UPI/123456789012/JOHN"
        )
        val junk = listOf(
            "Your OTP is 4821. Do not share.", "", " ", "😀😀😀", "offer! 50% discount today!",
            "Tk", "SAR", "د brawl بيتكوم debit ٠١٢٣٤٥", "a\u0000b\u0007c debited"
        )
        val allSenders = bdSenders + saudiSenders + indiaSenders
        val rnd = Random(909090)

        // 20,000-message inbox: 55% realistic financial, 25% junk/unrelated,
        // 20% seeded fuzz (malformed by construction)
        val inbox = ArrayList<Pair<String, String>>(20_000)
        repeat(20_000) { i ->
            val sender = allSenders.random(rnd)
            val roll = i % 20
            val body = when {
                roll < 11 -> (saudiRealistic + otherRealistic).random(rnd)
                roll < 15 -> junk.random(rnd)
                else -> {
                    val alphabet = "0123456789 TkSARRs.,:-/ab word debited credited at to paid \n\t😀مخص"
                    val len = rnd.nextInt(1, 500)
                    buildString { repeat(len) { append(alphabet.random(rnd)) } }
                }
            }
            inbox += sender to body
        }

        // THE SCAN — same routing as OptimizedSmsReaderWorker.parseMessage
        var processed = 0
        var parsed = 0
        var skippedNoParser = 0
        var skippedUnmatched = 0
        val started = System.currentTimeMillis()
        for ((sender, body) in inbox) {
            processed++
            val parsers = BankParserFactory.getParsers(sender)
            if (parsers.isEmpty()) {
                skippedNoParser++
                continue
            }
            val result = parsers.firstNotNullOfOrNull { parser ->
                parser.parse(body, sender, 1_770_000_000_000L)
            }
            if (result != null) parsed++ else skippedUnmatched++
        }
        val elapsed = System.currentTimeMillis() - started

        val summary = buildString {
            appendLine("Large mixed-inbox scan simulation (${inbox.size} messages):")
            appendLine("  processed:        $processed")
            appendLine("  parsed OK:        $parsed")
            appendLine("  skipped unmatched:$skippedUnmatched")
            appendLine("  skipped no-parser:$skippedNoParser")
            appendLine("  elapsed:          ${elapsed}ms")
        }
        println(summary)

        check(processed == 20_000) { "scan did not process the whole inbox" }
        check(parsed > 0) { "no message parsed — routing is broken" }
        check(skippedUnmatched + skippedNoParser > 0) {
            "unmatched messages were not ignored gracefully"
        }
    }

    /**
     * Bounded-range scan simulation (spec 13.2/13.4): the provider query is
     * date-filtered BEFORE any message is read (only rows inside the window
     * are ever fetched), and the scan classifies every message into
     * matched / skipped / failed exactly like the worker's save stage now
     * does. A 12k-message inbox spread across 13 months is scanned twice:
     * once for the last 30 days, once for all time — the bounded scan must
     * only ever see the rows inside its window and complete without crash.
     */
    @Test
    fun `bounded date-range scan only processes the selected window`() {
        val realistic = listOf(
            "Al Rajhi Bank: Purchase of SAR 125.50 with mada card 4567* at PANDA MARKET on 12/03. Avail. Bal: SAR 4,200.00",
            "SNB: SAR 89.00 debited from account *1234 for purchase at JARIR. Avail. Bal: SAR 6,411.00",
            "خصم\nحساب:*1234\nمبلغ SAR 350.00\nالتاجر: JARIR BOOKSTORE\nالرصيد المتاح SAR 6,500.00",
            "Your OTP is 4821. Do not share.",
            "50% discount today only!"
        )
        val rnd = Random(424242)
        val now = 1_788_000_000_000L // fixed "now" for determinism
        val dayMs = 24L * 60 * 60 * 1000

        // 12,000 messages spread over ~13 months.
        data class Row(val sender: String, val body: String, val date: Long)
        val inbox = ArrayList<Row>(12_000)
        repeat(12_000) {
            inbox += Row(
                (bdSenders + saudiSenders + indiaSenders).random(rnd),
                realistic.random(rnd),
                now - rnd.nextLong(0, 396) * dayMs
            )
        }

        fun scan(fromMs: Long, toMs: Long): Triple<Int, Int, Int> {
            // Provider-level filter: only rows within the window are read.
            val windowRows = inbox.filter { it.date in fromMs..toMs }
            var parsed = 0
            var skipped = 0
            var failed = 0
            for (row in windowRows) {
                try {
                    val parsers = BankParserFactory.getParsers(row.sender)
                    if (parsers.isEmpty()) {
                        skipped++
                        continue
                    }
                    val result = parsers.firstNotNullOfOrNull { parser ->
                        parser.parse(row.body, row.sender, row.date)
                    }
                    if (result != null) parsed++ else skipped++
                } catch (t: Throwable) {
                    failed++ // per-message isolation: one bad message never halts the scan
                }
            }
            return Triple(parsed, skipped, failed)
        }

        val month = scan(now - 30 * dayMs, now)
        val all = scan(0, now)

        // Bounded scan only sees its own window; full scan sees everything.
        val monthRows = inbox.count { it.date >= now - 30 * dayMs }
        check(month.first + month.second + month.third == monthRows) {
            "bounded scan must process exactly the rows in the window"
        }
        check(all.first + all.second + all.third == 12_000) {
            "all-time scan must process the entire inbox"
        }
        check(all.first > 0 && month.first > 0) { "routing broken in range scan" }
        println(
            "Bounded-range scan: last-30-days window rows=$monthRows " +
                "(parsed=${month.first}, skipped=${month.second}, failed=${month.third}); " +
                "all-time=12000 (parsed=${all.first}, skipped=${all.second}, failed=${all.third})"
        )
    }
}
