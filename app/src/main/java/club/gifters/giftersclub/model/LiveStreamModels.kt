package club.gifters.giftersclub.model

import com.google.gson.annotations.SerializedName

/**
 * Live Stream information
 */
data class LiveStream(
    val id: String,
    // Server responses use snake_case: host_id
    @SerializedName("host_id") val hostId: String,
    val title: String,
    val description: String,
    val status: String,
    @SerializedName("viewer_count") val viewerCount: Int,
    @SerializedName("started_at") val startedAt: String?,
    @SerializedName("ended_at") val endedAt: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    /**
     * LiveKit access token for connecting to SFU (via Edge Function)
     */
    val token: String? = null
)

/**
 * Viewer entry for active live stream sessions
 */
data class LiveStreamViewer(
    val id: String,
    @SerializedName("live_stream_id") val liveStreamId: String,
    @SerializedName("viewer_id") val viewerId: String,
    @SerializedName("joined_at") val joinedAt: String
)

/**
 * Comment made in a live stream
 */
data class LiveStreamComment(
    val id: String,
    @SerializedName("live_stream_id") val liveStreamId: String,
    @SerializedName("parent_comment_id") val parentCommentId: String?,
    @SerializedName("user_id") val userId: String,
    val content: String,
    @SerializedName("created_at") val createdAt: String,
    val profile: Profile? = null
)

/**
 * Template for gift targets in live stream gift gallery
 */
data class GiftGalleryTemplate(
    val id: String,
    @SerializedName("gift_id") val giftId: String,
    @SerializedName("target_count") val targetCount: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)

/**
 * Live stream gift gallery entry representing gift targets and progress
 */
data class LiveStreamGiftGallery(
    val id: String,
    @SerializedName("live_stream_id") val liveStreamId: String,
    @SerializedName("gift_id") val giftId: String,
    @SerializedName("target_count") val targetCount: Int,
    @SerializedName("gifted_count") val giftedCount: Int,
    @SerializedName("title_gifter_id") val titleGifterId: String?,
    val litUp: Boolean,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    val gift: Gift? = null,
    @SerializedName("title_gifter") val titleGifter: Profile? = null
)
