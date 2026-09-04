package com.ritesh.parser.core.bank

/**
 * Extensible, data-driven registry of Saudi banks and payment rails and
 * their SMS sender IDs — the Saudi counterpart of [BangladeshBankRegistry].
 *
 * Each bank declares:
 *  - [containsTokens]: the sender is handled when it CONTAINS any token
 *    (upper-cased comparison), and/or
 *  - [exactTokens]: the sender is handled when it EQUALS a token, and/or
 *  - [startsWithTokens]: the sender is handled when it STARTS WITH a token
 *    (used for dotted short codes like "ANB.9200").
 *
 * Adding a new Saudi bank later means adding one entry here — no parser
 * restructuring required. [SaudiBankParser] consults this registry in
 * [SaudiBankParser.canHandle].
 *
 * Sender-token semantics for pre-existing banks are copied verbatim from
 * the original SaudiBankParser.canHandle implementation so existing
 * behaviour is unchanged. Dedicated Saudi parsers (Al Rajhi, Alinma,
 * SNB AlAhli, SABB, STC Bank, the mada Pay/urpay/Alinma Pay wallets and
 * Bank Muscat) sit EARLIER in [BankParserFactory], so entries here only
 * claim senders those parsers do not already handle. The first six
 * entries document those dedicated-parser banks for lookup completeness;
 * [SaudiBankParser] resolves their names through the dedicated parsers,
 * never through this registry.
 */
object SaudiBankRegistry {

    /** One Saudi bank (or payment rail) and the sender IDs it uses for SMS. */
    data class Bank(
        val name: String,
        val containsTokens: List<String> = emptyList(),
        val exactTokens: List<String> = emptyList(),
        val startsWithTokens: List<String> = emptyList(),
        /** true when a dedicated parser in BankParserFactory claims these
         *  senders first; SaudiBankParser must not fallback-claim them. */
        val handledByDedicatedParser: Boolean = false
    )

    val BANKS: List<Bank> = listOf(
        // ----- Banks with dedicated parsers upstream of the generic one -----
        // (documented here for lookup completeness; their senders are claimed
        // by the dedicated parsers in BankParserFactory first)
        Bank("Al Rajhi Bank", containsTokens = listOf("ALRAJHI", "RAJHI"), handledByDedicatedParser = true),
        Bank("Saudi National Bank (SNB)", containsTokens = listOf("SNBAHLI", "SNBALAHLI"), handledByDedicatedParser = true),
        Bank("SABB", containsTokens = listOf("SABB"), handledByDedicatedParser = true),
        Bank("Alinma Bank", containsTokens = listOf("ALINMA"), handledByDedicatedParser = true),
        Bank("STC Bank / stc pay", containsTokens = listOf("STCPAY", "STC BANK"), handledByDedicatedParser = true),
        Bank("Bank Muscat (KSA branch)", containsTokens = listOf("BANKMUSCAT", "BANK MUSCAT"), handledByDedicatedParser = true),

        // ----- Banks handled by SaudiBankParser (registry-driven) -----
        // Sender tokens copied verbatim from the original canHandle.
        Bank("Riyad Bank", containsTokens = listOf("RIYAD")),
        Bank("SARIE instant payments rail", containsTokens = listOf("SARIE")),
        Bank("Bank Albilad",
            containsTokens = listOf("ALBILAD", "AL BLAD", "ALBLAD", "BILADI"),
            exactTokens = listOf("BILAD")),
        Bank("Bank AlJazira",
            containsTokens = listOf("ALJAZIRA", "AL JAZIRA", "ALJAZEERA"),
            exactTokens = listOf("BAJ")),
        Bank("Saudi Investment Bank (SAIB)",
            containsTokens = listOf("SAIB", "SAUDI INVESTMENT")),
        Bank("Banque Saudi Fransi",
            containsTokens = listOf("BSF", "SAUDI FRANSI", "SAUDI FRENCH")),
        Bank("Arab National Bank (ANB)",
            containsTokens = listOf("ARAB NATIONAL"),
            exactTokens = listOf("ANB"),
            startsWithTokens = listOf("ANB.")),

        // ----- Additional licensed banks (new coverage) -----
        Bank("Gulf International Bank (Saudi Arabia)",
            containsTokens = listOf("GIBSAUDI", "GIB SAUDI"),
            exactTokens = listOf("GIBSA"))
    )

    /** Returns the matching bank for the given sender, or null. */
    fun find(sender: String): Bank? {
        val s = sender.uppercase().trim()
        return BANKS.firstOrNull { bank ->
            bank.containsTokens.any { s.contains(it) } ||
                    bank.exactTokens.any { s == it } ||
                    bank.startsWithTokens.any { s.startsWith(it) }
        }
    }

    /** Returns the matching bank whose senders SaudiBankParser itself
     *  handles (i.e. excluding banks with dedicated parsers), or null. */
    fun findGeneric(sender: String): Bank? {
        val s = sender.uppercase().trim()
        return BANKS.firstOrNull { bank ->
            !bank.handledByDedicatedParser && (
                    bank.containsTokens.any { s.contains(it) } ||
                    bank.exactTokens.any { s == it } ||
                    bank.startsWithTokens.any { s.startsWith(it) })
        }
    }

    /** True when the sender belongs to a registered Saudi bank/rail. */
    fun matches(sender: String): Boolean = find(sender) != null
}
