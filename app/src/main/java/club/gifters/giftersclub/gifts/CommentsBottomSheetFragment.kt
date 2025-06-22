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
import club.gifters.giftersclub.gifts.CommentAdapter
import club.gifters.giftersclub.gifts.GifterFragment
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
                    CommentApiHolder.createComment(
                        postId,
                        content,
                        replyingTo?.id
                    )
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
    suspend fun createComment(
        postId: String,
        content: String,
        parentCommentId: String? = null
    ) {
        val body = mutableMapOf(
            "post_id" to postId,
            "content" to content
        )
        parentCommentId?.let { body["parent_comment_id"] = it }
        api.createComment(select = "*", comment = body)
    }
    suspend fun reactToComment(commentId: String, type: String) =
        api.reactToComment(mapOf("comment_id" to commentId, "type" to type))

    suspend fun getCommentReactionCountValue(commentId: String, type: String): Int {
        val resp = api.getCommentReactionCount(
            commentIdFilter = "eq.$commentId",
            type = type
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