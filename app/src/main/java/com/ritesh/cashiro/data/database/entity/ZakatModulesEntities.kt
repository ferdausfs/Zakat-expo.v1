package com.ritesh.cashiro.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A deductible debt/liability the user owes to others (spec 2.1).
 *
 * Short-term liabilities due within the zakat year are deducted from gross
 * zakatable wealth before the nisab comparison and rate. Long-term debts
 * (e.g. a 20-year mortgage) are handled by entering only the portion due
 * within the coming 12 months as its own entry — the user controls the
 * split, the calculator only ever deducts entries whose [dueDate] falls
 * within the next 12 months of the assessment date.
 */
@Entity(
    tableName = "zakat_liabilities",
    indices = [Index(value = ["due_date"])]
)
data class ZakatLiabilityEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    /** User-facing label, e.g. "Car loan instalments due this year". */
    @ColumnInfo(name = "name") val name: String = "",
    /** Outstanding amount due on [dueDate]. */
    @ColumnInfo(name = "amount") val amount: BigDecimal = BigDecimal.ZERO,
    /** Date the liability falls due. */
    @ColumnInfo(name = "due_date") val dueDate: LocalDate = LocalDate.now().plusMonths(1),
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: LocalDateTime = LocalDateTime.now(),
    @ColumnInfo(name = "updated_at") val updatedAt: LocalDateTime = LocalDateTime.now()
)

/** Irrigation method for a Ushr harvest entry (spec 5.3/5.4). */
enum class UshrIrrigationType {
    /** Naturally/rain irrigated — no significant cost or effort: 10%. */
    NATURAL,
    /** Artificially irrigated (pumps, purchased water, labour): 5%. */
    ARTIFICIAL,
    /** Mixed irrigation — minority/mixed-case option: 7.5%. */
    MIXED
}

/**
 * One harvest entry for the Ushr (agricultural produce) module (spec 5).
 *
 * Ushr is completely independent of the nisab/hawl pool: it is due at
 * harvest time, with no hawl requirement. The 5-wasq (~720 kg) threshold
 * applies to the harvested QUANTITY; the rate (10/5/7.5%) applies to the
 * harvest's market VALUE.
 */
@Entity(
    tableName = "ushr_entries",
    indices = [Index(value = ["harvest_date"])]
)
data class UshrEntryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    /** Crop label, e.g. "Wheat", "Dates". */
    @ColumnInfo(name = "crop_name") val cropName: String = "",
    /** Harvested quantity in kilograms. */
    @ColumnInfo(name = "quantity_kg") val quantityKg: BigDecimal = BigDecimal.ZERO,
    /** Market value of the harvest in the user's currency (total). */
    @ColumnInfo(name = "market_value") val marketValue: BigDecimal = BigDecimal.ZERO,
    @ColumnInfo(name = "irrigation_type") val irrigationType: String =
        UshrIrrigationType.NATURAL.name,
    @ColumnInfo(name = "harvest_date") val harvestDate: LocalDate = LocalDate.now(),
    /** Whether the Ushr due for this harvest has been paid. */
    @ColumnInfo(name = "is_paid", defaultValue = "0") val isPaid: Boolean = false,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: LocalDateTime = LocalDateTime.now(),
    @ColumnInfo(name = "updated_at") val updatedAt: LocalDateTime = LocalDateTime.now()
)

/** Animal class for the livestock module (spec 6). */
enum class LivestockAnimalType { CAMEL, CATTLE, SHEEP }

/**
 * One livestock herd entry (spec 6).
 *
 * Commercial livestock (raised for trade) is NOT calculated here — it is
 * business stock under spec 1.4 and should be entered as a BUSINESS zakat
 * asset valued at market. This table serves traditional/grazing livestock
 * (Sa'ima) whose zakat is paid from the animal stock itself using the
 * classical stepped nisab tables per animal type.
 */
@Entity(
    tableName = "livestock_entries",
    indices = [Index(value = ["animal_type"])]
)
data class LivestockEntryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    /** Herd label, e.g. "Desert sheep flock". */
    @ColumnInfo(name = "name") val name: String = "",
    @ColumnInfo(name = "animal_type") val animalType: String =
        LivestockAnimalType.SHEEP.name,
    /** Number of animals of this type in the herd. */
    @ColumnInfo(name = "count") val count: Int = 0,
    /** Grazes on natural pasture most of the year (Sa'ima). */
    @ColumnInfo(name = "is_grazing", defaultValue = "1") val isGrazing: Boolean = true,
    /** Whether the zakat due for this herd has been paid. */
    @ColumnInfo(name = "is_paid", defaultValue = "0") val isPaid: Boolean = false,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: LocalDateTime = LocalDateTime.now(),
    @ColumnInfo(name = "updated_at") val updatedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * Zakatul Fitr record (spec 9) — fully independent of the nisab/hawl pool.
 *
 * A fixed per-person obligation on behalf of every household member,
 * amount = staple price per kg × kg-per-person × household member count.
 * Tracked separately from the annual zakat calculation.
 */
@Entity(tableName = "zakatul_fitr")
data class FitrEntryEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    /** Gregorian (or lunar) year label, e.g. "1447 / 2026". */
    @ColumnInfo(name = "year_label") val yearLabel: String = "",
    /** Staple food used as the reference, e.g. "Rice", "Wheat", "Barley". */
    @ColumnInfo(name = "staple_name") val stapleName: String = "",
    /** Local price per kg of the staple. */
    @ColumnInfo(name = "price_per_kg") val pricePerKg: BigDecimal = BigDecimal.ZERO,
    /** Kg of staple per person (~1 sa' ≈ 2.5–3 kg; default 2.5). */
    @ColumnInfo(name = "kg_per_person", defaultValue = "2.5") val kgPerPerson: BigDecimal =
        BigDecimal("2.5"),
    /** Number of household members the obligation is owed for. */
    @ColumnInfo(name = "household_count") val householdCount: Int = 1,
    /** Due date (Eid al-Fitr day). */
    @ColumnInfo(name = "due_date") val dueDate: LocalDate? = null,
    /** Whether this Fitr obligation has been paid. */
    @ColumnInfo(name = "is_paid", defaultValue = "0") val isPaid: Boolean = false,
    /** When it was paid (for the record). */
    @ColumnInfo(name = "paid_at") val paidAt: LocalDate? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: LocalDateTime = LocalDateTime.now(),
    @ColumnInfo(name = "updated_at") val updatedAt: LocalDateTime = LocalDateTime.now()
)

/** What kind of giving was recorded (spec 12). */
enum class ZakatPaymentKind { ZAKAT, SADAQAH }

/**
 * Independent log of actual zakat payments and voluntary sadaqah (spec 12).
 *
 * Record-keeping only — never feeds back into any calculation. Linked from
 * the Zakat dashboard.
 */
@Entity(
    tableName = "zakat_payments",
    indices = [Index(value = ["date"])]
)
data class ZakatPaymentEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "kind") val kind: String = ZakatPaymentKind.ZAKAT.name,
    /** Amount actually paid. */
    @ColumnInfo(name = "amount") val amount: BigDecimal = BigDecimal.ZERO,
    /** Date of the payment. */
    @ColumnInfo(name = "date") val date: LocalDate = LocalDate.now(),
    /** Recipient or organisation, optional. */
    @ColumnInfo(name = "recipient") val recipient: String = "",
    /** Category note, e.g. "Fitr", "Ushr", "Annual zakat", optional. */
    @ColumnInfo(name = "category") val category: String = "",
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: LocalDateTime = LocalDateTime.now(),
    @ColumnInfo(name = "updated_at") val updatedAt: LocalDateTime = LocalDateTime.now()
)
