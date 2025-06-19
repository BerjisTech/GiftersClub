package club.gifters.giftersclub

import club.gifters.giftersclub.network.RetrofitClient

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import club.gifters.giftersclub.gifts.GiftFragment
import club.gifters.giftersclub.gifts.LeaderboardFragment
import club.gifters.giftersclub.gifts.PostsFragment
import club.gifters.giftersclub.gifts.CreatePostFragment
import club.gifters.giftersclub.gifts.AccountFragment
import club.gifters.giftersclub.chat.ConversationListFragment
import club.gifters.giftersclub.chat.NotificationListFragment

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RetrofitClient.init(this)
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
                    0 -> PostsFragment()
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
        if (savedInstanceState == null) {
            tabLayout.getTabAt(0)?.select()
            // Ensure Posts tab loads immediately
            supportFragmentManager.beginTransaction()
                .replace(R.id.mainContentContainer, PostsFragment())
                .commit()
        }

        // Setup bottom navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavView)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_new_post -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.mainContentContainer, CreatePostFragment())
                        .addToBackStack(null)
                        .commit()
                    true
                }
                R.id.nav_account -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.mainContentContainer, AccountFragment())
                        .addToBackStack(null)
                        .commit()
                    true
                }
                R.id.nav_chat -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.mainContentContainer, ConversationListFragment())
                        .addToBackStack(null)
                        .commit()
                    true
                }
                R.id.nav_notifications -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.mainContentContainer, NotificationListFragment())
                        .addToBackStack(null)
                        .commit()
                    true
                }
                else -> true
            }
        }
    }
}