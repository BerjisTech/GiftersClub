package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.Attachment
import club.gifters.giftersclub.model.Message
import club.gifters.giftersclub.model.ConversationOverview
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit interface for chat operations using Supabase REST API.
 */
interface ChatApi {
    /**
     * Fetch conversation overviews for the current user.
     */
    @GET("conversation_overview")
    suspend fun getConversations(
        @Query("select", encoded = true) select: String = "user_a,user_b,last_message_at",
        @Query("or", encoded = true) userIdFilter: String
    ): List<ConversationOverview>

    /**
     * Fetch messages between two users.
     */
    @GET("messages")
    suspend fun getMessages(
        @Query("select", encoded = true) select: String = "*",
        @Query("or", encoded = true) orFilter: String,
        @Query("order", encoded = true) order: String = "created_at.asc"
    ): List<Message>

    /**
     * Fetch the most recent message in a conversation.
     */
    @GET("messages")
    suspend fun getLastMessage(
        @Query("select", encoded = true) select: String = "*",
        @Query("or", encoded = true) orFilter: String,
        @Query("order", encoded = true) order: String = "created_at.desc",
        @Query("limit") limit: Int = 1
    ): List<Message>

    /**
     * Count unread messages (where read_at is null) for a conversation.
     * Supabase returns count in the Content-Range header.
     */
    @GET("messages")
    @Headers("Prefer: count=exact", "Range-Unit: items")
    suspend fun getUnreadCount(
        @Query("select", encoded = true) select: String = "*",
        @Query("or", encoded = true) orFilter: String,
        @Query("read_at", encoded = true) readFilter: String = "is.null"
    ): Response<Void>

    /**
     * Mark all messages from sender to receiver as read by setting read_at.
     */
    @Headers("Prefer: return=representation")
    @PATCH("messages")
    suspend fun markMessagesAsRead(
        @Query("sender_id", encoded = true) senderFilter: String,
        @Query("receiver_id", encoded = true) receiverFilter: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any>
    ): Response<List<Message>>

    /**
     * Send a new message, with optional media attachments.
     */
    @Headers("Prefer: return=representation")
    @POST("messages")
    suspend fun sendMessage(
        @Body payload: Map<String, @JvmSuppressWildcards Any>
    ): Response<List<Message>>
}