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
import androidx.appcompat.app.AlertDialog
import android.widget.EditText
import android.widget.LinearLayout
import club.gifters.giftersclub.AuthUtils
import retrofit2.HttpException
import club.gifters.giftersclub.gifts.PostAdapter
import kotlinx.coroutines.launch
import club.gifters.giftersclub.gifts.CommentApiHolder
import club.gifters.giftersclub.model.Post
import club.gifters.giftersclub.social.SubscriptionApiHolder


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
            onLike = { post ->
                lifecycleScope.launch {
                    val userId = AuthUtils.getCurrentUserId(requireContext()) ?: return@launch
                    val liked = CommentApiHolder.isPostLikedByUser(post.id)
                    val body = mapOf(
                        "post_id" to post.id,
                        "user_id" to userId,
                        "type" to "like"
                    )
                    if (!liked) CommentApiHolder.reactToPost(body)
                    else CommentApiHolder.unreactToPost(
                        postIdFilter = "eq.${post.id}",
                        userIdFilter = "eq.$userId",
                        typeFilter = "eq.like"
                    )
                    adapter.currentList.indexOf(post).takeIf { it >= 0 }?.let { idx ->
                        adapter.notifyItemChanged(idx)
                    }
                }
            },
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
            },
            onLocked = { post -> onLocked(post) },
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

    private fun onLocked(post: Post) {
        val userId = AuthUtils.getCurrentUserId(requireContext())
            ?: run {
                Toast.makeText(requireContext(), "Please login to proceed.", Toast.LENGTH_SHORT).show()
                return
            }
        if (post.accessType == "subscription") {
            // prompt for subscription parameters
            val durationInput = EditText(requireContext()).apply {
                hint = "Subscription type (one_time, monthly, annual)"
            }
            val tokensInput = EditText(requireContext()).apply {
                hint = "Price in tokens"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
            LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 20, 50, 0)
                addView(durationInput)
                addView(tokensInput)
            }.let { layout ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Subscribe to creator")
                    .setView(layout)
                    .setPositiveButton("Subscribe") { _, _ ->
                        val durationType = durationInput.text.toString().trim()
                        val tokens = tokensInput.text.toString().toIntOrNull() ?: 0
                        val txRef = "sub_${userId}_${System.currentTimeMillis()}"
                        lifecycleScope.launch {
                            val ok = SubscriptionApiHolder.subscribeToCreator(
                                post.userId, userId, tokens, durationType, txRef
                            )
                            Toast.makeText(
                                requireContext(),
                                if (ok) "Subscription successful!" else "Subscription failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        } else if (post.accessType == "paid") {
            AlertDialog.Builder(requireContext())
                .setTitle("Purchase access")
                .setMessage("Purchase access for ${post.price ?: 0} tokens?")
                .setPositiveButton("Buy") { _, _ ->
                    val txRef = "post_${userId}_${post.id}_${System.currentTimeMillis()}"
                    lifecycleScope.launch {
                        val ok = SubscriptionApiHolder.purchasePostAccess(
                            post.id, userId, post.price ?: 0, txRef
                        )
                        Toast.makeText(
                            requireContext(),
                            if (ok) "Purchase successful!" else "Purchase failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
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