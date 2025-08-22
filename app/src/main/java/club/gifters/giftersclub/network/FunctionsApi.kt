package club.gifters.giftersclub.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Query
import club.gifters.giftersclub.model.CreateLiveStreamRequest
import club.gifters.giftersclub.model.LiveStream

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
     * Purchase one-time live access via Edge Function.
     */
    @POST("purchase-live-access")
    suspend fun purchaseLiveAccessRpc(
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
     * Record a post view via Supabase Edge Function.
     * viewDuration is optional and measured in seconds.
     */
    @POST("log-post-view")
    suspend fun logPostView(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Unit>

    /**
     * Create a new live stream session via Edge Function.
     */
    @POST("live-session")
    suspend fun createLiveSession(
        @Body request: CreateLiveStreamRequest
    ): Response<LiveStream>

    /**
     * Fetch an existing live stream session via Edge Function.
     */
    @GET("live-session")
    suspend fun getLiveSession(
        @Query("id") id: String
    ): Response<LiveStream>

    /**
     * Update a live stream session via Edge Function.
     */
    @PATCH("live-session")
    suspend fun updateLiveSession(
        @Query("id") id: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any>
    ): Response<LiveStream>

    /**
     * Update interaction privacy settings via Edge Function.
     */
    @POST("update-interaction-settings")
    suspend fun updateInteractionSettingsRpc(
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
