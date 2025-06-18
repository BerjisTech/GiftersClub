package club.gifters.giftersclub

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import club.gifters.giftersclub.gifts.GiftFragment
import club.gifters.giftersclub.gifts.LeaderboardFragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Setup top tabs
        val tabLayout = findViewById<TabLayout>(R.id.topTabLayout)
        listOf("Posts", "Gifts", "Leaderboard", "Wishlists").forEach { title ->
            tabLayout.addTab(tabLayout.newTab().setText(title))
        }
        // Load fragment according to selected tab
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val frag = when (tab.position) {
                    1 -> GiftFragment()
                    2 -> LeaderboardFragment()
                    else -> null // TODO: implement other fragments
                }
                frag?.let {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.mainContentContainer, it)
                        .commit()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        // Show default tab
        tabLayout.getTabAt(0)?.select()

        // Setup bottom navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavView)
        bottomNav.setOnItemSelectedListener { item ->
            // TODO: Handle navigation item selection
            true
        }
    }
}