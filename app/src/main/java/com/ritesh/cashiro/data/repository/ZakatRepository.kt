package com.ritesh.cashiro.data.repository

import com.ritesh.cashiro.data.database.dao.ZakatAssetDao
import com.ritesh.cashiro.data.database.entity.AccountBalanceEntity
import com.ritesh.cashiro.data.database.entity.ZakatAssetEntity
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Repository for zakat assets and the combined wealth pool (Phase 2b).
 *
 * Assets are stored in their own table; cash is read from the existing
 * [AccountBalanceRepository] history. Accounts/Transactions are neither
 * modified nor rewritten by this repository — it is a read-only consumer
 * of balance data, which keeps the existing data model untouched.
 */
@Singleton
class ZakatRepository @Inject constructor(
    private val zakatAssetDao: ZakatAssetDao,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val liabilityDao: com.ritesh.cashiro.data.database.dao.ZakatLiabilityDao,
    private val ushrEntryDao: com.ritesh.cashiro.data.database.dao.UshrEntryDao,
    private val livestockEntryDao: com.ritesh.cashiro.data.database.dao.LivestockEntryDao,
    private val fitrEntryDao: com.ritesh.cashiro.data.database.dao.FitrEntryDao,
    private val zakatPaymentDao: com.ritesh.cashiro.data.database.dao.ZakatPaymentDao
) {

    // ---------------- Assets CRUD ----------------

    fun observeAssets(): Flow<List<ZakatAssetEntity>> = zakatAssetDao.observeAll()

    suspend fun getAssets(): List<ZakatAssetEntity> = zakatAssetDao.getAll()

    suspend fun getAsset(id: Long): ZakatAssetEntity? = zakatAssetDao.getById(id)

    /** Inserts a new asset or updates an existing one; returns its row id. */
    suspend fun upsertAsset(asset: ZakatAssetEntity): Long {
        return if (asset.id == 0L) {
            zakatAssetDao.insert(asset)
        } else {
            zakatAssetDao.update(asset.copy(updatedAt = LocalDateTime.now()))
            asset.id
        }
    }

    /** Soft-deletes an asset so history and backups keep working. */
    suspend fun deleteAsset(id: Long) {
        zakatAssetDao.softDelete(id, LocalDateTime.now())
    }

    /** Permanently removes an asset row (explicit user action). */
    suspend fun purgeAsset(id: Long) {
        zakatAssetDao.hardDelete(id)
    }

    // ---------------- Cash pool input ----------------

    /**
     * Latest known balance for every account (credit cards included so the
     * UI can annotate exclusions; wealth-pool math filters them out).
     */
    fun observeLatestBalances(): Flow<List<AccountBalanceEntity>> =
        accountBalanceRepository.getAllLatestBalances()

    /**
     * Observes the full balance history, used to rebuild the daily cash
     * series for nisab-crossing detection on every change.
     */
    fun observeAllBalanceHistory(): Flow<List<AccountBalanceEntity>> =
        accountBalanceRepository.getAllBalances()

    /**
     * One-shot full balance history (same data as [observeAllBalanceHistory]).
     */
    suspend fun getBalanceHistory(): List<AccountBalanceEntity> =
        accountBalanceRepository.getAllBalances().first()

    // ---------------- Liabilities (deductible debts, spec 2.1) ----------------

    fun observeLiabilities(): Flow<List<com.ritesh.cashiro.data.database.entity.ZakatLiabilityEntity>> =
        liabilityDao.observeAll()

    suspend fun getLiabilities(): List<com.ritesh.cashiro.data.database.entity.ZakatLiabilityEntity> =
        liabilityDao.getAll()

    suspend fun upsertLiability(entry: com.ritesh.cashiro.data.database.entity.ZakatLiabilityEntity): Long {
        return if (entry.id == 0L) {
            liabilityDao.insert(entry)
        } else {
            liabilityDao.update(entry.copy(updatedAt = LocalDateTime.now()))
            entry.id
        }
    }

    suspend fun deleteLiability(id: Long) = liabilityDao.softDelete(id, LocalDateTime.now())

    // ---------------- Ushr harvests (spec 5) ----------------

    fun observeUshrEntries(): Flow<List<com.ritesh.cashiro.data.database.entity.UshrEntryEntity>> =
        ushrEntryDao.observeAll()

    suspend fun getUshrEntries(): List<com.ritesh.cashiro.data.database.entity.UshrEntryEntity> =
        ushrEntryDao.getAll()

    suspend fun upsertUshrEntry(entry: com.ritesh.cashiro.data.database.entity.UshrEntryEntity): Long {
        return if (entry.id == 0L) {
            ushrEntryDao.insert(entry)
        } else {
            ushrEntryDao.update(entry.copy(updatedAt = LocalDateTime.now()))
            entry.id
        }
    }

    suspend fun deleteUshrEntry(id: Long) = ushrEntryDao.softDelete(id, LocalDateTime.now())

    // ---------------- Livestock herds (spec 6) ----------------

    fun observeLivestockEntries(): Flow<List<com.ritesh.cashiro.data.database.entity.LivestockEntryEntity>> =
        livestockEntryDao.observeAll()

    suspend fun getLivestockEntries(): List<com.ritesh.cashiro.data.database.entity.LivestockEntryEntity> =
        livestockEntryDao.getAll()

    suspend fun upsertLivestockEntry(entry: com.ritesh.cashiro.data.database.entity.LivestockEntryEntity): Long {
        return if (entry.id == 0L) {
            livestockEntryDao.insert(entry)
        } else {
            livestockEntryDao.update(entry.copy(updatedAt = LocalDateTime.now()))
            entry.id
        }
    }

    suspend fun deleteLivestockEntry(id: Long) =
        livestockEntryDao.softDelete(id, LocalDateTime.now())

    // ---------------- Zakatul Fitr (spec 9) ----------------

    fun observeFitrEntries(): Flow<List<com.ritesh.cashiro.data.database.entity.FitrEntryEntity>> =
        fitrEntryDao.observeAll()

    suspend fun getFitrEntries(): List<com.ritesh.cashiro.data.database.entity.FitrEntryEntity> =
        fitrEntryDao.getAll()

    suspend fun upsertFitrEntry(entry: com.ritesh.cashiro.data.database.entity.FitrEntryEntity): Long {
        return if (entry.id == 0L) {
            fitrEntryDao.insert(entry)
        } else {
            fitrEntryDao.update(entry.copy(updatedAt = LocalDateTime.now()))
            entry.id
        }
    }

    suspend fun deleteFitrEntry(id: Long) = fitrEntryDao.softDelete(id, LocalDateTime.now())

    // ---------------- Payment & sadaqah log (spec 12) ----------------

    fun observePayments(): Flow<List<com.ritesh.cashiro.data.database.entity.ZakatPaymentEntity>> =
        zakatPaymentDao.observeAll()

    suspend fun getPayments(): List<com.ritesh.cashiro.data.database.entity.ZakatPaymentEntity> =
        zakatPaymentDao.getAll()

    suspend fun upsertPayment(entry: com.ritesh.cashiro.data.database.entity.ZakatPaymentEntity): Long {
        return if (entry.id == 0L) {
            zakatPaymentDao.insert(entry)
        } else {
            zakatPaymentDao.update(entry.copy(updatedAt = LocalDateTime.now()))
            entry.id
        }
    }

    suspend fun deletePayment(id: Long) = zakatPaymentDao.softDelete(id, LocalDateTime.now())
}
