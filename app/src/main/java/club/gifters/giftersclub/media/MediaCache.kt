package club.gifters.giftersclub.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import club.gifters.giftersclub.AppServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Helpers for warming the single, app-wide Media3 cache used by players.
 */
@UnstableApi
object MediaCache {
    /**
     * Prefetch up to [bytesToRead] from a URL into the shared cache
     * to speed up initial playback.
     */
    fun prefetch(context: Context, url: String, bytesToRead: Long = 1_500_000L) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val factory = AppServices.mediaCacheDataSourceFactory
                val ds = factory.createDataSource()
                val spec = androidx.media3.datasource.DataSpec.Builder()
                    .setUri(Uri.parse(url))
                    .setPosition(0)
                    .setLength(bytesToRead)
                    .build()
                ds.open(spec)
                val buffer = ByteArray(64 * 1024)
                var remaining = bytesToRead
                while (remaining > 0) {
                    val toRead = if (remaining > buffer.size) buffer.size else remaining.toInt()
                    val read = ds.read(buffer, 0, toRead)
                    if (read <= 0) break
                    remaining -= read
                }
                try { ds.close() } catch (_: Exception) {}
            } catch (_: Exception) {
                // ignore prefetch errors; playback will still stream
            }
        }
    }
}

