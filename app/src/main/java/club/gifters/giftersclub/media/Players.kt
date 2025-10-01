package club.gifters.giftersclub.media

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import club.gifters.giftersclub.AppServices

/**
 * Helpers for creating cached ExoPlayer instances.
 */
object Players {
    fun newCachedPlayer(context: Context): ExoPlayer = AppServices.newCachedPlayer(context)
}

