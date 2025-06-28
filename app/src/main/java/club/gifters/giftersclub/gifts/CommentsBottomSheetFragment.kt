package club.gifters.giftersclub.gifts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import club.gifters.giftersclub.R
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.gifts.Comment
import club.gifters.giftersclub.gifts.CommentReactionCounts
import club.gifters.giftersclub.gifts.CommentAdapter
import club.gifters.giftersclub.gifts.GifterFragment
import android.content.Context
import android.util.Base64
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import club.gifters.giftersclub.AuthUtils
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.app.Dialog
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Bottom sheet fragment to display and post comments for a given post.
 */
class CommentsBottomSheetFragment : BottomSheetDialogFragment() {
    private lateinit var rvComments: RecyclerView
    private lateinit var etComment: EditText
    private lateinit var btnSendComment: ImageButton
    private lateinit var progressComments: ProgressBar
    private lateinit var adapter: CommentAdapter
    private lateinit var tvEmptyComments: TextView
    private var postId: String = ""

    companion object {
        private const val ARG_POST_ID = "arg_post_id"
        fun newInstance(postId: String) = CommentsBottomSheetFragment().apply {
            arguments = Bundle().apply { putString(ARG_POST_ID, postId) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        postId = arguments?.getString(ARG_POST_ID) ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_comments_bottom_sheet, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rvComments = view.findViewById(R.id.rvComments)
        etComment = view.findViewById(R.id.etComment)
        btnSendComment = view.findViewById(R.id.btnSendComment)
        progressComments = view.findViewById(R.id.progressComments)
        tvEmptyComments = view.findViewById(R.id.tvEmptyComments)

        var replyingTo: Comment? = null
        adapter = CommentAdapter(
            onReply = { comment ->
                replyingTo = comment
                etComment.setText("@${comment.profile?.username.orEmpty()} ")
                etComment.requestFocus()
            },
            onLike = { comment ->
                lifecycleScope.launch {
                    // include user_id so RLS allows reacting
                    val prefs = requireContext().getSharedPreferences("supabase", Context.MODE_PRIVATE)
                    val token = prefs.getString("access_token", "") ?: ""
                    var userId = ""
                    try {
                        val parts = token.split('.')
                        if (parts.size >= 2) {
                            val decoded = String(Base64.decode(parts[1], Base64.URL_SAFE), Charsets.UTF_8)
                            userId = JSONObject(decoded).optString("sub")
                        }
                    } catch (_: Exception) {}
                    val body = mapOf(
                        "comment_id" to comment.id,
                        "user_id" to userId,
                        "type" to "like"
                    )
                    CommentApiHolder.reactToComment(body)
                    adapter.currentList.indexOf(comment).takeIf { it >= 0 }?.let { idx ->
                        adapter.notifyItemChanged(idx)
                    }
                }
            },
            onDislike = { comment ->
                lifecycleScope.launch {
                    // include user_id so RLS allows reacting
                    val prefs = requireContext().getSharedPreferences("supabase", Context.MODE_PRIVATE)
                    val token = prefs.getString("access_token", "") ?: ""
                    var userId = ""
                    try {
                        val parts = token.split('.')
                        if (parts.size >= 2) {
                            val decoded = String(Base64.decode(parts[1], Base64.URL_SAFE), Charsets.UTF_8)
                            userId = JSONObject(decoded).optString("sub")
                        }
                    } catch (_: Exception) {}
                    val body = mapOf(
                        "comment_id" to comment.id,
                        "user_id" to userId,
                        "type" to "dislike"
                    )
                    CommentApiHolder.reactToComment(body)
                    adapter.currentList.indexOf(comment).takeIf { it >= 0 }?.let { idx ->
                        adapter.notifyItemChanged(idx)
                    }
                }
            },
            onProfileClick = { username ->
                parentFragmentManager.beginTransaction()
                    .replace(R.id.mainContentContainer, GifterFragment.newInstance(username))
                    .addToBackStack(null)
                    .commit()
            },
            scope = viewLifecycleOwner.lifecycleScope
        )
        rvComments.layoutManager = LinearLayoutManager(context)
        rvComments.adapter = adapter

        btnSendComment.setOnClickListener {
            val content = etComment.text.toString().trim()
            if (content.isNotBlank()) {
                lifecycleScope.launch {
                    // build comment payload including user_id (for RLS)
                    val prefs = requireContext().getSharedPreferences("supabase", Context.MODE_PRIVATE)
                    val token = prefs.getString("access_token", "") ?: ""
                    var userId = ""
                    try {
                        val parts = token.split('.')
                        if (parts.size >= 2) {
                            val decoded = String(Base64.decode(parts[1], Base64.URL_SAFE), Charsets.UTF_8)
                            userId = JSONObject(decoded).optString("sub")
                        }
                    } catch (_: Exception) {}
                    val body = mutableMapOf<String, Any>(
                        "post_id" to postId,
                        "user_id" to userId,
                        "content" to content
                    )
                    replyingTo?.id?.let { body["parent_comment_id"] = it }
                    CommentApiHolder.createComment(body)
                    replyingTo = null
                    etComment.text.clear()
                    loadComments()
                }
            }
        }

        loadComments()
    }

    override fun onStart() {
        super.onStart()
        dialog?.let { dlg ->
            val bottomSheet = dlg.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                // Always stick to the bottom and expand fully
                behavior.isFitToContents = true
                behavior.halfExpandedRatio = 0.7f
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        return dialog
    }

    private fun loadComments() {
        lifecycleScope.launch {
            rvComments.isVisible = false
            progressComments.isVisible = true
            val list = try {
                CommentApiHolder.getCommentsByPost(postId)
            } catch (e: Exception) {
                // avoid crash on malformed GET
                emptyList()
            }
            // fetch like/dislike counts for each comment before grouping
            list.forEach { c ->
                val likes = CommentApiHolder.getCommentReactionCountValue(c.id, "like")
                val dislikes = CommentApiHolder.getCommentReactionCountValue(c.id, "dislike")
                c.reactionCounts = CommentReactionCounts(likes, dislikes)
                // attach nested replies
                c.replies = list.filter { it.parentCommentId == c.id }
            }
            // show empty state if no comments
            tvEmptyComments.isVisible = list.isEmpty()
            // show empty state if no comments
            tvEmptyComments.isVisible = list.isEmpty()
            // group top-level comments, sort them, then append their replies (sorted too)
            val comparator = compareByDescending<Comment> { it.reactionCounts?.like ?: 0 }
                .thenByDescending { it.createdAt }
                .thenBy { it.reactionCounts?.dislike ?: 0 }
            val grouped = list.filter { it.parentCommentId == null }
                .sortedWith(comparator)
                .flatMap { parent ->
                    val children = parent.replies.orEmpty().sortedWith(comparator)
                    listOf(parent) + children
                }
            adapter.submitList(grouped)
            rvComments.isVisible = true
            progressComments.isVisible = false
        }
    }
}

/**
 * Simple object to hold the CommentApi for convenience.
 */
object CommentApiHolder {
    private val api = RetrofitClient.commentApi
    suspend fun getCommentsByPost(postId: String) = api.getCommentsByPost(
        postIdFilter = "eq.$postId"
    )
    /**
     * Insert a new comment (user_id must be provided in body for RLS).
     */
    suspend fun createComment(
        comment: Map<String, @JvmSuppressWildcards Any>
    ) = api.createComment(select = "*", comment = comment)
    /** React to a comment (like/dislike), user_id must be provided in body for RLS. */
    /** React to a comment (like/dislike); user_id must be included for RLS. */
    suspend fun reactToComment(
        reaction: Map<String, @JvmSuppressWildcards Any>
    ) = api.reactToComment(reaction)

    suspend fun getCommentReactionCountValue(commentId: String, type: String): Int {
        val resp = api.getCommentReactionCount(
            commentIdFilter = "eq.$commentId",
            type = "eq.$type"
        )
        val contentRange = resp.headers()["Content-Range"] ?: return 0
        return contentRange.substringAfterLast('/')?.toIntOrNull() ?: 0
    }

    suspend fun getPostCommentCountValue(postId: String): Int {
        val resp = api.getPostCommentCount(postIdFilter = "eq.$postId")
        val contentRange = resp.headers()["Content-Range"] ?: return 0
        return contentRange.substringAfterLast('/')?.toIntOrNull() ?: 0
    }

    suspend fun getCommentReplyCountValue(commentId: String): Int {
        val resp = api.getCommentReplyCount(parentIdFilter = "eq.$commentId")
        val contentRange = resp.headers()["Content-Range"] ?: return 0
        return contentRange.substringAfterLast('/')?.toIntOrNull() ?: 0
    }

    suspend fun getPostReactionCountValue(postId: String, type: String): Int {
        val resp = api.getPostReactionCount(
            postIdFilter = "eq.$postId",
            typeFilter = "eq.$type"
        )
        val contentRange = resp.headers()["Content-Range"] ?: return 0
        return contentRange.substringAfterLast('/')?.toIntOrNull() ?: 0
    }

    /**
     * React (like/share) to a post; user_id must be provided for RLS.
     */
    suspend fun reactToPost(reaction: Map<String, @JvmSuppressWildcards Any>) =
        api.reactToPost(reaction)

    /**
     * Remove a reaction (unlike/unshare) from a post.
     */
    suspend fun unreactToPost(
        postIdFilter: String,
        userIdFilter: String,
        typeFilter: String
    ) = api.unreactToPost(postIdFilter, userIdFilter, typeFilter)

    /**
     * Returns true if the current user has liked the given post.
     */
    suspend fun isPostLikedByUser(postId: String): Boolean = withContext(Dispatchers.IO) {
        val current = AuthUtils.getCurrentUserId(RetrofitClient.context) ?: return@withContext false
        val resp = api.isPostLikedByUser(
            postIdFilter = "eq.$postId",
            userIdFilter = "eq.$current",
            typeFilter = "eq.like"
        )
        val header = resp.headers()["Content-Range"] ?: return@withContext false
        (header.substringAfterLast('/').toIntOrNull() ?: 0) > 0
    }
}