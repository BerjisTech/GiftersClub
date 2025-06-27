package club.gifters.giftersclub.social

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import club.gifters.giftersclub.R
import club.gifters.giftersclub.social.MyFollowersFragment
import club.gifters.giftersclub.social.MyGiftersFragment
import club.gifters.giftersclub.social.MyFollowingFragment
import androidx.appcompat.app.AppCompatActivity

/**
 * Fragment displaying tabs for My Followers, My Gifters, and My Following.
 */
class FriendsFragment : Fragment(R.layout.fragment_friends) {
    companion object {
        private const val ARG_INITIAL_TAB = "initial_tab"
        /**
         * @param initialTab 0: My Followers, 1: My Gifters, 2: My Following
         */
        fun newInstance(initialTab: Int) = FriendsFragment().apply {
            arguments = Bundle().apply { putInt(ARG_INITIAL_TAB, initialTab) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Setup toolbar
        val toolbar = (requireActivity() as AppCompatActivity).findViewById<MaterialToolbar>(R.id.topAppBar)
        toolbar.title = getString(R.string.friends)
        // Setup tabs and viewpager
        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayoutFriends)
        val viewPager = view.findViewById<ViewPager2>(R.id.viewPagerFriends)
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 3
            override fun createFragment(position: Int) = when (position) {
                0 -> MyFollowersFragment()
                1 -> MyGiftersFragment()
                2 -> MyFollowingFragment()
                else -> MyFollowersFragment()
            }
        }
        TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
            tab.text = when (pos) {
                0 -> getString(R.string.my_followers)
                1 -> getString(R.string.my_gifters)
                2 -> getString(R.string.my_following)
                else -> ""
            }
        }.attach()
        // Select initial tab if provided
        val initial = arguments?.getInt(ARG_INITIAL_TAB) ?: 0
        viewPager.currentItem = initial
    }
}