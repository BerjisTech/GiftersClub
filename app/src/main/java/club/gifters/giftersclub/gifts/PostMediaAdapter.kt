package club.gifters.giftersclub.gifts

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.VideoView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.PostMedia
import android.widget.ProgressBar

/**
 * Adapter for media carousel in a post (images & videos).
 */
class PostMediaAdapter(
    private val mediaList: List<PostMedia>
) : ListAdapter<PostMedia, PostMediaAdapter.MediaViewHolder>(MediaDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_post_media, parent, false)
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getItemCount(): Int = mediaList.size

    init {
        submitList(mediaList)
    }

    inner class MediaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.mediaImageView)
        private val videoView: VideoView = itemView.findViewById(R.id.mediaVideoView)
        private val spinner: ProgressBar = itemView.findViewById(R.id.mediaLoadingSpinner)

        fun bind(media: PostMedia) {
            spinner.visibility = View.VISIBLE
            if (media.mediaType == "video") {
                imageView.visibility = View.GONE
                videoView.visibility = View.VISIBLE
                videoView.setVideoURI(Uri.parse(media.url))
                videoView.setOnPreparedListener { mp ->
                    mp.isLooping = true
                    spinner.visibility = View.GONE
                    videoView.start()
                }
            } else {
                videoView.visibility = View.GONE
                imageView.visibility = View.VISIBLE
                imageView.load(media.url) {
                    placeholder(android.R.color.darker_gray)
                    error(android.R.color.darker_gray)
                    listener(
                        onStart = { spinner.visibility = View.VISIBLE },
                        onSuccess = { _, _ -> spinner.visibility = View.GONE },
                        onError = { _, _ -> spinner.visibility = View.GONE }
                    )
                }
            }
        }
    }

    class MediaDiffCallback : DiffUtil.ItemCallback<PostMedia>() {
        override fun areItemsTheSame(old: PostMedia, new: PostMedia) = old.id == new.id
        override fun areContentsTheSame(old: PostMedia, new: PostMedia) = old == new
    }
}