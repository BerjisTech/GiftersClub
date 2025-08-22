package club.gifters.giftersclub.model

import com.google.gson.annotations.SerializedName

/**
 * Data class representing a Post with nested profile, media, and reaction counts.
 */
data class Post(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val content: String?,
    @SerializedName("quote_post_id") val quotePostId: String?,
    @SerializedName("reply_comment_id") val replyCommentId: String?,
    @SerializedName("created_at") val createdAt: String?,
    val profile: Profile?,
    val media: List<PostMedia>?,
    @SerializedName("reaction_counts") val reactionCounts: ReactionCounts?,
    /** Access policy: free, subscription-only, or pay-per-post */
    @SerializedName("access_type") val accessType: String,
    /** Price in tokens for pay-per-post; null otherwise */
    val price: Int?,
    /** Required plan for tiered subscriber access; null means all plans */
    @SerializedName("required_plan_id") val requiredPlanId: String?,
    /** Hashtags associated with this post */
    val tags: List<Tag>?
)

/**
 * Media attached to a post (photo or video).
 */
data class PostMedia(
    val id: String,
    @SerializedName("post_id") val postId: String?,
    @SerializedName("media_type") val mediaType: String,
    val url: String,
    @SerializedName("order") val order: Int,
    @SerializedName("created_at") val createdAt: String?
)

/**
 * Reaction counts per post.
 */
data class ReactionCounts(
    val like: Int,
    val repost: Int,
    val share: Int
)

/**
 * Tag (hashtag) data for posts.
 */
data class Tag(
    val id: String,
    val name: String,
    @SerializedName("created_at") val createdAt: String?
)
