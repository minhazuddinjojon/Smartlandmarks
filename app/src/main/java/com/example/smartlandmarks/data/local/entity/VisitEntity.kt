package com.example.smartlandmarks.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.smartlandmarks.domain.model.VisitStatus

/**
 * One row per visit attempt — and this table is deliberately doing two jobs at once.
 *
 * It is both the Activity screen's history and the offline visit queue. A visit made
 * offline is simply a row with status QUEUED and no job_id; once posted it becomes
 * PENDING with a job_id; once the server resolves it, DONE with a distance. Keeping
 * one table means the user can watch a queued visit progress instead of it vanishing
 * into an invisible outbox.
 */
@Entity(
    tableName = "visits",
    indices = [Index("status"), Index("landmark_id")]
)
data class VisitEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "local_id")
    val localId: Long = 0L,

    @ColumnInfo(name = "landmark_id")
    val landmarkId: Int,

    /** Denormalised so history still reads correctly if the landmark is later deleted. */
    @ColumnInfo(name = "landmark_title")
    val landmarkTitle: String,

    @ColumnInfo(name = "user_lat")
    val userLatitude: Double,

    @ColumnInfo(name = "user_lon")
    val userLongitude: Double,

    @ColumnInfo(name = "job_id")
    val jobId: Int?,

    @ColumnInfo(name = "status")
    val status: VisitStatus,

    @ColumnInfo(name = "distance")
    val distanceMetres: Double?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null
)
