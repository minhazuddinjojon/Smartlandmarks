package com.example.smartlandmarks.data.remote

import com.example.smartlandmarks.utils.ApiConstants
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.example.smartlandmarks.data.remote.dto.ApiErrorDto
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Every failure the app can meaningfully react to differently.
 *
 * These map one-to-one onto the error-handling requirements in the lab document, so a
 * ViewModel can decide between "snackbar with retry" and "blocking dialog" by matching
 * on the type rather than by parsing message strings.
 */
sealed interface ApiError {
    /** No usable connection. Not really an error — the app queues and carries on. */
    data object Network : ApiError

    /** The request took too long. Usually worth retrying. */
    data object Timeout : ApiError

    /** HTTP 403 — key missing, mistyped, or from a previous semester. Unrecoverable. */
    data object InvalidKey : ApiError

    /** HTTP 404 — unknown landmark_id, or a job_id belonging to another key. */
    data class NotFound(val message: String?) : ApiError

    /** HTTP 400 — required fields missing. */
    data class BadRequest(val message: String?) : ApiError

    /** 5xx, or any other unsuccessful status. */
    data class Server(val code: Int, val message: String?) : ApiError

    /** Response arrived but could not be understood — empty or corrupted body. */
    data class Parse(val message: String?) : ApiError

    data class Unknown(val message: String?) : ApiError
}

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

inline fun <T> ApiResult<T>.onSuccess(block: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) block(data)
    return this
}

inline fun <T> ApiResult<T>.onFailure(block: (ApiError) -> Unit): ApiResult<T> {
    if (this is ApiResult.Failure) block(error)
    return this
}

fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.data

/**
 * Wraps a Retrofit call so no exception escapes into the repository.
 *
 * The lab requires the app never to crash on a bad response, so parse failures and
 * empty bodies are treated as ordinary results rather than thrown.
 */
suspend fun <T : Any> safeApiCall(block: suspend () -> Response<T>): ApiResult<T> = try {
    val response = block()
    if (response.isSuccessful) {
        val body = response.body()
        if (body != null) {
            ApiResult.Success(body)
        } else {
            ApiResult.Failure(ApiError.Parse("Server returned an empty response body"))
        }
    } else {
        ApiResult.Failure(response.toApiError())
    }
} catch (e: UnknownHostException) {
    ApiResult.Failure(ApiError.Network)
} catch (e: SocketTimeoutException) {
    ApiResult.Failure(ApiError.Timeout)
} catch (e: IOException) {
    ApiResult.Failure(ApiError.Network)
} catch (e: JsonSyntaxException) {
    ApiResult.Failure(ApiError.Parse(e.message))
} catch (e: IllegalStateException) {
    // Gson throws this for a malformed document that is not a syntax error.
    ApiResult.Failure(ApiError.Parse(e.message))
} catch (e: Exception) {
    ApiResult.Failure(ApiError.Unknown(e.message))
}

/**
 * Variant for calls whose body carries no information worth keeping — only whether the
 * request succeeded.
 */
suspend fun <T : Any> safeApiCallIgnoringBody(block: suspend () -> Response<T>): ApiResult<Unit> =
    when (val result = safeApiCall(block)) {
        is ApiResult.Success -> ApiResult.Success(Unit)
        is ApiResult.Failure ->
            if (result.error is ApiError.Parse) ApiResult.Success(Unit) else result
    }

private fun <T> Response<T>.toApiError(): ApiError {
    val rawBody = runCatching { errorBody()?.string() }.getOrNull()
    val serverMessage = runCatching {
        Gson().fromJson(rawBody, ApiErrorDto::class.java)?.error
    }.getOrNull()

    return when (code()) {
        ApiConstants.HTTP_FORBIDDEN -> ApiError.InvalidKey
        ApiConstants.HTTP_NOT_FOUND -> ApiError.NotFound(serverMessage)
        ApiConstants.HTTP_BAD_REQUEST -> ApiError.BadRequest(serverMessage)
        else -> ApiError.Server(code(), serverMessage ?: message())
    }
}
