package club.gifters.giftersclub.gifts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import club.gifters.giftersclub.R
import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import club.gifters.giftersclub.gifts.CommentReactionCounts
import club.gifters.giftersclub.gifts.Comment as Cmt

/**
 * Adapter to display comments with like/dislike and reply actions.
 */
class CommentAdapter(
    private val onReply: (Cmt) -> Unit,
    private val onLike: (Cmt) -> Unit,
    private val onDislike: (Cmt) -> Unit,
    private val onProfileClick: (String) -> Unit
) : ListAdapter<Cmt, CommentAdapter.CommentViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view, onReply, onLike, onDislike, onProfileClick)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CommentViewHolder(
        itemView: View,
        private val onReply: (Cmt) -> Unit,
        private val onLike: (Cmt) -> Unit,
        private val onDislike: (Cmt) -> Unit,
        private val onProfileClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val ivAvatar: com.google.android.material.imageview.ShapeableImageView =
            itemView.findViewById(R.id.ivCommentAvatar)
        private val tvAuthor: TextView = itemView.findViewById(R.id.tvCommentAuthor)
        private val tvTime: TextView = itemView.findViewById(R.id.tvCommentTime)
        private val tvContent: TextView = itemView.findViewById(R.id.tvCommentContent)
        private val btnLike: TextView = itemView.findViewById(R.id.btnCommentLike)
        private val btnDislike: TextView = itemView.findViewById(R.id.btnCommentDislike)
        private val btnReply: TextView = itemView.findViewById(R.id.btnCommentReply)
        private val tvLikeCount: TextView = itemView.findViewById(R.id.tvCommentLikeCount)
        private val tvDislikeCount: TextView = itemView.findViewById(R.id.tvCommentDislikeCount)

        fun bind(c: Cmt) {
            // indent replies
            val params = (itemView.layoutParams as ViewGroup.MarginLayoutParams)
            params.marginStart = if (c.parentCommentId != null) 48 else 0
            itemView.layoutParams = params

            c.profile?.let { p ->
                tvAuthor.text = p.username
                if (p.image.isNotBlank()) {
                    ivAvatar.load(p.image) {
                        transformations(CircleCropTransformation())
                        placeholder(android.R.color.darker_gray)
                        error(android.R.color.darker_gray)
                    }
                } else {
                    ivAvatar.setImageResource(android.R.color.darker_gray)
                }
                ivAvatar.setOnClickListener { onProfileClick(p.username) }
                tvAuthor.setOnClickListener { onProfileClick(p.username) }
            }
            tvTime.text = formatRelativeTime(c.createdAt)
            tvContent.text = c.content
            tvLikeCount.text = c.reactionCounts?.like?.toString() ?: "0"
            tvDislikeCount.text = c.reactionCounts?.dislike?.toString() ?: "0"

            btnLike.setOnClickListener { onLike(c) }
            btnDislike.setOnClickListener { onDislike(c) }
            btnReply.setOnClickListener { onReply(c) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<Cmt>() {
        override fun areItemsTheSame(old: Cmt, new: Cmt) = old.id == new.id
        override fun areContentsTheSame(old: Cmt, new: Cmt) = old == new
    }

    private fun formatRelativeTime(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return try {
            val trimmed = iso.replace(Regex("\\.(\\d{3})\\d*"), ".$1")
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val then = sdf.parse(trimmed)?.time ?: return iso
            DateUtils.getRelativeTimeSpanString(
                then,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()
        } catch (_: Exception) {
            iso
        }
    }
}