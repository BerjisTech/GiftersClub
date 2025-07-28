package club.gifters.giftersclub.model

/** Represents the user_settings table row for interaction privacy */
data class UserSettings(
    val user_id: String,
    val who_can_interact: String
)