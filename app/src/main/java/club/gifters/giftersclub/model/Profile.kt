package club.gifters.giftersclub.model

import com.google.gson.annotations.SerializedName

/**
 * Data class matching web Profile interface.
 */
data class Profile(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val email: String?,
    val username: String,
    val name: String?,
    val bio: String?,
    val image: String,
    @SerializedName("token_balance") val tokenBalance: Int?,
    @SerializedName("tokens_received") val tokensReceived: Int?,
    @SerializedName("tokens_sent") val tokensSent: Int?,
    @SerializedName("followers_count") val followersCount: Int?,
    @SerializedName("following_count") val followingCount: Int?,
    @SerializedName("is_following") val isFollowing: Boolean?,
    @SerializedName("gifter_level") val gifterLevel: Int?,
    @SerializedName("gifter_level_name") val gifterLevelName: String?,
    @SerializedName("gifts_sent") val giftsSent: Int?,
    @SerializedName("gifts_received") val giftsReceived: Int?
)