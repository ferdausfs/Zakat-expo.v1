package com.ritesh.cashiro.data.database

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import android.database.Cursor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies the REAL Room migration chain on the committed schema snapshots
 * (app/schemas/com.ritesh.cashiro.data.database.CashiroDatabase/62.json, 64.json, 65.json):
 *
 *  1. 62 -> 65 : the full upgrade path of every user who last installed
 *     v2.1.62/63-beta (schema 62) and jumped to v2.1.65+ — creates the
 *     zakat_assets table, the four transaction hot-path indices, the four
 *     zakat_assets purpose columns and the five A-Z module tables. This is
 *     the exact path that used to crash ("A migration from 62 to 63 was
 *     required but not found") when DatabaseModule lagged behind
 *     getInstance().
 *  2. 64 -> 65 : the path of v2.1.65-67-beta users updating to v2.1.68+
 *     — the crash reported from a Galaxy A54 (SM-A546E): "A migration from
 *     64 to 65 was required but not found". Existing zakat_assets rows
 *     must survive with the new columns defaulted (RESALE / TRADING /
 *     not-amanat / not-personal-use), so old entries stay zakatable
 *     exactly as before.
 *  3. Fresh install : no prior database — Room must create schema 65
 *     directly and data must round-trip.
 *
 * Every runMigrationsAndValidate() call validates the final schema
 * (tables AND indices AND identity hash) against the committed 65.json,
 * so a wrong migration SQL fails here and not on a user's phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseMigrationTest {

    private fun helper(): MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            CashiroDatabase::class.java,
        )

    private fun Cursor.stringAt(column: String): String = getString(getColumnIndexOrThrow(column))

    private fun Cursor.longAt(column: String): Long = getLong(getColumnIndexOrThrow(column))

    @Test
    fun migrate62To65_preservesAccountsAndTransactions() {
        val h = helper()
        val dbName = "migration-test-62.db"

        // Build a database exactly as a v2.1.63-beta user's phone had it.
        h.createDatabase(dbName, 62).use { db ->
            db.execSQL(
                """
                INSERT INTO account_balances (
                    icon_res_id, icon_name, bank_name, account_last4, balance, timestamp,
                    is_credit_card, created_at, currency, is_wallet, color, is_sample
                ) VALUES (
                    1, 'bank', 'Al Rajhi Bank', '1234', '5000.00', '2026-01-01T10:00:00',
                    0, '2026-01-01T10:00:00', 'SAR', 0, '#000000', 0
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO transactions (
                    amount, merchant_name, category, transaction_type, date_time,
                    transaction_hash, is_recurring, is_deleted, created_at, updated_at,
                    currency, attachments, is_sample
                ) VALUES (
                    '250.50', 'Test Merchant', 'Groceries', 'expense', '2026-01-01T10:00:00',
                    'hash-1', 0, 0, '2026-01-01T10:00:00', '2026-01-01T10:00:00',
                    'SAR', '[]', 0
                )
                """.trimIndent()
            )
        }

        // Migrate through every real gap to the current version and validate
        // the resulting schema against the committed 65.json.
        h.runMigrationsAndValidate(
            dbName,
            65,
            true,
            CashiroDatabase.MIGRATION_62_63,
            CashiroDatabase.MIGRATION_63_64,
            CashiroDatabase.MIGRATION_64_65,
        ).use { db ->
            // Pre-existing data survived.
            db.query(
                "SELECT balance, bank_name FROM account_balances WHERE account_last4 = '1234'"
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("5000.00", c.stringAt("balance"))
                assertEquals("Al Rajhi Bank", c.stringAt("bank_name"))
            }
            db.query(
                "SELECT COUNT(*) FROM transactions WHERE transaction_hash = 'hash-1'"
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1L, c.longAt("COUNT(*)"))
            }

            // New module tables created and empty.
            listOf(
                "zakat_assets",
                "zakat_liabilities",
                "ushr_entries",
                "livestock_entries",
                "zakatul_fitr",
                "zakat_payments",
            ).forEach { table ->
                db.query("SELECT COUNT(*) FROM `$table`").use { c ->
                    assertTrue(c.moveToFirst())
                    assertEquals("table $table missing/empty-check", 0L, c.longAt("COUNT(*)"))
                }
            }

            // New zakat_assets columns from 64 -> 65 are queryable.
            db.query(
                "SELECT purpose, holding_intent, is_amanat, personal_use FROM zakat_assets LIMIT 1"
            ).use { c ->
                // Empty result is fine — the statement proves the columns exist.
                assertFalse(c.moveToFirst())
            }
        }
    }

    @Test
    fun migrate64To65_preservesAssetsWithSafeDefaults() {
        val h = helper()
        val dbName = "migration-test-64.db"

        // v2.1.65-67-beta state: zakat_assets exists with the original 12 columns.
        h.createDatabase(dbName, 64).use { db ->
            db.execSQL(
                """
                INSERT INTO zakat_assets (
                    type, name, quantity, unit, karat, currency, acquisition_date,
                    estimated_value, notes, is_deleted, created_at, updated_at
                ) VALUES (
                    'GOLD', 'Gold ring', '10', 'g', 22, 'SAR', '2025-06-01',
                    '3500.00', 'wedding gift', 0, '2026-01-01T10:00:00', '2026-01-01T10:00:00'
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO transactions (
                    amount, merchant_name, category, transaction_type, date_time,
                    transaction_hash, is_recurring, is_deleted, created_at, updated_at,
                    currency, attachments, is_sample
                ) VALUES (
                    '99.00', 'Pre-update TXN', 'Bills', 'expense', '2026-02-01T09:00:00',
                    'hash-2', 0, 0, '2026-02-01T09:00:00', '2026-02-01T09:00:00',
                    'SAR', '[]', 0
                )
                """.trimIndent()
            )
        }

        h.runMigrationsAndValidate(dbName, 65, true, CashiroDatabase.MIGRATION_64_65).use { db ->
            // The user's asset row survived, untouched, with the new columns
            // defaulted so it stays zakatable exactly as before the update.
            db.query(
                """
                SELECT name, estimated_value, quantity, purpose, holding_intent,
                       is_amanat, personal_use
                FROM zakat_assets WHERE name = 'Gold ring'
                """.trimIndent()
            ).use { c ->
                assertTrue("existing zakat asset lost during migration", c.moveToFirst())
                assertEquals("3500.00", c.stringAt("estimated_value"))
                assertEquals("10", c.stringAt("quantity"))
                assertEquals("RESALE", c.stringAt("purpose"))
                assertEquals("TRADING", c.stringAt("holding_intent"))
                assertEquals(0L, c.longAt("is_amanat"))
                assertEquals(0L, c.longAt("personal_use"))
            }

            // Pre-existing transactions survived too.
            db.query(
                "SELECT COUNT(*) FROM transactions WHERE transaction_hash = 'hash-2'"
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1L, c.longAt("COUNT(*)"))
            }

            // The five A-Z spec module tables exist and start empty.
            listOf(
                "zakat_liabilities",
                "ushr_entries",
                "livestock_entries",
                "zakatul_fitr",
                "zakat_payments",
            ).forEach { table ->
                db.query("SELECT COUNT(*) FROM `$table`").use { c ->
                    assertTrue(c.moveToFirst())
                    assertEquals("table $table missing after migration", 0L, c.longAt("COUNT(*)"))
                }
            }
        }
    }

    @Test
    fun freshInstall_createsSchema65AndRoundTripsData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "fresh-install-test.db"

        val db = Room.databaseBuilder(context, CashiroDatabase::class.java, dbName)
            // Fresh installs need no migration, but registering them must be harmless.
            .addMigrations(*CashiroDatabase.ALL_MIGRATIONS)
            .build()

        try {
            val conn = db.openHelper.writableDatabase
            conn.execSQL(
                """
                INSERT INTO account_balances (
                    icon_res_id, icon_name, bank_name, account_last4, balance, timestamp,
                    is_credit_card, created_at, currency, is_wallet, color, is_sample
                ) VALUES (
                    2, 'wallet', 'STC Pay', '5678', '1200.00', '2026-03-01T12:00:00',
                    0, '2026-03-01T12:00:00', 'SAR', 1, '#111111', 0
                )
                """.trimIndent()
            )
            conn.execSQL(
                """
                INSERT INTO zakat_payments (kind, amount, date, recipient, category, created_at, updated_at)
                VALUES ('ZAKAT', '500.00', '2026-03-01', 'Local masjid fund', 'Zakat al-mal',
                        '2026-03-01T12:00:00', '2026-03-01T12:00:00')
                """.trimIndent()
            )

            conn.query(
                "SELECT balance, bank_name FROM account_balances WHERE account_last4 = '5678'"
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("1200.00", c.stringAt("balance"))
                assertEquals("STC Pay", c.stringAt("bank_name"))
            }
            conn.query(
                "SELECT COUNT(*) FROM zakat_payments WHERE recipient = 'Local masjid fund'"
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1L, c.longAt("COUNT(*)"))
            }
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }
}
