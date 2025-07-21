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
import java.time.OffsetDateTime
import club.gifters.giftersclub.R

/**
 * Adapter to display conversation overviews.
 */
class ConversationAdapter(
    private val currentUserId: String,
    private val onClick: (ConversationUi) -> Unit
) : ListAdapter<ConversationUi, ConversationAdapter.ViewHolder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), currentUserId)
    }

    class ViewHolder(
        itemView: View,
        private val onClick: (ConversationUi) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val ivAvatar: ImageView = itemView.findViewById(R.id.ivConversationAvatar)
        private val tvName: TextView = itemView.findViewById(R.id.tvConversationName)
        private val tvPreview: TextView = itemView.findViewById(R.id.tvConversationPreview)
        private val tvTime: TextView = itemView.findViewById(R.id.tvConversationTime)
        private val tvUnread: TextView = itemView.findViewById(R.id.tvConversationUnread)

        fun bind(item: ConversationUi, currentUserId: String) {
            itemView.setOnClickListener { onClick(item) }
            ivAvatar.load(item.partner.image) {
                placeholder(android.R.color.darker_gray)
            }
            tvName.text = item.partner.name ?: item.partner.username
            tvPreview.text = item.lastMessage?.let {
                it.content.takeIf(String::isNotBlank)
                    ?: if (it.attachments?.firstOrNull()?.type == "image") "[PHOTO]" else "[VIDEO]"
            } ?: ""
            // relative time
            val ts = item.overview.lastMessageAt.let { raw ->
                try {
                    DateUtils.getRelativeTimeSpanString(
                        OffsetDateTime.parse(raw).toInstant().toEpochMilli(),
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS
                    )
                } catch (_: Exception) {
                    raw.substringBefore('T')
                }
            }
            tvTime.text = ts
            if (item.unreadCount > 0) {
                tvUnread.visibility = View.VISIBLE
                tvUnread.text = item.unreadCount.toString()
            } else {
                tvUnread.visibility = View.GONE
            }
        }
    }

    object Diff : DiffUtil.ItemCallback<ConversationUi>() {
        override fun areItemsTheSame(old: ConversationUi, new: ConversationUi) =
            old.overview == new.overview

        override fun areContentsTheSame(old: ConversationUi, new: ConversationUi) = old == new
    }
}