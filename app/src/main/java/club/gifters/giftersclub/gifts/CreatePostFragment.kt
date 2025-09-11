package club.gifters.giftersclub.gifts

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.TypedValue
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import coil.load
import coil.imageLoader
import coil.request.ImageRequest
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.VideoCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.provider.FontRequest
import androidx.core.provider.FontsContractCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.CreatePostRequest
import club.gifters.giftersclub.model.PostTagUpsertRequest
import club.gifters.giftersclub.model.TagUpsertRequest
import club.gifters.giftersclub.network.RetrofitClient
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.tabs.TabLayout
import com.yalantis.ucrop.UCrop
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.filter.GPUImageBrightnessFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageColorInvertFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageContrastFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import jp.co.cyberagent.android.gpuimage.filter.GPUImageGrayscaleFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSepiaToneFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import yuku.ambilwarna.AmbilWarnaDialog
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.lang.reflect.Field
import java.util.regex.Pattern

/**
 * Fragment for creating a new post in two steps: select media, then add details.
 */
class CreatePostFragment : Fragment(R.layout.fragment_create_post) {
    private lateinit var rgAccessType: android.widget.RadioGroup
    private lateinit var etPrice: EditText
    private lateinit var layoutPostPlanPicker: LinearLayout
    private lateinit var actvPostPlan: android.widget.AutoCompleteTextView
    private var postPlanIdByName: Map<String, String> = emptyMap()
    private val postApi = RetrofitClient.postApi

    // Media selection preview and next step removed; using camera UI by default
    private lateinit var flStepContainer: FrameLayout
    private lateinit var layoutMedia: ConstraintLayout
    private lateinit var layoutEdit: ConstraintLayout
    private lateinit var layoutDetails: ConstraintLayout
    private lateinit var rvFilters: RecyclerView
    private lateinit var sbFilterLevel: SeekBar
    private lateinit var gpuImageView: SafeGPUImageView
    private lateinit var baseFilter: GPUImageFilter
    private lateinit var contrastFilter: GPUImageContrastFilter
    private lateinit var brightnessFilter: GPUImageBrightnessFilter
    private lateinit var selectedFilterItem: FilterItem
    private var initialCameraFilter: GPUImageFilter? = null
    private val sliderPositions = mutableMapOf<String, Int>()
    private val enabledAdjustable = mutableSetOf<String>()
    private var customTimerSec = 15
    private lateinit var etContent: EditText
    private lateinit var ivPostPreview: ImageView
    private lateinit var btnEditMedia: Button
    private lateinit var btnEditMediaCard: androidx.cardview.widget.CardView
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
    private lateinit var previewView: SafePreviewView
    private lateinit var btnSwitchCamera: ImageView
    private lateinit var btnToggleFlash: ImageView
    private lateinit var btnSetTimer: ImageView
    private lateinit var btnShowFilters: ImageView
    private lateinit var btnTimer10m: TextView
    private lateinit var btnTimer60s: TextView
    private lateinit var btnTimer5s: TextView
    private lateinit var btnTimer15s: TextView
    private lateinit var btnModeToggle: ImageView
    private lateinit var btnTextMode: TextView
    private lateinit var btnUndoSegment: ImageView
    private lateinit var btnCapture: ImageView
    private lateinit var btnSelectDevice: ImageView
    private lateinit var layoutFilterOptions: LinearLayout
    private lateinit var hsvFilters: HorizontalScrollView
    private lateinit var pbRecordProgress: CircularProgressIndicator
    private lateinit var segmentsBar: LinearLayout
    private lateinit var tvElapsedTime: TextView
    private var recordStartTimeMs: Long = 0L
    private var elapsedHandler: Handler? = null
    private var elapsedRunnable: Runnable? = null


    // Crop & scale in image editor
    private lateinit var btnCrop: ImageView
    private lateinit var btnScale: ImageView

    // Pinch-to-zoom
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var currentZoomRatio = 1f

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
    private lateinit var hsvFontSizes: HorizontalScrollView
    private lateinit var llFontSizes: LinearLayout
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
    private var recordingActive = false
    private var isPaused = false
    private var camera: Camera? = null
    private var recordTimer: CountDownTimer? = null
    private var totalRecordedMs: Long = 0L
    private var currentSegmentStartMs: Long = 0L
    private val recordedSegments = mutableListOf<File>()
    private val recordedSegmentDurations = mutableListOf<Long>()
    private var pendingFinalize: Boolean = false
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var isLongPress = false
    // Legacy: used when async-loading overlays; no longer gating proceed
    private var pendingStickerLoads: Int = 0


    companion object {
        private const val TAG = "CreatePostFragment"
    }

    private enum class Step { MEDIA, EDIT, DETAILS, TEXT }

    private val stepStack: MutableList<Step> = mutableListOf()
    private var backCallback: androidx.activity.OnBackPressedCallback? = null

    /** Show exactly one of the steps and record navigation for back handling. */
    private fun showStep(step: View) {
        val next = when (step) {
            layoutMedia -> Step.MEDIA
            layoutEdit -> Step.EDIT
            layoutDetails -> Step.DETAILS
            layoutTextEditor -> Step.TEXT
            else -> Step.MEDIA
        }
        if (stepStack.isEmpty() || stepStack.last() != next) stepStack.add(next)
        // Only toggle visibility; do not reparent views (prevents PreviewView/GPUImage glitches)
        layoutMedia.isVisible = next == Step.MEDIA
        layoutEdit.isVisible = next == Step.EDIT
        layoutDetails.isVisible = next == Step.DETAILS
        layoutTextEditor.isVisible = next == Step.TEXT
        when (next) {
            Step.MEDIA -> startCamera()
            Step.EDIT -> { /* keep current image; avoid resetting to prevent visual artifacts */
            }

            Step.DETAILS -> updatePostPreview()
            else -> {}
        }
    }

    private fun showPreviousStepOrExit() {
        if (stepStack.size > 1) {
            // pop current and show previous without pushing again
            stepStack.removeAt(stepStack.lastIndex)
            when (stepStack.last()) {
                Step.MEDIA -> showStep(layoutMedia)
                Step.EDIT -> showStep(layoutEdit)
                Step.DETAILS -> showStep(layoutDetails)
                Step.TEXT -> showStep(layoutTextEditor)
            }
        } else {
            // let system handle back (pop fragment) without re-entering our callback
            try {
                backCallback?.isEnabled = false
            } catch (_: Exception) {
            }
            requireActivity().onBackPressed()
        }
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
                // Log.e(TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        flStepContainer = view.findViewById(R.id.flStepContainer)
        layoutMedia = view.findViewById(R.id.layoutMedia)
        layoutEdit = view.findViewById(R.id.layoutEdit)
        layoutDetails = view.findViewById(R.id.layoutDetails)
        // Details step views bound from details root
        ivPostPreview = layoutDetails.findViewById(R.id.ivPostPreview)
        etContent = layoutDetails.findViewById(R.id.etContent)
        btnEditMedia = layoutDetails.findViewById(R.id.btnEditMedia)
        btnEditMediaCard = layoutDetails.findViewById(R.id.btnEditMediaCard)
        btnApplyFilter = layoutEdit.findViewById(R.id.btnApplyFilter)
        btnPost = layoutDetails.findViewById(R.id.btnPost)
        progressBar = view.findViewById(R.id.progressBar)

        // defer initial step attach until all step roots are bound

        // Back press should go to previous step, not exit immediately
        backCallback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showPreviousStepOrExit()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback!!)

        btnEditMedia.setOnClickListener { showStep(layoutEdit) }
        btnPost.setOnClickListener {
            submitPost()
        }

        // Show edit button only for photo or text; hide for video mode
        fun updateEditVisibility() {
            val show = !isVideoSelected && !isVideoMode // text, or photo selection without video
            btnEditMediaCard.visibility = if (show) View.VISIBLE else View.GONE
        }
        updateEditVisibility()
        // Access type (free/subscription/paid) and pricing (bind from Details layout root)
        rgAccessType = layoutDetails.findViewById(R.id.rgAccessType)
        etPrice = layoutDetails.findViewById(R.id.etPrice)
        layoutPostPlanPicker = layoutDetails.findViewById(R.id.layoutPostPlanPicker)
        actvPostPlan = layoutDetails.findViewById(R.id.actvPostPlan)
        val layoutPostNoPlans: LinearLayout = layoutDetails.findViewById(R.id.layoutPostNoPlans)
        rgAccessType.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbPaid -> {
                    etPrice.visibility = View.VISIBLE
                    layoutPostPlanPicker.visibility = View.GONE
                    layoutPostNoPlans.visibility = View.GONE
                }

                R.id.rbSubscriberOnly -> {
                    etPrice.visibility = View.GONE
                    val hasPlans = postPlanIdByName.isNotEmpty()
                    layoutPostPlanPicker.visibility = if (hasPlans) View.VISIBLE else View.GONE
                    layoutPostNoPlans.visibility = if (hasPlans) View.GONE else View.VISIBLE
                }

                else -> {
                    etPrice.visibility = View.GONE
                    layoutPostPlanPicker.visibility = View.GONE
                    layoutPostNoPlans.visibility = View.GONE
                }
            }
        }

        // Load subscription plans for dropdown with "All" default
        val ctx = requireContext()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val uid = club.gifters.giftersclub.AuthUtils.getCurrentUserId(ctx) ?: ""
                if (uid.isNotEmpty()) {
                    val plans =
                        RetrofitClient.subscriptionPlanApi.getSubscriptionPlans(
                            "eq.$uid"
                        )
                    if (plans.isNotEmpty()) {
                        val names = listOf("All") + plans.map { it.name }
                        postPlanIdByName = plans.associate { it.name to it.id }
                        actvPostPlan.setAdapter(
                            android.widget.ArrayAdapter(
                                ctx,
                                android.R.layout.simple_dropdown_item_1line,
                                names
                            )
                        )
                        actvPostPlan.threshold = 0
                        actvPostPlan.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) actvPostPlan.showDropDown() }
                        actvPostPlan.setOnClickListener { actvPostPlan.showDropDown() }
                        actvPostPlan.setText("All", false)
                        // Adjust only if currently on subscriber-only
                        if (rgAccessType.checkedRadioButtonId == R.id.rbSubscriberOnly) {
                            layoutPostPlanPicker.visibility = View.VISIBLE
                            view.findViewById<LinearLayout>(R.id.layoutPostNoPlans).visibility =
                                View.GONE
                        }
                    } else {
                        // No plans loaded: only show message if subscriber-only is selected
                        if (rgAccessType.checkedRadioButtonId == R.id.rbSubscriberOnly) {
                            layoutPostPlanPicker.visibility = View.GONE
                            view.findViewById<LinearLayout>(R.id.layoutPostNoPlans).visibility =
                                View.VISIBLE
                        }
                        view.findViewById<Button>(R.id.btnOpenSubscriptionSettingsFromPost)
                            .setOnClickListener {
                                val intent = Intent(
                                    requireContext(),
                                    club.gifters.giftersclub.MainActivity::class.java
                                )
                                intent.putExtra(
                                    club.gifters.giftersclub.MainActivity.EXTRA_OPEN_SETTINGS_TAB,
                                    4
                                )
                                startActivity(intent)
                            }
                    }
                }
            } catch (_: Exception) {
            }
        }

        // CameraX UI setup and start camera preview
        // Media step views bound from media root
        previewView = layoutMedia.findViewById(R.id.previewView)
        btnSwitchCamera = layoutMedia.findViewById(R.id.btnSwitchCamera)
        btnToggleFlash = layoutMedia.findViewById(R.id.btnToggleFlash)
        btnSetTimer = layoutMedia.findViewById(R.id.btnSetTimer)
        btnShowFilters = layoutMedia.findViewById(R.id.btnShowFilters)
        btnTimer10m = layoutMedia.findViewById(R.id.btnTimer10m)
        btnTimer60s = layoutMedia.findViewById(R.id.btnTimer60s)
        btnTimer5s = layoutMedia.findViewById(R.id.btnTimer5s)
        btnTimer15s = layoutMedia.findViewById(R.id.btnTimer15s)
        btnModeToggle = layoutMedia.findViewById(R.id.btnModeToggle)
        // initialize photo/video icon
        btnModeToggle.setImageResource(if (isVideoMode) R.drawable.camera else R.drawable.video)
        // initialize photo/video icon
        btnModeToggle.setImageResource(if (isVideoMode) R.drawable.camera else R.drawable.video)
        btnTextMode = layoutMedia.findViewById(R.id.btnTextMode)
        btnUndoSegment = layoutMedia.findViewById(R.id.btnUndoSegment)
        btnUndoSegment.visibility = View.GONE
        btnUndoSegment.setOnClickListener {
            if (isPaused && recordedSegments.isNotEmpty()) {
                val file = recordedSegments.removeAt(recordedSegments.lastIndex)
                val dur = recordedSegmentDurations.removeAt(recordedSegmentDurations.lastIndex)
                totalRecordedMs = (totalRecordedMs - dur).coerceAtLeast(0L)
                try {
                    file.delete()
                } catch (_: Exception) {
                }
                // update progress bar to reflect removal
                recordLimitMs?.let { limit ->
                    val p = ((totalRecordedMs * 100) / limit).toInt().coerceIn(0, 100)
                    pbRecordProgress.progress = p
                }
                if (recordedSegments.isEmpty()) btnUndoSegment.visibility = View.GONE
            }
        }
        // Text post editor view bindings (bind from the text editor root)
        layoutTextEditor = view.findViewById(R.id.layoutTextEditor)
        flTextCanvas = layoutTextEditor.findViewById(R.id.flTextCanvas)
        etTextPost = layoutTextEditor.findViewById(R.id.etTextPost)
        btnCancelTextPost = layoutTextEditor.findViewById(R.id.btnCancelTextPost)
        btnDoneTextPost = layoutTextEditor.findViewById(R.id.btnDoneTextPost)
        hsvTextStyles = layoutTextEditor.findViewById(R.id.hsvTextStyles)
        llTextStyles = layoutTextEditor.findViewById(R.id.llTextStyles)
        hsvColorPickers = layoutTextEditor.findViewById(R.id.hsvColorPickers)
        llColorPickers = layoutTextEditor.findViewById(R.id.llColorPickers)
        hsvBgImages = layoutTextEditor.findViewById(R.id.hsvBgImages)
        llBgImages = layoutTextEditor.findViewById(R.id.llBgImages)
        hsvFonts = layoutTextEditor.findViewById(R.id.hsvFonts)
        llFonts = layoutTextEditor.findViewById(R.id.llFonts)
        tabTextTools = layoutTextEditor.findViewById(R.id.tabTextTools)

        // Now that all step roots are bound, attach the initial step into the container
        if (stepStack.isEmpty()) stepStack.add(Step.MEDIA)
        showStep(layoutMedia)

        // Populate text post editor controls
        listOf("B", "I", "U").forEach { style ->
            val toggle = ToggleButton(requireContext()).apply {
                text = style; textOn = style; textOff = style
                setPadding(4, 4, 4, 4)
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.TRANSPARENT)
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
        // Text color picker: square button with 'A', background sky-blue gradient, text colored to match!
        val pickerSize = (48 * resources.displayMetrics.density).toInt()
        val btnTextColorPicker = Button(requireContext()).apply {
            text = "A"
            setBackgroundResource(R.drawable.bg_sky_blue_gradient)
            setTextColor(etTextPost.currentTextColor)
            layoutParams = LinearLayout.LayoutParams(pickerSize, pickerSize).apply {
                val m = (8 * resources.displayMetrics.density).toInt()
                setMargins(m, 0, m, 0)
            }
            setOnClickListener {
                AmbilWarnaDialog(
                    requireContext(),
                    etTextPost.currentTextColor,
                    true,
                    object : AmbilWarnaDialog.OnAmbilWarnaListener {
                        override fun onOk(dialog: AmbilWarnaDialog, color: Int) {
                            etTextPost.setTextColor(color)
                            setTextColor(color)
                        }

                        override fun onCancel(dialog: AmbilWarnaDialog) {}
                    }).show()
            }
        }
        // Background color picker: square button with 'A', text always white, background tinted to canvas color
        var currentBg = Color.WHITE
        val btnBgColorPicker = Button(requireContext()).apply {
            text = "A"
            setTextColor(Color.WHITE)
            setBackgroundColor(currentBg)
            layoutParams = LinearLayout.LayoutParams(pickerSize, pickerSize).apply {
                val m = (8 * resources.displayMetrics.density).toInt()
                setMargins(m, 0, m, 0)
            }
            setOnClickListener {
                AmbilWarnaDialog(
                    requireContext(),
                    currentBg,
                    true,
                    object : AmbilWarnaDialog.OnAmbilWarnaListener {
                        override fun onOk(dialog: AmbilWarnaDialog, color: Int) {
                            currentBg = color
                            flTextCanvas.setBackgroundColor(color)
                            setBackgroundColor(color)
                        }

                        override fun onCancel(dialog: AmbilWarnaDialog) {}
                    }).show()
            }
        }
        llColorPickers.addView(btnTextColorPicker)
        llColorPickers.addView(btnBgColorPicker)
        // Font size pickers
        hsvFontSizes = layoutTextEditor.findViewById(R.id.hsvFontSizes)
        llFontSizes = layoutTextEditor.findViewById(R.id.llFontSizes)
        listOf(24, 32, 40, 48).forEach { sizeSp ->
            val sizeBtn = TextView(requireContext()).apply {
                text = "A"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp.toFloat())
                setTextColor(Color.WHITE)
                setPadding(16, 8, 16, 8)
                setOnClickListener {
                    etTextPost.setTextSize(
                        TypedValue.COMPLEX_UNIT_SP,
                        sizeSp.toFloat()
                    )
                }
            }
            llFontSizes.addView(sizeBtn)
        }
        listOf(
            R.drawable.black_hole, R.drawable.galaxy, R.drawable.nebula, R.drawable.solar_system,
            R.drawable.universe, R.drawable.supernova, R.drawable.castle, R.drawable.dragon,
            R.drawable.phoenix, R.drawable.mermaid, R.drawable.treasure_chest, R.drawable.unicorn,
            R.drawable.infinity, R.drawable.time_machine,
            // Gradient backgrounds
            R.drawable.bg_amber_indigo_gradient, R.drawable.bg_amber_yellow_gradient,
            R.drawable.bg_blue_gray_gradient, R.drawable.bg_cyan_rose_gradient,
            R.drawable.bg_cyan_sky_gradient, R.drawable.bg_emerald_fuchsia_gradient,
            R.drawable.bg_emerald_teal_gradient, R.drawable.bg_fuchsia_pink_gradient,
            R.drawable.bg_gray_indigo_gradient, R.drawable.bg_green_emerald_gradient,
            R.drawable.bg_green_purple_gradient, R.drawable.bg_indigo_zinc_gradient,
            R.drawable.bg_lime_green_gradient, R.drawable.bg_lime_violet_gradient,
            R.drawable.bg_neutral_orange_gradient, R.drawable.bg_neutral_slate_gradient,
            R.drawable.bg_orange_blue_gradient, R.drawable.bg_orange_stone_gradient,
            R.drawable.bg_pink_indigo_gradient, R.drawable.bg_pink_rose_gradient,
            R.drawable.bg_post_details_gradient, R.drawable.bg_purple_fuchsia_gradient,
            R.drawable.bg_red_neutral_gradient, R.drawable.bg_red_orange_gradient,
            R.drawable.bg_red_sky_gradient, R.drawable.bg_rose_amber_gradient,
            R.drawable.bg_sky_blue_gradient, R.drawable.bg_sky_slate_gradient,
            R.drawable.bg_slate_blue_gradient, R.drawable.bg_stone_amber_gradient,
            R.drawable.bg_stone_gray_gradient, R.drawable.bg_teal_cyan_gradient,
            R.drawable.bg_teal_pink_gradient, R.drawable.bg_violet_purple_gradient,
            R.drawable.bg_yellow_lime_gradient, R.drawable.bg_yellow_orange_gradient,
            R.drawable.bg_yellow_zinc_gradient, R.drawable.bg_zinc_violet_gradient
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
                setTextColor(Color.WHITE)
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
        // Tab switcher for styling controls (already bound from layoutTextEditor)
        // Tab‐driven switch between text styling controls
        val groups = listOf<View>(
            hsvTextStyles, hsvColorPickers, hsvFontSizes, hsvBgImages, hsvFonts
        )
        val labels = listOf(
            "Style",
            getString(R.string.colors),
            "Size",
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
            // Hide keyboard and cursor
            (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(etTextPost.windowToken, 0)
            etTextPost.clearFocus()
            etTextPost.isCursorVisible = false
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
            // Restore cursor visibility
            etTextPost.isCursorVisible = true
            handleSelectedMedia(listOf(Uri.fromFile(file)))
        }
        btnCapture = layoutMedia.findViewById(R.id.btnCapture)
        btnSelectDevice = layoutMedia.findViewById(R.id.btnSelectDevice)
        layoutFilterOptions = layoutMedia.findViewById(R.id.layoutFilterOptions)
        hsvFilters = layoutMedia.findViewById(R.id.hsvFilters)
        pbRecordProgress = layoutMedia.findViewById(R.id.pbRecordProgress)
        segmentsBar = layoutMedia.findViewById(R.id.segmentsBar)
        tvElapsedTime = layoutMedia.findViewById(R.id.tvElapsedTime)

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
                setPadding(4, 4, 4, 4)
                setTextColor(Color.WHITE)
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

        btnModeToggle.setOnClickListener {
            isVideoMode = !isVideoMode
            btnModeToggle.setImageResource(if (isVideoMode) R.drawable.camera else R.drawable.video)
            if (isVideoMode) {
                btnCapture.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.yellow_500),
                    PorterDuff.Mode.MULTIPLY
                )
            } else {
                btnCapture.clearColorFilter()
            }
            // update edit visibility when mode toggles
            val show = !isVideoSelected && !isVideoMode
            btnEditMediaCard.visibility = if (show) View.VISIBLE else View.GONE
        }
        btnTextMode.setOnClickListener {
            showStep(layoutTextEditor)
        }
        btnCapture.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isVideoMode) {
                        // Timer-based recording supports pause/resume
                        if (recordLimitMs != null) {
                            if (isRecording && !isPaused) {
                                pauseRecording()
                            } else {
                                // start or resume
                                if (recordLimitMs == null) recordLimitMs = 5 * 1000L
                                startRecording()
                            }
                        } else {
                            // No timer: behave as before (press-and-hold capture)
                            if (!isRecording) startRecording()
                        }
                    } else {
                        // Photo mode, potential long press
                        isLongPress = false
                        longPressRunnable = Runnable {
                            isLongPress = true
                            isVideoMode = true
                            btnModeToggle.setImageResource(R.drawable.video)
                            btnCapture.clearColorFilter()
                            // start video recording with current timer (default 5s if unset)
                            if (recordLimitMs == null) recordLimitMs = 5 * 1000L
                            startRecording()
                        }
                        longPressHandler.postDelayed(
                            longPressRunnable!!,
                            500
                        ) // 500ms for long press
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    if (isRecording && recordLimitMs == null && !isPaused) {
                        stopRecording()
                    } else if (!isLongPress && !isVideoMode) {
                        takePhoto()
                    }
                }
            }
            true
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
        btnTimer5s.setOnClickListener {
            recordLimitMs = 5 * 1000L
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
        // Edit step views bound from edit root
        gpuImageView = layoutEdit.findViewById(R.id.imageEditView)
        // Crop & scale toolbar
        btnCrop = layoutEdit.findViewById(R.id.btnCrop)
        btnScale = layoutEdit.findViewById(R.id.btnScale)
        btnCrop.setOnClickListener {
            editedBitmap?.let { bmp ->
                val srcFile =
                    File(requireContext().cacheDir, "CROP_SRC_${System.currentTimeMillis()}.jpg")
                FileOutputStream(srcFile).use { out ->
                    bmp.compress(
                        Bitmap.CompressFormat.JPEG,
                        95,
                        out
                    )
                }
                val destFile =
                    File(requireContext().cacheDir, "CROP_DST_${System.currentTimeMillis()}.jpg")
                val options = UCrop.Options().apply {
                    setFreeStyleCropEnabled(true) // allow free crop, phone-friendly
                    setHideBottomControls(false)
                    setToolbarTitle(getString(R.string.crop))
                }
                // Start UCrop for this Fragment so onActivityResult receives the result here
                UCrop.of(Uri.fromFile(srcFile), Uri.fromFile(destFile))
                    .withOptions(options)
                    .start(requireContext(), this, UCrop.REQUEST_CROP)
            }
        }
        btnScale.setOnClickListener { /* pinch-to-zoom implemented on preview */ }

        // Edit options toggle: animated rotation + text fade/slide
        run {
            val toggleOptionsText = layoutEdit.findViewById<ImageView>(R.id.toggleOptionsText)
            val captionOptionsText = layoutEdit.findViewById<TextView>(R.id.captionOptionsText)
            val stickerOptionsText = layoutEdit.findViewById<TextView>(R.id.stickerOptionsText)
            val effectsOptionsText = layoutEdit.findViewById<TextView>(R.id.effectsOptionsText)
            val aiMemeOptionsText = layoutEdit.findViewById<TextView>(R.id.aiMemeOptionsText)

            val interp = AccelerateDecelerateInterpolator()
            val offset = (8 * resources.displayMetrics.density)

            fun reveal(tv: View) {
                if (tv.visibility == View.VISIBLE) return
                tv.alpha = 0f
                tv.translationX = offset
                tv.visibility = View.VISIBLE
                tv.animate().alpha(1f).translationX(0f).setDuration(200).setInterpolator(interp)
                    .start()
            }

            fun conceal(tv: View) {
                if (tv.visibility != View.VISIBLE) return
                tv.animate()
                    .alpha(0f)
                    .translationX(offset)
                    .setDuration(150)
                    .setInterpolator(interp)
                    .withEndAction { tv.visibility = View.GONE }
                    .start()
            }
            // Ensure default state: rotation 90, texts hidden
            try {
                toggleOptionsText.rotation = 90f
            } catch (_: Exception) {
            }
            listOf(
                captionOptionsText,
                stickerOptionsText,
                effectsOptionsText,
                aiMemeOptionsText
            ).forEach {
                it.visibility = View.GONE
                it.alpha = 0f
                it.translationX = offset
            }

            toggleOptionsText.setOnClickListener {
                val showing = toggleOptionsText.rotation == -90f
                val targetRot = if (showing) 90f else -90f
                toggleOptionsText.animate().rotation(targetRot).setDuration(200)
                    .setInterpolator(interp).start()
                val texts = listOf(
                    captionOptionsText,
                    stickerOptionsText,
                    effectsOptionsText,
                    aiMemeOptionsText
                )
                if (showing) texts.forEach { conceal(it) } else texts.forEach { reveal(it) }
            }
        }

        // ----- Edit Options: Caption vs Effects panes -----
        val captionOptions = layoutEdit.findViewById<LinearLayout>(R.id.captionOptions)
        val effectsOptions = layoutEdit.findViewById<LinearLayout>(R.id.effectsOptions)
        val stickerOptions = layoutEdit.findViewById<LinearLayout>(R.id.stickerOptions)
        val aiMemeOptions = layoutEdit.findViewById<LinearLayout>(R.id.aiMemeOptions)
        val effectsPane = layoutEdit.findViewById<LinearLayout>(R.id.effectsPane)
        val postCaptionLayout = layoutEdit.findViewById<LinearLayout>(R.id.postCaptionLayout)
        var currentEditingCaption: TextView? = null
        // Default hidden per request
        effectsPane.visibility = View.GONE
        postCaptionLayout.visibility = View.GONE
        captionOptions.setOnClickListener {
            currentEditingCaption = null
            postCaptionLayout.visibility = View.VISIBLE
            effectsPane.visibility = View.GONE
            val captionText = layoutEdit.findViewById<TextView>(R.id.captionText)
            captionText.text = ""
            captionText.requestFocus()
            try {
                (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(captionText, InputMethodManager.SHOW_IMPLICIT)
            } catch (_: Exception) {
            }
        }
        effectsOptions.setOnClickListener {
            effectsPane.visibility = View.VISIBLE
            postCaptionLayout.visibility = View.GONE
        }

        // dp helper must appear before first use
        fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
        fun makeDraggable(tv: View) {
            var dX = 0f
            var dY = 0f
            var downX = 0f
            var downY = 0f
            var downTime = 0L
            val clickSlop = (8 * resources.displayMetrics.density)
            tv.setOnTouchListener { v, ev ->
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> { dX = v.x - ev.rawX; dY = v.y - ev.rawY; downX = ev.rawX; downY = ev.rawY; downTime = System.currentTimeMillis() }
                    MotionEvent.ACTION_MOVE -> { v.x = ev.rawX + dX; v.y = ev.rawY + dY }
                    MotionEvent.ACTION_UP -> {
                        val dx = ev.rawX - downX
                        val dy = ev.rawY - downY
                        val dist2 = dx * dx + dy * dy
                        val slop2 = clickSlop * clickSlop
                        val dur = System.currentTimeMillis() - downTime
                        if (dist2 < slop2 && dur < 250L) {
                            // Trigger view's own click handler; caption TextViews
                            // have their editor opener set via setOnClickListener elsewhere.
                            v.performClick()
                        }
                    }
                }
                true
            }
        }
        fun attachDragAndScale(view: View) {
            makeDraggable(view)
            val scaleDetector = ScaleGestureDetector(requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                private var startScaleX = 1f
                private var startScaleY = 1f
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    startScaleX = view.scaleX
                    startScaleY = view.scaleY
                    return true
                }
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val s = detector.scaleFactor
                    view.scaleX = (startScaleX * s).coerceIn(0.3f, 5f)
                    view.scaleY = (startScaleY * s).coerceIn(0.3f, 5f)
                    return true
                }
            })
            view.setOnTouchListener { v, ev ->
                scaleDetector.onTouchEvent(ev)
                if (!scaleDetector.isInProgress) {
                    when (ev.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            v.setTag(R.id.tag_dx, v.x - ev.rawX)
                            v.setTag(R.id.tag_dy, v.y - ev.rawY)
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (v.getTag(R.id.tag_dx) as? Float) ?: 0f
                            val dy = (v.getTag(R.id.tag_dy) as? Float) ?: 0f
                            v.x = ev.rawX + dx
                            v.y = ev.rawY + dy
                        }
                    }
                }
                true
            }
        }

        // --- Stickers: simple picker from bundled drawables ---
        val stickerCandidates = listOf(
            R.drawable.unicorn, R.drawable.rose, R.drawable.trophy, R.drawable.diamond,
            R.drawable.gift, R.drawable.friends, R.drawable.google, R.drawable.logo,
            R.drawable.bell, R.drawable.nebula
        )
        fun addStickerOverlay(@DrawableRes id: Int) {
            val overlay = layoutEdit.findViewById<FrameLayout>(R.id.editOverlay)
            val iv = ImageView(requireContext()).apply {
                setImageResource(id)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER
                }
                // Initial size scaled to ~20% of overlay width
                post {
                    val w = overlay.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
                    val target = (w * 0.25f).toInt()
                    this.layoutParams = this.layoutParams.apply {
                        width = target
                        height = FrameLayout.LayoutParams.WRAP_CONTENT
                    }
                    requestLayout()
                }
            }
            attachDragAndScale(iv)
            overlay.addView(iv)
        }
        fun addStickerOverlayFromDrawable(drawable: android.graphics.drawable.Drawable) {
            val overlay = layoutEdit.findViewById<FrameLayout>(R.id.editOverlay)
            // Clone drawable into a standalone Bitmap to avoid sharing state with dialog view
            fun drawableToBitmap(d: android.graphics.drawable.Drawable): android.graphics.Bitmap {
                return if (d is android.graphics.drawable.BitmapDrawable && d.bitmap != null) {
                    d.bitmap
                } else {
                    val iw = d.intrinsicWidth.takeIf { it > 0 } ?: dp(64)
                    val ih = d.intrinsicHeight.takeIf { it > 0 } ?: dp(64)
                    val bmp = android.graphics.Bitmap.createBitmap(iw, ih, android.graphics.Bitmap.Config.ARGB_8888)
                    val c = android.graphics.Canvas(bmp)
                    d.setBounds(0, 0, c.width, c.height)
                    d.draw(c)
                    bmp
                }
            }
            val clone = android.graphics.drawable.BitmapDrawable(resources, drawableToBitmap(drawable))
            val iv = ImageView(requireContext()).apply {
                setImageDrawable(clone)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply { gravity = Gravity.CENTER }
            }
            // Attach first, then size similar to local flow for consistency
            overlay.addView(iv)
            attachDragAndScale(iv)
            iv.post {
                val w = overlay.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
                val target = (w * 0.25f).toInt()
                iv.layoutParams = iv.layoutParams.apply {
                    width = target
                    height = FrameLayout.LayoutParams.WRAP_CONTENT
                }
                iv.requestLayout()
                overlay.invalidate()
            }
        }
        fun addStickerOverlayFromUrl(url: String) {
            val overlay = layoutEdit.findViewById<FrameLayout>(R.id.editOverlay)
            viewLifecycleOwner.lifecycleScope.launch {
                // Fetch sticker bitmap synchronously off main to avoid race conditions
                val drawable = withContext(Dispatchers.IO) {
                    try {
                        val req = ImageRequest.Builder(requireContext())
                            .data(url)
                            .allowHardware(false)
                            .build()
                        val res = requireContext().imageLoader.execute(req)
                        res.drawable
                    } catch (_: Exception) { null }
                }
                if (!isAdded) return@launch
                if (drawable == null) {
                    Toast.makeText(requireContext(), R.string.please_wait_sticker_loading, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val iv = ImageView(requireContext()).apply {
                    setImageDrawable(drawable)
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply { gravity = Gravity.CENTER }
                }
                // Add immediately to ensure it is present during composition,
                // then adjust size on the next frame similar to local flow.
                overlay.addView(iv)
                attachDragAndScale(iv)
                iv.post {
                    val w = overlay.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
                    val target = (w * 0.25f).toInt()
                    iv.layoutParams = iv.layoutParams.apply {
                        width = target
                        height = FrameLayout.LayoutParams.WRAP_CONTENT
                    }
                    iv.requestLayout()
                    overlay.invalidate()
                }
            }
        }

        // Ensure a remote sticker is cached to local storage and return the file path if available.
        suspend fun ensureStickerCached(url: String): String? = withContext(Dispatchers.IO) {
            try {
                val dir = File(requireContext().cacheDir, "stickers").apply { mkdirs() }
                val name = url.hashCode().toString() + ".png"
                val outFile = File(dir, name)
                // TTL: refresh if older than 3 days
                val threeDaysMs = 3L * 24 * 60 * 60 * 1000
                if (outFile.exists() && outFile.length() > 0 && (System.currentTimeMillis() - outFile.lastModified()) < threeDaysMs) {
                    return@withContext outFile.absolutePath
                }
                val req = ImageRequest.Builder(requireContext())
                    .data(url)
                    .allowHardware(false)
                    .build()
                val res = requireContext().imageLoader.execute(req)
                val drawable = res.drawable as? android.graphics.drawable.BitmapDrawable ?: return@withContext null
                FileOutputStream(outFile).use { fos -> drawable.bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos) }
                return@withContext outFile.absolutePath
            } catch (_: Exception) { null }
        }
        fun showStickerPicker() {
            var dlg: AlertDialog? = null
            var prefetchJob: Job? = null
            val outer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
            }
            val search = EditText(requireContext()).apply {
                hint = "Search stickers"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                setPadding(dp(12), dp(12), dp(12), dp(6))
            }
            val container = ScrollView(requireContext())
            val grid = GridLayout(requireContext()).apply {
                columnCount = 4
                setPadding(dp(12), dp(12), dp(12), dp(12))
            }
            container.addView(grid)
            outer.addView(search)
            outer.addView(container, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            // Hold references to views and their searchable names for filtering
            val stickerViews = mutableListOf<Pair<View, String>>()
            // First, populate with remote stickers if available
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val remote = withContext(Dispatchers.IO) { RetrofitClient.stickersApi.getActiveStickers() }
                    if (remote.isNotEmpty()) {
                        // Warm local cache in background (refresh quietly, limited cost)
                        val remoteShuffled = remote.shuffled()
                        prefetchJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                val semaphore = Semaphore(permits = 8)
                                val tasks = remoteShuffled.map { row ->
                                    async {
                                        semaphore.withPermit { ensureStickerCached(row.imageUrl) }
                                    }
                                }
                                tasks.awaitAll()
                            } catch (_: Exception) {}
                        }
                        remoteShuffled.forEach { row ->
                            val thumb = ImageView(requireContext()).apply {
                                val s = dp(64)
                                layoutParams = ViewGroup.MarginLayoutParams(s, s).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) }
                                scaleType = ImageView.ScaleType.FIT_CENTER
                            }
                            try { thumb.load(row.imageUrl) { crossfade(false) } } catch (_: Throwable) {}
                            thumb.setOnClickListener {
                                dlg?.dismiss()
                                // Prefer local cached file to avoid hardware bitmaps and ensure availability
                                viewLifecycleOwner.lifecycleScope.launch {
                                    val local = ensureStickerCached(row.imageUrl)
                                    addStickerOverlayFromUrl(local ?: row.imageUrl)
                                }
                            }
                            grid.addView(thumb)
                            // Record searchable name (prefer explicit name; fallback to filename stem)
                            val raw = try {
                                if (row.name.isNotBlank()) row.name else row.imageUrl.substringAfterLast('/').substringBeforeLast('.')
                            } catch (_: Exception) { row.name }
                            val normalized = raw.replace('_', ' ').lowercase()
                            stickerViews.add(thumb to normalized)
                        }
                    }
                } catch (_: Exception) { /* ignore */ }
                // Always add built-in local stickers as a fallback/extra
                stickerCandidates.shuffled().forEach { resId ->
                    val iv = ImageView(requireContext()).apply {
                        setImageResource(resId)
                        val s = dp(64)
                        layoutParams = ViewGroup.MarginLayoutParams(s, s).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) }
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setOnClickListener { dlg?.dismiss(); addStickerOverlay(resId) }
                    }
                    grid.addView(iv)
                    val resName = try { resources.getResourceEntryName(resId) } catch (_: Exception) { "" }
                    val normalized = resName.replace('_', ' ').lowercase()
                    stickerViews.add(iv to normalized)
                }
            }
            dlg = AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.stickers))
                .setView(outer)
                .setNegativeButton(android.R.string.cancel, null)
                .create()
            dlg.show()
            dlg.setOnDismissListener { prefetchJob?.cancel() }

            // Search filter behavior
            fun applyFilter(q: String?) {
                val query = q?.trim()?.lowercase().orEmpty()
                if (query.isEmpty()) {
                    stickerViews.forEach { (v, _) -> v.visibility = View.VISIBLE }
                } else {
                    stickerViews.forEach { (v, name) -> v.visibility = if (name.contains(query)) View.VISIBLE else View.GONE }
                }
                grid.requestLayout()
            }
            search.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { applyFilter(s?.toString()) }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        stickerOptions.setOnClickListener { showStickerPicker() }

        // --- AI Meme (local generator): top/bottom Impact-style text ---
        fun addMemeText(top: String?, bottom: String?) {
            val overlay = layoutEdit.findViewById<FrameLayout>(R.id.editOverlay)
            fun make(text: String, yPos: Float): TextView {
                val tv = MemeTextView(requireContext()).apply {
                    setTextColor(Color.WHITE)
                    textSize = 24f
                    setPadding(dp(6), dp(2), dp(6), dp(2))
                    setTypeface(typeface, Typeface.BOLD)
                    setText(text.uppercase())
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }
                }
                // Prefer Anton (Impact-like) if available via Downloadable Fonts
                try {
                    val req = FontRequest(
                        "com.google.android.gms.fonts",
                        "com.google.android.gms",
                        "Anton",
                        R.array.com_google_android_gms_fonts_certs
                    )
                    FontsContractCompat.requestFont(
                        requireContext(),
                        req,
                        object : FontsContractCompat.FontRequestCallback() {
                            override fun onTypefaceRetrieved(typeface: Typeface) { tv.typeface = typeface }
                        },
                        Handler(Looper.getMainLooper())
                    )
                } catch (_: Exception) {}
                tv.post {
                    tv.x = (overlay.width - tv.width) / 2f
                    tv.y = yPos
                }
                return tv
            }
            val margin = dp(12).toFloat()
            if (!top.isNullOrBlank()) {
                val tvTop = make(top, margin)
                attachDragAndScale(tvTop)
                overlay.addView(tvTop)
            }
            if (!bottom.isNullOrBlank()) {
                val tvBottom = make(bottom, (overlay.height - margin - dp(48)).coerceAtLeast(margin))
                attachDragAndScale(tvBottom)
                overlay.addView(tvBottom)
            }
        }
        fun showMemeDialog() {
            val container = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(4))
            }
            val etTop = EditText(requireContext()).apply { hint = getString(R.string.top_text); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES }
            val etBottom = EditText(requireContext()).apply { hint = getString(R.string.bottom_text); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES }
            container.addView(etTop)
            container.addView(etBottom)
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.ai_meme))
                .setView(container)
                .setPositiveButton(getString(R.string.add)) { _, _ -> addMemeText(etTop.text?.toString(), etBottom.text?.toString()) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        fun showAIMemeOverlay(show: Boolean) {
            val root = layoutEdit
            val ovTag = 0xA1A1A1
            if (show) {
                if (root.findViewWithTag<View>(ovTag) != null) return
                val overlay = FrameLayout(requireContext()).apply {
                    setBackgroundColor(0x88000000.toInt())
                    tag = ovTag
                    layoutParams = ConstraintLayout.LayoutParams(
                        ConstraintLayout.LayoutParams.MATCH_PARENT,
                        ConstraintLayout.LayoutParams.MATCH_PARENT
                    ).apply {
                        topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                        bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                        startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                        endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    }
                }
                val text = TextView(requireContext()).apply {
                    text = "AI meme generating…"
                    setTextColor(Color.WHITE)
                    textSize = 18f
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                    gravity = Gravity.CENTER
                }
                overlay.addView(text, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                root.addView(overlay)
            } else {
                root.findViewWithTag<View>(ovTag)?.let { root.removeView(it) }
            }
        }

        aiMemeOptions.setOnClickListener {
            // Try AI meme: capture current preview (image or first video frame) and call Edge Function
            val base = try { gpuImageView.capture() } catch (_: Exception) { editedBitmap ?: originalBitmap }
            if (base == null) { showMemeDialog(); return@setOnClickListener }
            showAIMemeOverlay(true)
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val baos = java.io.ByteArrayOutputStream()
                    base.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                    val b64 = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
                    val resp = RetrofitClient.functionsApi.generateMeme(mapOf("image_base64" to b64, "sfw" to true))
                    val body = resp.body()
                    withContext(Dispatchers.Main) {
                        showAIMemeOverlay(false)
                        if (resp.isSuccessful && body != null) {
                            val safe = fun(s: String?): String? {
                                if (s.isNullOrBlank()) return s
                                val banned = listOf("\uD83D\uDD1E") // placeholder minimal client filter
                                var t = s!!
                                banned.forEach { w -> t = t.replace(w, "") }
                                return t
                            }
                            val top = safe(body.top_text)
                            val bottom = safe(body.bottom_text)
                            addMemeText(top, bottom)
                            // Map AI sticker names to local assets
                            body.stickers?.forEach { name ->
                                val resName = name.lowercase().replace(" ", "_")
                                val candidate = when {
                                    resName.contains("unicorn") -> R.drawable.unicorn
                                    resName.contains("rose") -> R.drawable.rose
                                    resName.contains("trophy") -> R.drawable.trophy
                                    resName.contains("diamond") -> R.drawable.diamond
                                    resName.contains("gift") -> R.drawable.gift
                                    resName.contains("friends") -> R.drawable.friends
                                    resName.contains("google") -> R.drawable.google
                                    resName.contains("logo") -> R.drawable.logo
                                    resName.contains("bell") -> R.drawable.bell
                                    resName.contains("nebula") -> R.drawable.nebula
                                    else -> null
                                }
                                candidate?.let { addStickerOverlay(it) }
                            }
                            Toast.makeText(requireContext(), "Tip: tap text to edit, drag to move", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "Meme generator unavailable", Toast.LENGTH_SHORT).show()
                            showMemeDialog()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showAIMemeOverlay(false)
                        Toast.makeText(requireContext(), "Meme generator unavailable", Toast.LENGTH_SHORT).show()
                        showMemeDialog()
                    }
                }
            }
        }

        // moved showAIMemeOverlay above usage

        // ----- Caption controls -----
        val captionText = layoutEdit.findViewById<TextView>(R.id.captionText)
        val captionAlignment = layoutEdit.findViewById<LinearLayout>(R.id.captionAlignment)
        val captionOutlineMode = layoutEdit.findViewById<TextView>(R.id.captionOutlineMode)
        val captionFontBtn = layoutEdit.findViewById<TextView>(R.id.captionFont)
        val captionColorBtn = layoutEdit.findViewById<ImageView>(R.id.captionColor)
        val captionColorsScroll =
            layoutEdit.findViewById<HorizontalScrollView>(R.id.captionColorsScroll)
        val captionFontsScroll =
            layoutEdit.findViewById<HorizontalScrollView>(R.id.captionFontsScroll)
        val captionColors = layoutEdit.findViewById<LinearLayout>(R.id.captionColors)
        val captionFonts = layoutEdit.findViewById<LinearLayout>(R.id.captionFonts)

        // Both scrollers hidden by default
        captionColorsScroll.visibility = View.GONE
        captionFontsScroll.visibility = View.GONE

        var captionAlignIndex = 0 // 0=end, 1=center, 2=start
        var captionUseBg = false
        fun applyCaptionGravity() {
            val g = when (captionAlignIndex % 3) {
                0 -> Gravity.END or Gravity.CENTER_VERTICAL
                1 -> Gravity.CENTER
                else -> Gravity.START or Gravity.CENTER_VERTICAL
            }
            captionText.gravity = g
            captionAlignment.gravity = g
        }
        applyCaptionGravity()
        captionAlignment.setOnClickListener {
            captionAlignIndex = (captionAlignIndex + 1) % 3
            applyCaptionGravity()
        }

        // moved dp above first usage
        fun applyCaptionOutlineMode() {
            if (captionUseBg) {
                captionText.setShadowLayer(0f, 0f, 0f, 0)
                val bg = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(0x66000000)
                }
                captionText.background = bg
                val p = dp(8)
                captionText.setPadding(p, p, p, p)
            } else {
                captionText.background = null
                captionText.setPadding(0, 0, 0, 0)
                captionText.setShadowLayer(6f, 0f, 0f, Color.BLACK)
            }
        }
        applyCaptionOutlineMode()
        captionOutlineMode.setOnClickListener {
            captionUseBg = !captionUseBg
            applyCaptionOutlineMode()
        }

        // Clicking Font button shows Fonts scroller; clicking Color shows Colors scroller
        captionFontBtn.setOnClickListener {
            captionFontsScroll.visibility = View.VISIBLE
            captionColorsScroll.visibility = View.GONE
        }
        captionColorBtn.setOnClickListener {
            captionColorsScroll.visibility = View.VISIBLE
            captionFontsScroll.visibility = View.GONE
        }

        // Draggable caption overlays
        val editOverlay = layoutEdit.findViewById<FrameLayout>(R.id.editOverlay)
        // Editor opener lambda, accessible before drag handler
        val openCaptionEditor: (TextView?) -> Unit = { existing ->
            currentEditingCaption = existing
            if (existing != null) {
                captionText.text = existing.text
                captionText.typeface = existing.typeface
                captionText.setTextColor(existing.currentTextColor)
                captionText.background = existing.background?.constantState?.newDrawable()?.mutate()
                // Mirror alignment and outline state
                captionAlignIndex = when (existing.textAlignment) {
                    View.TEXT_ALIGNMENT_VIEW_END -> 0
                    View.TEXT_ALIGNMENT_CENTER -> 1
                    else -> 2
                }
                applyCaptionGravity()
                captionUseBg = existing.background != null
                applyCaptionOutlineMode()
            } else {
                captionText.text = ""
            }
            postCaptionLayout.visibility = View.VISIBLE
            effectsPane.visibility = View.GONE
            captionText.requestFocus()
            try {
                (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(captionText, InputMethodManager.SHOW_IMPLICIT)
            } catch (_: Exception) { }
        }
        fun applyStyle(to: TextView) {
            to.typeface = captionText.typeface
            to.setTextColor(captionText.currentTextColor)
            if (captionText.background != null) {
                to.background = captionText.background.constantState?.newDrawable()?.mutate()
                to.setPadding(
                    captionText.paddingLeft,
                    captionText.paddingTop,
                    captionText.paddingRight,
                    captionText.paddingBottom
                )
                to.setShadowLayer(0f, 0f, 0f, 0)
            } else {
                to.background = null
                to.setPadding(0, 0, 0, 0)
                to.setShadowLayer(6f, 0f, 0f, Color.BLACK)
            }
            // Keep overlay caption's gravity consistent with editor
            to.gravity = captionText.gravity
            to.textAlignment = when (captionText.gravity) {
                Gravity.CENTER -> View.TEXT_ALIGNMENT_CENTER
                else -> if ((captionText.gravity and Gravity.END) == Gravity.END) View.TEXT_ALIGNMENT_VIEW_END else View.TEXT_ALIGNMENT_VIEW_START
            }
        }

        // moved earlier
        layoutEdit.findViewById<View>(R.id.doneCaption)?.setOnClickListener {
            val txt = captionText.text?.toString()?.trim().orEmpty()
            postCaptionLayout.visibility = View.GONE
            if (txt.isEmpty()) return@setOnClickListener
            val target = currentEditingCaption ?: TextView(requireContext()).apply {
                textSize = 22f
                setTextColor(Color.WHITE)
                setOnClickListener { openCaptionEditor(this) }
            }
            target.text = txt
            applyStyle(target)
            if (currentEditingCaption == null) {
                val lp = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                lp.gravity = Gravity.CENTER
                editOverlay.addView(target, lp)
                makeDraggable(target)
            }
            try {
                (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(captionText.windowToken, 0)
            } catch (_: Exception) {
            }
            currentEditingCaption = null
        }
        editOverlay.setOnClickListener { openCaptionEditor(null) }

        // Populate fonts list with system families, styled like placeholders
        captionFonts.removeAllViews()
        listOf(
            Pair("Sans Serif", Typeface.SANS_SERIF),
            Pair("Light", Typeface.create("sans-serif-light", Typeface.NORMAL)),
            Pair("Medium", Typeface.create("sans-serif-medium", Typeface.NORMAL)),
            Pair("Black", Typeface.create("sans-serif-black", Typeface.NORMAL)),
            Pair("Serif", Typeface.SERIF),
            Pair("Mono", Typeface.MONOSPACE)
        ).forEach { (label, tf) ->
            val tv = TextView(requireContext()).apply {
                text = label
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                textSize = 20f
                background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.bg_border_gray_300)
                typeface = tf
                setOnClickListener { captionText.typeface = tf }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(dp(8), dp(8), dp(8), dp(8))
            captionFonts.addView(tv, params)
        }

        // Extend with Downloadable Fonts (Google Fonts provider). Fetch on tap, preview if available.
        fun requestDownloadableFont(family: String, onReady: (Typeface?) -> Unit) {
            try {
                val request = FontRequest(
                    "com.google.android.gms.fonts",
                    "com.google.android.gms",
                    family,
                    R.array.com_google_android_gms_fonts_certs
                )
                FontsContractCompat.requestFont(
                    requireContext(),
                    request,
                    object : FontsContractCompat.FontRequestCallback() {
                        override fun onTypefaceRetrieved(typeface: Typeface) { onReady(typeface) }
                        override fun onTypefaceRequestFailed(reason: Int) { onReady(null) }
                    },
                    Handler(Looper.getMainLooper())
                )
            } catch (_: Exception) { onReady(null) }
        }
        fun addFontChip(label: String) {
            val tv = TextView(requireContext()).apply {
                text = label
                setPadding(dp(8), dp(4), dp(8), dp(4))
                setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                textSize = 18f
                background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_border_gray_300)
                setOnClickListener {
                    requestDownloadableFont(label) { tf -> if (tf != null) captionText.typeface = tf }
                }
            }
            // Try preview
            requestDownloadableFont(label) { tf -> if (tf != null) tv.typeface = tf }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(dp(8), dp(8), dp(8), dp(8))
            captionFonts.addView(tv, params)
        }
        val googleFamilies = listOf(
            "Anton", "Bebas Neue", "Oswald", "Teko", "Titillium Web",
            "Lobster", "Pacifico", "Merriweather", "Playfair Display",
            "Abril Fatface", "Dancing Script", "Indie Flower",
            "Roboto Slab", "Montserrat", "Nunito", "Poppins",
            "Rubik", "Righteous", "Fjalla One", "Concert One",
            "Monoton", "Bangers"
        )
        googleFamilies.forEach { addFontChip(it) }

        // Populate colors list with ALL colors from R.color, styled like placeholders
        captionColors.removeAllViews()
        try {
            val fields: Array<Field> = R.color::class.java.fields
            fields.forEach { field ->
                val colorResId = try {
                    field.getInt(null)
                } catch (_: Exception) {
                    0
                }
                if (colorResId != 0) {
                    val iv = ImageView(requireContext()).apply {
                        val sz = dp(30)
                        layoutParams = LinearLayout.LayoutParams(sz, sz)
                            .apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
                        background = ContextCompat.getDrawable(
                            requireContext(),
                            R.drawable.bg_message_received
                        )
                        setPadding(dp(2), dp(2), dp(2), dp(2))
                        setImageDrawable(
                            ContextCompat.getDrawable(
                                requireContext(),
                                R.drawable.bg_message_received
                            )
                        )
                        imageTintList =
                            ContextCompat.getColorStateList(requireContext(), colorResId)
                        setOnClickListener {
                            val c = ContextCompat.getColor(requireContext(), colorResId)
                            captionText.setTextColor(c)
                        }
                    }
                    captionColors.addView(iv)
                }
            }
        } catch (_: Exception) {
        }

        // Details preview: open full-screen preview when tapped
        ivPostPreview.setOnClickListener {
            try {
                val intent = Intent(
                    requireContext(),
                    FullscreenPostPreviewActivity::class.java
                )
                val bmp = editedBitmap ?: originalBitmap
                if (bmp != null) {
                    val file = File(requireContext().cacheDir, "post_preview.png")
                    FileOutputStream(file)
                        .use { fos -> bmp.compress(Bitmap.CompressFormat.PNG, 100, fos) }
                    intent.putExtra("image_path", file.absolutePath)
                }
                intent.putExtra("content_text", etContent.text?.toString() ?: "")
                intent.putExtra(
                    "access_type", when (rgAccessType.checkedRadioButtonId) {
                        R.id.rbPaid -> "paid"
                        R.id.rbSubscriberOnly -> "subscription"
                        else -> "free"
                    }
                )
                val priceText = etPrice.text?.toString()?.trim()
                if (!priceText.isNullOrEmpty()) intent.putExtra("price", priceText)
                startActivity(intent)
            } catch (_: Exception) {
            }
        }

        // Pinch-to-zoom on the camera preview
        scaleGestureDetector = ScaleGestureDetector(
            requireContext(),
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val state = camera?.cameraInfo?.zoomState?.value ?: return true
                    val newRatio = (state.zoomRatio * detector.scaleFactor)
                        .coerceIn(state.minZoomRatio, state.maxZoomRatio)
                    camera?.cameraControl?.setZoomRatio(newRatio)
                    currentZoomRatio = newRatio
                    return true
                }
            })
        previewView.setOnTouchListener { _, ev ->
            scaleGestureDetector.onTouchEvent(ev)
            true
        }

        // Initialize segments bar (empty)
        updateSegmentsBar()

        baseFilter = GPUImageFilter()
        contrastFilter = GPUImageContrastFilter(1.0f)
        brightnessFilter = GPUImageBrightnessFilter(0.0f)
        selectedFilterItem = FilterItem("Normal", baseFilter, false)
        // initialize default slider positions for adjustable filters
        sliderPositions["Contrast+"] = 50
        sliderPositions["Bright+"] = 50

        fun applyFilters() {
            val group = GPUImageFilterGroup().apply {
                addFilter(baseFilter)
                // apply all enabled adjustable filters
                if ("Contrast+" in enabledAdjustable) addFilter(contrastFilter)
                if ("Bright+" in enabledAdjustable) addFilter(brightnessFilter)
            }
            gpuImageView.filter = group
            gpuImageView.requestRender()
        }

        rvFilters = layoutEdit.findViewById(R.id.rvFilters)
        rvFilters.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        val filters = listOf(
            FilterItem("Normal", baseFilter, false),
            FilterItem("Gray", GPUImageGrayscaleFilter()),
            FilterItem("Sepia", GPUImageSepiaToneFilter()),
            FilterItem("Invert", GPUImageColorInvertFilter()),
            FilterItem("Sketch", jp.co.cyberagent.android.gpuimage.filter.GPUImageSketchFilter()),
            FilterItem("Toon", jp.co.cyberagent.android.gpuimage.filter.GPUImageToonFilter()),
            FilterItem("Pixelate", jp.co.cyberagent.android.gpuimage.filter.GPUImagePixelationFilter()),
            FilterItem("Monochrome", jp.co.cyberagent.android.gpuimage.filter.GPUImageMonochromeFilter()),
            FilterItem("Vignette", jp.co.cyberagent.android.gpuimage.filter.GPUImageVignetteFilter()),
            FilterItem("GaussianBlur", jp.co.cyberagent.android.gpuimage.filter.GPUImageGaussianBlurFilter()),
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

        sbFilterLevel = layoutEdit.findViewById(R.id.sbFilterLevel)
        sbFilterLevel.isVisible = false
        sbFilterLevel.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (selectedFilterItem.adjustable) {
                    // remember and apply this adjustable filter
                    sliderPositions[selectedFilterItem.name] = progress
                    when (selectedFilterItem.filter) {
                        is GPUImageContrastFilter -> contrastFilter.setContrast(1f + (progress - 50) / 50f)
                        is GPUImageBrightnessFilter -> brightnessFilter.setBrightness((progress - 50) / 50f)
                    }
                    applyFilters()
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        btnApplyFilter.setOnClickListener {
            // Only capture/apply filters; stay on Edit. Run capture off UI thread.
            viewLifecycleOwner.lifecycleScope.launch {
                progressBar.isVisible = true
                val captured = withContext(Dispatchers.Default) {
                    try { gpuImageView.capture() } catch (_: InterruptedException) { null }
                }
                editedBitmap = captured ?: originalBitmap
                progressBar.isVisible = false
            }
        }

        // Proceed button: image -> compose bitmap; video -> export with overlays
        layoutEdit.findViewById<View>(R.id.proceedToDetails)?.setOnClickListener {
            // Do not gate proceed on remote loads; overlay rendering now uses pre-fetched bitmaps
            if (isVideoSelected && selectedUris.isNotEmpty()) {
                val uri = selectedUris.first()
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(requireContext(), uri)
                    val vw = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 720
                    val vh = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1280
                    val overlay = layoutEdit.findViewById<FrameLayout>(R.id.editOverlay)
                    val bmp = VideoOverlayExporter.renderOverlayBitmap(overlay, vw, vh)
                    progressBar.isVisible = true
                    VideoOverlayExporter.export(requireContext(), uri, bmp, onProgress = null) { res ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            progressBar.isVisible = false
                            res.output?.let { file ->
                                selectedUris.clear()
                                selectedUris.add(Uri.fromFile(file))
                            }
                            showStep(layoutDetails)
                            updatePostPreview()
                        }
                    }
                } catch (_: Exception) {
                    showStep(layoutDetails); updatePostPreview()
                } finally { retriever.release() }
            } else {
                viewLifecycleOwner.lifecycleScope.launch {
                    progressBar.isVisible = true
                    // Capture GPU output off UI thread
                    val base = withContext(Dispatchers.Default) {
                        try { gpuImageView.capture() } catch (_: InterruptedException) { editedBitmap ?: originalBitmap }
                    }
                    var composed: Bitmap? = null
                    if (base != null) {
                        // Render overlay on the main thread for View safety
                        val overlayBmp = withContext(Dispatchers.Main) {
                            val overlay = layoutEdit.findViewById<FrameLayout>(R.id.editOverlay)
                            VideoOverlayExporter.renderOverlayBitmap(overlay, base.width, base.height)
                        }
                        // Compose on a background thread
                        composed = withContext(Dispatchers.Default) {
                            val out = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(out)
                            canvas.drawBitmap(base, 0f, 0f, null)
                            try { canvas.drawBitmap(overlayBmp, 0f, 0f, null) } catch (e: Exception) { Log.e(TAG, "Overlay draw failed", e) }
                            out
                        }
                    }
                    if (composed != null) editedBitmap = composed
                    progressBar.isVisible = false
                    showStep(layoutDetails)
                    updatePostPreview()
                }
            }
        }
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

    /**
     * Set camera zoom via zoom wheel or preset, clamped to supported range.
     */
    private fun setZoomRatio(ratio: Float) {
        camera?.cameraInfo?.zoomState?.value?.let { state ->
            val clamped = ratio.coerceIn(state.minZoomRatio, state.maxZoomRatio)
            camera?.cameraControl?.setZoomRatio(clamped)
            currentZoomRatio = clamped
            if (clamped != ratio) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.zoom_not_supported, ratio),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


    private fun takePhoto() {
        val photoFile =
            File(requireContext().cacheDir, "IMG_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    // Log.e(TAG, "Photo capture failed", exc)
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
            File(requireContext().cacheDir, "VID_${System.currentTimeMillis()}.mp4")
        videoCapture?.let { vc ->
            VideoCaptureHelper.startRecording(
                vc,
                videoFile,
                ContextCompat.getMainExecutor(requireContext()),
                object : VideoCapture.OnVideoSavedCallback {
                    override fun onError(
                        videoCaptureError: Int,
                        message: String,
                        cause: Throwable?
                    ) {
                        val ctx = context ?: return
                        Toast.makeText(ctx, "Video capture failed", Toast.LENGTH_SHORT).show()
                        recordingActive = false
                    }

                    override fun onVideoSaved(output: VideoCapture.OutputFileResults) {
                        if (!isAdded) return
                        recordingActive = false
                        // Record this segment
                        if (isPaused || (recordLimitMs != null)) {
                            recordedSegments.add(videoFile)
                            val segDur = System.currentTimeMillis() - currentSegmentStartMs
                            recordedSegmentDurations.add(segDur)
                            totalRecordedMs += segDur
                            updateSegmentsBar()
                            if (pendingFinalize) {
                                pendingFinalize = false
                                // Merge all segments into a single file (fallback to last if merge fails)
                                viewLifecycleOwner.lifecycleScope.launch {
                                    val merged = withContext(Dispatchers.IO) {
                                        mergeSegmentsSafely(recordedSegments)
                                    }
                                    val file = merged ?: recordedSegments.lastOrNull() ?: videoFile
                                    handleSelectedMedia(listOf(Uri.fromFile(file)))
                                    // clean up other segments if merged
                                    if (merged != null) {
                                        recordedSegments.forEach {
                                            if (it != merged) try {
                                                it.delete()
                                            } catch (_: Exception) {
                                            }
                                        }
                                    }
                                    // reset state for next recording
                                    recordedSegments.clear()
                                    recordedSegmentDurations.clear()
                                    totalRecordedMs = 0L
                                    updateSegmentsBar()
                                }
                            }
                        } else {
                            // No timer flow: proceed directly
                            val savedUri = Uri.fromFile(videoFile)
                            handleSelectedMedia(listOf(savedUri))
                        }
                    }
                }
            )
        }
        isRecording = true
        recordingActive = true
        isPaused = false
        currentSegmentStartMs = System.currentTimeMillis()
        btnCapture.setImageResource(R.drawable.stop_record)
        pbRecordProgress.isVisible = true
        recordTimer?.cancel()
        recordLimitMs?.let { limit ->
            val remaining = (limit - totalRecordedMs).coerceAtLeast(0L)
            recordTimer =
                object : CountDownTimer(remaining, (remaining.coerceAtLeast(1000L) / 100)) {
                    override fun onTick(millisUntilFinished: Long) {
                        val elapsed = totalRecordedMs + (remaining - millisUntilFinished)
                        val p = ((elapsed * 100) / limit).toInt().coerceIn(0, 100)
                        pbRecordProgress.progress = p
                        updateSegmentsBar()
                    }

                    override fun onFinish() {
                        pbRecordProgress.progress = 100
                        pendingFinalize = true
                        stopRecording()
                    }
                }.apply { start() }
        }
        updateSegmentsBar()
    }

    private fun stopRecording() {
        if (!isRecording) return
        try {
            if (recordingActive) videoCapture?.stopRecording()
        } catch (_: Exception) {
        }
        isRecording = false
        recordingActive = false
        isPaused = false
        btnCapture.setImageResource(R.drawable.record)
        recordTimer?.cancel()
        pbRecordProgress.isVisible = false
        btnUndoSegment.visibility = View.GONE
        updateSegmentsBar()
    }

    private fun pauseRecording() {
        if (!isRecording || recordLimitMs == null) return
        try {
            if (recordingActive) videoCapture?.stopRecording()
        } catch (_: Exception) {
        }
        isRecording = false
        recordingActive = false
        isPaused = true
        btnUndoSegment.visibility = if (recordedSegments.isNotEmpty()) View.VISIBLE else View.GONE
        recordTimer?.cancel()
        // Keep progress bar visible while paused
        pbRecordProgress.isVisible = true
        updateSegmentsBar()
    }

    override fun onPause() {
        super.onPause()
        try {
            recordTimer?.cancel()
        } catch (_: Exception) {
        }
        if (isRecording) {
            try {
                if (recordingActive) videoCapture?.stopRecording()
            } catch (_: Exception) {
            }
            isRecording = false
            recordingActive = false
        }
    }

    private fun updateSegmentsBar() {
        val limit = recordLimitMs ?: run {
            segmentsBar.removeAllViews(); return
        }
        val ctx = segmentsBar.context
        val density = ctx.resources.displayMetrics.density
        val sepWidth = (2 * density).toInt()
        val totalElapsed = if (isRecording) {
            val nowSeg = (System.currentTimeMillis() - currentSegmentStartMs).coerceAtLeast(0L)
            totalRecordedMs + nowSeg
        } else totalRecordedMs
        segmentsBar.removeAllViews()
        // Set weightSum to limit (ms) to proportionally size blocks
        segmentsBar.weightSum = limit.toFloat()
        var acc = 0L
        // Add completed segments
        recordedSegmentDurations.forEachIndexed { idx, dur ->
            if (dur > 0) {
                val v = View(ctx)
                v.setBackgroundColor(Color.WHITE)
                v.alpha = 0.8f
                segmentsBar.addView(
                    v,
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dur.toFloat()
                    )
                )
                acc += dur
                // separator
                val sep = View(ctx)
                sep.setBackgroundColor(Color.WHITE)
                segmentsBar.addView(
                    sep,
                    LinearLayout.LayoutParams(sepWidth, LinearLayout.LayoutParams.MATCH_PARENT)
                )
            }
        }
        // Ongoing segment block (if recording or paused with current segment)
        val ongoing = (totalElapsed - acc).coerceAtLeast(0L)
        if (ongoing > 0) {
            val v = View(ctx)
            v.setBackgroundColor(Color.WHITE)
            v.alpha = 1.0f
            segmentsBar.addView(
                v,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    ongoing.toFloat()
                )
            )
        }
        // Remaining space filler (transparent or dim)
        val remain = (limit - totalElapsed).coerceAtLeast(0L)
        if (remain > 0) {
            val filler = View(ctx)
            filler.setBackgroundColor(Color.WHITE)
            filler.alpha = 0.25f
            segmentsBar.addView(
                filler,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    remain.toFloat()
                )
            )
        }
    }

    private fun showTimerDialog() {
        val maxSec = 10 * 60
        var chosen = customTimerSec.coerceIn(1, maxSec)
        fun formatDuration(sec: Int): String {
            val h = sec / 3600
            val m = (sec % 3600) / 60
            val s = sec % 60
            return when {
                h > 0 -> String.format("%d:%02d:%02d", h, m, s)
                m > 0 -> String.format("%d:%02d", m, s)
                else -> String.format("%d sec", s)
            }
        }

        val tv = TextView(requireContext()).apply {
            text = formatDuration(chosen)
            setPadding(0, 0, 0, 16)
        }
        val sb = SeekBar(requireContext()).apply {
            max = maxSec
            progress = chosen
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                    chosen = p.coerceAtLeast(1)
                    tv.text = formatDuration(chosen)
                }

                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.set_recording_timer)
            .setView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 16, 48, 16)
                setBackgroundResource(R.drawable.bg_sky_blue_gradient)
                addView(tv)
                addView(sb)
            })
            .setPositiveButton(R.string.ok) { _, _ ->
                customTimerSec = chosen
                recordLimitMs = chosen * 1000L
                startRecording()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_sky_blue_gradient)
    }

    private fun handleSelectedMedia(uris: List<Uri>) {
        if (!isAdded) return
        val ctx = context ?: return
        selectedUris.clear()
        isVideoSelected = uris.any { uri ->
            val mime = ctx.contentResolver.getType(uri)
            (mime?.startsWith("video/") == true) || (uri.path?.endsWith(".mp4") == true)
        }
        if (isVideoSelected) {
            val videoUri = uris.first { uri ->
                val mime = ctx.contentResolver.getType(uri)
                (mime?.startsWith("video/") == true) || (uri.path?.endsWith(".mp4") == true)
            }
            selectedUris.add(videoUri)
        } else {
            selectedUris.addAll(uris.take(10))
        }
        // Update edit button visibility after selection: hide for video, show for photo/text
        btnEditMediaCard.visibility =
            if (!isVideoSelected && !isVideoMode) View.VISIBLE else View.GONE
        // Proceed to next step after selection
        if (isVideoSelected) {
            showStep(layoutEdit)
            loadVideoFrameForEditing()
        } else if (selectedUris.isEmpty()) {
            showStep(layoutDetails)
            updatePostPreview()
        } else {
            showStep(layoutEdit)
            loadImageForEditing()
        }
    }

    private fun updatePostPreview() {
        try {
            when {
                isVideoSelected && selectedUris.isNotEmpty() -> {
                    val uri = selectedUris.first()
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(requireContext(), uri)
                    val bmp = retriever.getFrameAtTime(0)
                    retriever.release()
                    if (bmp != null) ivPostPreview.setImageBitmap(bmp)
                    else ivPostPreview.setImageResource(R.drawable.video)
                }

                editedBitmap != null -> {
                    ivPostPreview.setImageBitmap(editedBitmap)
                }

                selectedUris.isNotEmpty() -> {
                    val uri = selectedUris.first()
                    requireContext().contentResolver.openInputStream(uri)?.use { ins ->
                        val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                        val bmp = BitmapFactory.decodeStream(ins, null, opts)
                        ivPostPreview.setImageBitmap(bmp)
                    }
                }

                else -> {
                    // No media selected (text-only flow): keep existing or clear
                    ivPostPreview.setImageDrawable(null)
                }
            }
        } catch (_: Exception) {
        }
    }

    // Merge multiple MP4 segments (same codec) into a single MP4. Returns merged file or null on failure.
    private fun mergeSegmentsSafely(segments: List<File>): File? {
        if (segments.isEmpty()) return null
        try {
            val outFile =
                File(requireContext().cacheDir, "MERGED_${System.currentTimeMillis()}.mp4")
            val muxer = android.media.MediaMuxer(
                outFile.absolutePath,
                android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            // Determine tracks from first segment
            val first = segments.first()
            val firstExtractor = android.media.MediaExtractor()
            firstExtractor.setDataSource(first.absolutePath)
            var vTrack = -1
            var aTrack = -1
            var vFormat: android.media.MediaFormat? = null
            var aFormat: android.media.MediaFormat? = null
            for (i in 0 until firstExtractor.trackCount) {
                val fmt = firstExtractor.getTrackFormat(i)
                val mime = fmt.getString(android.media.MediaFormat.KEY_MIME)
                if (mime?.startsWith("video/") == true) {
                    vTrack = i; vFormat = fmt
                }
                if (mime?.startsWith("audio/") == true) {
                    aTrack = i; aFormat = fmt
                }
            }
            if (vFormat == null && aFormat == null) {
                firstExtractor.release(); muxer.release(); return null
            }
            var muxV = -1
            var muxA = -1
            if (vFormat != null) muxV = muxer.addTrack(vFormat!!)
            if (aFormat != null) muxA = muxer.addTrack(aFormat!!)
            muxer.start()
            firstExtractor.release()

            var vPtsOffset = 0L
            var aPtsOffset = 0L
            val buffer = java.nio.ByteBuffer.allocate(1024 * 1024)
            val info = android.media.MediaCodec.BufferInfo()

            for (file in segments) {
                val extractor = android.media.MediaExtractor()
                extractor.setDataSource(file.absolutePath)
                var thisV = -1
                var thisA = -1
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    val mime = fmt.getString(android.media.MediaFormat.KEY_MIME)
                    if (mime?.startsWith("video/") == true && vFormat != null) thisV = i
                    if (mime?.startsWith("audio/") == true && aFormat != null) thisA = i
                }
                if (thisV >= 0) extractor.selectTrack(thisV)
                if (thisA >= 0) extractor.selectTrack(thisA)

                var lastVPts = 0L
                var lastAPts = 0L
                // Interleave by track: write all video then all audio per segment (simpler, acceptable for concatenation)
                if (thisV >= 0 && muxV >= 0) {
                    while (true) {
                        info.offset = 0
                        info.size = extractor.readSampleData(buffer, 0)
                        if (info.size < 0) break
                        info.presentationTimeUs = extractor.sampleTime + vPtsOffset
                        info.flags = extractor.sampleFlags
                        muxer.writeSampleData(muxV, buffer, info)
                        lastVPts = info.presentationTimeUs
                        extractor.advance()
                    }
                    vPtsOffset = lastVPts + 1000 // +1ms gap
                }
                if (thisA >= 0 && muxA >= 0) {
                    extractor.unselectTrack(thisV.takeIf { it >= 0 } ?: 0)
                    extractor.selectTrack(thisA)
                    while (true) {
                        info.offset = 0
                        info.size = extractor.readSampleData(buffer, 0)
                        if (info.size < 0) break
                        info.presentationTimeUs = extractor.sampleTime + aPtsOffset
                        info.flags = extractor.sampleFlags
                        muxer.writeSampleData(muxA, buffer, info)
                        lastAPts = info.presentationTimeUs
                        extractor.advance()
                    }
                    aPtsOffset = lastAPts + 1000
                }
                extractor.release()
            }
            muxer.stop()
            muxer.release()
            return outFile
        } catch (_: Exception) {
            return null
        }
    }

    private fun loadImageForEditing() {
        val uri = selectedUris.firstOrNull() ?: return
        requireContext().contentResolver.openInputStream(uri)?.use { stream: InputStream ->
            originalBitmap = BitmapFactory.decodeStream(stream)
            editedBitmap = originalBitmap
            editedBitmap?.let { bitmap ->
                gpuImageView.setScaleType(GPUImage.ScaleType.CENTER_INSIDE)
                gpuImageView.setImage(bitmap)
            }
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

    private fun loadVideoFrameForEditing() {
        val uri = selectedUris.firstOrNull() ?: return
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(requireContext(), uri)
            val bmp = retriever.getFrameAtTime(0)
            retriever.release()
            if (bmp != null) {
                originalBitmap = bmp
                editedBitmap = bmp
                gpuImageView.setScaleType(GPUImage.ScaleType.CENTER_INSIDE)
                gpuImageView.setImage(bmp)
            }
        } catch (_: Exception) { }
        initialCameraFilter?.let { baseFilter = it }
        applyFilters()
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
                // Log.e(TAG, "Error decoding access token", e)
            }
        }
        lifecycleScope.launch {
            progressBar.isVisible = true
            try {
                val selectedAccessType = when (rgAccessType.checkedRadioButtonId) {
                    R.id.rbFree -> "free"
                    R.id.rbSubscriberOnly -> "subscription"
                    R.id.rbPaid -> "paid"
                    else -> "free"
                }
                val priceValue = if (selectedAccessType == "paid") {
                    etPrice.text.toString().toIntOrNull() ?: 0
                } else null
                val requiredPlanId = if (selectedAccessType == "subscription") {
                    val chosen = actvPostPlan.text?.toString()?.trim().orEmpty()
                    if (chosen.equals(
                            "All",
                            true
                        ) || chosen.isEmpty()
                    ) null else postPlanIdByName[chosen]
                } else null
                val postResp = postApi.createPost(
                    createPost = CreatePostRequest(
                        userId = userId,
                        content = content,
                        accessType = selectedAccessType,
                        price = priceValue,
                        requiredPlanId = requiredPlanId
                    )
                )
                if (!postResp.isSuccessful) {
//                    Log.e(
//                        TAG,
//                        "Failed to create post: HTTP ${postResp.code()} ${
//                            postResp.errorBody()?.string()
//                        }"
//                    )
                    Toast.makeText(requireContext(), "Failed to create post", Toast.LENGTH_SHORT)
                        .show()
                    return@launch
                }
                val posts = postResp.body().orEmpty()
                if (posts.isEmpty()) {
                    // Log.e(TAG, "CreatePost returned empty list")
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
                        // Log.e(
//                            TAG,
//                            "Failed to upsert tags: HTTP ${tagsResp.code()} ${
//                                tagsResp.errorBody()?.string()
//                            }"
//                        )
                    }
                }
                // Background media upload via WorkManager and navigate to profile
                enqueuePostUploadWork(post.id, selectedUris.toList(), editedBitmap)
                Toast.makeText(requireContext(), "Uploading in background", Toast.LENGTH_SHORT)
                    .show()
                // Return to MainActivity and open profile there (CreatePostActivity has no mainContentContainer)
                val intent = Intent(
                    requireContext(),
                    club.gifters.giftersclub.MainActivity::class.java
                )
                intent.putExtra(club.gifters.giftersclub.MainActivity.EXTRA_OPEN_PROFILE, true)
                startActivity(intent)
                requireActivity().finish()
            } catch (e: Exception) {
                // Log.e(TAG, "Failed to create post", e)
                Toast.makeText(requireContext(), "Failed to create post", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.isVisible = false
            }
        }
    }

    private fun getContentLength(uri: Uri): Long? =
        getContentLength(requireContext().applicationContext, uri)

    private fun getContentLength(ctx: Context, uri: Uri): Long? {
        return try {
            ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                val len = afd.length
                if (len > 0) return len
            }
            val cursor = ctx.contentResolver.query(
                uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (idx >= 0) {
                        val size = it.getLong(idx)
                        if (size > 0) return size
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun enqueuePostUploadWork(postId: String, uris: List<Uri>, editedBitmap: Bitmap?) {
        val appCtx = requireContext().applicationContext
        // Mark pending for current user so profile can show a banner
        val currentUid = club.gifters.giftersclub.AuthUtils.getCurrentUserId(appCtx)
        UploadTracker.addPending(appCtx, currentUid, postId)
        UploadTracker.setPendingVideo(appCtx, currentUid, isVideoSelected)
        // persist edited image (first item) if present and not a video selection
        val finalUris = uris.toMutableList()
        if (finalUris.isNotEmpty() && !isVideoSelected && editedBitmap != null) {
            try {
                val tmp = File(appCtx.cacheDir, "EDIT_${System.currentTimeMillis()}.jpg")
                FileOutputStream(tmp)
                    .use { out -> editedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                finalUris[0] = Uri.fromFile(tmp)
            } catch (_: Exception) {
            }
        }
        // Persist URI read permission for background (WorkManager) use across process restarts and older devices
        try {
            finalUris.forEach { uri ->
                if ("content".equals(uri.scheme, true)) {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
        } catch (_: Exception) {
        }
        val uriStrings = finalUris.map { it.toString() }
        val types =
            finalUris.map { appCtx.contentResolver.getType(it) ?: "application/octet-stream" }
        val data = UploadPostWorker.buildInput(postId, uriStrings, types)
        val work = androidx.work.OneTimeWorkRequestBuilder<UploadPostWorker>()
            .setInputData(data)
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()
            )
            .addTag("post-upload-${currentUid}")
            .build()
        androidx.work.WorkManager.getInstance(appCtx)
            .enqueue(work)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when {
            requestCode == REQUEST_PICK_MEDIA && resultCode == Activity.RESULT_OK -> {
                val uris = mutableListOf<Uri>()
                data?.data?.let { uris.add(it) }
                data?.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris.add(it) }
                }
                handleSelectedMedia(uris)
            }

            requestCode == UCrop.REQUEST_CROP && resultCode == Activity.RESULT_OK && data != null ->
                UCrop.getOutput(data)?.let { uri -> handleSelectedMedia(listOf(uri)) }
        }
    }
}
