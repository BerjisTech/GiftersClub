package club.gifters.giftersclub.model

import com.google.gson.annotations.SerializedName
import club.gifters.giftersclub.model.Profile
import club.gifters.giftersclub.model.PostMedia

/**
 * Post feed item with computed relevance score and engagement metrics.
 */
data class FeedPost(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val content: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("like_count") val likeCount: Int,
    @SerializedName("comment_count") val commentCount: Int,
    @SerializedName("share_count") val shareCount: Int,
    /** Combined score for ordering in the relevance-ranked feed */
    val score: Double,
    /** Access policy: free, subscription-only, or pay-per-post */
    @SerializedName("access_type") val accessType: String,
    /** Price in tokens for pay-per-post; null otherwise */
    val price: Int?,
    /** Author profile (id, user_id, username, image) */
    val profile: Profile?,
    /** Media attachments (id, media_type, url, order, created_at) */
    val media: List<PostMedia>?
)