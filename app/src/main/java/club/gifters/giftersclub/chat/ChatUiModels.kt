package club.gifters.giftersclub.chat

import club.gifters.giftersclub.model.ConversationOverview
import club.gifters.giftersclub.model.Message
import club.gifters.giftersclub.model.Profile

/**
 * UI model combining overview, partner profile, last message, and unread count.
 */
data class ConversationUi(
    val overview: ConversationOverview,
    val partner: Profile,
    val lastMessage: Message?,
    val unreadCount: Int
)