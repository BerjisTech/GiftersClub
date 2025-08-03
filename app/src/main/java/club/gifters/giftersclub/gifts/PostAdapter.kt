package club.gifters.giftersclub.gifts

import android.text.format.DateUtils
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.Post
import club.gifters.giftersclub.social.SubscriptionApiHolder
import coil.load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Adapter for displaying posts in a vertical ViewPager2.
 */
class PostAdapter(
    private val scope: CoroutineScope,
    private val onLike: (Post) -> Unit,
    private val onComment: (Post) -> Unit,
    private val onShare: (Post) -> Unit,
    private val onProfileClick: (String) -> Unit,
    private val onLocked: (Post) -> Unit
) : ListAdapter<Post, PostAdapter.PostViewHolder>(PostDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post, parent, false)
        return PostViewHolder(view, scope, onLike, onComment, onShare, onProfileClick, onLocked)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PostViewHolder(
        itemView: View,
        private val scope: CoroutineScope,
        private val onLike: (Post) -> Unit,
        private val onComment: (Post) -> Unit,
        private val onShare: (Post) -> Unit,
        private val onProfileClick: (String) -> Unit,
        private val onLocked: (Post) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val avatar: ImageView = itemView.findViewById(R.id.avatarImage)
        private val username: TextView = itemView.findViewById(R.id.usernameText)
        private val timestamp: TextView = itemView.findViewById(R.id.timestampText)
        private val content: TextView = itemView.findViewById(R.id.contentText)
        private val mediaPager: androidx.viewpager2.widget.ViewPager2 =
            itemView.findViewById(R.id.mediaPager)
        private val btnLike: TextView = itemView.findViewById(R.id.btnLike)
        private val tvLikeCount: TextView = itemView.findViewById(R.id.tvLikeCount)
        private val btnComment: TextView = itemView.findViewById(R.id.btnComment)
        private val tvCommentCount: TextView = itemView.findViewById(R.id.tvCommentCount)
        private val btnShare: TextView = itemView.findViewById(R.id.btnShare)
        private val tvShareCount: TextView = itemView.findViewById(R.id.tvShareCount)
        private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null
        private var current: Post? = null
        private var startX = 0f
        private var startY = 0f
        private var isCarouselTouch = false
        private val postDetails: View = itemView.findViewById(R.id.postDetails)
        init {
            // Allow double-tap anywhere on the item to like/unlike
            val doubleTap = GestureDetector(itemView.context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        current?.let(onLike)
                        return true
                    }
                }
            )
            itemView.setOnTouchListener { _, ev ->
                doubleTap.onTouchEvent(ev)
                false
            }
            // Also intercept touches on the mediaPager (video/image area) for double-tap
            mediaPager.post {
                (mediaPager.getChildAt(0) as? RecyclerView)?.setOnTouchListener { _, ev ->
                    doubleTap.onTouchEvent(ev)
                    false
                }
            }
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
            // Gate subscription/paid posts: hide everything until access is checked
            val overlay = itemView.findViewById<FrameLayout>(R.id.lockOverlay)
            val lockAction = itemView.findViewById<TextView>(R.id.tvLockAction)
            val mediaPager = itemView.findViewById<ViewPager2>(R.id.mediaPager)
            val indicatorLayout = itemView.findViewById<LinearLayout>(R.id.mediaIndicatorLayout)
            val postDetails = itemView.findViewById<View>(R.id.postDetails)
            mediaPager.visibility = View.GONE
            indicatorLayout.visibility = View.GONE
            postDetails.visibility = View.GONE
            overlay.visibility = View.GONE
            scope.launch {
                val currentUser = AuthUtils.getCurrentUserId(itemView.context)
                val hasAccess = if (currentUser == post.userId) true else when (post.accessType) {
                    "subscription" -> SubscriptionApiHolder.hasSubscription(post.userId)
                    "paid"         -> SubscriptionApiHolder.hasPostAccess(post.id)
                    else            -> true
                }
                if (!hasAccess) {
                    overlay.visibility = View.VISIBLE
                    lockAction.text = if (post.accessType == "subscription")
                        itemView.context.getString(R.string.subscribe_to_creator)
                    else
                        itemView.context.getString(R.string.purchase_access)
                    overlay.setOnClickListener { onLocked(post) }
                } else {
                    mediaPager.visibility = View.VISIBLE
                    postDetails.visibility = View.VISIBLE
                    overlay.visibility = View.GONE
                }
            }
            // Setup media carousel (images/videos)
            val mediaList = post.media ?: emptyList()
            mediaPager.adapter = PostMediaAdapter(
                mediaList = mediaList,
                playOnHover = false
            )
//            val indicatorLayout = itemView.findViewById<LinearLayout>(R.id.mediaIndicatorLayout)
            indicatorLayout.removeAllViews()
            if (mediaList.size <= 1) {
                indicatorLayout.visibility = View.GONE
            } else {
                indicatorLayout.visibility = View.VISIBLE
                pageChangeCallback?.let { mediaPager.unregisterOnPageChangeCallback(it) }
                mediaList.forEachIndexed { idx, _ ->
                    val dot = ImageView(itemView.context).apply {
                        setImageResource(if (idx == 0) R.drawable.dot_active else R.drawable.dot_inactive)
                        val size = (6 * context.resources.displayMetrics.density).toInt()
                        val params = LinearLayout.LayoutParams(size, size).apply {
                            val margin = (4 * context.resources.displayMetrics.density).toInt()
                            marginStart = margin; marginEnd = margin
                        }
                        layoutParams = params
                    }
                    indicatorLayout.addView(dot)
                }
                val callback = object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        for (i in 0 until indicatorLayout.childCount) {
                            val iv = indicatorLayout.getChildAt(i) as ImageView
                            iv.setImageResource(
                                if (i == position) R.drawable.dot_active else R.drawable.dot_inactive
                            )
                        }
                    }
                }
                mediaPager.registerOnPageChangeCallback(callback)
                pageChangeCallback = callback
            }
            btnLike.setOnClickListener    { current?.let(onLike) }
            btnComment.setOnClickListener { current?.let(onComment) }
            btnShare.setOnClickListener   { current?.let(onShare) }
            // initialize counts to zero; will refresh via API
            tvLikeCount.text    = "0"
            tvShareCount.text   = "0"
            tvCommentCount.text = "0"
            // fetch live counts via Supabase
            scope.launch {
                val likes = CommentApiHolder.getPostReactionCountValue(post.id, "like")
                tvLikeCount.text = likes.toString()
            }
            scope.launch {
                val shares = CommentApiHolder.getPostReactionCountValue(post.id, "share")
                tvShareCount.text = shares.toString()
            }
            scope.launch {
                val comments = CommentApiHolder.getPostCommentCountValue(post.id)
                tvCommentCount.text = comments.toString()
            }
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