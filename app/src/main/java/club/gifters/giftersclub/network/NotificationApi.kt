package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.Notification
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Query

/**
 * Retrofit interface for fetching and updating notifications.
 */
interface NotificationApi {
    /**
     * Fetch notifications for the current user.
     */
    @GET("notifications")
    suspend fun getNotifications(
        @Query("select", encoded = true) select: String = "*",
        @Query("user_id", encoded = true) userIdFilter: String
    ): List<Notification>

    /**
     * Mark a notification as read and return the updated record.
     */
    @Headers("Prefer: return=representation")
    @PATCH("notifications?select=*")
    suspend fun markAsRead(
        @Query("id", encoded = true) idFilter: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any>
    ): Response<List<Notification>>
    /**
     * Create a new notification record.
     */
    @Headers("Prefer: return=representation")
    @POST("notifications")
    suspend fun createNotification(
        @Body notification: Notification
    ): Response<List<Notification>>
}