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
import android.util.Log
import club.gifters.giftersclub.explore.ExplorePostAdapter
import club.gifters.giftersclub.model.Post
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Fragment for displaying post search results in Explore (Top, Videos, Photos).
 */
class ExplorePostsFragment : Fragment(R.layout.fragment_explore_posts) {
    companion object {
        private const val TAG = "ExplorePostsFragment"
        private const val ARG_QUERY = "query"
        private const val ARG_MEDIA_TYPE = "mediaType"
        private const val ARG_LIST = "arg_list"

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

        /**
         * Instantiate with a preloaded list of posts.
         */
        fun newInstanceFromList(list: List<Post>): ExplorePostsFragment {
            return ExplorePostsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_LIST, Gson().toJson(list))
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
        // If created with explicit list, show it and return
        arguments?.getString(ARG_LIST)?.let { json ->
            val type = object : TypeToken<List<Post>>() {}.type
            adapter.submitList(Gson().fromJson(json, type))
            swipe.isRefreshing = false
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 1) direct content and hashtag matches
                val contentFilter = "(content.ilike.*${query}*,content.ilike.*#${query}*)"
                Log.d(TAG, "Direct filter=$contentFilter")
                val direct = RetrofitClient.postApi.searchPosts(
                    orFilter = contentFilter,
                    mediaTypeFilter = mediaType?.let { "eq.$it" },
                    order = "reaction_counts.comment.desc,reaction_counts.like.desc,created_at.desc",
                    limit = 50,
                    offset = 0
                )
                // 2) posts via matching comments
                val commentFilter = "(content.ilike.*${query}*)"
                Log.d(TAG, "Comment filter=$commentFilter")
                val comments = RetrofitClient.commentApi.searchComments(
                    select = "post_id",
                    orFilter = commentFilter
                )
                val commentPostIds = comments.map { it.postId }.distinct()
                val commentPosts = if (commentPostIds.isNotEmpty()) {
                    RetrofitClient.postApi.getPostsByIds(
                        idFilter = "in.(${commentPostIds.joinToString(",")})",
                        mediaTypeFilter = mediaType?.let { "eq.$it" },
                        order = "reaction_counts.comment.desc,reaction_counts.like.desc,created_at.desc"
                    )
                } else emptyList()
                // 3) posts via matching profile name/username
                val userFilter = "(username.ilike.*${query}*,name.ilike.*${query}*)"
                Log.d(TAG, "User filter=$userFilter")
                val profiles = RetrofitClient.profileApi.searchProfiles("*", userFilter)
                val userIds = profiles.map { it.userId }.distinct()
                val userPosts = if (userIds.isNotEmpty()) {
                    RetrofitClient.postApi.getUserPosts(
                        order = "reaction_counts.comment.desc,reaction_counts.like.desc,created_at.desc",
                        limit = 50,
                        offset = 0,
                        userIdFilter = "in.(${userIds.joinToString(",")})"
                    )
                } else emptyList()
                // merge unique in priority order
                Log.d(TAG, "Results sizes direct=${direct.size}, commentPosts=${commentPosts.size}, userPosts=${userPosts.size}")
                val seen = mutableSetOf<String>()
                val merged = mutableListOf<Post>()
                direct.filter { seen.add(it.id) }.let { merged += it }
                commentPosts.filter { seen.add(it.id) }.let { merged += it }
                userPosts.filter { seen.add(it.id) }.let { merged += it }
                Log.d(TAG, "Merged total=${merged.size} posts")
                if (merged.isEmpty()) {
                    Log.d(TAG, "Merged empty, falling back to direct content search")
                    val fallbackFilter = "(content.ilike.*${query}*)"
                    val fallback = RetrofitClient.postApi.searchPosts(
                        orFilter = fallbackFilter,
                        mediaTypeFilter = mediaType?.let { "eq.$it" },
                        order = "reaction_counts.like.desc,created_at.desc",
                        limit = 50,
                        offset = 0
                    )
                    try {
                        adapter.submitList(fallback)
                    } catch (e: Exception) {
                        Log.w(TAG, "Fallback content search failed", e)
                        adapter.submitList(emptyList())
                    }
                } else {
                    adapter.submitList(merged)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Post search failed", e)
                adapter.submitList(emptyList())
            } finally {
                swipe.isRefreshing = false
            }
        }
    }
}