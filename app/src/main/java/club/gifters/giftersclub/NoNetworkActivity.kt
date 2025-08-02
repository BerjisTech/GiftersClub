package club.gifters.giftersclub

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import club.gifters.giftersclub.util.NetworkUtils

class NoNetworkActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_no_network)

        val refreshLayout = findViewById<SwipeRefreshLayout>(R.id.main)
        refreshLayout.setOnRefreshListener { isNetworkAvailable() }
    }

    private fun isNetworkAvailable(): Boolean {
        if(NetworkUtils.isOnline(this)){
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return true
        } else {
            findViewById<SwipeRefreshLayout>(R.id.main).isRefreshing = false
            return false
        }
    }
}