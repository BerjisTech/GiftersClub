package club.gifters.giftersclub.media

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import club.gifters.giftersclub.AppServices

/**
 * Thin wrapper that returns an ExoPlayer configured to use the single,
 * app-wide cached media stack from AppServices. This avoids multiple
 * SimpleCache instances and excess heap usage.
 */
object Exo {
    fun newPlayer(context: Context): ExoPlayer = AppServices.newCachedPlayer(context)
}
