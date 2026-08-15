package com.example.smartlandmarks.domain.repository

import com.example.smartlandmarks.data.remote.ApiResult
import com.example.smartlandmarks.domain.model.Landmark
import com.example.smartlandmarks.domain.model.Visit
import kotlinx.coroutines.flow.Flow
import java.io.File

/** Outcome of recording a visit, from the caller's point of view. */
sealed interface VisitOutcome {
    /** Accepted by the server; a job is now being polled in the background. */
    data class Accepted(val localId: Long) : VisitOutcome

    /** No connectivity — stored locally and will be posted when the network returns. */
    data class Queued(val localId: Long) : VisitOutcome

    /** Rejected outright (bad landmark id, invalid key). Nothing is retried. */
    data class Rejected(val error: com.example.smartlandmarks.data.remote.ApiError) : VisitOutcome
}

/** Outcome of adding a landmark. */
sealed interface CreateOutcome {
    data class Created(val serverId: Int?) : CreateOutcome
    data object Queued : CreateOutcome
    data class Rejected(val error: com.example.smartlandmarks.data.remote.ApiError) : CreateOutcome
}

/** What a single sync pass achieved, so the worker can decide retry vs success. */
data class SyncReport(
    val postedVisits: Int = 0,
    val resolvedJobs: Int = 0,
    val uploadedLandmarks: Int = 0,
    val stillUnresolved: Int = 0,
    val hadTransientFailure: Boolean = false,
    val invalidKey: Boolean = false
)

/**
 * The app's single entry point to data.
 *
 * Read operations return Flows backed by Room, never by the network, so every screen
 * keeps working offline for free.
 */
interface LandmarkRepository {

    fun observeLandmarks(): Flow<List<Landmark>>

    fun observeLandmark(id: Int): Flow<Landmark?>

    fun observeVisits(): Flow<List<Visit>>

    fun observePendingWorkCount(): Flow<Int>

    suspend fun hasCachedLandmarks(): Boolean

    /** Fetches from the server and reconciles the cache. Safe to call when offline. */
    suspend fun refreshLandmarks(): ApiResult<Unit>

    suspend fun recordVisit(
        landmarkId: Int,
        landmarkTitle: String,
        userLatitude: Double,
        userLongitude: Double
    ): VisitOutcome

    suspend fun createLandmark(
        title: String,
        latitude: Double,
        longitude: Double,
        image: File?
    ): CreateOutcome

    suspend fun deleteLandmark(id: Int): ApiResult<Unit>

    suspend fun restoreLandmark(id: Int): ApiResult<Unit>

    /** One full background pass: drain the queue, poll jobs, upload pending landmarks. */
    suspend fun runSyncPass(): SyncReport
}
