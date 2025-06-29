package club.gifters.giftersclub.gifts

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.DELETE
import retrofit2.http.Query

/**
 * API endpoints for comments and comment reactions.
 */
interface CommentApi {
    @GET("comments")
    suspend fun getCommentsByPost(
        @Query("post_id", encoded = true) postIdFilter: String,
        @Query("order", encoded = true) order: String = "created_at.desc",
        @Query("select", encoded = true) select: String =
            "*,profile:profiles(id,user_id,username,image)"
    ): List<Comment>

    @Headers("Prefer: return=representation")
    @POST("comments")
    suspend fun createComment(
        @Query("select", encoded = true) select: String = "*",
        @Body comment: Map<String, @JvmSuppressWildcards Any>
    ): Response<List<Comment>>

    @Headers("Prefer: count=exact")
    @GET("comment_reactions")
    suspend fun getCommentReactionCount(
        @Query("comment_id", encoded = true) commentIdFilter: String,
        @Query("type", encoded = true) type: String,
        @Query("select", encoded = true) select: String = "id",
        @Query("limit") limit: Int = 0
    ): Response<Void>

    @Headers("Prefer: return=representation")
    @POST("comment_reactions")
    suspend fun reactToComment(
        @Body reaction: Map<String, @JvmSuppressWildcards Any>
    ): Response<List<CommentReaction>>

    @Headers("Prefer: count=exact")
    @GET("comments")
    suspend fun getPostCommentCount(
        @Query("post_id", encoded = true) postIdFilter: String
    ): Response<Void>

    @Headers("Prefer: count=exact")
    @GET("post_reactions")
    suspend fun getPostReactionCount(
        @Query("post_id", encoded = true) postIdFilter: String,
        @Query("type", encoded = true) typeFilter: String,
        @Query("select", encoded = true) select: String = "id",
        @Query("limit") limit: Int = 0
    ): Response<Void>

    /**
     * React (like/share) to a post; user_id must be included for RLS.
     */
    @Headers("Prefer: return=representation")
    @POST("post_reactions")
    suspend fun reactToPost(
        @Body reaction: Map<String, @JvmSuppressWildcards Any>
    ): Response<List<PostReaction>>

    /**
     * Remove a reaction (unlike/unshare) from a post.
     */
    @Headers("Prefer: return=minimal")
    @DELETE("post_reactions")
    suspend fun unreactToPost(
        @Query("post_id", encoded = true) postIdFilter: String,
        @Query("user_id", encoded = true) userIdFilter: String,
        @Query("type", encoded = true) typeFilter: String
    ): Response<Void>

    /**
     * Check if the current user has reacted to (liked) a post.
     * Returns count via Content-Range header.
     */
    @Headers("Prefer: count=exact")
    @GET("post_reactions")
    suspend fun isPostLikedByUser(
        @Query("post_id", encoded = true) postIdFilter: String,
        @Query("user_id", encoded = true) userIdFilter: String,
        @Query("type", encoded = true) typeFilter: String,
        @Query("select", encoded = true) select: String = "id",
        @Query("limit") limit: Int = 0
    ): Response<Void>

    @Headers("Prefer: count=exact")
    @GET("comments")
    suspend fun getCommentReplyCount(
        @Query("parent_comment_id", encoded = true) parentIdFilter: String
    ): Response<Void>

    /**
     * Search comments by content keyword, returning post_id for matched comments.
     */
    @GET("comments")
    suspend fun searchComments(
        @Query("select", encoded = true) select: String = "post_id",
        @Query("or",      encoded = true) orFilter: String
    ): List<Comment>
}