package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.Wishlist
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for fetching user wishlists.
 */
interface WishlistApi {
    @GET("wishlists")
    suspend fun getWishlists(
        @Query("select", encoded = true) select: String = "*",
        @Query("user_id", encoded = true) userIdFilter: String
    ): List<Wishlist>
}