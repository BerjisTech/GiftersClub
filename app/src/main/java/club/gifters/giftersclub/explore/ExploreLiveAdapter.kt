package club.gifters.giftersclub.explore

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.LiveStream

/**
 * Adapter for showing live stream search results in Explore.
 */
class ExploreLiveAdapter : ListAdapter<LiveStream, ExploreLiveAdapter.VH>(Diff) {
    companion object {
        private val Diff = object : DiffUtil.ItemCallback<LiveStream>() {
            override fun areItemsTheSame(old: LiveStream, new: LiveStream) = old.id == new.id
            override fun areContentsTheSame(old: LiveStream, new: LiveStream) = old == new
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_explore_live, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        private val tvViewerCount: TextView = view.findViewById(R.id.tvViewerCount)
        init {
            view.setOnClickListener {
                (getItem(bindingAdapterPosition))?.let { ls ->
                    val ctx = itemView.context
                    val uri = Uri.parse("https://gifters.club/live/${ls.id}")
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    intent.setClassName(ctx, "club.gifters.giftersclub.live.LiveStreamActivity")
                    ctx.startActivity(intent)
                }
            }
        }
        fun bind(item: LiveStream) {
            tvTitle.text = item.title
            tvViewerCount.text = "${item.viewerCount} watching"
        }
    }
}
