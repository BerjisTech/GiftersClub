package club.gifters.giftersclub.chat

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
import club.gifters.giftersclub.chat.ConversationUi
import club.gifters.giftersclub.model.Message

/**
 * Adapter for the conversation list.
 */
class ConversationAdapter(
    private val onClick: (ConversationUi) -> Unit
) : ListAdapter<ConversationUi, ConversationAdapter.ConversationViewHolder>(ConversationDiff)
{
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ConversationViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ConversationViewHolder(
        itemView: View,
        private val onClick: (ConversationUi) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val ivAvatar: ImageView = itemView.findViewById(R.id.ivAvatar)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvLastMessage: TextView = itemView.findViewById(R.id.tvLastMessage)
        private val tvUnreadCount: TextView = itemView.findViewById(R.id.tvUnreadCount)
        private var current: ConversationUi? = null

        init {
            itemView.setOnClickListener {
                current?.let(onClick)
            }
        }

        fun bind(item: ConversationUi) {
            current = item
            val partner = item.partner
            tvName.text = partner.name
            if (partner.image.isNotBlank()) {
                ivAvatar.load(partner.image) { placeholder(android.R.color.darker_gray); error(android.R.color.darker_gray) }
            } else {
                ivAvatar.setImageResource(android.R.color.darker_gray)
            }
            tvLastMessage.text = previewMessage(item.lastMessage)
            val time = item.lastMessage?.createdAt ?: item.overview.lastMessageAt
            tvTimestamp.text = formatRelativeTime(time)
            if (item.unreadCount > 0) {
                tvUnreadCount.visibility = View.VISIBLE
                tvUnreadCount.text = item.unreadCount.toString()
            } else {
                tvUnreadCount.visibility = View.GONE
            }
        }

        private fun previewMessage(msg: Message?): String {
            msg ?: return ""
            if (msg.content.isNotBlank()) return msg.content
            msg.attachments?.firstOrNull()?.let {
                return if (it.type == "image") "[PHOTO]" else "[VIDEO]"
            }
            return ""
        }

        private fun formatRelativeTime(iso: String?): String {
            if (iso.isNullOrBlank()) return ""
            return try {
                val trimmed = iso.replace(Regex("\\.(\\d{3})\\d*"), ".$1")
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val then = sdf.parse(trimmed)?.time ?: return iso
                android.text.format.DateUtils.getRelativeTimeSpanString(
                    then, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS,
                    android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE
                ).toString()
            } catch (e: Exception) {
                iso
            }
        }
    }

    object ConversationDiff : DiffUtil.ItemCallback<ConversationUi>() {
        override fun areItemsTheSame(old: ConversationUi, new: ConversationUi) =
            old.overview.userA == new.overview.userA && old.overview.userB == new.overview.userB

        override fun areContentsTheSame(old: ConversationUi, new: ConversationUi) = old == new
    }
}