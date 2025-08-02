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
import club.gifters.giftersclub.util.NetworkUtils

/**
 * BaseActivity that adds a global offline overlay on top of all content.
 * All Activities in the app should extend this to automatically show/hide
 * a no-network message when connectivity is lost.
 */
abstract class BaseActivity : AppCompatActivity() {
    private lateinit var offlineOverlay: View

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        val parent = findViewById<ViewGroup>(android.R.id.content)
        offlineOverlay = LayoutInflater.from(this)
            .inflate(R.layout.no_network_overlay, parent, false)
        parent.addView(offlineOverlay)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    runOnUiThread { offlineOverlay.visibility = View.GONE }
                }

                override fun onLost(network: Network) {
                    runOnUiThread { offlineOverlay.visibility = View.VISIBLE }
                }
            })
        } else {
            offlineOverlay.visibility = if (NetworkUtils.isOnline(this)) {
                View.GONE
            } else {
                View.VISIBLE
            }
        }
    }
}