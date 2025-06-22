package club.gifters.giftersclub.gifts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import club.gifters.giftersclub.R
import club.gifters.giftersclub.gifts.CommentReactionCounts
import club.gifters.giftersclub.gifts.Comment as Cmt

/**
 * Adapter to display comments with like/dislike and reply actions.
 */
class CommentAdapter(
    private val onReply: (Cmt) -> Unit,
    private val onLike: (Cmt) -> Unit,
    private val onDislike: (Cmt) -> Unit
) : ListAdapter<Cmt, CommentAdapter.CommentViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view, onReply, onLike, onDislike)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CommentViewHolder(
        itemView: View,
        private val onReply: (Cmt) -> Unit,
        private val onLike: (Cmt) -> Unit,
        private val onDislike: (Cmt) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvAuthor: TextView = itemView.findViewById(R.id.tvCommentAuthor)
        private val tvTime: TextView = itemView.findViewById(R.id.tvCommentTime)
        private val tvContent: TextView = itemView.findViewById(R.id.tvCommentContent)
        private val btnLike: ImageButton = itemView.findViewById(R.id.btnCommentLike)
        private val btnDislike: ImageButton = itemView.findViewById(R.id.btnCommentDislike)
        private val btnReply: ImageButton = itemView.findViewById(R.id.btnCommentReply)
        private val tvLikeCount: TextView = itemView.findViewById(R.id.tvCommentLikeCount)
        private val tvDislikeCount: TextView = itemView.findViewById(R.id.tvCommentDislikeCount)

        fun bind(c: Cmt) {
            // indent replies
            val params = (itemView.layoutParams as ViewGroup.MarginLayoutParams)
            params.marginStart = if (c.parentCommentId != null) 48 else 0
            itemView.layoutParams = params

            tvAuthor.text = c.profile?.username ?: ""
            tvTime.text = c.createdAt
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
}