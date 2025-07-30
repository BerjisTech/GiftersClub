package club.gifters.giftersclub.settings

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputEditText
import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.R
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.settings.BlockedUsersFragment
import club.gifters.giftersclub.settings.ReportedUsersFragment
import kotlinx.coroutines.launch

/**
 * Allows blocking/unblocking users and reporting users.
 */
class SecuritySettingsFragment : Fragment(R.layout.fragment_security_settings) {
    private val profileApi = RetrofitClient.profileApi
    private var userId: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userId = AuthUtils.getCurrentUserId(requireContext()) ?: return

        val etUsername = view.findViewById<TextInputEditText>(R.id.etUsernameInput)
        val btnBlock = view.findViewById<MaterialButton>(R.id.btnBlockUser)
        val btnReport = view.findViewById<MaterialButton>(R.id.btnReportUser)
        val tabLayout = view.findViewById<com.google.android.material.tabs.TabLayout>(R.id.securitySubTabLayout)
        val viewPager = view.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.securitySubViewPager)

        btnBlock.setOnClickListener {
            val username = etUsername.text.toString().trim()
            if (username.isNotEmpty()) {
                lifecycleScope.launch {
                    try {
                        val users = profileApi.searchProfiles("*", "(username.eq.$username)")
                        users.firstOrNull()?.let {
                            profileApi.blockUser(mapOf(
                                "blocker_user_id" to userId,
                                "blocked_user_id" to it.userId
                            ))
                            Toast.makeText(requireContext(), "User blocked", Toast.LENGTH_SHORT).show()
                            childFragmentManager.fragments
                                .filterIsInstance<BlockedUsersFragment>()
                                .firstOrNull()
                                ?.refreshList()
                        }
                    } catch (_: Exception) {
                        Toast.makeText(requireContext(), "Failed to block user", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        btnReport.setOnClickListener {
            val usernameRpt = etUsername.text.toString().trim()
            if (usernameRpt.isNotEmpty()) {
                // Prompt for report reason
                val input = android.widget.EditText(requireContext()).apply {
                    hint = "Reason for report"
                }
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Report User")
                    .setView(input)
                    .setPositiveButton("Submit") { _, _ ->
                        val reason = input.text.toString().trim()
                        lifecycleScope.launch {
                            try {
                                val users = profileApi.searchProfiles("*", "(username.eq.$usernameRpt)")
                                users.firstOrNull()?.let {
                                    profileApi.reportUser(mapOf(
                                        "reporter_user_id" to userId,
                                        "reported_user_id" to it.userId,
                                        "reason" to reason
                                    ))
                                    Toast.makeText(requireContext(), "Report submitted", Toast.LENGTH_SHORT).show()
                                    childFragmentManager.fragments
                                        .filterIsInstance<ReportedUsersFragment>()
                                        .firstOrNull()
                                        ?.refreshList()
                                }
                            } catch (_: Exception) {
                                Toast.makeText(requireContext(), "Failed to report user", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        // Setup inner tabs for blocked and reported lists
        viewPager.adapter = object : androidx.viewpager2.adapter.FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2
            override fun createFragment(position: Int) = when (position) {
                0 -> BlockedUsersFragment()
                1 -> ReportedUsersFragment()
                else -> BlockedUsersFragment()
            }
        }
        com.google.android.material.tabs.TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
            tab.text = when (pos) {
                0 -> "Blocked"
                1 -> "Reported"
                else -> ""
            }
        }.attach()
    }

}