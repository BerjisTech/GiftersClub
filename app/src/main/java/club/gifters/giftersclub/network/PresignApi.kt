package club.gifters.giftersclub.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Request model for obtaining S3 presigned upload URLs.
 */
data class PresignRequest(
    val fileName: String,
    val fileType: String,
    val bucket: String,
    val overwrite: Boolean = false
)

/**
 * Response model containing presigned upload URL and public URL.
 */
data class PresignResponse(
    val uploadUrl: String,
    val key: String,
    val publicUrl: String
)

/**
 * Retrofit API for requesting S3 presigned URLs.
 */
interface PresignApi {
    /**
     * Returns a presigned PUT URL for direct upload to S3.
     * @param url Full endpoint URL for presign API.
     */
    @POST
    suspend fun getPresignedUrl(
        @Url url: String,
        @Body request: PresignRequest
    ): Response<PresignResponse>
}