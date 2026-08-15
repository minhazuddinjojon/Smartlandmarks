package com.example.smartlandmarks.utils

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream

/**
 * Copies picked images into app-private cache storage.
 *
 * A `content://` URI from the photo picker is only readable while the permission grant
 * lasts. A landmark queued while offline may not upload until hours later, possibly
 * after a reboot, so the bytes have to be owned by the app rather than borrowed.
 */
object FileUtils {

    private const val CACHE_DIR = "pending_images"

    fun copyToCache(context: Context, uri: Uri): File? = runCatching {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val extension = resolveExtension(context, uri)
        val target = File(dir, "landmark_${System.currentTimeMillis()}.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: return null
        target
    }.getOrNull()

    fun sizeOf(file: File): Long = if (file.exists()) file.length() else 0L

    fun isWithinSizeLimit(file: File): Boolean = sizeOf(file) <= ApiConstants.MAX_IMAGE_BYTES

    /**
     * Derived from the file extension alone, so background workers can build a
     * multipart body without holding a Context.
     */
    fun mimeTypeOf(file: File): String {
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "image/jpeg"
    }

    fun deleteQuietly(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    private fun resolveExtension(context: Context, uri: Uri): String {
        val type = context.contentResolver.getType(uri)
        val fromMime = type?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return when (fromMime) {
            null, "" -> "jpg"
            else -> fromMime
        }
    }
}
