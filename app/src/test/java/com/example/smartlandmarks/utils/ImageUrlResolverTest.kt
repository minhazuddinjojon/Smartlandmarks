package com.example.smartlandmarks.utils

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The API returns relative image paths; Coil needs absolute URLs. BuildConfig is not
 * available to plain JVM tests for its value, so these assert on shape rather than the
 * exact host.
 */
class ImageUrlResolverTest {

    @Test
    fun `blank and null paths resolve to null`() {
        assertNull(ImageUrlResolver.resolve(null))
        assertNull(ImageUrlResolver.resolve(""))
        assertNull(ImageUrlResolver.resolve("   "))
    }

    @Test
    fun `absolute urls are passed through unchanged`() {
        val url = "https://example.com/uploads/a.jpg"
        assertTrue(ImageUrlResolver.resolve(url) == url)
    }

    @Test
    fun `relative paths gain a single separator`() {
        val resolved = ImageUrlResolver.resolve("uploads/1786430640_5629.jpg")
        assertTrue(resolved!!.endsWith("/uploads/1786430640_5629.jpg"))
        assertTrue(!resolved.contains("//uploads"))
    }
}
