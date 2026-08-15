package com.example.smartlandmarks.data.remote

import com.example.smartlandmarks.utils.ApiConstants
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Named

/**
 * Appends the student key to every outgoing request.
 *
 * Doing this centrally means no call site can forget it — a missing key turns every
 * endpoint into a 403, which is a tedious bug to chase from the UI layer.
 */
class AuthInterceptor @Inject constructor(
    @Named("apiKey") private val apiKey: String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url.newBuilder()
            .addQueryParameter(ApiConstants.PARAM_KEY, apiKey)
            .build()
        return chain.proceed(original.newBuilder().url(url).build())
    }
}
