package com.example.smartlandmarks.fake

import com.example.smartlandmarks.data.remote.ApiError
import com.example.smartlandmarks.data.remote.ApiResult
import com.example.smartlandmarks.domain.model.Landmark
import com.example.smartlandmarks.domain.model.Visit
import com.example.smartlandmarks.domain.repository.CreateOutcome
import com.example.smartlandmarks.domain.repository.LandmarkRepository
import com.example.smartlandmarks.domain.repository.SyncReport
import com.example.smartlandmarks.domain.repository.VisitOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * In-memory stand-in used by ViewModel tests. Because the UI depends on the repository
 * interface rather than the implementation, these tests need no network and no Room.
 */
class FakeLandmarkRepository : LandmarkRepository {

    val landmarks = MutableStateFlow<List<Landmark>>(emptyList())
    val visits = MutableStateFlow<List<Visit>>(emptyList())

    var createOutcome: CreateOutcome = CreateOutcome.Created(1)
    var visitOutcome: VisitOutcome = VisitOutcome.Accepted(1L)
    var refreshResult: ApiResult<Unit> = ApiResult.Success(Unit)

    var createCallCount = 0
        private set
    var lastCreatedTitle: String? = null
        private set

    override fun observeLandmarks(): Flow<List<Landmark>> = landmarks

    override fun observeLandmark(id: Int): Flow<Landmark?> =
        landmarks.map { list -> list.firstOrNull { it.id == id } }

    override fun observeVisits(): Flow<List<Visit>> = visits

    override fun observePendingWorkCount(): Flow<Int> = MutableStateFlow(0)

    override suspend fun hasCachedLandmarks(): Boolean = landmarks.value.isNotEmpty()

    override suspend fun refreshLandmarks(): ApiResult<Unit> = refreshResult

    override suspend fun recordVisit(
        landmarkId: Int,
        landmarkTitle: String,
        userLatitude: Double,
        userLongitude: Double
    ): VisitOutcome = visitOutcome

    override suspend fun createLandmark(
        title: String,
        latitude: Double,
        longitude: Double,
        image: File?
    ): CreateOutcome {
        createCallCount++
        lastCreatedTitle = title
        return createOutcome
    }

    override suspend fun deleteLandmark(id: Int): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun restoreLandmark(id: Int): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun runSyncPass(): SyncReport = SyncReport()
}
