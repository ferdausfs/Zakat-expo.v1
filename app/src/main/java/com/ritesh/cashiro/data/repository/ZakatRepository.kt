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
    private val accountBalanceRepository: AccountBalanceRepository
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
}
