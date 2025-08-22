package club.gifters.giftersclub.gifts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.MainActivity
import club.gifters.giftersclub.NoNetworkActivity
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.Post
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.payments.PaymentWebViewActivity
import club.gifters.giftersclub.social.SubscriptionApiHolder
import club.gifters.giftersclub.social.PostViewApiHolder
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import club.gifters.giftersclub.util.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


/**
 * Fragment for displaying posts in a vertical, swipeable view (one post per screen).
 */
class PostsFragment : Fragment(R.layout.fragment_posts) {
    private lateinit var pager: ViewPager2
    private var lastViewedPostId: String? = null
    private var lastViewStart: Long = 0L
    companion object {
        private const val TAG = "PostsFragment"
        private const val ARG_POST_ID = "post_id"
        private const val ARG_LIST = "arg_list"
        private const val ARG_START_POSITION = "start_position"
        private const val ARG_USER_ID = "user_id"

        // URLs for like/unlike Lottie animations
        private const val LIKE_LOTTIE_URL =
            "https://lottie.host/fe660a41-2c70-4105-afb4-bab713f7e77b/AcLybokfnG.lottie"
        private const val UNLIKE_LOTTIE_URL =
            "https://lottie.host/810116a8-7247-45df-a8b2-f2d777b491f0/JDVtLuvkEG.lottie"

        fun newInstance(postId: String): PostsFragment {
            val args = Bundle().apply { putString(ARG_POST_ID, postId) }
            return PostsFragment().apply { arguments = args }
        }

        /**
         * Instantiate with a preloaded list of posts and initial index.
         */
        fun newInstanceFromList(list: List<Post>, startPosition: Int): PostsFragment {
            val args = Bundle().apply {
                putString(ARG_LIST, Gson().toJson(list))
                putInt(ARG_START_POSITION, startPosition)
            }
            return PostsFragment().apply { arguments = args }
        }

        /**
         * Instantiate for user context so only that user's posts are shown.
         */
        fun newInstanceForUser(postId: String, userId: String): PostsFragment {
            val args = Bundle().apply {
                putString(ARG_POST_ID, postId)
                putString(ARG_USER_ID, userId)
            }
            return PostsFragment().apply { arguments = args }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // log view duration for last viewed post
        lastViewedPostId?.let { prevId ->
            val durationSec = ((System.currentTimeMillis() - lastViewStart) / 1000).toInt()
            lifecycleScope.launch {
                PostViewApiHolder.logPostView(prevId, durationSec)
            }
        }
    }

    private val api = RetrofitClient.postApi
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var adapter: FeedAdapter
    private var page = 0
    private val limit = 10
    private val perAuthorLimit = 3
    private var isLoading = false
    private var hasRetry401 = false
    private var isLastPage = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // If no internet redirect to NoNetworkActivity
        if (!NetworkUtils.isOnline(requireContext())) {
            startActivity(Intent(requireContext(), NoNetworkActivity::class.java))
            return
        }
        swipeRefresh = view.findViewById(R.id.swipeRefresh)
        pager = view.findViewById(R.id.viewPagerPosts)
        adapter = FeedAdapter(
            lifecycleScope,
            onLike = { post ->
                lifecycleScope.launch {
                    val userId = AuthUtils.getCurrentUserId(requireContext()) ?: return@launch
                    val likedBefore = CommentApiHolder.isPostLikedByUser(post.id)
                    // show like/unlike Lottie animation
                    (activity as? MainActivity)?.showLottieAnimation(
                        if (!likedBefore) LIKE_LOTTIE_URL else UNLIKE_LOTTIE_URL
                    )
                    val body = mapOf(
                        "post_id" to post.id,
                        "user_id" to userId,
                        "type" to "like"
                    )
                    if (!likedBefore) CommentApiHolder.reactToPost(body)
                    else CommentApiHolder.unreactToPost(
                        postIdFilter = "eq.${post.id}",
                        userIdFilter = "eq.$userId",
                        typeFilter = "eq.like"
                    )
                    adapter.currentList
                        .indexOfFirst { it is FeedItem.PostItem && it.post.id == post.id }
                        .takeIf { it >= 0 }
                        ?.let { idx -> adapter.notifyItemChanged(idx) }
                }
            },
            onComment = { post ->
                CommentsBottomSheetFragment.newInstance(post.id)
                    .show(parentFragmentManager, "comments")
            },
            onShare = { post ->
                val deepLink = "giftersclub://posts/${post.id}"
                val webLink = "https://gifters.club/posts/${post.id}"
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

        // If instantiated with explicit list, show it and return
        arguments?.getString(ARG_LIST)?.let { json ->
            val type = object : TypeToken<List<Post>>() {}.type
            val list: List<Post> = Gson().fromJson(json, type)
            adapter.submitList(list.map { FeedItem.PostItem(it) })
            val pos = arguments?.getInt(ARG_START_POSITION) ?: 0
            pager.setCurrentItem(pos, false)
            swipeRefresh.isEnabled = false
            return
        }

        // Enable pull-to-refresh only when at top (first post)
        swipeRefresh.setOnChildScrollUpCallback { _, _ -> pager.currentItem != 0 }
        swipeRefresh.setOnRefreshListener {
            page = 0
            isLastPage = false
            hasRetry401 = false
            loadPosts(clear = true)
        }

        val postId = arguments?.getString(ARG_POST_ID)
        if (postId != null) {
            loadPostById(postId)
            lastViewedPostId = postId
        } else {
            swipeRefresh.isRefreshing = true
            loadPosts(clear = true)
        }
        // start timing for view_duration
        lastViewStart = System.currentTimeMillis()

        // Listen for scroll to end to load more
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // log duration for previous post view
                val now = System.currentTimeMillis()
                lastViewedPostId?.let { prevId ->
                    val durationSec = ((now - lastViewStart) / 1000).toInt()
                    lifecycleScope.launch {
                        PostViewApiHolder.logPostView(prevId, durationSec)
                    }
                }
                // start timing new post view
                lastViewedPostId = (adapter.currentList.getOrNull(position) as? FeedItem.PostItem)
                    ?.post
                    ?.id
                lastViewStart = now
                // load more if at end
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
            // Plan-based subscription: pick required plan or cheapest
            lifecycleScope.launch {
                try {
                    val plans = withContext(Dispatchers.IO) {
                        RetrofitClient.subscriptionPlanApi.getSubscriptionPlans("eq.${post.userId}")
                    }
                    if (plans.isEmpty()) {
                        Toast.makeText(requireContext(), "No subscription plans available", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val cheapest = plans.minBy { it.tokens }
                    val selected = post.requiredPlanId?.let { id -> plans.find { it.id == id } } ?: cheapest
                    val ok = SubscriptionApiHolder.subscribeToCreatorByPlan(post.userId, selected.id)
                    Toast.makeText(
                        requireContext(),
                        if (ok) getString(R.string.subscription_successful) else getString(R.string.subscription_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), getString(R.string.subscription_failed), Toast.LENGTH_SHORT).show()
                }
            }
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
                val items: List<Post> = when (arguments?.getString(ARG_USER_ID)) {
                    null -> {
                        val currentUser = AuthUtils.getCurrentUserId(requireContext())
                        val feedParams = mapOf(
                            "_user_id" to currentUser,
                            "_limit" to limit,
                            "_offset" to page * limit
                        )
                        api.getFeedPosts(feedParams).map { f ->
                            Post(
                                id = f.id,
                                userId = f.userId,
                                content = f.content,
                                quotePostId = null,
                                replyCommentId = null,
                                createdAt = f.createdAt,
                                profile = f.profile,
                                media = f.media,
                                reactionCounts = null,
                                accessType = f.accessType,
                                price = f.price,
                                requiredPlanId = f.requiredPlanId,
                                tags = null
                            )
                        }
                    }
                    else -> {
                        api.getUserPosts(
                            order = "created_at.desc",
                            limit = limit,
                            offset = page * limit,
                            userIdFilter = "eq.${arguments?.getString(ARG_USER_ID)}"
                        )
                    }
                }
                if (clear) {
                    adapter.submitList(interleaveWithLives(items))
                } else {
                    val merged = (adapter.currentList.mapNotNull { (it as? FeedItem.PostItem)?.post } + items)
                    adapter.submitList(interleaveWithLives(merged))
                }
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
                // Let global overlay show the offline state; show failure toast if list empty
                if (adapter.currentList.isEmpty()) {
                    Toast.makeText(requireContext(), "Failed to load posts", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (adapter.currentList.isEmpty()) {
                    Toast.makeText(requireContext(), "Failed to load posts", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isLoading = false
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private suspend fun fetchRankedLives(max: Int): List<club.gifters.giftersclub.model.LiveStream> {
        return try {
            val currentUser = AuthUtils.getCurrentUserId(requireContext())
            val params = mapOf(
                "in_viewer_id" to currentUser,
                "in_limit" to max,
                "in_query" to null
            )
            club.gifters.giftersclub.network.RetrofitClient.liveStreamApi.getFeedLiveStreams(params)
        } catch (_: Exception) { emptyList() }
    }

    private suspend fun interleaveWithLives(posts: List<Post>): List<FeedItem> {
        val items = mutableListOf<FeedItem>()
        val lives = fetchRankedLives(max = (posts.size / 3).coerceAtLeast(1))
        var li = 0
        var since = 0
        var inserted = 0
        val maxLives = 3
        var lastHost: String? = null
        var gap = (3..5).random()
        posts.forEach { p ->
            items += FeedItem.PostItem(p)
            since++
            if (since >= gap && li < lives.size && inserted < maxLives) {
                val s = lives[li]
                if (s.hostId != lastHost) {
                    items += FeedItem.LiveItem(s)
                    lastHost = s.hostId
                    li++
                    since = 0
                    inserted++
                    gap = (3..5).random()
                }
            }
        }
        return items
    }

    private fun loadPostById(postId: String) {
        isLoading = true
        lifecycleScope.launch {
            try {
                // Use PostgREST eq filter so getPostById returns a List<Post>
                val list = api.getPostById("eq.$postId")
                val post = list.firstOrNull() ?: return@launch
                adapter.submitList(listOf(FeedItem.PostItem(post)))
                // Now load the rest of the posts
                page = 0
                isLastPage = false
                loadPosts(clear = false)
            } catch (e: Exception) {
                // Log.e(TAG, "Failed to load post", e)
                Toast.makeText(requireContext(), "Failed to load post", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
                swipeRefresh.isRefreshing = false
            }
        }
    }
}
