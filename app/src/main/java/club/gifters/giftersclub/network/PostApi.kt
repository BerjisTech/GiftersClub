package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.Post
import retrofit2.http.GET
import retrofit2.http.Query

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
}