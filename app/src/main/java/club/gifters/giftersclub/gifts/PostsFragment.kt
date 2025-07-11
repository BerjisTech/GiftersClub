package club.gifters.giftersclub.gifts

import android.content.Intent
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.R
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.gifts.GifterFragment
import club.gifters.giftersclub.gifts.PostAdapter
import club.gifters.giftersclub.gifts.CommentApiHolder
import club.gifters.giftersclub.model.Post
import club.gifters.giftersclub.payments.PaymentWebViewActivity
import club.gifters.giftersclub.social.SubscriptionApiHolder
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import retrofit2.HttpException
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast


/**
 * Fragment for displaying posts in a vertical, swipeable view (one post per screen).
 */
class PostsFragment : Fragment(R.layout.fragment_posts) {
    private lateinit var pager: ViewPager2
    companion object {
        private const val TAG = "PostsFragment"
        private const val ARG_POST_ID = "post_id"

        fun newInstance(postId: String): PostsFragment {
            val args = Bundle().apply { putString(ARG_POST_ID, postId) }
            return PostsFragment().apply { arguments = args }
        }
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
        pager = view.findViewById(R.id.viewPagerPosts)
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
            onShare = { post ->
                val deepLink = "giftersclub://post/${post.id}"
                val webLink = "https://gifters.club/post/${post.id}"
                val shareText = "Check out this post on Gifters Club!\n$webLink"

                val intent = android.content.Intent(android.content.Intent.ACTION_SEND)
                intent.type = "text/plain"
                intent.putExtra(android.content.Intent.EXTRA_TEXT, shareText)

                val chooser = android.content.Intent.createChooser(intent, "Share Post")
                startActivity(chooser)
            },
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

        val postId = arguments?.getString("post_id")
        if (postId != null) {
            loadPostById(postId)
        } else {
            // Load initial posts
            swipeRefresh.isRefreshing = true
            loadPosts(clear = true)
        }

        // Listen for scroll to end to load more
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (!isLoading && !isLastPage && position >= adapter.itemCount - 1) {
                    loadPosts(clear = false)
                }
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageScrollStateChanged(state: Int) {}
        })
    }

    private fun onLocked(post: Post) {
        val userId = AuthUtils.getCurrentUserId(requireContext())
            ?: run {
                Toast.makeText(requireContext(), getString(R.string.login_to_proceed), Toast.LENGTH_SHORT).show()
                return
            }
        if (post.accessType == "subscription") {
            val sheet = BottomSheetDialog(requireContext())
            sheet.setOnShowListener { dialogInterface: android.content.DialogInterface ->
                (dialogInterface as BottomSheetDialog)
                    .findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                    ?.setBackgroundResource(R.drawable.bg_rounded_top)
            }
            val view = layoutInflater.inflate(R.layout.dialog_subscribe_creator, null)
            val etDur = view.findViewById<EditText>(R.id.etDurationType)
            val etTok = view.findViewById<EditText>(R.id.etSubscriptionTokens)
            view.findViewById<Button>(R.id.btnSubscribeConfirm).setOnClickListener {
                val durationType = etDur.text.toString().trim()
                val tokens = etTok.text.toString().toIntOrNull() ?: 0
                val txRef = "sub_${userId}_${System.currentTimeMillis()}"
                sheet.dismiss()
                lifecycleScope.launch {
                    val ok = SubscriptionApiHolder.subscribeToCreator(
                        post.userId, userId, tokens, durationType, txRef
                    )
                    Toast.makeText(
                        requireContext(),
                        if (ok) getString(R.string.subscription_successful) else getString(R.string.subscription_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            view.findViewById<Button>(R.id.btnSubscribeCancel).setOnClickListener { sheet.dismiss() }
            sheet.setContentView(view)
            sheet.show()
        } else if (post.accessType == "paid") {
            val sheet = BottomSheetDialog(requireContext())
            sheet.setOnShowListener { dialogInterface: android.content.DialogInterface ->
                (dialogInterface as BottomSheetDialog)
                    .findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                    ?.setBackgroundResource(R.drawable.bg_rounded_top)
            }
            val view = layoutInflater.inflate(R.layout.dialog_purchase_post_access, null)
            val tvMsg = view.findViewById<TextView>(R.id.tvPurchaseMessage)
            tvMsg.text = getString(R.string.purchase_for_tokens, post.price ?: 0)
            view.findViewById<Button>(R.id.btnPurchaseConfirm).setOnClickListener {
                sheet.dismiss()
                lifecycleScope.launch {
                    val profList = RetrofitClient.profileApi.getProfileByUserId("*", "eq.$userId")
                    val prof = profList.firstOrNull()
                    val balance = prof?.tokenBalance ?: 0
                    val price = post.price ?: 0
                    if (balance < price) {
                        val needed = price - balance
                        val topupRef = "topup_${userId}_${System.currentTimeMillis()}"
                        PaymentWebViewActivity.start(
                            requireContext(), userId, prof?.email.orEmpty(), needed, topupRef, ""
                        )
                        return@launch
                    }
                    val txRef = "post_${userId}_${post.id}_${System.currentTimeMillis()}"
                    val ok = SubscriptionApiHolder.purchasePostAccess(
                        post.id, userId, price, txRef
                    )
                    Toast.makeText(
                        requireContext(),
                        if (ok) getString(R.string.purchase_successful) else getString(R.string.purchase_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    if (ok) {
                        // remove lock overlay and show post immediately
                        pager.adapter?.notifyDataSetChanged()
                    }
                }
            }
            view.findViewById<Button>(R.id.btnPurchaseCancel).setOnClickListener { sheet.dismiss() }
            sheet.setContentView(view)
            sheet.show()
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

    private fun loadPostById(postId: String) {
        isLoading = true
        lifecycleScope.launch {
            try {
                // Use PostgREST eq filter so getPostById returns a List<Post>
                val list = api.getPostById("eq.$postId")
                val post = list.firstOrNull() ?: return@launch
                adapter.submitList(listOf(post))
                // Now load the rest of the posts
                page = 0
                isLastPage = false
                loadPosts(clear = false)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load post", e)
                Toast.makeText(requireContext(), "Failed to load post", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
                swipeRefresh.isRefreshing = false
            }
        }
    }
}