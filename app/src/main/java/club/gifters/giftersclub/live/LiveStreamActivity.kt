package club.gifters.giftersclub.live

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import club.gifters.giftersclub.R
import android.widget.FrameLayout
import android.widget.ImageView
import com.google.android.material.bottomsheet.BottomSheetBehavior

/**
 * Activity displaying the live stream UI (stream view, comments, and gift drawer).
 * UI only; functionality to be implemented.
 */
class LiveStreamActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_stream)

        val btnOpenGifts = findViewById<ImageView>(R.id.btnOpenGifts)
        val flGiftsBottomSheet = findViewById<FrameLayout>(R.id.flGiftsBottomSheet)
        val giftsBottomSheetBehavior = BottomSheetBehavior.from(flGiftsBottomSheet)
        btnOpenGifts.setOnClickListener {
            giftsBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }
}