package com.example.smartlandmarks.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Request body for `visit_landmark`. This endpoint takes raw JSON. */
data class VisitRequestDto(
    @SerializedName("landmark_id") val landmarkId: Int,
    @SerializedName("user_lat") val userLat: Double,
    @SerializedName("user_lon") val userLon: Double
)

/** Immediate response from `visit_landmark` — a job to poll, never a distance. */
data class VisitJobDto(
    @SerializedName("job_id") val jobId: Int?,
    @SerializedName("status") val status: String?
)

/** Response from `get_job_status`. `distance` is only present once status is `done`. */
data class JobStatusDto(
    @SerializedName("job_id") val jobId: Int?,
    @SerializedName("status") val status: String?,
    @SerializedName("distance") val distance: Double?,
    @SerializedName("error") val error: String?
)
