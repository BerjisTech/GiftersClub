Android Caching Guide (Images, Video, HTTP)

Summary: Configure OkHttp/Retrofit, Coil, and Media3 ExoPlayer to cache aggressively while respecting CloudFront/S3 headers. Prefer versioned media URLs so you can keep long TTLs safely.

1) HTTP Cache (OkHttp)

Create a shared OkHttp client with a disk `Cache` (50–100MB+), and interceptors that respect server Cache-Control and add conservative defaults for CDN assets when missing.

Kotlin (e.g., in `GiftersClubApp`):

val cacheDir = File(applicationContext.cacheDir, "http_cache").apply { mkdirs() }
val cache = Cache(cacheDir, 100L * 1024L * 1024L) // 100MB

val client = OkHttpClient.Builder()
  .cache(cache)
  .addNetworkInterceptor { chain ->
    val resp = chain.proceed(chain.request())
    // Respect server headers, but provide a default for immutable media paths
    val url = chain.request().url.toString()
    val isCdnMedia = url.contains("cloudfront") || url.contains("/posts/") || url.contains("/videos/")
    if (isCdnMedia && resp.header("Cache-Control").isNullOrEmpty()) {
      resp.newBuilder()
        .header("Cache-Control", "public, max-age=31536000, immutable")
        .build()
    } else resp
  }
  .build()

// Retrofit example
val retrofit = Retrofit.Builder()
  .baseUrl("https://api.gifters.club/")
  .client(client)
  .addConverterFactory(GsonConverterFactory.create())
  .build()

2) Image Caching (Coil)

Coil already includes memory + disk caching. Provide a custom `ImageLoader` that shares the OkHttp client. Add a dedicated disk cache dir and enable crossfade.

val imageLoader = ImageLoader.Builder(this)
  .okHttpClient { client }
  .diskCache(
    DiskCache.Builder()
      .directory(File(cacheDir, "coil_image_cache"))
      .maxSizeBytes(150L * 1024L * 1024L) // 150MB
      .build()
  )
  .respectCacheHeaders(true)
  .crossfade(true)
  .build()
Coil.setImageLoader(imageLoader)

Usage:
imageView.load(url) {
  placeholder(R.drawable.profile)
  error(R.drawable.profile)
}

3) Video Caching (Media3 ExoPlayer)

Use a shared `SimpleCache` with LRU eviction and a `CacheDataSource.Factory` so the player reads/writes cached data. This benefits both MP4 and HLS (segment-level) playback.

val db = StandaloneDatabaseProvider(this)
val cache = SimpleCache(
  File(cacheDir, "media_cache"),
  LeastRecentlyUsedCacheEvictor(512L * 1024L * 1024L), // 512MB
  db
)

val httpDsFactory = DefaultHttpDataSource.Factory()
  .setUserAgent("GiftersClub/1.0")

val cacheDsFactory = CacheDataSource.Factory()
  .setCache(cache)
  .setUpstreamDataSourceFactory(httpDsFactory)
  .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

val mediaSourceFactory = DefaultMediaSourceFactory(cacheDsFactory)

val player = ExoPlayer.Builder(this)
  .setMediaSourceFactory(mediaSourceFactory)
  .build()

// Example: HLS
player.setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
player.prepare()

4) Prefetch / Offline (optional)

For guaranteed smoothness on slow networks, prefetch media:
- Small images: enqueue lightweight GETs via OkHttp/Coil (they’ll hit disk cache).
- Videos: use Media3 DownloadManager/Downloader to prefetch key HLS segments or first N MB of MP4.
- Schedule via WorkManager with constraints (Wi‑Fi, charging).

5) Invalidation Strategy

Prefer versioned URLs from your backend/CDN (include hash or timestamp). This lets the app cache indefinitely; when content changes, the URL changes and the new resource is fetched.

6) Suggested Sizes

- OkHttp cache: 50–100MB minimum.
- Coil disk cache: 150–300MB if image‑heavy.
- ExoPlayer media cache: 256–1024MB depending on typical session length.

Notes

- These caches respect CloudFront/S3 Cache‑Control headers. Ensure the CDN sends `public, max-age=31536000, immutable` for immutable media.
- Use HLS for adaptive video so ExoPlayer can select the right bitrate for the user’s network conditions.

