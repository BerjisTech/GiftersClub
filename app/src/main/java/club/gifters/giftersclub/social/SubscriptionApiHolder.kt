package club.gifters.giftersclub.social

import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Helper object to handle creator subscriptions and post access purchases.
 */
object SubscriptionApiHolder {
    private val functionsApi = RetrofitClient.functionsApi
    private val subscriptionsApi = RetrofitClient.subscriptionsApi

    /**
     * Subscribe to a creator by purchasing a subscription via Edge Function.
     */
    suspend fun subscribeToCreator(
        creatorId: String,
        subscriberId: String,
        tokens: Int,
        durationType: String,
        txRef: String
    ): Boolean = withContext(Dispatchers.IO) {
        val body = mapOf(
            "creatorId" to creatorId,
            "subscriberId" to subscriberId,
            "tokens" to tokens,
            "durationType" to durationType,
            "txRef" to txRef
        )
        functionsApi.subscribeToCreatorRpc(body).isSuccessful
    }

    /**
     * Purchase pay-per-post access via Edge Function.
     */
    suspend fun purchasePostAccess(
        postId: String,
        userId: String,
        tokens: Int,
        txRef: String
    ): Boolean = withContext(Dispatchers.IO) {
        val body = mapOf(
            "postId" to postId,
            "userId" to userId,
            "tokens" to tokens,
            "txRef" to txRef
        )
        functionsApi.purchasePostAccessRpc(body).isSuccessful
    }

    /**
     * Returns true if the current user has purchased pay-per-post access.
     */
    suspend fun hasPostAccess(postId: String): Boolean = withContext(Dispatchers.IO) {
        val userId = AuthUtils.getCurrentUserId(RetrofitClient.context) ?: return@withContext false
        val resp = RetrofitClient.postAccessApi.getPostAccesses(
            postFilter = "eq.$postId",
            userFilter = "eq.$userId"
        )
        resp.isNotEmpty()
    }

    /**
     * Returns true if the current user has an active subscription to the given creator.
     */
    suspend fun hasSubscription(creatorId: String): Boolean = withContext(Dispatchers.IO) {
        val subscriberId = AuthUtils.getCurrentUserId(RetrofitClient.context) ?: return@withContext false
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(System.currentTimeMillis())
        val resp = subscriptionsApi.isSubscribed(
            creatorFilter = "eq.$creatorId",
            subscriberFilter = "eq.$subscriberId",
            orFilter = "end_date.is.null,end_date.gt.$now"
        )
        val header = resp.headers()["Content-Range"] ?: return@withContext false
        (header.substringAfterLast('/').toIntOrNull() ?: 0) > 0
    }
}