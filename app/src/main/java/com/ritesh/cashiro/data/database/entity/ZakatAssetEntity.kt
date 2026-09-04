package com.ritesh.cashiro.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A single zakat-asset entry (Phase 2b).
 *
 * Assets are an additive top-level data type, parallel to Accounts and
 * Transactions. Accounts/Transactions keep tracking cash flow; this table
 * tracks non-cash zakatable holdings (gold, silver, property, business
 * stock, investments, other) together with the date they were acquired,
 * which drives per-asset hawl calculations.
 *
 * Metals (gold/silver) are stored as a quantity plus a unit from the
 * Bangladeshi jeweller's unit system (gram / vori / ana / ratti), with an
 * optional karat for gold purity. Their current value is derived at
 * assessment time from the user-maintained market price per gram.
 *
 * Non-metal assets carry a user-entered [estimatedValue] in the asset's
 * own currency; there is no live rate source for property/business
 * holdings, so the user keeps the value current by editing the entry.
 */
@Entity(
    tableName = "zakat_assets",
    indices = [
        Index(value = ["type"]),
        Index(value = ["acquisition_date"])
    ]
)
data class ZakatAssetEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    /** Asset class; see [ZakatAssetType]. */
    @ColumnInfo(name = "type") val type: String = ZakatAssetType.OTHER.name,
    /** User-facing label, e.g. "Wedding gold set". */
    @ColumnInfo(name = "name") val name: String = "",
    /** Quantity held; for metals this is in [unit] units, for others 1. */
    @ColumnInfo(name = "quantity") val quantity: BigDecimal = BigDecimal.ONE,
    /** Measurement unit; see [ZakatAssetUnit]. */
    @ColumnInfo(name = "unit") val unit: String = ZakatAssetUnit.GRAM.name,
    /** Gold purity in karat (24/22/21/18); null for silver and non-metals. */
    @ColumnInfo(name = "karat") val karat: Int? = null,
    /** Currency the asset is denominated/valued in (e.g. SAR, BDT). */
    @ColumnInfo(name = "currency") val currency: String = "INR",
    /** Date the asset was acquired; drives per-asset hawl. */
    @ColumnInfo(name = "acquisition_date") val acquisitionDate: LocalDate = LocalDate.now(),
    /**
     * Current value for non-metal assets (property, business stock,
     * investments, other) in [currency]. Null for metals, whose value is
     * computed from quantity and the current metal price.
     */
    @ColumnInfo(name = "estimated_value") val estimatedValue: BigDecimal? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "is_deleted", defaultValue = "0") val isDeleted: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: LocalDateTime = LocalDateTime.now(),
    @ColumnInfo(name = "updated_at") val updatedAt: LocalDateTime = LocalDateTime.now()
)

/** Asset classes tracked for zakat. */
enum class ZakatAssetType {
    GOLD,
    SILVER,
    PROPERTY,
    BUSINESS,
    INVESTMENT,
    OTHER;

    val isMetal: Boolean get() = this == GOLD || this == SILVER
}

/** Measurement units for metal quantities (Bangladeshi jeweller's system). */
enum class ZakatAssetUnit(val grams: BigDecimal?) {
    /** Metric gram. */
    GRAM(BigDecimal.ONE),

    /** 1 vori (bhori) = 11.664 g (Bangladesh government standard). */
    VORI(BigDecimal("11.664")),

    /** 1 ana = 1/16 vori = 0.729 g. */
    ANA(BigDecimal("0.729")),

    /** 1 ratti = 1/48 vori = 0.243 g. */
    RATTI(BigDecimal("0.243")),

    /** Non-metal entries: value entered directly, no unit conversion. */
    VALUE(null)
}
