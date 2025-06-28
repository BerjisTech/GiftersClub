package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.Wishlist
import club.gifters.giftersclub.model.CreateWishlistRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import club.gifters.giftersclub.model.WishlistContribution

/**
 * Retrofit interface for fetching user wishlists.
 */
interface WishlistApi {
    @GET("wishlists")
    suspend fun getWishlists(
        @Query("select", encoded = true) select: String = "*,profile:profiles(id,user_id,username,name),wishlist_contributions(tokens)",
        @Query("user_id", encoded = true) userIdFilter: String? = null,
        @Query("or",       encoded = true) orFilter: String? = null,
        @Query("order",    encoded = true) order: String = "created_at.desc",
        @Query("limit")   limit: Int,
        @Query("offset")  offset: Int
    ): List<club.gifters.giftersclub.gifts.WishlistWithOwner>

    /**
     * Create a new wishlist record. Returns the created Wishlist.
     */
    @Headers("Prefer: return=representation")
    @POST("wishlists")
    suspend fun createWishlist(
        @Query("select", encoded = true) select: String = "*",
        @Body createWishlist: CreateWishlistRequest
    ): Response<List<Wishlist>>

    /**
     * Fetch a single wishlist by id.
     */
    @GET("wishlists")
    suspend fun getWishlistById(
        @Query("select", encoded = true) select: String = "*",
        @Query("id", encoded = true) idFilter: String
    ): List<Wishlist>

    /**
     * Fetch contributions for a wishlist.
     */
    @GET("wishlist_contributions")
    suspend fun getWishlistContributions(
        @Query("select", encoded = true) select: String = "*",
        @Query("wishlist_id", encoded = true) wishlistIdFilter: String
    ): List<WishlistContribution>
}