package club.gifters.giftersclub.explore

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.Profile
import coil.load
import coil.transform.CircleCropTransformation

/**
 * Adapter for showing user search results in Explore.
 */
class ExploreUserAdapter(
    private val onClick: (Profile) -> Unit
) : ListAdapter<Profile, ExploreUserAdapter.VH>(Diff) {
    companion object {
        private val Diff = object : DiffUtil.ItemCallback<Profile>() {
            override fun areItemsTheSame(old: Profile, new: Profile) = old.userId == new.userId
            override fun areContentsTheSame(old: Profile, new: Profile) = old == new
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_user, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val iv: ImageView = view.findViewById(R.id.ivAvatar)
        private val tvName: TextView = view.findViewById(R.id.tvName)
        private val tvUsername: TextView = view.findViewById(R.id.tvUsername)

        init {
            view.setOnClickListener { onClick(getItem(bindingAdapterPosition)) }
        }

        fun bind(profile: Profile) {
            tvName.text = profile.name.orEmpty()
            tvUsername.text = "@${profile.username}"
            if (profile.image.isNotBlank()) {
                iv.load(profile.image) {
                    transformations(CircleCropTransformation())
                    placeholder(android.R.color.darker_gray)
                }
            } else {
                iv.setImageResource(android.R.color.darker_gray)
            }
        }
    }
}