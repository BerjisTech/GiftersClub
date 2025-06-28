package club.gifters.giftersclub.gifts

import com.google.gson.annotations.SerializedName

/**
 * Reaction (like/share) on a post by a user.
 */
data class PostReaction(
    val id: String,
    @SerializedName("post_id") val postId: String,
    @SerializedName("user_id") val userId: String,
    val type: String,
    @SerializedName("created_at") val createdAt: String
)