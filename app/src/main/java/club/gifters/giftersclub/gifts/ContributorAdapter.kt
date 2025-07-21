package club.gifters.giftersclub.gifts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.ContributorSummary
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Adapter to display contributors to a wishlist.
 */
class ContributorAdapter : ListAdapter<ContributorSummary, ContributorAdapter.ViewHolder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contributor, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivAvatar: ImageView = itemView.findViewById(R.id.ivContributorAvatar)
        private val tvName: TextView = itemView.findViewById(R.id.tvContributorName)
        private val tvDate: TextView = itemView.findViewById(R.id.tvContributorDate)
        private val tvTokens: TextView = itemView.findViewById(R.id.tvContributorTokens)

        fun bind(item: ContributorSummary) {
            ivAvatar.load(item.profile.image) {
                placeholder(android.R.color.darker_gray)
                error(android.R.color.darker_gray)
            }
            tvName.text = item.profile.name ?: item.profile.username
            // Format the contribution date
            val rawDate = item.contributedAt
            tvDate.text = try {
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    .format(OffsetDateTime.parse(rawDate).toLocalDate())
            } catch (_: Exception) {
                rawDate.substringBefore('T')
            }
            // Format token count with grouping separators
            tvTokens.text = NumberFormat.getNumberInstance().format(item.tokens)
        }
    }

    object Diff : DiffUtil.ItemCallback<ContributorSummary>() {
        override fun areItemsTheSame(old: ContributorSummary, new: ContributorSummary) =
            old.profile.userId == new.profile.userId

        override fun areContentsTheSame(old: ContributorSummary, new: ContributorSummary) =
            old == new
    }
}