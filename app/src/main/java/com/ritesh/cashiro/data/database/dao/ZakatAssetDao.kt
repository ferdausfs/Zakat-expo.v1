package com.ritesh.cashiro.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ritesh.cashiro.data.database.entity.ZakatAssetEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for the zakat_assets table (Phase 2b).
 *
 * Deletions are soft by default so asset entries survive backup/restore
 * cycles and can be audited; a hard delete is provided for explicit
 * "remove permanently" actions.
 */
@Dao
interface ZakatAssetDao {

    @Query("SELECT * FROM zakat_assets WHERE is_deleted = 0 ORDER BY acquisition_date DESC, id DESC")
    fun observeAll(): Flow<List<ZakatAssetEntity>>

    @Query("SELECT * FROM zakat_assets WHERE is_deleted = 0 ORDER BY acquisition_date DESC, id DESC")
    suspend fun getAll(): List<ZakatAssetEntity>

    @Query("SELECT * FROM zakat_assets WHERE id = :id")
    suspend fun getById(id: Long): ZakatAssetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(asset: ZakatAssetEntity): Long

    @Update
    suspend fun update(asset: ZakatAssetEntity)

    @Query("UPDATE zakat_assets SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: java.time.LocalDateTime)

    @Query("DELETE FROM zakat_assets WHERE id = :id")
    suspend fun hardDelete(id: Long)

    @Query("SELECT COUNT(*) FROM zakat_assets WHERE is_deleted = 0")
    suspend fun countActive(): Int
}
