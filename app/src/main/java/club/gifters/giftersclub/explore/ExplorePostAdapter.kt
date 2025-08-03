package club.gifters.giftersclub.explore

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import club.gifters.giftersclub.R
import club.gifters.giftersclub.gifts.PostMediaAdapter
import club.gifters.giftersclub.model.Post
import coil.load
import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.social.SubscriptionApiHolder
import android.widget.FrameLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Adapter for showing post search results in Explore.
 */
class ExplorePostAdapter(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val onPostClick: (List<Post>, Int) -> Unit,
    private val onLocked: (Post) -> Unit,
    private val onVideoComplete: ((Int) -> Unit)? = null
) : ListAdapter<Post, ExplorePostAdapter.VH>(Diff) {
    companion object {
        private val Diff = object : DiffUtil.ItemCallback<Post>() {
            override fun areItemsTheSame(old: Post, new: Post) = old.id == new.id
            override fun areContentsTheSame(old: Post, new: Post) = old == new
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_explore_post, parent, false)
        val height = (parent.context.resources.displayMetrics.heightPixels * 0.47f).toInt()
        view.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            height
        )
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onPostClick(currentList, pos)
        }
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val avatarImage: com.google.android.material.imageview.ShapeableImageView =
            view.findViewById(R.id.avatarImage)
        private val usernameText: TextView = view.findViewById(R.id.usernameText)
        private val contentText: TextView = view.findViewById(R.id.contentText)
        private val mediaPager: ViewPager2 = view.findViewById(R.id.mediaPager)
        private val mediaIndicatorLayout: LinearLayout = view.findViewById(R.id.mediaIndicatorLayout)
        private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null
        private val timestampText: TextView = view.findViewById(R.id.timestampText)
        fun bind(post: Post) {
            // Gate subscription/paid posts: hide post UI and lock overlay until check completes
            mediaPager.visibility = View.GONE
            mediaIndicatorLayout.visibility = View.GONE
            itemView.findViewById<View>(R.id.postDetails).visibility = View.GONE
            val overlay = itemView.findViewById<FrameLayout>(R.id.lockOverlay)
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
                    overlay.setOnClickListener { onLocked(post) }
                } else {
                    mediaPager.visibility = View.VISIBLE
                    mediaIndicatorLayout.visibility = if (mediaPager.adapter?.itemCount ?: 0 > 1) View.VISIBLE else View.GONE
                    itemView.findViewById<View>(R.id.postDetails).visibility = View.VISIBLE
                    overlay.visibility = View.GONE
                }
            }
            // Navigate to full-post pager when tapping on details overlay
            val details = itemView.findViewById<View>(R.id.postDetails)
            details.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onPostClick(currentList, pos)
            }
            post.profile?.let { p ->
                usernameText.text = p.username
                if (p.image.isNotBlank()) {
                    avatarImage.load(p.image) {
                        placeholder(android.R.color.darker_gray)
                        transformations(coil.transform.CircleCropTransformation())
                    }
                } else {
                    avatarImage.setImageResource(android.R.color.darker_gray)
                }
            }
            timestampText.text = formatRelativeTime(post.createdAt)
            contentText.text = post.content.orEmpty()
            val mediaList = post.media ?: emptyList()
            val onCompleted = onVideoComplete?.let { cb -> { _: Int -> cb(bindingAdapterPosition) } }
            mediaPager.adapter = PostMediaAdapter(
                mediaList,
                playOnHover = true,
                onVideoCompleted = onCompleted
            )
            mediaIndicatorLayout.removeAllViews()
            if (mediaList.size <= 1) {
                mediaIndicatorLayout.visibility = View.GONE
            } else {
                mediaIndicatorLayout.visibility = View.VISIBLE
                pageChangeCallback?.let { mediaPager.unregisterOnPageChangeCallback(it) }
                mediaList.forEachIndexed { idx, _ ->
                    val dot = ImageView(itemView.context).apply {
                        setImageResource(
                            if (idx == 0) R.drawable.dot_active else R.drawable.dot_inactive
                        )
                        val size = (6 * context.resources.displayMetrics.density).toInt()
                        layoutParams = LinearLayout.LayoutParams(size, size).apply {
                            val margin = (4 * context.resources.displayMetrics.density).toInt()
                            marginStart = margin
                            marginEnd = margin
                        }
                    }
                    mediaIndicatorLayout.addView(dot)
                }
                val callback = object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        for (i in 0 until mediaIndicatorLayout.childCount) {
                            val iv = mediaIndicatorLayout.getChildAt(i) as ImageView
                            iv.setImageResource(
                                if (i == position) R.drawable.dot_active else R.drawable.dot_inactive
                            )
                        }
                    }
                }
                mediaPager.registerOnPageChangeCallback(callback)
                pageChangeCallback = callback
            }
        }
    }
    private fun formatRelativeTime(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return try {
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
