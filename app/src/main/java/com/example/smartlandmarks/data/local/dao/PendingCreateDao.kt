package com.example.smartlandmarks.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.smartlandmarks.data.local.entity.PendingCreateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingCreateDao {

    @Query("SELECT * FROM pending_creates ORDER BY created_at ASC")
    suspend fun findAll(): List<PendingCreateEntity>

    @Query("SELECT COUNT(*) FROM pending_creates")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM pending_creates")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PendingCreateEntity): Long

    @Query("UPDATE pending_creates SET attempt_count = :attempts WHERE local_id = :localId")
    suspend fun updateAttempts(localId: Long, attempts: Int)

    @Query("DELETE FROM pending_creates WHERE local_id = :localId")
    suspend fun delete(localId: Long)
}
