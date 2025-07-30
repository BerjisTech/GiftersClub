package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.Profile
import club.gifters.giftersclub.model.WishlistItem
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query
import club.gifters.giftersclub.model.FilteredWord
import club.gifters.giftersclub.model.UserBlock
import club.gifters.giftersclub.model.UserReport
import club.gifters.giftersclub.model.UserSettings
import club.gifters.giftersclub.model.BlockedUserItem
import club.gifters.giftersclub.model.ReportedUserItem

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
    ): List<Profile>

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
     * Fetch interaction settings (who_can_interact) for a user.
     */
    @GET("user_settings")
    suspend fun getUserSettings(
        @Query("select", encoded = true) select: String = "who_can_interact",
        @Query("user_id", encoded = true) userIdFilter: String
    ): List<UserSettings>

    /**
     * Search profiles by username or email (or exact user_id).
     * Uses Supabase OR filter with ilike for partial matches.
     */
    @GET("profiles")
    suspend fun searchProfiles(
        @Query("select", encoded = true) select: String = "*",
        @Query("or", encoded = true) orFilter: String
    ): List<Profile>

    /** Fetch filtered words for current user */
    @GET("filtered_words")
    suspend fun getFilteredWords(
        @Query("select", encoded = true) select: String = "word",
        @Query("user_id", encoded = true) userIdFilter: String
    ): List<FilteredWord>

    /** Insert a new filtered word */
    @Headers("Prefer: return=representation")
    @POST("filtered_words")
    suspend fun insertFilteredWord(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): List<FilteredWord>

    /** Remove a filtered word */
    @DELETE("filtered_words")
    suspend fun deleteFilteredWord(
        @Query("user_id", encoded = true) userIdFilter: String,
        @Query("word", encoded = true) wordFilter: String
    ): Response<Unit>

    /** Fetch blocks for current user */
    @GET("user_blocks")
    suspend fun getUserBlocks(
        @Query("blocker_user_id", encoded = true) blockerFilter: String
    ): List<UserBlock>

    /** Block a user */
    @Headers("Prefer: return=representation")
    @POST("user_blocks")
    suspend fun blockUser(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): List<UserBlock>

    /** Unblock a user */
    @DELETE("user_blocks")
    suspend fun unblockUser(
        @Query("blocker_user_id", encoded = true) blockerFilter: String,
        @Query("blocked_user_id", encoded = true) blockedFilter: String
    ): Response<Unit>

    /**
     * Unblock all users blocked by the current user.
     */
    @DELETE("user_blocks")
    suspend fun unblockAll(
        @Query("blocker_user_id", encoded = true) blockerFilter: String
    ): Response<Unit>

    /** Fetch user reports for current user */
    @GET("user_reports")
    suspend fun getUserReports(
        @Query("select", encoded = true) select: String = "*",
        @Query("reporter_user_id", encoded = true) reporterFilter: String
    ): List<UserReport>

    /**
     * Fetch blocked users with nested profile information (paginated).
     */
    @GET("user_blocks")
    suspend fun getBlockedUserItems(
        @Query("select", encoded = true) select: String = "blocked_user_id(user_id,username)",
        @Query("blocker_user_id", encoded = true) blockerFilter: String,
        @Query("limit", encoded = true) limit: Int,
        @Query("offset", encoded = true) offset: Int
    ): List<BlockedUserItem>

    /**
     * Fetch reported users with nested profile information (paginated).
     */
    @GET("user_reports")
    suspend fun getReportedUserItems(
        @Query("select", encoded = true) select: String = "reported_user_id(user_id,username),reason,status,created_at",
        @Query("reporter_user_id", encoded = true) reporterFilter: String,
        @Query("limit", encoded = true) limit: Int,
        @Query("offset", encoded = true) offset: Int
    ): List<ReportedUserItem>

    /** Report a user */
    @Headers("Prefer: return=representation")
    @POST("user_reports")
    suspend fun reportUser(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): List<UserReport>
}