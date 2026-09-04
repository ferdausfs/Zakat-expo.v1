package com.ritesh.cashiro.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ritesh.cashiro.data.database.entity.FitrEntryEntity
import com.ritesh.cashiro.data.database.entity.LivestockEntryEntity
import com.ritesh.cashiro.data.database.entity.UshrEntryEntity
import com.ritesh.cashiro.data.database.entity.ZakatLiabilityEntity
import com.ritesh.cashiro.data.database.entity.ZakatPaymentEntity
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow

/**
 * DAOs for the zakat module tables (liabilities, Ushr, livestock,
 * Zakatul Fitr, payment log). Deletions are soft by default so records
 * survive backup/restore cycles, mirroring [ZakatAssetDao].
 */
@Dao
interface ZakatLiabilityDao {
    @Query("SELECT * FROM zakat_liabilities WHERE is_deleted = 0 ORDER BY due_date ASC, id DESC")
    fun observeAll(): Flow<List<ZakatLiabilityEntity>>

    @Query("SELECT * FROM zakat_liabilities WHERE is_deleted = 0 ORDER BY due_date ASC, id DESC")
    suspend fun getAll(): List<ZakatLiabilityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ZakatLiabilityEntity): Long

    @Update
    suspend fun update(entry: ZakatLiabilityEntity)

    @Query("UPDATE zakat_liabilities SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: LocalDateTime)

    @Query("DELETE FROM zakat_liabilities WHERE id = :id")
    suspend fun hardDelete(id: Long)
}

@Dao
interface UshrEntryDao {
    @Query("SELECT * FROM ushr_entries WHERE is_deleted = 0 ORDER BY harvest_date DESC, id DESC")
    fun observeAll(): Flow<List<UshrEntryEntity>>

    @Query("SELECT * FROM ushr_entries WHERE is_deleted = 0 ORDER BY harvest_date DESC, id DESC")
    suspend fun getAll(): List<UshrEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: UshrEntryEntity): Long

    @Update
    suspend fun update(entry: UshrEntryEntity)

    @Query("UPDATE ushr_entries SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: LocalDateTime)

    @Query("DELETE FROM ushr_entries WHERE id = :id")
    suspend fun hardDelete(id: Long)
}

@Dao
interface LivestockEntryDao {
    @Query("SELECT * FROM livestock_entries WHERE is_deleted = 0 ORDER BY id DESC")
    fun observeAll(): Flow<List<LivestockEntryEntity>>

    @Query("SELECT * FROM livestock_entries WHERE is_deleted = 0 ORDER BY id DESC")
    suspend fun getAll(): List<LivestockEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LivestockEntryEntity): Long

    @Update
    suspend fun update(entry: LivestockEntryEntity)

    @Query("UPDATE livestock_entries SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: LocalDateTime)

    @Query("DELETE FROM livestock_entries WHERE id = :id")
    suspend fun hardDelete(id: Long)
}

@Dao
interface FitrEntryDao {
    @Query("SELECT * FROM zakatul_fitr WHERE is_deleted = 0 ORDER BY due_date DESC, id DESC")
    fun observeAll(): Flow<List<FitrEntryEntity>>

    @Query("SELECT * FROM zakatul_fitr WHERE is_deleted = 0 ORDER BY due_date DESC, id DESC")
    suspend fun getAll(): List<FitrEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: FitrEntryEntity): Long

    @Update
    suspend fun update(entry: FitrEntryEntity)

    @Query("UPDATE zakatul_fitr SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: LocalDateTime)

    @Query("DELETE FROM zakatul_fitr WHERE id = :id")
    suspend fun hardDelete(id: Long)
}

@Dao
interface ZakatPaymentDao {
    @Query("SELECT * FROM zakat_payments WHERE is_deleted = 0 ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<ZakatPaymentEntity>>

    @Query("SELECT * FROM zakat_payments WHERE is_deleted = 0 ORDER BY date DESC, id DESC")
    suspend fun getAll(): List<ZakatPaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ZakatPaymentEntity): Long

    @Update
    suspend fun update(entry: ZakatPaymentEntity)

    @Query("UPDATE zakat_payments SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: LocalDateTime)

    @Query("DELETE FROM zakat_payments WHERE id = :id")
    suspend fun hardDelete(id: Long)
}
