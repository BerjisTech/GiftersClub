package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.TopGifter
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for Top Gifters (leaderboard).
 */
interface LeaderboardApi {
    @GET("top_gifters")
    suspend fun getTopGifters(
        @Query("select") select: String = "*",
        @Query("order") order: String = "tokens_sent.desc",
        @Query("limit") limit: Int = 100
    ): List<TopGifter>
}