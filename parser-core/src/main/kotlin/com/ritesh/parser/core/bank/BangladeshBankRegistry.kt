package com.ritesh.parser.core.bank

/**
 * Extensible, data-driven registry of Bangladeshi banks and their SMS
 * sender IDs.
 *
 * Each bank declares:
 *  - [containsTokens]: the sender is handled when it CONTAINS any token
 *    (upper-cased comparison), and/or
 *  - [exactTokens]: the sender is handled when it EQUALS a token.
 *
 * Adding a new bank later means adding one entry here — no parser
 * restructuring required. [BangladeshBankParser] consults this registry
 * in [BangladeshBankParser.canHandle].
 *
 * Sender-token semantics for pre-existing banks are copied verbatim from
 * the original BangladeshBankParser.canHandle implementation so existing
 * behaviour is unchanged. Tokens are kept specific (e.g. "PRIMEBANK",
 * not bare "PRIME") so parsers for other countries keep priority.
 */
object BangladeshBankRegistry {

    /** One Bangladeshi bank and the sender IDs it uses for SMS. */
    data class Bank(
        val name: String,
        val containsTokens: List<String> = emptyList(),
        val exactTokens: List<String> = emptyList()
    )

    val BANKS: List<Bank> = listOf(
        // ----- Private/commercial banks (pre-existing coverage) -----
        Bank("BRAC Bank", containsTokens = listOf("BRAC")),
        Bank("City Bank", containsTokens = listOf("CITYBANK", "CITY BANK"), exactTokens = listOf("CITY")),
        Bank("Eastern Bank (EBL)", containsTokens = listOf("EBLBD"), exactTokens = listOf("EBL")),
        Bank("Islami Bank Bangladesh (IBBL)", containsTokens = listOf("IBBL")),
        Bank("UCB", containsTokens = listOf("UCBBANK"), exactTokens = listOf("UCB")),
        Bank("Mutual Trust Bank (MTB)", containsTokens = listOf("MTB")),
        Bank("Pubali Bank", containsTokens = listOf("PUBLALI")),
        Bank("Prime Bank", containsTokens = listOf("PRIMEBANK", "PRIME BANK")),
        Bank("Bank Asia", containsTokens = listOf("BANKASIA", "BANK ASIA")),
        Bank("Southeast Bank", containsTokens = listOf("SOUTHEAST")),
        Bank("Trust Bank", containsTokens = listOf("TRUSTBANK", "TRUST BANK")),
        Bank("Dutch-Bangla Bank", containsTokens = listOf("DUTCHBANG", "DUTCH-BANG", "DUTCH BANG")),
        Bank("Jamuna Bank", containsTokens = listOf("JAMUNA")),
        Bank("NCC Bank", containsTokens = listOf("NCC")),
        Bank("Shahjalal Islami Bank", containsTokens = listOf("SHAHJAL")),
        Bank("Al-Arafah Islami Bank", containsTokens = listOf("ALARAFAH", "AL-ARAFAH", "AL ARAFAH")),
        Bank("Midland Bank", containsTokens = listOf("MIDLAND")),
        Bank("Dhaka Bank", containsTokens = listOf("DHAKABANK", "DHAKA BANK")),
        // ----- State-owned banks -----
        Bank("Sonali Bank", containsTokens = listOf("SONALIBANK", "SONALI BANK", "SONALI")),
        Bank("Agrani Bank", containsTokens = listOf("AGRANIBANK", "AGRANI BANK", "AGRANI")),
        Bank("Janata Bank", containsTokens = listOf("JANATABANK", "JANATA BANK", "JANATA")),
        Bank("Rupali Bank", containsTokens = listOf("RUPALIBANK", "RUPALI BANK", "RUPALI")),
        Bank("Bangladesh Development Bank (BDBL)", containsTokens = listOf("BDBL")),
        // ----- Additional private banks -----
        Bank("IFIC Bank", containsTokens = listOf("IFICBANK", "IFICBD"), exactTokens = listOf("IFIC")),
        Bank("AB Bank", containsTokens = listOf("ABBANK", "AB BANK"), exactTokens = listOf("ABBL")),
        Bank("Exim Bank", containsTokens = listOf("EXIMBANK", "EXIM BANK"), exactTokens = listOf("EXIM")),
        Bank("Mercantile Bank", containsTokens = listOf("MERCANTILE")),
        Bank("One Bank", containsTokens = listOf("ONEBANK", "ONE BANK"))
    )

    /** Returns the matching bank for the given sender, or null. */
    fun find(sender: String): Bank? {
        val s = sender.uppercase()
        return BANKS.firstOrNull { bank ->
            bank.containsTokens.any { s.contains(it) } ||
                    bank.exactTokens.any { s == it }
        }
    }

    /** True when the sender belongs to a registered Bangladeshi bank. */
    fun matches(sender: String): Boolean = find(sender) != null
}
