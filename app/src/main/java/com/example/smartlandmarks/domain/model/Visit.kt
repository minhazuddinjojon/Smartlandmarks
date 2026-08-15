package com.example.smartlandmarks.domain.model

/**
 * The lifecycle of a visit, from tapping the button to the server resolving the job.
 *
 * QUEUED exists because a visit made offline is a legitimate state, not an error. It is
 * held locally until connectivity returns, at which point the sync worker posts it.
 */
enum class VisitStatus {
    /** Recorded locally; not yet sent to the server. */
    QUEUED,

    /** Sent; the server returned a job_id and is processing it. */
    PENDING,

    /** The server resolved the job and returned a distance. */
    DONE,

    /** The server reported failure, or the app gave up after repeated attempts. */
    FAILED
}

data class Visit(
    val localId: Long,
    val landmarkId: Int,
    val landmarkTitle: String,
    val userLatitude: Double,
    val userLongitude: Double,
    val jobId: Int?,
    val status: VisitStatus,
    val distanceMetres: Double?,
    val createdAt: Long,
    val errorMessage: String?
)
