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
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view as ViewGroup)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = getItem(position)
        holder.bind(comment)
    }

    class CommentViewHolder(private val container: ViewGroup) : RecyclerView.ViewHolder(container) {
        private val ivProfile: ShapeableImageView = container.findViewById(R.id.ivCommentAvatar)
        private val tvName: TextView = container.findViewById(R.id.tvCommentAuthor)
        private val tvContent: TextView = container.findViewById(R.id.tvCommentContent)
        fun bind(comment: LiveStreamComment) {
            ivProfile.load(comment.profile?.image)
            tvName.text = comment.profile?.username ?: comment.userId
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