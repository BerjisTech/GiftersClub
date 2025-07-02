package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.PostAccess
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Supabase REST interface for querying post access records.
 */
interface PostAccessApi {
    @GET("post_access")
    suspend fun getPostAccesses(
        @Query("post_id", encoded = true) postFilter: String,
        @Query("user_id", encoded = true) userFilter: String
    ): List<PostAccess>
}