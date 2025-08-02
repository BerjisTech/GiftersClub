package club.gifters.giftersclub.model

import com.google.gson.annotations.SerializedName

/**
 * Record of a user's app install/version history.
 */
data class UserApp(
    val id: String,
    @SerializedName("version_number")
    val versionNumber: Int
)