package club.gifters.giftersclub.gifts

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.Post
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.gifts.GifterFragment
import club.gifters.giftersclub.gifts.PostsFragment
import club.gifters.giftersclub.gifts.PostGridAdapter
import kotlinx.coroutines.launch
import club.gifters.giftersclub.AuthUtils

private const val ARG_USER_ID = "user_id"

/**
 * Fragment for displaying posts by a specific user in a grid view.
 */
class UserPostsFragment : Fragment(R.layout.fragment_user_posts) {
    private val api = RetrofitClient.postApi
    private lateinit var adapter: PostGridAdapter
    private var page = 0
    private val limit = 10
    private var isLoading = false
    private var isLastPage = false
    private var userId: String? = null

    private var isSelectionMode = false
    private val selectedPosts = mutableSetOf<Post>()

    // Callback to GifterFragment to show/hide delete button
    interface OnSelectionModeChangeListener {
        fun onSelectionModeChanged(inSelectionMode: Boolean)
        fun onDeleteSelectedPosts(selectedPosts: Set<Post>)
    }

    private var selectionModeChangeListener: OnSelectionModeChangeListener? = null

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        if (parentFragment is OnSelectionModeChangeListener) {
            selectionModeChangeListener = parentFragment as OnSelectionModeChangeListener
        }
    }

    override fun onDetach() {
        super.onDetach()
        selectionModeChangeListener = null
    }

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

    private val canDelete: Boolean
        get() = AuthUtils.getCurrentUserId(requireContext()) == userId

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewPosts)
        recyclerView.layoutManager = GridLayoutManager(context, 3) // 3 columns

        adapter = PostGridAdapter(
            onPostClick = { post ->
                if (isSelectionMode) {
                    togglePostSelection(post)
                } else {
                    requireActivity().supportFragmentManager.beginTransaction()
                        .replace(R.id.mainContentContainer, PostsFragment.newInstance(post.id))
                        .addToBackStack(null)
                        .commit()
                }
            },
            onPostLongClick = fun(post) {
                if (!canDelete) return
                if (!isSelectionMode) {
                    isSelectionMode = true
                    selectionModeChangeListener?.onSelectionModeChanged(true)
                }
                togglePostSelection(post)
            },
            isSelectionMode = { isSelectionMode },
            isPostSelected = { post -> selectedPosts.contains(post) },
            togglePostSelection = { post -> togglePostSelection(post) }
        )
        recyclerView.adapter = adapter

        // Load initial posts
        loadPosts(clear = true)

        // Infinite scroll
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as GridLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!isLoading && !isLastPage) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                        && firstVisibleItemPosition >= 0
                        && totalItemCount >= limit) {
                        loadPosts(clear = false)
                    }
                }
            }
        })
    }

    private fun togglePostSelection(post: Post) {
        if (selectedPosts.contains(post)) {
            selectedPosts.remove(post)
        } else {
            selectedPosts.add(post)
        }
        adapter.notifyDataSetChanged()
        if (selectedPosts.isEmpty()) {
            if (isSelectionMode) {
                isSelectionMode = false
                selectionModeChangeListener?.onSelectionModeChanged(false)
                adapter.notifyDataSetChanged()
            }
        } else {
            selectionModeChangeListener?.onDeleteSelectedPosts(selectedPosts)
        }
    }

    fun deleteSelectedPosts() {
        if (selectedPosts.isEmpty()) return

        lifecycleScope.launch {
            try {
                selectedPosts.forEach { post ->
                    api.deletePost("eq.${post.id}")
                }
                // Optimistically remove deleted posts from UI before reloading
                val remaining = adapter.currentList.filterNot { selectedPosts.contains(it) }
                adapter.submitList(remaining)
                Toast.makeText(requireContext(), "Selected posts deleted", Toast.LENGTH_SHORT).show()
                selectedPosts.clear()
                isSelectionMode = false
                selectionModeChangeListener?.onSelectionModeChanged(false)
                adapter.notifyDataSetChanged()
                loadPosts(clear = true) // Reload posts after deletion
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to delete posts", Toast.LENGTH_SHORT).show()
            }
        }
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