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
import android.view.MotionEvent
import android.media.MediaMetadataRetriever
import android.graphics.Bitmap
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Adapter for media carousel in a post (images & videos).
 */
class PostMediaAdapter(
    private val mediaList: List<PostMedia>,
    private val playOnHover: Boolean = false,
    private val onVideoCompleted: ((Int) -> Unit)? = null
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

        /**
         * Extract a single video frame for thumbnail (background thread).
         */
        private suspend fun getVideoFrame(url: String): Bitmap? = withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(url, HashMap())
                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
                frame
            } catch (_: Exception) {
                null
            }
        }

        fun bind(media: PostMedia) {
            if (media.mediaType == "video") {
                spinner.visibility = View.VISIBLE
                if (playOnHover) {
                    imageView.visibility = View.VISIBLE
                    videoView.visibility = View.GONE
                    (itemView.context as? LifecycleOwner)
                        ?.lifecycleScope
                        ?.launch {
                            val bmp = getVideoFrame(media.url)
                            if (bmp != null) imageView.setImageBitmap(bmp)
                            else imageView.setImageResource(android.R.color.darker_gray)
                            spinner.visibility = View.GONE
                        }
                } else {
                    imageView.visibility = View.GONE
                    videoView.visibility = View.VISIBLE
                }
                videoView.setVideoURI(Uri.parse(media.url))
                videoView.setOnPreparedListener { mp ->
                    spinner.visibility = View.GONE
                    mp.isLooping = true
                    if (playOnHover) {
                        imageView.visibility = View.GONE
                        videoView.visibility = View.VISIBLE
                        videoView.start()
                    } else {
                        videoView.start()
                    }
                }
                if (playOnHover) {
                    imageView.setOnHoverListener { _, event ->
                        if (event.actionMasked == MotionEvent.ACTION_HOVER_ENTER) {
                            spinner.visibility = View.VISIBLE
                            imageView.visibility = View.GONE
                            videoView.visibility = View.VISIBLE
                            videoView.start()
                        }
                        true
                    }
                }
                onVideoCompleted?.let { cb ->
                    videoView.setOnCompletionListener {
                        cb.invoke(bindingAdapterPosition)
                    }
                }
            } else {
                videoView.visibility = View.GONE
                imageView.visibility = View.VISIBLE
                spinner.visibility = View.VISIBLE
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
            // allow tap to toggle play/pause
            videoView.setOnClickListener {
                if (videoView.isPlaying) videoView.pause() else videoView.start()
            }
        }

        /**
         * Manually start playback of a video, if bound to a video media item.
         */
        fun startPlayback() {
            if (videoView.visibility == View.VISIBLE) {
                videoView.start()
            }
        }
    }

    class MediaDiffCallback : DiffUtil.ItemCallback<PostMedia>() {
        override fun areItemsTheSame(old: PostMedia, new: PostMedia) = old.id == new.id
        override fun areContentsTheSame(old: PostMedia, new: PostMedia) = old == new
    }
}