package com.example.smartlandmarks.domain.model

/**
 * A landmark as the UI understands it. Deliberately free of Room and Retrofit types so
 * the presentation layer never depends on where the data came from.
 */
data class Landmark(
    val id: Int,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val imageUrl: String?,
    val score: Double,
    val visitCount: Int,
    val averageDistance: Double,
    val isActive: Boolean
)
