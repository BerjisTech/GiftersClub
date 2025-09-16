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
        private var preview: SurfaceViewRenderer? = null
        private var room: Room? = null
        private var bindJob: Job? = null
        private var eventsJob: Job? = null
        init {
            view.setOnClickListener {
                val item = (getItem(bindingAdapterPosition) as? FeedItem.LiveItem)?.live ?: return@setOnClickListener
                // Stop preview first to avoid overlapping audio
                stopPreview()
                val ctx = itemView.context
                val uri = Uri.parse("https://gifters.club/live/${item.id}")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.setClassName(ctx, "club.gifters.giftersclub.live.LiveStreamActivity")
                ctx.startActivity(intent)
            }
        }
        fun bind(live: LiveStream) {
            title.text = live.title
            viewers.text = "${live.viewerCount} watching"
            host.text = ""
            scope.launch(Dispatchers.IO) {
                try {
                    val prof = RetrofitClient.profileApi.getProfileByUserId("*", "eq.${live.hostId}").firstOrNull()
                    withContext(Dispatchers.Main) { host.text = prof?.username ?: prof?.name ?: "" }
                } catch (_: Exception) { }
            }
            startPreview(live)
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
            val overlay = itemView.findViewById<FrameLayout>(R.id.lockOverlay)
            val lockAction = itemView.findViewById<TextView>(R.id.tvLockAction)
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
                    "paid" -> SubscriptionApiHolder.hasPostAccess(post.id)
                    else -> true
                }
                if (!hasAccess) {
                    overlay.visibility = View.VISIBLE
                    lockAction.text = if (post.accessType == "subscription")
                        itemView.context.getString(R.string.subscribe_to_creator)
                    else
                        itemView.context.getString(R.string.purchase_access)
                    overlay.isEnabled = true
                    overlay.setOnClickListener {
                        lockAction.text = if (post.accessType == "subscription")
                            itemView.context.getString(R.string.subscribing_ellipsis)
                        else
                            itemView.context.getString(R.string.purchasing_ellipsis)
                        overlay.isEnabled = false
                        onLocked(post)
                    }
                } else {
                    mediaPager.visibility = View.VISIBLE
                    postDetails.visibility = View.VISIBLE
                    overlay.visibility = View.GONE
                }
            }
            val mediaList = post.media ?: emptyList()
            mediaPager.adapter = PostMediaAdapter(
                mediaList = mediaList,
                playOnHover = false
            )
            // Auto-control video playback based on inner page visibility
            pageChangeCallback?.let { mediaPager.unregisterOnPageChangeCallback(it) }
            val callback = object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    pauseAllVideos()
                    playVideoAt(position)
                }
            }
            mediaPager.registerOnPageChangeCallback(callback)
            pageChangeCallback = callback
            // Ensure only the first page’s video (if any) plays
            itemView.post { playVideoAt(0) }
            // Touch handling is done in init with directional logic to avoid blocking vertical feed scroll
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
            val innerRv = mediaPager.getChildAt(0) as? RecyclerView ?: return
            // Play only if the specified child is laid out
            val child = innerRv.getChildAt(index) ?: return
            val pv = child.findViewById<androidx.media3.ui.PlayerView>(R.id.mediaPlayerView)
            pv?.player?.playWhenReady = true
        }
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
