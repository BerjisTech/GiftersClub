package club.gifters.giftersclub.model

/**
 * Aggregated contribution info for display.
 */
data class ContributorSummary(
    val profile: Profile,
    val tokens: Int,
    val contributedAt: String
)