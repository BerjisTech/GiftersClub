package club.gifters.giftersclub.model

import com.google.gson.annotations.SerializedName

/**
 * Attachment for chat messages (image or video).
 */
data class Attachment(
    val url: String,
    val type: String // "image" or "video"
)

/**
 * Message exchanged between users.
 */
data class Message(
    val id: String,
    @SerializedName("sender_id") val senderId: String,
    @SerializedName("receiver_id") val receiverId: String,
    val content: String,
    @SerializedName("created_at") val createdAt: String,
    val attachments: List<Attachment>? = null
)

/**
 * Overview of conversation between two users.
 */
data class ConversationOverview(
    @SerializedName("user_a") val userA: String,
    @SerializedName("user_b") val userB: String,
    @SerializedName("last_message_at") val lastMessageAt: String
)