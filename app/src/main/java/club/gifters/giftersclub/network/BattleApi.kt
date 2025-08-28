package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.BattleSession
import club.gifters.giftersclub.model.BattleParticipant
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

interface BattleApi {
    @Headers("Prefer: return=representation")
    @POST("battle_sessions")
    suspend fun createBattle(
        @Query("select", encoded = true) select: String = "*",
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<List<BattleSession>>

    @Headers("Prefer: return=representation")
    @PATCH("battle_sessions")
    suspend fun updateBattle(
        @Query("select", encoded = true) select: String = "*",
        @Query("id", encoded = true) idFilter: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any>
    ): Response<List<BattleSession>>

    @Headers("Prefer: return=representation")
    @POST("battle_participants")
    suspend fun addParticipant(
        @Query("select", encoded = true) select: String = "*",
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<List<BattleParticipant>>

    @GET("battle_participants")
    suspend fun getParticipants(
        @Query("select", encoded = true) select: String = "*",
        @Query("battle_id", encoded = true) battleFilter: String
    ): List<BattleParticipant>

    @PATCH("battle_participants")
    suspend fun updateParticipant(
        @Query("select", encoded = true) select: String = "*",
        @Query("id", encoded = true) idFilter: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any>
    ): Response<List<BattleParticipant>>

    @retrofit2.http.DELETE("battle_participants")
    suspend fun deleteParticipant(
        @Query("id", encoded = true) idFilter: String
    ): Response<Unit>
}
