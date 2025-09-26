package club.gifters.giftersclub.gifts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaCodec
import android.media.MediaExtractor
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
import android.media.MediaFormat
import java.nio.ByteBuffer

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
        val edited = EditedMediaItem.Builder(mediaItem)
            .setEffects(effects)
            .build()

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MediaFormat.MIMETYPE_VIDEO_AVC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    val finalFile = ensureAudioTrack(context, input, outFile)
                    val valid = finalFile?.let { validateExport(it) } == true
                    if (valid) {
                        onComplete(Result(finalFile, null))
                    } else {
                        try { outFile.delete() } catch (_: Exception) {}
                        onComplete(Result(null, IllegalStateException("Invalid MP4 output")))
                    }
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
        // Ensure even dimensions to satisfy encoders that require 2-aligned sizes.
        val w = if (videoWidth % 2 == 1) videoWidth + 1 else videoWidth
        val h = if (videoHeight % 2 == 1) videoHeight + 1 else videoHeight
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val sx = w / (overlayRoot.width.takeIf { it > 0 }?.toFloat() ?: 1f)
        val sy = h / (overlayRoot.height.takeIf { it > 0 }?.toFloat() ?: 1f)
        canvas.scale(sx, sy)
        overlayRoot.draw(canvas)
        return bmp
    }

    private fun ensureAudioTrack(context: Context, original: Uri, exported: File): File? {
        if (!validateExport(exported, requireAudio = false)) return null
        if (hasAudioTrack(exported)) return exported
        val restored = muxOriginalAudio(context, original, exported) ?: return null
        return if (validateExport(restored)) restored else null
    }

    private fun muxOriginalAudio(context: Context, original: Uri, videoOnly: File): File? {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: android.media.MediaMuxer? = null
        return try {
            videoExtractor.setDataSource(videoOnly.absolutePath)
            audioExtractor.setDataSource(context, original, null)
            val videoTrack = findTrack(videoExtractor, "video/")
            val audioTrack = findTrack(audioExtractor, "audio/")
            if (videoTrack < 0 || audioTrack < 0) return null
            videoExtractor.selectTrack(videoTrack)
            audioExtractor.selectTrack(audioTrack)

            val temp = File(videoOnly.parentFile ?: context.cacheDir, "AUD_${System.currentTimeMillis()}.mp4")
            muxer = android.media.MediaMuxer(temp.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxVideo = muxer.addTrack(videoExtractor.getTrackFormat(videoTrack))
            val muxAudio = muxer.addTrack(audioExtractor.getTrackFormat(audioTrack))
            muxer.start()

            val buffer = ByteBuffer.allocate(1 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()

            var videoBasePts = -1L
            while (true) {
                buffer.clear()
                info.offset = 0
                info.size = videoExtractor.readSampleData(buffer, 0)
                if (info.size < 0) break
                if (videoExtractor.sampleTrackIndex != videoTrack) {
                    videoExtractor.advance()
                    continue
                }
                val pts = videoExtractor.sampleTime
                if (videoBasePts < 0) videoBasePts = pts
                info.presentationTimeUs = (pts - videoBasePts).coerceAtLeast(0L)
                info.flags = videoExtractor.sampleFlags
                buffer.limit(info.offset + info.size)
                buffer.position(info.offset)
                muxer.writeSampleData(muxVideo, buffer, info)
                videoExtractor.advance()
            }

            var audioBasePts = -1L
            while (true) {
                buffer.clear()
                info.offset = 0
                info.size = audioExtractor.readSampleData(buffer, 0)
                if (info.size < 0) break
                if (audioExtractor.sampleTrackIndex != audioTrack) {
                    audioExtractor.advance()
                    continue
                }
                val pts = audioExtractor.sampleTime
                if (audioBasePts < 0) audioBasePts = pts
                info.presentationTimeUs = (pts - audioBasePts).coerceAtLeast(0L)
                info.flags = audioExtractor.sampleFlags
                buffer.limit(info.offset + info.size)
                buffer.position(info.offset)
                muxer.writeSampleData(muxAudio, buffer, info)
                audioExtractor.advance()
            }

            muxer.stop()
            val finalFile = if (videoOnly.delete()) {
                if (temp.renameTo(videoOnly)) videoOnly else temp
            } else temp
            finalFile
        } catch (_: Exception) {
            null
        } finally {
            try { muxer?.release() } catch (_: Exception) {}
            try { videoExtractor.release() } catch (_: Exception) {}
            try { audioExtractor.release() } catch (_: Exception) {}
        }
    }

    private fun findTrack(extractor: MediaExtractor, prefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith(prefix) == true) return i
        }
        return -1
    }

    private fun validateExport(file: File, requireAudio: Boolean = true): Boolean {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val w = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val d = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val f = retriever.getFrameAtTime(0)
            val hasAudio = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                ?.equals("yes", ignoreCase = true) == true
            retriever.release()
            w > 0 && h > 0 && d > 0 && f != null && (!requireAudio || hasAudio)
        } catch (_: Exception) {
            false
        }
    }

    private fun hasAudioTrack(file: File): Boolean {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val hasAudio = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                ?.equals("yes", ignoreCase = true) == true
            retriever.release()
            hasAudio
        } catch (_: Exception) {
            false
        }
    }
}
