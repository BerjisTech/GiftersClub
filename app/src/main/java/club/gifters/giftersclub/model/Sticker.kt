package club.gifters.giftersclub.model

import com.google.gson.annotations.SerializedName

/**
 * Remote sticker row fetched from Supabase (public.stickers)
 */
data class Sticker(
    val id: String,
    val name: String,
    @SerializedName("image_url") val imageUrl: String,
    @SerializedName("is_active") val isActive: Boolean = true,
    @SerializedName("sort_index") val sortIndex: Int? = null
)

