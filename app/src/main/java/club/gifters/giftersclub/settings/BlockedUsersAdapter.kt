package club.gifters.giftersclub.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import com.google.android.material.button.MaterialButton

/**
 * Adapter for displaying blocked users with multi-select and individual unblock.
 */
data class BlockedUserItemView(
    val userId: String,
    val username: String,
    var isSelected: Boolean = false
)

class BlockedUsersAdapter(
    private val onItemSelectionChanged: (selectedCount: Int) -> Unit,
    private val onUnblockClick: (item: BlockedUserItemView) -> Unit
) : RecyclerView.Adapter<BlockedUsersAdapter.ViewHolder>() {

    private val items = mutableListOf<BlockedUserItemView>()

    /** Append new items for pagination. */
    fun addItems(newItems: List<BlockedUserItemView>) {
        val start = items.size
        items.addAll(newItems)
        notifyItemRangeInserted(start, newItems.size)
    }

    /** Clear all items (e.g. on refresh). */
    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    /** Get selected user IDs for bulk unblock. */
    fun getSelectedUserIds(): List<String> = items.filter { it.isSelected }.map { it.userId }

    /** Clear selection state. */
    fun clearSelection() {
        items.forEach { it.isSelected = false }
        notifyDataSetChanged()
        onItemSelectionChanged(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_blocked_user, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val checkBox: CheckBox = view.findViewById(R.id.cbSelect)
        private val tvUsername: TextView = view.findViewById(R.id.tvUsername)
        private val btnUnblock: MaterialButton = view.findViewById(R.id.btnUnblock)

        fun bind(item: BlockedUserItemView) {
            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = item.isSelected
            tvUsername.text = item.username
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                item.isSelected = isChecked
                onItemSelectionChanged(items.count { it.isSelected })
            }
            btnUnblock.setOnClickListener {
                onUnblockClick(item)
            }
        }
    }
}