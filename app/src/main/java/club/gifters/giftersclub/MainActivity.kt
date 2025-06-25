package club.gifters.giftersclub

import club.gifters.giftersclub.network.RetrofitClient

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.View
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.widget.FrameLayout
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import club.gifters.giftersclub.gifts.GiftFragment
import club.gifters.giftersclub.gifts.LeaderboardFragment
import club.gifters.giftersclub.gifts.PostsFragment
import club.gifters.giftersclub.gifts.CreatePostFragment
import club.gifters.giftersclub.gifts.AccountFragment
import club.gifters.giftersclub.gifts.WishlistsFragment
import club.gifters.giftersclub.chat.ChatFragment
import club.gifters.giftersclub.chat.NotificationListFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RetrofitClient.init(this)
        setContentView(R.layout.activity_main)

        // Setup ViewPager + top tabs (swipeable like TikTok)
        val tabTitles = listOf("Posts", "Gifts", "Gifters", "Wishlists")
        val tabLayout = findViewById<TabLayout>(R.id.topTabLayout)
        val viewPager = findViewById<ViewPager2>(R.id.viewPagerMain)
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = tabTitles.size
            override fun createFragment(position: Int) = when (position) {
                0 -> PostsFragment()
                1 -> GiftFragment()
                2 -> LeaderboardFragment()
                3 -> WishlistsFragment()
                else -> PostsFragment()
            }
        }
        // Link TabLayout and ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
            tab.text = tabTitles[pos]
        }.attach()


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
                        .replace(R.id.mainContentContainer, ChatFragment())
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

        // Handle back-stack changes: show tabs + bottom nav on root, else show toolbar back arrow
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.topAppBar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { onBackPressed() }

        fun updateBars() {
            val isRoot = supportFragmentManager.backStackEntryCount == 0
            val current = supportFragmentManager.findFragmentById(R.id.mainContentContainer)
            val isCreatePost = current is CreatePostFragment
            tabLayout.visibility = if (isRoot) View.VISIBLE else View.GONE
            bottomNav.visibility = if (isRoot) View.VISIBLE else View.GONE
            toolbar.visibility = if (isRoot || isCreatePost) View.GONE else View.VISIBLE
            // overlay container for bottom-nav screens
            findViewById<FrameLayout>(R.id.mainContentContainer).visibility = if (isRoot) View.GONE else View.VISIBLE
            supportActionBar?.setDisplayHomeAsUpEnabled(!isRoot && !isCreatePost)
        }

        supportFragmentManager.addOnBackStackChangedListener { updateBars() }
        updateBars()
    }
}