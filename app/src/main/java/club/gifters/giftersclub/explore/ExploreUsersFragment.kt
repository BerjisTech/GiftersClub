package club.gifters.giftersclub.explore

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import club.gifters.giftersclub.R
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.explore.ExploreUserAdapter
import club.gifters.giftersclub.model.Profile
import club.gifters.giftersclub.gifts.GifterFragment
import kotlinx.coroutines.launch

/**
 * Fragment for displaying user search results in Explore.
 */
class ExploreUsersFragment : Fragment(R.layout.fragment_explore_users) {
    companion object {
        private const val ARG_QUERY = "query"

        fun newInstance(query: String): ExploreUsersFragment {
            return ExploreUsersFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_QUERY, query)
                }
            }
        }
    }

    private val query: String by lazy { requireArguments().getString(ARG_QUERY).orEmpty() }
    private lateinit var adapter: ExploreUserAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val swipe = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
        val rv = view.findViewById<RecyclerView>(R.id.rvUsers)
        adapter = ExploreUserAdapter { profile: Profile ->
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainContentContainer, GifterFragment.newInstance(profile.username))
                .addToBackStack(null)
                .commit()
        }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        swipe.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val wild = "*${'$'}{query}*"
                val orFilter = "(username.ilike.${'$'}wild,name.ilike.${'$'}wild)"
                val users = RetrofitClient.profileApi.searchProfiles("*", orFilter)
                adapter.submitList(users)
            } catch (e: Exception) {
                adapter.submitList(emptyList())
            } finally {
                swipe.isRefreshing = false
            }
        }
    }
}