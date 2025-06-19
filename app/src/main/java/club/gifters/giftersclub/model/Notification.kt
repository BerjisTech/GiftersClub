package club.gifters.giftersclub.model

import com.google.gson.annotations.SerializedName

/**
 * Notification for activities such as gifts, follows, mentions, etc.
 */
data class Notification(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val type: String,
    @SerializedName("reference_id") val referenceId: String?,
    val message: String,
    @SerializedName("is_read") var isRead: Boolean,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String?,
    @SerializedName("sender_id") val senderId: String?
)