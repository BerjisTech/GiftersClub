package club.gifters.giftersclub.gifts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.Post
import android.view.GestureDetector
import android.view.MotionEvent
import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Adapter for displaying posts in a vertical ViewPager2.
 */
class PostAdapter(
    private val onLike: (Post) -> Unit,
    private val onComment: (Post) -> Unit,
    private val onShare: (Post) -> Unit,
    private val onProfileClick: (String) -> Unit
) : ListAdapter<Post, PostAdapter.PostViewHolder>(PostDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view, onLike, onComment, onShare, onProfileClick)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PostViewHolder(
        itemView: View,
        private val onLike: (Post) -> Unit,
        private val onComment: (Post) -> Unit,
        private val onShare: (Post) -> Unit,
        private val onProfileClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val avatar: ImageView = itemView.findViewById(R.id.avatarImage)
        private val username: TextView = itemView.findViewById(R.id.usernameText)
        private val timestamp: TextView = itemView.findViewById(R.id.timestampText)
        private val content: TextView = itemView.findViewById(R.id.contentText)
        private val mediaPager: androidx.viewpager2.widget.ViewPager2 =
            itemView.findViewById(R.id.mediaPager)
        private val btnLike: ImageButton = itemView.findViewById(R.id.btnLike)
        private val btnComment: ImageButton = itemView.findViewById(R.id.btnComment)
        private val btnShare: ImageButton = itemView.findViewById(R.id.btnShare)
        private var current: Post? = null
        private val gestureDetector = GestureDetector(itemView.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    current?.let(onLike)
                    showHeart(e.x, e.y)
                    return true
                }
            }
        )
        init {
            itemView.setOnTouchListener { _, ev ->
                gestureDetector.onTouchEvent(ev)
                false
            }
        }

        private fun showHeart(xPos: Float, yPos: Float) {
            val size = (100 * itemView.context.resources.displayMetrics.density).toInt()
            val heart = ImageView(itemView.context).apply {
                setImageResource(R.drawable.ic_heart_red)
                layoutParams = ViewGroup.LayoutParams(size, size)
                scaleX = 0.3f
                scaleY = 0.3f
                alpha = 1f
                x = xPos - size / 2
                y = yPos - size / 2
            }
            (itemView as ViewGroup).addView(heart)
            heart.animate()
                .scaleX(1.5f).scaleY(1.5f)
                .alpha(0f)
                .setDuration(600)
                .withEndAction { (itemView as ViewGroup).removeView(heart) }
                .start()
        }

        fun bind(post: Post) {
            current = post
            post.profile?.let { p ->
                username.text = p.username
                // navigate to profile
                avatar.setOnClickListener { onProfileClick(p.username) }
                username.setOnClickListener { onProfileClick(p.username) }
                if (p.image.isNotBlank()) {
                    avatar.load(p.image) {
                        placeholder(android.R.color.darker_gray)
                        error(android.R.color.darker_gray)
                    }
                } else {
                    avatar.setImageResource(android.R.color.darker_gray)
                }
            }
            timestamp.text = formatRelativeTime(post.createdAt)
            content.text = post.content ?: ""
            // Setup media carousel (images/videos)
            mediaPager.adapter = PostMediaAdapter(post.media ?: emptyList())
            btnLike.setOnClickListener    { current?.let(onLike) }
            btnComment.setOnClickListener { current?.let(onComment) }
            btnShare.setOnClickListener   { current?.let(onShare) }
        }
    }

    class PostDiffCallback : DiffUtil.ItemCallback<Post>() {
        override fun areItemsTheSame(old: Post, new: Post) = old.id == new.id
        override fun areContentsTheSame(old: Post, new: Post) = old == new
    }

    private fun formatRelativeTime(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return try {
            // Trim fractional seconds to 3 digits and parse ISO offset
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