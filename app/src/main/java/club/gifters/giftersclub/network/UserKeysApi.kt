package club.gifters.giftersclub.network

import retrofit2.Response
import retrofit2.http.*

data class UserKeyRow(val user_id: String, val public_key: String, val created_at: String?)

interface UserKeysApi {
    @GET("user_e2ee_keys")
    suspend fun getKey(
        @Query("select", encoded = true) select: String = "user_id,public_key,created_at",
        @Query("user_id", encoded = true) userIdFilter: String,
        @Query("limit") limit: Int = 1
    ): List<UserKeyRow>

    @Headers("Prefer: resolution=merge-duplicates", "Prefer: return=representation")
    @POST("user_e2ee_keys")
    suspend fun upsertKey(
        @Body row: Map<String, @JvmSuppressWildcards Any>
    ): Response<List<UserKeyRow>>
}

