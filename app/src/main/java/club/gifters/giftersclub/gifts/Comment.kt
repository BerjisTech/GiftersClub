package club.gifters.giftersclub.gifts

import club.gifters.giftersclub.model.Profile
import com.google.gson.annotations.SerializedName

/**
 * Comment on a post, with optional nested replies and reaction counts.
 */
data class Comment(
    val id: String,
    @SerializedName("post_id") val postId: String,
    @SerializedName("parent_comment_id") val parentCommentId: String?,
    @SerializedName("user_id") val userId: String,
    val content: String,
    @SerializedName("created_at") val createdAt: String,
    val profile: Profile?,
    @SerializedName("reaction_counts") var reactionCounts: CommentReactionCounts?,
    var replies: List<Comment>?
)