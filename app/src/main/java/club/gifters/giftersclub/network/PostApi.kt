package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.Post
import club.gifters.giftersclub.model.SearchExploreResult
import club.gifters.giftersclub.model.PostMedia
import club.gifters.giftersclub.model.Tag
import club.gifters.giftersclub.model.CreatePostRequest
import club.gifters.giftersclub.model.CreatePostMediaRequest
import club.gifters.giftersclub.model.TagUpsertRequest
import club.gifters.giftersclub.model.PostTagUpsertRequest
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Query
import retrofit2.http.Headers
import retrofit2.http.DELETE
import retrofit2.Response

/**
 * Retrofit interface for fetching posts with profile media and reaction counts.
 */
interface PostApi {
    @GET("posts")
    suspend fun getPosts(
        @Query("select", encoded = true)
        select: String =
            "*,profile:profiles(id,user_id,username,image)," +
            "media:post_media(id,media_type,url,order,created_at)"
        ,
        @Query("order") order: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
    ): List<Post>

    /**
     * Create a new post record. Returns Response<Post> to allow checking HTTP status without exceptions.
     */
    @Headers("Prefer: return=representation")
    @POST("posts")
    suspend fun createPost(
        @Query("select", encoded = true) select: String = "*",
        @Body createPost: CreatePostRequest
    ): Response<List<Post>>

    /**
     * Create a new post_media record. Returns Response<PostMedia> to allow checking HTTP status without exceptions.
     */
    @Headers("Prefer: return=representation")
    @POST("post_media")
    suspend fun createPostMedia(
        @Query("select", encoded = true) select: String = "*",
        @Body createMedia: CreatePostMediaRequest
    ): Response<List<PostMedia>>

    /**
     * Upsert hashtags and return full Tag records. Returns Response<List<Tag>> to allow checking HTTP status without exceptions.
     */
    @Headers("Prefer: resolution=merge-duplicates, return=representation")
    @POST("tags")
    suspend fun upsertTags(
        @Body tags: List<TagUpsertRequest>
    ): Response<List<Tag>>

    /**
     * Upsert mappings between posts and tags.
     */
    @Headers("Prefer: resolution=merge-duplicates")
    @POST("post_tags")
    suspend fun upsertPostTags(
        @Body mappings: List<PostTagUpsertRequest>
    )

    /**
     * Fetch posts created by a specific user.
     */
    @GET("posts")
    suspend fun getUserPosts(
        @Query("select", encoded = true) select: String =
            "*,profile:profiles(id,user_id,username,image)," +
            "media:post_media(id,media_type,url,order,created_at)",
        @Query("order") order: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
        @Query("user_id", encoded = true) userIdFilter: String
    ): List<Post>

    /**
     * Search posts by keyword in content and optional media type filter.
     */
    @GET("posts")
    suspend fun searchPosts(
        @Query("select", encoded = true) select: String =
            "*,profile:profiles(id,user_id,username,image)," +
            "media:post_media(id,media_type,url,order,created_at)",
        @Query("or", encoded = true) orFilter: String,
        @Query("media.media_type", encoded = true) mediaTypeFilter: String? = null,
        @Query("order", encoded = true) order: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
    ): List<Post>

    /**
     * Fetch posts by a set of explicit post IDs, with optional media type filter.
     */
    @GET("posts")
    suspend fun getPostsByIds(
        @Query("select", encoded = true) select: String =
            "*,profile:profiles(id,user_id,username,image)," +
            "media:post_media(id,media_type,url,order,created_at)",
        @Query("id", encoded = true) idFilter: String,
        @Query("media.media_type", encoded = true) mediaTypeFilter: String? = null,
        @Query("order", encoded = true) order: String,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): List<Post>

    @GET("posts")
    suspend fun getPostById(
        @Query("id", encoded = true) id: String,
        @Query("select", encoded = true)
        select: String =
            "*,profile:profiles(id,user_id,username,image)," +
            "media:post_media(id,media_type,url,order,created_at)"
    ): List<Post>

    /**
     * RPC for unified explore search on backend.
     */
    @Headers("Prefer: params=multiple-objects")
    @POST("rpc/search_explore")
    suspend fun searchExploreRpc(
        @Body params: Map<String, @JvmSuppressWildcards Any>
    ): SearchExploreResult

    /**
     * Delete a post by moving it to the deleted_posts archive table via a database trigger.
     */
    @Headers("Prefer: return=minimal")
    @DELETE("posts")
    suspend fun deletePost(
        @Query("id", encoded = true) idFilter: String
    ): Response<Void>
}