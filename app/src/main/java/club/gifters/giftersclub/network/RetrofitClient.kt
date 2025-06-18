package club.gifters.giftersclub.network

import club.gifters.giftersclub.SupabaseConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import club.gifters.giftersclub.network.LeaderboardApi

/**
 * Singleton Retrofit client configured with Supabase REST URL and API key interceptor.
 */
object RetrofitClient {
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer ${SupabaseConfig.SUPABASE_ANON_KEY}")
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
}