package com.example.smartlandmarks.data.repository

import com.example.smartlandmarks.data.local.dao.LandmarkDao
import com.example.smartlandmarks.data.local.dao.PendingCreateDao
import com.example.smartlandmarks.data.local.dao.VisitDao
import com.example.smartlandmarks.data.local.entity.PendingCreateEntity
import com.example.smartlandmarks.data.local.entity.VisitEntity
import com.example.smartlandmarks.data.mapper.toDomain
import com.example.smartlandmarks.data.mapper.toEntityOrNull
import com.example.smartlandmarks.data.remote.ApiError
import com.example.smartlandmarks.data.remote.ApiResult
import com.example.smartlandmarks.data.remote.ApiService
import com.example.smartlandmarks.data.remote.dto.VisitRequestDto
import com.example.smartlandmarks.data.remote.safeApiCall
import com.example.smartlandmarks.data.remote.safeApiCallIgnoringBody
import com.example.smartlandmarks.di.IoDispatcher
import com.example.smartlandmarks.domain.model.Landmark
import com.example.smartlandmarks.domain.model.Visit
import com.example.smartlandmarks.domain.model.VisitStatus
import com.example.smartlandmarks.domain.repository.CreateOutcome
import com.example.smartlandmarks.domain.repository.LandmarkRepository
import com.example.smartlandmarks.domain.repository.SyncReport
import com.example.smartlandmarks.domain.repository.VisitOutcome
import com.example.smartlandmarks.services.NetworkMonitor
import com.example.smartlandmarks.utils.ApiConstants
import com.example.smartlandmarks.utils.FileUtils
import com.example.smartlandmarks.utils.SyncConstants
import com.example.smartlandmarks.workers.WorkScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LandmarkRepositoryImpl @Inject constructor(
    private val api: ApiService,
    private val landmarkDao: LandmarkDao,
    private val visitDao: VisitDao,
    private val pendingCreateDao: PendingCreateDao,
    private val networkMonitor: NetworkMonitor,
    private val workScheduler: WorkScheduler,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : LandmarkRepository {

    // ------------------------------------------------------------------ reads

    override fun observeLandmarks(): Flow<List<Landmark>> =
        landmarkDao.observeActive().map { rows -> rows.map { it.toDomain() } }

    override fun observeLandmark(id: Int): Flow<Landmark?> =
        landmarkDao.observeById(id).map { it?.toDomain() }

    override fun observeVisits(): Flow<List<Visit>> =
        visitDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observePendingWorkCount(): Flow<Int> =
        combine(
            visitDao.observeUnresolvedCount(),
            pendingCreateDao.observeCount()
        ) { visits, creates -> visits + creates }

    override suspend fun hasCachedLandmarks(): Boolean = withContext(ioDispatcher) {
        landmarkDao.countActive() > 0
    }

    // ------------------------------------------------------------- refreshing

    override suspend fun refreshLandmarks(): ApiResult<Unit> = withContext(ioDispatcher) {
        when (val result = safeApiCall {
            api.getLandmarks(ApiConstants.ACTION_GET_LANDMARKS)
        }) {
            is ApiResult.Success -> {
                val now = System.currentTimeMillis()
                // Rows the server could not describe well enough to place on a map are
                // dropped rather than stored half-formed.
                val entities = result.data.mapNotNull { it.toEntityOrNull(now) }
                landmarkDao.replaceAll(entities)
                ApiResult.Success(Unit)
            }

            is ApiResult.Failure -> result
        }
    }

    // ----------------------------------------------------------------- visits

    /**
     * Records a visit, then either posts it immediately or leaves it queued.
     *
     * The local row is written *before* the network attempt on purpose: if the process
     * dies mid-request the visit is not lost, and the user sees it in Activity right
     * away instead of after a round trip.
     */
    override suspend fun recordVisit(
        landmarkId: Int,
        landmarkTitle: String,
        userLatitude: Double,
        userLongitude: Double
    ): VisitOutcome = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val localId = visitDao.insert(
            VisitEntity(
                landmarkId = landmarkId,
                landmarkTitle = landmarkTitle,
                userLatitude = userLatitude,
                userLongitude = userLongitude,
                jobId = null,
                status = VisitStatus.QUEUED,
                distanceMetres = null,
                createdAt = now,
                updatedAt = now
            )
        )

        if (!networkMonitor.currentlyOnline()) {
            workScheduler.enqueueSyncNow()
            return@withContext VisitOutcome.Queued(localId)
        }

        when (val posted = postVisit(localId, landmarkId, userLatitude, userLongitude, attempt = 1)) {
            is PostResult.Accepted -> {
                workScheduler.enqueueSyncNow()
                VisitOutcome.Accepted(localId)
            }

            is PostResult.Transient -> {
                workScheduler.enqueueSyncNow()
                VisitOutcome.Queued(localId)
            }

            is PostResult.Fatal -> {
                visitDao.markAttempt(
                    localId = localId,
                    status = VisitStatus.FAILED,
                    attempts = 1,
                    message = posted.error.describe(),
                    updatedAt = System.currentTimeMillis()
                )
                VisitOutcome.Rejected(posted.error)
            }
        }
    }

    // ------------------------------------------------------- create / delete

    override suspend fun createLandmark(
        title: String,
        latitude: Double,
        longitude: Double,
        image: File?
    ): CreateOutcome = withContext(ioDispatcher) {
        if (!networkMonitor.currentlyOnline()) {
            queueCreate(title, latitude, longitude, image)
            workScheduler.enqueueSyncNow()
            return@withContext CreateOutcome.Queued
        }

        when (val result = uploadLandmark(title, latitude, longitude, image)) {
            is ApiResult.Success -> {
                refreshLandmarks()
                FileUtils.deleteQuietly(image?.absolutePath)
                CreateOutcome.Created(result.data)
            }

            is ApiResult.Failure -> {
                if (result.error.isTransient()) {
                    queueCreate(title, latitude, longitude, image)
                    workScheduler.enqueueSyncNow()
                    CreateOutcome.Queued
                } else {
                    CreateOutcome.Rejected(result.error)
                }
            }
        }
    }

    override suspend fun deleteLandmark(id: Int): ApiResult<Unit> = withContext(ioDispatcher) {
        val result = safeApiCallIgnoringBody {
            api.deleteLandmark(ApiConstants.ACTION_DELETE_LANDMARK, id)
        }
        if (result is ApiResult.Success) {
            // Hide it locally at once so the list responds instantly, then reconcile.
            landmarkDao.setActive(id, false)
            refreshLandmarks()
        }
        result
    }

    override suspend fun restoreLandmark(id: Int): ApiResult<Unit> = withContext(ioDispatcher) {
        val result = safeApiCallIgnoringBody {
            api.restoreLandmark(ApiConstants.ACTION_RESTORE_LANDMARK, id)
        }
        if (result is ApiResult.Success) {
            landmarkDao.setActive(id, true)
            refreshLandmarks()
        }
        result
    }

    // ------------------------------------------------------------------ sync

    /**
     * One complete background pass, in dependency order:
     *   1. post queued visits (offline queue drain)
     *   2. poll every job the server has accepted
     *   3. upload landmarks created offline
     *
     * Posting before polling matters — a visit posted in step 1 becomes pollable in
     * step 2 of the same pass, so a visit made offline can resolve fully the moment
     * connectivity returns rather than needing two separate runs.
     */
    override suspend fun runSyncPass(): SyncReport = withContext(ioDispatcher) {
        var posted = 0
        var resolved = 0
        var uploaded = 0
        var transient = false
        var invalidKey = false

        // 1. Drain the offline visit queue.
        for (visit in visitDao.findQueued()) {
            val attempt = visit.attemptCount + 1
            when (val result = postVisit(
                visit.localId, visit.landmarkId, visit.userLatitude, visit.userLongitude, attempt
            )) {
                is PostResult.Accepted -> posted++
                is PostResult.Transient -> {
                    transient = true
                    if (attempt >= SyncConstants.MAX_UPLOAD_ATTEMPTS) {
                        visitDao.markAttempt(
                            visit.localId, VisitStatus.FAILED, attempt,
                            "Gave up after $attempt attempts", System.currentTimeMillis()
                        )
                    }
                }

                is PostResult.Fatal -> {
                    if (result.error is ApiError.InvalidKey) invalidKey = true
                    visitDao.markAttempt(
                        visit.localId, VisitStatus.FAILED, attempt,
                        result.error.describe(), System.currentTimeMillis()
                    )
                }
            }
        }

        // 2. Poll jobs the server has accepted.
        for (visit in visitDao.findPending()) {
            val jobId = visit.jobId ?: continue
            val attempt = visit.attemptCount + 1
            when (val result = safeApiCall {
                api.getJobStatus(ApiConstants.ACTION_GET_JOB_STATUS, jobId)
            }) {
                is ApiResult.Success -> {
                    val status = result.data.status?.lowercase()
                    val now = System.currentTimeMillis()
                    when (status) {
                        ApiConstants.STATUS_DONE -> {
                            visitDao.markResolved(
                                visit.localId, VisitStatus.DONE, result.data.distance, now
                            )
                            resolved++
                        }

                        ApiConstants.STATUS_FAILED -> {
                            visitDao.markAttempt(
                                visit.localId, VisitStatus.FAILED, attempt,
                                result.data.error ?: "The server could not process this visit",
                                now
                            )
                        }

                        else -> {
                            // Still pending. Keep polling unless it has clearly stalled.
                            if (attempt >= SyncConstants.MAX_POLL_ATTEMPTS) {
                                visitDao.markAttempt(
                                    visit.localId, VisitStatus.FAILED, attempt,
                                    "The job did not complete in time", now
                                )
                            } else {
                                visitDao.markAttempt(
                                    visit.localId, VisitStatus.PENDING, attempt, null, now
                                )
                            }
                        }
                    }
                }

                is ApiResult.Failure -> when (val error = result.error) {
                    is ApiError.NotFound -> visitDao.markAttempt(
                        visit.localId, VisitStatus.FAILED, attempt,
                        "This visit job no longer exists on the server",
                        System.currentTimeMillis()
                    )

                    is ApiError.InvalidKey -> {
                        invalidKey = true
                        visitDao.markAttempt(
                            visit.localId, VisitStatus.FAILED, attempt,
                            error.describe(), System.currentTimeMillis()
                        )
                    }

                    else -> transient = true
                }
            }
        }

        // 3. Upload landmarks created while offline.
        for (pending in pendingCreateDao.findAll()) {
            val attempt = pending.attemptCount + 1
            val file = pending.imagePath?.let { File(it) }?.takeIf { it.exists() }
            when (val result = uploadLandmark(pending.title, pending.latitude, pending.longitude, file)) {
                is ApiResult.Success -> {
                    pendingCreateDao.delete(pending.localId)
                    FileUtils.deleteQuietly(pending.imagePath)
                    uploaded++
                }

                is ApiResult.Failure -> {
                    if (result.error is ApiError.InvalidKey) invalidKey = true
                    if (result.error.isTransient() && attempt < SyncConstants.MAX_UPLOAD_ATTEMPTS) {
                        transient = true
                        pendingCreateDao.updateAttempts(pending.localId, attempt)
                    } else {
                        // Permanently rejected, or out of attempts: stop retrying forever.
                        pendingCreateDao.delete(pending.localId)
                        FileUtils.deleteQuietly(pending.imagePath)
                    }
                }
            }
        }

        if (posted > 0 || resolved > 0 || uploaded > 0) {
            refreshLandmarks()
        }

        SyncReport(
            postedVisits = posted,
            resolvedJobs = resolved,
            uploadedLandmarks = uploaded,
            stillUnresolved = visitDao.countUnresolved() + pendingCreateDao.count(),
            hadTransientFailure = transient,
            invalidKey = invalidKey
        )
    }

    // ---------------------------------------------------------------- helpers

    private sealed interface PostResult {
        data object Accepted : PostResult
        data object Transient : PostResult
        data class Fatal(val error: ApiError) : PostResult
    }

    private suspend fun postVisit(
        localId: Long,
        landmarkId: Int,
        latitude: Double,
        longitude: Double,
        attempt: Int
    ): PostResult {
        val result = safeApiCall {
            api.visitLandmark(
                ApiConstants.ACTION_VISIT_LANDMARK,
                VisitRequestDto(landmarkId, latitude, longitude)
            )
        }
        val now = System.currentTimeMillis()

        return when (result) {
            is ApiResult.Success -> {
                val jobId = result.data.jobId
                if (jobId == null) {
                    // Accepted but unusable — without a job_id there is nothing to poll.
                    visitDao.markAttempt(
                        localId, VisitStatus.FAILED, attempt,
                        "Server did not return a job id", now
                    )
                    PostResult.Fatal(ApiError.Parse("Missing job_id"))
                } else {
                    visitDao.markPosted(localId, jobId, VisitStatus.PENDING, attempt, now)
                    PostResult.Accepted
                }
            }

            is ApiResult.Failure -> {
                if (result.error.isTransient()) {
                    visitDao.markAttempt(localId, VisitStatus.QUEUED, attempt, null, now)
                    PostResult.Transient
                } else {
                    PostResult.Fatal(result.error)
                }
            }
        }
    }

    private suspend fun uploadLandmark(
        title: String,
        latitude: Double,
        longitude: Double,
        image: File?
    ): ApiResult<Int?> {
        val plain = "text/plain".toMediaTypeOrNull()
        val imagePart = image?.takeIf { it.exists() }?.let { file ->
            val mime = FileUtils.mimeTypeOf(file).toMediaTypeOrNull()
            MultipartBody.Part.createFormData("image", file.name, file.asRequestBody(mime))
        }

        return when (
            val result = safeApiCall {
                api.createLandmark(
                    ApiConstants.ACTION_CREATE_LANDMARK,
                    title.toPlainBody(plain),
                    latitude.toString().toPlainBody(plain),
                    longitude.toString().toPlainBody(plain),
                    imagePart
                )
            }
        ) {
            is ApiResult.Success -> ApiResult.Success(result.data.id?.toIntOrNull())
            is ApiResult.Failure -> result
        }
    }

    private suspend fun queueCreate(
        title: String,
        latitude: Double,
        longitude: Double,
        image: File?
    ) {
        pendingCreateDao.insert(
            PendingCreateEntity(
                title = title,
                latitude = latitude,
                longitude = longitude,
                imagePath = image?.absolutePath,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private fun String.toPlainBody(type: okhttp3.MediaType?): RequestBody =
        toRequestBody(type)

    private fun ApiError.isTransient(): Boolean = when (this) {
        ApiError.Network, ApiError.Timeout -> true
        is ApiError.Server -> code >= 500
        else -> false
    }

    private fun ApiError.describe(): String = when (this) {
        ApiError.Network -> "No internet connection"
        ApiError.Timeout -> "The server took too long to respond"
        ApiError.InvalidKey -> "Your API key is invalid or has expired"
        is ApiError.NotFound -> message ?: "Not found on the server"
        is ApiError.BadRequest -> message ?: "The request was rejected"
        is ApiError.Server -> message ?: "Server error ($code)"
        is ApiError.Parse -> message ?: "The server response could not be read"
        is ApiError.Unknown -> message ?: "Something went wrong"
    }
}
