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
import club.gifters.giftersclub.gifts.FollowApiHolder
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
        private val directFollow: ImageView = itemView.findViewById(R.id.directFollowUser)
        private var isFollowingAuthor: Boolean = false

        private fun updateFollowIcon() {
            directFollow.setImageResource(
                if (isFollowingAuthor) android.R.drawable.ic_menu_send else R.drawable.ic_plus_white
            )
        }
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
                var startX = 0f
                var startY = 0f
                (mediaPager.getChildAt(0) as? RecyclerView)?.setOnTouchListener { v, ev ->
                    doubleTap.onTouchEvent(ev)
                    when (ev.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = ev.x; startY = ev.y
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = kotlin.math.abs(ev.x - startX)
                            val dy = kotlin.math.abs(ev.y - startY)
                            v.parent?.requestDisallowInterceptTouchEvent(dx > dy)
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
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

            // Default icon while we resolve status
            isFollowingAuthor = false
            updateFollowIcon()

            // Hide follow for own posts; otherwise resolve follow status
            scope.launch {
                try {
                    val currentUser = AuthUtils.getCurrentUserId(itemView.context)
                    if (currentUser == post.userId) {
                        directFollow.visibility = View.GONE
                    } else {
                        directFollow.visibility = View.VISIBLE
                        isFollowingAuthor = FollowApiHolder.isFollowingUser(post.userId)
                        updateFollowIcon()
                    }
                } catch (_: Exception) { }
            }

            // Toggle follow/unfollow on tap
            directFollow.setOnClickListener {
                scope.launch {
                    try {
                        val ok = if (isFollowingAuthor)
                            FollowApiHolder.unfollowUser(post.userId)
                        else
                            FollowApiHolder.followUser(post.userId)
                        if (ok) {
                            isFollowingAuthor = !isFollowingAuthor
                            updateFollowIcon()
                        }
                    } catch (_: Exception) { }
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
                    // Show blurred media behind frosted overlay
                    mediaPager.visibility = View.VISIBLE
                    postDetails.visibility = View.VISIBLE
                    val isSub = post.accessType == "subscription"
                    lockAction.text = if (isSub)
                        itemView.context.getString(R.string.subscribe_to_creator)
                    else
                        itemView.context.getString(R.string.purchase_access)
                    itemView.findViewById<TextView>(R.id.tvCreatorName)?.text = "@" + (post.profile?.username ?: "")
                    val btn = itemView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnLockCta)
                    btn.text = if (isSub) itemView.context.getString(R.string.subscribe) else itemView.context.getString(R.string.unlock)
                    btn.isEnabled = true
                    btn.setOnClickListener {
                        btn.isEnabled = false
                        lockAction.text = if (isSub) itemView.context.getString(R.string.subscribing_ellipsis) else itemView.context.getString(R.string.purchasing_ellipsis)
                        onLocked(post)
                    }
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= 31) {
                            val blur = android.graphics.RenderEffect.createBlurEffect(24f, 24f, android.graphics.Shader.TileMode.CLAMP)
                            itemView.findViewById<View>(R.id.mediaPager)?.setRenderEffect(blur)
                            itemView.findViewById<View>(R.id.postDetails)?.setRenderEffect(blur)
                        }
                    } catch (_: Exception) {}
                } else {
                    mediaPager.visibility = View.VISIBLE
                    postDetails.visibility = View.VISIBLE
                    overlay.visibility = View.GONE
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= 31) {
                            itemView.findViewById<View>(R.id.mediaPager)?.setRenderEffect(null)
                            itemView.findViewById<View>(R.id.postDetails)?.setRenderEffect(null)
                        }
                    } catch (_: Exception) {}
                    if ((post.media ?: emptyList()).size > 1) {
                        indicatorLayout.visibility = View.VISIBLE
                    }
                }
            }
            // Setup media carousel (images/videos)
            val mediaList = post.media ?: emptyList()
            mediaPager.adapter = PostMediaAdapter(
                mediaList = mediaList,
                playOnHover = false
            )
            // Remove any extra onTouch overrides here; handled in init with directional logic
//            val indicatorLayout = itemView.findViewById<LinearLayout>(R.id.mediaIndicatorLayout)
            indicatorLayout.removeAllViews()
            if (mediaList.size <= 1) {
                indicatorLayout.visibility = View.GONE
            } else {
                // Defer visibility until post details are visible
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
            // Set like button text based on liked state
            scope.launch {
                try {
                    val liked = CommentApiHolder.isPostLikedByUser(post.id)
                    btnLike.text = itemView.context.getString(
                        if (liked) R.string._like_emoji_filled else R.string._like_emoji
                    )
                } catch (_: Exception) {
                    btnLike.text = itemView.context.getString(R.string._like_emoji)
                }
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
