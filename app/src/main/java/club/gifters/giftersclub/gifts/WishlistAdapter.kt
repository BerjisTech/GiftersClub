package club.gifters.giftersclub.gifts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.ProgressBar
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.Wishlist

/**
 * Adapter for displaying a user's wishlists.
 */
class WishlistAdapter(
    private val onClick: (Wishlist) -> Unit
) : ListAdapter<Wishlist, WishlistAdapter.WishlistViewHolder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WishlistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wishlist, parent, false)
        return WishlistViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: WishlistViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class WishlistViewHolder(
        itemView: View,
        private val onClick: (Wishlist) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val ivImage: ImageView = itemView.findViewById(R.id.ivWishlistImage)
        private val tvName: TextView = itemView.findViewById(R.id.tvWishlistName)
        private val tvDesc: TextView = itemView.findViewById(R.id.tvWishlistDesc)
        private val tvPercentage: TextView = itemView.findViewById(R.id.contributionPercentage)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.contributionProgressBar)

        fun bind(item: Wishlist) {
            itemView.setOnClickListener { onClick(item) }
            tvName.text = item.name
            tvDesc.text = item.description
            if (item.image.isNotBlank()) {
                ivImage.load(item.image) {
                    placeholder(android.R.color.darker_gray)
                    error(android.R.color.darker_gray)
                }
            } else {
                ivImage.setImageResource(android.R.color.darker_gray)
            }
            val total = item.contributorsCount ?: 0
            val max = item.tokens
            val percent = if (max > 0) (total * 100 / max) else 0
            val clamped = percent.coerceIn(0, 100)
            progressBar.progress = clamped
            tvPercentage.text = "$clamped%"
        }
    }

    object Diff : DiffUtil.ItemCallback<Wishlist>() {
        override fun areItemsTheSame(old: Wishlist, new: Wishlist) = old.id == new.id
        override fun areContentsTheSame(old: Wishlist, new: Wishlist) = old == new
    }
}