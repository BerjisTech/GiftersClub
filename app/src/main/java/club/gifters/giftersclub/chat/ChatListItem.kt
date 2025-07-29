package club.gifters.giftersclub.chat

import java.time.OffsetDateTime

/**
 * Unified list item for chat and notification headers.
 */
sealed class ChatListItem {
    /** Timestamp for sorting (epoch mills) */
    abstract val time: Long

    /** Header entry for static notification sections. */
    data class Header(
        val type: HeaderType,
        val title: String,
        val preview: String,
        override val time: Long
    ) : ChatListItem()

    /** Chat conversation entry. */
    data class Conversation(val ui: ConversationUi) : ChatListItem() {
        override val time: Long = try {
            OffsetDateTime.parse(ui.overview.lastMessageAt)
                .toInstant()
                .toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }

    /** Types for the header items. */
    enum class HeaderType { NEW_FOLLOWERS, ACTIVITY, SYSTEM_NOTIFICATIONS }
}