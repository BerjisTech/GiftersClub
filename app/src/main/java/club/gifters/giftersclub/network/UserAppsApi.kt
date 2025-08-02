package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.UserApp
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * API for tracking user app install/version history.
 */
interface UserAppsApi {
    @GET("user_apps")
    suspend fun queryUserApps(
        @Query("select", encoded = true) select: String,
        @Query("user_id", encoded = true) userId: String? = null,
        @Query("platform", encoded = true) platform: String? = null,
        @Query("order", encoded = true) order: String? = null,
        @Query("limit") limit: Int? = null
    ): List<UserApp>

    @Headers("Prefer: return=representation")
    @PATCH("user_apps")
    suspend fun updateUserApp(
        @Query("id", encoded = true) id: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any>
    ): List<UserApp>

    @Headers("Prefer: return=representation")
    @POST("user_apps")
    suspend fun insertUserApp(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): List<UserApp>
}