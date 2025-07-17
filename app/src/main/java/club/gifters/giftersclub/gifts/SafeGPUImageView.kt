package club.gifters.giftersclub.gifts

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.AttributeSet
import android.widget.FrameLayout
import jp.co.cyberagent.android.gpuimage.GPUImageView
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter

/**
 * Safe GPUImageView that skips initialization in Android Studio layout editor to avoid preview crashes.
 *
 * At runtime, it wraps a real GPUImageView instance; in edit mode, it shows an empty placeholder.
 */
class SafeGPUImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private val gpuImageView: GPUImageView?

    init {
        if (isInEditMode) {
            gpuImageView = null
        } else {
            val view = GPUImageView(context, attrs)
            addView(
                view,
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            )
            gpuImageView = view
        }
    }

    /** Sets the filter to be applied on the image. */
    var filter: GPUImageFilter?
        get() = gpuImageView?.filter
        set(value) {
            value?.let { gpuImageView?.setFilter(it) }
        }

    /** Requests the preview to be rendered again. */
    fun requestRender() {
        gpuImageView?.requestRender()
    }

    /**
     * Captures the filtered image; must be called off the UI thread.
     * @throws InterruptedException if capture is interrupted
     */
    @Throws(InterruptedException::class)
    fun capture(): Bitmap = gpuImageView!!.capture()

    /** Sets the image on which the filter should be applied. */
    fun setImage(bitmap: Bitmap) {
        gpuImageView?.setImage(bitmap)
    }

    /** Sets the image on which the filter should be applied from a Uri. */
    fun setImage(uri: Uri) {
        gpuImageView?.setImage(uri)
    }
}