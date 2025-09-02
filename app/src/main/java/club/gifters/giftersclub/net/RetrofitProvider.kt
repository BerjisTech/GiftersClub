package club.gifters.giftersclub.net

import club.gifters.giftersclub.AppServices
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Central Retrofit provider using the shared OkHttp client with disk caching.
 */
object RetrofitProvider {
    @Volatile
    private var retrofit: Retrofit? = null

    /**
     * Returns a Retrofit instance for the given baseUrl using the shared OkHttp client.
     * Use different instances if you have multiple base URLs.
     */
    fun get(baseUrl: String): Retrofit {
        val existing = retrofit
        if (existing != null && existing.baseUrl().toUrl().toString() == baseUrl) return existing

        synchronized(this) {
            val again = retrofit
            if (again != null && again.baseUrl().toUrl().toString() == baseUrl) return again
            val client: OkHttpClient = AppServices.okHttpClient
            val built = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            retrofit = built
            return built
        }
    }
}

