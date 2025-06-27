package club.gifters.giftersclub.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R

/**
 * Adapter to show static header items in the conversation list: new followers, activity, system notifications.
 */
class ChatHeaderAdapter(
    private val onClick: (HeaderType) -> Unit
) : RecyclerView.Adapter<ChatHeaderAdapter.ViewHolder>() {
    enum class HeaderType { NEW_FOLLOWERS, ACTIVITY, SYSTEM_NOTIFICATIONS }

    private val items = listOf(
        HeaderType.NEW_FOLLOWERS,
        HeaderType.ACTIVITY,
        HeaderType.SYSTEM_NOTIFICATIONS
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivAvatar: ImageView = itemView.findViewById(R.id.ivConversationAvatar)
        private val tvName: TextView = itemView.findViewById(R.id.tvConversationName)
        private val tvPreview: TextView = itemView.findViewById(R.id.tvConversationPreview)
        private val tvTime: TextView = itemView.findViewById(R.id.tvConversationTime)
        private val tvUnread: TextView = itemView.findViewById(R.id.tvConversationUnread)

        fun bind(type: HeaderType) {
            itemView.setOnClickListener { onClick(type) }
            tvTime.visibility = View.GONE
            tvUnread.visibility = View.GONE
            when (type) {
                HeaderType.NEW_FOLLOWERS -> {
                    ivAvatar.setImageResource(android.R.drawable.ic_menu_share)
                    tvName.text = itemView.context.getString(R.string.new_followers)
                    tvPreview.text = itemView.context.getString(R.string.new_followers_preview)
                }
                HeaderType.ACTIVITY -> {
                    ivAvatar.setImageResource(android.R.drawable.ic_menu_info_details)
                    tvName.text = itemView.context.getString(R.string.activity)
                    tvPreview.text = itemView.context.getString(R.string.activity_preview)
                }
                HeaderType.SYSTEM_NOTIFICATIONS -> {
                    ivAvatar.setImageResource(android.R.drawable.ic_dialog_alert)
                    tvName.text = itemView.context.getString(R.string.system_notifications)
                    tvPreview.text = itemView.context.getString(R.string.system_notifications_preview)
                }
            }
        }
    }
}