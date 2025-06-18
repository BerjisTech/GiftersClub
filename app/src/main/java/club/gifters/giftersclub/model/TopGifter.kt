package club.gifters.giftersclub.model

import com.google.gson.annotations.SerializedName

/**
 * Data class matching web TopGifter interface and view 'top_gifters'.
 */
data class TopGifter(
    @SerializedName("user_id") val userId: String,
    val username: String,
    val image: String,
    @SerializedName("gifts_sent") val giftsSent: Int,
    @SerializedName("tokens_sent") val tokensSent: Int,
    @SerializedName("gifter_level") val gifterLevel: Int,
    @SerializedName("gifter_level_name") val gifterLevelName: String,
    @SerializedName("largest_gift_name") val largestGiftName: String,
    @SerializedName("largest_gift_id") val largestGiftId: String,
    @SerializedName("largest_gift_color") val largestGiftColor: String,
    @SerializedName("largest_gift_tokens") val largestGiftTokens: Int,
    val badge: String
)