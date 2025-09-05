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
    private var tvPendingUpload: TextView? = null
    private var workObserverRegistered = false

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
        tvPendingUpload = view.findViewById(R.id.tvPendingUpload)
        recyclerView.layoutManager = GridLayoutManager(context, 3) // 3 columns
        swipeRefreshLayout = view.findViewById(R.id.swipeRefresh)
        emptyTextView = view.findViewById(R.id.textEmpty)

        adapter = PostGridAdapter(
            onPostClick = { post ->
                if (isSelectionMode) {
                    togglePostSelection(post)
                } else {
                    requireActivity().supportFragmentManager.beginTransaction()
                        .replace(
                            R.id.mainContentContainer,
                            PostsFragment.newInstanceForUser(post.id, requireNotNull(userId))
                        )
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
        updatePendingBanner()
        observeUploadWork()

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

    override fun onResume() {
        super.onResume()
        updatePendingBanner()
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
        val ctx = context ?: return
        val isEmpty = adapter.currentList.isEmpty()
        swipeRefreshLayout?.isVisible = !isEmpty
        emptyTextView?.isVisible = isEmpty
        if (isEmpty) {
            emptyTextView?.text = if (AuthUtils.getCurrentUserId(ctx) == userId) {
                val text = "You haven't uploaded any posts, get started by creating a new post"
                val spannable = SpannableString(text)
                val clickable = "create a new post"
                val start = text.indexOf(clickable)
                if (start >= 0) {
                    spannable.setSpan(object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            if (!isAdded) return
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

    private fun updatePendingBanner() {
        val ctx = context ?: return
        val pending = UploadTracker.hasPending(ctx, userId)
        val isVideo = UploadTracker.isPendingVideo(ctx, userId)
        tvPendingUpload?.text = if (isVideo) "Uploading video…" else "Posting… your new post is uploading"
        tvPendingUpload?.visibility = if (pending) View.VISIBLE else View.GONE
    }

    private fun observeUploadWork() {
        if (workObserverRegistered || userId.isNullOrBlank()) return
        val tag = "post-upload-${userId}"
        val ctx = context ?: return
        androidx.work.WorkManager.getInstance(ctx)
            .getWorkInfosByTagLiveData(tag)
            .observe(viewLifecycleOwner) { infos ->
                val running = infos.any { it.state == androidx.work.WorkInfo.State.ENQUEUED || it.state == androidx.work.WorkInfo.State.RUNNING }
                if (running) {
                    tvPendingUpload?.visibility = View.VISIBLE
                    injectPlaceholder()
                } else {
                    tvPendingUpload?.visibility = View.GONE
                    // Reload posts so the new one appears after upload completes
                    loadPosts(clear = true)
                }
            }
        workObserverRegistered = true
    }

    private fun injectPlaceholder() {
        // Prepend a synthetic placeholder post at the top if not already present
        val list = adapter.currentList.toMutableList()
        val hasPlaceholder = list.firstOrNull()?.id?.startsWith("pending-") == true
        if (!hasPlaceholder) {
            val placeholder = club.gifters.giftersclub.model.Post(
                id = "pending-" + System.currentTimeMillis(),
                userId = userId ?: "",
                content = "",
                quotePostId = null,
                replyCommentId = null,
                createdAt = null,
                profile = null,
                media = emptyList(),
                reactionCounts = null,
                accessType = "free",
                price = null,
                requiredPlanId = null,
                tags = emptyList()
            )
            list.add(0, placeholder)
            adapter.submitList(list)
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
                context?.let { Toast.makeText(it, "Selected posts deleted", Toast.LENGTH_SHORT).show() }
                selectedPosts.clear()
                isSelectionMode = false
                selectionModeChangeListener?.onSelectionModeChanged(false)
                adapter.notifyDataSetChanged()
                loadPosts(clear = true) // Reload posts after deletion
            } catch (e: Exception) {
                context?.let { Toast.makeText(it, "Failed to delete posts", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun loadPosts(clear: Boolean) {
        if (isLoading || isLastPage) return
        isLoading = true
        lifecycleScope.launch {
            val ctx = context ?: return@launch
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
                val visible = filterAccessible(items, ctx)
                if (clear) adapter.submitList(visible) else adapter.submitList(adapter.currentList + visible)
                if (isAdded) updateEmptyState()
                if (items.size < limit) isLastPage = true else page++
            } catch (e: Exception) {
                context?.let { Toast.makeText(it, "Failed to load posts", Toast.LENGTH_SHORT).show() }
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Remove posts the current user cannot access (subscription or paywalled).
     */
    private suspend fun filterAccessible(posts: List<Post>, ctx: android.content.Context): List<Post> {
        val userId = AuthUtils.getCurrentUserId(ctx)
        return posts.filter { post ->
            when {
                post.accessType == "free" || post.userId == userId -> true
                post.accessType == "subscription" -> SubscriptionApiHolder.hasSubscription(post.userId)
                post.accessType == "paid"         -> SubscriptionApiHolder.hasPostAccess(post.id)
                else                                -> true
            }
        }
    }

    fun refresh() {
        page = 0
        isLastPage = false
        loadPosts(clear = true)
    }
}
