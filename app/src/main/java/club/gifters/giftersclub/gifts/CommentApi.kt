package club.gifters.giftersclub.gifts

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * API endpoints for comments and comment reactions.
 */
interface CommentApi {
    @GET("comments")
    suspend fun getCommentsByPost(
        @Query("post_id", encoded = true) postIdFilter: String,
        @Query("order", encoded = true) order: String = "created_at.desc",
        @Query("select", encoded = true) select: String = "*"
    ): List<Comment>

    @Headers("Prefer: return=representation")
    @POST("comments")
    suspend fun createComment(
        @Body comment: Map<String, @JvmSuppressWildcards Any>
    ): Response<List<Comment>>

    @Headers("Prefer: count=exact")
    @GET("comment_reactions")
    suspend fun getCommentReactionCount(
        @Query("comment_id", encoded = true) commentIdFilter: String,
        @Query("type", encoded = true) type: String
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
}