package club.gifters.giftersclub

import android.app.Application
import android.content.Context
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import okhttp3.Cache
import okhttp3.OkHttpClient
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.File
import com.rollbar.android.Rollbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Application wiring for on-device caching across HTTP, images, and video.
 * - OkHttp: disk cache shared with Retrofit and Coil
 * - Coil: image loader with disk cache, honors CDN Cache-Control
 * - ExoPlayer: cached data source via SimpleCache for MP4/HLS segments
 */
class GiftersClubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Manifest-based initialization as per Rollbar docs
        // Requires <meta-data android:name="com.rollbar.android.ACCESS_TOKEN" android:value="${ROLLBAR_ACCESS_TOKEN}" /> in AndroidManifest
        AppServices.init(this)
        // Initialize Google Play Billing manager
        try { club.gifters.giftersclub.payments.BillingManager.init(this) } catch (_: Throwable) {}
        // Initialize Rollbar crash/error reporting if token is provided
        try {
            Rollbar.init(this)
        } catch (_: Throwable) {
            // No-op: if manifest is missing or malformed, Rollbar will not initialize
        }
        // E2EE: publish public key if logged in (app already had a session)
        try {
            val prefs = getSharedPreferences("supabase", MODE_PRIVATE)
            val access = prefs.getString("access_token", null)
            if (!access.isNullOrBlank()) {
                val parts = access.split('.')
                val userId = try {
                    val body = String(android.util.Base64.decode(parts.getOrNull(1) ?: "", android.util.Base64.URL_SAFE))
                    org.json.JSONObject(body).optString("sub")
                } catch (e: Exception) { "" }
                if (userId.isNotEmpty()) {
                    val pub = club.gifters.giftersclub.security.E2EEKeyManager.getOrCreatePublicKeyBase64(this)
                    GlobalScope.launch(Dispatchers.IO) {
                        try { club.gifters.giftersclub.network.RetrofitClient.userKeysApi.upsertKey(mapOf("user_id" to userId, "public_key" to pub)) } catch (_: Exception) {}
                    }
                    // Also upsert FCM token for unified push targeting
                    FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                        GlobalScope.launch(Dispatchers.IO) {
                            try {
                                club.gifters.giftersclub.network.RetrofitClient.deviceTokensApi.upsert(
                                    mapOf("user_id" to userId, "platform" to "android", "provider" to "fcm", "token" to token,
                                        "app_version" to try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (e: Exception) { "" })
                                )
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        } catch (_: Throwable) { }
    }
}

object AppServices {
    lateinit var okHttpClient: OkHttpClient
        private set

    lateinit var imageLoader: ImageLoader
        private set

    // Media3 cache and media source factory shared by players
    private lateinit var mediaCache: SimpleCache
    private lateinit var mediaSourceFactory: DefaultMediaSourceFactory

    fun init(context: Context) {
        // ---- OkHttp HTTP cache (100MB) ----
        val httpCacheDir = File(context.cacheDir, "http_cache").apply { mkdirs() }
        val httpCache = Cache(httpCacheDir, 100L * 1024L * 1024L)
        okHttpClient = OkHttpClient.Builder()
            .cache(httpCache)
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                // Provide default long-lived cache headers for immutable CDN media if missing.
                val url = request.url.toString()
                val looksLikeCdnMedia = url.contains("cloudfront") || url.contains("/posts/") || url.contains("/videos/")
                if (looksLikeCdnMedia && response.header("Cache-Control").isNullOrEmpty()) {
                    response.newBuilder()
                        .header("Cache-Control", "public, max-age=31536000, immutable")
                        .build()
                } else {
                    response
                }
            }
            .build()

        // ---- Coil image loader with disk cache (150MB) ----
        imageLoader = ImageLoader.Builder(context)
            .okHttpClient { okHttpClient }
            .diskCache(
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "coil_image_cache"))
                    .maxSizeBytes(150L * 1024L * 1024L)
                    .build()
            )
            .respectCacheHeaders(true)
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)

        // ---- Media3 ExoPlayer cache (512MB) ----
        val db = StandaloneDatabaseProvider(context)
        val mediaCacheDir = File(context.cacheDir, "media_cache").apply { mkdirs() }
        val evictor = LeastRecentlyUsedCacheEvictor(512L * 1024L * 1024L)
        mediaCache = SimpleCache(mediaCacheDir, evictor, db)

        val upstreamFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("GiftersClub/1.0")
        val cacheDsFactory = CacheDataSource.Factory()
            .setCache(mediaCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        mediaSourceFactory = DefaultMediaSourceFactory(cacheDsFactory)
    }

    /**
     * Create an ExoPlayer instance configured to use the shared cache.
     */
    fun newCachedPlayer(context: Context): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }
}
