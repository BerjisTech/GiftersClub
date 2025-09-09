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
        private val controls: View = itemView.findViewById(R.id.mediaControls)
        private val timeBar: androidx.media3.ui.DefaultTimeBar = itemView.findViewById(R.id.timeBar)
        private val speedBtn: android.widget.TextView = itemView.findViewById(R.id.speedButton)
        private var hideControlsRunnable: Runnable? = null

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
                    crossfade(true)
                    placeholder(android.R.color.darker_gray)
                    error(android.R.color.darker_gray)
                    videoFrameMillis(100) // grab an early frame
                    listener(
                        onStart = { spinner.visibility = View.VISIBLE },
                        onSuccess = { _, _ -> spinner.visibility = View.GONE },
                        onError = { _, _ -> spinner.visibility = View.GONE }
                    )
                }

                // Warm the cache to reduce startup delay
                try { club.gifters.giftersclub.media.MediaCache.prefetch(itemView.context, media.url, 1_500_000L) } catch (_: Exception) {}

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

                // Wire custom controls
                bindControls()

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
                    crossfade(true)
                    placeholder(android.R.color.darker_gray)
                    error(android.R.color.darker_gray)
                    listener(
                        onStart = { spinner.visibility = View.VISIBLE },
                        onSuccess = { _, _ -> spinner.visibility = View.GONE },
                        onError = { _, _ -> spinner.visibility = View.GONE }
                    )
                }
            }
            // Keep player view non-focusable to avoid interfering with parent gestures
            playerView.isFocusable = false
        }

        private fun bindControls() {
            val p = player ?: return
            // Initialize time bar listeners
            timeBar.addListener(object : androidx.media3.ui.TimeBar.OnScrubListener {
                override fun onScrubStart(timeBar: androidx.media3.ui.TimeBar, position: Long) {
                    // Pause during scrub for precision
                    p.playWhenReady = false
                }
                override fun onScrubMove(timeBar: androidx.media3.ui.TimeBar, position: Long) {
                    p.seekTo(position)
                }
                override fun onScrubStop(timeBar: androidx.media3.ui.TimeBar, position: Long, canceled: Boolean) {
                    p.seekTo(position)
                    // Resume only if was playing before
                    p.playWhenReady = true
                    showControlsTemporarily()
                }
            })
            // Keep timebar in sync with player
            p.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    showControlsTemporarily()
                }
                override fun onEvents(player: Player, events: Player.Events) {
                    val dur = if (player.duration > 0) player.duration else 0
                    val pos = player.currentPosition
                    timeBar.setDuration(dur)
                    timeBar.setPosition(pos)
                    timeBar.setBufferedPosition(player.bufferedPosition)
                }
            })
            // Cycle speed: 0.5x -> 1x -> 2x -> 0.5x
            fun applySpeed(sp: Float) {
                p.playbackParameters = p.playbackParameters.withSpeed(sp)
                speedBtn.text = if (sp == 0.5f) "x0.5" else if (sp == 2f) "x2" else "x1"
            }
            speedBtn.setOnClickListener {
                val cur = p.playbackParameters.speed
                when {
                    cur < 0.75f -> applySpeed(1f)
                    cur < 1.5f -> applySpeed(2f)
                    else -> applySpeed(0.5f)
                }
                showControlsTemporarily()
            }
            // Single tap toggles play/pause and shows controls; double‑tap bubbles to parent to like
            val gd = android.view.GestureDetector(itemView.context, object: android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                    if (p.isPlaying) p.pause() else p.play()
                    showControlsTemporarily()
                    return true
                }
                override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                    // Do not consume; let parent PostAdapter handle double‑tap like/unlike
                    return false
                }
            })
            // Attach detector to the video surface and the thumbnail image
            val touchListener = View.OnTouchListener { _, ev ->
                gd.onTouchEvent(ev)
                false // allow parent to also receive for double‑tap
            }
            playerView.setOnTouchListener(touchListener)
            imageView.setOnTouchListener(touchListener)
        }

        private fun showControlsTemporarily() {
            controls.visibility = View.VISIBLE
            // Cancel previous hide, then schedule a new one
            hideControlsRunnable?.let { controls.removeCallbacks(it) }
            hideControlsRunnable = Runnable { controls.visibility = View.GONE }
            controls.postDelayed(hideControlsRunnable!!, 2000)
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
