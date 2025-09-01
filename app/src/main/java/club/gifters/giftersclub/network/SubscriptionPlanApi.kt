package club.gifters.giftersclub.network

import club.gifters.giftersclub.model.SubscriptionPlan
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Query

/**
 * Retrofit interface for managing subscription plans in Supabase.
 */
interface SubscriptionPlanApi {
    /**
     * Fetch all subscription plans for a given creator.
     */
    @GET("subscription_plans")
    suspend fun getSubscriptionPlans(
        @Query("creator_id", encoded = true) creatorFilter: String
    ): List<SubscriptionPlan>

    /**
     * Create a new subscription plan.
     */
    @POST("subscription_plans")
    suspend fun createSubscriptionPlan(
        @Body plan: SubscriptionPlan
    ): Response<SubscriptionPlan>

    /**
     * Create via a minimal body map (avoid sending created_at/updated_at).
     */
    @Headers("Prefer: return=minimal")
    @POST("subscription_plans")
    suspend fun createSubscriptionPlanMap(
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Void>

    /**
     * Update an existing subscription plan by ID.
     */
    @PATCH("subscription_plans")
    suspend fun updateSubscriptionPlan(
        @Query("id", encoded = true) planId: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any>
    ): Response<SubscriptionPlan>

    /**
     * Delete a subscription plan by ID.
     */
    @DELETE("subscription_plans")
    suspend fun deleteSubscriptionPlan(
        @Query("id", encoded = true) planId: String
    ): Response<Unit>
}
