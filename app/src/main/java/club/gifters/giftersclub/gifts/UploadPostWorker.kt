package club.gifters.giftersclub.gifts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import android.content.pm.ServiceInfo
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import club.gifters.giftersclub.R
import club.gifters.giftersclub.network.PresignRequest
import club.gifters.giftersclub.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

class UploadPostWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_POST_ID = "post_id"
        const val KEY_URIS = "uris"
        const val KEY_TYPES = "types" // optional pre-resolved MIME types
        const val CHANNEL_ID = "post_uploads"

        fun buildInput(postId: String, uris: List<String>, types: List<String>? = null): Data {
            val b = Data.Builder()
                .putString(KEY_POST_ID, postId)
                .putStringArray(KEY_URIS, uris.toTypedArray())
            types?.let { b.putStringArray(KEY_TYPES, it.toTypedArray()) }
            return b.build()
        }
    }

    private val notifId: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

    override suspend fun getForegroundInfo(): ForegroundInfo {
        ensureChannel()
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText("Uploading post")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= 34) {
            ForegroundInfo(notifId, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notifId, notif)
        }
    }

    override suspend fun doWork(): Result {
        // Android 14+ restricts starting foreground services from background. Best-effort start;
        // if not allowed, continue in background with regular notifications.
        try {
            if (android.os.Build.VERSION.SDK_INT < 34) {
                setForeground(getForegroundInfo())
            }
        } catch (_: Exception) {
            // Ignore and continue without foreground service
        }
        val postId = inputData.getString(KEY_POST_ID) ?: return Result.failure()
        val uriStrings = inputData.getStringArray(KEY_URIS)?.toList().orEmpty()
        if (uriStrings.isEmpty()) return Result.success()
        val typeStrings = inputData.getStringArray(KEY_TYPES)?.toList()
        val total = uriStrings.size
        var done = 0
        try {
            uriStrings.forEachIndexed { index, us ->
                val uri = Uri.parse(us)
                val resolvedType = typeStrings?.getOrNull(index)
                    ?: applicationContext.contentResolver.getType(uri)
                    ?: "application/octet-stream"
                val isVideo = resolvedType.startsWith("video/") || us.endsWith(".mp4")
                val ext = resolvedType.substringAfterLast('/', "bin")
                val ts = System.currentTimeMillis()
                val filename = "${postId}-$ts-$index.$ext"
                val body: RequestBody = if (isVideo) {
                    // Many cloud providers (S3/GCS) require a fixed Content-Length for pre-signed PUTs.
                    // Ensure we upload with a known length by copying to a temp file when necessary.
                    val srcFile: java.io.File = when (uri.scheme?.lowercase()) {
                        "file" -> java.io.File(uri.path!!)
                        else -> {
                            val tmp = java.io.File(applicationContext.cacheDir, "UP_${System.currentTimeMillis()}_${index}.bin")
                            withContext(Dispatchers.IO) {
                                applicationContext.contentResolver.openInputStream(uri)?.use { ins ->
                                    java.io.FileOutputStream(tmp).use { outs -> ins.copyTo(outs) }
                                } ?: throw Exception("Failed to open video stream")
                            }
                            tmp
                        }
                    }
                    srcFile.asRequestBody(resolvedType.toMediaTypeOrNull())
                } else {
                    val bytes = withContext(Dispatchers.IO) {
                        applicationContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    } ?: throw Exception("Failed to read media data")
                    bytes.toRequestBody(resolvedType.toMediaTypeOrNull())
                }

                val presignResp = RetrofitClient.functionsApi.uploadMedia(
                    PresignRequest(fileName = filename, fileType = resolvedType, bucket = "post", overwrite = false)
                )
                if (!presignResp.isSuccessful) throw HttpException(presignResp)
                val presignData = presignResp.body()!!
                val putReq = Request.Builder().url(presignData.uploadUrl).put(body).build()
                val putResp = withContext(Dispatchers.IO) { RetrofitClient.awsClient.newCall(putReq).execute() }
                val ok = putResp.isSuccessful
                val code = putResp.code
                val errBody = try { putResp.body?.string() } catch (_: Exception) { null }
                putResp.close()
                if (!ok) throw Exception("Upload failed: $code ${errBody ?: ""}")
                val publicUrl = presignData.publicUrl
                val mediaResp = RetrofitClient.postApi.createPostMedia(
                    createMedia = club.gifters.giftersclub.model.CreatePostMediaRequest(
                        postId,
                        if (isVideo) "video" else "photo",
                        publicUrl,
                        index
                    )
                )
                if (!mediaResp.isSuccessful) throw Exception("Failed to save post media")
                done++
                updateProgress(done, total)
            }
            complete("Post published")
            // clear pending marker (and video flag if none pending)
            UploadTracker.clearAll(applicationContext, getCurrentUserId(), postId)
            return Result.success()
        } catch (e: Exception) {
            complete("Upload failed")
            return Result.retry()
        }
    }

    private fun ensureChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val mgr = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Post Uploads", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun updateProgress(done: Int, total: Int) {
        val mgr = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText("$done of $total uploaded")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, (done * 100 / total).coerceIn(0, 100), false)
            .build()
        mgr.notify(notifId, notif)
    }

    private fun complete(text: String) {
        val mgr = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(if (text.contains("failed", true)) android.R.drawable.stat_notify_error else android.R.drawable.stat_sys_upload_done)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(false)
            .build()
        mgr.notify(notifId, notif)
    }

    private fun getCurrentUserId(): String? {
        val prefs = applicationContext.getSharedPreferences("supabase", Context.MODE_PRIVATE)
        val access = prefs.getString("access_token", null) ?: return null
        val parts = access.split('.')
        if (parts.size < 2) return null
        return try {
            val decoded = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE), Charsets.UTF_8)
            org.json.JSONObject(decoded).optString("sub").takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }

    private fun getContentLength(uri: Uri): Long? {
        return try {
            applicationContext.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                val len = afd.length
                if (len > 0) return len
            }
            val cursor = applicationContext.contentResolver.query(
                uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (idx >= 0) {
                        val size = it.getLong(idx)
                        if (size > 0) return size
                    }
                }
            }
            null
        } catch (_: Exception) { null }
    }
}
