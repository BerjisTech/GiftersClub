package club.gifters.giftersclub.chat

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.Notification

/**
 * Adapter for displaying a list of notifications.
 */
class NotificationAdapter : ListAdapter<Notification, NotificationAdapter.NotificationViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val tvCreatedAt: TextView = itemView.findViewById(R.id.tvCreatedAt)

        fun bind(note: Notification) {
            tvMessage.text = note.message
            tvCreatedAt.text = formatRelativeTime(note.createdAt)
        }

        private fun formatRelativeTime(iso: String?): String {
            if (iso.isNullOrBlank()) return ""
            return try {
                val trimmed = iso.replace(Regex("\\.(\\d{3})\\d*"), ".$1")
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val then = sdf.parse(trimmed)?.time ?: return iso
                DateUtils.getRelativeTimeSpanString(
                    then, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                ).toString()
            } catch (_: Exception) {
                iso
            }
        }
    }

    object Diff : DiffUtil.ItemCallback<Notification>() {
        override fun areItemsTheSame(old: Notification, new: Notification) = old.id == new.id
        override fun areContentsTheSame(old: Notification, new: Notification) = old == new
    }
}