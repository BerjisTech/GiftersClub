package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.Sticker
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for fetching remote stickers.
 */
interface StickersApi {
    @GET("stickers")
    suspend fun getActiveStickers(
        @Query("select", encoded = true) select: String = "*",
        @Query("is_active", encoded = true) activeFilter: String = "eq.true",
        @Query("order", encoded = true) order: String = "sort_index.desc,created_at.desc"
    ): List<Sticker>
}

