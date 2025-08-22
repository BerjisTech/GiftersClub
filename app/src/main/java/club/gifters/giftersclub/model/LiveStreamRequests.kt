package club.gifters.giftersclub.model

import com.google.gson.annotations.SerializedName

/**
 * Request body for creating a new live stream.
 */
data class CreateLiveStreamRequest(
    // use camelCase so JSON property matches server expectation (hostId)
    val hostId: String,
    val title: String,
    val description: String = "",
    val categoryId: Int? = null,
    val tags: List<String>? = null
)

/**
 * Request body for inserting a viewer into a live stream.
 */
data class LiveStreamViewerRequest(
    @SerializedName("live_stream_id") val liveStreamId: String,
    @SerializedName("viewer_id") val viewerId: String
)

/**
 * Request body for adding a comment to a live stream.
 */
data class LiveStreamCommentRequest(
    @SerializedName("live_stream_id") val liveStreamId: String,
    @SerializedName("parent_comment_id") val parentCommentId: String? = null,
    @SerializedName("user_id") val userId: String,
    val content: String
)
