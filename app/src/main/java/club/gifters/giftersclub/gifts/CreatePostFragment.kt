package club.gifters.giftersclub.gifts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ImageView
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.SeekBar
import jp.co.cyberagent.android.gpuimage.GPUImageView
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageGrayscaleFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSepiaToneFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageColorInvertFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageContrastFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageBrightnessFilter
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import club.gifters.giftersclub.gifts.FilterAdapter
import club.gifters.giftersclub.gifts.FilterItem
import java.util.regex.Pattern
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Button
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.VideoCapture
import androidx.camera.core.Camera
import java.io.File
import androidx.camera.view.PreviewView
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat

/**
 * Fragment for creating a new post in two steps: select media, then add details.
 */
class CreatePostFragment : Fragment(R.layout.fragment_create_post) {
    private val postApi = RetrofitClient.postApi
    private val storageApi = RetrofitClient.storageApi
    // Media selection preview and next step removed; using camera UI by default
    private lateinit var layoutMedia: ConstraintLayout
    private lateinit var layoutEdit: ConstraintLayout
    private lateinit var layoutDetails: LinearLayout
    private lateinit var rvFilters: RecyclerView
    private lateinit var sbFilterLevel: SeekBar
    private lateinit var gpuImageView: jp.co.cyberagent.android.gpuimage.GPUImageView
    private lateinit var etContent: EditText
    private lateinit var btnEditMedia: Button
    private lateinit var btnApplyFilter: Button
    private lateinit var btnPost: Button
    private lateinit var progressBar: ProgressBar
    private var originalBitmap: Bitmap? = null
    private var editedBitmap: Bitmap? = null
    private val selectedUris = mutableListOf<Uri>()
    private var isVideoSelected = false
    private val REQUEST_PICK_MEDIA = 1001
    private val REQUEST_CAMERA_PERM = 2001

    // CameraX variables
    private lateinit var previewView: PreviewView
    private lateinit var btnSwitchCamera: ImageView
    private lateinit var btnToggleFlash: ImageView
    private lateinit var btnSetTimer: ImageView
    private lateinit var btnShowFilters: ImageView
    private lateinit var btnTimer10m: TextView
    private lateinit var btnTimer60s: TextView
    private lateinit var btnTimer15s: TextView
    private lateinit var btnModeToggle: ImageView
    private lateinit var btnTextMode: ImageView
    private lateinit var btnCapture: ImageView
    private lateinit var btnSelectDevice: ImageView
    private lateinit var layoutFilterOptions: LinearLayout

    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture? = null
    private var recordLimitMs: Long? = null
    private var torchEnabled = false
    private var isVideoMode = false
    private var isRecording = false
    private var camera: Camera? = null
    private val recordingHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var recordingRunnable: Runnable? = null

    companion object {
        private const val TAG = "CreatePostFragment"
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            videoCapture = VideoCapture.Builder().build()
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture,
                    videoCapture
                )
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        layoutMedia = view.findViewById(R.id.layoutMedia)
        layoutEdit = view.findViewById(R.id.layoutEdit)
        layoutDetails = view.findViewById(R.id.layoutDetails)
        etContent = view.findViewById(R.id.etContent)
        btnEditMedia = view.findViewById(R.id.btnEditMedia)
        btnApplyFilter = view.findViewById(R.id.btnApplyFilter)
        btnPost = view.findViewById(R.id.btnPost)
        progressBar = view.findViewById(R.id.progressBar)

        btnEditMedia.setOnClickListener {
            layoutDetails.isVisible = false
            layoutMedia.isVisible = true
        }
        btnPost.setOnClickListener {
            submitPost()
        }
        // Update toolbar title
        requireActivity().title = getString(R.string.create_post)

        // CameraX UI setup and start camera preview
        previewView = view.findViewById(R.id.previewView)
        btnSwitchCamera = view.findViewById(R.id.btnSwitchCamera)
        btnToggleFlash = view.findViewById(R.id.btnToggleFlash)
        btnSetTimer = view.findViewById(R.id.btnSetTimer)
        btnShowFilters = view.findViewById(R.id.btnShowFilters)
        btnTimer10m = view.findViewById(R.id.btnTimer10m)
        btnTimer60s = view.findViewById(R.id.btnTimer60s)
        btnTimer15s = view.findViewById(R.id.btnTimer15s)
        btnModeToggle = view.findViewById(R.id.btnModeToggle)
        btnTextMode = view.findViewById(R.id.btnTextMode)
        btnCapture = view.findViewById(R.id.btnCapture)
        btnSelectDevice = view.findViewById(R.id.btnSelectDevice)
        layoutFilterOptions = view.findViewById(R.id.layoutFilterOptions)

        btnSelectDevice.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            startActivityForResult(intent, REQUEST_PICK_MEDIA)
        }

        // Request camera and audio permissions before starting preview
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                REQUEST_CAMERA_PERM
            )
        }

        // Camera control buttons
        btnSwitchCamera.setOnClickListener {
            cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
                CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            startCamera()
        }
        btnToggleFlash.setOnClickListener {
            torchEnabled = !torchEnabled
            camera?.cameraControl?.enableTorch(torchEnabled)
        }
        btnShowFilters.setOnClickListener {
            layoutFilterOptions.isVisible = !layoutFilterOptions.isVisible
        }

        // Mode toggle and capture
        btnModeToggle.setOnClickListener {
            isVideoMode = !isVideoMode
        }
        btnTextMode.setOnClickListener {
            layoutMedia.isVisible = false
            layoutDetails.isVisible = true
        }
        btnCapture.setOnClickListener {
            if (isVideoMode) startRecording() else takePhoto()
        }

        // Preset timers
        btnTimer10m.setOnClickListener {
            recordLimitMs = 10 * 60 * 1000L
            startRecording()
        }
        btnTimer60s.setOnClickListener {
            recordLimitMs = 60 * 1000L
            startRecording()
        }
        btnTimer15s.setOnClickListener {
            recordLimitMs = 15 * 1000L
            startRecording()
        }

        // Filter buttons for preview (stub for adding filter options)
        // TODO: Populate layoutFilterOptions with filter thumbnails for live preview

        // Filter buttons
        // Initialize GPUImageView for filter preview
        gpuImageView = view.findViewById(R.id.imageEditView)

        // Setup filter selector
        rvFilters = view.findViewById(R.id.rvFilters)
        rvFilters.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        val filters = listOf(
            FilterItem("Normal", GPUImageFilter(), false),
            FilterItem("Gray", GPUImageGrayscaleFilter()),
            FilterItem("Sepia", GPUImageSepiaToneFilter()),
            FilterItem("Invert", GPUImageColorInvertFilter()),
            FilterItem("Contrast+", GPUImageContrastFilter(2.0f), true),
            FilterItem("Bright+", GPUImageBrightnessFilter(0.5f), true)
        )
        val filterAdapter = FilterAdapter { item ->
            gpuImageView.filter = item.filter
            // reset slider
            sbFilterLevel.progress = if (item.adjustable) 50 else 0
        }
        filterAdapter.submitList(filters)
        rvFilters.adapter = filterAdapter
        // default to Normal filter
        gpuImageView.filter = filters.first().filter

        // SeekBar for adjustable filters
        sbFilterLevel = view.findViewById(R.id.sbFilterLevel)
        sbFilterLevel.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val fi = filters[filterAdapter.currentList.indexOfFirst { gpuImageView.filter == it.filter }]
                if (fi.adjustable) {
                    when (val f = gpuImageView.filter) {
                        is GPUImageContrastFilter -> f.setContrast(1f + (progress - 50) / 50f)
                        is GPUImageBrightnessFilter -> f.setBrightness((progress - 50) / 50f)
                    }
                    gpuImageView.requestRender()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        btnApplyFilter.setOnClickListener {
            // Capture the filtered image and proceed to details
            editedBitmap = try {
                gpuImageView.capture()
            } catch (e: InterruptedException) {
                Log.e(TAG, "Error capturing filtered image", e)
                originalBitmap
            }
            layoutEdit.isVisible = false
            layoutDetails.isVisible = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERM) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startCamera()
            } else {
                Toast.makeText(requireContext(), "Camera and audio permissions are required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun takePhoto() {
        val photoFile = java.io.File(requireContext().cacheDir, "IMG_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed", exc)
                    Toast.makeText(requireContext(), "Photo capture failed", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    handleSelectedMedia(listOf(savedUri))
                }
            }
        )
    }

    private fun startRecording() {
        if (isRecording) return
        val videoFile = java.io.File(requireContext().cacheDir, "VID_${System.currentTimeMillis()}.mp4")
        val outputOptions = VideoCapture.OutputFileOptions.Builder(videoFile).build()
        videoCapture?.startRecording(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : VideoCapture.OnVideoSavedCallback {
                override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                    Log.e(TAG, "Video capture failed: $message", cause)
                    Toast.makeText(requireContext(), "Video capture failed", Toast.LENGTH_SHORT).show()
                }

                override fun onVideoSaved(output: VideoCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(videoFile)
                    handleSelectedMedia(listOf(savedUri))
                }
            }
        )
        isRecording = true
        recordLimitMs?.let { limit ->
            recordingRunnable?.let { recordingHandler.removeCallbacks(it) }
            recordingRunnable = Runnable { stopRecording() }
            recordingHandler.postDelayed(recordingRunnable!!, limit)
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        videoCapture?.stopRecording()
        isRecording = false
        recordingRunnable?.let { recordingHandler.removeCallbacks(it) }
        recordingRunnable = null
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
        // Proceed to next step after selection
        layoutMedia.isVisible = false
        if (isVideoSelected || selectedUris.isEmpty()) {
            layoutDetails.isVisible = true
        } else {
            layoutEdit.isVisible = true
            loadImageForEditing()
        }
    }

    private fun loadImageForEditing() {
        val uri = selectedUris.firstOrNull() ?: return
        requireContext().contentResolver.openInputStream(uri)?.use { stream ->
            originalBitmap = BitmapFactory.decodeStream(stream)
            editedBitmap = originalBitmap
            gpuImageView.setImage(editedBitmap)
        }
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