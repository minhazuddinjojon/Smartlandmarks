package com.example.smartlandmarks.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.smartlandmarks.data.local.entity.LandmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LandmarkDao {

    /**
     * Soft-deleted landmarks are filtered out here rather than in the UI, so no screen
     * can accidentally show one by forgetting the check.
     */
    @Query("SELECT * FROM landmarks WHERE is_active = 1 ORDER BY score DESC")
    fun observeActive(): Flow<List<LandmarkEntity>>

    @Query("SELECT * FROM landmarks WHERE id = :id LIMIT 1")
    fun observeById(id: Int): Flow<LandmarkEntity?>

    @Query("SELECT * FROM landmarks WHERE id = :id LIMIT 1")
    suspend fun findById(id: Int): LandmarkEntity?

    @Query("SELECT COUNT(*) FROM landmarks WHERE is_active = 1")
    suspend fun countActive(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(landmarks: List<LandmarkEntity>)

    @Query("UPDATE landmarks SET is_active = :active WHERE id = :id")
    suspend fun setActive(id: Int, active: Boolean)

    @Query("DELETE FROM landmarks WHERE id NOT IN (:keepIds)")
    suspend fun deleteMissing(keepIds: List<Int>)

    @Query("DELETE FROM landmarks")
    suspend fun deleteAll()

    /**
     * Replaces the cache with the server's view in one transaction.
     *
     * Reconciling rather than blindly inserting matters: a landmark soft-deleted by
     * another client disappears from get_landmarks, and without this pass it would
     * linger in the local cache forever.
     */
    @Transaction
    suspend fun replaceAll(landmarks: List<LandmarkEntity>) {
        if (landmarks.isEmpty()) {
            deleteAll()
            return
        }
        deleteMissing(landmarks.map { it.id })
        upsertAll(landmarks)
    }
}
