package club.gifters.giftersclub.gifts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.Post
import coil.load

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.media.MediaMetadataRetriever

class PostGridAdapter(
    private val onPostClick: (Post) -> Unit,
    private val onPostLongClick: (Post) -> Unit,
    private val isSelectionMode: () -> Boolean,
    private val isPostSelected: (Post) -> Boolean,
    private val togglePostSelection: (Post) -> Unit
) : ListAdapter<Post, PostGridAdapter.PostViewHolder>(PostDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_post_grid_item, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        val post = getItem(position)
        holder.bind(post)
    }

    inner class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val postImage: ImageView = itemView.findViewById(R.id.imagePost)
        private val checkBox: CheckBox = itemView.findViewById(R.id.checkboxSelect)

        fun bind(post: Post) {
            val mediaItem = post.media?.firstOrNull()
            if (mediaItem?.mediaType == "video") {
                // Load video thumbnail instead of blank rectangle
                (itemView.context as? androidx.lifecycle.LifecycleOwner)
                    ?.lifecycleScope
                    ?.launch {
                        val bitmap = withContext(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val retriever = android.media.MediaMetadataRetriever()
                                retriever.setDataSource(mediaItem.url, HashMap())
                                val bmp = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                retriever.release()
                                bmp
                            } catch (_: Exception) {
                                null
                            }
                        }
                        if (bitmap != null) postImage.setImageBitmap(bitmap)
                        else postImage.setImageResource(R.drawable.video)
                    }
            } else {
                postImage.load(mediaItem?.url) { placeholder(R.drawable.bg_sky_blue_gradient) }
            }

            itemView.setOnClickListener { onPostClick(post) }
            itemView.setOnLongClickListener { onPostLongClick(post); true }

            if (isSelectionMode()) {
                checkBox.visibility = View.VISIBLE
                checkBox.isChecked = isPostSelected(post)
                checkBox.setOnClickListener { togglePostSelection(post) }
            } else {
                checkBox.visibility = View.GONE
                checkBox.isChecked = false
            }
        }
    }

    class PostDiffCallback : DiffUtil.ItemCallback<Post>() {
        override fun areItemsTheSame(oldItem: Post, newItem: Post): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Post, newItem: Post): Boolean {
            return oldItem == newItem
        }
    }
}
