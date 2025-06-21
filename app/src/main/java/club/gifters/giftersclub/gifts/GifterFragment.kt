package club.gifters.giftersclub.gifts

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import club.gifters.giftersclub.R
import club.gifters.giftersclub.gifts.GiftFragment
import club.gifters.giftersclub.gifts.UserPostsFragment
import club.gifters.giftersclub.gifts.UserWishlistsFragment
import club.gifters.giftersclub.model.Profile
import club.gifters.giftersclub.network.ProfileApi
import club.gifters.giftersclub.network.RetrofitClient
import kotlinx.coroutines.launch

/**
 * Fragment showing a user's profile and their gift page.
 */
class GifterFragment : Fragment(R.layout.fragment_gifter) {

    private val profileApi: ProfileApi = RetrofitClient.profileApi
    private var username: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        username = arguments?.getString(ARG_USERNAME)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val imageAvatar = view.findViewById<ImageView>(R.id.imageAvatar)
        val textName    = view.findViewById<TextView>(R.id.textName)
        val textUser    = view.findViewById<TextView>(R.id.textUsername)
        val textBio     = view.findViewById<TextView>(R.id.textBio)

        lifecycleScope.launch {
            username?.let { uname ->
                val list = profileApi.getProfileByUsername("*", "eq.$uname")
                val profile = list.firstOrNull()
                profile?.let { prof ->
                    bindProfile(prof, imageAvatar, textName, textUser, textBio)
                    // Setup tabs and viewpager for this user
                    val tabLayout = view.findViewById<TabLayout>(R.id.tabLayout)
                    val viewPager = view.findViewById<ViewPager2>(R.id.viewPager)
                    viewPager.adapter = object : FragmentStateAdapter(this@GifterFragment) {
                        override fun getItemCount() = 3
                        override fun createFragment(position: Int) = when (position) {
                            0 -> GiftFragment.newInstance(prof.userId, prof.username)
                            1 -> UserWishlistsFragment.newInstance(prof.userId)
                            2 -> UserPostsFragment.newInstance(prof.userId)
                            else -> GiftFragment.newInstance(prof.userId, prof.username)
                        }
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
    }

    private fun bindProfile(
        p: Profile,
        imageAvatar: ImageView,
        textName: TextView,
        textUser: TextView,
        textBio: TextView
    ) {
        textName.text     = p.name
        textUser.text     = "@${p.username}"
        textBio.text      = p.bio
        if (p.image.isNotBlank()) {
            imageAvatar.load(p.image) { placeholder(android.R.color.darker_gray) }
        }
    }

    companion object {
        private const val ARG_USERNAME = "username"
        fun newInstance(username: String) = GifterFragment().apply {
            arguments = Bundle().apply { putString(ARG_USERNAME, username) }
        }
    }
}