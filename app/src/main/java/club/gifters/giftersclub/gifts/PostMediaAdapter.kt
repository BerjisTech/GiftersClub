package club.gifters.giftersclub.gifts

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
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
import coil.request.videoFrameMillis
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
        private val playerView: PlayerView = itemView.findViewById(R.id.mediaPlayerView)
        private var player: androidx.media3.exoplayer.ExoPlayer? = null
        private val spinner: ProgressBar = itemView.findViewById(R.id.mediaLoadingSpinner)

        /**
         * Extract a single video frame for thumbnail (background thread).
         */
        private suspend fun getVideoFrame(url: String): Bitmap? = withContext(Dispatchers.IO) { null }

        fun bind(media: PostMedia) {
            if (media.mediaType == "video") {
                spinner.visibility = View.VISIBLE
                // Show a fast thumbnail using Coil's video frame decoder
                imageView.visibility = View.VISIBLE
                playerView.visibility = View.GONE
                imageView.load(media.url) {
                    placeholder(android.R.color.darker_gray)
                    error(android.R.color.darker_gray)
                    videoFrameMillis(100) // grab an early frame
                    listener(
                        onStart = { spinner.visibility = View.VISIBLE },
                        onSuccess = { _, _ -> spinner.visibility = View.GONE },
                        onError = { _, _ -> spinner.visibility = View.GONE }
                    )
                }

                // Prepare ExoPlayer with cached data source
                player?.release()
                val ctx = itemView.context
                player = club.gifters.giftersclub.media.Exo.newPlayer(ctx)
                playerView.player = player
                val mediaItem = MediaItem.fromUri(media.url)
                player?.setMediaItem(mediaItem)
                player?.repeatMode = Player.REPEAT_MODE_ALL
                player?.prepare()
                player?.playWhenReady = !playOnHover

                if (playOnHover) {
                    imageView.setOnHoverListener { _, event ->
                        if (event.actionMasked == MotionEvent.ACTION_HOVER_ENTER) {
                            imageView.visibility = View.GONE
                            playerView.visibility = View.VISIBLE
                            player?.playWhenReady = true
                        }
                        true
                    }
                } else {
                    // Switch to player view once ready
                    player?.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY) {
                                imageView.visibility = View.GONE
                                playerView.visibility = View.VISIBLE
                                spinner.visibility = View.GONE
                            }
                        }
                    })
                }
                onVideoCompleted?.let { cb ->
                    player?.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_ENDED) {
                                cb.invoke(bindingAdapterPosition)
                            }
                        }
                    })
                }
            } else {
                player?.release(); player = null
                playerView.visibility = View.GONE
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
            playerView.setOnClickListener {
                player?.let { p -> if (p.isPlaying) p.pause() else p.play() }
            }
        }

        /**
         * Manually start playback of a video, if bound to a video media item.
         */
        fun startPlayback() {
            if (playerView.visibility == View.VISIBLE) player?.playWhenReady = true
        }

        fun release() {
            playerView.player = null
            player?.release()
            player = null
        }
    }

    override fun onViewRecycled(holder: MediaViewHolder) {
        super.onViewRecycled(holder)
        holder.release()
    }

    class MediaDiffCallback : DiffUtil.ItemCallback<PostMedia>() {
        override fun areItemsTheSame(old: PostMedia, new: PostMedia) = old.id == new.id
        override fun areContentsTheSame(old: PostMedia, new: PostMedia) = old == new
    }
}
