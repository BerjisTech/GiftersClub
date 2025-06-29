package club.gifters.giftersclub.explore

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import club.gifters.giftersclub.R
import club.gifters.giftersclub.network.RetrofitClient
import kotlinx.coroutines.launch
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.recyclerview.widget.RecyclerView

/**
 * Fragment for displaying post search results in Explore (Top, Videos, Photos).
 */
class ExplorePostsFragment : Fragment(R.layout.fragment_explore_posts) {
    companion object {
        private const val ARG_QUERY = "query"
        private const val ARG_MEDIA_TYPE = "mediaType"

        /**
         * @param mediaType null for all, or "video" / "photo" to filter media type
         */
        fun newInstance(query: String, mediaType: String?): ExplorePostsFragment {
            return ExplorePostsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_QUERY, query)
                    putString(ARG_MEDIA_TYPE, mediaType)
                }
            }
        }
    }

    private val query: String by lazy { requireArguments().getString(ARG_QUERY).orEmpty() }
    private val mediaType: String? by lazy { requireArguments().getString(ARG_MEDIA_TYPE) }
    private lateinit var adapter: ExplorePostAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val swipe = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
        val rv = view.findViewById<RecyclerView>(R.id.rvPosts)
        adapter = ExplorePostAdapter()
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        swipe.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val filter = "(content.ilike.*${'$'}{query}*)"
                val posts = RetrofitClient.postApi.searchPosts(
                    orFilter = filter,
                    mediaTypeFilter = mediaType?.let { "eq.$it" },
                    order = "reaction_counts.like.desc,created_at.desc",
                    limit = 50,
                    offset = 0
                )
                adapter.submitList(posts)
            } catch (e: Exception) {
                adapter.submitList(emptyList())
            } finally {
                swipe.isRefreshing = false
            }
        }
    }
}