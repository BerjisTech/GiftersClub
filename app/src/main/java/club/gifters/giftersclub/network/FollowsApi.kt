package club.gifters.giftersclub.network

import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit interface for follow/unfollow and follow-status endpoints.
 */
interface FollowsApi {
    /**
     * Follow a user (current user follows another).
     */
    @Headers("Prefer: return=representation")
    @POST("follows")
    suspend fun followUser(
        @Body follow: Map<String, @JvmSuppressWildcards Any>
    ): Response<List<Any>>

    /**
     * Unfollow a user.
     */
    @Headers("Prefer: return=minimal")
    @DELETE("follows")
    suspend fun unfollowUser(
        @Query("followed_id", encoded = true) followedIdFilter: String,
        @Query("follower_id", encoded = true) followerIdFilter: String
    ): Response<Void>

    /**
     * Check if current user follows the given user.
     * Returns count via Content-Range header.
     */
    @Headers("Prefer: count=exact")
    @GET("follows")
    suspend fun isFollowingUser(
        @Query("followed_id", encoded = true) followedIdFilter: String,
        @Query("follower_id", encoded = true) followerIdFilter: String,
        @Query("select", encoded = true) select: String = "id",
        @Query("limit") limit: Int = 0
    ): Response<Void>

    /**
     * Check if given user follows the current user.
     * Returns count via Content-Range header.
     */
    @Headers("Prefer: count=exact")
    @GET("follows")
    suspend fun isFollowedByUser(
        @Query("followed_id", encoded = true) followedIdFilter: String,
        @Query("follower_id", encoded = true) followerIdFilter: String,
        @Query("select", encoded = true) select: String = "id",
        @Query("limit") limit: Int = 0
    ): Response<Void>
}