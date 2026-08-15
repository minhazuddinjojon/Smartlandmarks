package com.example.smartlandmarks.data.mapper

import com.example.smartlandmarks.data.local.entity.LandmarkEntity
import com.example.smartlandmarks.data.local.entity.PendingCreateEntity
import com.example.smartlandmarks.data.local.entity.VisitEntity
import com.example.smartlandmarks.data.remote.dto.LandmarkDto
import com.example.smartlandmarks.domain.model.Landmark
import com.example.smartlandmarks.domain.model.PendingLandmark
import com.example.smartlandmarks.domain.model.Visit
import com.example.smartlandmarks.utils.ImageUrlResolver

/**
 * Conversions between the three representations of the same data.
 *
 * All defaulting for missing server fields happens here, in one place, so a partial
 * response produces a usable row rather than an exception somewhere downstream.
 */

fun LandmarkDto.toEntityOrNull(cachedAt: Long): LandmarkEntity? {
    val safeId = id ?: return null
    val safeLat = lat ?: return null
    val safeLon = lon ?: return null
    return LandmarkEntity(
        id = safeId,
        title = title?.takeIf { it.isNotBlank() } ?: "Untitled landmark",
        latitude = safeLat,
        longitude = safeLon,
        imagePath = image,
        score = score ?: 0.0,
        visitCount = visitCount ?: 0,
        averageDistance = avgDistance ?: 0.0,
        // Absent is_active means the server already filtered it in, so treat it as active.
        isActive = (isActive ?: 1) != 0,
        cachedAt = cachedAt
    )
}

fun LandmarkEntity.toDomain(): Landmark = Landmark(
    id = id,
    title = title,
    latitude = latitude,
    longitude = longitude,
    imageUrl = ImageUrlResolver.resolve(imagePath),
    score = score,
    visitCount = visitCount,
    averageDistance = averageDistance,
    isActive = isActive
)

fun VisitEntity.toDomain(): Visit = Visit(
    localId = localId,
    landmarkId = landmarkId,
    landmarkTitle = landmarkTitle,
    userLatitude = userLatitude,
    userLongitude = userLongitude,
    jobId = jobId,
    status = status,
    distanceMetres = distanceMetres,
    createdAt = createdAt,
    errorMessage = errorMessage
)

fun PendingCreateEntity.toDomain(): PendingLandmark = PendingLandmark(
    localId = localId,
    title = title,
    latitude = latitude,
    longitude = longitude,
    imagePath = imagePath,
    createdAt = createdAt
)
