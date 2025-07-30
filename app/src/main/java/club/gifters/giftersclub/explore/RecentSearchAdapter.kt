package club.gifters.giftersclub.explore

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R

/**
 * Adapter for displaying user's recent search queries with delete option.
 */
class RecentSearchAdapter(
    private val onClick: (String) -> Unit,
    private val onDelete: (String) -> Unit
) : ListAdapter<String, RecentSearchAdapter.VH>(Diff) {
    companion object {
        private val Diff = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(old: String, new: String) = old == new
            override fun areContentsTheSame(old: String, new: String) = old == new
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_search_query, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.tvQuery.text = item
        holder.tvQuery.setOnClickListener { onClick(item) }
        holder.tvDelete.setOnClickListener { onDelete(item) }
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvQuery: TextView = view.findViewById(R.id.tvQuery)
        val tvDelete: TextView = view.findViewById(R.id.tvDelete)
    }
}