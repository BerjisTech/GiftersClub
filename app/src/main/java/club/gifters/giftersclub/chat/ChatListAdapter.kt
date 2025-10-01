package club.gifters.giftersclub.chat

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import club.gifters.giftersclub.R
import club.gifters.giftersclub.chat.ChatListItem
import club.gifters.giftersclub.chat.ChatListItem.HeaderType
import club.gifters.giftersclub.chat.ConversationUi
import java.time.OffsetDateTime
import android.graphics.Typeface
import androidx.core.content.ContextCompat

/**
 * Adapter to display a merged list of chat conversations and notification headers,
 * sorted by timestamp (newest first).
 */
class ChatListAdapter(
    private val onHeaderClick: (HeaderType) -> Unit,
    private val onConversationClick: (ConversationUi) -> Unit
) : ListAdapter<ChatListItem, RecyclerView.ViewHolder>(Diff) {
    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_CONVERSATION = 1
        const val TYPE_EMPTY = 2
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ChatListItem.Header       -> TYPE_HEADER
        is ChatListItem.Conversation -> TYPE_CONVERSATION
        is ChatListItem.Empty        -> TYPE_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_conversation, parent, false)
                HeaderViewHolder(view, onHeaderClick)
            }
            TYPE_CONVERSATION -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_conversation, parent, false)
                ConversationViewHolder(view, onConversationClick)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_chat_empty, parent, false)
                EmptyViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ChatListItem.Header       -> (holder as HeaderViewHolder).bind(item)
            is ChatListItem.Conversation -> (holder as ConversationViewHolder).bind(item.ui)
            is ChatListItem.Empty        -> { /* no-op */ }
        }
    }

    private class HeaderViewHolder(
        itemView: View,
        private val onClick: (HeaderType) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val ivAvatar: ImageView = itemView.findViewById(R.id.ivConversationAvatar)
        private val tvName: TextView = itemView.findViewById(R.id.tvConversationName)
        private val tvPreview: TextView = itemView.findViewById(R.id.tvConversationPreview)
        private val tvTime: TextView = itemView.findViewById(R.id.tvConversationTime)
        private val tvUnread: TextView = itemView.findViewById(R.id.tvConversationUnread)

        fun bind(item: ChatListItem.Header) {
            itemView.setOnClickListener { onClick(item.type) }
            ivAvatar.setImageResource(
                when (item.type) {
                    HeaderType.NEW_FOLLOWERS -> android.R.drawable.ic_menu_share
                    HeaderType.ACTIVITY -> android.R.drawable.ic_menu_info_details
                    HeaderType.SYSTEM_NOTIFICATIONS -> android.R.drawable.ic_dialog_alert
                }
            )
            tvName.text = item.title
            tvPreview.text = item.preview
            tvUnread.visibility = View.GONE
            if (item.time > 0L) {
                tvTime.visibility = View.VISIBLE
                tvTime.text = DateUtils.getRelativeTimeSpanString(
                    item.time,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
            } else {
                tvTime.visibility = View.GONE
            }
        }
    }

    private class ConversationViewHolder(
        itemView: View,
        private val onClick: (ConversationUi) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val ivAvatar: ImageView = itemView.findViewById(R.id.ivConversationAvatar)
        private val tvName: TextView = itemView.findViewById(R.id.tvConversationName)
        private val tvPreview: TextView = itemView.findViewById(R.id.tvConversationPreview)
        private val tvTime: TextView = itemView.findViewById(R.id.tvConversationTime)
        private val tvUnread: TextView = itemView.findViewById(R.id.tvConversationUnread)
        private val defaultPreviewColor: Int = tvPreview.currentTextColor
        private val defaultPreviewTypeface = tvPreview.typeface

        private fun looksEncrypted(s: String?): Boolean =
            !s.isNullOrBlank() && s.trim().startsWith("{") && s.contains("\"ct\"")

        fun bind(ui: ConversationUi) {
            itemView.setOnClickListener { onClick(ui) }
            ivAvatar.load(ui.partner.image) { placeholder(android.R.color.darker_gray) }
            tvName.text = ui.partner.name ?: ui.partner.username
            val previewRaw = ui.lastMessage?.let {
                it.content.takeIf(String::isNotBlank)
                    ?: if (it.attachments?.firstOrNull()?.type == "image") "[PHOTO]" else "[VIDEO]"
            } ?: ""
            if (looksEncrypted(previewRaw)) {
                tvPreview.text = "${ui.partner.username}'s security keys changed"
                tvPreview.setTypeface(defaultPreviewTypeface, Typeface.ITALIC)
                try { tvPreview.setTextColor(ContextCompat.getColor(itemView.context, R.color.gray_500)) } catch (_: Exception) {}
            } else {
                tvPreview.typeface = defaultPreviewTypeface
                tvPreview.setTextColor(defaultPreviewColor)
                tvPreview.text = previewRaw
            }
            tvTime.text = try {
                val epoch = OffsetDateTime.parse(ui.overview.lastMessageAt)
                    .toInstant()
                    .toEpochMilli()
                DateUtils.getRelativeTimeSpanString(
                    epoch,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                )
            } catch (_: Exception) {
                ui.overview.lastMessageAt.substringBefore('T')
            }
            if (ui.unreadCount > 0) {
                tvUnread.visibility = View.VISIBLE
                tvUnread.text = ui.unreadCount.toString()
            } else {
                tvUnread.visibility = View.GONE
            }
        }
    }

    private class EmptyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    private object Diff : DiffUtil.ItemCallback<ChatListItem>() {
        override fun areItemsTheSame(old: ChatListItem, new: ChatListItem): Boolean = when {
            old is ChatListItem.Header       && new is ChatListItem.Header       -> old.type == new.type
            old is ChatListItem.Conversation && new is ChatListItem.Conversation -> old.ui.overview == new.ui.overview
            old is ChatListItem.Empty        && new is ChatListItem.Empty        -> true
            else -> false
        }

        override fun areContentsTheSame(old: ChatListItem, new: ChatListItem) = old == new
    }
}
