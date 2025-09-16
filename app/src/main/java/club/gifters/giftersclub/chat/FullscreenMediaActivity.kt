package club.gifters.giftersclub.chat

import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.widget.ImageView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import club.gifters.giftersclub.BaseActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import coil.load
import club.gifters.giftersclub.R
import club.gifters.giftersclub.media.Exo

/**
 * Fullscreen viewer for image or video attachments.
 */
class FullscreenMediaActivity : BaseActivity() {
    private var player: ExoPlayer? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen_media)
        val url = intent.getStringExtra("url") ?: return
        val type = intent.getStringExtra("type")
        val iv = findViewById<ImageView>(R.id.fullscreenImage)
        val pv = findViewById<PlayerView>(R.id.fullscreenPlayer)
        if (type == "video") {
            iv.isVisible = false
            pv.isVisible = true
            // Build ExoPlayer using the app-wide cached media stack
            val exo = Exo.newPlayer(this)
            pv.player = exo
            exo.repeatMode = Player.REPEAT_MODE_ONE
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            exo.playWhenReady = true
            player = exo
        } else {
            pv.isVisible = false
            iv.isVisible = true
            iv.load(url) {
                placeholder(android.R.color.darker_gray)
                memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                diskCachePolicy(coil.request.CachePolicy.ENABLED)
            }
        }
        // Tap to close fullscreen
        findViewById<ImageView>(R.id.fullscreenClose).setOnClickListener { finish() }
    }

    override fun onStop() {
        super.onStop()
        player?.release(); player = null
    }
}
