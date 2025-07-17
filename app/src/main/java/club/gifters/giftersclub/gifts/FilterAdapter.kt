package club.gifters.giftersclub.gifts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter

/**
 * Data model for a named GPUImage filter, optionally adjustable via SeekBar.
 */
data class FilterItem(
    val name: String,
    val filter: GPUImageFilter,
    val adjustable: Boolean = false
)

class FilterAdapter(
    private val onSelect: (FilterItem) -> Unit
) : ListAdapter<FilterItem, FilterAdapter.VH>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_filter, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName: TextView = view.findViewById(R.id.tvFilterName)
        fun bind(item: FilterItem) {
            tvName.text = item.name
            itemView.alpha = if (item.adjustable || item.filter is GPUImageFilter) 1f else 0.5f
            itemView.setOnClickListener { onSelect(item) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<FilterItem>() {
        override fun areItemsTheSame(old: FilterItem, new: FilterItem) = old.name == new.name
        override fun areContentsTheSame(old: FilterItem, new: FilterItem) = old == new
    }
}