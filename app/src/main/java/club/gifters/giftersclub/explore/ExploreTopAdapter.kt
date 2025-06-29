package club.gifters.giftersclub.explore

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
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
        TYPE_POST -> PostVH(LayoutInflater.from(parent.context)
            .inflate(R.layout.item_explore_post, parent, false))
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
        fun bind(item: Any) { tv.text = (item as Post).content.orEmpty() }
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