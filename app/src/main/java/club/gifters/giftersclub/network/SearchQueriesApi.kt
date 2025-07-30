package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.SearchQuery
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * API for retrieving saved search query suggestions.
 */
interface SearchQueriesApi {
    /**
     * Fetch distinct queries matching the user input, ordered by suggestion_index and result_clicked_index.
     */
    @GET("search_queries")
    suspend fun searchQueries(
        @Query("select", encoded = true) select: String = "query",
        @Query("distinct", encoded = true) distinct: String = "query",
        @Query("query") queryFilter: String? = null,
        @Query("user_id", encoded = true) userIdFilter: String? = null,
        @Query(
            "order",
            encoded = true
        ) order: String = "suggestion_index.desc,result_clicked_index.desc",
        @Query("limit") limit: Int = 10
    ): List<SearchQuery>

    /**
     * Fetch querry suggestions count(query) as query_frequency group by query where user is not passed user_id ie current user
     */
    @GET("search_queries")
    suspend fun searchRecommendedQueries(
        @Query("select", encoded = true) select: String = "query",
        @Query("user_id", encoded = true) userId: String,
        @Query("limit") limit: Int = 1000
    ): List<SearchQuery>

    /**
     * Fetch distinct queries for current user ordered by most recent.
     */
    @GET("search_queries")
    suspend fun getUserSearchQueries(
        @Query("select", encoded = true) select: String = "query,created_at",
        @Query("user_id", encoded = true) userIdFilter: String,
        @Query("order", encoded = true) order: String = "created_at.desc",
        @Query("limit") limit: Int = 10
    ): List<SearchQuery>

    /**
     * Record a search event for current user.
     */
    @Headers("Prefer: return=representation")
    @POST("search_queries")
    suspend fun insertSearchQuery(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): List<SearchQuery>

    /**
     * Delete a specific search query for current user.
     */
    @DELETE("search_queries")
    suspend fun deleteSearchQuery(
        @Query("query", encoded = true) queryFilter: String,
        @Query("user_id", encoded = true) userIdFilter: String
    ): Response<Unit>

    /**
     * Delete all search queries for current user.
     */
    @DELETE("search_queries")
    suspend fun deleteAllSearchQueries(
        @Query("user_id", encoded = true) userIdFilter: String
    ): Response<Unit>
}