package club.gifters.giftersclub.model

import com.google.gson.annotations.SerializedName

/**
 * Represents an entry in the follows table linking follower and followed user IDs.
 */
data class FollowsEntry(
    @SerializedName("follower_id") val followerId: String?,
    @SerializedName("followed_id") val followedId: String?
)