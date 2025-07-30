package club.gifters.giftersclub.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a blocked user entry with nested profile information.
 */
data class BlockedUserItem(
    @SerializedName("blocked_user_id")
    val blockedUser: ProfileNested
)