package club.gifters.giftersclub.gifts

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import android.content.Context
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import club.gifters.giftersclub.R
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.gifts.GifterFragment
import club.gifters.giftersclub.gifts.CommentsBottomSheetFragment
import android.util.Log
import android.widget.Toast
import retrofit2.HttpException
import club.gifters.giftersclub.gifts.PostAdapter
import kotlinx.coroutines.launch

/**
 * Fragment for displaying posts in a vertical, swipeable view (one post per screen).
 */
class PostsFragment : Fragment(R.layout.fragment_posts) {
    companion object {
        private const val TAG = "PostsFragment"
    }

    private val api = RetrofitClient.postApi
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var adapter: PostAdapter
    private var page = 0
    private val limit = 10
    private var isLoading = false
    private var hasRetry401 = false
    private var isLastPage = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        val pager = view.findViewById<ViewPager2>(R.id.viewPagerPosts)
        adapter = PostAdapter(
            lifecycleScope,
            onLike = { /* TODO: handle like */ },
            onComment = { post ->
                CommentsBottomSheetFragment.newInstance(post.id)
                    .show(parentFragmentManager, "comments")
            },
            onShare = { /* TODO: handle share */ },
            onProfileClick = { uname ->
                parentFragmentManager.beginTransaction()
                    .replace(R.id.mainContentContainer, GifterFragment.newInstance(uname))
                    .addToBackStack(null)
                    .commit()
            }
        )
        pager.adapter = adapter

        // Enable pull-to-refresh only when at top (first post)
        swipeRefresh.setOnChildScrollUpCallback { _, _ -> pager.currentItem != 0 }
        swipeRefresh.setOnRefreshListener {
            // reset pagination and reload newest posts
            page = 0
            isLastPage = false
            hasRetry401 = false
            loadPosts(clear = true)
        }
        // Load initial posts
        swipeRefresh.isRefreshing = true
        loadPosts(clear = true)

        // Listen for scroll to end to load more
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (!isLoading && !isLastPage && position >= adapter.itemCount - 1) {
                    loadPosts(clear = false)
                }
            }
        })
    }

    private fun loadPosts(clear: Boolean) {
        if (isLoading || isLastPage) return
        isLoading = true
        lifecycleScope.launch {
            try {
                val items = api.getPosts(
                    order = "created_at.desc",
                    limit = limit,
                    offset = page * limit
                )
                if (clear) adapter.submitList(items)
                else adapter.submitList(adapter.currentList + items)
                if (items.size < limit) isLastPage = true else page++
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401 && !hasRetry401) {
                    hasRetry401 = true
                    // Clear invalid token to fall back to anon access and retry
                    requireContext().getSharedPreferences("supabase", Context.MODE_PRIVATE)
                        .edit().remove("access_token").remove("refresh_token").apply()
                    loadPosts(clear)
                    return@launch
                }
                Log.e(TAG, "Failed to load posts", e)
                Toast.makeText(requireContext(), "Failed to load posts", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load posts", e)
                Toast.makeText(requireContext(), "Failed to load posts", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
                swipeRefresh.isRefreshing = false
            }
        }
    }
}