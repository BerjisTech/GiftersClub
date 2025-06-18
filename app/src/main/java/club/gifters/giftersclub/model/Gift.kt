package club.gifters.giftersclub.model

import com.google.gson.annotations.SerializedName

/**
 * Data class representing a Gift fetched from Supabase.
 */
data class Gift(
    val id: String,
    val name: String,
    val description: String,
    val image: String,
    val tokens: Int,
    @SerializedName("is_popular") val isPopular: Boolean,
    @SerializedName("is_featured") val isFeatured: Boolean,
    @SerializedName("is_quick") val isQuick: Boolean,
    @SerializedName("is_new") val isNew: Boolean,
    @SerializedName("theme_color") val themeColor: String,
    val category: String?,
    @SerializedName("max_daily_limit") val maxDailyLimit: Int?,
    @SerializedName("cooldown_hours") val cooldownHours: Int?
)