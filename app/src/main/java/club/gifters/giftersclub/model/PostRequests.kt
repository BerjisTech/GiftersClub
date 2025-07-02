package club.gifters.giftersclub.model

import com.google.gson.annotations.SerializedName

/**
 * Request body for creating a new post.
 */
data class CreatePostRequest(
    @SerializedName("user_id") val userId: String,
    val content: String,
    @SerializedName("quote_post_id") val quotePostId: String? = null,
    @SerializedName("reply_comment_id") val replyCommentId: String? = null,
    @SerializedName("access_type") val accessType: String? = null,
    val price: Int? = null
)

/**
 * Request body for creating a post_media record after uploading to storage.
 */
data class CreatePostMediaRequest(
    @SerializedName("post_id") val postId: String,
    @SerializedName("media_type") val mediaType: String,
    val url: String,
    val order: Int
)

/**
 * Request body for upserting tags (hashtags).
 */
data class TagUpsertRequest(
    val name: String
)

/**
 * Request body for upserting post_tags mapping.
 */
data class PostTagUpsertRequest(
    @SerializedName("post_id") val postId: String,
    @SerializedName("tag_id") val tagId: String
)