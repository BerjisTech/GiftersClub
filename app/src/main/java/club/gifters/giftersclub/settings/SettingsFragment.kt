package club.gifters.giftersclub.settings

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import club.gifters.giftersclub.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

/**
 * Settings screen with tabs for Profile, Security, Moderation, Interaction.
 */
class SettingsFragment : Fragment(R.layout.fragment_settings) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val tabLayout = view.findViewById<TabLayout>(R.id.settingsTabLayout)
        val viewPager = view.findViewById<ViewPager2>(R.id.settingsViewPager)
        viewPager.adapter = SettingsPagerAdapter(requireActivity())
        TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
            tab.text = when (pos) {
                0 -> "Profile"
                1 -> "Security"
                2 -> "Moderation"
                3 -> "Interaction"
                else -> ""
            }
        }.attach()
    }
}

private class SettingsPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
    override fun getItemCount(): Int = 4
    override fun createFragment(position: Int) = when (position) {
        0 -> ProfileSettingsFragment()
        1 -> SecuritySettingsFragment()
        2 -> ModerationSettingsFragment()
        3 -> InteractionSettingsFragment()
        else -> ProfileSettingsFragment()
    }
}