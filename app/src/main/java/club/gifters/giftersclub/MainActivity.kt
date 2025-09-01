package club.gifters.giftersclub

import android.Manifest
import android.animation.Animator
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import club.gifters.giftersclub.chat.ChatFragment
import club.gifters.giftersclub.explore.ExploreFragment
import club.gifters.giftersclub.gifts.CreatePostFragment
import club.gifters.giftersclub.gifts.GiftFragment
import club.gifters.giftersclub.gifts.GifterFragment
import club.gifters.giftersclub.gifts.LeaderboardFragment
import club.gifters.giftersclub.gifts.PostsFragment
import club.gifters.giftersclub.gifts.WishlistsFragment
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.util.NetworkUtils
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieCompositionFactory
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

class MainActivity : BaseActivity() {
    companion object {
        private const val NOTIF_PERMISSION_REQUEST_CODE = 1001
        /** Intent extra to reopen the Create/Go-Live bottom sheet when returning here */
        const val EXTRA_SHOW_CREATE_SHEET = "EXTRA_SHOW_CREATE_SHEET"
        /** Intent extra to open Settings at a particular tab index */
        const val EXTRA_OPEN_SETTINGS_TAB = "EXTRA_OPEN_SETTINGS_TAB"
        /** Intent extra to immediately open the current user's profile */
        const val EXTRA_OPEN_PROFILE = "EXTRA_OPEN_PROFILE"
    }

    private fun enlargeCreateItem(bottomNav: BottomNavigationView) {
        // Ensure clicking the menu item opens create
        bottomNav.menu.findItem(R.id.nav_create)?.setOnMenuItemClickListener {
            CreateOrGoLiveBottomSheetFragment().show(supportFragmentManager, CreateOrGoLiveBottomSheetFragment.TAG)
            true
        }
        bottomNav.post {
            val menu = bottomNav.menu
            val menuView = bottomNav.getChildAt(0) as? ViewGroup ?: return@post
            var createIndex = -1
            for (i in 0 until menu.size()) {
                if (menu.getItem(i).itemId == R.id.nav_create) { createIndex = i; break }
            }
            if (createIndex < 0 || createIndex >= menuView.childCount) return@post
            val itemView = menuView.getChildAt(createIndex) as? ViewGroup ?: return@post
            val iconId = com.google.android.material.R.id.icon
            val iconView = itemView.findViewById<ImageView>(iconId) ?: return@post
            val sizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 60f, resources.displayMetrics
            ).toInt()
            val lp = iconView.layoutParams
            lp.width = sizePx
            lp.height = sizePx
            iconView.layoutParams = lp
            iconView.scaleType = ImageView.ScaleType.CENTER_CROP
            // Optionally hide label text for the center item to avoid overlap
            menu.getItem(createIndex).title = ""
        }
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
        // If requested, open settings to a specific tab (e.g., Subscriptions)
        if (intent.hasExtra(EXTRA_OPEN_SETTINGS_TAB)) {
            val tabIndex = intent.getIntExtra(EXTRA_OPEN_SETTINGS_TAB, 0)
            supportFragmentManager.beginTransaction()
                .replace(R.id.mainContentContainer, club.gifters.giftersclub.settings.SettingsFragment.newInstance(tabIndex))
                .addToBackStack(null)
                .commit()
        }
        // If requested, open current user's profile
        if (intent.getBooleanExtra(EXTRA_OPEN_PROFILE, false)) {
            AuthUtils.getCurrentUserId(this)?.let { uid ->
                lifecycleScope.launch {
                    try {
                        RetrofitClient.profileApi.getProfileByUserId("*", "eq.$uid")
                            .firstOrNull()?.let { prof ->
                                supportFragmentManager.beginTransaction()
                                    .replace(
                                        R.id.mainContentContainer,
                                        club.gifters.giftersclub.gifts.GifterFragment.newInstance(prof.username)
                                    )
                                    .addToBackStack(null)
                                    .commit()
                            }
                    } catch (_: Exception) { }
                }
            }
        }
        RetrofitClient.init(this)
        // Resume-live banner action
        findViewById<Button>(R.id.btnResumeLive)?.setOnClickListener {
            val banner = findViewById<CardView>(R.id.resumeLiveBanner)
            val sid = banner?.tag as? String
            if (!sid.isNullOrEmpty()) {
                val uri = Uri.parse("https://gifters.club/live/$sid")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.setClassName(this, "club.gifters.giftersclub.live.LiveStreamActivity")
                startActivity(intent)
            }
        }



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
                // Prefer a numeric versionName if used (e.g., "19", "20"); otherwise fall back to versionCode
                val nameNumber = pkgInfo.versionName?.toIntOrNull()
                val codeNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkgInfo.longVersionCode.toInt() else @Suppress("DEPRECATION") pkgInfo.versionCode
                val currentVersion = nameNumber ?: codeNumber

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

                val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
                val dismissedFor = prefs.getInt("dismiss_update_version", -1)
                if (currentVersion < latestVersion && dismissedFor != latestVersion) {
                    withContext(Dispatchers.Main) {
                        val banner = findViewById<CardView>(R.id.updateBanner)
                        val text = findViewById<TextView>(R.id.updateBannerText)
                        val btn = findViewById<Button>(R.id.updateBannerButton)
                        val dismiss = findViewById<TextView>(R.id.updateBannerDismiss)
                        val diff = latestVersion - currentVersion
                        banner?.visibility = View.VISIBLE
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
                        dismiss.setOnClickListener {
                            banner?.visibility = View.GONE
                            prefs.edit().putInt("dismiss_update_version", latestVersion).apply()
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
                R.id.nav_explore -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.mainContentContainer, ExploreFragment())
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
        // Initial check for active host livestream (show resume banner)
        lifecycleScope.launch(Dispatchers.IO) { checkActiveHostLive() }

        // Enlarge only the nav_create icon to ~60dp and attach action
        enlargeCreateItem(bottomNav)
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
        // Refresh resume-live banner on returning to app
        lifecycleScope.launch(Dispatchers.IO) { checkActiveHostLive() }
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

    fun setLoading(show: Boolean) {
        val overlay = findViewById<FrameLayout>(R.id.loadingOverlay)
        overlay?.visibility = if (show) View.VISIBLE else View.GONE
    }

    private suspend fun checkActiveHostLive() {
        try {
            val uid = AuthUtils.getCurrentUserId(this) ?: return
            val lives = RetrofitClient.liveStreamApi.getLiveStreamsByHosts(
                select = "id,title,status",
                hostFilter = "eq.$uid",
                statusFilter = "eq.live",
                order = "live_stream_viewer_count.desc"
            )
            withContext(Dispatchers.Main) {
                val banner = findViewById<CardView>(R.id.resumeLiveBanner)
                val text = findViewById<TextView>(R.id.tvResumeLiveText)
                val live = lives.firstOrNull()
                if (live != null) {
                    banner?.visibility = View.VISIBLE
                    banner?.tag = live.id
                    text?.text = "Resume live stream"
                } else {
                    banner?.visibility = View.GONE
                    banner?.tag = null
                }
            }
        } catch (_: Exception) { }
    }
}
