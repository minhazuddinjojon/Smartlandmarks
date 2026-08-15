package com.example.smartlandmarks.data

import com.example.smartlandmarks.data.remote.ApiError
import com.example.smartlandmarks.data.remote.ApiResult
import com.example.smartlandmarks.data.remote.safeApiCall
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * These cover the exact status codes the CSE 489 API documents, because the UI branches
 * on them: 403 is fatal, 404 kills a single job, 400 is a bad request, and IO failures
 * mean "queue it and try later".
 */
class ApiResultTest {

    private val json = "application/json".toMediaType()

    @Test
    fun `successful response with a body is a Success`() = runTest {
        val result = safeApiCall { Response.success("payload") }
        assertTrue(result is ApiResult.Success)
        assertEquals("payload", (result as ApiResult.Success).data)
    }

    @Test
    fun `403 maps to InvalidKey`() = runTest {
        val result = safeApiCall<String> {
            Response.error(403, """{"error":"invalid_or_expired_key"}""".toResponseBody(json))
        }
        assertEquals(ApiError.InvalidKey, (result as ApiResult.Failure).error)
    }

    @Test
    fun `404 maps to NotFound and keeps the server message`() = runTest {
        val result = safeApiCall<String> {
            Response.error(404, """{"error":"job_not_found"}""".toResponseBody(json))
        }
        val error = (result as ApiResult.Failure).error
        assertTrue(error is ApiError.NotFound)
        assertEquals("job_not_found", (error as ApiError.NotFound).message)
    }

    @Test
    fun `400 maps to BadRequest`() = runTest {
        val result = safeApiCall<String> {
            Response.error(400, """{"error":"missing_fields"}""".toResponseBody(json))
        }
        assertTrue((result as ApiResult.Failure).error is ApiError.BadRequest)
    }

    @Test
    fun `500 maps to Server with its code preserved`() = runTest {
        val result = safeApiCall<String> {
            Response.error(500, "boom".toResponseBody(json))
        }
        val error = (result as ApiResult.Failure).error
        assertTrue(error is ApiError.Server)
        assertEquals(500, (error as ApiError.Server).code)
    }

    @Test
    fun `IO failure maps to Network rather than throwing`() = runTest {
        val result = safeApiCall<String> { throw IOException("no route to host") }
        assertEquals(ApiError.Network, (result as ApiResult.Failure).error)
    }

    @Test
    fun `timeout is distinguished from a generic network failure`() = runTest {
        val result = safeApiCall<String> { throw SocketTimeoutException() }
        assertEquals(ApiError.Timeout, (result as ApiResult.Failure).error)
    }

    /** An empty body must not crash the app — the lab requires graceful degradation. */
    @Test
    fun `empty success body maps to Parse instead of crashing`() = runTest {
        val result = safeApiCall<String> { Response.success(null) }
        assertTrue((result as ApiResult.Failure).error is ApiError.Parse)
    }
}
