package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.RecentGiftEntry
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for the recent_gifts view returning entries for gifts received.
 */
interface RecentGiftsApi {
    /**
     * List recent gifts received by a user, selecting only gifter info.
     */
    @GET("recent_gifts")
    suspend fun listRecentGifts(
        @Query("select", encoded = true) select: String =
            "gifter_id,gifter_username,gifter_image",
        @Query("receiver_id", encoded = true) receiverFilter: String
    ): List<RecentGiftEntry>
}