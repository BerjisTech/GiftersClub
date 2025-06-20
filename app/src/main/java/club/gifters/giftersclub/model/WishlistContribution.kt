package club.gifters.giftersclub.model

import com.google.gson.annotations.SerializedName

/**
 * Contribution record for a wishlist.
 */
data class WishlistContribution(
    val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("contributor_id") val contributorId: String,
    @SerializedName("wishlist_id") val wishlistId: String,
    @SerializedName("gift_id") val giftId: String?,
    val tokens: Int,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)