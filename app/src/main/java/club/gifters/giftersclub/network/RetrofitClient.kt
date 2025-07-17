package club.gifters.giftersclub.network

import android.content.Context
import club.gifters.giftersclub.AwsConfig
import club.gifters.giftersclub.SupabaseConfig
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Singleton Retrofit client configured with Supabase REST URL and API key interceptor.
 */
private class TokenRefreshAuthenticator : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) > 1) return null

        val context = RetrofitClient.context ?: return null
        val prefs = context.getSharedPreferences("supabase", Context.MODE_PRIVATE)
        val refreshToken = prefs.getString("refresh_token", null) ?: return null

        val url = "${SupabaseConfig.SUPABASE_URL}/auth/v1/token?grant_type=refresh_token"
        val bodyJson = JSONObject().put("refresh_token", refreshToken).toString()
        val requestBody = bodyJson.toRequestBody("application/json".toMediaTypeOrNull())
        val refreshRequest = okhttp3.Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Content-Type", "application/json")
            .build()

        val client = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val refreshResponse = try {
            client.newCall(refreshRequest).execute()
        } catch (e: Exception) {
            prefs.edit().remove("access_token").remove("refresh_token").apply()
            return null
        }

        if (!refreshResponse.isSuccessful) {
            prefs.edit().remove("access_token").remove("refresh_token").apply()
            return null
        }

        val body = refreshResponse.body?.string() ?: return null
        val json = JSONObject(body)
        val newAccess = json.optString("access_token")
        val newRefresh = json.optString("refresh_token")
        if (newAccess.isBlank() || newRefresh.isBlank()) {
            prefs.edit().remove("access_token").remove("refresh_token").apply()
            return null
        }

        prefs.edit()
            .putString("access_token", newAccess)
            .putString("refresh_token", newRefresh)
            .apply()

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newAccess")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}

object RetrofitClient {
    var context: Context? = null

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        // Log all REST requests and responses for debugging
        .addInterceptor { chain ->
            val request = chain.request()
            println("REST → ${request.method} ${request.url}")
            val response = chain.proceed(request)
            println("REST ← ${response.code} ${response.request.url}")
            response
        }
        .addInterceptor { chain ->
            val accessToken = context
                ?.getSharedPreferences("supabase", Context.MODE_PRIVATE)
                ?.getString("access_token", null)
            val authHeader = if (!accessToken.isNullOrBlank()) {
                "Bearer $accessToken"
            } else {
                "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}"
            }
            val request = chain.request().newBuilder()
                .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", authHeader)
                .build()
            chain.proceed(request)
        }
        .authenticator(TokenRefreshAuthenticator())
        .build()

    /**
     * Base REST URL for Supabase PostgREST endpoints.
     */
    const val BASE_URL = "${SupabaseConfig.SUPABASE_URL}/rest/v1/"
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val giftApi: GiftApi = retrofit.create(GiftApi::class.java)
    val leaderboardApi: LeaderboardApi = retrofit.create(LeaderboardApi::class.java)
    val profileApi: ProfileApi = retrofit.create(ProfileApi::class.java)
    val postApi:    PostApi    = retrofit.create(PostApi::class.java)
    /**
     * API for checking purchased post access.
     */
    val postAccessApi: PostAccessApi = retrofit.create(PostAccessApi::class.java)
    /**
     * Chat API for sending and retrieving messages and conversations.
     */
    val chatApi: ChatApi = retrofit.create(ChatApi::class.java)
    /**
     * Notifications API for fetching and marking read notifications.
     */
    val notificationApi: NotificationApi = retrofit.create(NotificationApi::class.java)

    /**
     * API for fetching wishlists of a user.
     */
    val wishlistApi: WishlistApi = retrofit.create(WishlistApi::class.java)
    val liveStreamApi: LiveStreamApi = retrofit.create(LiveStreamApi::class.java)
    val withdrawalApi: WithdrawalApi = retrofit.create(WithdrawalApi::class.java)
    val tokenApi: TokenApi = retrofit.create(TokenApi::class.java)
    val followsApi: FollowsApi = retrofit.create(FollowsApi::class.java)

    /**
     * API for querying the recent_gifts view (gifts received by user).
     */
    val recentGiftsApi: RecentGiftsApi = retrofit.create(RecentGiftsApi::class.java)

    private val functionsRetrofit = Retrofit.Builder()
        .baseUrl("${SupabaseConfig.SUPABASE_URL}/functions/v1/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    val subscriptionsApi: SubscriptionsApi = retrofit.create(SubscriptionsApi::class.java)
    val functionsApi: FunctionsApi = functionsRetrofit.create(FunctionsApi::class.java)
    val commentApi: club.gifters.giftersclub.gifts.CommentApi = retrofit.create(club.gifters.giftersclub.gifts.CommentApi::class.java)
    /**
     * API client for search query suggestions.
     */
    val searchQueriesApi: SearchQueriesApi = retrofit.create(SearchQueriesApi::class.java)

    // AWS S3 presigned URL API for media uploads (requires Supabase JWT auth)
    val awsClient = client.newBuilder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            // Host for our presign-Lambda endpoint (API Gateway)
            val presignHost = AwsConfig.API_URL
                .removePrefix("https://").removePrefix("http://").substringBefore('/')

            // Start with a clean builder (strip any supabase headers)
            val builder = original.newBuilder()
                .removeHeader("apikey")
                .removeHeader("Authorization")

            // Only re-add the Supabase JWT when requesting a presigned URL
            if (original.url.host == presignHost) {
                context
                    ?.getSharedPreferences("supabase", Context.MODE_PRIVATE)
                    ?.getString("access_token", null)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { builder.addHeader("Authorization", "Bearer $it") }
            }

            val request = builder.build()
            println("AWS → ${request.method} ${request.url}")
            val response = chain.proceed(request)
            println("AWS ← ${response.code} ${response.request.url}")
            response
        }
        .build()

    private val awsRetrofit = Retrofit.Builder()
        .baseUrl(club.gifters.giftersclub.AwsConfig.API_URL)
        .client(awsClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val presignApi: PresignApi = awsRetrofit.create(PresignApi::class.java)
    /**
     * API for searching tags (hashtags) for explore suggestions.
     */
    val tagApi: TagApi = retrofit.create(TagApi::class.java)
}