package club.gifters.giftersclub.explore

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import club.gifters.giftersclub.R
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.network.SearchQueriesApi
import club.gifters.giftersclub.model.SearchExploreResult
import kotlinx.coroutines.launch
import android.util.Log
import club.gifters.giftersclub.explore.SuggestionAdapter
import club.gifters.giftersclub.explore.ExploreTopFragment
import club.gifters.giftersclub.explore.ExplorePostsFragment
import club.gifters.giftersclub.explore.ExploreUsersFragment
import club.gifters.giftersclub.explore.ExploreLiveFragment
import retrofit2.HttpException

/**
 * Fragment for explore search with suggestions and tabbed results.
 * Loads default explore results on initial view creation.
 */
class ExploreFragment : Fragment(R.layout.fragment_explore) {
    companion object {
        private const val TAG = "ExploreFragment"
    }
    private lateinit var etSearch: EditText
    private lateinit var rvSuggestions: RecyclerView
    private lateinit var suggestionAdapter: SuggestionAdapter
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private val tabTitles = listOf("Top", "Videos", "Photos", "Users", "Live")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        etSearch = view.findViewById(R.id.etSearch)
        rvSuggestions = view.findViewById(R.id.rvSuggestions)
        rvSuggestions.layoutManager = LinearLayoutManager(requireContext())
        suggestionAdapter = SuggestionAdapter { suggestion ->
            etSearch.setText(suggestion)
            etSearch.setSelection(suggestion.length)
            performSearch(suggestion)
        }
        rvSuggestions.adapter = suggestionAdapter

        tabLayout = view.findViewById(R.id.tabLayout)
        viewPager = view.findViewById(R.id.viewPager)

        etSearch.doAfterTextChanged { editable ->
            val q = editable.toString().trim()
            if (q.length >= 2) {
                Log.d(TAG, "Suggest query='$q'")
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val results = RetrofitClient.searchQueriesApi.searchQueries(
                            queryFilter = "ilike.*${q}*"
                        )
                        val suggestions = results.map { it.query }
                        Log.d(TAG, "Suggestions count=${suggestions.size}")
                        if (suggestions.isNotEmpty()) {
                            suggestionAdapter.submitList(suggestions)
                            rvSuggestions.visibility = View.VISIBLE
                        } else {
                            suggestionAdapter.submitList(emptyList())
                            rvSuggestions.visibility = View.GONE
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Suggestion fetch failed", e)
                        suggestionAdapter.submitList(emptyList())
                        rvSuggestions.visibility = View.GONE
                    }
                }
            } else {
                suggestionAdapter.submitList(emptyList())
                rvSuggestions.visibility = View.GONE
            }
        }

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val q = etSearch.text.toString().trim()
                if (q.isNotEmpty()) performSearch(q)
                true
            } else false
        }

        // Load default explore results immediately
        performSearch("")
    }

    private fun performSearch(query: String) {
        Log.d(TAG, "Perform search for='$query'")
        rvSuggestions.visibility = View.GONE
        hideKeyboard()
        tabLayout.visibility = View.VISIBLE
        viewPager.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = RetrofitClient.postApi.searchExploreRpc(mapOf("q" to query))
                // Populate tabs from unified result
                viewPager.adapter = object : FragmentStateAdapter(this@ExploreFragment) {
                    override fun getItemCount() = tabTitles.size
                    override fun createFragment(position: Int) = when (position) {
                        0 -> ExploreTopFragment.newInstance(result.top, result.users, result.live)
                        1 -> ExplorePostsFragment.newInstanceFromList(result.videos)
                        2 -> ExplorePostsFragment.newInstanceFromList(result.photos)
                        3 -> ExploreUsersFragment.newInstanceFromList(result.users)
                        4 -> ExploreLiveFragment.newInstanceFromList(result.live)
                        else -> ExploreTopFragment.newInstance(result.top, result.users, result.live)
                    }
                }
                TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
                    tab.text = tabTitles[pos]
                }.attach()
            } catch (e: Exception) {
                Log.w(TAG, "Search RPC failed", e)
                // If the unified RPC is not available, fall back to the per-tab search fragments
                if (e is retrofit2.HttpException && e.code() == 404) {
                    viewPager.adapter = object : FragmentStateAdapter(this@ExploreFragment) {
                        override fun getItemCount() = tabTitles.size
                        override fun createFragment(position: Int) = when (position) {
                            0 -> ExplorePostsFragment.newInstance(query, null)
                            1 -> ExplorePostsFragment.newInstance(query, "video")
                            2 -> ExplorePostsFragment.newInstance(query, "photo")
                            3 -> ExploreUsersFragment.newInstance(query)
                            4 -> ExploreLiveFragment.newInstance(query)
                            else -> ExplorePostsFragment.newInstance(query, null)
                        }
                    }
                    TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
                        tab.text = tabTitles[pos]
                    }.attach()
                } else {
                    // On other errors, show empty lists so tabs still render
                    viewPager.adapter = object : FragmentStateAdapter(this@ExploreFragment) {
                        override fun getItemCount() = tabTitles.size
                        override fun createFragment(position: Int) = when (position) {
                            0 -> ExploreTopFragment.newInstance(emptyList(), emptyList(), emptyList())
                            1 -> ExplorePostsFragment.newInstanceFromList(emptyList())
                            2 -> ExplorePostsFragment.newInstanceFromList(emptyList())
                            3 -> ExploreUsersFragment.newInstanceFromList(emptyList())
                            4 -> ExploreLiveFragment.newInstanceFromList(emptyList())
                            else -> ExploreTopFragment.newInstance(emptyList(), emptyList(), emptyList())
                        }
                    }
                    TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
                        tab.text = tabTitles[pos]
                    }.attach()
                }
            }
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE)
                as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
    }
}