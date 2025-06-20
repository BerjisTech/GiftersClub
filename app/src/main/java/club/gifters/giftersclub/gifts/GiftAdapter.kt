package club.gifters.giftersclub.gifts

import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.card.MaterialCardView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.Gift
import java.text.NumberFormat
import java.util.Locale

// Tailwind CSS v4 shade-500 color palette (hex values)
private val tailwind500Colors = mapOf(
    "amber" to "#F59E0B",
    "blue" to "#3B82F6",
    "red" to "#EF4444",
    "orange" to "#F97316",
    "yellow" to "#EAB308",
    "lime" to "#84CC16",
    "green" to "#22C55E",
    "emerald" to "#10B981",
    "teal" to "#14B8A6",
    "cyan" to "#06B6D4",
    "sky" to "#0EA5E9",
    "indigo" to "#6366F1",
    "violet" to "#8B5CF6",
    "purple" to "#A855F7",
    "fuchsia" to "#D946EF",
    "pink" to "#EC4899",
    "rose" to "#F43F5E",
    "gray" to "#6B7280",
    "slate" to "#64748B",
    "zinc" to "#71717A",
    "stone" to "#78716C"
)

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
        private val overlayView: View = itemView.findViewById(R.id.overlayView)
        private val textName: TextView = itemView.findViewById(R.id.textName)
        private val textTokens: TextView = itemView.findViewById(R.id.textTokens)
        private val cardView: MaterialCardView = itemView as MaterialCardView
        private var currentGift: Gift? = null

        init {
            itemView.setOnClickListener {
                currentGift?.let(onClick)
            }
        }

        fun bind(gift: Gift) {
            currentGift = gift

            // set overlay tint and glass blur from gift themeColor
            val colorKey = gift.themeColor.lowercase(Locale.US)
            val colorHex = tailwind500Colors[colorKey] ?: "#FFFFFF"
            val baseColorInt = Color.parseColor(colorHex)
            cardView.setCardBackgroundColor(baseColorInt)
            val overlayColor = (0x80 shl 24) or (baseColorInt and 0x00FFFFFF)
            overlayView.setBackgroundColor(overlayColor)

            // apply blur to gift image for glassmorphism (Android S+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    imageGift.setRenderEffect(
                        RenderEffect.createBlurEffect(0f, 0f, Shader.TileMode.CLAMP)
                    )
                } catch (_: Throwable) {
                }
            }

            textName.text = gift.name
            textTokens.text = NumberFormat.getNumberInstance().format(gift.tokens)

            // Try loading local drawable matching gift name
            val ctx = itemView.context
            val resourceName = gift.name.lowercase(Locale.US).replace(" ", "_")
            val resId = ctx.resources.getIdentifier(resourceName, "drawable", ctx.packageName)
            if (resId != 0) {
                imageGift.setImageResource(resId)
            } else {
                val imageUrl = if (gift.image.isNotBlank()) gift.image
                    else "https://gifters.club/assets/images/$resourceName.png"
                imageGift.load(imageUrl) {
                    placeholder(android.R.color.darker_gray)
                    error(android.R.color.darker_gray)
                }
            }
        }
    }

    class GiftDiffCallback : DiffUtil.ItemCallback<Gift>() {
        override fun areItemsTheSame(old: Gift, new: Gift) = old.id == new.id
        override fun areContentsTheSame(old: Gift, new: Gift) = old == new
    }
}