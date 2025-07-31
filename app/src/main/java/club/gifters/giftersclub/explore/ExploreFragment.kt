package club.gifters.giftersclub.explore

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import club.gifters.giftersclub.R
import club.gifters.giftersclub.network.RetrofitClient
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import android.util.Base64
import org.json.JSONObject
import retrofit2.HttpException
import android.util.Log

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
    private lateinit var initialSearchContainer: View
    private lateinit var rvRecentQueries: RecyclerView
    private lateinit var recentAdapter: RecentSearchAdapter
    private lateinit var tvRecentSeeMore: TextView
    private lateinit var tvRecentClearAll: TextView
    private var isRecentExpanded = false
    private var recentFullList = emptyList<String>()
    private val recentDisplayLimit = 3
    private lateinit var rvRecommendedQueries: RecyclerView
    private lateinit var recommendedAdapter: SuggestionAdapter
    private lateinit var tvRefreshRecommended: TextView
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

        initialSearchContainer = view.findViewById(R.id.initialSearchContainer)
        rvRecentQueries = view.findViewById(R.id.rvRecentQueries)
        rvRecentQueries.layoutManager = LinearLayoutManager(requireContext())
        recentAdapter = RecentSearchAdapter({ q ->
            etSearch.setText(q)
            etSearch.setSelection(q.length)
            performSearch(q)
        }, { q -> deleteRecentQuery(q) })
        rvRecentQueries.adapter = recentAdapter

        tvRecentSeeMore = view.findViewById(R.id.tvRecentSeeMore)
        tvRecentSeeMore.setOnClickListener { toggleRecentExpansion() }

        tvRecentClearAll = view.findViewById(R.id.tvRecentClearAll)
        tvRecentClearAll.setOnClickListener { clearAllRecentQueries() }

        rvRecommendedQueries = view.findViewById(R.id.rvRecommendedQueries)
        rvRecommendedQueries.layoutManager = LinearLayoutManager(requireContext())
        recommendedAdapter = SuggestionAdapter { q ->
            etSearch.setText(q)
            etSearch.setSelection(q.length)
            performSearch(q)
        }
        rvRecommendedQueries.adapter = recommendedAdapter

        tvRefreshRecommended = view.findViewById(R.id.tvRefreshRecommended)
        tvRefreshRecommended.setOnClickListener { loadRecommendedQueries() }

        // Load initial recent and recommended queries
        loadRecentQueries()
        loadRecommendedQueries()

        tabLayout = view.findViewById(R.id.tabLayout)
        viewPager = view.findViewById(R.id.viewPager)

        etSearch.doAfterTextChanged { editable ->
            val q = editable.toString().trim()
            if (q.length >= 2) {
                initialSearchContainer.visibility = View.GONE
                // Log.d(TAG, "Suggest query='$q'")
                // Log.d("ExploreFragment", "Using BASE_URL=${RetrofitClient.BASE_URL}")
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val results = RetrofitClient.searchQueriesApi.searchQueries(
                            queryFilter = "ilike.*${q}*"
                        )
                        val suggestions = results.map { it.query }
                        // Log.d(TAG, "Suggestions count=${suggestions.size}")
                        if (suggestions.isNotEmpty()) {
                            suggestionAdapter.submitList(suggestions)
                            rvSuggestions.visibility = View.VISIBLE
                        } else {
                            suggestionAdapter.submitList(emptyList())
                            rvSuggestions.visibility = View.GONE
                        }
                    } catch (e: HttpException) {
                        val url = e.response()?.raw()?.request?.url
                        val code = e.code()
                        val errorBody = e.response()?.errorBody()?.string()
                        // Log.w(TAG, "Suggestion fetch failed HTTP $code for $url: $errorBody")
                        suggestionAdapter.submitList(emptyList())
                        rvSuggestions.visibility = View.GONE
                    } catch (e: Exception) {
                        // Log.w(TAG, "Suggestion fetch failed", e)
                        suggestionAdapter.submitList(emptyList())
                        rvSuggestions.visibility = View.GONE
                    }
                }
            } else {
                suggestionAdapter.submitList(emptyList())
                rvSuggestions.visibility = View.GONE
                initialSearchContainer.visibility = View.VISIBLE
                loadRecentQueries()
            }
        }

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val q = etSearch.text.toString().trim()
                if (q.isNotEmpty()) performSearch(q)
                true
            } else false
        }
    }

    private fun performSearch(query: String) {
        // Log.d(TAG, "Perform search for='$query'")
        rvSuggestions.visibility = View.GONE
        initialSearchContainer.visibility = View.GONE
        hideKeyboard()
        tabLayout.visibility = View.VISIBLE
        viewPager.visibility = View.VISIBLE
        // record search event
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // record the search event (tab is required by table schema)
                val body = mutableMapOf<String, Any>(
                    "query" to query,
                    "tab" to tabTitles.getOrNull(viewPager.currentItem)?.lowercase().orEmpty()
                )
                getCurrentUserId()?.let { uid -> body["user_id"] = uid }
                RetrofitClient.searchQueriesApi.insertSearchQuery(body)
                loadRecentQueries()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to record search query", e)
            }
        }
        // Log.d("ExploreFragment", "Using BASE_URL=${RetrofitClient.BASE_URL}")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Log.d(TAG, "→ RPC search_explore body={q=$query}")
                val result = RetrofitClient.postApi.searchExploreRpc(mapOf("q" to query))
                // Log.d(TAG, "← RPC search_explore result count: top=${result.top.size}, videos=${result.videos.size}, photos=${result.photos.size}")
                // Populate tabs from unified result
                viewPager.adapter = object : FragmentStateAdapter(this@ExploreFragment) {
                    override fun getItemCount() = tabTitles.size
                    override fun createFragment(position: Int) = when (position) {
                        0 -> ExploreTopFragment.newInstance(result.top, result.users, result.live)
                        1 -> ExplorePostsFragment.newInstanceFromList(result.videos)
                        2 -> ExplorePostsFragment.newInstanceFromList(result.photos)
                        3 -> ExploreUsersFragment.newInstanceFromList(result.users)
                        4 -> ExploreLiveFragment.newInstanceFromList(result.live)
                        else -> ExploreTopFragment.newInstance(
                            result.top,
                            result.users,
                            result.live
                        )
                    }
                }
                TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
                    tab.text = tabTitles[pos]
                }.attach()
            } catch (e: HttpException) {
                val url = e.response()?.raw()?.request?.url
                val code = e.code()
                val errorBody = e.response()?.errorBody()?.string()
                // Log.w(TAG, "Search RPC failed HTTP $code for $url: $errorBody")
                // If the unified RPC is not available (404), fall back
                if (code == 404) {
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
                            0 -> ExploreTopFragment.newInstance(
                                emptyList(),
                                emptyList(),
                                emptyList()
                            )

                            1 -> ExplorePostsFragment.newInstanceFromList(emptyList())
                            2 -> ExplorePostsFragment.newInstanceFromList(emptyList())
                            3 -> ExploreUsersFragment.newInstanceFromList(emptyList())
                            4 -> ExploreLiveFragment.newInstanceFromList(emptyList())
                            else -> ExploreTopFragment.newInstance(
                                emptyList(),
                                emptyList(),
                                emptyList()
                            )
                        }
                    }
                    TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
                        tab.text = tabTitles[pos]
                    }.attach()
                }
            }
        }
    }

    private fun getCurrentUserId(): String? {
        val token = RetrofitClient.context
            ?.getSharedPreferences("supabase", Context.MODE_PRIVATE)
            ?.getString("access_token", null)
            ?: return null
        val parts = token.split('.')
        if (parts.size < 2) return null
        return try {
            val decoded = String(
                Base64.decode(
                    parts[1],
                    Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                )
            )
            JSONObject(decoded).optString("sub").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadRecentQueries() {
        val uid = getCurrentUserId() ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d(TAG, "loadRecentQueries: userId=$uid")
                val results = RetrofitClient.searchQueriesApi.getUserSearchQueries(
                    userIdFilter = "eq.$uid"
                )
                Log.d(TAG, "loadRecentQueries: fetched ${results.size} recents")
                // filter results to only include non-empty queries, only diplay an item once, calculate frequencies and sort by frequencies
                recentFullList = results
                    .map { it.query }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sortedByDescending { it.length } // Sort by length as a simple heuristic
                    .take(10)
                // recentFullList = results.map { it.query }
                isRecentExpanded = false
                updateRecentDisplay()
            } catch (e: HttpException) {
                val url = e.response()?.raw()?.request?.url
                val code = e.code()
                val errorBody = e.response()?.errorBody()?.string()
                Log.w(TAG, "Recent fetch failed HTTP $code for $url: $errorBody")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load recent queries", e)
            }
        }
    }

    private fun updateRecentDisplay() {
        val display = if (!isRecentExpanded && recentFullList.size > recentDisplayLimit) {
            recentFullList.take(recentDisplayLimit)
        } else {
            recentFullList
        }
        Log.d(
            TAG,
            "updateRecentDisplay: displayCount=${display.size}, fullList=${recentFullList.size}, expanded=$isRecentExpanded"
        )
        recentAdapter.submitList(display)
        rvRecentQueries.visibility = if (display.isNotEmpty()) View.VISIBLE else View.GONE
        tvRecentSeeMore.visibility =
            if (!isRecentExpanded && recentFullList.size > recentDisplayLimit) View.VISIBLE else View.GONE
        tvRecentClearAll.visibility =
            if (isRecentExpanded && recentFullList.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun toggleRecentExpansion() {
        isRecentExpanded = true
        updateRecentDisplay()
    }

    private fun clearAllRecentQueries() {
        val uid = getCurrentUserId() ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                RetrofitClient.searchQueriesApi.deleteAllSearchQueries(
                    userIdFilter = "eq.$uid"
                )
                recentFullList = emptyList()
                updateRecentDisplay()
            } catch (_: Exception) {
            }
        }
    }

    private fun deleteRecentQuery(query: String) {
        val uid = getCurrentUserId() ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                RetrofitClient.searchQueriesApi.deleteSearchQuery(
                    queryFilter = "eq.$query",
                    userIdFilter = "eq.$uid"
                )
                recentFullList = recentFullList.filter { it != query }
                updateRecentDisplay()
            } catch (_: Exception) {
            }
        }
    }

    private fun loadRecommendedQueries() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val uid = getCurrentUserId()
                Log.d(TAG, "loadRecommendedQueries: excludeUid=$uid")
                // Fetch raw queries and group locally to compute frequencies
                val raw = RetrofitClient.searchQueriesApi.searchRecommendedQueries(
                    userId = "not.eq.$uid",
                )
                val freq = raw.groupingBy { it.query }.eachCount()
                val trending = freq.entries
                    .sortedByDescending { it.value }
                    .map { it.key }
                    .take(10)
                Log.d(
                    TAG,
                    "loadRecommendedQueries: computed ${trending.size} trending from ${raw.size} raw"
                )
                recommendedAdapter.submitList(trending)
                rvRecommendedQueries.visibility =
                    if (trending.isNotEmpty()) View.VISIBLE else View.GONE
            } catch (e: HttpException) {
                val url = e.response()?.raw()?.request?.url
                val code = e.code()
                val errorBody = e.response()?.errorBody()?.string()
                Log.w(TAG, "Recommended fetch failed HTTP $code for $url: $errorBody")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load recommended queries", e)
            }
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE)
                as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
    }
}