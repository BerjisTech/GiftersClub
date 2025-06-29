package club.gifters.giftersclub.explore

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.Post

/**
 * Adapter for showing post search results in Explore.
 */
class ExplorePostAdapter : ListAdapter<Post, ExplorePostAdapter.VH>(Diff) {
    companion object {
        private val Diff = object : DiffUtil.ItemCallback<Post>() {
            override fun areItemsTheSame(old: Post, new: Post) = old.id == new.id
            override fun areContentsTheSame(old: Post, new: Post) = old == new
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_explore_post, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvUser: TextView = view.findViewById(R.id.tvUsername)
        private val tvContent: TextView = view.findViewById(R.id.tvContent)
        fun bind(post: Post) {
            tvUser.text = post.profile?.username.orEmpty()
            tvContent.text = post.content.orEmpty()
        }
    }
}