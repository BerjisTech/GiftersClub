package club.gifters.giftersclub.model

import com.google.gson.annotations.SerializedName

/**
 * Minimal wishlist item for counting open vs fulfilled entries.
 */
data class WishlistItem(
    val id: String,
    @SerializedName("is_fulfilled") val isFulfilled: Boolean
)