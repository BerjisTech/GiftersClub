package club.gifters.giftersclub.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a purchased access record for a post.
 */
data class PostAccess(
    @SerializedName("post_id") val postId: String,
    @SerializedName("user_id") val userId: String
)