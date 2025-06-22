package club.gifters.giftersclub.gifts

import com.google.gson.annotations.SerializedName

/**
 * Reaction (like or dislike) on a comment by a user.
 */
data class CommentReaction(
    val id: String,
    @SerializedName("comment_id") val commentId: String,
    @SerializedName("user_id") val userId: String,
    val type: String,
    @SerializedName("created_at") val createdAt: String
)