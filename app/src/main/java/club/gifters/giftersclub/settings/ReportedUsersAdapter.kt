package club.gifters.giftersclub.settings

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R

/**
 * Adapter for displaying reported users with status indicator.
 */
data class ReportedUserItemView(
    val username: String,
    val reason: String,
    val status: String
)

class ReportedUsersAdapter : RecyclerView.Adapter<ReportedUsersAdapter.ViewHolder>() {

    private val items = mutableListOf<ReportedUserItemView>()

    /** Append new items for pagination. */
    fun addItems(newItems: List<ReportedUserItemView>) {
        val start = items.size
        items.addAll(newItems)
        notifyItemRangeInserted(start, newItems.size)
    }

    /** Clear current items (e.g. on refresh). */
    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reported_user, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvUsername: TextView = view.findViewById(R.id.tvReportedUsername)
        private val tvReason: TextView = view.findViewById(R.id.tvReason)
        private val tvStatus: TextView = view.findViewById(R.id.tvStatus)

        fun bind(item: ReportedUserItemView) {
            tvUsername.text = item.username
            tvReason.text = item.reason
            tvStatus.text = item.status.capitalize()
            when (item.status.lowercase()) {
                "pending" -> tvStatus.setTextColor(Color.parseColor("#FFA000"))
                "no_violation" -> tvStatus.setTextColor(Color.GRAY)
                "action_taken" -> tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                else -> tvStatus.setTextColor(Color.GRAY)
            }
        }
    }
}