package com.example.smartlandmarks.domain.model

/** A landmark created while offline, waiting to be uploaded. */
data class PendingLandmark(
    val localId: Long,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val imagePath: String?,
    val createdAt: Long
)
