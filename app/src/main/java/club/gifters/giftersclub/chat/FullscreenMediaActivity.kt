package club.gifters.giftersclub.chat

import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
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
            vv.setOnPreparedListener { mp ->
                mp.isLooping = true
                // Fit video inside the screen without stretching (letterbox if needed)
                val videoW = mp.videoWidth.takeIf { it > 0 } ?: return@setOnPreparedListener
                val videoH = mp.videoHeight.takeIf { it > 0 } ?: return@setOnPreparedListener
                val metrics = DisplayMetrics()
                windowManager.defaultDisplay.getRealMetrics(metrics)
                val screenW = metrics.widthPixels
                val screenH = metrics.heightPixels
                val videoRatio = videoW.toFloat() / videoH
                val screenRatio = screenW.toFloat() / screenH
                val targetW: Int
                val targetH: Int
                if (videoRatio > screenRatio) {
                    // Limited by width
                    targetW = screenW
                    targetH = (screenW / videoRatio).toInt()
                } else {
                    // Limited by height
                    targetH = screenH
                    targetW = (screenH * videoRatio).toInt()
                }
                val lp = vv.layoutParams
                lp.width = targetW
                lp.height = targetH
                vv.layoutParams = lp
                vv.requestLayout()
            }
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
