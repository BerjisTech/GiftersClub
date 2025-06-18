package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.Gift
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit API definition for fetching gifts from Supabase REST endpoint.
 */
interface GiftApi {
    @GET("gifts")
    suspend fun getGifts(
        @Query("select") select: String = "*",
        @Query("order") order: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
    ): List<Gift>
}