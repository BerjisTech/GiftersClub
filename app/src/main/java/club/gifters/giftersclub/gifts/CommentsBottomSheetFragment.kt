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
import kotlinx.coroutines.launch

/**
 * Bottom sheet fragment to display and post comments for a given post.
 */
class CommentsBottomSheetFragment : BottomSheetDialogFragment() {
    private lateinit var rvComments: RecyclerView
    private lateinit var etComment: EditText
    private lateinit var btnSendComment: Button
    private lateinit var adapter: CommentAdapter
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

        var replyingTo: Comment? = null
        adapter = CommentAdapter(
            onReply = { comment ->
                replyingTo = comment
                etComment.setText("@${comment.profile?.username.orEmpty()} ")
                etComment.requestFocus()
            },
            onLike = { comment ->
                lifecycleScope.launch { CommentApiHolder.reactToComment(comment.id, "like") }
            },
            onDislike = { comment ->
                lifecycleScope.launch { CommentApiHolder.reactToComment(comment.id, "dislike") }
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

    private fun loadComments() {
        lifecycleScope.launch {
            rvComments.isVisible = false
            val list = try {
                CommentApiHolder.getCommentsByPost(postId)
            } catch (e: Exception) {
                // avoid crash on malformed GET
                emptyList()
            }
            // fetch like/dislike counts for each comment before sorting
            list.forEach { c ->
                val likes = CommentApiHolder.getCommentReactionCountValue(c.id, "like")
                val dislikes = CommentApiHolder.getCommentReactionCountValue(c.id, "dislike")
                c.reactionCounts = CommentReactionCounts(likes, dislikes)
            }
            val sorted = list.sortedWith(
                compareByDescending<Comment> { it.reactionCounts?.like ?: 0 }
                    .thenByDescending { it.createdAt }
                    .thenBy { it.reactionCounts?.dislike ?: 0 }
            )
            adapter.submitList(sorted)
            rvComments.isVisible = true
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
    suspend fun reactToComment(commentId: String, type: String) =
        api.reactToComment(mapOf("comment_id" to commentId, "type" to type))

    suspend fun getCommentReactionCountValue(commentId: String, type: String): Int {
        val resp = api.getCommentReactionCount(
            commentIdFilter = "eq.$commentId",
            type = "eq.$type"
        )
        val contentRange = resp.headers()["Content-Range"] ?: return 0
        return contentRange.substringAfterLast('/')?.toIntOrNull() ?: 0
    }

    suspend fun getCommentReplyCountValue(commentId: String): Int {
        val resp = api.getCommentReplyCount(parentIdFilter = "eq.$commentId")
        val contentRange = resp.headers()["Content-Range"] ?: return 0
        return contentRange.substringAfterLast('/')?.toIntOrNull() ?: 0
    }
}