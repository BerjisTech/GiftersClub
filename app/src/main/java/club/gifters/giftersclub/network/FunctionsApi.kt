package club.gifters.giftersclub.network

import retrofit2.Response
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
}