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
import club.gifters.giftersclub.model.Wishlist

/**
 * Adapter for displaying a user's wishlists.
 */
class WishlistAdapter : ListAdapter<Wishlist, WishlistAdapter.WishlistViewHolder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WishlistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wishlist, parent, false)
        return WishlistViewHolder(view)
    }

    override fun onBindViewHolder(holder: WishlistViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class WishlistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivImage: ImageView = itemView.findViewById(R.id.ivWishlistImage)
        private val tvName: TextView = itemView.findViewById(R.id.tvWishlistName)
        private val tvDesc: TextView = itemView.findViewById(R.id.tvWishlistDesc)

        fun bind(item: Wishlist) {
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
        }
    }

    object Diff : DiffUtil.ItemCallback<Wishlist>() {
        override fun areItemsTheSame(old: Wishlist, new: Wishlist) = old.id == new.id
        override fun areContentsTheSame(old: Wishlist, new: Wishlist) = old == new
    }
}