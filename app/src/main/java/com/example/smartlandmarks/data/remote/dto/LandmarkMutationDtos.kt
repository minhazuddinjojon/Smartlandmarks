package com.example.smartlandmarks.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Response from `create_landmark`. The server returns the id as a JSON string. */
data class CreateLandmarkDto(
    @SerializedName("id") val id: String?
)

/** Response from `delete_landmark` / `restore_landmark`. */
data class StatusDto(
    @SerializedName("status") val status: String?
)

/** Error envelope, e.g. `{"error": "invalid_or_expired_key"}`. */
data class ApiErrorDto(
    @SerializedName("error") val error: String?
)
