package club.gifters.giftersclub.gifts

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.VideoView
import android.widget.Toast
import android.content.Context
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import club.gifters.giftersclub.MainActivity
import club.gifters.giftersclub.R
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.SupabaseConfig
import club.gifters.giftersclub.model.CreatePostMediaRequest
import club.gifters.giftersclub.model.CreatePostRequest
import club.gifters.giftersclub.model.PostTagUpsertRequest
import club.gifters.giftersclub.model.TagUpsertRequest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.regex.Pattern
import kotlinx.coroutines.launch
import retrofit2.Response

/**
 * Fragment for creating a new post in two steps: select media, then add details.
 */
class CreatePostFragment : Fragment(R.layout.fragment_create_post) {
    private val postApi = RetrofitClient.postApi
    private val storageApi = RetrofitClient.storageApi
    private lateinit var btnClose: ImageButton
    private lateinit var btnSelectMedia: Button
    private lateinit var tvSelectedCount: TextView
    private lateinit var layoutPreviews: LinearLayout
    private lateinit var btnNext: Button
    private lateinit var layoutMedia: LinearLayout
    private lateinit var layoutDetails: LinearLayout
    private lateinit var etContent: EditText
    private lateinit var btnEditMedia: Button
    private lateinit var btnPost: Button
    private lateinit var progressBar: ProgressBar
    private val selectedUris = mutableListOf<Uri>()
    private var isVideoSelected = false
    private val REQUEST_PICK_MEDIA = 1001

    companion object {
        private const val TAG = "CreatePostFragment"
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnClose = view.findViewById(R.id.btnClose)
        btnSelectMedia = view.findViewById(R.id.btnSelectMedia)
        tvSelectedCount = view.findViewById(R.id.tvSelectedCount)
        layoutPreviews = view.findViewById(R.id.layoutPreviews)
        btnNext = view.findViewById(R.id.btnNext)
        layoutMedia = view.findViewById(R.id.layoutMedia)
        layoutDetails = view.findViewById(R.id.layoutDetails)
        etContent = view.findViewById(R.id.etContent)
        btnEditMedia = view.findViewById(R.id.btnEditMedia)
        btnPost = view.findViewById(R.id.btnPost)
        progressBar = view.findViewById(R.id.progressBar)

        btnClose.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        btnSelectMedia.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            startActivityForResult(intent, REQUEST_PICK_MEDIA)
        }
        btnNext.setOnClickListener {
            layoutMedia.isVisible = false
            layoutDetails.isVisible = true
        }
        btnEditMedia.setOnClickListener {
            layoutDetails.isVisible = false
            layoutMedia.isVisible = true
        }
        btnPost.setOnClickListener {
            submitPost()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    private fun handleSelectedMedia(uris: List<Uri>) {
        selectedUris.clear()
        isVideoSelected = uris.any { uri ->
            val type = requireContext().contentResolver.getType(uri)
            type?.startsWith("video/") == true
        }
        if (isVideoSelected) {
            val videoUri = uris.first { uri ->
                val type = requireContext().contentResolver.getType(uri)
                type?.startsWith("video/") == true
            }
            selectedUris.add(videoUri)
        } else {
            selectedUris.addAll(uris.take(10))
        }
        updatePreviews()
        btnNext.isEnabled = selectedUris.isNotEmpty()
    }

    private fun updatePreviews() {
        layoutPreviews.removeAllViews()
        for (uri in selectedUris) {
            val view = if (isVideoSelected) {
                VideoView(requireContext()).apply {
                    setVideoURI(uri)
                    setOnPreparedListener { mp -> mp.isLooping = true; pause() }
                    layoutParams = LinearLayout.LayoutParams(300, 300).apply { setMargins(8, 0, 8, 0) }
                }
            } else {
                ImageView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(300, 300).apply { setMargins(8, 0, 8, 0) }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageURI(uri)
                }
            }
            layoutPreviews.addView(view)
        }
        tvSelectedCount.text = "${selectedUris.size} file(s) selected"
    }

    private fun submitPost() {
        val content = etContent.text.toString().trim()
        if (selectedUris.isEmpty()) return
        val prefs = requireContext().getSharedPreferences("supabase", Context.MODE_PRIVATE)
        val accessToken = prefs.getString("access_token", "") ?: ""
        var userId = ""
        if (accessToken.isNotBlank()) {
            try {
                val parts = accessToken.split(".")
                if (parts.size >= 2) {
                    val decoded = String(Base64.decode(parts[1], Base64.URL_SAFE), Charsets.UTF_8)
                    userId = JSONObject(decoded).optString("sub")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding access token", e)
            }
        }
        lifecycleScope.launch {
            progressBar.isVisible = true
            try {
                val postResp = postApi.createPost(
                    createPost = CreatePostRequest(
                        userId = userId,
                        content = content
                    )
                )
                if (!postResp.isSuccessful) {
                    Log.e(TAG, "Failed to create post: HTTP ${postResp.code()} ${postResp.errorBody()?.string()}")
                    Toast.makeText(requireContext(), "Failed to create post", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val posts = postResp.body().orEmpty()
                if (posts.isEmpty()) {
                    Log.e(TAG, "CreatePost returned empty list")
                    Toast.makeText(requireContext(), "Failed to create post", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val post = posts[0]
                val tagMatches = Pattern.compile("#(\\w+)")
                    .matcher(content)
                    .run {
                        mutableListOf<String>().apply {
                            while (find()) add(group(1).lowercase())
                        }.distinct()
                    }
                if (tagMatches.isNotEmpty()) {
                    val tagsResp = postApi.upsertTags(tagMatches.map { TagUpsertRequest(it) })
                    if (tagsResp.isSuccessful) {
                        postApi.upsertPostTags(tagsResp.body()!!.map { PostTagUpsertRequest(post.id, it.id) })
                    } else {
                        Log.e(TAG, "Failed to upsert tags: HTTP ${tagsResp.code()} ${tagsResp.errorBody()?.string()}")
                    }
                }
                for ((index, uri) in selectedUris.withIndex()) {
                    val type = requireContext().contentResolver.getType(uri) ?: "application/octet-stream"
                    val ext = type.substringAfterLast('/', "bin")
                    val ts = System.currentTimeMillis()
                    val filename = "${post.id}-$ts-$index.$ext"
                    requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                        val bytes = stream.readBytes()
                        val body = bytes.toRequestBody(type.toMediaTypeOrNull())
                        storageApi.uploadPostMedia(filename, body, type)
                    }
                    val publicUrl = "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/${SupabaseConfig.POSTS_BUCKET}/$filename"
                    val mediaResp = postApi.createPostMedia(
                        createMedia = CreatePostMediaRequest(
                            post.id,
                            if (type.startsWith("video/")) "video" else "photo",
                            publicUrl,
                            index
                        )
                    )
                    if (!mediaResp.isSuccessful) {
                        Log.e(TAG, "Failed to save post media: HTTP ${mediaResp.code()} ${mediaResp.errorBody()?.string()}")
                    }
                }
                Toast.makeText(requireContext(), "Post created successfully", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create post", e)
                Toast.makeText(requireContext(), "Failed to create post", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.isVisible = false
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_MEDIA && resultCode == Activity.RESULT_OK) {
            val uris = mutableListOf<Uri>()
            data?.data?.let { uris.add(it) }
            data?.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i).uri?.let { uris.add(it) }
                }
            }
            handleSelectedMedia(uris)
        }
    }
}