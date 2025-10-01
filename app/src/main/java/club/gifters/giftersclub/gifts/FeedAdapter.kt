package club.gifters.giftersclub.gifts

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.GestureDetector
import android.view.MotionEvent
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
import club.gifters.giftersclub.model.LiveStream
import club.gifters.giftersclub.model.Post
import club.gifters.giftersclub.model.PostMedia
import club.gifters.giftersclub.social.SubscriptionApiHolder
import club.gifters.giftersclub.gifts.FollowApiHolder
import coil.load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import club.gifters.giftersclub.LiveKitConfig
import club.gifters.giftersclub.network.RetrofitClient
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalAudioTrackOptions
import io.livekit.android.room.track.LocalVideoTrackOptions
import io.livekit.android.room.track.RemoteVideoTrack
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

sealed class FeedItem {
    data class PostItem(val post: Post): FeedItem()
    data class LiveItem(val live: LiveStream): FeedItem()
}

class FeedAdapter(
    private val scope: CoroutineScope,
    private val onLike: (Post) -> Unit,
    private val onComment: (Post) -> Unit,
    private val onShare: (Post) -> Unit,
    private val onRepost: (Post) -> Unit,
    private val onProfileClick: (String) -> Unit,
    private val onLocked: (Post) -> Unit
): ListAdapter<FeedItem, RecyclerView.ViewHolder>(Diff) {

    companion object {
        private const val TYPE_POST = 0
        private const val TYPE_LIVE = 1
        private val Diff = object : DiffUtil.ItemCallback<FeedItem>() {
            override fun areItemsTheSame(old: FeedItem, new: FeedItem): Boolean = when {
                old is FeedItem.PostItem && new is FeedItem.PostItem -> old.post.id == new.post.id
                old is FeedItem.LiveItem && new is FeedItem.LiveItem -> old.live.id == new.live.id
                else -> false
            }
            override fun areContentsTheSame(old: FeedItem, new: FeedItem) = old == new
        }
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is FeedItem.PostItem -> TYPE_POST
        is FeedItem.LiveItem -> TYPE_LIVE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = when (viewType) {
        TYPE_POST -> PostVH(LayoutInflater.from(parent.context).inflate(R.layout.item_post, parent, false))
        TYPE_LIVE -> LiveVH(LayoutInflater.from(parent.context).inflate(R.layout.item_feed_live, parent, false))
        else -> throw IllegalArgumentException("unknown view type")
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is PostVH -> holder.bind((getItem(position) as FeedItem.PostItem).post)
            is LiveVH -> holder.bind((getItem(position) as FeedItem.LiveItem).live)
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        if (holder is PostVH) {
            holder.pauseAllVideos()
        } else if (holder is LiveVH) {
            holder.stopPreview()
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is LiveVH) {
            holder.stopPreview()
        }
    }

    inner class LiveVH(view: View): RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.tvLiveTitle)
        private val viewers: TextView = view.findViewById(R.id.tvViewerCount)
        private val host: TextView = view.findViewById(R.id.tvLiveHost)
        private val previewContainer: FrameLayout = view.findViewById(R.id.previewContainer)
        private val lockOverlay: FrameLayout = view.findViewById(R.id.liveLockOverlay)
        private val lockTitle: TextView = view.findViewById(R.id.tvLiveLockTitle)
        private val lockAction: TextView = view.findViewById(R.id.tvLiveLockAction)
        private val lockButton: MaterialButton = view.findViewById(R.id.btnLiveLockCta)
        private var preview: SurfaceViewRenderer? = null
        private var room: Room? = null
        private var bindJob: Job? = null
        private var eventsJob: Job? = null
        private var accessJob: Job? = null

        init {
            view.setOnClickListener {
                val item = (getItem(bindingAdapterPosition) as? FeedItem.LiveItem)?.live ?: return@setOnClickListener
                stopPreview()
                val ctx = itemView.context
                val uri = Uri.parse("https://gifters.club/live/${item.id}")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.setClassName(ctx, "club.gifters.giftersclub.live.LiveStreamActivity")
                ctx.startActivity(intent)
            }
            lockOverlay.setOnClickListener { view.performClick() }
            lockButton.setOnClickListener { view.performClick() }
        }

        fun bind(live: LiveStream) {
            accessJob?.cancel(); accessJob = null
            stopPreview()

            title.text = live.title
            viewers.text = "${live.viewerCount} watching"
            host.text = ""
            scope.launch(Dispatchers.IO) {
                try {
                    val prof = RetrofitClient.profileApi.getProfileByUserId("*", "eq.${live.hostId}").firstOrNull()
                    withContext(Dispatchers.Main) { host.text = prof?.username ?: prof?.name ?: "" }
                } catch (_: Exception) { }
            }

            val ctx = itemView.context
            val accessType = (live.accessType ?: "free").lowercase()
            val currentUser = AuthUtils.getCurrentUserId(ctx)
            val isHost = currentUser != null && currentUser == live.hostId
            val requiresAccess = !isHost && (accessType == "paid" || accessType == "subscription")

            if (requiresAccess) {
                showLockedOverlay(accessType)
                if (accessType == "subscription") {
                    val boundId = live.id
                    accessJob = scope.launch(Dispatchers.IO) {
                        val hasSub = try { SubscriptionApiHolder.hasSubscription(live.hostId) } catch (_: Exception) { false }
                        withContext(Dispatchers.Main) {
                            val current = (getItem(bindingAdapterPosition) as? FeedItem.LiveItem)?.live
                            if (current?.id != boundId) return@withContext
                            if (hasSub) {
                                hideLockedOverlay()
                                startPreview(live)
                            }
                        }
                    }
                }
            } else {
                hideLockedOverlay()
                startPreview(live)
            }
        }

        private fun showLockedOverlay(accessType: String) {
            lockOverlay.visibility = View.VISIBLE
            lockTitle.text = itemView.context.getString(R.string.access_required)
            if (accessType == "subscription") {
                lockAction.text = itemView.context.getString(R.string.live_paywall_feed_sub)
                lockButton.text = itemView.context.getString(R.string.subscribe)
            } else {
                lockAction.text = itemView.context.getString(R.string.live_paywall_feed_paid)
                lockButton.text = itemView.context.getString(R.string.unlock)
            }
            lockButton.isEnabled = true
        }

        private fun hideLockedOverlay() {
            lockOverlay.visibility = View.GONE
            lockButton.isEnabled = false
        }

        private fun startPreview(live: LiveStream) {
            stopPreview()
            val pv = SurfaceViewRenderer(itemView.context)
            pv.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            preview = pv
            previewContainer.removeAllViews()
            previewContainer.addView(pv)

            bindJob = scope.launch(Dispatchers.Main) {
                try {
                    val resp = withContext(Dispatchers.IO) { RetrofitClient.functionsApi.getLiveSession(live.id) }
                    if (!resp.isSuccessful) return@launch
                    val lkToken = resp.body()?.let { it.token ?: it.id }
                    if (lkToken.isNullOrBlank()) return@launch
                    val roomOptions = RoomOptions(
                        false,
                        false,
                        null,
                        LocalAudioTrackOptions(),
                        LocalVideoTrackOptions(),
                        null,
                        null
                    )
                    val r = LiveKit.create(itemView.context, roomOptions, LiveKitOverrides())
                    r.initVideoRenderer(pv)
                    room = r
                    withContext(Dispatchers.IO) {
                        r.connect(LiveKitConfig.WS_URL, lkToken, io.livekit.android.ConnectOptions())
                    }
                    r.remoteParticipants.values.forEach { p ->
                        p.videoTrackPublications.forEach { pubPair ->
                            (pubPair.second as? RemoteVideoTrack)?.addRenderer(pv)
                        }
                    }
                    eventsJob = scope.launch(Dispatchers.Main) {
                        r.events.collect { evt ->
                            if (evt is RoomEvent.TrackSubscribed && evt.track is RemoteVideoTrack) {
                                (evt.track as RemoteVideoTrack).addRenderer(pv)
                            }
                        }
                    }
                } catch (_: Exception) { }
            }
        }

        fun stopPreview() {
            accessJob?.cancel(); accessJob = null
            bindJob?.cancel(); bindJob = null
            eventsJob?.cancel(); eventsJob = null
            try { room?.disconnect() } catch (_: Exception) {}
            room = null
            previewContainer.removeAllViews()
            preview?.release(); preview = null
        }
    }

    inner class PostVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val avatar: ImageView = itemView.findViewById(R.id.avatarImage)
        private val username: TextView = itemView.findViewById(R.id.usernameText)
        private val timestamp: TextView = itemView.findViewById(R.id.timestampText)
        private val content: TextView = itemView.findViewById(R.id.contentText)
        private val mediaPager: ViewPager2 = itemView.findViewById(R.id.mediaPager)
        private val btnLike: TextView = itemView.findViewById(R.id.btnLike)
        private val tvLikeCount: TextView = itemView.findViewById(R.id.tvLikeCount)
        private val btnComment: TextView = itemView.findViewById(R.id.btnComment)
        private val tvCommentCount: TextView = itemView.findViewById(R.id.tvCommentCount)
        private val btnShare: TextView = itemView.findViewById(R.id.btnShare)
        private val btnRepost: TextView = itemView.findViewById(R.id.btnRepost)
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
        private val indicatorLayout: LinearLayout = itemView.findViewById(R.id.mediaIndicatorLayout)
        private val overlay: FrameLayout = itemView.findViewById(R.id.lockOverlay)
        private val lockActionText: TextView = itemView.findViewById(R.id.tvLockAction)
        private val lockButton: MaterialButton = itemView.findViewById(R.id.btnLockCta)
        private val postDetails: View = itemView.findViewById(R.id.postDetails)
        private val creatorAvatar: ShapeableImageView = itemView.findViewById(R.id.ivCreatorAvatar)
        private val creatorName: TextView = itemView.findViewById(R.id.tvCreatorName)
        private var accessJob: Job? = null

        init {
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
            // Reposter card: default hidden to avoid stale recycled state
            val reposterCard = itemView.findViewById<androidx.cardview.widget.CardView>(R.id.reposterDetailsCard)
            val reposterUsername = itemView.findViewById<TextView>(R.id.reposterUsername)
            reposterUsername.text = ""
            reposterCard.visibility = View.GONE

            current = post
            post.profile?.let { p ->
                username.text = p.username
                avatar.setOnClickListener {
                    pauseAllVideos()
                    onProfileClick(p.username)
                }
                username.setOnClickListener {
                    pauseAllVideos()
                    onProfileClick(p.username)
                }
                if (p.image.isNotBlank()) avatar.load(p.image) else avatar.setImageResource(android.R.color.darker_gray)
            }
            timestamp.text = formatRelativeTime(post.createdAt)
            content.text = post.content ?: ""
            makeHashtagsClickable(content)

            // Default to not-following icon, then resolve actual state
            isFollowingAuthor = false
            updateFollowIcon()

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
            // Paywall state handled below
            accessJob?.cancel()
            val mediaList = post.media ?: emptyList()
            val currentUserId = AuthUtils.getCurrentUserId(itemView.context)
            val accessType = (post.accessType ?: "free").lowercase()
            val requiresAccess = currentUserId != post.userId && (accessType == "subscription" || accessType == "paid")

            applyLockVisual(post, accessType, requiresAccess)
            configureMedia(mediaList, requiresAccess)

            if (requiresAccess) {
                accessJob = scope.launch {
                    val hasAccess = try {
                        when (accessType) {
                            "subscription" -> SubscriptionApiHolder.hasSubscription(post.userId)
                            "paid" -> SubscriptionApiHolder.hasPostAccess(post.id)
                            else -> true
                        }
                    } catch (_: Exception) { false }
                    withContext(Dispatchers.Main) {
                        val currentPost = current
                        if (currentPost != null && currentPost.id == post.id) {
                            val stillLocked = !hasAccess
                            applyLockVisual(post, accessType, stillLocked)
                            configureMedia(mediaList, stillLocked)
                        }
                    }
                }
            } else {
                accessJob = null
            }
            btnLike.setOnClickListener { onLike(post) }
            btnComment.setOnClickListener { onComment(post) }
            btnShare.setOnClickListener { onShare(post) }
            btnRepost.setOnClickListener { onRepost(post) }
            tvLikeCount.text = "0"
            tvShareCount.text = "0"
            val tvRepostCount = itemView.findViewById<TextView>(R.id.tvRepostCount)
            tvRepostCount.text = "0"
            tvCommentCount.text = "0"
            scope.launch {
                val likes = CommentApiHolder.getPostReactionCountValue(post.id, "like")
                tvLikeCount.text = likes.toString()
            }
            // Set like button text based on whether current user has liked this post
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
                val reposts = CommentApiHolder.getPostReactionCountValue(post.id, "repost")
                tvRepostCount.text = reposts.toString()
            }
            scope.launch {
                val comments = CommentApiHolder.getPostCommentCountValue(post.id)
                tvCommentCount.text = comments.toString()
            }

            // Reposter details card support (shown when backend includes reposter info in future)
            // If backend provides reposter user id, fetch username and show card
            post.reposterUserId?.takeIf { it.isNotBlank() }?.let { uid ->
                scope.launch {
                    try {
                        val prof = RetrofitClient.profileApi.getProfileByUserId("*", "eq.$uid").firstOrNull()
                        val uname = prof?.username?.takeIf { it.isNotBlank() }
                        if (!uname.isNullOrBlank()) {
                            reposterUsername.post {
                                reposterUsername.text = uname
                                reposterCard.visibility = View.VISIBLE
                                reposterCard.setOnClickListener { onProfileClick(uname) }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        fun pauseAllVideos() {
            val innerRv = mediaPager.getChildAt(0) as? RecyclerView ?: return
            for (i in 0 until innerRv.childCount) {
                val child = innerRv.getChildAt(i)
                val pv = child.findViewById<androidx.media3.ui.PlayerView>(R.id.mediaPlayerView)
                pv?.player?.playWhenReady = false
                pv?.player?.pause()
            }
        }

        private fun playVideoAt(index: Int) {
            if (overlay.visibility == View.VISIBLE) return
            val innerRv = mediaPager.getChildAt(0) as? RecyclerView ?: return
            val holder = innerRv.findViewHolderForAdapterPosition(index) as? PostMediaAdapter.MediaViewHolder
            if (holder != null) {
                holder.startPlayback()
                return
            }
            // Fallback to the first attached child when the targeted holder has not yet been laid out
            val child = innerRv.getChildAt(0) ?: return
            val pv = child.findViewById<androidx.media3.ui.PlayerView>(R.id.mediaPlayerView)
            pv?.player?.playWhenReady = true
            pv?.player?.play()
        }

        private fun configureMedia(mediaList: List<PostMedia>, locked: Boolean) {
            pageChangeCallback?.let { mediaPager.unregisterOnPageChangeCallback(it) }
            pageChangeCallback = null
            mediaPager.adapter = PostMediaAdapter(
                mediaList = mediaList,
                playOnHover = false,
                onVideoCompleted = null,
                locked = locked
            )
            mediaPager.visibility = View.VISIBLE
            postDetails.visibility = View.VISIBLE
            indicatorLayout.removeAllViews()
            if (mediaList.size <= 1) {
                indicatorLayout.visibility = View.GONE
            } else {
                indicatorLayout.visibility = View.VISIBLE
                val density = itemView.context.resources.displayMetrics.density
                mediaList.forEachIndexed { idx, _ ->
                    val dot = ImageView(itemView.context).apply {
                        setImageResource(if (idx == 0) R.drawable.dot_active else R.drawable.dot_inactive)
                        val size = (6 * density).toInt()
                        layoutParams = LinearLayout.LayoutParams(size, size).apply {
                            val margin = (4 * density).toInt()
                            marginStart = margin; marginEnd = margin
                        }
                    }
                    indicatorLayout.addView(dot)
                }
                val callback = object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        updateIndicatorSelection(position)
                        if (!locked) {
                            pauseAllVideos()
                            playVideoAt(position)
                        }
                    }
                }
                mediaPager.registerOnPageChangeCallback(callback)
                pageChangeCallback = callback
                updateIndicatorSelection(0)
            }
            if (locked) {
                pauseAllVideos()
            } else {
                pauseAllVideos()
                itemView.post {
                    if (isCurrentPagerItem()) {
                        val target = mediaPager.currentItem
                        playVideoAt(target)
                    }
                }
            }
        }

        private fun isCurrentPagerItem(): Boolean {
            val pos = bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return false
            val parentRv = itemView.parent as? RecyclerView ?: return false
            val parentPager = parentRv.parent as? ViewPager2 ?: return false
            return pos == parentPager.currentItem && parentPager.scrollState == ViewPager2.SCROLL_STATE_IDLE
        }

        private fun updateIndicatorSelection(position: Int) {
            for (i in 0 until indicatorLayout.childCount) {
                val dot = indicatorLayout.getChildAt(i) as ImageView
                dot.setImageResource(if (i == position) R.drawable.dot_active else R.drawable.dot_inactive)
            }
        }

        private fun applyLockVisual(post: Post, accessType: String, locked: Boolean) {
            if (locked) {
                overlay.visibility = View.VISIBLE
                val isSubscription = accessType == "subscription"
                lockActionText.text = if (isSubscription) itemView.context.getString(R.string.subscribe_to_creator) else itemView.context.getString(R.string.purchase_access)
                creatorName.text = "@" + (post.profile?.username ?: "")
                val avatarUrl = post.profile?.image.orEmpty()
                if (avatarUrl.isNotBlank()) {
                    creatorAvatar.load(avatarUrl)
                } else {
                    creatorAvatar.setImageResource(android.R.color.darker_gray)
                }
                lockButton.text = if (isSubscription) itemView.context.getString(R.string.subscribe) else itemView.context.getString(R.string.unlock)
                lockButton.isEnabled = true
                lockButton.setOnClickListener {
                    lockButton.isEnabled = false
                    lockActionText.text = if (isSubscription) itemView.context.getString(R.string.subscribing_ellipsis) else itemView.context.getString(R.string.purchasing_ellipsis)
                    onLocked(post)
                }
                pauseAllVideos()
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    val blur = android.graphics.RenderEffect.createBlurEffect(60f, 60f, android.graphics.Shader.TileMode.CLAMP)
                    mediaPager.setRenderEffect(blur)
                    postDetails.setRenderEffect(blur)
                }
            } else {
                overlay.visibility = View.GONE
                lockButton.setOnClickListener(null)
                lockButton.isEnabled = false
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    mediaPager.setRenderEffect(null)
                    postDetails.setRenderEffect(null)
                }
            }
        }
    }
    private fun makeHashtagsClickable(tv: TextView) {
        val text = tv.text?.toString() ?: return
        val spannable = android.text.SpannableString(text)
        val pattern = java.util.regex.Pattern.compile("#([A-Za-z0-9_]+)")
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            val tag = matcher.group(1) ?: continue
            val start = matcher.start()
            val end = matcher.end()
            val span = object : android.text.style.ClickableSpan() {
                override fun onClick(widget: android.view.View) {
                    val ctx = widget.context
                    val uri = Uri.parse("giftersclub://explore?query=%23$tag")
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: Exception) {
                        // Fallback to web URL if no handler for custom scheme
                        val web = Uri.parse("https://gifters.club/explore?query=%23$tag")
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, web))
                    }
                }
                override fun updateDrawState(ds: android.text.TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = false
                    ds.color = android.graphics.Color.CYAN
                }
            }
            spannable.setSpan(span, start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        tv.text = spannable
        tv.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        tv.highlightColor = android.graphics.Color.TRANSPARENT
    }

    private fun formatRelativeTime(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return try {
            val trimmed = iso.replace(Regex("\\.(\\\\d{3})\\\\d*"), ".$1")
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val then = sdf.parse(trimmed)?.time ?: return iso
            DateUtils.getRelativeTimeSpanString(
                then, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()
        } catch (_: Exception) {
            iso
        }
    }
}
