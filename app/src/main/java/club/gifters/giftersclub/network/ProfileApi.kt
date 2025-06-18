package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.Profile
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for fetching user profiles.
 */
interface ProfileApi {
    @GET("profiles")
    suspend fun getProfileByUsername(
        @Query("select") select: String = "*",
        @Query("username", encoded = true) usernameFilter: String
    ): List<Profile>
}