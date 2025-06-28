package club.gifters.giftersclub.gifts

import club.gifters.giftersclub.model.Profile
import club.gifters.giftersclub.model.Wishlist
import com.google.gson.annotations.SerializedName
import club.gifters.giftersclub.model.WishlistContribution

/**
 * Data model representing a wishlist joined with its owner's profile.
 */
data class WishlistWithOwner(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val link: String,
    val name: String,
    val description: String,
    val image: String,
    val tokens: Int,
    @SerializedName("is_fulfilled") val isFulfilled: Boolean? = null,
    @SerializedName("contributors_count") val contributorsCount: Int? = null,
    @SerializedName("created_at")           val createdAt: String? = null,
    @SerializedName("wishlist_contributions") val contributions: List<WishlistContribution>? = null,
    val profile: Profile
) {
    /** Convert to plain Wishlist model for adapter consumption. */
    fun toWishlist() = Wishlist(
        id = id,
        userId = userId,
        link = link,
        name = name,
        description = description,
        image = image,
        tokens = tokens,
        isFulfilled = isFulfilled ?: false,
        contributorsCount   = contributorsCount,
        createdAt           = createdAt,
        tokensContributed   = contributions?.sumOf { it.tokens } ?: 0
    )
}