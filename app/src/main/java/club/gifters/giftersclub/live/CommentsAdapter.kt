package club.gifters.giftersclub.live

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.LiveStreamComment
import com.google.android.material.imageview.ShapeableImageView
import android.widget.TextView
import coil.load

/**
 * Adapter for displaying live stream comments in reverse (newest at bottom).
 */
class CommentsAdapter : ListAdapter<LiveStreamComment, CommentsAdapter.CommentViewHolder>(CommentDiff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_live_comment, parent, false)
        return CommentViewHolder(view as ViewGroup)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = getItem(position)
        holder.bind(comment)
    }

    class CommentViewHolder(private val container: ViewGroup) : RecyclerView.ViewHolder(container) {
        private val ivProfile: ShapeableImageView = container.findViewById(R.id.ivLiveCommentAvatar)
        private val tvName: TextView = container.findViewById(R.id.tvLiveCommentAuthor)
        private val tvBadge: TextView = container.findViewById(R.id.tvLiveGifterBadge)
        private val tvContent: TextView = container.findViewById(R.id.tvLiveCommentContent)
        fun bind(comment: LiveStreamComment) {
            val imageUrl = comment.profile?.image
            if (imageUrl.isNullOrEmpty()) {
                ivProfile.visibility = android.view.View.GONE
            } else {
                ivProfile.visibility = android.view.View.VISIBLE
                ivProfile.load(imageUrl)
            }
            tvName.text = comment.profile?.username ?: comment.userId
            val level = comment.profile?.gifterLevel
            if (level != null && level > 0) {
                tvBadge.visibility = android.view.View.VISIBLE
                tvBadge.text = "Lv $level"
            } else {
                tvBadge.visibility = android.view.View.GONE
            }
            tvContent.text = comment.content
        }
    }

    private object CommentDiff : DiffUtil.ItemCallback<LiveStreamComment>() {
        override fun areItemsTheSame(old: LiveStreamComment, new: LiveStreamComment): Boolean =
            old.id == new.id

        override fun areContentsTheSame(old: LiveStreamComment, new: LiveStreamComment): Boolean =
            old == new
    }
}
