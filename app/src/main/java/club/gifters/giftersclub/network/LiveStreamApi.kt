package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.CreateLiveStreamRequest
import club.gifters.giftersclub.model.LiveStream
import club.gifters.giftersclub.model.LiveStreamComment
import club.gifters.giftersclub.model.LiveStreamCommentRequest
import club.gifters.giftersclub.model.LiveStreamViewer
import club.gifters.giftersclub.model.LiveStreamViewerRequest
import club.gifters.giftersclub.model.GiftGalleryTemplate
import club.gifters.giftersclub.model.LiveStreamGiftGallery
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit interface for live stream operations.
 */
interface LiveStreamApi {
    @Headers("Prefer: return=representation")
    @POST("live_streams")
    suspend fun createLiveStream(
        @Query("select", encoded = true) select: String = "*",
        @Body createLiveStream: CreateLiveStreamRequest
    ): Response<List<LiveStream>>

    @Headers("Prefer: return=representation")
    @PATCH("live_streams")
    suspend fun endLiveStream(
        @Query("select", encoded = true) select: String = "*",
        @Query("id", encoded = true) idFilter: String,
        @Body updates: Map<String, Any>
    ): Response<List<LiveStream>>

    @GET("live_streams")
    suspend fun getLiveStreamById(
        @Query("select", encoded = true) select: String = "*",
        @Query("id", encoded = true) idFilter: String
    ): List<LiveStream>

    @GET("live_stream_comments")
    suspend fun getLiveStreamComments(
        @Query("select", encoded = true) select: String = "*,profile:profiles(*)",
        @Query("live_stream_id", encoded = true) streamFilter: String
    ): List<LiveStreamComment>

    @Headers("Prefer: return=representation")
    @POST("live_stream_comments")
    suspend fun createLiveStreamComment(
        @Query("select", encoded = true) select: String = "*,profile:profiles(*)",
        @Body comment: LiveStreamCommentRequest
    ): Response<List<LiveStreamComment>>

    @GET("live_stream_viewers")
    suspend fun getLiveStreamViewers(
        @Query("select", encoded = true) select: String = "*",
        @Query("live_stream_id", encoded = true) streamFilter: String
    ): List<LiveStreamViewer>

    @Headers("Prefer: return=representation")
    @POST("live_stream_viewers")
    suspend fun joinLiveStream(
        @Query("select", encoded = true) select: String = "*",
        @Body viewer: LiveStreamViewerRequest
    ): Response<List<LiveStreamViewer>>

    @GET("gift_gallery_templates")
    suspend fun getGiftGalleryTemplates(
        @Query("select", encoded = true) select: String = "*"
    ): List<GiftGalleryTemplate>

    @GET("live_stream_gift_gallery")
    suspend fun getGiftGalleryByStream(
        @Query("select", encoded = true) select: String = "*,gift:gifts(*),title_gifter:profiles(*)",
        @Query("live_stream_id", encoded = true) streamFilter: String,
        @Query("order", encoded = true) order: String = "created_at.asc"
    ): List<LiveStreamGiftGallery>

    /**
     * Search live streams by title or description keyword.
     */
    @GET("live_streams")
    suspend fun searchLiveStreams(
        @Query("select", encoded = true) select: String = "*",
        @Query("or", encoded = true) orFilter: String
    ): List<LiveStream>
}