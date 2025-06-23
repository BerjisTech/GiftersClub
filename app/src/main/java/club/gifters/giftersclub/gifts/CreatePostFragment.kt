package club.gifters.giftersclub.gifts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import club.gifters.giftersclub.R
import club.gifters.giftersclub.SupabaseConfig
import club.gifters.giftersclub.model.CreatePostMediaRequest
import club.gifters.giftersclub.model.CreatePostRequest
import club.gifters.giftersclub.model.PostTagUpsertRequest
import club.gifters.giftersclub.model.TagUpsertRequest
import club.gifters.giftersclub.network.RetrofitClient
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Fragment for creating a new post in two steps: select media, then add details.
 */
class CreatePostFragment : Fragment(R.layout.fragment_create_post) {
    private val postApi = RetrofitClient.postApi
    private val storageApi = RetrofitClient.storageApi
    private lateinit var btnSelectMedia: Button
    private lateinit var tvSelectedCount: TextView
    private lateinit var layoutPreviews: LinearLayout
    private lateinit var btnNext: Button
    private lateinit var layoutMedia: ConstraintLayout
    private lateinit var layoutEdit: ConstraintLayout
    private lateinit var layoutDetails: LinearLayout
    private lateinit var imageEditView: ImageView
    private lateinit var btnFilterNone: Button
    private lateinit var btnFilterGray: Button
    private lateinit var btnFilterSepia: Button
    private lateinit var btnApplyFilter: Button
    private lateinit var etContent: EditText
    private lateinit var btnEditMedia: Button
    private lateinit var btnPost: Button
    private lateinit var progressBar: ProgressBar
    private var originalBitmap: Bitmap? = null
    private var editedBitmap: Bitmap? = null
    private val selectedUris = mutableListOf<Uri>()
    private var isVideoSelected = false
    private val REQUEST_PICK_MEDIA = 1001

    companion object {
        private const val TAG = "CreatePostFragment"
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnSelectMedia = view.findViewById(R.id.btnSelectMedia)
        tvSelectedCount = view.findViewById(R.id.tvSelectedCount)
        layoutPreviews = view.findViewById(R.id.layoutPreviews)
        btnNext = view.findViewById(R.id.btnNext)
        layoutMedia = view.findViewById(R.id.layoutMedia)
        layoutEdit = view.findViewById(R.id.layoutEdit)
        layoutDetails = view.findViewById(R.id.layoutDetails)
        imageEditView = view.findViewById(R.id.imageEditView)
        btnFilterNone = view.findViewById(R.id.btnFilterNone)
        btnFilterGray = view.findViewById(R.id.btnFilterGray)
        btnFilterSepia = view.findViewById(R.id.btnFilterSepia)
        btnApplyFilter = view.findViewById(R.id.btnApplyFilter)
        etContent = view.findViewById(R.id.etContent)
        btnEditMedia = view.findViewById(R.id.btnEditMedia)
        btnPost = view.findViewById(R.id.btnPost)
        progressBar = view.findViewById(R.id.progressBar)

    // Remove fragment-level close button; use toolbar back arrow only
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
            if (isVideoSelected || selectedUris.isEmpty()) {
                layoutDetails.isVisible = true
            } else {
                // proceed to edit first image
                layoutEdit.isVisible = true
                loadImageForEditing()
            }
        }
        btnEditMedia.setOnClickListener {
            layoutDetails.isVisible = false
            layoutMedia.isVisible = true
        }
        btnPost.setOnClickListener {
            submitPost()
        }
        // Update toolbar title
        requireActivity().title = getString(R.string.create_post)

        // Filter buttons
        btnFilterNone.setOnClickListener { applyFilterNone() }
        btnFilterGray.setOnClickListener { applyFilterGray() }
        btnFilterSepia.setOnClickListener { applyFilterSepia() }
        btnApplyFilter.setOnClickListener {
            // use editedBitmap for upload
            layoutEdit.isVisible = false
            layoutDetails.isVisible = true
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

    private fun loadImageForEditing() {
        val uri = selectedUris.firstOrNull() ?: return
        requireContext().contentResolver.openInputStream(uri)?.use { stream ->
            originalBitmap = BitmapFactory.decodeStream(stream)
            editedBitmap = originalBitmap
            imageEditView.setImageBitmap(editedBitmap)
        }
    }

    private fun applyFilterNone() {
        editedBitmap = originalBitmap
        imageEditView.setImageBitmap(editedBitmap)
    }

    private fun applyFilterGray() {
        originalBitmap?.let { bmp ->
            val cm = ColorMatrix().apply { setSaturation(0f) }
            val config = bmp.config ?: Bitmap.Config.ARGB_8888
            val filtered = Bitmap.createBitmap(bmp.width, bmp.height, config)
            val canvas = android.graphics.Canvas(filtered)
            val paint = android.graphics.Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
            canvas.drawBitmap(bmp, 0f, 0f, paint)
            editedBitmap = filtered
            imageEditView.setImageBitmap(filtered)
        }
    }

    private fun applyFilterSepia() {
        originalBitmap?.let { bmp ->
            val cm = ColorMatrix().apply {
                setScale(1f, .95f, .82f, 1f)
            }
            val config = bmp.config ?: Bitmap.Config.ARGB_8888
            val filtered = Bitmap.createBitmap(bmp.width, bmp.height, config)
            val canvas = android.graphics.Canvas(filtered)
            val paint = android.graphics.Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
            canvas.drawBitmap(bmp, 0f, 0f, paint)
            editedBitmap = filtered
            imageEditView.setImageBitmap(filtered)
        }
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
                        val bytes = if (index == 0 && editedBitmap != null) {
                            java.io.ByteArrayOutputStream().apply {
                                editedBitmap!!.compress(Bitmap.CompressFormat.JPEG, 90, this)
                            }.toByteArray()
                        } else {
                            stream.readBytes()
                        }
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