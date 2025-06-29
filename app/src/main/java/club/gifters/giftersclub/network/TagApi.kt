package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.Tag
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * API for querying hashtags (tags) for explore suggestions.
 */
interface TagApi {
    /**
     * Search tags (hashtags) by name keyword, ordered by recency.
     */
    @GET("tags")
    suspend fun searchTags(
        @Query("select", encoded = true) select: String = "*",
        @Query("name.ilike", encoded = true) nameFilter: String,
        @Query("order", encoded = true) order: String = "created_at.desc",
        @Query("limit", encoded = true) limit: Int = 10
    ): List<Tag>
}