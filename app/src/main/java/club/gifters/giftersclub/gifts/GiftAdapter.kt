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
import club.gifters.giftersclub.model.Gift
import java.text.NumberFormat

/**
 * RecyclerView adapter for displaying a grid of gifts.
 */
class GiftAdapter(
    private val onClick: (Gift) -> Unit
) : ListAdapter<Gift, GiftAdapter.GiftViewHolder>(GiftDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GiftViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gift, parent, false)
        return GiftViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: GiftViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class GiftViewHolder(
        itemView: View,
        val onClick: (Gift) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val imageGift: ImageView = itemView.findViewById(R.id.imageGift)
        private val textName: TextView = itemView.findViewById(R.id.textName)
        private val textTokens: TextView = itemView.findViewById(R.id.textTokens)
        private var currentGift: Gift? = null

        init {
            itemView.setOnClickListener {
                currentGift?.let(onClick)
            }
        }

        fun bind(gift: Gift) {
            currentGift = gift
            textName.text = gift.name
            textTokens.text = NumberFormat.getNumberInstance().format(gift.tokens)
            // Load image if URL is provided, otherwise show placeholder
            if (gift.image.isNotBlank()) {
                imageGift.load(gift.image) {
                    placeholder(android.R.color.darker_gray)
                    error(android.R.color.darker_gray)
                }
            } else {
                imageGift.setImageResource(android.R.color.darker_gray)
            }
        }
    }

    class GiftDiffCallback : DiffUtil.ItemCallback<Gift>() {
        override fun areItemsTheSame(old: Gift, new: Gift) = old.id == new.id
        override fun areContentsTheSame(old: Gift, new: Gift) = old == new
    }
}