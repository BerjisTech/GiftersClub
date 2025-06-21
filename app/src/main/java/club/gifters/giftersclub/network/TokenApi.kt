package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.TokenTransaction
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

interface TokenApi {
    @Headers("Prefer: return=representation")
    @POST("token_transactions?select=*" )
    suspend fun recordTokenTransaction(
        @Body transaction: Map<String, @JvmSuppressWildcards Any>
    ): Response<List<TokenTransaction>>

    @Headers("Prefer: return=representation")
    @PATCH("token_transactions?select=*" )
    suspend fun updateTokenTransaction(
        @Query("id", encoded = true) idFilter: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any>
    ): Response<List<TokenTransaction>>
}