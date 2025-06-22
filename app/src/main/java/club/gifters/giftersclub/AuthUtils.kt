package club.gifters.giftersclub

import android.content.Context
import android.util.Base64
import org.json.JSONObject

/**
 * Utilities for extracting information from the Supabase JWT stored in SharedPreferences.
 */
object AuthUtils {
    /**
     * Decodes the stored access_token (JWT) and returns the 'sub' field (user_id).
     */
    fun getCurrentUserId(context: Context?): String? {
        val prefs = context?.getSharedPreferences("supabase", Context.MODE_PRIVATE)
            ?: return null
        val token = prefs.getString("access_token", null) ?: return null
        return try {
            val parts = token.split('.')
            if (parts.size < 2) return null
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
            JSONObject(payload).optString("sub")
        } catch (_: Exception) {
            null
        }
    }
}