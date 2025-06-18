package club.gifters.giftersclub.gifts

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import club.gifters.giftersclub.R
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.gifts.GifterFragment
import club.gifters.giftersclub.gifts.PostAdapter
import kotlinx.coroutines.launch

/**
 * Fragment for displaying posts in a vertical, swipeable view (one post per screen).
 */
class PostsFragment : Fragment(R.layout.fragment_posts) {

    private val api = RetrofitClient.postApi
    private lateinit var adapter: PostAdapter
    private var page = 0
    private val limit = 10
    private var isLoading = false
    private var isLastPage = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pager = view.findViewById<ViewPager2>(R.id.viewPagerPosts)
        adapter = PostAdapter(
            onLike = { /* TODO: handle like */ },
            onComment = { /* TODO: handle comment */ },
            onShare = { /* TODO: handle share */ },
            onProfileClick = { uname ->
                parentFragmentManager.beginTransaction()
                    .replace(R.id.mainContentContainer, GifterFragment.newInstance(uname))
                    .addToBackStack(null)
                    .commit()
            }
        )
        pager.adapter = adapter

        // Load initial posts
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
            val items = api.getPosts(
                select = "*",
                order = "created_at.desc",
                limit = limit,
                offset = page * limit
            )
            if (clear) adapter.submitList(items)
            else adapter.submitList(adapter.currentList + items)
            if (items.size < limit) isLastPage = true else page++
            isLoading = false
        }
    }
}