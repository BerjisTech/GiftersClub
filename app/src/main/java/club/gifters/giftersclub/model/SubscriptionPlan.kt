package club.gifters.giftersclub.model

/**
 * A subscription plan defined by a creator for recurring or one-time access.
 */
data class SubscriptionPlan(
    val id: String,
    val creator_id: String,
    val name: String,
    val description: String?,
    val tokens: Int,
    val duration_type: String,
    val created_at: String,
    val updated_at: String
)