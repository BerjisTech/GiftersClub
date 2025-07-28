package club.gifters.giftersclub.settings

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.R
import club.gifters.giftersclub.network.RetrofitClient
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
        val spinnerBlocked = view.findViewById<android.widget.Spinner>(R.id.spinnerBlockedUsers)
        val btnUnblock = view.findViewById<MaterialButton>(R.id.btnUnblockUser)
        val btnReport = view.findViewById<MaterialButton>(R.id.btnReportUser)
        val spinnerReports = view.findViewById<android.widget.Spinner>(R.id.spinnerUserReports)

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
                            loadBlockedUsers(userId, spinnerBlocked)
                        }
                    } catch (_: Exception) {
                        Toast.makeText(requireContext(), "Failed to block user", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        btnUnblock.setOnClickListener {
            val selected = spinnerBlocked.selectedItem as? String ?: return@setOnClickListener
            lifecycleScope.launch {
                try {
                    profileApi.unblockUser(
                        blockerFilter = "eq.$userId",
                        blockedFilter = "eq.$selected"
                    )
                    Toast.makeText(requireContext(), "User unblocked", Toast.LENGTH_SHORT).show()
                    loadBlockedUsers(userId, spinnerBlocked)
                } catch (_: Exception) {
                    Toast.makeText(requireContext(), "Failed to unblock user", Toast.LENGTH_SHORT).show()
                }
            }
        }
        btnReport.setOnClickListener {
            val usernameRpt = etUsername.text.toString().trim()
            if (usernameRpt.isNotEmpty()) {
                lifecycleScope.launch {
                    try {
                        profileApi.reportUser(mapOf(
                            "reporter_user_id" to userId,
                            "reported_user_id" to usernameRpt,
                            "reason" to ""
                        ))
                        Toast.makeText(requireContext(), "User reported", Toast.LENGTH_SHORT).show()
                        loadUserReports(userId, spinnerReports)
                    } catch (_: Exception) {
                        Toast.makeText(requireContext(), "Failed to report user", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        loadBlockedUsers(userId, spinnerBlocked)
        loadUserReports(userId, spinnerReports)
    }

    private fun loadBlockedUsers(userId: String, spinner: android.widget.Spinner) {
        lifecycleScope.launch {
            try {
                val blocks = profileApi.getUserBlocks("eq.$userId")
                val names = blocks.map { it.blocked_user_id }
                spinner.adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    names
                )
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Failed to load blocked users", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadUserReports(userId: String, spinner: android.widget.Spinner) {
        lifecycleScope.launch {
            try {
                val reports = profileApi.getUserReports("*", "eq.$userId")
                val entries = reports.map { it.reported_user_id }
                spinner.adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    entries
                )
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Failed to load reports", Toast.LENGTH_SHORT).show()
            }
        }
    }
}