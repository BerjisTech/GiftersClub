package club.gifters.giftersclub.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a user report entry with nested profile information for the reported user.
 */
data class ReportedUserItem(
    @SerializedName("reported_user_id")
    val reportedUser: ProfileNested,
    val reason: String,
    val status: String,
    val created_at: String
)