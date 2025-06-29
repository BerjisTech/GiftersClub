package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.SearchQuery
import retrofit2.http.GET
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
        @Query("select",   encoded = true) select: String   = "query",
        @Query("distinct", encoded = true) distinct: String = "query",
        @Query("\"query\"", encoded = true) queryFilter: String,
        @Query("order", encoded = true) order: String = "suggestion_index.desc,result_clicked_index.desc",
        @Query("limit") limit: Int = 10
    ): List<SearchQuery>
}