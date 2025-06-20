package club.gifters.giftersclub.model

import com.google.gson.annotations.SerializedName

/**
 * Request body for creating a new wishlist.
 */
data class CreateWishlistRequest(
    @SerializedName("user_id") val userId: String,
    val name: String,
    val description: String,
    val link: String = "",
    val image: String = "",
    val tokens: Int,
    @SerializedName("is_fulfilled") val isFulfilled: Boolean = false
)