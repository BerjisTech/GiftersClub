package club.gifters.giftersclub

import club.gifters.giftersclub.network.RetrofitClient
import android.os.Bundle
import club.gifters.giftersclub.BaseActivity
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
import club.gifters.giftersclub.CreateOrGoLiveBottomSheetFragment
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
import androidx.cardview.widget.CardView
import android.widget.Button
import android.widget.TextView
import android.net.Uri
import club.gifters.giftersclub.util.NetworkUtils
import kotlinx.coroutines.withContext
import java.io.IOException
import retrofit2.HttpException
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieCompositionFactory
import android.animation.Animator

class MainActivity : BaseActivity() {
    companion object {
        private const val NOTIF_PERMISSION_REQUEST_CODE = 1001
        /** Intent extra to reopen the Create/Go-Live bottom sheet when returning here */
        const val EXTRA_SHOW_CREATE_SHEET = "EXTRA_SHOW_CREATE_SHEET"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // If offline at startup, redirect to NoNetworkActivity and abort
        if (!NetworkUtils.isOnline(this)) {
            startActivity(Intent(this, NoNetworkActivity::class.java))
            finish()
            return
        }
        // If no internet redirect to NoNetworkActivity
        if (!NetworkUtils.isOnline(this)) {
            startActivity(Intent(this, NoNetworkActivity::class.java))
            finish()
        }
        if (AuthUtils.getCurrentUserId(this) == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_main)
        // If returning here after canceling live-stream setup, re-open create/go-live sheet
        if (intent.getBooleanExtra(EXTRA_SHOW_CREATE_SHEET, false)) {
            CreateOrGoLiveBottomSheetFragment()
                .show(supportFragmentManager, CreateOrGoLiveBottomSheetFragment.TAG)
        }
        RetrofitClient.init(this)



        // Track and compare installed version against DB records for update prompting
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val userId = AuthUtils.getCurrentUserId(this@MainActivity)
                if (userId == null || userId.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        startActivity(Intent(this@MainActivity, AuthActivity::class.java))
                        finish()
                    }
                    return@launch
                }
                val platform = "android"
                val pkgInfo = packageManager.getPackageInfo(packageName, 0)
                val currentVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    pkgInfo.versionCode
                }

                // Get the user's last recorded app entry
                val last = RetrofitClient.userAppsApi.queryUserApps(
                    select = "id,version_number",
                    userId = "eq.$userId",
                    platform = "eq.$platform",
                    order = "date_installed.desc",
                    limit = 1
                ).firstOrNull()
                if (last?.versionNumber != currentVersion) {
                    last?.let {
                        RetrofitClient.userAppsApi.updateUserApp(
                            id = "eq.${it.id}",
                            updates = mapOf("updated_at" to "now()")
                        )
                    }
                    RetrofitClient.userAppsApi.insertUserApp(
                        mapOf(
                            "user_id" to userId,
                            "platform" to platform,
                            "version_number" to currentVersion,
                            "date_installed" to "now()"
                        )
                    )
                }

                // Get the latest published version (developer updates this record)
                val latestVersion = RetrofitClient.userAppsApi.queryUserApps(
                    select = "version_number",
                    platform = "eq.$platform",
                    order = "version_number.desc",
                    limit = 1
                ).firstOrNull()?.versionNumber ?: currentVersion

                if (currentVersion < latestVersion) {
                    withContext(Dispatchers.Main) {
                        val banner = findViewById<CardView>(R.id.updateBanner)
                        val text = findViewById<TextView>(R.id.updateBannerText)
                        val btn = findViewById<Button>(R.id.updateBannerButton)
                        val diff = latestVersion - currentVersion
                        banner.visibility = View.VISIBLE
                        text.text =
                            "You are $diff version${if (diff > 1) "s" else ""} behind. " +
                            "Update GiftersClub for the best experience."
                        btn.setOnClickListener {
                            startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("market://details?id=$packageName")
                                )
                            )
                        }
                    }
                }
            } catch (ioe: IOException) {
            } catch (e: HttpException) {
            }
        }
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
                            try {
                                RetrofitClient.profileApi.updateProfile(
                                    select = "*",
                                    userIdFilter = "eq.$userId",
                                    updates = mapOf("fcm_token" to fcmToken)
                                )
                            } catch (ioe: IOException) {
                            } catch (e: HttpException) {
                            }
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
            // check current fragment type and set padding accordingly
            override fun getItemId(position: Int): Long {
                return position.toLong()
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

        // if viewpager tab is PostFragment, set layout_constraintTop_toBottomOf to @+id/topAppBar else set it to @+id/topTabLayout
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == 0) {
                    viewPager.setPadding(0, 0, 0, 0)
                } else {
                    viewPager.setPadding(0, 150, 0, 0)
                }
            }
        })

        // Setup bottom navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavView)
        bottomNav.itemIconTintList = null
        bottomNav.setOnItemSelectedListener { item ->
            if (!NetworkUtils.isOnline(this)) {
                startActivity(Intent(this, NoNetworkActivity::class.java))
                return@setOnItemSelectedListener true
            }
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
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
                    CreateOrGoLiveBottomSheetFragment().show(supportFragmentManager, CreateOrGoLiveBottomSheetFragment.TAG)
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
        // If no internet redirect to NoNetworkActivity
        if (!NetworkUtils.isOnline(this)) {
            startActivity(Intent(this, NoNetworkActivity::class.java))
            finish()
        }
        // If no user logged in, redirect to AuthActivity
        if (AuthUtils.getCurrentUserId(this) == null) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
        }
    }

    /**
     * Show a full-screen Lottie animation from a URL, then hide overlay when done.
     */
    fun showLottieAnimation(url: String) {
        val overlay = findViewById<FrameLayout>(R.id.animationOverlayContainer)
        val lottieView = findViewById<LottieAnimationView>(R.id.overlayLottieView)
        overlay.visibility = View.VISIBLE
        lottieView.visibility = View.VISIBLE
        LottieCompositionFactory.fromUrl(this, url).addListener { composition ->
            lottieView.setComposition(composition)
            lottieView.playAnimation()
        }
        lottieView.addAnimatorListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                lottieView.removeAllAnimatorListeners()
                lottieView.visibility = View.GONE
                overlay.visibility = View.GONE
            }
            override fun onAnimationCancel(animation: Animator) {
                onAnimationEnd(animation)
            }
            override fun onAnimationRepeat(animation: Animator) {}
        })
    }
}