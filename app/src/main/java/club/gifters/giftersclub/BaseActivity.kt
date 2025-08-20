package club.gifters.giftersclub

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import club.gifters.giftersclub.R
import club.gifters.giftersclub.util.NetworkUtils
import club.gifters.giftersclub.NoNetworkActivity
import android.content.Intent
import club.gifters.giftersclub.live.LiveStreamActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.card.MaterialCardView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.network.RetrofitClient
import android.net.Uri

/**
 * BaseActivity that adds a global offline overlay on top of all content.
 * All Activities in the app should extend this to automatically show/hide
 * a no-network message when connectivity is lost.
 */
abstract class BaseActivity : AppCompatActivity() {
    private lateinit var offlineOverlay: View
    private lateinit var cm: ConnectivityManager

    override fun setContentView(layoutResID: Int) {
        // If there's no network, show the overlay immediately
        if (!NetworkUtils.isOnline(this)) {
            startActivity(Intent(this, NoNetworkActivity::class.java))
            finish()
            return
        }
        super.setContentView(layoutResID)
        // Inject global no-network overlay
        val parent = findViewById<ViewGroup>(android.R.id.content)
        offlineOverlay = LayoutInflater.from(this)
            .inflate(R.layout.no_network_overlay, parent, false)
        parent.addView(offlineOverlay)
        // global live-banner only on non-live screens
        if (this !is LiveStreamActivity) {
            val hostBanner = LayoutInflater.from(this)
                .inflate(R.layout.live_host_banner, parent, false) as MaterialCardView
            parent.addView(hostBanner)
            hostBanner.visibility = View.GONE

            lifecycleScope.launch {
                val uid = AuthUtils.getCurrentUserId(this@BaseActivity) ?: return@launch
                try {
                    val active = RetrofitClient.liveStreamApi.getLiveStreamsByHosts(
                        select = "id",
                        hostFilter = "eq.$uid",
                        statusFilter = "eq.live",
                        order = "updated_at.desc"
                    )
                    if (active.isNotEmpty()) {
                        val sid = active[0].id
                        hostBanner.visibility = View.VISIBLE
                        hostBanner.setOnClickListener {
                            val uri = Uri.parse("https://gifters.club/live/$sid")
                            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                setClassName(
                                    this@BaseActivity,
                                    "club.gifters.giftersclub.live.LiveStreamActivity"
                                )
                            }
                            startActivity(intent)
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // Setup network callback after overlay is available
        cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    offlineOverlay.visibility = View.GONE
                    if (this@BaseActivity is NoNetworkActivity) finish()
                }
            }

            override fun onLost(network: Network) {
                runOnUiThread {
                    // Posts tab (0) uses its own overlay; hide global here
                    if (this@BaseActivity is MainActivity &&
                        findViewById<ViewPager2>(R.id.viewPagerMain).currentItem == 0
                    ) {
                        offlineOverlay.visibility = View.GONE
                    } else if (this@BaseActivity !is NoNetworkActivity) {
                        startActivity(
                            Intent(this@BaseActivity, NoNetworkActivity::class.java)
                        )
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cm.registerDefaultNetworkCallback(callback)
            // initial offline check
            if (!NetworkUtils.isOnline(this)) callback.onLost(cm.activeNetwork ?: return)
        } else {
            if (NetworkUtils.isOnline(this)) {
                // send to NoNetworkActivity if already offline
                offlineOverlay.visibility = View.GONE
            } else {
                startActivity(Intent(this, NoNetworkActivity::class.java))
                finish()
            }
        }
    }
}