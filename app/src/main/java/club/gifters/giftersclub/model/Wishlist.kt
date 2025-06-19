package club.gifters.giftersclub.model

import com.google.gson.annotations.SerializedName

/**
 * Wishlist belonging to a user.
 */
data class Wishlist(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val link: String,
    val name: String,
    val description: String,
    val image: String,
    val tokens: Int,
    @SerializedName("is_fulfilled") val isFulfilled: Boolean? = null,
    @SerializedName("contributors_count") val contributorsCount: Int? = null,
    @SerializedName("created_at") val createdAt: String? = null
)