package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.Profile
import club.gifters.giftersclub.model.WishlistItem
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.Query

/**
 * Retrofit interface for fetching user profiles.
 */
interface ProfileApi {
    @GET("profiles")
    suspend fun getProfileByUsername(
        @Query("select", encoded = true) select: String =
            "*,followers_count:follows!follows_followed_id(count)," +
            "following_count:follows!follows_follower_id(count)",
        @Query("username", encoded = true) usernameFilter: String
    ): List<Profile>

    /**
     * Fetch the profile for the current user by user_id
     */
    @GET("profiles")
    suspend fun getProfileByUserId(
        @Query("select", encoded = true) select: String =
            "*,followers_count:follows!follows_followed_id(count)," +
            "following_count:follows!follows_follower_id(count)",
        @Query("user_id", encoded = true) userIdFilter: String
    ): List<Profile>

    /**
     * Update profile fields for the current user and return the updated record.
     */
    @Headers("Prefer: return=representation")
    @PATCH("profiles")
    suspend fun updateProfile(
        @Query("select", encoded = true) select: String = "*",
        @Query("user_id", encoded = true) userIdFilter: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any>
    ): Response<List<Profile>>

    /**
     * List wishlist items for a user (to count open vs fulfilled wishlists).
     */
    @GET("wishlists")
    suspend fun listWishlistsByUser(
        @Query("select", encoded = true) select: String = "id,is_fulfilled",
        @Query("user_id", encoded = true) userIdFilter: String
    ): List<WishlistItem>

    /**
     * Fetch profiles for multiple user_ids (in filter).
     */
    @GET("profiles")
    suspend fun getProfilesByUserIds(
        @Query("select", encoded = true) select: String = "*",
        @Query("user_id", encoded = true) userIdsFilter: String
    ): List<Profile>

    /**
     * Search profiles by username or email (or exact user_id).
     * Uses Supabase OR filter with ilike for partial matches.
     */
    @GET("profiles")
    suspend fun searchProfiles(
        @Query("select", encoded = true) select: String = "*",
        @Query("or", encoded = true) orFilter: String
    ): List<Profile>
}