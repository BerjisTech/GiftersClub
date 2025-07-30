package club.gifters.giftersclub.gifts

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.Post
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.social.SubscriptionApiHolder
import kotlinx.coroutines.launch
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.widget.TextView
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.method.LinkMovementMethod
import androidx.core.view.isVisible
import club.gifters.giftersclub.gifts.CreatePostFragment

private const val ARG_USER_ID = "user_id"
private const val ARG_USERNAME = "username"

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
    private var username: String? = null
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var emptyTextView: TextView? = null

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
        fun newInstance(userId: String, username: String): UserPostsFragment {
            val args = Bundle().apply {
                putString(ARG_USER_ID, userId)
                putString(ARG_USERNAME, username)
            }
            return UserPostsFragment().apply { arguments = args }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userId = arguments?.getString(ARG_USER_ID)
        username = arguments?.getString(ARG_USERNAME)
    }

    private val canDelete: Boolean
        get() = AuthUtils.getCurrentUserId(requireContext()) == userId

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerViewPosts)
        recyclerView.layoutManager = GridLayoutManager(context, 3) // 3 columns
        swipeRefreshLayout = view.findViewById(R.id.swipeRefresh)
        emptyTextView = view.findViewById(R.id.textEmpty)

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

    private fun updateEmptyState() {
        val isEmpty = adapter.currentList.isEmpty()
        swipeRefreshLayout?.isVisible = !isEmpty
        emptyTextView?.isVisible = isEmpty
        if (isEmpty) {
            emptyTextView?.text = if (AuthUtils.getCurrentUserId(requireContext()) == userId) {
                val text = "You haven't uploaded any posts, get started by creating a new post"
                val spannable = SpannableString(text)
                val clickable = "create a new post"
                val start = text.indexOf(clickable)
                if (start >= 0) {
                    spannable.setSpan(object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            requireActivity().supportFragmentManager.beginTransaction()
                                .replace(R.id.mainContentContainer, CreatePostFragment())
                                .addToBackStack(null)
                                .commit()
                        }
                    }, start, start + clickable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                emptyTextView?.movementMethod = LinkMovementMethod.getInstance()
                spannable
            } else {
                "@${username} has not uploaded anything yet"
            }
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
                // filter posts the user cannot access
                val visible = filterAccessible(items)
                if (clear) adapter.submitList(visible) else adapter.submitList(adapter.currentList + visible)
                updateEmptyState()
                if (items.size < limit) isLastPage = true else page++
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to load posts", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Remove posts the current user cannot access (subscription or paywalled).
     */
    private suspend fun filterAccessible(posts: List<Post>): List<Post> {
        val userId = AuthUtils.getCurrentUserId(requireContext())
        return posts.filter { post ->
            when {
                post.accessType == "free" || post.userId == userId -> true
                post.accessType == "subscription" -> SubscriptionApiHolder.hasSubscription(post.userId)
                post.accessType == "paid"         -> SubscriptionApiHolder.hasPostAccess(post.id)
                else                                -> true
            }
        }
    }
}