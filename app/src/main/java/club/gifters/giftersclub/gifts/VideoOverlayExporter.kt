package club.gifters.giftersclub.gifts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.View
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
// OverlaySettings not needed with Media3 1.8; use default placement
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.Composition
import java.io.File

/**
 * Exports a video with a static bitmap overlay (captured from the editor overlay view).
 * Uses Media3 Transformer + Effects (hardware accelerated where possible).
 */
@UnstableApi
object VideoOverlayExporter {

    data class Result(val output: File?, val error: Throwable? = null)

    fun export(
        context: Context,
        input: Uri,
        overlayBitmap: Bitmap,
        onProgress: ((Float) -> Unit)? = null,
        onComplete: (Result) -> Unit
    ) {
        val outFile = File(context.cacheDir, "VID_OVL_${System.currentTimeMillis()}.mp4")
        val mediaItem = MediaItem.fromUri(input)

        val bitmapOverlay = BitmapOverlay.createStaticBitmapOverlay(overlayBitmap)
        val overlays: com.google.common.collect.ImmutableList<TextureOverlay> =
            com.google.common.collect.ImmutableList.of(bitmapOverlay as TextureOverlay)
        val overlayEffect = OverlayEffect(overlays)
        val effects = Effects(/*audioEffects=*/ emptyList(), /*videoEffects=*/ listOf(overlayEffect))
        val edited = EditedMediaItem.Builder(mediaItem).setEffects(effects).build()

        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    onComplete(Result(outFile, null))
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exception: ExportException) {
                    onComplete(Result(null, exception))
                }
            })
            .build()

        transformer.start(edited, outFile.absolutePath)
    }

    /**
     * Render a `View` hierarchy (e.g. the edit overlay container) into a bitmap sized to the target video size.
     */
    fun renderOverlayBitmap(overlayRoot: View, videoWidth: Int, videoHeight: Int): Bitmap {
        val bmp = Bitmap.createBitmap(videoWidth, videoHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val sx = videoWidth / (overlayRoot.width.takeIf { it > 0 }?.toFloat() ?: 1f)
        val sy = videoHeight / (overlayRoot.height.takeIf { it > 0 }?.toFloat() ?: 1f)
        canvas.scale(sx, sy)
        overlayRoot.draw(canvas)
        return bmp
    }
}
