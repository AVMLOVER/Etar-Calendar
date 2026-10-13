/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Saves a countdown card as a PNG.
 *
 * Two paths, because MediaStore.Downloads only exists from API 29 while this app still supports 24:
 * on 29 and up the image is inserted through MediaStore, and below that it is written straight to the
 * public Downloads directory, which is why WRITE_EXTERNAL_STORAGE is declared with a maxSdkVersion.
 */
package com.android.calendar.countdown

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object PngExporter {

    /** Thrown when the image could not be saved, so the screen can say so rather than fail silently. */
    class Failed(message: String, cause: Throwable? = null) : Exception(message, cause)

    /**
     * Writes [bitmap] into Downloads and returns a content URI for it, or null when the platform
     * refused to hand one back.
     */
    fun saveToDownloads(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val fileName = sanitize(displayName) + ".png"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, bitmap, fileName)
        } else {
            saveToPublicDownloads(bitmap, fileName)
        }
    }

    private fun saveViaMediaStore(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            // Left pending until the bytes are written, so no gallery or file manager sees a
            // half-written image.
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw Failed("Downloads is not available")

        try {
            resolver.openOutputStream(uri).use { stream ->
                write(bitmap, stream)
            }
        } catch (t: Throwable) {
            // Remove the placeholder so a failed export does not leave an empty file behind.
            resolver.delete(uri, null, null)
            throw Failed("could not write the image", t)
        }

        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
            null,
            null,
        )
        return uri
    }

    private fun saveToPublicDownloads(bitmap: Bitmap, fileName: String): Uri {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists() && !dir.mkdirs()) {
            throw Failed("Downloads is not available")
        }
        val file = File(dir, fileName)
        try {
            FileOutputStream(file).use { stream -> write(bitmap, stream) }
        } catch (t: Throwable) {
            throw Failed("could not write the image", t)
        }
        return Uri.fromFile(file)
    }

    private fun write(bitmap: Bitmap, stream: OutputStream?) {
        if (stream == null) {
            throw Failed("no output stream")
        }
        // PNG ignores the quality argument, but it cannot be omitted.
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
            throw Failed("the image could not be encoded")
        }
        stream.flush()
    }

    /** Keeps the name usable as a file name: the countdown's own name can hold anything. */
    private fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.ifEmpty { "countdown" }.take(60)
    }
}
