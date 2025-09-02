package club.gifters.giftersclub.media

import android.content.Context
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

object Exo {
    @Volatile private var cache: SimpleCache? = null

    private fun appCacheDir(context: Context): File = File(context.cacheDir, "media3-cache").apply { mkdirs() }

    private fun cacheInstance(context: Context): SimpleCache {
        return cache ?: synchronized(this) {
            cache ?: SimpleCache(
                appCacheDir(context),
                LeastRecentlyUsedCacheEvictor(200L * 1024L * 1024L) // 200MB
            ).also { cache = it }
        }
    }

    fun dataSourceFactory(context: Context): CacheDataSource.Factory {
        val upstream = DefaultDataSource.Factory(context)
        return CacheDataSource.Factory()
            .setCache(cacheInstance(context))
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun newPlayer(context: Context): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    dataSourceFactory(context)
                )
            )
            .build()
    }
}

