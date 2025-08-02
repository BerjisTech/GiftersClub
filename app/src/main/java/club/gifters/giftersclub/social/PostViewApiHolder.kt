package club.gifters.giftersclub.social

import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper object to log post view events (with optional duration) via Supabase Edge Function.
 */
object PostViewApiHolder {
    private val functionsApi = RetrofitClient.functionsApi

    /**
     * Log a post view with optional duration in seconds.
     */
    suspend fun logPostView(postId: String, durationSec: Int? = null) = withContext(Dispatchers.IO) {
        val userId = AuthUtils.getCurrentUserId(RetrofitClient.context) ?: return@withContext
        val payload = mutableMapOf<String, Any>(
            "postId" to postId,
            "userId" to userId,
            "platform" to "android"
        )
        durationSec?.let { payload["viewDuration"] = it }
        functionsApi.logPostView(payload)
    }
}