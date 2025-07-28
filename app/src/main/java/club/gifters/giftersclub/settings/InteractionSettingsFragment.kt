package club.gifters.giftersclub.settings

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import club.gifters.giftersclub.R
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.model.UserSettings

/** Stub for Interaction settings UI */
class InteractionSettingsFragment : Fragment(R.layout.fragment_interaction_settings) {
    private val options = listOf("anyone", "followers", "friends")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val radioAnyone = view.findViewById<android.widget.RadioButton>(R.id.radioAnyone)
        val radioFollowers = view.findViewById<android.widget.RadioButton>(R.id.radioFollowers)
        val radioFriends = view.findViewById<android.widget.RadioButton>(R.id.radioFriends)
        val btnSave = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveInteraction)

        lifecycleScope.launchWhenStarted {
            val userId = AuthUtils.getCurrentUserId(requireContext()) ?: return@launchWhenStarted
            try {
                val settings = RetrofitClient.profileApi.getUserSettings(
                    "who_can_interact", "eq.$userId"
                ).firstOrNull()
                when (settings?.who_can_interact) {
                    "followers" -> radioFollowers.isChecked = true
                    "friends"   -> radioFriends.isChecked = true
                    else -> radioAnyone.isChecked = true
                }
            } catch (_: Exception) {
                radioAnyone.isChecked = true
            }
        }

        btnSave.setOnClickListener {
            val mode = when {
                radioFollowers.isChecked -> "followers"
                radioFriends.isChecked   -> "friends"
                else -> "anyone"
            }
            lifecycleScope.launchWhenStarted {
                try {
                    val res = RetrofitClient.functionsApi.updateInteractionSettingsRpc(
                        mapOf("whoCanInteract" to mode)
                    )
                    if (res.isSuccessful) {
                        Toast.makeText(requireContext(), "Settings updated", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to update settings", Toast.LENGTH_SHORT).show()
                    }
                } catch (ex: Exception) {
                    Toast.makeText(requireContext(), "Error: ${ex.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}