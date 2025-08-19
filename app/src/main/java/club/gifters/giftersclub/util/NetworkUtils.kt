package club.gifters.giftersclub.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/**
 * Utility for checking network connectivity state.
 */
object NetworkUtils {
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return true
            // Fall back to a quick socket probe when VALIDATED is unavailable but interface is up
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return probeInternet()
            false
        } else {
            @Suppress("DEPRECATION")
            val ni = cm.activeNetworkInfo
            (ni != null && ni.isConnected) && probeInternet()
        }
    }

    private fun probeInternet(): Boolean {
        return try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress("1.1.1.1", 53), 1000)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
