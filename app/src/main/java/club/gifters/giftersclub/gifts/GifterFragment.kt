package club.gifters.giftersclub.gifts

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.R
import club.gifters.giftersclub.SupabaseConfig
import club.gifters.giftersclub.chat.ChatFragment
import club.gifters.giftersclub.settings.SettingsFragment
import club.gifters.giftersclub.model.Post
import club.gifters.giftersclub.model.Profile
import club.gifters.giftersclub.network.ProfileApi
import club.gifters.giftersclub.network.RetrofitClient
import coil.load
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

/**
 * Fragment showing a user's profile and their gift page.
 */
class GifterFragment : Fragment(R.layout.fragment_gifter), UserPostsFragment.OnSelectionModeChangeListener {

    private val profileApi: ProfileApi = RetrofitClient.profileApi
    private var username: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        username = arguments?.getString(ARG_USERNAME)
    }

    private var isFollowing = false
    private var isFollowedBy = false
    private var isFriend     = false
    private var isOwner      = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val imageAvatar = view.findViewById<ImageView>(R.id.imageAvatar)
        val textName    = view.findViewById<TextView>(R.id.textName)
        val textUser    = view.findViewById<TextView>(R.id.textUsername)
        val textFollowers = view.findViewById<TextView>(R.id.textFollowers)
        val textFollowing = view.findViewById<TextView>(R.id.textFollowing)
        val textBio     = view.findViewById<TextView>(R.id.textBio)
        val btnFollow   = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFollow)
        val btnShareProfile = view.findViewById<ImageView>(R.id.btnShareProfile)

        // Toolbar title changes as header collapses
        val nestedScroll = view.findViewById<NestedScrollView>(R.id.nestedScrollView)
        val headerUsername = view.findViewById<TextView>(R.id.textUsername)
        val toolbar = (requireActivity() as AppCompatActivity).findViewById<MaterialToolbar>(R.id.topAppBar)
        toolbar.title = ""
        nestedScroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
            toolbar.title = if (scrollY >= headerUsername.bottom) username.orEmpty() else ""
        })
        lifecycleScope.launch {
                username?.let { uname ->
                val profiles = profileApi.getProfileByUsername("*", "eq.$uname")
                val prof = profiles.firstOrNull() ?: return@launch
                // Log.d(TAG, "Fetched profile: $prof")
                bindProfile(prof, imageAvatar, textName, textUser, textFollowers, textFollowing, textBio)

//                    Share  Profile

                    btnShareProfile.setOnClickListener { shareProfile() }

                // follow/friend button state
                val currentUserId = AuthUtils.getCurrentUserId(requireContext())
                if (currentUserId != null && currentUserId != prof.userId) {
                    isFollowing  = FollowApiHolder.isFollowingUser(prof.userId)
                    isFollowedBy = FollowApiHolder.isFollowedByUser(prof.userId)
                    isFriend     = isFollowing && isFollowedBy
                    // Log.d(TAG, "Follow state: isFollowing=$isFollowing isFollowedBy=$isFollowedBy isFriend=$isFriend")
                    btnFollow.isVisible = true
                    btnFollow.setOnClickListener {
                        lifecycleScope.launch {
                            if (isFollowing) FollowApiHolder.unfollowUser(prof.userId)
                            else              FollowApiHolder.followUser(prof.userId)
                            isFollowing = !isFollowing
                            isFriend    = isFollowing && isFollowedBy
                            updateFollowButton(btnFollow)
                            profileApi.getProfileByUserId("*", "eq.${prof.userId}")
                                .firstOrNull()?.let { p ->
                                    bindProfile(p, imageAvatar, textName,
                                        textUser, textFollowers, textFollowing, textBio)
                                }
                        }
                    }
                    updateFollowButton(btnFollow)
                    // chat button for direct messaging
                    val btnChat = view.findViewById<ImageButton>(R.id.btnChat)
                    btnChat.isVisible = true
                    btnChat.setOnClickListener {
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.mainContentContainer,
                                ChatFragment.newInstance(prof.userId, prof.username)
                            )
                            .addToBackStack(null)
                            .commit()
                    }
                }

                // Settings button for account owner
                isOwner = currentUserId != null && currentUserId == prof.userId
                val btnSettings = view.findViewById<CardView>(R.id.btnSettings)
                val btnAccount = view.findViewById<CardView>(R.id.btnAccount)
                if (isOwner) {
                    btnSettings.isVisible = true
                    btnAccount.isVisible = true
                    btnAccount.setOnClickListener {
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.mainContentContainer, AccountFragment())
                            .addToBackStack(null)
                            .commit()
                    }
                    btnSettings.setOnClickListener {
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.mainContentContainer, SettingsFragment())
                            .addToBackStack(null)
                            .commit()
                    }
                }
                // Setup tabs and viewpager
                val tabLayout = view.findViewById<TabLayout>(R.id.tabLayout)
                val viewPager = view.findViewById<ViewPager2>(R.id.viewPager)
                val btnDeleteSelectedPosts = view.findViewById<MaterialButton>(R.id.btnDeleteSelectedPosts)
                val gifterActions = view.findViewById<LinearLayout>(R.id.gifterActions)

                var userPostsFragment: UserPostsFragment? = null

                viewPager.adapter = object : FragmentStateAdapter(this@GifterFragment) {
                    override fun getItemCount() = 3
                    override fun createFragment(position: Int) = when (position) {
                        0 -> GiftFragment.newInstance(prof.userId, prof.username)
                        1 -> UserWishlistsFragment.newInstance(prof.userId)
                        2 -> {
                            userPostsFragment = UserPostsFragment.newInstance(prof.userId, prof.username)
                            userPostsFragment!!
                        }
                        else -> GiftFragment.newInstance(prof.userId, prof.username)
                    }
                }

                btnDeleteSelectedPosts.setOnClickListener {
                    userPostsFragment?.deleteSelectedPosts()
                }
                TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
                    tab.text = when (pos) {
                        0 -> "Gifts"
                        1 -> "Wishlists"
                        2 -> "Posts"
                        else -> ""
                    }
                }.attach()
            }
        }
    }

    private fun bindProfile(
        p: Profile,
        imageAvatar: ImageView,
        textName: TextView,
        textUser: TextView,
        textFollowers: TextView,
        textFollowing: TextView,
        textBio: TextView
    ) {
        // Log.d(TAG, "bindProfile counts: followers=${p.followersCount} following=${p.followingCount}")
        textName.text      = p.name
        textUser.text      = "@${p.username}"
        textFollowers.text = "${p.followersCount ?: 0} follower${if ((p.followersCount ?: 0) == 1) "" else "s"}"
        textFollowing.text = "${p.followingCount ?: 0} following"
        textBio.text       = p.bio
        if (p.image.isNotBlank()) {
            imageAvatar.load(p.image) { placeholder(android.R.color.darker_gray) }
        }
    }

    companion object {
        private const val ARG_USERNAME = "username"
        private const val TAG = "GifterFragment"
        fun newInstance(username: String) = GifterFragment().apply {
            arguments = Bundle().apply { putString(ARG_USERNAME, username) }
        }
    }

    private fun updateFollowButton(btn: MaterialButton) {
        if (isFriend) {
            btn.text = "Friends"
            btn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#22c55e"))
        } else if (isFollowing) {
            btn.text = "Following"
            btn.setBackgroundResource(R.drawable.bg_pink_indigo_gradient)
        } else {
            btn.text = "Follow"
            btn.setBackgroundResource(R.drawable.bg_yellow_orange_gradient)
        }
        btn.setTextColor(Color.WHITE)
    }

    override fun onSelectionModeChanged(inSelectionMode: Boolean) {
        if (!isOwner) return
        val gifterActions = requireView().findViewById<LinearLayout>(R.id.gifterActions)
        val btnDeleteSelectedPosts = requireView().findViewById<MaterialButton>(R.id.btnDeleteSelectedPosts)
        gifterActions.isVisible = inSelectionMode
        btnDeleteSelectedPosts.isVisible = inSelectionMode
    }

    override fun onDeleteSelectedPosts(selectedPosts: Set<Post>) {
        if (!isOwner) return
        val gifterActions = requireView().findViewById<LinearLayout>(R.id.gifterActions)
        val btnDeleteSelectedPosts = requireView().findViewById<MaterialButton>(R.id.btnDeleteSelectedPosts)
        gifterActions.isVisible = true
        btnDeleteSelectedPosts.isVisible = true
        btnDeleteSelectedPosts.text = "Delete ${selectedPosts.size} Posts"
    }

    private fun shareProfile() {
        val shareUrl = "${
            SupabaseConfig.SUPABASE_URL.replace(
                ".supabase.co",
                ".supabase.co/profile/"
            )
        }${username}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareUrl)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_profile)))
    }
}