package club.gifters.giftersclub.gifts

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.view.LayoutInflater
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
import club.gifters.giftersclub.model.LiveStream
import club.gifters.giftersclub.model.Post
import club.gifters.giftersclub.social.SubscriptionApiHolder
import coil.load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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

    inner class LiveVH(view: View): RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.tvLiveTitle)
        private val viewers: TextView = view.findViewById(R.id.tvViewerCount)
        private val started: TextView = view.findViewById(R.id.tvStarted)
        init {
            view.setOnClickListener {
                val item = (getItem(bindingAdapterPosition) as? FeedItem.LiveItem)?.live ?: return@setOnClickListener
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
            started.text = live.startedAt?.let { formatRelativeTime(it) } ?: ""
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
        private val tvShareCount: TextView = itemView.findViewById(R.id.tvShareCount)
        private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null

        fun bind(post: Post) {
            post.profile?.let { p ->
                username.text = p.username
                avatar.setOnClickListener { onProfileClick(p.username) }
                username.setOnClickListener { onProfileClick(p.username) }
                if (p.image.isNotBlank()) avatar.load(p.image) else avatar.setImageResource(android.R.color.darker_gray)
            }
            timestamp.text = formatRelativeTime(post.createdAt)
            content.text = post.content ?: ""
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
                    overlay.setOnClickListener { onLocked(post) }
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
            // counts are loaded elsewhere in fragment; leave defaults
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

