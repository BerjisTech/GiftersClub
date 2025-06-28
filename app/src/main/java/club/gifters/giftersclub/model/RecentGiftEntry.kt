package club.gifters.giftersclub.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a row in the recent_gifts view for gifts received by the current user.
 */
data class RecentGiftEntry(
    val id: String,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("gifter_id") val gifterId: String?,
    @SerializedName("gifter_username") val gifterUsername: String?,
    @SerializedName("gifter_image") val gifterImage: String?
)