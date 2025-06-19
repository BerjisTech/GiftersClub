package club.gifters.giftersclub.chat

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.Message

/**
 * Adapter for displaying chat messages in a RecyclerView.
 */
class MessageAdapter(
    private val currentUserId: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<Message>()

    companion object {
        private const val TYPE_SENT = 1
        private const val TYPE_RECEIVED = 2
    }

    fun submitList(list: List<Message>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    /** Append a new message to the end of the list. */
    fun addMessage(msg: Message) {
        items.add(msg)
        notifyItemInserted(items.size - 1)
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].senderId == currentUserId) TYPE_SENT else TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_SENT) {
            val view = inflater.inflate(R.layout.item_message_sent, parent, false)
            SentViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_message_received, parent, false)
            ReceivedViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = items[position]
        if (holder is SentViewHolder) holder.bind(msg)
        else if (holder is ReceivedViewHolder) holder.bind(msg)
    }

    override fun getItemCount(): Int = items.size

    private abstract class BaseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        protected val tvContent: TextView = view.findViewById(R.id.tvContent)
        protected val llAttachments: LinearLayout = view.findViewById(R.id.llAttachments)

        fun displayAttachments(msg: Message) {
            llAttachments.removeAllViews()
            msg.attachments?.forEach { attach ->
                if (attach.type == "image") {
                    val iv = ImageView(itemView.context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setPadding(0, 4, 0, 0)
                    }
                    iv.load(attach.url) { placeholder(android.R.color.darker_gray) }
                    llAttachments.addView(iv)
                } else {
                    val vv = VideoView(itemView.context).apply {
                        layoutParams = LinearLayout.LayoutParams(300, 300)
                        setVideoURI(Uri.parse(attach.url))
                        setPadding(0, 4, 0, 0)
                    }
                    llAttachments.addView(vv)
                }
            }
        }
    }

    private class SentViewHolder(view: View) : BaseViewHolder(view) {
        fun bind(msg: Message) {
            tvContent.text = msg.content
            displayAttachments(msg)
        }
    }

    private class ReceivedViewHolder(view: View) : BaseViewHolder(view) {
        fun bind(msg: Message) {
            tvContent.text = msg.content
            displayAttachments(msg)
        }
    }
}