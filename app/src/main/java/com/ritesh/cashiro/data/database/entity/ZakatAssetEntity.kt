package com.ritesh.cashiro.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ritesh.cashiro.data.model.Currency
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
    @ColumnInfo(name = "currency") val currency: String = Currency.DEFAULT_CURRENCY_CODE,
    /** Date the asset was acquired; drives per-asset hawl. */
    @ColumnInfo(name = "acquisition_date") val acquisitionDate: LocalDate = LocalDate.now(),
    /**
     * Current value for non-metal assets (property, business stock,
     * investments, other) in [currency]. Null for metals, whose value is
     * computed from quantity and the current metal price.
     */
    @ColumnInfo(name = "estimated_value") val estimatedValue: BigDecimal? = null,
    /**
     * PROPERTY only: why the property is held.
     * - RESALE: held for resale/trading — fully zakatable at market value
     *   (spec 1.9).
     * - PERSONAL: personal residence — NOT zakatable.
     * - RENTAL: income-generating rental — NOT zakatable (only the received
     *   rent, once it sits in an account a full hawl, is zakatable as cash).
     * Default RESALE keeps existing entries zakatable (conservative for
     * wealth; the user marks personal/rental property to exclude it).
     */
    @ColumnInfo(name = "purpose", defaultValue = "RESALE") val purpose: String =
        PropertyPurpose.RESALE.name,
    /**
     * INVESTMENT only (spec 1.5):
     * - TRADING: held for resale — fully zakatable at market value.
     * - LONG_TERM: held for long-term dividend income — excluded from the
     *   pool. The app applies the documented simplification: it does not
     *   force a look-through calculation of the underlying company's
     *   zakatable assets; the entry is flagged for informational purposes.
     */
    @ColumnInfo(name = "holding_intent", defaultValue = "TRADING") val holdingIntent: String =
        HoldingIntent.TRADING.name,
    /**
     * Amanat (spec 7.1): money/assets held in trust or as a deposit on
     * behalf of someone else. Excluded ENTIRELY from the zakatable pool,
     * nisab comparison and hawl tracking.
     */
    @ColumnInfo(name = "is_amanat", defaultValue = "0") val isAmanat: Boolean = false,
    /**
     * Metals only: jewelry worn for personal use. Under the Hanafi madhhab
     * all gold/silver jewelry is zakatable regardless of use; under the
     * other madhhabs personal-use jewelry is exempt. The pool applies the
     * user's madhhab setting (spec 1.10); default false (zakatable).
     */
    @ColumnInfo(name = "personal_use", defaultValue = "0") val personalUse: Boolean = false,
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
    /** Money owed TO the user, reasonably expected to be repaid (spec 1.6). */
    RECEIVABLE,
    /** Personal-use items explicitly excluded from zakatable wealth (spec 1.10). */
    PERSONAL,
    OTHER;

    val isMetal: Boolean get() = this == GOLD || this == SILVER

    /** Types whose entries are ALWAYS included in the pool by default. */
    val defaultZakatable: Boolean get() = when (this) {
        GOLD, SILVER, BUSINESS, RECEIVABLE, OTHER -> true
        // PROPERTY and INVESTMENT depend on purpose/holdingIntent flags.
        PROPERTY, INVESTMENT, PERSONAL -> false
    }
}

/** Why a PROPERTY entry is held (spec 1.9). */
enum class PropertyPurpose { PERSONAL, RENTAL, RESALE }

/** Whether an INVESTMENT entry is trading stock or a long-term holding (spec 1.5). */
enum class HoldingIntent { TRADING, LONG_TERM }

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
