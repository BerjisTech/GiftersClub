package club.gifters.giftersclub.social

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
import club.gifters.giftersclub.model.Profile

/**
 * Adapter for displaying a list of user profiles in a RecyclerView.
 */
class ProfileAdapter(
    private val onClick: (Profile) -> Unit
) : ListAdapter<Profile, ProfileAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_user, parent, false)
        return ViewHolder(view, onClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        itemView: View,
        private val onClick: (Profile) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val ivAvatar: ImageView = itemView.findViewById(R.id.ivAvatar)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvUsername: TextView = itemView.findViewById(R.id.tvUsername)
        private var current: Profile? = null

        init {
            itemView.setOnClickListener {
                current?.let(onClick)
            }
        }

        fun bind(profile: Profile) {
            current = profile
            tvName.text = profile.name.orEmpty()
            tvUsername.text = "@${profile.username}"
            if (profile.image.isNotBlank()) {
                ivAvatar.load(profile.image) {
                    placeholder(android.R.color.darker_gray)
                    error(android.R.color.darker_gray)
                }
            } else {
                ivAvatar.setImageResource(android.R.color.darker_gray)
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Profile>() {
        override fun areItemsTheSame(old: Profile, new: Profile) = old.userId == new.userId
        override fun areContentsTheSame(old: Profile, new: Profile) = old == new
    }
}