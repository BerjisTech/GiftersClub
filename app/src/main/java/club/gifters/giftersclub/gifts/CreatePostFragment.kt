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
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import android.graphics.Typeface
import android.widget.FrameLayout
import com.google.android.material.tabs.TabLayout
import android.view.inputmethod.InputMethodManager
import yuku.ambilwarna.AmbilWarnaDialog
import androidx.appcompat.app.AlertDialog
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
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import club.gifters.giftersclub.gifts.FilterAdapter
import club.gifters.giftersclub.gifts.FilterItem
import java.io.File
import java.io.FileOutputStream
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.VideoCapture
import androidx.camera.core.Camera
import androidx.camera.view.PreviewView
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import java.util.regex.Pattern

/**
 * Fragment for creating a new post in two steps: select media, then add details.
 */
class CreatePostFragment : Fragment(R.layout.fragment_create_post) {
    private val postApi = RetrofitClient.postApi
    private val storageApi = RetrofitClient.storageApi

    // Media selection preview and next step removed; using camera UI by default
    private lateinit var layoutMedia: ConstraintLayout
    private lateinit var layoutEdit: ConstraintLayout
    private lateinit var layoutDetails: ConstraintLayout
    private lateinit var rvFilters: RecyclerView
    private lateinit var sbFilterLevel: SeekBar
    private lateinit var gpuImageView: jp.co.cyberagent.android.gpuimage.GPUImageView
    private lateinit var baseFilter: GPUImageFilter
    private lateinit var contrastFilter: GPUImageContrastFilter
    private lateinit var brightnessFilter: GPUImageBrightnessFilter
    private lateinit var selectedFilterItem: FilterItem
    private var initialCameraFilter: GPUImageFilter? = null
    private val sliderPositions = mutableMapOf<String, Int>()
    private val enabledAdjustable = mutableSetOf<String>()
    private var customTimerSec = 15
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
    private lateinit var hsvFilters: HorizontalScrollView
    private lateinit var pbRecordProgress: ProgressBar
    private lateinit var tvElapsedTime: TextView
    private var recordStartTimeMs: Long = 0L
    private var elapsedHandler: Handler? = null
    private var elapsedRunnable: Runnable? = null

    // Text post editor components
    private lateinit var layoutTextEditor: ConstraintLayout
    private lateinit var flTextCanvas: FrameLayout
    private lateinit var etTextPost: EditText
    private lateinit var btnCancelTextPost: Button
    private lateinit var btnDoneTextPost: Button
    private lateinit var hsvTextStyles: HorizontalScrollView
    private lateinit var llTextStyles: LinearLayout
    private lateinit var hsvColorPickers: HorizontalScrollView
    private lateinit var llColorPickers: LinearLayout
    private lateinit var hsvBgImages: HorizontalScrollView
    private lateinit var llBgImages: LinearLayout
    private lateinit var hsvFonts: HorizontalScrollView
    private lateinit var llFonts: LinearLayout
    private lateinit var tabTextTools: TabLayout

    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture? = null
    private var recordLimitMs: Long? = null
    private var torchEnabled = false
    private var isVideoMode = false
    private var isRecording = false
    private var camera: Camera? = null
    private var recordTimer: CountDownTimer? = null

    companion object {
        private const val TAG = "CreatePostFragment"
    }

    /**
     * Show exactly one of the four editor steps.
     */
    private fun showStep(step: View) {
        layoutMedia.isVisible = step === layoutMedia
        layoutEdit.isVisible = step === layoutEdit
        layoutDetails.isVisible = step === layoutDetails
        layoutTextEditor.isVisible = step === layoutTextEditor
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
            showStep(layoutMedia)
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
        // Text post editor view bindings
        layoutTextEditor = view.findViewById(R.id.layoutTextEditor)
        flTextCanvas = view.findViewById(R.id.flTextCanvas)
        etTextPost = view.findViewById(R.id.etTextPost)
        btnCancelTextPost = view.findViewById(R.id.btnCancelTextPost)
        btnDoneTextPost = view.findViewById(R.id.btnDoneTextPost)
        hsvTextStyles = view.findViewById(R.id.hsvTextStyles)
        llTextStyles = view.findViewById(R.id.llTextStyles)
        hsvColorPickers = view.findViewById(R.id.hsvColorPickers)
        llColorPickers = view.findViewById(R.id.llColorPickers)
        hsvBgImages = view.findViewById(R.id.hsvBgImages)
        llBgImages = view.findViewById(R.id.llBgImages)
        hsvFonts = view.findViewById(R.id.hsvFonts)
        llFonts = view.findViewById(R.id.llFonts)
        tabTextTools = view.findViewById(R.id.tabTextTools)

        // Populate text post editor controls
        listOf("B", "I", "U").forEach { style ->
            val toggle = ToggleButton(requireContext()).apply {
                text = style; textOn = style; textOff = style
                setTextAppearance(android.R.style.TextAppearance_Material_Headline)
                setOnCheckedChangeListener { _, isChecked ->
                    val paintFlags = etTextPost.paintFlags
                    when (style) {
                        "U" -> etTextPost.paintFlags =
                            if (isChecked) paintFlags or Paint.UNDERLINE_TEXT_FLAG else paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()

                        else -> {
                            var tf = etTextPost.typeface?.style ?: Typeface.NORMAL
                            tf = when (style) {
                                "B" -> if (isChecked) tf or Typeface.BOLD else tf and Typeface.BOLD.inv()
                                "I" -> if (isChecked) tf or Typeface.ITALIC else tf and Typeface.ITALIC.inv()
                                else -> tf
                            }
                            etTextPost.setTypeface(null, tf)
                        }
                    }
                }
            }
            llTextStyles.addView(toggle)
        }
        // Text & background color selectors: launch a full color picker
        val btnTextColorPicker = Button(requireContext()).apply {
            text = getString(R.string.text_color)
            setOnClickListener {
                AmbilWarnaDialog(
                    requireContext(),
                    Color.BLACK,
                    true,
                    object : AmbilWarnaDialog.OnAmbilWarnaListener {
                        override fun onOk(dialog: AmbilWarnaDialog, color: Int) {
                            etTextPost.setTextColor(color)
                        }

                        override fun onCancel(dialog: AmbilWarnaDialog) {}
                    }).show()
            }
        }
        val btnBgColorPicker = Button(requireContext()).apply {
            text = getString(R.string.background_color)
            setOnClickListener {
                AmbilWarnaDialog(
                    requireContext(),
                    Color.WHITE,
                    true,
                    object : AmbilWarnaDialog.OnAmbilWarnaListener {
                        override fun onOk(dialog: AmbilWarnaDialog, color: Int) {
                            flTextCanvas.setBackgroundColor(color)
                        }

                        override fun onCancel(dialog: AmbilWarnaDialog) {}
                    }).show()
            }
        }
        llColorPickers.addView(btnTextColorPicker)
        llColorPickers.addView(btnBgColorPicker)
        listOf(
            R.drawable.black_hole, R.drawable.galaxy, R.drawable.nebula, R.drawable.solar_system,
            R.drawable.universe, R.drawable.supernova, R.drawable.castle, R.drawable.dragon,
            R.drawable.phoenix, R.drawable.mermaid, R.drawable.treasure_chest, R.drawable.unicorn,
            R.drawable.infinity, R.drawable.time_machine
        ).forEach { resId ->
            val iv = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(120, 120).apply { setMargins(8, 8, 8, 8) }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(resId)
                setOnClickListener { flTextCanvas.setBackgroundResource(resId) }
            }
            llBgImages.addView(iv)
        }
        listOf("Sans", "Serif", "Mono").forEach { name ->
            val txt = TextView(requireContext()).apply {
                text = name
                setPadding(16, 8, 16, 8)
                setOnClickListener {
                    etTextPost.typeface = when (name) {
                        "Serif" -> Typeface.SERIF
                        "Mono" -> Typeface.MONOSPACE
                        else -> Typeface.SANS_SERIF
                    }
                }
            }
            llFonts.addView(txt)
        }
        // Tab switcher for styling controls
        tabTextTools = view.findViewById(R.id.tabTextTools)
        // Tab‐driven switch between text styling controls
        val groups = listOf<View>(
            hsvTextStyles, hsvColorPickers, hsvBgImages, hsvFonts
        )
        val labels = listOf(
            "Style",
            getString(R.string.colors),
            getString(R.string.bg_image),
            getString(R.string.font)
        )
        labels.forEach { tabTextTools.addTab(tabTextTools.newTab().setText(it)) }
        fun showGroup(idx: Int) {
            groups.forEachIndexed { i, g -> g.isVisible = i == idx }
        }
        showGroup(0)
        tabTextTools.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = showGroup(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        // Text editor cancel and done actions
        btnCancelTextPost.setOnClickListener {
            showStep(layoutMedia)
        }
        btnDoneTextPost.setOnClickListener {
            // Hide keyboard
            (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(etTextPost.windowToken, 0)
            // Render editor view to bitmap
            val bmp = Bitmap.createBitmap(
                flTextCanvas.width,
                flTextCanvas.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bmp)
            flTextCanvas.draw(canvas)
            val file = File(requireContext().cacheDir, "TXT_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            handleSelectedMedia(listOf(Uri.fromFile(file)))
        }
        btnCapture = view.findViewById(R.id.btnCapture)
        btnSelectDevice = view.findViewById(R.id.btnSelectDevice)
        layoutFilterOptions = view.findViewById(R.id.layoutFilterOptions)
        hsvFilters = view.findViewById(R.id.hsvFilters)
        pbRecordProgress = view.findViewById(R.id.pbRecordProgress)
        tvElapsedTime = view.findViewById(R.id.tvElapsedTime)

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
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
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
            hsvFilters.isVisible = !hsvFilters.isVisible
        }

        // Populate camera filter options
        listOf("Normal", "Gray", "Sepia", "Invert").forEach { name ->
            val tv = TextView(requireContext()).apply {
                text = name
                setPadding(16, 8, 16, 8)
                alpha = if (name == "Normal") 1f else 0.5f
                setOnClickListener {
                    initialCameraFilter = when (name) {
                        "Gray" -> GPUImageGrayscaleFilter()
                        "Sepia" -> GPUImageSepiaToneFilter()
                        "Invert" -> GPUImageColorInvertFilter()
                        else -> GPUImageFilter()
                    }
                    for (i in 0 until layoutFilterOptions.childCount) {
                        val child = layoutFilterOptions.getChildAt(i)
                        child.alpha = if (child == it) 1f else 0.5f
                    }
                }
            }
            layoutFilterOptions.addView(tv)
        }

        // Mode toggle and capture
        btnModeToggle.setOnClickListener {
            isVideoMode = !isVideoMode
        }
        btnTextMode.setOnClickListener {
            showStep(layoutTextEditor)
        }
        btnCapture.setOnClickListener {
            if (!isVideoMode) {
                takePhoto()
            } else {
                if (!isRecording) {
                    startRecording()
                    recordStartTimeMs = System.currentTimeMillis()
                    tvElapsedTime.isVisible = true
                    elapsedHandler = Handler(Looper.getMainLooper())
                    elapsedRunnable = object : Runnable {
                        override fun run() {
                            val secs = ((System.currentTimeMillis() - recordStartTimeMs) / 1000).toInt()
                            tvElapsedTime.text = String.format("%02d:%02d", secs / 60, secs % 60)
                            elapsedHandler?.postDelayed(this, 1000)
                        }
                    }.also { it.run() }
                } else {
                    stopRecording()
                    tvElapsedTime.isVisible = false
                    elapsedHandler?.removeCallbacks(elapsedRunnable!!)
                }
            }
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
        btnSetTimer.setOnClickListener {
            showTimerDialog()
        }

        // Initialize GPUImageView for filter preview and multi-filter setup
        gpuImageView = view.findViewById(R.id.imageEditView)

        baseFilter = GPUImageFilter()
        contrastFilter = GPUImageContrastFilter(1.0f)
        brightnessFilter = GPUImageBrightnessFilter(0.0f)
        selectedFilterItem = FilterItem("Normal", baseFilter, false)
        // initialize default slider positions for adjustable filters
        sliderPositions["Contrast+"] = 50
        sliderPositions["Bright+"]   = 50

        fun applyFilters() {
            val group = GPUImageFilterGroup().apply {
                addFilter(baseFilter)
                // apply all enabled adjustable filters
                if ("Contrast+" in enabledAdjustable) addFilter(contrastFilter)
                if ("Bright+"   in enabledAdjustable) addFilter(brightnessFilter)
            }
            gpuImageView.filter = group
            gpuImageView.requestRender()
        }

        rvFilters = view.findViewById(R.id.rvFilters)
        rvFilters.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        val filters = listOf(
            FilterItem("Normal", baseFilter, false),
            FilterItem("Gray", GPUImageGrayscaleFilter()),
            FilterItem("Sepia", GPUImageSepiaToneFilter()),
            FilterItem("Invert", GPUImageColorInvertFilter()),
            FilterItem("Contrast+", contrastFilter, true),
            FilterItem("Bright+", brightnessFilter, true)
        )
        val filterAdapter = FilterAdapter { item ->
            selectedFilterItem = item
            if (item.adjustable) {
                // enable contrast/brightness without disabling others
                enabledAdjustable.add(item.name)
            } else {
                // static filter: set base; clear all on Normal
                baseFilter = item.filter
                if (item.name == "Normal") enabledAdjustable.clear()
            }
            applyFilters()
            if (item.adjustable) {
                sbFilterLevel.isVisible = true
                sbFilterLevel.progress = sliderPositions[item.name] ?: 50
            } else {
                sbFilterLevel.isVisible = false
            }
        }
        filterAdapter.submitList(filters)
        rvFilters.adapter = filterAdapter

        applyFilters()

        sbFilterLevel = view.findViewById(R.id.sbFilterLevel)
        sbFilterLevel.isVisible = false
        sbFilterLevel.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (selectedFilterItem.adjustable) {
                    // remember and apply this adjustable filter
                    sliderPositions[selectedFilterItem.name] = progress
                    when (selectedFilterItem.filter) {
                        is GPUImageContrastFilter   -> contrastFilter.setContrast(1f + (progress - 50)/50f)
                        is GPUImageBrightnessFilter -> brightnessFilter.setBrightness((progress - 50)/50f)
                    }
                    applyFilters()
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        btnApplyFilter.setOnClickListener {
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
                Toast.makeText(
                    requireContext(),
                    "Camera and audio permissions are required",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun takePhoto() {
        val photoFile =
            java.io.File(requireContext().cacheDir, "IMG_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed", exc)
                    Toast.makeText(requireContext(), "Photo capture failed", Toast.LENGTH_SHORT)
                        .show()
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
        val videoFile =
            java.io.File(requireContext().cacheDir, "VID_${System.currentTimeMillis()}.mp4")
        val outputOptions = VideoCapture.OutputFileOptions.Builder(videoFile).build()
        videoCapture?.startRecording(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : VideoCapture.OnVideoSavedCallback {
                override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                    Log.e(TAG, "Video capture failed: $message", cause)
                    Toast.makeText(requireContext(), "Video capture failed", Toast.LENGTH_SHORT)
                        .show()
                }

                override fun onVideoSaved(output: VideoCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(videoFile)
                    handleSelectedMedia(listOf(savedUri))
                }
            }
        )
        isRecording = true
        pbRecordProgress.isVisible = true
        recordTimer?.cancel()
        recordLimitMs?.let { limit ->
            recordTimer = object : CountDownTimer(limit, limit / 100) {
                override fun onTick(millisUntilFinished: Long) {
                    val p = ((limit - millisUntilFinished) * 100 / limit).toInt()
                    pbRecordProgress.progress = p
                }

                override fun onFinish() {
                    pbRecordProgress.progress = 100
                    stopRecording()
                }
            }.apply { start() }
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        videoCapture?.stopRecording()
        isRecording = false
        recordTimer?.cancel()
        pbRecordProgress.isVisible = false
    }

    private fun showTimerDialog() {
        val maxSec = 10 * 60
        var chosen = customTimerSec.coerceIn(1, maxSec)
        val tv = TextView(requireContext()).apply {
            text = String.format("%d sec", chosen)
            setPadding(0, 0, 0, 16)
        }
        val sb = SeekBar(requireContext()).apply {
            max = maxSec
            progress = chosen
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    chosen = p.coerceAtLeast(1)
                    tv.text = String.format("%d sec", chosen)
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.set_recording_timer)
            .setView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 16, 48, 16)
                addView(tv)
                addView(sb)
            })
            .setPositiveButton(R.string.ok) { _, _ ->
                customTimerSec = chosen
                recordLimitMs = chosen * 1000L
                startRecording()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun handleSelectedMedia(uris: List<Uri>) {
        selectedUris.clear()
        isVideoSelected = uris.any { uri ->
            val mime = requireContext().contentResolver.getType(uri)
            (mime?.startsWith("video/") == true) || (uri.path?.endsWith(".mp4") == true)
        }
        if (isVideoSelected) {
            val videoUri = uris.first { uri ->
                val mime = requireContext().contentResolver.getType(uri)
                (mime?.startsWith("video/") == true) || (uri.path?.endsWith(".mp4") == true)
            }
            selectedUris.add(videoUri)
        } else {
            selectedUris.addAll(uris.take(10))
        }
        // Proceed to next step after selection
        if (isVideoSelected || selectedUris.isEmpty()) {
            showStep(layoutDetails)
        } else {
            showStep(layoutEdit)
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
        initialCameraFilter?.let { baseFilter = it }
        applyFilters()
    }

    private fun applyFilters() {
        val group = GPUImageFilterGroup()
        group.addFilter(baseFilter)
        group.addFilter(contrastFilter)
        group.addFilter(brightnessFilter)
        gpuImageView.filter = group
        gpuImageView.requestRender()
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
                    Log.e(
                        TAG,
                        "Failed to create post: HTTP ${postResp.code()} ${
                            postResp.errorBody()?.string()
                        }"
                    )
                    Toast.makeText(requireContext(), "Failed to create post", Toast.LENGTH_SHORT)
                        .show()
                    return@launch
                }
                val posts = postResp.body().orEmpty()
                if (posts.isEmpty()) {
                    Log.e(TAG, "CreatePost returned empty list")
                    Toast.makeText(requireContext(), "Failed to create post", Toast.LENGTH_SHORT)
                        .show()
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
                        postApi.upsertPostTags(
                            tagsResp.body()!!.map { PostTagUpsertRequest(post.id, it.id) })
                    } else {
                        Log.e(
                            TAG,
                            "Failed to upsert tags: HTTP ${tagsResp.code()} ${
                                tagsResp.errorBody()?.string()
                            }"
                        )
                    }
                }
                for ((index, uri) in selectedUris.withIndex()) {
                    val rawType = requireContext().contentResolver.getType(uri)
                    val isVideo = rawType?.startsWith("video/") == true || uri.path?.endsWith(".mp4") == true
                    val type = rawType ?: "application/octet-stream"
                    val ext = if (isVideo) "bin" else type.substringAfterLast('/', "bin")
                    val ts = System.currentTimeMillis()
                    val filename = "${post.id}-$ts-$index.$ext"
                    requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                        val bytes = if (index == 0 && editedBitmap != null && !isVideo) {
                            java.io.ByteArrayOutputStream().apply {
                                editedBitmap!!.compress(Bitmap.CompressFormat.JPEG, 90, this)
                            }.toByteArray()
                        } else {
                            stream.readBytes()
                        }
                        val body = bytes.toRequestBody(type.toMediaTypeOrNull())
                        storageApi.uploadPostMedia(filename, body, type)
                    }
                    val publicUrl =
                        "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/${SupabaseConfig.POSTS_BUCKET}/$filename"
                    val mediaResp = postApi.createPostMedia(
                        createMedia = CreatePostMediaRequest(
                            post.id,
                            if (isVideo) "video" else "photo",
                            publicUrl,
                            index
                        )
                    )
                    if (!mediaResp.isSuccessful) {
                        Log.e(
                            TAG,
                            "Failed to save post media: HTTP ${mediaResp.code()} ${
                                mediaResp.errorBody()?.string()
                            }"
                        )
                    }
                }
                Toast.makeText(requireContext(), "Post created successfully", Toast.LENGTH_SHORT)
                    .show()
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