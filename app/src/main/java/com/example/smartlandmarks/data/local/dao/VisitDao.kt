package com.example.smartlandmarks.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.smartlandmarks.data.local.entity.VisitEntity
import com.example.smartlandmarks.domain.model.VisitStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitDao {

    @Query("SELECT * FROM visits ORDER BY created_at DESC")
    fun observeAll(): Flow<List<VisitEntity>>

    @Query("SELECT * FROM visits WHERE local_id = :localId LIMIT 1")
    fun observeById(localId: Long): Flow<VisitEntity?>

    @Query("SELECT COUNT(*) FROM visits WHERE status IN ('QUEUED', 'PENDING')")
    fun observeUnresolvedCount(): Flow<Int>

    /** Offline queue: visits recorded but never posted. */
    @Query("SELECT * FROM visits WHERE status = 'QUEUED' ORDER BY created_at ASC")
    suspend fun findQueued(): List<VisitEntity>

    /** Jobs accepted by the server that still need polling. */
    @Query("SELECT * FROM visits WHERE status = 'PENDING' AND job_id IS NOT NULL ORDER BY created_at ASC")
    suspend fun findPending(): List<VisitEntity>

    @Query("SELECT COUNT(*) FROM visits WHERE status IN ('QUEUED', 'PENDING')")
    suspend fun countUnresolved(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(visit: VisitEntity): Long

    @Update
    suspend fun update(visit: VisitEntity)

    @Query(
        """
        UPDATE visits
        SET job_id = :jobId, status = :status, attempt_count = :attempts,
            updated_at = :updatedAt, error_message = NULL
        WHERE local_id = :localId
        """
    )
    suspend fun markPosted(
        localId: Long,
        jobId: Int,
        status: VisitStatus,
        attempts: Int,
        updatedAt: Long
    )

    @Query(
        """
        UPDATE visits
        SET status = :status, distance = :distance, updated_at = :updatedAt,
            error_message = NULL
        WHERE local_id = :localId
        """
    )
    suspend fun markResolved(
        localId: Long,
        status: VisitStatus,
        distance: Double?,
        updatedAt: Long
    )

    @Query(
        """
        UPDATE visits
        SET status = :status, attempt_count = :attempts, error_message = :message,
            updated_at = :updatedAt
        WHERE local_id = :localId
        """
    )
    suspend fun markAttempt(
        localId: Long,
        status: VisitStatus,
        attempts: Int,
        message: String?,
        updatedAt: Long
    )

    @Query("DELETE FROM visits WHERE local_id = :localId")
    suspend fun delete(localId: Long)

    @Query("DELETE FROM visits")
    suspend fun deleteAll()
}
