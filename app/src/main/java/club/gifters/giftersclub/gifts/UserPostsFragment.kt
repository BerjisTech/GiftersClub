package club.gifters.giftersclub.gifts

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.Post
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.gifts.GifterFragment
import club.gifters.giftersclub.gifts.PostAdapter
import kotlinx.coroutines.launch

private const val ARG_USER_ID = "user_id"

/**
 * Fragment for displaying posts by a specific user in a swipeable view.
 */
class UserPostsFragment : Fragment(R.layout.fragment_posts) {
    private val api = RetrofitClient.postApi
    private lateinit var adapter: PostAdapter
    private var page = 0
    private val limit = 10
    private var isLoading = false
    private var isLastPage = false
    private var hasRetry401 = false
    private var userId: String? = null

    companion object {
        fun newInstance(userId: String): UserPostsFragment {
            val args = Bundle().apply { putString(ARG_USER_ID, userId) }
            return UserPostsFragment().apply { arguments = args }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userId = arguments?.getString(ARG_USER_ID)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pager = view.findViewById<ViewPager2>(R.id.viewPagerPosts)
        adapter = PostAdapter(
            lifecycleScope,
            onLike = {}, onComment = {}, onShare = {}, onProfileClick = { uname ->
                parentFragmentManager.beginTransaction()
                    .replace(R.id.mainContentContainer, GifterFragment.newInstance(uname))
                    .addToBackStack(null)
                    .commit()
            }
        )
        pager.adapter = adapter

        // Load initial posts
        loadPosts(clear = true)

        // Infinite scroll
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
                val items: List<Post> = if (userId.isNullOrBlank()) {
                    api.getPosts(order = "created_at.desc", limit = limit, offset = page * limit)
                } else {
                    api.getUserPosts(
                        order = "created_at.desc", limit = limit,
                        offset = page * limit, userIdFilter = "eq.$userId"
                    )
                }
                if (clear) adapter.submitList(items) else adapter.submitList(adapter.currentList + items)
                if (items.size < limit) isLastPage = true else page++
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to load posts", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }
}