package club.gifters.giftersclub.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Base64
import androidx.core.app.NotificationCompat
import club.gifters.giftersclub.network.RetrofitClient
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class MyFirebaseMessagingService : FirebaseMessagingService() {
  override fun onNewToken(token: String) {
    super.onNewToken(token)
    CoroutineScope(Dispatchers.IO).launch {
      val prefs = getSharedPreferences("supabase", Context.MODE_PRIVATE)
      val access = prefs.getString("access_token", null) ?: return@launch
      val parts = access.split('.')
      val userId = parts.getOrNull(1)
        ?.let { String(Base64.decode(it, Base64.URL_SAFE)) }
        ?.let { JSONObject(it).optString("sub") } ?: return@launch
      RetrofitClient.profileApi.updateProfile(
        select = "*",
        userIdFilter = "eq.$userId",
        updates = mapOf("fcm_token" to token)
      )
    }
  }

  override fun onMessageReceived(message: RemoteMessage) {
    message.notification?.let {
      val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val channelId = "gifters_notifications"
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        mgr.createNotificationChannel(
          NotificationChannel(channelId, "Gifters Club", NotificationManager.IMPORTANCE_HIGH)
        )
      }
      val notif = NotificationCompat.Builder(this, channelId)
        .setContentTitle(it.title)
        .setContentText(it.body)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setAutoCancel(true)
        .build()
      mgr.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notif)
    }
  }
}