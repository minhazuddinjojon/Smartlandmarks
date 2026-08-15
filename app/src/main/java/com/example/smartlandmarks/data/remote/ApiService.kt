package com.example.smartlandmarks.data.remote

import com.example.smartlandmarks.data.remote.dto.CreateLandmarkDto
import com.example.smartlandmarks.data.remote.dto.JobStatusDto
import com.example.smartlandmarks.data.remote.dto.LandmarkDto
import com.example.smartlandmarks.data.remote.dto.StatusDto
import com.example.smartlandmarks.data.remote.dto.VisitJobDto
import com.example.smartlandmarks.data.remote.dto.VisitRequestDto
import com.example.smartlandmarks.utils.ApiConstants
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * The whole CSE 489 API surface.
 *
 * Note the three different content types below — this API is not internally consistent
 * and mixing them up is the most common way to fail it:
 *   - create_landmark  -> multipart/form-data (PHP reads $_FILES; JSON leaves it empty)
 *   - delete / restore -> application/x-www-form-urlencoded
 *   - visit_landmark   -> application/json
 *
 * The `key` query parameter is injected by [AuthInterceptor], so no method declares it.
 */
interface ApiService {

    @GET(ApiConstants.PATH)
    suspend fun getLandmarks(
        @Query(ApiConstants.PARAM_ACTION) action: String
    ): Response<List<LandmarkDto>>

    @Multipart
    @POST(ApiConstants.PATH)
    suspend fun createLandmark(
        @Query(ApiConstants.PARAM_ACTION) action: String,
        @Part("title") title: RequestBody,
        @Part("lat") lat: RequestBody,
        @Part("lon") lon: RequestBody,
        @Part image: MultipartBody.Part?
    ): Response<CreateLandmarkDto>

    @FormUrlEncoded
    @POST(ApiConstants.PATH)
    suspend fun deleteLandmark(
        @Query(ApiConstants.PARAM_ACTION) action: String,
        @Field("id") id: Int
    ): Response<StatusDto>

    @FormUrlEncoded
    @POST(ApiConstants.PATH)
    suspend fun restoreLandmark(
        @Query(ApiConstants.PARAM_ACTION) action: String,
        @Field("id") id: Int
    ): Response<StatusDto>

    @POST(ApiConstants.PATH)
    suspend fun visitLandmark(
        @Query(ApiConstants.PARAM_ACTION) action: String,
        @retrofit2.http.Body body: VisitRequestDto
    ): Response<VisitJobDto>

    @GET(ApiConstants.PATH)
    suspend fun getJobStatus(
        @Query(ApiConstants.PARAM_ACTION) action: String,
        @Query(ApiConstants.PARAM_JOB_ID) jobId: Int
    ): Response<JobStatusDto>
}
