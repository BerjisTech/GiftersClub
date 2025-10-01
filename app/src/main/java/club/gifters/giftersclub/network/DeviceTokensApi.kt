package club.gifters.giftersclub.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface DeviceTokensApi {
    @Headers("Prefer: resolution=merge-duplicates", "Prefer: return=representation")
    @POST("user_device_tokens")
    suspend fun upsert(
        @Body row: Map<String, @JvmSuppressWildcards Any>
    ): Response<List<Map<String, Any>>>
}

