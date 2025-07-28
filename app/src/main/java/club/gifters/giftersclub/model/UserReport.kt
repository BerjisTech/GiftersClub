package club.gifters.giftersclub.model

data class UserReport(
    val id: String,
    val reporter_user_id: String,
    val reported_user_id: String,
    val reason: String,
    val status: String,
    val processed_by: String?,
    val processed_at: String?,
    val created_at: String,
    val updated_at: String
)