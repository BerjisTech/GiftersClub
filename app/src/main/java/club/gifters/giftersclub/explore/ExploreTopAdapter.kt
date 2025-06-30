package club.gifters.giftersclub.explore

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
import club.gifters.giftersclub.model.LiveStream
import club.gifters.giftersclub.model.Post
import club.gifters.giftersclub.model.Profile

/**
 * Adapter for mixed explore 'Top' feed (posts, users, live streams).
 */
class ExploreTopAdapter : ListAdapter<Any, RecyclerView.ViewHolder>(Diff) {
    companion object {
        private const val TYPE_POST = 0
        private const val TYPE_USER = 1
        private const val TYPE_LIVE = 2

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
        TYPE_USER -> UserVH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_user, parent, false))
        TYPE_LIVE -> LiveVH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_explore_live, parent, false))
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
        private val tv = view.findViewById<TextView>(R.id.tvContent)
        private val mediaPager: ViewPager2 = view.findViewById(R.id.mediaPager)
        private val mediaIndicatorLayout: LinearLayout = view.findViewById(R.id.mediaIndicatorLayout)
        private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null

        fun bind(item: Any) {
            val post = item as Post
            tv.text = post.content.orEmpty()
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
    }
    private class UserVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tv = view.findViewById<TextView>(R.id.tvName)
        fun bind(item: Any) { tv.text = (item as Profile).username }
    }
    private class LiveVH(view: View) : RecyclerView.ViewHolder(view) {
        private val tv = view.findViewById<TextView>(R.id.tvTitle)
        fun bind(item: Any) { tv.text = (item as LiveStream).title }
    }
}