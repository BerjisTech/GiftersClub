package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.Wishlist
import club.gifters.giftersclub.model.CreateWishlistRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
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

    /**
     * Create a new wishlist record. Returns the created Wishlist.
     */
    @Headers("Prefer: return=representation")
    @POST("wishlists")
    suspend fun createWishlist(
        @Query("select", encoded = true) select: String = "*",
        @Body createWishlist: CreateWishlistRequest
    ): Response<List<Wishlist>>
}