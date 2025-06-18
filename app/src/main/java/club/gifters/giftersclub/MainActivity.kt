package club.gifters.giftersclub

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Setup top tabs
        val tabLayout = findViewById<TabLayout>(R.id.topTabLayout)
        listOf("Posts", "Gifts", "Leaderboard", "Wishlists").forEach { title ->
            tabLayout.addTab(tabLayout.newTab().setText(title))
        }

        // Setup bottom navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavView)
        bottomNav.setOnItemSelectedListener { item ->
            // TODO: Handle navigation item selection
            true
        }
    }
}