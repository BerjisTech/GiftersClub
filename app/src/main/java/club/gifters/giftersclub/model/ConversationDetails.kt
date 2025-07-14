package club.gifters.giftersclub.model

import com.google.gson.annotations.SerializedName

/**
 * Data class matching the conversation_details view for optimized chat sidebar.
 */
data class ConversationDetails(
    @SerializedName("user_a") val userA: String,
    @SerializedName("user_b") val userB: String,
    @SerializedName("last_message_at") val lastMessageAt: String,
    @SerializedName("partner_id") val partnerId: String,
    @SerializedName("partner_name") val partnerName: String?,
    @SerializedName("partner_image") val partnerImage: String?,
    @SerializedName("unread_count") val unreadCount: Int,
    @SerializedName("last_message_id") val lastMessageId: String?
)