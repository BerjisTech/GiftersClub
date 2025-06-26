package club.gifters.giftersclub.live

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.Gift
import java.text.NumberFormat
import java.util.Locale
import android.widget.TextView

/**
 * Adapter for displaying gift icons in a grid.
 */
class LiveGiftAdapter(
    private val onClick: (Gift) -> Unit
) : ListAdapter<Gift, LiveGiftAdapter.GiftViewHolder>(GiftDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GiftViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_live_gift, parent, false)
        return GiftViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: GiftViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class GiftViewHolder(
        itemView: View,
        private val onClick: (Gift) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val ivGiftIcon: ImageView = itemView.findViewById(R.id.ivGiftIcon)
        private val tvGiftName: TextView = itemView.findViewById(R.id.tvGiftName)
        private val ivTokenIcon: ImageView = itemView.findViewById(R.id.ivTokenIcon)
        private val tvGiftTokens: TextView = itemView.findViewById(R.id.tvGiftTokens)
        private var current: Gift? = null

        init {
            itemView.setOnClickListener {
                current?.let(onClick)
            }
        }

        fun bind(gift: Gift) {
            current = gift
            val ctx = itemView.context
            // bind icon
            val resourceName = gift.name.lowercase(Locale.US).replace(' ', '_')
            val resId = ctx.resources.getIdentifier(resourceName, "drawable", ctx.packageName)
            if (resId != 0) ivGiftIcon.setImageResource(resId)
            else ivGiftIcon.load(gift.image) {
                placeholder(android.R.color.darker_gray)
                error(android.R.color.darker_gray)
            }
            // bind name and tokens
            tvGiftName.text = gift.name
            tvGiftTokens.text = NumberFormat.getInstance().format(gift.tokens)
            ivTokenIcon.setImageResource(R.drawable.token)
        }
    }

    private class GiftDiffCallback : DiffUtil.ItemCallback<Gift>() {
        override fun areItemsTheSame(oldItem: Gift, newItem: Gift) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Gift, newItem: Gift) = oldItem == newItem
    }
}