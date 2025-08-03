package club.gifters.giftersclub.chat

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.Message
import coil.load
import java.time.OffsetDateTime
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

private fun relativeTimeAgo(isoTime: String): String {
    val timeMillis = try {
        OffsetDateTime.parse(isoTime).toInstant().toEpochMilli()
    } catch (e: Exception) {
        return ""
    }
    val now = System.currentTimeMillis()
    val diff = now - timeMillis
    val seconds = TimeUnit.MILLISECONDS.toSeconds(diff)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    val weeks = days / 7
    val months = days / 30
    val years = days / 365
    return when {
        seconds < 60 -> "${seconds}s ago"
        minutes < 60 -> "${minutes}min ago"
        hours < 24 -> "${hours}hours ago"
        days < 7 -> "${days} days ago"
        weeks < 4 -> "${weeks} weeks ago"
        months < 12 -> "${months} months ago"
        else -> "${years} years ago"
    }
}

/**
 * Adapter for displaying chat messages and date headers in a RecyclerView.
 */
class MessageAdapter(
    private val currentUserId: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class ChatItem {
        data class DateHeader(val label: String) : ChatItem()
        data class Msg(val message: Message) : ChatItem()
    }

    companion object {
        private const val TYPE_DATE_HEADER = 0
        private const val TYPE_SENT = 1
        private const val TYPE_RECEIVED = 2
    }

    private val items = mutableListOf<ChatItem>()

    /** Replace entire chat list, building date headers. */
    fun submitList(list: List<Message>) {
        items.clear()
        items.addAll(buildChatItems(list))
        notifyDataSetChanged()
    }

    /** Append a new message and re-group headers. */
    fun addMessage(msg: Message) {
        val currentMsgs = items.filterIsInstance<ChatItem.Msg>().map { it.message } + msg
        submitList(currentMsgs)
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is ChatItem.DateHeader -> TYPE_DATE_HEADER
        is ChatItem.Msg -> {
            val message = (items[position] as ChatItem.Msg).message
            if (message.senderId == currentUserId) TYPE_SENT else TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_DATE_HEADER -> {
                val view = inflater.inflate(R.layout.item_date_header, parent, false)
                DateHeaderViewHolder(view)
            }
            TYPE_SENT -> {
                val view = inflater.inflate(R.layout.item_message_sent, parent, false)
                SentViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_message_received, parent, false)
                ReceivedViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ChatItem.DateHeader -> (holder as DateHeaderViewHolder).bind(item.label)
            is ChatItem.Msg -> if (holder is SentViewHolder) holder.bind(item.message)
                           else if (holder is ReceivedViewHolder) holder.bind(item.message)
        }
    }

    override fun getItemCount(): Int = items.size

    /** Construct a mixed list of date headers and messages for day grouping. */
    private fun buildChatItems(list: List<Message>): List<ChatItem> {
        val result = mutableListOf<ChatItem>()
        var lastDate: java.time.LocalDate? = null
        val today = java.time.LocalDate.now()
        for (msg in list) {
            val date = java.time.OffsetDateTime.parse(msg.createdAt).toLocalDate()
            if (lastDate == null || date != lastDate) {
                val days = java.time.temporal.ChronoUnit.DAYS.between(date, today).toInt()
                val label = when (days) {
                    0 -> "Today"
                    1 -> "Yesterday"
                    else -> "$days days ago"
                }
                result.add(ChatItem.DateHeader(label))
                lastDate = date
            }
            result.add(ChatItem.Msg(msg))
        }
        return result
    }

    private abstract class BaseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        protected val tvContent: TextView = view.findViewById(R.id.tvContent)
        protected val llAttachments: LinearLayout = view.findViewById(R.id.llAttachments)
        protected val tvTimestamp: TextView = view.findViewById(R.id.tvTimestamp)

        init {
            val metrics = itemView.context.resources.displayMetrics
            val maxBubbleWidth = (metrics.widthPixels * 0.6f).toInt()
            tvContent.maxWidth = maxBubbleWidth
        }

        fun displayAttachments(msg: Message) {
            llAttachments.removeAllViews()
            val metrics = itemView.context.resources.displayMetrics
            val maxBubbleWidth = (metrics.widthPixels * 0.6f).toInt()
            val maxHeight = (300 * metrics.density).toInt()
            msg.attachments?.forEach { attach ->
                if (attach.type == "image") {
                    val iv = ImageView(itemView.context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            maxBubbleWidth,
                            maxHeight
                        )
                        adjustViewBounds = true
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setPadding(0, 4, 0, 0)
                        setOnClickListener {
                            val ctx = itemView.context
                            val intent = Intent(ctx, FullscreenMediaActivity::class.java).apply {
                                putExtra("url", attach.url)
                                putExtra("type", "image")
                            }
                            ctx.startActivity(intent)
                        }
                    }
                    iv.load(attach.url) { placeholder(android.R.color.darker_gray) }
                    llAttachments.addView(iv)
                } else {
                    val vv = VideoView(itemView.context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            maxBubbleWidth,
                            maxHeight
                        )
                        setPadding(0, 4, 0, 0)
                        setOnPreparedListener { mp -> mp.isLooping = true }
                        setOnClickListener {
                            val ctx = itemView.context
                            val intent = Intent(ctx, FullscreenMediaActivity::class.java).apply {
                                putExtra("url", attach.url)
                                putExtra("type", "video")
                            }
                            ctx.startActivity(intent)
                        }
                    }
                    vv.setVideoURI(Uri.parse(attach.url))
                    llAttachments.addView(vv)
                }
            }
        }
    }

    private class SentViewHolder(view: View) : BaseViewHolder(view) {
        fun bind(msg: Message) {
            tvContent.text = msg.content
            displayAttachments(msg)
            tvTimestamp.text = relativeTimeAgo(msg.createdAt)
        }
    }

    private class ReceivedViewHolder(view: View) : BaseViewHolder(view) {
        fun bind(msg: Message) {
            tvContent.text = msg.content
            displayAttachments(msg)
            tvTimestamp.text = relativeTimeAgo(msg.createdAt)
        }
    }

    /** ViewHolder for date headers inserted between messages. */
    private class DateHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tv: TextView = view.findViewById(R.id.tvDateHeader)
        fun bind(label: String) {
            tv.text = label
        }
    }
}