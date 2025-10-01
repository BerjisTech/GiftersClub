package club.gifters.giftersclub.util

import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.net.URLConnection
import java.util.Locale

object MimeUtils {

    private const val DEFAULT_MIME = "application/octet-stream"

    fun resolveMimeType(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val initial = resolver.getType(uri)?.takeIf { !it.isNullOrBlank() }
        val sanitized = initial?.takeUnless { it.equals(DEFAULT_MIME, ignoreCase = true) }
        if (!sanitized.isNullOrBlank()) return sanitized

        val ext = inferExtension(context, uri)
        val fromExt = ext?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.lowercase(Locale.US)) }
        if (!fromExt.isNullOrBlank()) return fromExt

        val name = inferDisplayName(context, uri)
        val guess = name?.let { URLConnection.guessContentTypeFromName(it) }
        if (!guess.isNullOrBlank() && !guess.equals(DEFAULT_MIME, true)) {
            return guess
        }

        val fromMetadata = detectViaMetadata(context, uri)
        if (!fromMetadata.isNullOrBlank()) return fromMetadata

        return DEFAULT_MIME
    }

    fun canonicalExtensionForMime(mime: String): String {
        val cleaned = mime.substringBefore(';').lowercase(Locale.US)
        return when (cleaned) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            "video/mp4", "video/avc" -> "mp4"
            "video/mpeg" -> "mpg"
            "video/quicktime" -> "mov"
            "video/x-matroska", "video/webm" -> "webm"
            "audio/mpeg" -> "mp3"
            "audio/mp4" -> "m4a"
            else -> cleaned.substringAfter('/', "bin")
        }
    }

    private fun inferExtension(context: Context, uri: Uri): String? {
        when (uri.scheme?.lowercase(Locale.US)) {
            "file" -> {
                val path = uri.path ?: return null
                return File(path).extension.takeIf { it.isNotBlank() }
            }
            "content" -> inferDisplayName(context, uri)?.substringAfterLast('.', missingDelimiterValue = "")?.takeIf { it.isNotBlank() }
        }
        return uri.path?.substringAfterLast('.', missingDelimiterValue = "")?.takeIf { it.isNotBlank() }
    }

    private fun inferDisplayName(context: Context, uri: Uri): String? {
        if (!"content".equals(uri.scheme, ignoreCase = true)) {
            return uri.lastPathSegment
        }
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }

    private fun detectViaMetadata(context: Context, uri: Uri): String? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
                ?.equals("yes", ignoreCase = true) == true
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                ?.equals("yes", ignoreCase = true) == true
            val metaMime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            retriever.release()
            when {
                !metaMime.isNullOrBlank() -> metaMime
                hasVideo -> "video/mp4"
                hasAudio -> "audio/mp4"
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
