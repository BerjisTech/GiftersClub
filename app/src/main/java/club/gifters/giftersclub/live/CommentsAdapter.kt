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
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import androidx.core.content.ContextCompat
import android.graphics.drawable.Drawable
import android.util.TypedValue
import coil.load

/**
 * Adapter for displaying live stream comments in reverse (newest at bottom).
 */
class CommentsAdapter : ListAdapter<LiveStreamComment, CommentsAdapter.CommentViewHolder>(CommentDiff) {
    private val palette = intArrayOf(
        android.graphics.Color.parseColor("#F59E0B"),
        android.graphics.Color.parseColor("#10B981"),
        android.graphics.Color.parseColor("#3B82F6"),
        android.graphics.Color.parseColor("#EC4899"),
        android.graphics.Color.parseColor("#8B5CF6"),
        android.graphics.Color.parseColor("#EF4444"),
        android.graphics.Color.parseColor("#22C55E"),
        android.graphics.Color.parseColor("#06B6D4"),
        android.graphics.Color.parseColor("#F97316"),
        android.graphics.Color.parseColor("#84CC16"),
        android.graphics.Color.parseColor("#EAB308"),
        android.graphics.Color.parseColor("#2DD4BF"),
        android.graphics.Color.parseColor("#60A5FA"),
        android.graphics.Color.parseColor("#F472B6"),
        android.graphics.Color.parseColor("#A78BFA"),
        android.graphics.Color.parseColor("#34D399"),
        android.graphics.Color.parseColor("#FB7185"),
        android.graphics.Color.parseColor("#38BDF8"),
        android.graphics.Color.parseColor("#F43F5E"),
        android.graphics.Color.parseColor("#14B8A6")
    )

    private fun colorForUser(id: String?): Int {
        if (id.isNullOrEmpty()) return android.graphics.Color.WHITE
        val idx = (id.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }) % palette.size
        return palette[idx]
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_live_comment, parent, false)
        return CommentViewHolder(view as ViewGroup)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = getItem(position)
        holder.bind(comment)
    }

    inner class CommentViewHolder(private val container: ViewGroup) : RecyclerView.ViewHolder(container) {
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
            tvName.setTextColor(colorForUser(comment.userId))
            val level = comment.profile?.gifterLevel
            if (level != null && level > 0) {
                tvBadge.visibility = android.view.View.VISIBLE
                tvBadge.text = "Lv $level"
            } else {
                tvBadge.visibility = android.view.View.GONE
            }
            tvContent.text = decorateGiftMessage(container, comment.content)
        }
    }

    private object CommentDiff : DiffUtil.ItemCallback<LiveStreamComment>() {
        override fun areItemsTheSame(old: LiveStreamComment, new: LiveStreamComment): Boolean =
            old.id == new.id

        override fun areContentsTheSame(old: LiveStreamComment, new: LiveStreamComment): Boolean =
            old == new
    }

    /** Append a synthetic comment (e.g., gift notification) to the end of the list */
    fun addSyntheticComment(userId: String, content: String, profile: club.gifters.giftersclub.model.Profile? = null) {
        val current = currentList.toMutableList()
        val synthetic = LiveStreamComment(
            id = "gift-" + System.currentTimeMillis(),
            liveStreamId = current.lastOrNull()?.liveStreamId ?: "",
            parentCommentId = null,
            userId = userId,
            content = content,
            createdAt = java.time.Instant.now().toString(),
            profile = profile
        )
        current.add(synthetic)
        submitList(current)
    }

    /** Update content at index (for combo updates) */
    fun updateContentAt(index: Int, newContent: String) {
        if (index < 0 || index >= currentList.size) return
        val current = currentList.toMutableList()
        val old = current[index]
        current[index] = old.copy(content = newContent)
        submitList(current)
    }

    private fun dpToPx(dp: Float, view: ViewGroup): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            view.resources.displayMetrics
        ).toInt()
    }

    private fun giftEmojiFor(name: String): String? {
        val lower = name.lowercase()
        return when {
            "rose" in lower -> "🌹"
            "heart" in lower -> "❤️"
            "diamond" in lower -> "💎"
            "star" in lower -> "⭐"
            "rocket" in lower -> "🚀"
            "cake" in lower -> "🎂"
            "coffee" in lower -> "☕"
            "crown" in lower -> "👑"
            "kiss" in lower -> "💋"
            "fire" in lower -> "🔥"
            "balloon" in lower -> "🎈"
            "flower" in lower -> "🌸"
            "teddy" in lower -> "🧸"
            "car" in lower -> "🚗"
            "yacht" in lower -> "🛥️"
            "castle" in lower -> "🏰"
            else -> null
        }
    }

    private fun extractGiftName(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val t = text.trim()
        // sent 3 Rose combo
        Regex("sent\\s+\\d+\\s+(.+?)\\s+combo", RegexOption.IGNORE_CASE).find(t)?.let {
            return it.groupValues.getOrNull(1)?.trim()
        }
        // sent a Rose / sent an Apple
        Regex("sent\\s+a?n?\\s+(.+)", RegexOption.IGNORE_CASE).find(t)?.let {
            return it.groupValues.getOrNull(1)?.trim()
        }
        return null
    }

    /**
     * Decorate gift notification messages by appending a small image (if drawable exists)
     * or an emoji fallback.
     */
    private fun decorateGiftMessage(container: ViewGroup, text: String?): CharSequence {
        val base = text ?: return ""
        val name = extractGiftName(base)
        if (name.isNullOrBlank()) return base
        // Try to find a drawable by resource name
        val resName = name.lowercase().replace(' ', '_')
        val ctx = container.context
        val resId = ctx.resources.getIdentifier(resName, "drawable", ctx.packageName)
        val sb = SpannableStringBuilder(base)
        sb.append(" ")
        if (resId != 0) {
            val drawable: Drawable? = ContextCompat.getDrawable(ctx, resId)
            if (drawable != null) {
                val size = dpToPx(16f, container)
                drawable.setBounds(0, 0, size, size)
                val span = ImageSpan(drawable, ImageSpan.ALIGN_BOTTOM)
                val start = sb.length
                sb.append(" ")
                sb.setSpan(span, start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                return sb
            }
        }
        // Fallback to emoji mapping
        giftEmojiFor(name)?.let { emoji ->
            sb.append(emoji)
            return sb
        }
        return base
    }
}
