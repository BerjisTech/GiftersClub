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
        private val tvDescription: TextView = view.findViewById(R.id.tvDescription)
        fun bind(item: LiveStream) {
            tvTitle.text = item.title
            tvDescription.text = item.description
        }
    }
}