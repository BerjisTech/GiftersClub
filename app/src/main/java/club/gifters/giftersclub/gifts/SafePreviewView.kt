package club.gifters.giftersclub.gifts

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView

/**
 * Safe PreviewView that skips display listener registration in Android Studio layout editor to prevent preview crashes.
 *
 * At runtime, it wraps a real PreviewView instance; in edit mode, it shows an empty placeholder.
 */
class SafePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val previewView: PreviewView?

    init {
        if (isInEditMode) {
            previewView = null
        } else {
            val view = PreviewView(context, attrs, defStyleAttr)
            addView(
                view,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            )
            previewView = view
        }
    }

    /** Surface provider for CameraX Preview use case. */
    val surfaceProvider: Preview.SurfaceProvider?
        get() = previewView?.surfaceProvider
}