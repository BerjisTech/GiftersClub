package club.gifters.giftersclub.network

import club.gifters.giftersclub.SupabaseConfig

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit interface for Supabase Storage API (public bucket uploads).
 */
interface StorageApi {
    /**
     * Upload a file to the 'posts' bucket.
     * The filePath should include any desired subfolders and filename.
     * The Content-Type header must match the file's MIME type.
     */
    @POST("object/${SupabaseConfig.POSTS_BUCKET}/{filePath}")
    suspend fun uploadPostMedia(
        @Path(value = "filePath", encoded = true) filePath: String,
        @Body file: RequestBody,
        @Header("Content-Type") contentType: String
    ): Response<ResponseBody>

    /**
     * Upload a file to the chat-media bucket for chat attachments.
     */
    @POST("object/${SupabaseConfig.CHAT_MEDIA_BUCKET}/{filePath}")
    suspend fun uploadChatMedia(
        @Path(value = "filePath", encoded = true) filePath: String,
        @Body file: RequestBody,
        @Header("Content-Type") contentType: String
    ): Response<ResponseBody>
}