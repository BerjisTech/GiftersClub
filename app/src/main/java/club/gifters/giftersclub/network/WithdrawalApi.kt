package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.WithdrawalRequest
import kotlin.jvm.JvmSuppressWildcards
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit interface for fetching and requesting withdrawals.
 */
interface WithdrawalApi {
    /**
     * Fetch withdrawal requests for the specified user.
     */
    @GET("withdrawals")
    suspend fun getWithdrawalsByUser(
        @Query("select", encoded = true) select: String = "*",
        @Query("user_id", encoded = true) userId: String,
        @Query("order",    encoded = true) order: String = "created_at.desc"
    ): List<WithdrawalRequest>

    /**
     * Call stored procedure to request a withdrawal; returns the created withdrawal record.
     */
    @POST("rpc/request_withdrawal")
    suspend fun requestWithdrawal(
        @Body params: Map<String, @JvmSuppressWildcards Any>
    ): WithdrawalRequest
}