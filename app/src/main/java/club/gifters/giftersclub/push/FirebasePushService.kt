package club.gifters.giftersclub.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import club.gifters.giftersclub.network.RetrofitClient
import android.util.Base64
import org.json.JSONObject
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class FirebasePushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        upsertToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // Handle foreground notifications or data-only messages if needed
    }

    private fun upsertToken(token: String) {
        val prefs = getSharedPreferences("supabase", MODE_PRIVATE)
        val access = prefs.getString("access_token", null) ?: return
        val userId = try {
            val parts = access.split('.')
            val body = String(Base64.decode(parts.getOrNull(1) ?: "", Base64.URL_SAFE))
            JSONObject(body).optString("sub")
        } catch (e: Exception) { "" }
        if (userId.isEmpty()) return
        GlobalScope.launch(Dispatchers.IO) {
            try {
                RetrofitClient.deviceTokensApi.upsert(
                    mapOf(
                        "user_id" to userId,
                        "platform" to "android",
                        "provider" to "fcm",
                        "token" to token,
                        "app_version" to try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (e: Exception) { "" }
                    )
                )
            } catch (_: Exception) {}
        }
    }
}

