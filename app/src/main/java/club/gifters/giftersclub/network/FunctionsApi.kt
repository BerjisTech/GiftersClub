package club.gifters.giftersclub.network

import retrofit2.Response
import club.gifters.giftersclub.network.PresignRequest
import club.gifters.giftersclub.network.PresignResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface FunctionsApi {
    /**
     * Process gift sending securely on backend via Edge Function.
     */
    @POST("send-gift")
    suspend fun processGiftSendRpc(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Unit>

    /**
     * Securely contribute to a wishlist via Edge Function.
     */
    @POST("contribute-wishlist")
    suspend fun processWishlistContributionRpc(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Unit>

    /**
     * Process purchase of tokens securely on backend via Edge Function.
     */
    @POST("purchase-tokens")
    suspend fun processPurchaseTokensRpc(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Unit>

    /**
     * Subscribe to a creator by purchasing a subscription via Edge Function.
     */
    @POST("subscribe-creator")
    suspend fun subscribeToCreatorRpc(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Unit>

    /**
     * Purchase pay-per-post access via Edge Function.
     */
    @POST("purchase-post-access")
    suspend fun purchasePostAccessRpc(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Unit>

    /**
     * Trigger notification email via Supabase Edge Function.
     */
    @POST("send-notification-email")
    suspend fun sendNotificationEmail(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Unit>

    /**
     * Record an auth log event (sign-in) via Edge Function.
     */
    @POST("auth-log")
    suspend fun authLogRpc(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Unit>

    /**
     * Obtain S3 presigned URLs for media uploads via Supabase Edge Function.
     */
    @POST("upload-media")
    suspend fun uploadMedia(
        @Body request: PresignRequest
    ): Response<PresignResponse>
}