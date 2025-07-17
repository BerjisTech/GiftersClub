package club.gifters.giftersclub.explore

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.R
import club.gifters.giftersclub.gifts.GifterFragment
import club.gifters.giftersclub.model.Profile
import club.gifters.giftersclub.network.RetrofitClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

/**
 * Fragment for displaying user search results in Explore.
 */
class ExploreUsersFragment : Fragment(R.layout.fragment_explore_users) {
    companion object {
        private const val TAG = "ExploreUsersFragment"
        private const val ARG_QUERY = "query"
        private const val ARG_LIST = "arg_list"

        /** Instantiate with query string. */
        fun newInstance(query: String): ExploreUsersFragment = ExploreUsersFragment().apply {
            arguments = Bundle().apply { putString(ARG_QUERY, query) }
        }

        /** Instantiate with preloaded user list. */
        fun newInstanceFromList(list: List<Profile>): ExploreUsersFragment = ExploreUsersFragment().apply {
            arguments = Bundle().apply { putString(ARG_LIST, Gson().toJson(list)) }
        }
    }

    private val query: String by lazy { requireArguments().getString(ARG_QUERY).orEmpty() }
    private lateinit var adapter: ExploreUserAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val swipe = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
        val rv = view.findViewById<RecyclerView>(R.id.rvUsers)
        adapter = ExploreUserAdapter { profile: Profile ->
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.mainContentContainer, GifterFragment.newInstance(profile.username))
                .addToBackStack(null)
                .commit()
        }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        swipe.isRefreshing = true
        arguments?.getString(ARG_LIST)?.let { json ->
            val type = object : TypeToken<List<Profile>>() {}.type
            adapter.submitList(Gson().fromJson(json, type))
            swipe.isRefreshing = false
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // reuse GiftFragment user search logic
                val filter = "(username.ilike.*${query}*,name.ilike.*${query}*)"
                // Log.d(TAG, "User search filter=$filter")
                val raw = RetrofitClient.profileApi.searchProfiles("*", filter)
                val currentUser = AuthUtils.getCurrentUserId(requireContext())
                val results = raw.filter { it.userId != currentUser }
                // Log.d(TAG, "Users found=${results.size}")
                adapter.submitList(results)
            } catch (e: Exception) {
                // Log.w(TAG, "User search failed", e)
                adapter.submitList(emptyList())
            } finally {
                swipe.isRefreshing = false
            }
        }
    }
}