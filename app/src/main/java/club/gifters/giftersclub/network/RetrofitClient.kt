package club.gifters.giftersclub.network

import android.content.Context

import club.gifters.giftersclub.SupabaseConfig
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import club.gifters.giftersclub.network.LeaderboardApi
import club.gifters.giftersclub.network.PostApi
import club.gifters.giftersclub.network.ProfileApi
import club.gifters.giftersclub.network.StorageApi
import club.gifters.giftersclub.network.GiftApi
import club.gifters.giftersclub.network.ChatApi

/**
 * Singleton Retrofit client configured with Supabase REST URL and API key interceptor.
 */
object RetrofitClient {
    private var context: Context? = null

    fun init(context: Context) {
        this.context = context.applicationContext
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val prefs = context?.getSharedPreferences("supabase", Context.MODE_PRIVATE)
            val accessToken = prefs?.getString("access_token", null)
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
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("${SupabaseConfig.SUPABASE_URL}/rest/v1/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val giftApi: GiftApi = retrofit.create(GiftApi::class.java)
    val leaderboardApi: LeaderboardApi = retrofit.create(LeaderboardApi::class.java)
    val profileApi: ProfileApi = retrofit.create(ProfileApi::class.java)
    val postApi:    PostApi    = retrofit.create(PostApi::class.java)
    /**
     * Supabase Storage API client for uploading to public buckets.
     */
    private val storageRetrofit = Retrofit.Builder()
        .baseUrl("${SupabaseConfig.SUPABASE_URL}/storage/v1/")
        .client(client)
        .build()

    val storageApi: StorageApi = storageRetrofit.create(StorageApi::class.java)
    /**
     * Chat API for sending and retrieving messages and conversations.
     */
    val chatApi: ChatApi = retrofit.create(ChatApi::class.java)
}