package club.gifters.giftersclub.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Shared media cache and helpers for caching chat videos/images.
 */
@UnstableApi
object MediaCache {
    @Volatile private var cache: Cache? = null

    private fun getCache(context: Context): Cache {
        val existing = cache
        if (existing != null) return existing
        synchronized(this) {
            val again = cache
            if (again != null) return again
            val dir = File(context.applicationContext.cacheDir, "media_cache")
            if (!dir.exists()) dir.mkdirs()
            val evictor = LeastRecentlyUsedCacheEvictor(200L * 1024 * 1024) // 200MB
            val db = StandaloneDatabaseProvider(context.applicationContext)
            val c = SimpleCache(dir, evictor, db)
            cache = c
            return c
        }
    }

    /**
     * DataSource.Factory that reads via HTTP and writes to cache.
     */
        fun cacheDataSourceFactory(context: Context): DataSource.Factory {
        val httpFactory: DataSource.Factory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
        val upstream = DefaultDataSource.Factory(context.applicationContext, httpFactory)
        val sinkFactory = CacheDataSink.Factory().setCache(getCache(context))
        return CacheDataSource.Factory()
            .setCache(getCache(context))
            .setUpstreamDataSourceFactory(upstream)
            .setCacheWriteDataSinkFactory(sinkFactory)
            .setCacheReadDataSourceFactory(FileDataSource.Factory())
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Prefetch up to [bytesToRead] from a URL into cache to speed up playback.
     */
        fun prefetch(context: Context, url: String, bytesToRead: Long = 1_500_000L) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val factory = cacheDataSourceFactory(context)
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

