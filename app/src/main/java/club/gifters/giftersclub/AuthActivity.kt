package club.gifters.giftersclub

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import club.gifters.giftersclub.BaseActivity
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.util.Base64
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import club.gifters.giftersclub.network.RetrofitClient
import java.net.URL

class AuthActivity : BaseActivity() {

    private val prefs by lazy { getSharedPreferences("supabase", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
            android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        supportActionBar?.hide()
        // If already signed in, go directly to posts screen
        prefs.getString("access_token", null)?.let {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_auth)

        findViewById<Button>(R.id.buttonSignIn).setOnClickListener {
            val authorizeUrl = Uri.parse(
                "${SupabaseConfig.SUPABASE_URL}/auth/v1/authorize?provider=google&redirect_to=${SupabaseConfig.REDIRECT_URI}"
            )
            startActivity(Intent(Intent.ACTION_VIEW, authorizeUrl))
        }

        intent.data?.let { data ->
            handleAuthRedirect(data)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.data?.let { data ->
            handleAuthRedirect(data)
        }
    }

    private fun handleAuthRedirect(uri: Uri) {
        // Validate the URI scheme and host to prevent deep link hijacking
        if (uri.scheme != "gifterclub" || uri.host != "login-callback") {
            return
        }

        val fragment = uri.fragment ?: return
        val params = fragment.split("&").associate { part ->
            val (key, value) = part.split("=", limit = 2)
            key to value
        }
        val accessToken = params["access_token"]
        val refreshToken = params["refresh_token"]
        if (!accessToken.isNullOrBlank()) {
            prefs.edit()
                .putString("access_token", accessToken)
                .putString("refresh_token", refreshToken)
                .apply()
        // Log sign-in via Edge Function, passing real public IP for geo lookup
        CoroutineScope(Dispatchers.IO).launch {
            // Decode user ID from JWT
            val parts = accessToken.split('.')
            val userId = parts.getOrNull(1)
                ?.let { String(Base64.decode(it, Base64.URL_SAFE)) }
                ?.let { JSONObject(it).optString("sub") } ?: return@launch
            // Fetch public IP (like Angular/ipify) to get country code server‑side
            val clientIp = try {
                val ipJson = URL("https://api.ipify.org?format=json").readText()
                JSONObject(ipJson).optString("ip")
            } catch (_: Exception) {
                ""
            }
            RetrofitClient.functionsApi.authLogRpc(
                mapOf(
                    "user_id" to userId,
                    "provider" to "google",
                    "device" to Build.MODEL,
                    "client_ip" to clientIp
                )
            )
        }
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}