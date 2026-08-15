package com.example.smartlandmarks.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached copy of a server landmark. Room is the single source of truth: the UI reads
 * only from here, and the network layer's job is to keep this table current.
 */
@Entity(tableName = "landmarks")
data class LandmarkEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "lat")
    val latitude: Double,

    @ColumnInfo(name = "lon")
    val longitude: Double,

    @ColumnInfo(name = "image_path")
    val imagePath: String?,

    @ColumnInfo(name = "score")
    val score: Double,

    @ColumnInfo(name = "visit_count")
    val visitCount: Int,

    @ColumnInfo(name = "avg_distance")
    val averageDistance: Double,

    /** Soft delete flag. Rows are kept so a restore does not need a full refetch. */
    @ColumnInfo(name = "is_active")
    val isActive: Boolean,

    @ColumnInfo(name = "cached_at")
    val cachedAt: Long
)
