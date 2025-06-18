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
import club.gifters.giftersclub.model.TopGifter
import java.text.NumberFormat

/**
 * Adapter for rendering a list of TopGifters in a RecyclerView.
 */
class TopGifterAdapter(
    private val onClick: (TopGifter) -> Unit
) : ListAdapter<TopGifter, TopGifterAdapter.GifterViewHolder>(GifterDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GifterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_top_gifter, parent, false)
        return GifterViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: GifterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class GifterViewHolder(
        itemView: View,
        val onClick: (TopGifter) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val imageAvatar: ImageView = itemView.findViewById(R.id.imageAvatar)
        private val textUsername: TextView = itemView.findViewById(R.id.textUsername)
        private val textLevel: TextView = itemView.findViewById(R.id.textLevel)
        private val textTokensSent: TextView = itemView.findViewById(R.id.textTokensSent)
        private var currentGifter: TopGifter? = null

        init {
            itemView.setOnClickListener {
                currentGifter?.let(onClick)
            }
        }

        fun bind(gifter: TopGifter) {
            currentGifter = gifter
            textUsername.text = gifter.username
            textLevel.text = "Level ${gifter.gifterLevel} - ${gifter.gifterLevelName}"
            textTokensSent.text = NumberFormat.getNumberInstance().format(gifter.tokensSent)
            if (gifter.image.isNotBlank()) {
                imageAvatar.load(gifter.image) {
                    placeholder(android.R.color.darker_gray)
                    error(android.R.color.darker_gray)
                }
            } else {
                imageAvatar.setImageResource(android.R.color.darker_gray)
            }
        }
    }

    class GifterDiffCallback : DiffUtil.ItemCallback<TopGifter>() {
        override fun areItemsTheSame(old: TopGifter, new: TopGifter) = old.userId == new.userId
        override fun areContentsTheSame(old: TopGifter, new: TopGifter) = old == new
    }
}