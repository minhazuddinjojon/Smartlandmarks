package com.example.smartlandmarks.utils

import com.example.smartlandmarks.BuildConfig

/**
 * The API returns image paths relative to the deployment root (e.g.
 * `uploads/1786430640_5629.jpg`). Coil needs an absolute URL.
 */
object ImageUrlResolver {

    fun resolve(rawPath: String?): String? {
        val path = rawPath?.trim().orEmpty()
        if (path.isEmpty()) return null
        if (path.startsWith("http://", ignoreCase = true) ||
            path.startsWith("https://", ignoreCase = true)
        ) {
            return path
        }
        return BuildConfig.BASE_URL.trimEnd('/') + "/" + path.trimStart('/')
    }
}
