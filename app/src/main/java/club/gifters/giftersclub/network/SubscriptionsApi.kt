package club.gifters.giftersclub.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

/**
 * Retrofit interface for checking active creator subscriptions.
 */
interface SubscriptionsApi {
    /**
     * Returns count of active subscriptions via Content-Range header.
     * Filters on creator_id, subscriber_id, status, and optional end_date conditions.
     */
    @Headers("Prefer: count=exact")
    @GET("subscriptions")
    suspend fun isSubscribed(
        @Query("creator_id", encoded = true) creatorFilter: String,
        @Query("subscriber_id", encoded = true) subscriberFilter: String,
        @Query("status", encoded = true) statusFilter: String = "eq.active",
        @Query("or", encoded = true) orFilter: String
    ): Response<Void>
}