package club.gifters.giftersclub.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface FunctionsApi {
    @POST("purchase-tokens")
    suspend fun processPurchaseTokensRpc(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Unit>
}