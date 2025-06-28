package club.gifters.giftersclub.social

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.network.RetrofitClient
import kotlinx.coroutines.launch
import club.gifters.giftersclub.model.FollowsEntry
import club.gifters.giftersclub.social.ProfileAdapter
import club.gifters.giftersclub.gifts.GifterFragment

/**
 * Fragment showing list of users the current user is following.
 */
class MyFollowingFragment : Fragment(R.layout.fragment_my_following) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rvMyFollowing)
        rv.layoutManager = LinearLayoutManager(requireContext())
        val adapter = ProfileAdapter { profile ->
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainContentContainer, GifterFragment.newInstance(profile.username))
                .addToBackStack(null)
                .commit()
        }
        rv.adapter = adapter

        // load and display following users
        lifecycleScope.launch {
            val currentUserId = AuthUtils.getCurrentUserId(requireContext()) ?: return@launch
            try {
                val entries = RetrofitClient.followsApi.getFollowing(
                    followerIdFilter = "eq.$currentUserId"
                )
                val ids = entries.mapNotNull { it.followedId }
                if (ids.isNotEmpty()) {
                    val filter = "in.(${ids.joinToString(",")})"
                    val profiles = RetrofitClient.profileApi.getProfilesByUserIds("*", filter)
                    adapter.submitList(profiles)
                } else {
                    adapter.submitList(emptyList())
                }
            } catch (_: Exception) {
                adapter.submitList(emptyList())
            }
        }
    }
}