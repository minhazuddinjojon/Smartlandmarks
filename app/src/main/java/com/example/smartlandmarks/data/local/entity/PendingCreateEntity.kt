package com.example.smartlandmarks.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A landmark the user added with no connectivity, waiting for the sync worker. */
@Entity(tableName = "pending_creates")
data class PendingCreateEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "local_id")
    val localId: Long = 0L,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "lat")
    val latitude: Double,

    @ColumnInfo(name = "lon")
    val longitude: Double,

    /** Absolute path to an app-private cache copy of the picked image. */
    @ColumnInfo(name = "image_path")
    val imagePath: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0
)
