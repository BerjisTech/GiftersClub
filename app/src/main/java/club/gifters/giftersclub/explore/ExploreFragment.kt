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
import kotlinx.coroutines.launch

/**
 * Fragment for explore search with suggestions and tabbed results.
 */
class ExploreFragment : Fragment(R.layout.fragment_explore) {
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
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val tags = RetrofitClient.tagApi.searchTags("*", "*${'$'}q*")
                        val suggestions = tags.map { it.name }
                        if (suggestions.isNotEmpty()) {
                            suggestionAdapter.submitList(suggestions)
                            rvSuggestions.visibility = View.VISIBLE
                        } else {
                            suggestionAdapter.submitList(emptyList())
                            rvSuggestions.visibility = View.GONE
                        }
                    } catch (e: Exception) {
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
    }

    private fun performSearch(query: String) {
        rvSuggestions.visibility = View.GONE
        hideKeyboard()
        tabLayout.visibility = View.VISIBLE
        viewPager.visibility = View.VISIBLE
        viewPager.adapter = object : FragmentStateAdapter(this) {
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
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE)
                as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
    }
}