package club.gifters.giftersclub.explore

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.load
import coil.transform.CircleCropTransformation
import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import club.gifters.giftersclub.R
import club.gifters.giftersclub.gifts.PostMediaAdapter
import club.gifters.giftersclub.model.LiveStream
import club.gifters.giftersclub.model.Post
import club.gifters.giftersclub.model.Profile

/**
 * Adapter for mixed explore 'Top' feed (posts, users, live streams).
 * @param onUserClick optional callback invoked when a user item is clicked.
 */
class ExploreTopAdapter(
    private val onUserClick: (Profile) -> Unit = {}
) : ListAdapter<Any, RecyclerView.ViewHolder>(Diff) {
    companion object {
        const val TYPE_POST = 0
        const val TYPE_USER = 1
        const val TYPE_LIVE = 2

        private val Diff = object : DiffUtil.ItemCallback<Any>() {
            override fun areItemsTheSame(old: Any, new: Any) = when {
                old is Post       && new is Post       -> old.id == new.id
                old is Profile    && new is Profile    -> old.userId == new.userId
                old is LiveStream && new is LiveStream -> old.id == new.id
                else -> false
            }
            override fun areContentsTheSame(old: Any, new: Any) = old == new
        }
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is Post       -> TYPE_POST
        is Profile    -> TYPE_USER
        is LiveStream -> TYPE_LIVE
        else          -> TYPE_POST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
        TYPE_POST -> {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_explore_post, parent, false)
            val height = (parent.context.resources.displayMetrics.heightPixels * 0.43f).toInt()
            view.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            )
            PostVH(view)
        }
        TYPE_USER -> {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_search_user, parent, false)
            view.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            UserVH(view)
        }
        TYPE_LIVE -> {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_explore_live, parent, false)
            view.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            LiveVH(view)
        }
        else      -> PostVH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_explore_post, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is PostVH  -> holder.bind(getItem(position) as Post)
            is UserVH  -> holder.bind(getItem(position) as Profile)
            is LiveVH  -> holder.bind(getItem(position) as LiveStream)
        }
    }

    private class PostVH(view: View) : RecyclerView.ViewHolder(view) {
        private val avatarImage: com.google.android.material.imageview.ShapeableImageView =
            view.findViewById(R.id.avatarImage)
        private val usernameText: TextView = view.findViewById(R.id.usernameText)
        private val contentText: TextView = view.findViewById(R.id.contentText)
        private val mediaPager: ViewPager2 = view.findViewById(R.id.mediaPager)
        private val mediaIndicatorLayout: LinearLayout = view.findViewById(R.id.mediaIndicatorLayout)
        private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null
        private val timestampText: TextView = view.findViewById(R.id.timestampText)

        fun bind(item: Any) {
            val post = item as Post
            post.profile?.let { p ->
                usernameText.text = p.username
                if (p.image.isNotBlank()) {
                    avatarImage.load(p.image) {
                        placeholder(android.R.color.darker_gray)
                        transformations(CircleCropTransformation())
                    }
                } else {
                    avatarImage.setImageResource(android.R.color.darker_gray)
                }
            }
            timestampText.text = formatRelativeTime(post.createdAt)
            contentText.text = post.content.orEmpty()
            val mediaList = post.media ?: emptyList()
            mediaPager.adapter = PostMediaAdapter(mediaList)
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

        private fun formatRelativeTime(iso: String?): String {
            if (iso.isNullOrBlank()) return ""
            return try {
                val trimmed = iso.replace(Regex("\\.(\\d{3})\\d*"), ".$1")
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
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
    private inner class UserVH(view: View) : RecyclerView.ViewHolder(view) {
        private val iv: ImageView = view.findViewById(R.id.ivAvatar)
        private val tvName: TextView = view.findViewById(R.id.tvName)
        private val tvUsername: TextView = view.findViewById(R.id.tvUsername)

        init {
            view.setOnClickListener {
                (getItem(bindingAdapterPosition) as? Profile)?.let { onUserClick(it) }
            }
        }

        fun bind(item: Any) {
            val profile = item as Profile
            tvName.text = profile.name.orEmpty()
            tvUsername.text = "@${profile.username}"
            if (profile.image.isNotBlank()) {
                iv.load(profile.image) {
                    transformations(CircleCropTransformation())
                    placeholder(android.R.color.darker_gray)
                }
            } else {
                iv.setImageResource(android.R.color.darker_gray)
            }
        }
    }
    private class LiveVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tv = view.findViewById<TextView>(R.id.tvTitle)
        fun bind(item: Any) { tv.text = (item as LiveStream).title }
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