package com.ritesh.cashiro.domain.zakat

import com.ritesh.cashiro.data.database.entity.FitrEntryEntity
import com.ritesh.cashiro.data.database.entity.LivestockAnimalType
import com.ritesh.cashiro.data.database.entity.LivestockEntryEntity
import com.ritesh.cashiro.data.database.entity.UshrEntryEntity
import com.ritesh.cashiro.data.database.entity.UshrIrrigationType
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Ushr (agricultural produce zakat) — spec section 5.
 *
 * Completely separate from the nisab/hawl pool:
 *  - no hawl requirement: due at harvest time (5.1);
 *  - threshold ~720 kg (5 wasq) of harvested produce; below it, nothing
 *    is due (5.2);
 *  - rate 10% for naturally/rain-irrigated land, 5% for artificially
 *    irrigated land, and an optional 7.5% for mixed irrigation labelled
 *    as a minority/mixed-case option (5.3/5.4);
 *  - the rate applies to the harvest's market value.
 */
object UshrCalculator {

    /** 5 wasq; 1 wasq = 60 sa' ≈ 65.3 kg per classical measure → ~653 kg? */
    // The commonly used modern figure for the 5-wasq threshold is
    // ~653 kg (Prophet's sa' ≈ 2.176 kg × 60 × 5). Many contemporary
    // authorities round it to ~720 kg. The specification pins the
    // threshold at ~720 kg, so that value is used (conservative: it
    // slightly raises the threshold and thus lowers the obligation).
    val NISAB_KG: BigDecimal = BigDecimal("720")

    val RATE_NATURAL: BigDecimal = BigDecimal("0.10")
    val RATE_ARTIFICIAL: BigDecimal = BigDecimal("0.05")
    val RATE_MIXED: BigDecimal = BigDecimal("0.075")

    data class UshrResult(
        val thresholdMet: Boolean,
        val quantityKg: BigDecimal,
        val rate: BigDecimal,
        val ushrDue: BigDecimal
    )

    fun rateFor(irrigationType: String): BigDecimal {
        return when (
            runCatching { UshrIrrigationType.valueOf(irrigationType.uppercase()) }
                .getOrDefault(UshrIrrigationType.NATURAL)
        ) {
            UshrIrrigationType.NATURAL -> RATE_NATURAL
            UshrIrrigationType.ARTIFICIAL -> RATE_ARTIFICIAL
            UshrIrrigationType.MIXED -> RATE_MIXED
        }
    }

    fun calculate(quantityKg: BigDecimal, marketValue: BigDecimal, irrigationType: String): UshrResult {
        val thresholdMet = quantityKg >= NISAB_KG
        val rate = rateFor(irrigationType)
        val due = if (thresholdMet && marketValue.signum() > 0) {
            marketValue.multiply(rate).setScale(2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO.setScale(2)
        }
        return UshrResult(
            thresholdMet = thresholdMet,
            quantityKg = quantityKg,
            rate = rate,
            ushrDue = due
        )
    }

    fun calculate(entry: UshrEntryEntity): UshrResult =
        calculate(entry.quantityKg, entry.marketValue, entry.irrigationType)
}

/**
 * Traditional/grazing livestock (Sa'ima) zakat — spec section 6.
 *
 * Implements the classical stepped nisab tables:
 *  - Sheep/goats: nisab 40. 40–120 → 1 sheep; 121–200 → 2; 201–399 → 3;
 *    400+ → 4 plus 1 for every additional 100 (i.e. count/100).
 *  - Cattle: nisab 30. 30–39 → 1 tabi'; 40–59 → 1 musinnah; 60–69 → 2 tabi';
 *    70–79 → 1 tabi' + 1 musinnah; 80–89 → 2 musinnah; 90–99 → 3 tabi';
 *    100+ → the classical 30/40 split rule (every 30 → 1 tabi', every
 *    40 → 1 musinnah, combining to cover the herd with the largest
 *    coverage achievable).
 *  - Camels: nisab 5. 5–9 → 1 sheep; 10–14 → 2; 15–19 → 3; 20–24 → 4;
 *    25–35 → 1 bint makhad; 36–45 → 1 bint labun; 46–60 → 1 hiqqah;
 *    61–75 → 1 jadha'ah; 76–90 → 2 bint labun; 91–120 → 2 hiqqah;
 *    121+ → per the classical rule of one bint labun per 40 and one
 *    hiqqah per 50, combined to cover the herd.
 *
 * Commercial livestock is NOT handled here (it is trade stock, spec 1.4);
 * herds below their type's nisab owe nothing (6.3).
 */
object LivestockCalculator {

    data class LivestockDue(
        /** Human-readable units due, e.g. "1 bint labun". */
        val description: String,
        /** Count of sheep-equivalent animals due (for sheep: the sheep count). */
        val sheepDue: Int = 0,
        val bintMakhadDue: Int = 0,
        val bintLabunDue: Int = 0,
        val hiqqahDue: Int = 0,
        val jadhaahDue: Int = 0,
        val tabiDue: Int = 0,
        val musinnahDue: Int = 0,
        /** True when the herd meets its type's nisab. */
        val nisabMet: Boolean
    ) {
        val totalAnimalsDue: Int
            get() = sheepDue + bintMakhadDue + bintLabunDue + hiqqahDue +
                jadhaahDue + tabiDue + musinnahDue
    }

    // ---------------- Sheep / goats ----------------

    fun sheepDue(count: Int): LivestockDue {
        return when {
            count < 40 -> LivestockDue("Nothing due", nisabMet = false)
            count <= 120 -> LivestockDue("1 sheep/goat", sheepDue = 1, nisabMet = true)
            count <= 200 -> LivestockDue("2 sheep/goats", sheepDue = 2, nisabMet = true)
            count <= 399 -> LivestockDue("3 sheep/goats", sheepDue = 3, nisabMet = true)
            else -> {
                // 400 → 4, 500 → 5, ...: one sheep per full 100 animals.
                val due = count / 100
                LivestockDue("$due sheep/goats", sheepDue = due, nisabMet = true)
            }
        }
    }

    // ---------------- Cattle ----------------

    fun cattleDue(count: Int): LivestockDue {
        return when {
            count < 30 -> LivestockDue("Nothing due", nisabMet = false)
            count <= 39 -> LivestockDue("1 tabi'", tabiDue = 1, nisabMet = true)
            count <= 59 -> LivestockDue("1 musinnah", musinnahDue = 1, nisabMet = true)
            count <= 69 -> LivestockDue("2 tabi'", tabiDue = 2, nisabMet = true)
            count <= 79 -> LivestockDue(
                "1 tabi' + 1 musinnah", tabiDue = 1, musinnahDue = 1, nisabMet = true
            )
            count <= 89 -> LivestockDue("2 musinnah", musinnahDue = 2, nisabMet = true)
            count <= 99 -> LivestockDue("3 tabi'", tabiDue = 3, nisabMet = true)
            count <= 109 -> LivestockDue(
                "2 tabi' + 1 musinnah", tabiDue = 2, musinnahDue = 1, nisabMet = true
            )
            else -> {
                // Beyond 110: classical 30/40 combination rule — every 30
                // cattle → 1 tabi', every 40 → 1 musinnah, using the
                // combination that covers the most animals without
                // exceeding the herd size.
                val best = bestSplit(count, smallGroup = 30, largeGroup = 40)
                val parts = mutableListOf<String>()
                if (best.second > 0) parts.add("${best.second} tabi'")
                if (best.first > 0) parts.add("${best.first} musinnah")
                LivestockDue(
                    parts.joinToString(" + "),
                    tabiDue = best.second,
                    musinnahDue = best.first,
                    nisabMet = true
                )
            }
        }
    }

    // ---------------- Camels ----------------

    fun camelDue(count: Int): LivestockDue {
        return when {
            count < 5 -> LivestockDue("Nothing due", nisabMet = false)
            count <= 9 -> LivestockDue("1 sheep/goat", sheepDue = 1, nisabMet = true)
            count <= 14 -> LivestockDue("2 sheep/goats", sheepDue = 2, nisabMet = true)
            count <= 19 -> LivestockDue("3 sheep/goats", sheepDue = 3, nisabMet = true)
            count <= 24 -> LivestockDue("4 sheep/goats", sheepDue = 4, nisabMet = true)
            count <= 35 -> LivestockDue("1 bint makhad", bintMakhadDue = 1, nisabMet = true)
            count <= 45 -> LivestockDue("1 bint labun", bintLabunDue = 1, nisabMet = true)
            count <= 60 -> LivestockDue("1 hiqqah", hiqqahDue = 1, nisabMet = true)
            count <= 75 -> LivestockDue("1 jadha'ah", jadhaahDue = 1, nisabMet = true)
            count <= 90 -> LivestockDue("2 bint labun", bintLabunDue = 2, nisabMet = true)
            count <= 120 -> LivestockDue("2 hiqqah", hiqqahDue = 2, nisabMet = true)
            else -> {
                // Above 120: classical rule — for every 40 camels, 1 bint
                // labun; for every 50, 1 hiqqah — using the combination
                // that covers the most camels without exceeding the herd.
                val best = bestSplit(count, smallGroup = 40, largeGroup = 50)
                val parts = mutableListOf<String>()
                if (best.second > 0) parts.add("${best.second} bint labun")
                if (best.first > 0) parts.add("${best.first} hiqqah")
                LivestockDue(
                    parts.joinToString(" + "),
                    hiqqahDue = best.first,
                    bintLabunDue = best.second,
                    nisabMet = true
                )
            }
        }
    }

    /**
     * Finds the mix of [largeGroup]s (returned first) and [smallGroup]s
     * (returned second) whose total coverage is the largest value ≤ count.
     * Used by the classical camel (40/50) and cattle (30/40) tables beyond
     * their fixed bracket ranges.
     */
    private fun bestSplit(count: Int, smallGroup: Int, largeGroup: Int): Pair<Int, Int> {
        var bestLarge = 0
        var bestSmall = 0
        var bestCoverage = 0
        val maxLarge = count / largeGroup
        for (large in 0..maxLarge) {
            val remainder = count - large * largeGroup
            val small = remainder / smallGroup
            val coverage = large * largeGroup + small * smallGroup
            if (coverage > bestCoverage ||
                (coverage == bestCoverage && large + small < bestLarge + bestSmall)
            ) {
                bestLarge = large
                bestSmall = small
                bestCoverage = coverage
            }
        }
        return bestLarge to bestSmall
    }

    /** Routes to the correct table for one herd entry. */
    fun calculate(entry: LivestockEntryEntity): LivestockDue {
        if (!entry.isGrazing) {
            // Non-grazing (commercial) livestock is trade stock: the user
            // is guided to value it under business assets instead.
            return LivestockDue("Commercial livestock — use business assets", nisabMet = false)
        }
        return when (
            runCatching { LivestockAnimalType.valueOf(entry.animalType.uppercase()) }
                .getOrDefault(LivestockAnimalType.SHEEP)
        ) {
            LivestockAnimalType.SHEEP -> sheepDue(entry.count)
            LivestockAnimalType.CATTLE -> cattleDue(entry.count)
            LivestockAnimalType.CAMEL -> camelDue(entry.count)
        }
    }
}

/**
 * Zakatul Fitr — spec section 9.
 *
 * Not tied to nisab, hawl or wealth at all: a fixed per-person obligation
 * due before the Eid al-Fitr prayer, owed on behalf of every household
 * member including dependents. Total = price per kg × kg per person ×
 * household member count.
 */
object ZakatulFitrCalculator {

    /** Minimum (Hanafi) per-person quantity: 2 sa' ≈ 2.5 kg of staple food. */
    val DEFAULT_KG_PER_PERSON: BigDecimal = BigDecimal("2.5")

    /** Upper reference (~1 sa' ≈ 3 kg) offered in the UI. */
    val MAX_KG_PER_PERSON: BigDecimal = BigDecimal("3")

    data class FitrResult(
        val amountPerPerson: BigDecimal,
        val totalDue: BigDecimal,
        val householdCount: Int
    )

    fun calculate(entry: FitrEntryEntity): FitrResult {
        val kgPerPerson = if (entry.kgPerPerson.signum() > 0) {
            entry.kgPerPerson
        } else {
            DEFAULT_KG_PER_PERSON
        }
        val perPerson = entry.pricePerKg.multiply(kgPerPerson)
            .setScale(2, RoundingMode.HALF_UP)
        val total = if (entry.householdCount > 0) {
            perPerson.multiply(BigDecimal(entry.householdCount))
                .setScale(2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO.setScale(2)
        }
        return FitrResult(
            amountPerPerson = perPerson,
            totalDue = total,
            householdCount = entry.householdCount
        )
    }
}
