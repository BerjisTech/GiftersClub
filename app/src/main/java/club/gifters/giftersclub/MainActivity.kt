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
import android.content.Intent
import club.gifters.giftersclub.live.LiveStreamActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.AuthActivity
import com.google.firebase.messaging.FirebaseMessaging
import club.gifters.giftersclub.gifts.GifterFragment
import club.gifters.giftersclub.social.FriendsFragment
import club.gifters.giftersclub.explore.ExploreFragment
import android.widget.ImageView
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {
    companion object {
        private const val NOTIF_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AuthUtils.getCurrentUserId(this) == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }
        RetrofitClient.init(this)
        setContentView(R.layout.activity_main)
        // Handle deep links: wishlist (/wishlist/{id}) and gifter profiles (/u/{username}, /g/{username})
        intent?.data?.let { uri ->
            val segments = uri.pathSegments
            when {
                segments.firstOrNull() == "wishlist" && segments.size > 1 -> {
                    val id = segments[1]
                    supportFragmentManager.beginTransaction()
                        .replace(
                            R.id.mainContentContainer,
                            club.gifters.giftersclub.gifts.WishlistDetailFragment.newInstance(id)
                        )
                        .addToBackStack(null)
                        .commit()
                }
                (segments.firstOrNull() == "u" || segments.firstOrNull() == "g") && segments.size > 1 -> {
                    val username = segments[1]
                    supportFragmentManager.beginTransaction()
                        .replace(
                            R.id.mainContentContainer,
                            GifterFragment.newInstance(username)
                        )
                        .addToBackStack(null)
                        .commit()
                }
            }
        }
        // On Android 13+, request runtime permission to post notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIF_PERMISSION_REQUEST_CODE
                )
            }
        }
        // Retrieve current FCM token and store it in profiles via Supabase
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val fcmToken = task.result
                    AuthUtils.getCurrentUserId(this)?.let { userId ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            RetrofitClient.profileApi.updateProfile(
                                select = "*",
                                userIdFilter = "eq.$userId",
                                updates = mapOf("fcm_token" to fcmToken)
                            )
                        }
                    }
                }
            }

        // Setup ViewPager + top tabs (swipeable like TikTok)
        val tabTitles = listOf("Posts", "Gifts", "Gifters", "Wishlists")
        val tabLayout = findViewById<TabLayout>(R.id.topTabLayout)
        val exploreIcon = findViewById<ImageView>(R.id.exploreIcon)
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
        findViewById<ImageView>(R.id.exploreIcon).setOnClickListener {
            supportFragmentManager.beginTransaction()
                .replace(R.id.mainContentContainer, ExploreFragment())
                .addToBackStack(null)
                .commit()
        }


        // Setup bottom navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavView)
        bottomNav.itemIconTintList = null
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    supportFragmentManager.popBackStack(
                        null,
                        androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
                    )
                    true
                }
                R.id.nav_friends -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.mainContentContainer, 
                            FriendsFragment.newInstance(0)
                        )
                        .addToBackStack(null)
                        .commit()
                    true
                }
                R.id.nav_create -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.mainContentContainer, CreatePostFragment())
                        .addToBackStack(null)
                        .commit()
                    true
                }
                R.id.nav_inbox -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.mainContentContainer, ChatFragment())
                        .addToBackStack(null)
                        .commit()
                    true
                }
                R.id.nav_profile -> {
                    AuthUtils.getCurrentUserId(this)?.let { uid ->
                        lifecycleScope.launch {
                            RetrofitClient.profileApi.getProfileByUserId(
                                "*", "eq.$uid"
                            ).firstOrNull()?.let { prof ->
                                supportFragmentManager.beginTransaction()
                                    .replace(
                                        R.id.mainContentContainer,
                                        GifterFragment.newInstance(prof.username)
                                    )
                                    .addToBackStack(null)
                                    .commit()
                            }
                        }
                    }
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
            exploreIcon.visibility = if (isRoot) View.VISIBLE else View.GONE
            val isExplore = current is ExploreFragment
            bottomNav.visibility = if (isCreatePost || isExplore) View.GONE else View.VISIBLE
            toolbar.visibility = if (isRoot || isCreatePost || isExplore) View.GONE else View.VISIBLE
            // overlay container for bottom-nav screens
            findViewById<FrameLayout>(R.id.mainContentContainer).visibility = if (isRoot) View.GONE else View.VISIBLE
            supportActionBar?.setDisplayHomeAsUpEnabled(!isRoot && !isCreatePost)
        }

        supportFragmentManager.addOnBackStackChangedListener { updateBars() }
        updateBars()
    }

    override fun onResume() {
        super.onResume()
        if (AuthUtils.getCurrentUserId(this) == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
        }
    }
}