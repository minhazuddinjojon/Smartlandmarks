package com.example.smartlandmarks.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Every field is nullable on purpose.
 *
 * Gson constructs DTOs by reflection and bypasses Kotlin constructors, so a missing or
 * null JSON field would silently produce a null in a non-null Kotlin property and throw
 * at some unrelated later point. Declaring them nullable makes the mapper the single
 * place where defaults are applied, and a malformed response degrades instead of
 * crashing — which the lab explicitly asks for.
 */
data class LandmarkDto(
    @SerializedName("id") val id: Int?,
    @SerializedName("title") val title: String?,
    @SerializedName("lat") val lat: Double?,
    @SerializedName("lon") val lon: Double?,
    @SerializedName("image") val image: String?,
    @SerializedName("is_active") val isActive: Int?,
    @SerializedName("visit_count") val visitCount: Int?,
    @SerializedName("avg_distance") val avgDistance: Double?,
    @SerializedName("score") val score: Double?
)
