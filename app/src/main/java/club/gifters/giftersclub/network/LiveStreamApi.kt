package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.CreateLiveStreamRequest
import club.gifters.giftersclub.model.LiveStream
import club.gifters.giftersclub.model.LiveStreamComment
import club.gifters.giftersclub.model.LiveStreamCommentRequest
import club.gifters.giftersclub.model.LiveStreamViewer
import club.gifters.giftersclub.model.LiveStreamViewerRequest
import club.gifters.giftersclub.model.GiftGalleryTemplate
import club.gifters.giftersclub.model.LiveStreamGiftGallery
import club.gifters.giftersclub.model.LiveGiftEvent
import retrofit2.http.Path
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

    /** Raw create for advanced scenarios like scheduling (status/started_at). */
    @Headers("Prefer: return=representation")
    @POST("live_streams")
    suspend fun createLiveStreamRaw(
        @Query("select", encoded = true) select: String = "*",
        @Body body: Map<String, @JvmSuppressWildcards Any?>
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

    @GET("live_streams")
    suspend fun getLiveStreamsByRoomId(
        @Query("select", encoded = true) select: String = "id",
        @Query("room_id", encoded = true) roomFilter: String
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
        @Query("or",     encoded = true) orFilter: String,
        @Query("status", encoded = true) statusFilter: String? = null,
        @Query("order",  encoded = true) order: String? = null
    ): List<LiveStream>

    /**
     * Fetch live streams by host IDs, filtering for status.
     */
    @GET("live_streams")
    suspend fun getLiveStreamsByHosts(
        @Query("select", encoded = true) select: String = "*",
        @Query("host_id") hostFilter: String,
        @Query("status") statusFilter: String = "eq.live",
        @Query("order", encoded = true) order: String = "live_stream_viewer_count.desc,live_stream_comment_count_so_far.desc"
    ): List<LiveStream>

    /**
     * RPC: ranked live streams for the feed for a given viewer.
     */
    @Headers("Prefer: params=multiple-objects")
    @POST("rpc/feed_live_streams")
    suspend fun getFeedLiveStreams(
        @Body params: Map<String, @JvmSuppressWildcards Any?>
    ): List<LiveStream>

    /**
     * Fetch gift events for a stream (joined with gift and gifter) since a timestamp.
     */
    @GET("gift_sent")
    suspend fun getGiftEvents(
        @Query("select", encoded = true) select: String = "id,live_stream_id,gifter,recipient,gift,tokens_used,created_at,gift:gifts(*),gifter:profiles(*)",
        @Query("live_stream_id", encoded = true) streamFilter: String,
        @Query("created_at", encoded = true) createdAfterFilter: String? = null,
        @Query("order", encoded = true) order: String = "created_at.asc"
    ): List<LiveGiftEvent>

    /**
     * Gift animation rules with embedded animation row.
     */
    @GET("gift_animation_rules")
    suspend fun getGiftAnimationRules(
        @Query("select", encoded = true) select: String = "*,animation:gift_animations(*)",
        @Query("gift_id", encoded = true) giftIdFilter: String,
        @Query("scope", encoded = true) scopeFilter: String = "in.(all,solo,multi_host,match)",
        @Query("order", encoded = true) order: String = "is_featured.desc,min_tokens.desc,combo_count.desc"
    ): List<Map<String, Any?>>

    // Battles
    @GET("battle_sessions")
    suspend fun getActiveBattleForStream(
        @Query("select", encoded = true) select: String = "*",
        @Query("live_stream_id", encoded = true) streamFilter: String,
        @Query("status", encoded = true) statusFilter: String = "eq.active",
        @Query("order", encoded = true) order: String = "started_at.desc"
    ): List<club.gifters.giftersclub.model.BattleSession>

    @GET("battle_participants")
    suspend fun getBattleParticipants(
        @Query("select", encoded = true) select: String = "*",
        @Query("battle_id", encoded = true) battleFilter: String
    ): List<club.gifters.giftersclub.model.BattleParticipant>
}
