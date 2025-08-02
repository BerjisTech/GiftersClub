package club.gifters.giftersclub.chat

import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.VideoView
import club.gifters.giftersclub.BaseActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import coil.load
import club.gifters.giftersclub.R

/**
 * Fullscreen viewer for image or video attachments.
 */
class FullscreenMediaActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen_media)
        val url = intent.getStringExtra("url") ?: return
        val type = intent.getStringExtra("type")
        val iv = findViewById<ImageView>(R.id.fullscreenImage)
        val vv = findViewById<VideoView>(R.id.fullscreenVideo)
        if (type == "video") {
            iv.isVisible = false
            vv.isVisible = true
            vv.setVideoURI(Uri.parse(url))
            vv.setOnPreparedListener { mp -> mp.isLooping = true }
            vv.start()
        } else {
            vv.isVisible = false
            iv.isVisible = true
            iv.load(url) { placeholder(android.R.color.darker_gray) }
        }
        // Tap to close fullscreen
        findViewById<ImageView>(R.id.fullscreenClose).setOnClickListener { finish() }
    }
}