package club.gifters.giftersclub.live

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.util.Base64
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.BaseActivity
import club.gifters.giftersclub.LiveKitConfig
import club.gifters.giftersclub.LiveStreamSetupBottomSheetFragment
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.CreateLiveStreamRequest
import club.gifters.giftersclub.model.Gift
import club.gifters.giftersclub.model.LiveStream
import club.gifters.giftersclub.model.LiveStreamCommentRequest
import club.gifters.giftersclub.model.LiveStreamViewerRequest
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.payments.BillingManager
import coil.load
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.imageview.ShapeableImageView
import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalAudioTrackOptions
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.LocalVideoTrackOptions
import io.livekit.android.room.track.RemoteVideoTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.NumberFormat

/**
 * Activity displaying and managing a live streaming session (camera preview, comments, and gifts).
 */
class LiveStreamActivity : BaseActivity() {

    companion object {
        private const val TAG = "LiveStreamActivity"
        private const val USE_LIVEKIT_CAMERA_PREVIEW = true
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        private const val REQUEST_CODE_PERMISSIONS = 1001
    }

    private lateinit var rvLiveComments: RecyclerView
    private lateinit var commentsAdapter: CommentsAdapter
    private lateinit var btnFollowStreamer: ImageView
    private lateinit var tvFollowerCount: TextView
    private lateinit var tvViewerCount: TextView
    private var tvTapCount: TextView? = null

    private var currentStream: LiveStream? = null
    // LiveKit room instance for host controls and realtime
    private var liveKitRoom: Room? = null
    // CameraX lens facing state for switching camera
    private var currentLensFacing = CameraSelector.LENS_FACING_FRONT
    // Microphone enabled state for mute/unmute
    private var isMicEnabled = true
    // LiveKit local preview
    private var previewView: SurfaceViewRenderer? = null
    private var isFrontFacing = true
    // Job for polling comments and gifts
    private var commentsJob: Job? = null
    private var invitePollJob: Job? = null
    private var statusJob: Job? = null
    private var giftsJob: Job? = null
    private var trackPollJob: Job? = null
    // Host aggregation disabled for simplicity; each tap writes directly via RPC
    private var isEnded: Boolean = false
    private var lastGiftAt: String? = null
    private val giftCombos = mutableMapOf<String, Pair<Int, Int>>() // key -> (count, commentIndex)
    private val processedGiftIds: java.util.LinkedHashSet<String> = object : java.util.LinkedHashSet<String>() {
        override fun add(element: String): Boolean {
            val added = super.add(element)
            if (size > 1000) {
                // remove oldest to cap memory
                val it = iterator()
                if (it.hasNext()) { it.next(); it.remove() }
            }
            return added
        }
    }
    private var paywallPlanId: String? = null
    private var paywallPlanTokens: Int = 0
    // Multi-host: map participant/track to its renderer for tiling
    private val videoViews: MutableMap<String, SurfaceViewRenderer> = mutableMapOf()
    private val tileViews: MutableMap<String, View> = mutableMapOf()
    // Whether the video container is in grid mode (local + remotes as tiles)
    private var isGridMode: Boolean = false
    // Static tiling: root of included tile layouts and per-track renderers
    private var tilesRoot: View? = null
    private val staticTrackRenderers: MutableMap<String, SurfaceViewRenderer> = mutableMapOf()

    // UI references for dynamic live stream controls
    private lateinit var liveTopBar: ConstraintLayout
    private lateinit var ivStreamerImage: ShapeableImageView
    private lateinit var tvStreamerName: TextView
    private lateinit var btnCloseLive: ImageButton
    private lateinit var btnEndLive: CardView
    private lateinit var btnSwitchCamera: ImageView
    private lateinit var btnToggleMic: ImageButton
    private lateinit var btnToggleCamera: ImageButton
    private lateinit var shareLive: ImageView
    private lateinit var btnRequestToJoin: ImageView
    private lateinit var matchOverlay: FrameLayout
    private lateinit var btnInvite: ImageView
    private lateinit var btnRequests: ImageView
    private lateinit var requestsPanel: LinearLayout

    // Battle/Match state (multi-host matches)
    private var isMatch: Boolean = false
    private var battleId: String? = null
    private var battleStartedAt: String? = null
    private var matchEndsAtIso: String? = null
    private var matchTimerJob: Job? = null
    private var matchTimerText: TextView? = null
    private val matchParticipants: MutableList<club.gifters.giftersclub.model.BattleParticipant> = mutableListOf()
    private val userIdToStreamId: MutableMap<String, String> = mutableMapOf() // also stores usernames under key "uname:<userId>"
    private val tokenTallies: MutableMap<String, Int> = mutableMapOf()

    private val lastGiftAtByStream: MutableMap<String, String?> = mutableMapOf()
    private val tokenPills: MutableMap<String, TextView> = mutableMapOf()
    private val namePills: MutableMap<String, TextView> = mutableMapOf()
    // Client-side options hydrated from currentStream
    private var commentScope: String = "shared" // or "isolated"
    private val maxHosts: Int get() = currentStream?.layoutMaxHosts ?: 8
    // Temporary stabilization: prefer showing a single remote tile (viewer) by default
    private var preferSingleRemote: Boolean = true
    private var overflowBadge: TextView? = null
    // If we request permissions while attempting to resume a host/co-host session,
    // stash the target stream here and continue after the user grants.
    private var pendingResumeLive: LiveStream? = null

    // Likes/Taps
    private var likeCommentSent = false
    private var localTapCount = 0
    private var tapHud: LinearLayout? = null
    private var tapHudProgress: ProgressBar? = null
    private var isLocallyTapping = false
    private var tapsProgressBar: ProgressBar? = null
    private var tapsCountTv: TextView? = null
    private var tapsFractionTv: TextView? = null
    private var pendingLocalTapIncrements: Int = 0
    private var lastTapsValue: Int = 0
    // Queue taps that happen before stream/session is ready; flushed once ready
    private var queuedTapCount: Int = 0

    private fun startCommentsPolling(sid: String) {
        commentsJob?.cancel()
        commentsJob = lifecycleScope.launch {
            while (isActive && !isEnded) {
                delay(2000)
                // Determine scope: shared -> merge by room_id, isolated -> just this stream
                val streamIds = if (commentScope == "shared") {
                    try {
                        val rows = RetrofitClient.liveStreamApi.getLiveStreamById("id,room_id", "eq.$sid")
                        val room = rows.firstOrNull()?.roomId
                        if (!room.isNullOrEmpty()) RetrofitClient.liveStreamApi.getLiveStreamsByRoomId("id", "eq.$room").map { it.id } else listOf(sid)
                    } catch (_: Exception) { listOf(sid) }
                } else listOf(sid)
                val merged = mutableListOf<club.gifters.giftersclub.model.LiveStreamComment>()
                for (s in streamIds) {
                    try {
                        merged += RetrofitClient.liveStreamApi.getLiveStreamComments("*,profile:profiles(*)", "eq.$s")
                        // also poll gifts per stream to maintain token tallies for overlays
                        val since = lastGiftAtByStream[s]?.let { "gt.$it" }
                        val events = RetrofitClient.liveStreamApi.getGiftEvents(
                            streamFilter = "eq.$s",
                            createdAfterFilter = since
                        )
                        if (events.isNotEmpty()) {
                            lastGiftAtByStream[s] = events.last().createdAt
                            events.forEach { e ->
                                val rid = e.recipientId
                                val inc = e.tokensUsed ?: 0
                                tokenTallies[rid] = (tokenTallies[rid] ?: 0) + inc
                                // update pill if visible
                                tokenPills[rid]?.text = (tokenTallies[rid] ?: 0).toString()
                            }
                        }
                    } catch (_: Exception) {}
                }
                val ordered = merged.sortedBy { it.createdAt }
                commentsAdapter.submitList(ordered)
                if (ordered.isNotEmpty()) rvLiveComments.scrollToPosition(ordered.size - 1)
                // Spawn hearts for other users' like comment
                ordered.lastOrNull()?.let { last ->
                    if (last.content.trim().equals("liked this live", ignoreCase = true)) {
                        // burst hearts from bottom-right
                        val root = this@LiveStreamActivity.findViewById<FrameLayout>(R.id.flLiveStream)
                        val startX = root.width - 48f
                        val startY = root.height - 220f
                        for (i in 0 until 6) {
                            root.postDelayed({ spawnHeart(startX - (0..40).random(), startY - (0..20).random()) }, (i * 60).toLong())
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_stream)
        // Deep-link support: if URL is https://gifters.club/live/{streamId}
        val deepId = intent.data?.lastPathSegment?.takeIf { it.isNotEmpty() }
        deepId?.let { handleDeepLinkStream(it) }
        // comments list overlay (bottom-up) – max half-screen height, bring above video
        rvLiveComments = findViewById<RecyclerView>(R.id.rvLiveComments).also { rv ->
            commentsAdapter = CommentsAdapter()
            rv.layoutManager = LinearLayoutManager(this).apply {
                reverseLayout = false
                stackFromEnd = true
            }
            rv.adapter = commentsAdapter
            // limit height to half screen
            val half = resources.displayMetrics.heightPixels / 2
            rv.layoutParams.height = half
            rv.bringToFront()
        }
        // Top bar for streamer details (hidden until stream starts)
        liveTopBar = findViewById(R.id.liveTopBar)
        liveTopBar.bringToFront()
        liveTopBar.visibility = View.GONE
        findViewById<LinearLayout>(R.id.liveBottomBar).bringToFront()

        ivStreamerImage = findViewById(R.id.ivStreamerImage)
        tvStreamerName = findViewById(R.id.tvStreamerName)
        btnCloseLive = findViewById(R.id.btnCloseLive)
        btnEndLive = findViewById(R.id.btnEndLive)
        // follower count & follow button
        tvFollowerCount = findViewById(R.id.tvFollowerCount)
        tvViewerCount = findViewById(R.id.tvViewerCount)
        btnFollowStreamer = findViewById(R.id.btnFollowStreamer)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        btnToggleMic = findViewById(R.id.btnToggleMic)
        btnToggleCamera = findViewById<ImageButton>(R.id.btnToggleCamera)
        shareLive = findViewById(R.id.shareLive)
        btnRequestToJoin = findViewById(R.id.btnRequestToJoin)
        matchOverlay = findViewById(R.id.matchOverlay)
        btnInvite = ImageView(this).apply {
            setImageResource(R.drawable.user_group)
            layoutParams = ConstraintLayout.LayoutParams(48,48).apply {
                (this as ConstraintLayout.LayoutParams).endToStart = R.id.btnEndLive
                (this as ConstraintLayout.LayoutParams).topToTop = R.id.streamerDetails
                setMargins(8,0,8,0)
            }
            visibility = View.GONE
        }
        findViewById<ConstraintLayout>(R.id.liveTopBar).addView(btnInvite)
        btnInvite.setOnClickListener {
            val sid = currentStream?.id ?: return@setOnClickListener
            val hostId = AuthUtils.getCurrentUserId(this) ?: return@setOnClickListener
            val sheet = MatchSetupBottomSheetFragment.newInstance(sid, hostId)
            sheet.show(supportFragmentManager, "MatchSetupBottomSheet")
        }
        // Add tap HUD (legacy) and set up tap capture
        run {
            val root = findViewById<FrameLayout>(R.id.flLiveStream)
            tapHud = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(16, 8, 16, 8)
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 48f
                    setColor(0x59000000) // black with alpha
                }
                alpha = 0.95f
                visibility = View.GONE
                val heart = TextView(this@LiveStreamActivity).apply { text = "❤"; textSize = 16f; setTextColor(0xFFFF0000.toInt()) }
                val progress = ProgressBar(this@LiveStreamActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 300
                    progress = 0
                    layoutParams = LinearLayout.LayoutParams(200, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(12, 0, 0, 0) }
                }
                addView(heart)
                addView(progress)
                tapHudProgress = progress
            }
            val hudParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            hudParams.topMargin = (48 * resources.displayMetrics.density).toInt()
            hudParams.leftMargin = (12 * resources.displayMetrics.density).toInt()
            tapHud?.layoutParams = hudParams
            root.addView(tapHud)
            root.setOnTouchListener { _, ev ->
                if (ev.action == android.view.MotionEvent.ACTION_DOWN) { handleTap(ev.x, ev.y) }
                false
            }
        }
        // Pending requests indicator for host (tap opens requests dialog)
        btnRequests = findViewById(R.id.btnRequests)
        // Bind tapsDetails group from layout (heart + progress + count under streamer box)
        run {
            val tapsRow = findViewById<LinearLayout>(R.id.tapsDetails)
            tapsProgressBar = findViewById(R.id.tapsProgress)
            tapsCountTv = findViewById(R.id.tapsCount)
            tapsProgressBar?.max = 300
            tapsProgressBar?.progress = 0
            tapsProgressBar?.visibility = View.GONE
            tapsCountTv?.text = formatCount(0)
            // Add fraction text after progress (local-only visibility)
            tapsFractionTv = TextView(this).apply {
                setTextColor(0xFFFFFFFF.toInt()); textSize = 11f; text = ""
                visibility = View.GONE
            }
            // Insert fraction view right after progress bar
            val progIndex = tapsRow?.indexOfChild(tapsProgressBar) ?: -1
            if (tapsRow != null && progIndex >= 0) tapsRow.addView(tapsFractionTv, progIndex + 1)
            // Use tapsCount view as the total taps display
            tvTapCount = tapsCountTv
        }
        // Requests panel overlay (hidden until tapped) - inflate from XML for styling
        // Deprecated overlay panel replaced by bottom sheet dialog
        requestsPanel = LinearLayout(this) // placeholder; not used for UI anymore
        btnRequests.setOnClickListener { openRequestsBottomSheet() }

        if (deepId == null) {
            // If user already has an active live, resume it instead of creating a new one.
            val currentUser = AuthUtils.getCurrentUserId(this)
            if (currentUser != null) {
                lifecycleScope.launch {
                    val active = try {
                        RetrofitClient.liveStreamApi.getLiveStreamsByHosts(
                            select = "id,status",
                            hostFilter = "eq.$currentUser",
                            statusFilter = "eq.live",
                            order = "updated_at.desc"
                        ).firstOrNull()
                    } catch (_: Exception) { null }
                    if (active != null) {
                        handleDeepLinkStream(active.id)
                    } else {
                        if (!allPermissionsGranted()) {
                            ActivityCompat.requestPermissions(
                                this@LiveStreamActivity,
                                REQUIRED_PERMISSIONS,
                                REQUEST_CODE_PERMISSIONS
                            )
                        } else {
                            showCreateStreamDialog()
                        }
                    }
                }
            } else {
                if (!allPermissionsGranted()) {
                    ActivityCompat.requestPermissions(
                        this,
                        REQUIRED_PERMISSIONS,
                        REQUEST_CODE_PERMISSIONS
                    )
                } else {
                    showCreateStreamDialog()
                }
            }
        }

        // Request to join (viewer only)
        btnRequestToJoin.visibility = View.GONE
        btnRequestToJoin.setOnClickListener {
            currentStream?.id?.let { sid ->
                lifecycleScope.launch {
                    try { RetrofitClient.functionsApi.liveInvite(mapOf("action" to "request", "streamId" to sid)) } catch (_: Exception) {}
                    Toast.makeText(this@LiveStreamActivity, "Requested to join", Toast.LENGTH_SHORT).show()
                    btnRequestToJoin.visibility = View.GONE
                    startInvitePolling(sid)
                }
            }
        }

        // Bottom sheet for gifts, hidden initially until user clicks gift icon
        val btnOpenGifts = findViewById<ImageView>(R.id.btnOpenGifts)
        val flGiftsBottomSheet = findViewById<FrameLayout>(R.id.flGiftsBottomSheet)
        val overlayDim = findViewById<View>(R.id.overlayDim)
        val giftsBottomSheetBehavior = BottomSheetBehavior.from(flGiftsBottomSheet).apply {
            isHideable = true
            isDraggable = false // keep steady; dismiss only on outside tap
            state = BottomSheetBehavior.STATE_HIDDEN
            addBottomSheetCallback(object: BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                        overlayDim.visibility = View.GONE
                    } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                        // Force max height = half screen
                        state = BottomSheetBehavior.STATE_HALF_EXPANDED
                    } else {
                        overlayDim.visibility = View.VISIBLE
                    }
                }
                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    // keep dim visible while open
                    overlayDim.visibility = if (state == BottomSheetBehavior.STATE_HIDDEN) View.GONE else View.VISIBLE
                }
            })
        }
        overlayDim.setOnClickListener { giftsBottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN }
        btnOpenGifts.setOnClickListener {
            giftsBottomSheetBehavior.state = if (giftsBottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN)
                BottomSheetBehavior.STATE_HALF_EXPANDED else BottomSheetBehavior.STATE_HIDDEN
        }

        // Ensure tiles root is ready after container is measured (no dynamic layout calculations)
        try {
            val container = findViewById<FrameLayout>(R.id.flLiveStream)
            container.viewTreeObserver.addOnGlobalLayoutListener(object: android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (container.width > 0 && container.height > 0) {
                        ensureTilesLayout(container)
                        container.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    }
                }
            })
        } catch (_: Exception) {}

        // End stream when user taps close; ask for confirmation
        val endDialog = AlertDialog.Builder(this)
            .setTitle(R.string.end_live_stream)
            .setMessage(R.string.confirm_end_live_stream)
            .setPositiveButton(R.string.yes) { _, _ -> endLiveSession() }
            .setNegativeButton(R.string.no, null)
        btnEndLive.setOnClickListener { endDialog.show() }
        btnCloseLive.setOnClickListener { endDialog.show() }

        // Enter key sends comment
        val etLiveComment = findViewById<EditText>(R.id.etLiveComment)
        etLiveComment.imeOptions = EditorInfo.IME_ACTION_SEND
        etLiveComment.setRawInputType(InputType.TYPE_CLASS_TEXT)
        etLiveComment.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                if (!isEnded) sendLiveComment()
                true
            } else {
                false
            }
        }

        // Existing UI setup for gifts carousel, comments, and viewer count
        val rvLiveGifts = findViewById<RecyclerView>(R.id.rvLiveGifts)
        rvLiveGifts.layoutManager = GridLayoutManager(this, 4)
        val giftsAdapter = LiveGiftAdapter { gift: Gift ->
            lifecycleScope.launch { sendGiftToLive(gift) }
        }
        rvLiveGifts.adapter = giftsAdapter
        // fetch gifts and retain original order
        var originalGifts: List<Gift> = emptyList()
        lifecycleScope.launch {
            try {
                originalGifts = RetrofitClient.giftApi.getGifts(
                    order = "is_popular.desc",
                    limit = 100,
                    offset = 0
                )
                giftsAdapter.submitList(originalGifts)
            } catch (_: Exception) {}
        }

        // Spinner filter like Angular: All, Popular, Cheapest, Most Expensive, Special
        val spGiftSort = findViewById<android.widget.Spinner>(R.id.spGiftSort)
        val sortAdapter = android.widget.ArrayAdapter.createFromResource(
            this,
            club.gifters.giftersclub.R.array.gift_sort_entries,
            android.R.layout.simple_spinner_item
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        spGiftSort.adapter = sortAdapter
        spGiftSort.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                val choice = parent.getItemAtPosition(position).toString().lowercase()
                val list = when (choice) {
                    "popular" -> originalGifts.sortedByDescending { it.isPopular }
                    "cheapest" -> originalGifts.sortedBy { it.tokens }
                    "most expensive" -> originalGifts.sortedByDescending { it.tokens }
                    "special" -> originalGifts.filter { it.isFeatured }
                    else -> originalGifts
                }
                giftsAdapter.submitList(list)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) { /* no-op */ }
        }

        // Setup recharge button with user token balance
        val rechargeContainer = findViewById<ConstraintLayout>(R.id.btnRechargeTokens)
        val tvRechargeText = findViewById<TextView>(R.id.btnRechargeTokensText)
        var userId = ""
        getSharedPreferences("supabase", Context.MODE_PRIVATE)
            .getString("access_token", "")
            ?.takeIf { it.isNotBlank() }
            ?.let {
                val parts = it.split('.')
                if (parts.size > 1) {
                    val decoded = String(Base64.decode(parts[1], Base64.URL_SAFE))
                    userId = JSONObject(decoded).optString("sub")
                }
            }
        lifecycleScope.launch {
            try {
                val profiles = RetrofitClient.profileApi.getProfileByUserId(userIdFilter = "eq.$userId")
                if (profiles.isNotEmpty()) {
                    val balance = profiles[0].tokenBalance ?: 0
                    tvRechargeText.text = if (balance > 0)
                        NumberFormat.getInstance().format(balance)
                    else getString(R.string.recharge)
                }
            } catch (_: Exception) {}
        }
        rechargeContainer.setOnClickListener {
            showBuyTokensDialog(userId)
        }
        shareLive.setOnClickListener {
            // Share live stream link via intent
            val streamId = currentStream?.id ?: return@setOnClickListener
            val shareText = "Watch my live stream: https://gifters.club/live/$streamId"
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_live_stream)))
        }
    }

    private fun showBuyTokensDialog(userId: String) {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.enter_token_amount)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.buy_tokens)
            .setView(input)
            .setPositiveButton(R.string.buy) { _, _ ->
                val amount = input.text.toString().toIntOrNull()
                if (amount == null || amount <= 0) {
                    Toast.makeText(this, R.string.invalid_amount, Toast.LENGTH_SHORT).show()
                } else {
                    initiateTopup(userId, amount)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                // If we were resuming a live session (host/co-host), continue that now.
                pendingResumeLive?.let { live ->
                    pendingResumeLive = null
                    resumeHostSession(live)
                    return
                }
                // Otherwise proceed to creation flow.
                showCreateStreamDialog()
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted() =
        REQUIRED_PERMISSIONS.all { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }

    private fun showCreateStreamDialog() {
        LiveStreamSetupBottomSheetFragment()
            .show(supportFragmentManager, LiveStreamSetupBottomSheetFragment.TAG)
    }

    /**
     * Initialize viewer mode: show host info, comments, and subscribe to live video.
     */
    private fun initViewer() {
        btnSwitchCamera.visibility = View.GONE
        btnToggleCamera.visibility = View.GONE
        btnToggleMic.visibility = View.GONE
        btnEndLive.visibility = View.GONE
        btnCloseLive.visibility = View.GONE
        // Show host top bar
        liveTopBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            // Fetch and display host profile
            val hostId = currentStream?.hostId ?: return@launch
            val profiles = RetrofitClient.profileApi.getProfileByUserId("*", "eq.$hostId")
            if (profiles.isNotEmpty()) {
                val p = profiles[0]
                tvStreamerName.text = (p.name ?: p.username).take(14)
                ivStreamerImage.load(p.image)
            }
            val tempFollowerCount = profiles.getOrNull(0)?.followersCount?.let { formatCount(it) }
                ?: formatCount(0)
            tvFollowerCount.text = getString(R.string.follower_count, tempFollowerCount)
            // Follow/unfollow & request-to-join for viewer
            val currentId = AuthUtils.getCurrentUserId(this@LiveStreamActivity)
            if (currentId != null && currentId != hostId) {
                val header = RetrofitClient.followsApi.isFollowingUser(
                    followedIdFilter = "eq.$hostId", followerIdFilter = "eq.$currentId"
                ).headers()["Content-Range"]
                var isFollowing = (header?.substringAfterLast("/")?.toIntOrNull() ?: 0) > 0
                btnFollowStreamer.apply {
                    visibility = View.VISIBLE
                    setImageResource(if (isFollowing) R.drawable.unfollow else R.drawable.follow)
                    setOnClickListener {
                        lifecycleScope.launch {
                            if (isFollowing) {
                                RetrofitClient.followsApi.unfollowUser(
                                    followedIdFilter = "eq.$hostId", followerIdFilter = "eq.$currentId"
                                )
                            } else {
                                RetrofitClient.followsApi.followUser(
                                    mapOf("followed_id" to hostId, "follower_id" to currentId)
                                )
                            }
                            // Reload to update follower count and follow state
                            val updated = RetrofitClient.profileApi.getProfileByUserId(
                                select = "*", userIdFilter = "eq.$hostId"
                            ).firstOrNull()
                            if (updated != null) {
                                tvFollowerCount.text = getString(R.string.follower_count, formatCount(updated.followersCount ?: 0))
                                isFollowing = updated.isFollowing ?: false
                                setImageResource(if (isFollowing) R.drawable.unfollow else R.drawable.follow)
                            }
                        }
                    }
                }
                // Show join request icon for non-battle streams (heuristic: always show for now)
                btnRequestToJoin.visibility = View.VISIBLE
            } else {
                btnFollowStreamer.visibility = View.GONE
                btnRequestToJoin.visibility = View.GONE
            }
            // Host: tap viewerCount to open pending requests dialog
            if (currentId != null && currentId == hostId) {
                // Always show requests icon for host; tint red when there are pending requests
                btnRequests.visibility = View.VISIBLE
                tvViewerCount.setOnClickListener { openRequestsBottomSheet() }
                // Poll pending requests and show indicator
                lifecycleScope.launch {
                    while (isActive && !isEnded) {
                        delay(3000)
                        try {
                            val resp = RetrofitClient.functionsApi.liveInviteRaw(mapOf("action" to "list", "streamId" to (currentStream?.id ?: return@launch)))
                            if (!resp.isSuccessful) continue
                            val body = resp.body()?.string() ?: "[]"
                            val arr = org.json.JSONArray(body)
                            val pending = (0 until arr.length()).count {
                                val st = arr.getJSONObject(it).optString("status").lowercase()
                                st == "requested" || st == "pending" || st == "request"
                            }
                            if (pending > 0) {
                                btnRequests.setColorFilter(android.graphics.Color.RED)
                            } else {
                                btnRequests.clearColorFilter()
                            }
                        } catch (_: Exception) {}
                    }
                }
                // Show invite icon for host always (start/manage match)
                btnInvite.visibility = View.VISIBLE
            }
            // Load and show comments
            currentStream?.id?.let { sid ->
                val initial = RetrofitClient.liveStreamApi.getLiveStreamComments(
                    select = "*,profile:profiles(*)",
                    streamFilter = "eq.$sid"
                )
                val initialSorted = initial.sortedBy { it.createdAt }
                commentsAdapter.submitList(initialSorted)
                if (initialSorted.isNotEmpty()) rvLiveComments.scrollToPosition(initialSorted.size - 1)
                startCommentsPolling(sid)
            }
        }

        // Prepare video container
        val container = findViewById<FrameLayout>(R.id.flLiveStream)
        container.removeAllViews()
        // Clear both dynamic and static renderers
        videoViews.values.forEach { it.release() }
        videoViews.clear()
        tilesRoot = null
        staticTrackRenderers.values.forEach { try { it.release() } catch (_: Exception) {} }
        staticTrackRenderers.clear()

        // Connect to LiveKit as viewer (subscribe only)
        val lkToken = currentStream?.token ?: currentStream?.id
        if (!lkToken.isNullOrBlank()) {
            val roomOptions = RoomOptions(
                /*publishAudio=*/false,
                /*publishVideo=*/false,
                null,
                LocalAudioTrackOptions(),
                LocalVideoTrackOptions(),
                null,
                null
            )
            val room = LiveKit.create(
                this@LiveStreamActivity,
                roomOptions,
                LiveKitOverrides()
            )
            lifecycleScope.launch {
                try {
                    room.connect(
                        LiveKitConfig.WS_URL,
                        lkToken,
                        ConnectOptions( /* autoSubscribe = */ true )
                    )
                    liveKitRoom = room
                    // Listen for data messages for realtime tallies
                    launch {
                        room.events.collect { evt ->
                            if (evt is RoomEvent.DataReceived) {
                                try {
                                    val txt = String(evt.data, Charsets.UTF_8)
                                    val obj = org.json.JSONObject(txt)
                                    if (obj.optString("type") == "gift_delta") {
                                        val rid = obj.optString("recipient_id")
                                        val inc = obj.optInt("tokens_used", 0)
                                        tokenTallies[rid] = (tokenTallies[rid] ?: 0) + inc
                                        renderMatchOverlay()
                                    } else if (obj.optString("type") == "tap") {
                                        val root = findViewById<FrameLayout>(R.id.flLiveStream)
                                        val startX = root.width - 48f
                                        val startY = root.height - 220f
                                        for (i in 0 until 6) {
                                            root.postDelayed({ spawnHeart(startX - (0..40).random(), startY - (0..20).random()) }, (i * 50).toLong())
                                        // Near-instant total bump for all clients (authoritative reconciliation via polling)
                                        lastTapsValue += 1
                                        tvTapCount?.text = formatCount(lastTapsValue)
                                        tapsCountTv?.text = formatCount(lastTapsValue)
                                    }
                                    }
                                } catch (_: Exception) { }
                            }
                        }
                    }
                    // Attach already-subscribed remote videos into static tiles
                    updateStaticTiles(container, includeLocal = false)
                    // Poll briefly for initial tracks to reduce first-frame delay on Android
                    trackPollJob?.cancel()
                    trackPollJob = launch {
                        repeat(30) { // ~15s max
                            try {
                                val hasRemote = room.remoteParticipants.values.any { p -> p.videoTrackPublications.isNotEmpty() }
                                updateStaticTiles(container, includeLocal = false)
                                if (hasRemote) return@launch
                            } catch (_: Exception) {}
                            delay(500)
                        }
                    }
                    // Send a 'hello' identity message so viewers can label tiles accurately
                    try {
                        val uid = AuthUtils.getCurrentUserId(this@LiveStreamActivity) ?: ""
                        val uname = try {
                            val prof = if (uid.isNotEmpty()) RetrofitClient.profileApi.getProfileByUserId("*", "eq.$uid").firstOrNull() else null
                            prof?.username ?: ""
                        } catch (_: Exception) { "" }
                        val json = org.json.JSONObject().apply {
                            put("type", "hello"); put("user_id", uid); put("username", uname)
                        }
                        // Publish as reliable by default (SDK default)
                        room.localParticipant.publishData(
                            json.toString().toByteArray(Charsets.UTF_8)
                        )
                    } catch (_: Exception) {}

                    // Listen for subscribe/unsubscribe/data to manage tiles and labels
                    launch {
                        room.events.collect { event ->
                            when (event) {
                                is RoomEvent.TrackSubscribed, is RoomEvent.TrackPublished,
                                is RoomEvent.ParticipantConnected -> {
                                    updateStaticTiles(container, includeLocal = false)
                                }
                                is RoomEvent.DataReceived -> {
                                    try {
                                        val txt = String(event.data, Charsets.UTF_8)
                                        val obj = org.json.JSONObject(txt)
                                if (obj.optString("type") == "hello") {
                                    val helloUid = obj.optString("user_id")
                                    val helloName = obj.optString("username")
                                    if (helloUid.isNotEmpty()) {
                                        userIdToStreamId["uname:" + helloUid] = if (helloName.isNotEmpty()) helloName else helloUid.take(6)
                                        namePills[helloUid]?.text = "@" + (userIdToStreamId["uname:" + helloUid] ?: helloUid.take(6))
                                    }
                                } else if (obj.optString("type") == "tap") {
                                    val root = findViewById<FrameLayout>(R.id.flLiveStream)
                                    val startX = root.width - 48f
                                    val startY = root.height - 220f
                                    for (i in 0 until 6) {
                                        root.postDelayed({ spawnHeart(startX - (0..40).random(), startY - (0..20).random()) }, (i * 50).toLong())
                                    }
                                    // Near-instant total bump; prefer provided total if present
                                    val t = obj.optInt("t", -1)
                                    if (t >= 0) lastTapsValue = t else lastTapsValue += 1
                                    tvTapCount?.text = formatCount(lastTapsValue)
                                    tapsCountTv?.text = formatCount(lastTapsValue)
                                }
                                    } catch (_: Exception) { }
                                }
                                is RoomEvent.TrackUnsubscribed, is RoomEvent.ParticipantDisconnected -> {
                                    updateStaticTiles(container, includeLocal = false)
                                }
                                else -> Unit
                            }
                        }
                    }
                    // Start status polling to update viewer/taps and exit when stream ends
                    val sid = currentStream?.id
                    if (sid != null) {
                        statusJob?.cancel()
                        statusJob = launch {
                            while (isActive && !isEnded) {
                                delay(3000)
                                try {
                                    val rows = RetrofitClient.liveStreamApi.getLiveStreamById("id,status,viewer_count,taps", "eq.$sid")
                                    val row = rows.firstOrNull()
                                    if (row != null) {
                                        tvViewerCount.text = (row.viewerCount).toString()
                                        // Update taps and spawn remote hearts for others (consuming local pending increments)
                                        val prev = lastTapsValue
                                        val taps = row.taps ?: 0
                                        tvTapCount?.text = formatCount(taps)
                                        tapsCountTv?.text = formatCount(taps)
                                        var delta = taps - prev
                                        lastTapsValue = taps
                                        if (delta > 0) {
                                            val consume = kotlin.math.min(delta, pendingLocalTapIncrements)
                                            pendingLocalTapIncrements -= consume
                                            delta -= consume
                                            if (delta > 0) {
                                                val root = findViewById<FrameLayout>(R.id.flLiveStream)
                                                val startX = root.width - 48f
                                                val startY = root.height - 220f
                                                for (i in 0 until kotlin.math.min(6, delta)) {
                                                    root.postDelayed({ spawnHeart(startX - (0..40).random(), startY - (0..20).random()) }, (i * 60).toLong())
                                                }
                                            }
                                        }
                                        if (row.status != "live") {
                                            isEnded = true
                                            try { liveKitRoom?.disconnect() } catch (_: Exception) {}
                                            commentsJob?.cancel(); commentsJob = null
                                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                Toast.makeText(this@LiveStreamActivity, R.string.stream_not_found, Toast.LENGTH_SHORT).show()
                                                finish()
                                            }
                                        }
                                    }
                                } catch (_: Exception) { }
                            }
                        }
                        // Gifts polling: convert gift events into comment lines with combo aggregation
                        giftsJob?.cancel(); giftsJob = launch {
                            while (isActive && !isEnded) {
                                delay(2000)
                                try {
                                    val since = lastGiftAt?.let { "gt.$it" }
                                    val events = RetrofitClient.liveStreamApi.getGiftEvents(
                                        streamFilter = "eq.$sid",
                                        createdAfterFilter = since
                                    )
                                    if (events.isNotEmpty()) {
                                        lastGiftAt = events.last().createdAt
                                        events.forEach { e ->
                                            if (!processedGiftIds.add(e.id)) {
                                                return@forEach
                                            }
                                            val giftName = e.gift?.name ?: "gift"
                                            val key = e.gifterId + "_" + e.giftId
                                            val combo = giftCombos[key]
                                            if (combo != null) {
                                                val newCount = combo.first + 1
                                                val idx = combo.second
                                                commentsAdapter.updateContentAt(idx, "sent ${newCount} $giftName combo")
                                                giftCombos[key] = Pair(newCount, idx)
                                                // Try resolve animation rule for combo
                                                val anim = resolveAnimationForGift(e.giftId, (e.tokensUsed ?: 0), newCount)
                                                if (anim != null) {
                                                    Toast.makeText(this@LiveStreamActivity, "Animation: ${anim.first}", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                commentsAdapter.addSyntheticComment(e.gifterId, "sent a $giftName", e.gifter)
                                                val idx = commentsAdapter.itemCount - 1
                                                giftCombos[key] = Pair(1, idx)
                                                val anim = resolveAnimationForGift(e.giftId, (e.tokensUsed ?: 0), 1)
                                                if (anim != null) {
                                                    Toast.makeText(this@LiveStreamActivity, "Animation: ${anim.first}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                        rvLiveComments.scrollToPosition(commentsAdapter.itemCount - 1)
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun addVideoTile(container: FrameLayout, key: String, track: RemoteVideoTrack) {
        if (videoViews.containsKey(key)) return
        val v = SurfaceViewRenderer(this)
        v.setScalingType(livekit.org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        v.setEnableHardwareScaler(true)
        try { liveKitRoom?.initVideoRenderer(v) } catch (_: Exception) {}
        // For remote videos, draw as media overlay to ensure visibility above other SurfaceViews
        v.setZOrderMediaOverlay(true)
        val tile = FrameLayout(this)
        tile.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        val tileLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        tile.layoutParams = tileLp
        // set border (1dp gray for non-match; colored when match)
        val strokeColor = if (isMatch) android.graphics.Color.TRANSPARENT else android.graphics.Color.parseColor("#cbd5e1")
        val gd = android.graphics.drawable.GradientDrawable()
        gd.setColor(0x00000000)
        gd.setStroke((resources.displayMetrics.density).toInt(), strokeColor)
        gd.cornerRadius = 8 * resources.displayMetrics.density
        tile.background = gd
        tile.addView(v, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        // overlay pills container
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = android.view.Gravity.BOTTOM
                bottomMargin = (8 * resources.displayMetrics.density).toInt()
                marginStart = (8 * resources.displayMetrics.density).toInt()
                marginEnd = (8 * resources.displayMetrics.density).toInt()
            }
            setPadding(0,0,0,0)
            weightSum = 1f
        }
        val name = TextView(this).apply {
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(0xE0D946EF.toInt()) // approx pink-500 with opacity
            textSize = 12f
            setPadding(12,6,12,6)
        }
        val tokens = TextView(this).apply {
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(0xE060A5FA.toInt()) // approx blue-400 with opacity
            textSize = 12f
            setPadding(12,6,12,6)
        }
        val leftLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { weight = 0f }
        val rightLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            weight = 0f
            gravity = android.view.Gravity.END
        }
        overlay.addView(name, leftLp)
        overlay.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f)) // spacer
        overlay.addView(tokens, rightLp)
        tile.addView(overlay)
        // Try to resolve participant identity -> userId for pill data
        val parts = key.split("_")
        val participantSid = parts.firstOrNull() ?: ""
        val uid = try { liveKitRoom?.remoteParticipants?.values?.firstOrNull { it.sid.toString() == participantSid }?.identity?.toString() ?: "" } catch (_: Exception) { "" }
        val userId = if (uid.contains("-")) uid.substringAfterLast("-") else uid
        if (userId.isNotEmpty()) {
            name.text = "@${userIdToStreamId["uname:" + userId] ?: userId.take(6)}"
            tokens.text = (tokenTallies[userId] ?: 0).toString()
            tokenPills[userId] = tokens
            namePills[userId] = name
        }
        runOnUiThread {
            if (preferSingleRemote) {
                // Remove any existing remote tiles so only one remote video is shown
                val toRemove = videoViews.keys.filter { !it.startsWith("local_") && it != key }
                toRemove.forEach { k -> removeVideoTile(container, k) }
            }
            container.addView(tile)
            // Defer layout until after the container has a size
            container.post { layoutTiles(container) }
            videoViews[key] = v
            tileViews[key] = tile
            track.addRenderer(v)
        }
    }

    private suspend fun resolveAnimationForGift(
        giftId: String,
        tokensUsed: Int = 0,
        comboCount: Int = 1
    ): Pair<String, String>? {
        val scope = when {
            isMatch -> "match"
            (currentStream?.roomId?.isNotEmpty() == true) -> "multi_host"
            else -> "solo"
        }
        return try {
            val rules = RetrofitClient.liveStreamApi.getGiftAnimationRules(
                giftIdFilter = "eq.$giftId",
                scopeFilter = "in.(all,$scope)"
            )
            val chosen = rules.firstOrNull { r ->
                val min = (r["min_tokens"] as? Double)?.toInt() ?: 0
                val combo = (r["combo_count"] as? Double)?.toInt() ?: 1
                tokensUsed >= min && comboCount >= combo
            } ?: rules.firstOrNull()
            val anim = (chosen?.get("animation") as? Map<*, *>) ?: return null
            val type = anim["type"] as? String ?: return null
            val url = anim["url"] as? String ?: return null
            type to url
        } catch (_: Exception) { null }
    }

    private fun addLocalTile(container: FrameLayout) {
        val room = liveKitRoom ?: return
        val localPubPair = room.localParticipant.videoTrackPublications.firstOrNull() ?: return
        val localTrack = localPubPair.second as? LocalVideoTrack ?: return
        val key = "local_${localTrack.sid ?: "cam"}"
        if (videoViews.containsKey(key)) return
        val v = SurfaceViewRenderer(this)
        v.setScalingType(livekit.org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        v.setEnableHardwareScaler(true)
        try { room.initVideoRenderer(v) } catch (_: Exception) {}
        // Keep normal Z-order for stability across devices
        v.setZOrderMediaOverlay(false)
        // Mirror only for front-facing camera so host sees a natural preview
        v.setMirror(isFrontFacing)
        val tile = FrameLayout(this)
        tile.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        val gd = android.graphics.drawable.GradientDrawable()
        gd.setColor(0x00000000)
        gd.setStroke((resources.displayMetrics.density).toInt(), android.graphics.Color.parseColor("#cbd5e1"))
        gd.cornerRadius = 8 * resources.displayMetrics.density
        tile.background = gd
        tile.addView(v, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        // overlay pills for local user
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = android.view.Gravity.BOTTOM
                bottomMargin = (8 * resources.displayMetrics.density).toInt()
                marginStart = (8 * resources.displayMetrics.density).toInt()
                marginEnd = (8 * resources.displayMetrics.density).toInt()
            }
        }
        val name = TextView(this).apply {
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(0xE0D946EF.toInt())
            textSize = 12f
            setPadding(12,6,12,6)
        }
        val tokens = TextView(this).apply {
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(0xE060A5FA.toInt())
            textSize = 12f
            setPadding(12,6,12,6)
        }
        overlay.addView(name)
        overlay.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        overlay.addView(tokens)
        tile.addView(overlay)
        val me = AuthUtils.getCurrentUserId(this) ?: ""
        if (me.isNotEmpty()) {
            name.text = "@${userIdToStreamId["uname:" + me] ?: me.take(6)}"
            tokens.text = (tokenTallies[me] ?: 0).toString()
            tokenPills[me] = tokens
            namePills[me] = name
        }
        container.addView(tile)
        layoutTiles(container)
        videoViews[key] = v
        tileViews[key] = tile
        localTrack.addRenderer(v)
    }

    private fun ensureGridMode(container: FrameLayout) {
        if (isGridMode) return
        isGridMode = true
        // Replace the full-screen preview with tiled layout including local track
        try { previewView?.release() } catch (_: Exception) {}
        previewView = null
        container.removeAllViews()
        ensureOverflowBadge(container)
        addLocalTile(container)
        // Also add any currently subscribed remote tracks
        try {
            liveKitRoom?.remoteParticipants?.values?.forEach { p ->
                p.videoTrackPublications.forEach { pubPair ->
                    val rt = pubPair.second as? RemoteVideoTrack
                    if (rt != null) {
                        val key = "${p.sid}_${rt.sid}"
                        addVideoTile(container, key, rt)
                        updateOverflow(container)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun removeVideoTile(container: FrameLayout, key: String) {
        val v = videoViews.remove(key)
        val tile = tileViews.remove(key)
        if (v == null && tile == null) return
        try { v?.release() } catch (_: Exception) {}
        if (tile != null) container.removeView(tile) else container.removeView(v)
        layoutTiles(container)
    }

    private fun layoutTiles(container: FrameLayout) {
        // All children except overflow badge are tiles
        val all = (0 until container.childCount).map { container.getChildAt(it) }
        val tiles = all.filter { it !== overflowBadge }
        val n = tiles.size
        if (n == 0) return
        val w = container.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val h = container.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

        // Respect maxHosts: hide overflow tiles and only layout the first displayCount
        val displayCount = kotlin.math.min(n, maxHosts)
        tiles.forEachIndexed { idx, v -> v.visibility = if (idx < displayCount) View.VISIBLE else View.GONE }

        fun place(childIndex: Int, l: Int, t: Int, r: Int, b: Int) {
            if (childIndex >= 0 && childIndex < displayCount) {
                tiles[childIndex].layout(l, t, r, b)
            }
        }

        when (displayCount) {
            1 -> place(0, 0, 0, w, h)
            2 -> {
                val cw = w / 2
                val ch = kotlin.math.min(h, w / 2) // keep tiles roughly square
                place(0, 0, 0, cw, ch)
                place(1, cw, 0, w, ch)
            }
            3 -> {
                // One large left (2/3 width), two stacked squares on right (1/3 width)
                val leftW = (w * 2) / 3
                val rightW = w - leftW
                val rightH = h / 2
                place(0, 0, 0, leftW, h)
                place(1, leftW, 0, w, rightH)
                place(2, leftW, rightH, w, h)
            }
            4 -> {
                val cw = w / 2
                val ch = h / 2
                place(0, 0, 0, cw, ch)
                place(1, cw, 0, w, ch)
                place(2, 0, ch, cw, h)
                place(3, cw, ch, w, h)
            }
            5 -> {
                // One large top (full width, half height), then 2x2 grid bottom
                val topH = h / 2
                place(0, 0, 0, w, topH)
                val bw = w / 2
                val bh = (h - topH) / 2
                place(1, 0, topH, bw, topH + bh)
                place(2, bw, topH, w, topH + bh)
                place(3, 0, topH + bh, bw, h)
                place(4, bw, topH + bh, w, h)
            }
            6 -> {
                val cw = w / 3
                val ch = h / 2
                for (i in 0 until 6) {
                    val r = i / 3
                    val c = i % 3
                    val l = c * cw
                    val t = r * ch
                    place(i, l, t, l + cw, t + ch)
                }
            }
            9 -> {
                val cw = w / 3
                val ch = h / 3
                for (i in 0 until 9) {
                    val r = i / 3
                    val c = i % 3
                    val l = c * cw
                    val t = r * ch
                    place(i, l, t, l + cw, t + ch)
                }
            }
            else -> {
                // Fallback: pick columns based on available width to keep tiles usable on phones
                val minTilePx = (120 * resources.displayMetrics.density).toInt()
                val maxColsByWidth = kotlin.math.max(1, w / minTilePx)
                val cols = kotlin.math.min(kotlin.math.min(3, n), kotlin.math.max(1, maxColsByWidth))
                val rows = kotlin.math.ceil(displayCount / cols.toDouble()).toInt()
                val cw = w / cols
                val ch = h / rows
                for (i in 0 until displayCount) {
                    val r = i / cols
                    val c = i % cols
                    val l = c * cw
                    val t = r * ch
                    place(i, l, t, l + cw, t + ch)
                }
            }
        }
        container.requestLayout()
        updateOverflow(container)
    }

    private fun ensureOverflowBadge(container: FrameLayout) {
        if (overflowBadge != null) return
        val tv = TextView(this).apply {
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(0xB3000000.toInt())
            textSize = 12f
            setPadding(12, 6, 12, 6)
            visibility = View.GONE
        }
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = android.view.Gravity.END or android.view.Gravity.TOP
            topMargin = (8 * resources.displayMetrics.density).toInt()
            marginEnd = (8 * resources.displayMetrics.density).toInt()
        }
        container.addView(tv, lp)
        overflowBadge = tv
    }

    private fun updateOverflow(container: FrameLayout) {
        val tv = overflowBadge ?: return
        val tiles = (0 until container.childCount).map { container.getChildAt(it) }.filter { it !== tv }
        val total = tiles.size
        val overflow = total - kotlin.math.min(total, maxHosts)
        if (overflow > 0) {
            tv.text = "+$overflow"
            tv.visibility = View.VISIBLE
        } else {
            tv.visibility = View.GONE
        }
    }

    /**
     * Switch primary focus to a participant's stream. For now, this is a stub
     * until we add explicit track pinning/selection logic.
     */
    private fun switchTo(streamId: String) {
        Toast.makeText(this, "Switching to stream $streamId", Toast.LENGTH_SHORT).show()
        // TODO: Implement track pinning/layout prioritization using mapping of streamId -> participant/track.
    }

    private fun renderRequestsPanel(items: List<Pair<String,String>>) {
        val list = requestsPanel.findViewById<LinearLayout>(R.id.listRequests)
        list.removeAllViews()
        for ((uname, id) in items) {
            val row = layoutInflater.inflate(R.layout.item_request_row, list, false)
            row.findViewById<TextView>(R.id.tvUsername).text = uname
            row.findViewById<Button>(R.id.btnAcceptRequest).setOnClickListener {
                lifecycleScope.launch {
                    try {
                        RetrofitClient.functionsApi.liveInvite(mapOf("action" to "accept", "inviteId" to id))
                        Toast.makeText(this@LiveStreamActivity, "Accepted", Toast.LENGTH_SHORT).show()
                        showRequestsPanel()
                    } catch (_: Exception) {
                        Toast.makeText(this@LiveStreamActivity, "Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            row.findViewById<Button>(R.id.btnRejectRequest).setOnClickListener {
                lifecycleScope.launch {
                    try {
                        RetrofitClient.functionsApi.liveInvite(mapOf("action" to "reject", "inviteId" to id))
                        Toast.makeText(this@LiveStreamActivity, "Rejected", Toast.LENGTH_SHORT).show()
                        showRequestsPanel()
                    } catch (_: Exception) {
                        Toast.makeText(this@LiveStreamActivity, "Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            list.addView(row)
        }
    }

    private fun showRequestsPanel() {
        openRequestsBottomSheet()
    }

    private fun openRequestsBottomSheet() {
        val sid = currentStream?.id ?: return
        try {
            RequestsBottomSheetFragment.newInstance(sid)
                .show(supportFragmentManager, "RequestsBottomSheet")
        } catch (_: Exception) {}
    }

    /** Send selected gift to current live stream; on success inserts a comment line. */
    private suspend fun sendGiftToLive(gift: Gift) {
        val stream = currentStream ?: return
        val gifterId = AuthUtils.getCurrentUserId(this) ?: return
        val recipientId = stream.hostId
        val tokens = gift.tokens
        val txRef = "live_gift_${stream.id}_${gift.id}_${System.currentTimeMillis()}"
        try {
            val resp = RetrofitClient.functionsApi.processGiftSendRpc(
                mapOf(
                    "giftId" to gift.id,
                    "gifterId" to gifterId,
                    "recipientId" to recipientId,
                    "tokens" to tokens,
                    "txRef" to txRef,
                    "liveStreamId" to stream.id
                )
            )
            if (resp.isSuccessful) {
                // Do not insert an explicit comment; gift events are rendered as comments, avoiding duplicates.
                Toast.makeText(this, getString(R.string.gift_sent_success), Toast.LENGTH_SHORT).show()
            } else {
                // fallthrough to error handler
                throw retrofit2.HttpException(resp)
            }
        } catch (e: Exception) {
            val isInsufficient = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
                ?.lowercase()?.contains("insufficient") == true
            if (isInsufficient) {
                val uid = gifterId
                TokenPurchaseBottomSheetFragment.newInstance(arrayListOf(10, 50, 500, 1200, 3000))
                    .setListener(object: TokenPurchaseBottomSheetFragment.Listener {
                        override fun onPurchase(amount: Int) {
                            initiateTopup(uid, amount)
                        }
                    })
                    .show(supportFragmentManager, "TokenPurchaseBottomSheet")
            } else {
                Toast.makeText(this, getString(R.string.failed_to_send_gift), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Deep-link handler: load an existing live stream by ID and join as viewer.
     */
    private fun handleDeepLinkStream(streamId: String) {
        // track fetched session for error-handling
        var fetchedLive: LiveStream? = null
        lifecycleScope.launch {
            try {
                // Fetch via Edge Function to include a LiveKit token for viewer or host
                val resp = RetrofitClient.functionsApi.getLiveSession(streamId)
                if (!resp.isSuccessful) {
                    Toast.makeText(
                        this@LiveStreamActivity,
                        R.string.stream_not_found,
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                    return@launch
                }
                val ls = resp.body() ?: run {
                    Toast.makeText(
                        this@LiveStreamActivity,
                        R.string.stream_not_found,
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                    return@launch
                }
                // save for potential cleanup in catch
                fetchedLive = ls
                // set current stream for viewer flows
                currentStream = ls
                // If user tapped before we loaded, flush queued taps now that we have a stream id
                flushQueuedTaps()
                // hydrate options
                commentScope = (ls.commentScope ?: "shared").lowercase()
                // Access gating before joining (host bypass)
                val currentUser = AuthUtils.getCurrentUserId(this@LiveStreamActivity)
                val isHost = (currentUser != null && currentUser == ls.hostId)
                if (!isHost) {
                    val ok = ensureLiveAccess(ls)
                    if (!ok) {
                        // if user declined or failed, stop here
                        return@launch
                    }
                }
                // If host resumes their own live, go into host UI; else join as viewer
                AuthUtils.getCurrentUserId(this@LiveStreamActivity)?.let { currentId ->
                    if (currentId == ls.hostId) {
                        preferSingleRemote = false
                        resumeHostSession(ls)
                        return@launch
                    }
                    // viewer flow: register as viewer then init viewer UI
                    RetrofitClient.liveStreamApi.joinLiveStream(
                        select = "*",
                        viewer = LiveStreamViewerRequest(ls.id, currentId)
                    )
                }
                // Viewer: prefer single remote tile by default
                preferSingleRemote = true
                initViewer()
            } catch (e: Exception) {
                // Do not end the live on client-side error; just show message and exit
                Toast.makeText(
                    this@LiveStreamActivity,
                    R.string.error_loading_stream,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    /** Ensure viewer has access to the live stream; shows dialogs for purchase/subscribe/upgrade when needed. */
    private suspend fun ensureLiveAccess(ls: LiveStream): Boolean {
        // Free streams require no action
        val type = (ls.accessType ?: "free").lowercase()
        if (type == "free") return true
        val hostId = ls.hostId
        val userId = AuthUtils.getCurrentUserId(this) ?: return false
        if (userId == hostId) return true
        return when (type) {
            "paid" -> {
                // Attempt purchase-live-access; on failure, prompt
                try {
                    val ok = RetrofitClient.functionsApi.purchaseLiveAccessRpc(mapOf("liveStreamId" to ls.id)).isSuccessful
                    if (ok) true else {
                        LiveAccessBottomSheetFragment.newInstance(
                            mode = "paid",
                            message = getString(R.string.purchase_live_access_for_tokens) + " " + (ls.price ?: 0),
                            benefits = arrayListOf(),
                            price = ls.price ?: 0,
                            streamId = ls.id,
                            creatorId = ls.hostId,
                            planId = null,
                            planTokens = null
                        ).setListener(object: LiveAccessBottomSheetFragment.Listener {
                            override fun onPurchase(streamId: String) {
                                lifecycleScope.launch {
                                    RetrofitClient.functionsApi.purchaseLiveAccessRpc(mapOf("liveStreamId" to streamId))
                                    handleDeepLinkStream(streamId)
                                }
                            }
                            override fun onSubscribe(creatorId: String, planId: String, planTokens: Int) { /* no-op */ }
                        }).show(supportFragmentManager, "LiveAccessBottomSheet")
                        false
                    }
                } catch (e: Exception) {
                    false
                }
            }
            "subscription" -> {
                try {
                    val plans = RetrofitClient.subscriptionPlanApi.getSubscriptionPlans("eq.$hostId")
                    if (plans.isEmpty()) {
                        Toast.makeText(this, R.string.no_plans_available, Toast.LENGTH_SHORT).show()
                        return false
                    }
                    val sorted = plans.sortedBy { it.tokens }
                    val required = ls.requiredPlanId?.let { id -> sorted.find { it.id == id } } ?: sorted.first()
                    // Check current user's subscription tokens
                    val nowIso = java.time.Instant.now().toString()
                    val subs = RetrofitClient.subscriptionsApiExt.getActiveSubscriptions(
                        creatorFilter = "eq.$hostId",
                        subscriberFilter = "eq.$userId",
                        orFilter = "end_date.is.null,end_date.gt.$nowIso"
                    )
                    val currentTokens = subs.maxOfOrNull { it.tokens } ?: 0
                    if (currentTokens >= required.tokens) return true
                    // Show custom subscribe/upgrade bottom sheet
                    paywallPlanId = required.id
                    paywallPlanTokens = required.tokens
                    LiveAccessBottomSheetFragment.newInstance(
                        mode = "subscription",
                        message = getString(R.string.live_requires_plan_and_above) + " " + required.name + " (" + required.tokens + ")",
                        benefits = ArrayList((required.description ?: "").split('\n').filter { it.isNotBlank() }),
                        price = null,
                        streamId = ls.id,
                        creatorId = hostId,
                        planId = required.id,
                        planTokens = required.tokens
                    ).setListener(object: LiveAccessBottomSheetFragment.Listener {
                        override fun onPurchase(streamId: String) { /* no-op */ }
                        override fun onSubscribe(creatorId: String, planId: String, planTokens: Int) {
                            lifecycleScope.launch { attemptSubscribeWithTopup(creatorId, planId, planTokens) }
                        }
                    }).show(supportFragmentManager, "LiveAccessBottomSheet")
                    false
                } catch (e: Exception) {
                    false
                }
            }
            else -> true
        }
    }

    private suspend fun attemptSubscribeWithTopup(creatorId: String, planId: String, tokens: Int) {
        try {
            val ok = RetrofitClient.functionsApi.subscribeToCreatorRpc(mapOf("creatorId" to creatorId, "planId" to planId)).isSuccessful
            if (ok) {
                handleDeepLinkStream(currentStream?.id ?: return)
                return
            }
        } catch (e: Exception) {
            // fall through to topup
        }
        // Top-up flow: open buy tokens dialog and retry
        val uid = AuthUtils.getCurrentUserId(this) ?: return
        showBuyTokensDialog(uid)
        Toast.makeText(this, R.string.please_retry_after_topup, Toast.LENGTH_SHORT).show()
    }

    /**
     * Resume an existing live stream for host without creating a new session.
     */
    private fun resumeHostSession(live: LiveStream) {
        // require camera & audio permissions
        if (!allPermissionsGranted()) {
            // Remember to resume this exact session after permission grant
            pendingResumeLive = live
            ActivityCompat.requestPermissions(
                this@LiveStreamActivity,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
            return
        }
        currentStream = live
        commentScope = (live.commentScope ?: "shared").lowercase()
        liveTopBar.visibility = View.VISIBLE
        liveTopBar.bringToFront()
        // load host profile and comments
        lifecycleScope.launch {
            val hostId = live.hostId
            val profiles = RetrofitClient.profileApi.getProfileByUserId("*", "eq.$hostId")
            if (profiles.isNotEmpty()) {
                val p = profiles[0]
                tvStreamerName.text = (p.name ?: p.username).take(14)
                ivStreamerImage.load(p.image)
            }
            tvFollowerCount.text = getString(
                R.string.follower_count,
                profiles.getOrNull(0)?.followersCount?.let { formatCount(it) } ?: formatCount(0)
            )
            // Host: show requests icon and polling for pending join requests
            btnRequests.visibility = View.VISIBLE
            btnRequests.setOnClickListener { openRequestsBottomSheet() }
            tvViewerCount.setOnClickListener { openRequestsBottomSheet() }
            launch {
                while (isActive && !isEnded) {
                    delay(3000)
                    try {
                        val resp = RetrofitClient.functionsApi.liveInviteRaw(mapOf("action" to "list", "streamId" to live.id))
                        if (!resp.isSuccessful) continue
                        val body = resp.body()?.string() ?: "[]"
                        val arr = org.json.JSONArray(body)
                            val pending = (0 until arr.length()).count {
                                val st = arr.getJSONObject(it).optString("status").lowercase()
                                st == "requested" || st == "pending" || st == "request"
                            }
                            if (pending > 0) btnRequests.setColorFilter(android.graphics.Color.RED) else btnRequests.clearColorFilter()
                    } catch (_: Exception) {}
                }
            }
            // load comments and poll
            live.id.let { sid ->
                val initial = RetrofitClient.liveStreamApi.getLiveStreamComments(
                    select = "*,profile:profiles(*)",
                    streamFilter = "eq.$sid"
                )
                val initialSorted = initial.sortedBy { it.createdAt }
                commentsAdapter.submitList(initialSorted)
                if (initialSorted.isNotEmpty()) rvLiveComments.scrollToPosition(initialSorted.size - 1)
                commentsJob = launch {
                    while (isActive) {
                        delay(3000)
                        val updated = RetrofitClient.liveStreamApi.getLiveStreamComments(
                            select = "*,profile:profiles(*)",
                            streamFilter = "eq.$sid"
                        )
                        val updatedSorted = updated.sortedBy { it.createdAt }
                        commentsAdapter.submitList(updatedSorted)
                        if (updatedSorted.isNotEmpty()) rvLiveComments.scrollToPosition(updatedSorted.size - 1)
                    }
                }
            }
        }
        // host camera preview & controls
        if (!USE_LIVEKIT_CAMERA_PREVIEW) startCamera()
        btnToggleMic.visibility = View.VISIBLE
        btnSwitchCamera.visibility = View.VISIBLE
        btnToggleCamera.visibility = if (USE_LIVEKIT_CAMERA_PREVIEW) View.VISIBLE else View.GONE

        // Prepare preview surface for local publish
        val container = findViewById<FrameLayout>(R.id.flLiveStream)
        container.removeAllViews()
        val preview = SurfaceViewRenderer(this@LiveStreamActivity)
        preview.setScalingType(livekit.org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        preview.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        // Mirror only for front-facing camera so host sees a natural preview
        preview.setMirror(isFrontFacing)
        container.addView(preview)
        previewView = preview

        // Re-wire host control buttons on resume
        if (USE_LIVEKIT_CAMERA_PREVIEW) {
            btnSwitchCamera.visibility = View.VISIBLE
            btnSwitchCamera.setOnClickListener {
                try {
                    val localPubPair = liveKitRoom?.localParticipant?.videoTrackPublications?.firstOrNull()
                    val localTrack2 = localPubPair?.second as? LocalVideoTrack
                    localTrack2?.switchCamera()
                    isFrontFacing = !isFrontFacing
                    // Update preview mirroring when camera flips
                    previewView?.setMirror(isFrontFacing)
                } catch (_: Exception) { }
            }

            btnToggleCamera.visibility = View.VISIBLE
            var isVideoEnabled = true
            btnToggleCamera.setOnClickListener {
                isVideoEnabled = !isVideoEnabled
                lifecycleScope.launch {
                    try {
                        liveKitRoom?.localParticipant?.setCameraEnabled(isVideoEnabled)
                    } catch (_: Exception) {
                        // Revert state if publish not permitted
                        isVideoEnabled = !isVideoEnabled
                        Toast.makeText(this@LiveStreamActivity, R.string.permission_denied, Toast.LENGTH_SHORT).show()
                    }
                }
                btnToggleCamera.setImageResource(
                    if (isVideoEnabled) android.R.drawable.ic_menu_view
                    else android.R.drawable.ic_menu_close_clear_cancel
                )
            }
        } else {
            btnSwitchCamera.visibility = View.VISIBLE
            btnToggleCamera.visibility = View.GONE
            btnSwitchCamera.setOnClickListener {
                currentLensFacing = if (currentLensFacing == CameraSelector.LENS_FACING_FRONT)
                    CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
                startCamera(currentLensFacing)
            }
        }

        btnToggleMic.setOnClickListener {
            isMicEnabled = !isMicEnabled
            lifecycleScope.launch {
                try {
                    liveKitRoom?.localParticipant?.setMicrophoneEnabled(isMicEnabled)
                } catch (_: Exception) {
                    // Revert toggle on failure and notify user
                    isMicEnabled = !isMicEnabled
                    Toast.makeText(this@LiveStreamActivity, R.string.permission_denied, Toast.LENGTH_SHORT).show()
                }
            }
            btnToggleMic.setImageResource(
                if (isMicEnabled) android.R.drawable.ic_lock_silent_mode_off
                else android.R.drawable.ic_lock_silent_mode
            )
        }

        // connect to LiveKit as host and (re)publish
        live.token?.takeIf { it.isNotBlank() }?.let { lkToken ->
            val roomOptions = RoomOptions(
                /*publishAudio=*/true,
                /*publishVideo=*/true,
                null,
                LocalAudioTrackOptions(),
                LocalVideoTrackOptions(),
                null,
                null
            )
            val room = LiveKit.create(this@LiveStreamActivity, roomOptions, LiveKitOverrides())
                    liveKitRoom = room
                    // Now that realtime is connected, flush any queued taps for immediate propagation
                    flushQueuedTaps()
            lifecycleScope.launch {
                try {
                    room.connect(
                        LiveKitConfig.WS_URL,
                        lkToken,
                        ConnectOptions( /* autoSubscribe = */ true )
                    )
                    // enable camera/mic and attach local preview
                    try { room.localParticipant.setCameraEnabled(true) } catch (_: Exception) {}
                    try { room.localParticipant.setMicrophoneEnabled(true) } catch (_: Exception) {}
                    room.initVideoRenderer(preview)
                    val localPubPair = room.localParticipant.videoTrackPublications.firstOrNull()
                    val localTrack = localPubPair?.second as? LocalVideoTrack
                    localTrack?.addRenderer(preview)
                    // After connect, enumerate any already-subscribed remote tracks
                    val container = findViewById<FrameLayout>(R.id.flLiveStream)
                    // Switch from any transient preview to static tiles
                    container.removeAllViews()
                    tilesRoot = null
                    staticTrackRenderers.values.forEach { try { it.release() } catch (_: Exception) {} }
                    staticTrackRenderers.clear()
                    updateStaticTiles(container, includeLocal = true)
                } catch (_: Exception) { }
                // Load battle state after connect
                lifecycleScope.launch { loadBattleState() }
            }
            // Subscribe to new remote tracks/unsubscribes to manage tiles
            lifecycleScope.launch {
                room.events.collect { evt ->
                    when (evt) {
                        is RoomEvent.TrackSubscribed, is RoomEvent.TrackPublished,
                        is RoomEvent.ParticipantConnected,
                        is RoomEvent.TrackUnsubscribed, is RoomEvent.ParticipantDisconnected -> {
                            updateStaticTiles(container, includeLocal = true)
                        }
                        is RoomEvent.DataReceived -> {
                            try {
                                val txt = String(evt.data, Charsets.UTF_8)
                                val obj = org.json.JSONObject(txt)
                                if (obj.optString("type") == "tap") {
                                    val root = findViewById<FrameLayout>(R.id.flLiveStream)
                                    val startX = root.width - 48f
                                    val startY = root.height - 220f
                                    for (i in 0 until 6) {
                                        root.postDelayed({ spawnHeart(startX - (0..40).random(), startY - (0..20).random()) }, (i * 50).toLong())
                                    }
                                    // Host no longer aggregates; DB increments are done per-tap by viewers.
                                }
                            } catch (_: Exception) { }
                        }
                        else -> Unit
                    }
                }
            }
            // Also poll briefly after connect to ensure local and any early remote tracks attach promptly
            trackPollJob?.cancel()
            trackPollJob = lifecycleScope.launch {
                repeat(20) { // ~10s max
                    try {
                        updateStaticTiles(container, includeLocal = true)
                        val hasLocal = try { room.localParticipant.videoTrackPublications.isNotEmpty() } catch (_: Exception) { false }
                        if (hasLocal) return@launch
                    } catch (_: Exception) {}
                    delay(500)
                }
            }
            // status polling to end when stream ends
            statusJob?.cancel()
            statusJob = lifecycleScope.launch(Dispatchers.IO) {
                while (isActive) {
                    delay(3000)
                    val rows = RetrofitClient.liveStreamApi.getLiveStreamById(
                        "id,status,viewer_count,taps", "eq.${live.id}"
                    )
                    val row = rows.firstOrNull() ?: break
                    withContext(Dispatchers.Main) {
                        tvViewerCount.text = row.viewerCount.toString()
                        val prev = lastTapsValue
                        val taps = row.taps ?: 0
                        tvTapCount?.text = taps.toString()
                        lastTapsValue = taps
                        val delta = taps - prev
                        if (delta > 0) {
                            val root = findViewById<FrameLayout>(R.id.flLiveStream)
                            val startX = root.width - 48f
                            val startY = root.height - 220f
                            for (i in 0 until kotlin.math.min(6, delta)) {
                                root.postDelayed({ spawnHeart(startX - (0..40).random(), startY - (0..20).random()) }, (i * 60).toLong())
                            }
                        }
                        if (row.status != "live") {
                            isEnded = true
                            try { liveKitRoom?.disconnect() } catch (_: Exception) {}
                            commentsJob?.cancel(); commentsJob = null
                            Toast.makeText(
                                this@LiveStreamActivity,
                                R.string.stream_not_found,
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                        }
                    }
                }
            }
            // Host no-op: viewers write taps via RPC per tap.
        }
    }

    private suspend fun loadBattleState() {
        val sid = currentStream?.id ?: return
        try {
            val battles = RetrofitClient.liveStreamApi.getActiveBattleForStream("*", "eq.$sid")
            val b = battles.firstOrNull() ?: return
            isMatch = true
            battleId = b.id
            battleStartedAt = b.startedAt
            matchParticipants.clear()
            matchParticipants.addAll(RetrofitClient.liveStreamApi.getBattleParticipants("*", "eq.${b.id}"))
            userIdToStreamId.clear()
            matchParticipants.forEach { userIdToStreamId[it.userId] = it.liveStreamId }
            // fetch usernames for pills
            try {
                val ids = matchParticipants.joinToString(",") { it.userId }
                val profs = RetrofitClient.profileApi.getProfilesByUserIds("*", "in.($ids)")
                val map = profs.associateBy({ it.userId }, { it.username ?: it.userId.take(6) })
                // replace userId placeholder in pills later by using this map via tag
                // store temporarily in tokenTallies map negative key? Instead, set in a local structure
                // We'll update tokens during render
                // Stash usernames into a separate map by extending userIdToStreamId key prefix
                profs.forEach { p -> userIdToStreamId["uname:" + p.userId] = p.username ?: p.userId.take(6) }
            } catch (_: Exception) {}
            // Determine endsAt from row or fallback to startedAt + match_duration_sec
            matchEndsAtIso = b.endsAt ?: run {
                val start = b.startedAt
                val dur = currentStream?.matchDurationSec ?: 180
                if (start != null) try {
                    java.time.Instant.parse(start).plusSeconds(dur.toLong()).toString()
                } catch (_: Exception) { null } else null
            }
            renderMatchOverlay()
            startMatchTimer()
        } catch (_: Exception) { }
    }

    private fun renderMatchOverlay() {
        runOnUiThread {
            matchOverlay.removeAllViews()
            if (!isMatch || matchParticipants.isEmpty()) return@runOnUiThread
            // Progress bars at top
            val barContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
                    topMargin = 16
                }
            }
            // Center timer text
            val timer = TextView(this).apply {
                setTextColor(android.graphics.Color.WHITE)
                textSize = 14f
                setPadding(12,6,12,6)
                setBackgroundColor(0x66000000)
            }
            matchTimerText = timer
            val timerLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
                topMargin = 40
            }
            val bar1 = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = percentFor(teamOrFirst())
                progressTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#ec4899"))
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x33FFFFFF)
                layoutParams = LinearLayout.LayoutParams(0, 16, 1f).apply { marginEnd = 8 }
            }
            val bar2 = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = 100 - percentFor(teamOrFirst())
                progressTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#93c5fd"))
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0x33FFFFFF)
                layoutParams = LinearLayout.LayoutParams(0, 16, 1f).apply { marginStart = 8 }
            }
            barContainer.addView(bar1); barContainer.addView(bar2)
            matchOverlay.addView(barContainer)
            matchOverlay.addView(timer, timerLp)

            // Clickable boxes overlay using GridLayout
            val grid = android.widget.GridLayout(this).apply {
                rowCount = if (matchParticipants.size >= 3) 2 else 1
                columnCount = 2
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
            fun colorFor(index: Int): Int {
                val p = matchParticipants[index]
                val team = p.team ?: 0
                return if (team == 1) android.graphics.Color.parseColor("#ec4899")
                else if (team == 2) android.graphics.Color.parseColor("#93c5fd")
                else when (index % 4) {
                    0 -> android.graphics.Color.parseColor("#ef4444")
                    1 -> android.graphics.Color.parseColor("#3b82f6")
                    2 -> android.graphics.Color.parseColor("#10b981")
                    else -> android.graphics.Color.parseColor("#f59e0b")
                }
            }

            fun addBox(index: Int) {
                val p = matchParticipants[index]
                val box = FrameLayout(this)
                val specRow = if (matchParticipants.size == 3 && index == 0) android.widget.GridLayout.spec(0, 2) else android.widget.GridLayout.spec(if (matchParticipants.size >= 3 && index > 0) (index - 1) else 0)
                val specCol = if (matchParticipants.size == 3 && index == 0) android.widget.GridLayout.spec(0) else android.widget.GridLayout.spec(if (matchParticipants.size >= 3 && index > 0) 1 else index)
                val lp = android.widget.GridLayout.LayoutParams(specRow, specCol).apply {
                    width = 0; height = 0; columnSpec = specCol; rowSpec = specRow
                    setGravity(android.view.Gravity.FILL)
                }
                lp.width = 0; lp.height = 0
                lp.columnSpec = specCol; lp.rowSpec = specRow
                lp.setMargins(8,8,8,8)
                box.layoutParams = lp
                val btn = View(this)
                btn.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                btn.setOnClickListener { switchTo(userIdToStreamId[p.userId] ?: return@setOnClickListener) }
                // set colored border
                val border = android.graphics.drawable.GradientDrawable()
                border.setColor(0x00000000)
                border.setStroke((resources.displayMetrics.density * 2).toInt(), colorFor(index))
                border.cornerRadius = 8 * resources.displayMetrics.density
                box.background = border
                // Name pill
                val pill = TextView(this).apply {
                    val uname = userIdToStreamId["uname:" + p.userId] ?: p.userId.take(6)
                    text = uname
                    setTextColor(android.graphics.Color.WHITE)
                    setBackgroundColor(0x80000000.toInt())
                    textSize = 11f
                    setPadding(12,6,12,6)
                }
                val pillLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                    bottomMargin = 16; rightMargin = 16
                }
                val tokenPill = TextView(this).apply {
                    text = (tokenTallies[p.userId] ?: 0).toString()
                    setTextColor(android.graphics.Color.WHITE)
                    setBackgroundColor(0x80000000.toInt())
                    textSize = 11f
                    setPadding(12,6,12,6)
                }
                val tokenLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = android.view.Gravity.TOP or android.view.Gravity.START
                    topMargin = 16; leftMargin = 16
                }
                box.addView(btn)
                box.addView(tokenPill, tokenLp)
                box.addView(pill, pillLp)
                grid.addView(box)
            }
            when (matchParticipants.size) {
                2 -> { addBox(0); addBox(1) }
                3 -> { addBox(0); addBox(1); addBox(2) }
                else -> { for (i in 0 until kotlin.math.min(4, matchParticipants.size)) addBox(i) }
            }
            matchOverlay.addView(grid)
        }
    }

    private fun startMatchTimer() {
        matchTimerJob?.cancel()
        val endIso = matchEndsAtIso ?: return
        matchTimerJob = lifecycleScope.launch {
            while (isActive && isMatch) {
                try {
                    val now = java.time.Instant.now()
                    val end = java.time.Instant.parse(endIso)
                    val remaining = java.time.Duration.between(now, end).seconds
                    val secs = remaining.coerceAtLeast(0)
                    val mm = (secs / 60).toInt()
                    val ss = (secs % 60).toInt()
                    val txt = String.format("%02d:%02d", mm, ss)
                    runOnUiThread { matchTimerText?.text = txt }
                    if (secs <= 0) break
                } catch (_: Exception) { }
                delay(1000)
            }
            runOnUiThread { matchTimerText?.text = "00:00" }
        }
    }

    /** Inflate the static tiles layout into the container if not present. */
    private fun ensureTilesLayout(container: FrameLayout) {
        if (tilesRoot != null) return
        val v = layoutInflater.inflate(R.layout.include_livestream_tiles, container, false)
        container.addView(v)
        tilesRoot = v
    }

    /** Show the appropriate tile group for [count] and return the target frames in order. */
    private fun selectFrames(count: Int): List<FrameLayout> {
        val root = tilesRoot ?: return emptyList()
        val one = root.findViewById<View>(R.id.oneHostView)
        val two = root.findViewById<View>(R.id.twoHostView)
        val three = root.findViewById<View>(R.id.threeHostView)
        val four = root.findViewById<View>(R.id.fourHostView)
        val many = root.findViewById<View>(R.id.upTo17HostView)
        fun hideAll() { one.visibility = View.GONE; two.visibility = View.GONE; three.visibility = View.GONE; four.visibility = View.GONE; many.visibility = View.GONE }
        hideAll()
        return when (count.coerceIn(0, 17)) {
            0 -> { /* hide all */ emptyList() }
            1 -> { one.visibility = View.VISIBLE; listOf(root.findViewById(R.id.oneHostView)) }
            2 -> {
                two.visibility = View.VISIBLE
                listOf(
                    root.findViewById(R.id.twoHostViewPrimary),
                    root.findViewById(R.id.twoHostViewSecondary)
                )
            }
            3 -> {
                three.visibility = View.VISIBLE
                listOf(
                    root.findViewById(R.id.threeHostViewPrimary),
                    root.findViewById(R.id.threeHostViewSecondary1),
                    root.findViewById(R.id.threeHostViewSecondary2)
                )
            }
            4 -> {
                four.visibility = View.VISIBLE
                listOf(
                    root.findViewById(R.id.fourHostViewPrimary),
                    root.findViewById(R.id.fourHostViewSecondary1),
                    root.findViewById(R.id.fourHostViewSecondary2),
                    root.findViewById(R.id.fourHostViewSecondary3)
                )
            }
            else -> {
                many.visibility = View.VISIBLE
                val frames = mutableListOf<FrameLayout>()
                frames += root.findViewById<FrameLayout>(R.id.upToNineHostViewPrimary)
                // Secondary1..16
                val ids = intArrayOf(
                    R.id.upToNineHostViewSecondary1,
                    R.id.upToNineHostViewSecondary2,
                    R.id.upToNineHostViewSecondary3,
                    R.id.upToNineHostViewSecondary4,
                    R.id.upToNineHostViewSecondary5,
                    R.id.upToNineHostViewSecondary6,
                    R.id.upToNineHostViewSecondary7,
                    R.id.upToNineHostViewSecondary8,
                    R.id.upToNineHostViewSecondary9,
                    R.id.upToNineHostViewSecondary10,
                    R.id.upToNineHostViewSecondary11,
                    R.id.upToNineHostViewSecondary12,
                    R.id.upToNineHostViewSecondary13,
                    R.id.upToNineHostViewSecondary14,
                    R.id.upToNineHostViewSecondary15,
                    R.id.upToNineHostViewSecondary16,
                )
                ids.forEach { frames += root.findViewById<FrameLayout>(it) }
                frames
            }
        }
    }

    /** Rebuild static tiles and attach current tracks. */
    private fun updateStaticTiles(container: FrameLayout, includeLocal: Boolean = false) {
        ensureTilesLayout(container)
        val room = liveKitRoom ?: return
        val tracks = mutableListOf<Pair<String, Any>>()
        if (includeLocal) try {
            val pub = room.localParticipant.videoTrackPublications.firstOrNull()
            val lt = pub?.second as? LocalVideoTrack
            if (lt != null) tracks += ("local_${lt.sid ?: "cam"}") to lt
        } catch (_: Exception) {}
        try {
            room.remoteParticipants.values.forEach { p ->
                p.videoTrackPublications.forEach { pubPair ->
                    val rt = pubPair.second as? RemoteVideoTrack
                    if (rt != null) tracks += (rt.sid?.toString() ?: pubPair.first.sid.toString()) to rt
                }
            }
        } catch (_: Exception) {}
        val display = tracks.take(17)
        val frames = selectFrames(display.size)
        // Release renderers no longer used
        val activeIds = display.map { it.first }.toSet()
        val toRemove = staticTrackRenderers.keys.filter { it !in activeIds }
        toRemove.forEach { id ->
            try { staticTrackRenderers.remove(id)?.release() } catch (_: Exception) {}
        }
        // Attach tracks to frames
        for (i in display.indices) {
            val (id, t) = display[i]
            val frame = frames[i]
            frame.removeAllViews()
            val renderer = staticTrackRenderers[id] ?: SurfaceViewRenderer(this).also { r ->
                r.setScalingType(livekit.org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                r.setEnableHardwareScaler(true)
                try { room.initVideoRenderer(r) } catch (_: Exception) {}
                staticTrackRenderers[id] = r
            }
            frame.addView(renderer, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            when (t) {
                is RemoteVideoTrack -> t.addRenderer(renderer)
                is LocalVideoTrack -> t.addRenderer(renderer)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Keep the screen awake while watching a live stream
        try { window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } catch (_: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        // Allow the screen to sleep again and release live resources
        try { window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) } catch (_: Exception) {}
        try { liveKitRoom?.disconnect() } catch (_: Exception) {}
        liveKitRoom = null
        try {
            videoViews.values.forEach { it.release() }
            videoViews.clear()
            findViewById<android.widget.FrameLayout>(R.id.flLiveStream)?.removeAllViews()
            staticTrackRenderers.values.forEach { it.release() }
            staticTrackRenderers.clear()
            tilesRoot = null
        } catch (_: Exception) {}
    }

    private fun percentFor(userId: String): Int {
        val sum = tokenTallies.values.sum()
        if (sum <= 0) return 0
        return kotlin.math.round(((tokenTallies[userId] ?: 0) * 100.0 / sum)).toInt()
    }
    private fun teamOrFirst(): String {
        // fallback: first participant userId
        return matchParticipants.firstOrNull()?.userId ?: ""
    }

    private fun showInviteDialog() {
        val input = android.widget.EditText(this).apply { hint = "username" }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Invite guest by username")
            .setView(input)
            .setPositiveButton("Invite") { _, _ ->
                val uname = input.text.toString().trim()
                if (uname.isEmpty()) return@setPositiveButton
                lifecycleScope.launch {
                    try {
                        // Resolve username → user_id (optional, live-invite can accept username too)
                        RetrofitClient.functionsApi.liveInvite(mapOf("action" to "invite", "streamId" to (currentStream?.id ?: return@launch), "username" to uname))
                        // Switch mode to multi_host with shared comments when inviting
                        val sid = currentStream?.id
                        if (!sid.isNullOrEmpty()) {
                            try { RetrofitClient.functionsApi.setLiveMode(mapOf("streamId" to sid, "mode" to "multi_host", "commentScope" to "shared")) } catch (_: Exception) {}
                        }
                        Toast.makeText(this@LiveStreamActivity, "Invited", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        Toast.makeText(this@LiveStreamActivity, "Invite failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRequestsDialog() {
        lifecycleScope.launch {
            try {
                val resp = RetrofitClient.functionsApi.liveInviteRaw(mapOf("action" to "list", "streamId" to (currentStream?.id ?: return@launch)))
                if (!resp.isSuccessful) return@launch
                val arr = org.json.JSONArray(resp.body()?.string() ?: "[]")
                if (arr.length() == 0) {
                    Toast.makeText(this@LiveStreamActivity, "No requests", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val items = Array(arr.length()) { i ->
                    val obj = arr.getJSONObject(i)
                    val uname = obj.optJSONObject("profiles")?.optString("username") ?: obj.optString("invitee_id").take(6)
                    val id = obj.optString("id")
                    Pair(uname, id)
                }
                val names = items.map { it.first }.toTypedArray()
                AlertDialog.Builder(this@LiveStreamActivity)
                    .setTitle("Requests to join")
                    .setItems(names) { _, which ->
                        val inviteId = items[which].second
                        lifecycleScope.launch {
                            try {
                                RetrofitClient.functionsApi.liveInvite(mapOf("action" to "accept", "inviteId" to inviteId))
                                // Set mode to multi_host with shared comments for this live
                                val sid = currentStream?.id
                                if (!sid.isNullOrEmpty()) {
                                    try { RetrofitClient.functionsApi.setLiveMode(mapOf("streamId" to sid, "mode" to "multi_host", "commentScope" to "shared")) } catch (_: Exception) {}
                                }
                                Toast.makeText(this@LiveStreamActivity, "Accepted", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {}
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } catch (_: Exception) {}
        }
    }

    fun startLiveSession(
        title: String,
        description: String,
        categoryId: Int,
        tags: List<String>,
        accessType: String? = null,
        priceTokens: Int? = null,
        requiredPlanId: String? = null,
        isMatch: Boolean = false
    ) {
        val userId = AuthUtils.getCurrentUserId(this) ?: return
        lifecycleScope.launch {
            try {
                // If an active live already exists for this host, resume it instead of ending/creating.
                try {
                    val existing = RetrofitClient.liveStreamApi.getLiveStreamsByHosts(
                        select = "id,status",
                        hostFilter = "eq.$userId",
                        statusFilter = "eq.live",
                        order = "updated_at.desc"
                    )
                    val active = existing.firstOrNull()
                    if (active != null) {
                        handleDeepLinkStream(active.id)
                        return@launch
                    }
                } catch (_: Exception) { /* proceed to create */ }

                val resp = RetrofitClient.functionsApi.createLiveSession(
                    CreateLiveStreamRequest(userId, title, description, categoryId, tags)
                )
                val errorBody = resp.errorBody()?.string().orEmpty()
                
                if (resp.isSuccessful) {
                    currentStream = resp.body()
                    // Ensure stream is marked live (in case backend defaulted to 'scheduled')
                    currentStream?.let { ls ->
                        if (ls.status.lowercase() != "live") {
                            try {
                                val nowIso = java.time.Instant.now().toString()
                                val updates = mutableMapOf<String, Any>(
                                    "status" to "live",
                                    "started_at" to nowIso
                                )
                                accessType?.let { updates["access_type"] = it }
                                priceTokens?.let { updates["price"] = it }
                                requiredPlanId?.let { updates["required_plan_id"] = it }
                                RetrofitClient.functionsApi.updateLiveSession(
                                    id = ls.id,
                                    updates = updates
                                )
                            } catch (_: Exception) { }
                        }
                    }
                    liveTopBar.visibility = View.VISIBLE
                    liveTopBar.bringToFront()
                    if (isMatch) {
                        val sid = currentStream?.id
                        val host = AuthUtils.getCurrentUserId(this@LiveStreamActivity)
                        if (!sid.isNullOrEmpty() && !host.isNullOrEmpty()) {
                            // Switch this live to match mode with isolated comments and default duration
                            try {
                                val body = mutableMapOf<String, Any>("streamId" to sid, "mode" to "match", "commentScope" to "isolated")
                                val dur = currentStream?.matchDurationSec ?: 180
                                body["matchDurationSec"] = dur
                                body["matchScoring"] = (currentStream?.matchScoring ?: "tokens")
                                RetrofitClient.functionsApi.setLiveMode(body)
                            } catch (_: Exception) {}
                            // Open match setup to mirror web "This is a match"
                            try {
                                MatchSetupBottomSheetFragment
                                    .newInstance(sid, host)
                                    .show(supportFragmentManager, "MatchSetupBottomSheet")
                            } catch (_: Exception) { }
                        }
                    }
                    lifecycleScope.launch {
                        // Determine the host ID (fallback to current user if missing)
                        val hostId = currentStream?.hostId ?: AuthUtils.getCurrentUserId(this@LiveStreamActivity)!!
                        // Fetch and display host profile
                        val profiles = RetrofitClient.profileApi.getProfileByUserId("*", "eq.$hostId")
                        if (profiles.isNotEmpty()) {
                            val p = profiles[0]
                            tvStreamerName.text = (p.name ?: p.username).take(14)
                            ivStreamerImage.load(p.image)
                        }
                        // Display follower count (from profile metadata)
                        tvFollowerCount.text = getString(R.string.follower_count, profiles.getOrNull(0)?.followersCount?.let {
                            formatCount(it)
                        } ?: formatCount(0))
                        // Show follow/unfollow only for viewers (host cannot follow self)
                        val currentId = AuthUtils.getCurrentUserId(this@LiveStreamActivity)
                        if (currentId != null && currentId != hostId) {
                            val header = RetrofitClient.followsApi.isFollowingUser(
                                followedIdFilter = "eq.$hostId", followerIdFilter = "eq.$currentId"
                            ).headers()["Content-Range"]
                            var isFollowing = (header?.substringAfterLast("/")?.toIntOrNull() ?: 0) > 0
                            btnFollowStreamer.apply {
                                visibility = View.VISIBLE
                                setImageResource(if (isFollowing) R.drawable.unfollow else R.drawable.follow)
                                setOnClickListener {
                                    lifecycleScope.launch {
                                        if (isFollowing) {
                                            RetrofitClient.followsApi.unfollowUser(
                                                followedIdFilter = "eq.$hostId", followerIdFilter = "eq.$currentId"
                                            )
                                        } else {
                                            RetrofitClient.followsApi.followUser(
                                                mapOf("followed_id" to hostId, "follower_id" to currentId)
                                            )
                                        }
                                        // Reload profile to update follower count and follow state
                                        val updated = RetrofitClient.profileApi.getProfileByUserId(
                                            select = "*", userIdFilter = "eq.$hostId"
                                        ).firstOrNull()
                                        if (updated != null) {
                                            tvFollowerCount.text = getString(R.string.follower_count, formatCount(updated.followersCount ?: 0))
                                            isFollowing = updated.isFollowing ?: false
                                            setImageResource(if (isFollowing) R.drawable.unfollow else R.drawable.follow)
                                        }
                                    }
                                }
                            }
                        } else {
                            // Host view: show requests icon and poll pending
                            btnFollowStreamer.visibility = View.GONE
                            btnRequests.visibility = View.VISIBLE
                            btnRequests.setOnClickListener { showRequestsPanel() }
                            tvViewerCount.setOnClickListener { showRequestsPanel() }
                            launch {
                                while (isActive && !isEnded) {
                                    delay(3000)
                                    try {
                                        val resp = RetrofitClient.functionsApi.liveInviteRaw(mapOf("action" to "list", "streamId" to (currentStream?.id ?: return@launch)))
                                        if (!resp.isSuccessful) continue
                                        val body = resp.body()?.string() ?: "[]"
                                        val arr = org.json.JSONArray(body)
                            val pending = (0 until arr.length()).count {
                                val st = arr.getJSONObject(it).optString("status").lowercase()
                                st == "requested" || st == "pending" || st == "request"
                            }
                                        if (pending > 0) btnRequests.setColorFilter(android.graphics.Color.RED) else btnRequests.clearColorFilter()
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                        // Load and show initial comments and start polling for new comments
                        currentStream?.id?.let { sid ->
                            val initial = RetrofitClient.liveStreamApi.getLiveStreamComments(
                                select = "*,profile:profiles(*)",
                                streamFilter = "eq.$sid"
                            )
                val initialSorted = initial.sortedBy { it.createdAt }
                commentsAdapter.submitList(initialSorted)
                if (initialSorted.isNotEmpty()) rvLiveComments.scrollToPosition(initialSorted.size - 1)
                        commentsJob = lifecycleScope.launch {
                            while (isActive) {
                                delay(3000)
                                val updated = RetrofitClient.liveStreamApi.getLiveStreamComments(
                                    select = "*,profile:profiles(*)",
                                    streamFilter = "eq.$sid"
                                )
                        val updatedSorted = updated.sortedBy { it.createdAt }
                        commentsAdapter.submitList(updatedSorted)
                        if (updatedSorted.isNotEmpty()) rvLiveComments.scrollToPosition(updatedSorted.size - 1)
                            }
                        }
                        }
                    }
                    // Prefer LiveKit-managed camera; skip CameraX preview to avoid camera conflicts
                    if (!USE_LIVEKIT_CAMERA_PREVIEW) {
                        startCamera()
                    }
                    // Show host controls: switch camera and mute/unmute microphone
                    btnToggleMic.visibility = View.VISIBLE
                    if (USE_LIVEKIT_CAMERA_PREVIEW) {
                        // Show LiveKit-based flip (no CameraX)
                        btnSwitchCamera.visibility = View.VISIBLE
                        btnSwitchCamera.setOnClickListener {
                            try {
                                val localPubPair = liveKitRoom?.localParticipant?.videoTrackPublications?.firstOrNull()
                                val localTrack2 = localPubPair?.second as? LocalVideoTrack
                                localTrack2?.switchCamera()
                                isFrontFacing = !isFrontFacing
                    // Update preview mirroring when camera flips
                    previewView?.setMirror(isFrontFacing)
                            } catch (_: Exception) { }
                        }
                        // Camera on/off toggle
                        btnToggleCamera.visibility = View.VISIBLE
                        var isVideoEnabled = true
                        btnToggleCamera.setOnClickListener {
                            isVideoEnabled = !isVideoEnabled
                            lifecycleScope.launch {
                                liveKitRoom?.localParticipant?.setCameraEnabled(isVideoEnabled)
                            }
                            btnToggleCamera.setImageResource(
                                if (isVideoEnabled) android.R.drawable.ic_menu_view
                                else android.R.drawable.ic_menu_close_clear_cancel
                            )
                        }
                    } else {
                        // CameraX-based preview/flip (not used when LiveKit manages camera)
                        btnSwitchCamera.visibility = View.VISIBLE
                        btnToggleCamera.visibility = View.GONE
                        btnSwitchCamera.setOnClickListener {
                            currentLensFacing = if (currentLensFacing == CameraSelector.LENS_FACING_FRONT)
                                CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
                            startCamera(currentLensFacing)
                        }
                    }
                    btnToggleMic.setOnClickListener {
                        isMicEnabled = !isMicEnabled
                        lifecycleScope.launch {
                            liveKitRoom?.localParticipant?.setMicrophoneEnabled(isMicEnabled)
                        }
                        btnToggleMic.setImageResource(
                            if (isMicEnabled) android.R.drawable.ic_lock_silent_mode_off
                            else android.R.drawable.ic_lock_silent_mode
                        )
                    }
                    // Connect to LiveKit using v2 API (LiveKit.create + Room.connect)
                    val lkToken = resp.body()?.let { it.token ?: it.id }
                    if (!lkToken.isNullOrBlank()) {
                        try {
                            // configure RoomOptions (audio/video defaults + e2ee if needed)
                            val roomOptions = RoomOptions(
                                true,
                                false,
                                null,
                                LocalAudioTrackOptions(),
                                LocalVideoTrackOptions(),
                                null,
                                null
                            )
                            // create Room instance
                        val room = LiveKit.create(
                            this@LiveStreamActivity,
                            roomOptions,
                            LiveKitOverrides()
                        )
                        lifecycleScope.launch {
                            room.connect(
                                LiveKitConfig.WS_URL,
                                lkToken,
                                ConnectOptions()
                            )
                            // keep reference for host mic controls
                            liveKitRoom = room
                            // enable camera and microphone publishing (LiveKit manages camera capture)
                            try { room.localParticipant.setCameraEnabled(true) } catch (_: Exception) {}
                            try { room.localParticipant.setMicrophoneEnabled(true) } catch (_: Exception) {}

                            // Attach local preview to container using SurfaceViewRenderer
                            val container = findViewById<FrameLayout>(R.id.flLiveStream)
                            container.removeAllViews()
                            val preview = SurfaceViewRenderer(this@LiveStreamActivity)
                            preview.setScalingType(livekit.org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                            preview.layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                            // Mirror only for front-facing camera so host sees a natural preview
                            preview.setMirror(isFrontFacing)
                            container.addView(preview)
                            previewView = preview
                            // Initialize renderer and bind the first local video track (if available)
                            room.initVideoRenderer(preview)
                            val localPubPair = room.localParticipant.videoTrackPublications.firstOrNull()
                            val localTrack = localPubPair?.second as? LocalVideoTrack
                            localTrack?.addRenderer(preview)

                            // Poll DB viewer_count periodically and update UI
                            lifecycleScope.launch {
                                while (isActive) {
                                    delay(3000)
                                    try {
                                        val rows = RetrofitClient.liveStreamApi.getLiveStreamById("id,viewer_count", "eq.${currentStream?.id}")
                                        val vc = rows.firstOrNull()?.viewerCount ?: 0
                                        tvViewerCount.text = vc.toString()
                                    } catch (_: Exception) { }
                                }
                            }
                        }
                        } catch (e: Exception) {
                        }
                    }
                } else {
                    Toast.makeText(
                        this@LiveStreamActivity,
                        "Failed to start live stream: HTTP ${resp.code()}",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@LiveStreamActivity, e.message, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    fun scheduleLiveSession(
        title: String,
        description: String,
        categoryId: Int,
        tags: List<String>,
        accessType: String? = null,
        priceTokens: Int? = null,
        requiredPlanId: String? = null,
        scheduledAtIso: String
    ) {
        val userId = AuthUtils.getCurrentUserId(this) ?: return
        lifecycleScope.launch {
            try {
                val body = mutableMapOf<String, Any?>(
                    "host_id" to userId,
                    "title" to title,
                    "description" to description,
                    "status" to "scheduled",
                    "started_at" to scheduledAtIso,
                    "category_id" to categoryId,
                    "tags" to tags
                )
                when (accessType) {
                    "paid" -> { body["access_type"] = "paid"; body["price"] = priceTokens }
                    "subscription" -> { body["access_type"] = "subscription"; body["required_plan_id"] = requiredPlanId }
                    else -> body["access_type"] = "free"
                }
                val resp = RetrofitClient.liveStreamApi.createLiveStreamRaw(body = body)
                if (resp.isSuccessful) {
                    Toast.makeText(this@LiveStreamActivity, R.string.scheduled_success, Toast.LENGTH_SHORT).show()
                    try { RetrofitClient.functionsApi.notifyScheduledLive(mapOf("liveStreamId" to (resp.body()?.firstOrNull()?.id ?: ""))) } catch (_: Exception) {}
                } else {
                    Toast.makeText(this@LiveStreamActivity, R.string.failed_to_schedule, Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this@LiveStreamActivity, R.string.failed_to_schedule, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCamera(lensFacing: Int = CameraSelector.LENS_FACING_FRONT) {
        val previewView = PreviewView(this)
        val container = findViewById<FrameLayout>(R.id.flLiveStream)
        container.removeAllViews()
        container.addView(
            previewView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, selector, preview)
            } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(this))
    }

    private fun endLiveSession() {
        val streamId = currentStream?.id ?: return
        lifecycleScope.launch {
            try {
                val resp = RetrofitClient.functionsApi.updateLiveSession(
                    streamId,
                    mapOf("status" to "ended")
                )
                if (resp.isSuccessful) finish() else
                    Toast.makeText(this@LiveStreamActivity, R.string.failed_end_stream, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@LiveStreamActivity, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startInvitePolling(streamId: String) {
        invitePollJob?.cancel()
        val userId = AuthUtils.getCurrentUserId(this) ?: return
        invitePollJob = lifecycleScope.launch {
            // Resolve room_id for the host stream once (best-effort)
            var roomId: String? = null
            try {
                val rows = RetrofitClient.liveStreamApi.getLiveStreamById("id,room_id", "eq.$streamId")
                roomId = rows.firstOrNull()?.roomId
            } catch (_: Exception) {}
            while (isActive) {
                try {
                    // If we know the room, look for my provisioned stream in that room
                    val guestId = if (!roomId.isNullOrEmpty()) {
                        try {
                            val siblings = RetrofitClient.liveStreamApi.getLiveStreamsByRoomId("id,host_id,status,started_at", "eq.$roomId")
                            siblings.firstOrNull { it.hostId == userId && (it.status == "live" || it.status == "scheduled") }?.id ?: ""
                        } catch (_: Exception) { "" }
                    } else ""
                    if (guestId.isNotEmpty()) {
                        withContext(Dispatchers.Main) { handleDeepLinkStream(guestId) }
                        invitePollJob?.cancel(); break
                    }
                } catch (_: Exception) {}
                delay(1500)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        currentStream?.id?.let { sid -> startCommentsPolling(sid) }
    }

    override fun onPause() {
        super.onPause()
        commentsJob?.cancel()
    }

    private fun sendLiveComment() {
        val etLiveComment = findViewById<EditText>(R.id.etLiveComment)
        val commentText = etLiveComment.text.toString().trim()
        val userId = AuthUtils.getCurrentUserId(this) ?: return
        val streamId = currentStream?.id ?: return
        if (commentText.isEmpty()) return

        lifecycleScope.launch {
            try {
                val resp = RetrofitClient.liveStreamApi.createLiveStreamComment(
                    select = "*,profile:profiles(*)",
                    LiveStreamCommentRequest(streamId, null, userId, commentText)
                )
                if (resp.isSuccessful) {
                    etLiveComment.setText("")
                    // refresh comments list
                    currentStream?.id?.let { sid ->
                        val updated = RetrofitClient.liveStreamApi.getLiveStreamComments(
                            select = "*,profile:profiles(*)",
                            streamFilter = "eq.$sid"
                        )
                        val updatedSorted = updated.sortedBy { it.createdAt }
                        commentsAdapter.submitList(updatedSorted)
                        if (updatedSorted.isNotEmpty()) rvLiveComments.scrollToPosition(updatedSorted.size - 1)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun handleTap(x: Float, y: Float) {
        // Host cannot tap
        val me = AuthUtils.getCurrentUserId(this)
        val host = currentStream?.hostId
        if (me != null && host != null && me == host) return
        // Local floating heart
        spawnHeart(x, y)
        localTapCount += 1
        // tapsDetails: show progress after 30, hide at 300; show fraction in tapsCount
        if (localTapCount >= 30 && localTapCount < 300) {
            tapsProgressBar?.visibility = View.VISIBLE
            tapsFractionTv?.visibility = View.VISIBLE
        }
        tapsProgressBar?.progress = kotlin.math.min(300, localTapCount)
        tapsFractionTv?.text = "${kotlin.math.min(localTapCount, 300)}/300"
        // Do not change the total taps label locally; show server-authoritative total via polling
        // One-time auto-like comment – only if we have a stream id available; else defer
        if (!likeCommentSent) {
            val userId = AuthUtils.getCurrentUserId(this)
            val streamId = currentStream?.id
            if (userId != null && !streamId.isNullOrEmpty()) {
                likeCommentSent = true
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        RetrofitClient.liveStreamApi.createLiveStreamComment(
                            select = "*,profile:profiles(*)",
                            comment = LiveStreamCommentRequest(streamId, null, userId, "liked this live")
                        )
                    } catch (_: Exception) { }
                }
            }
        }
        // If stream id isn't ready yet, queue the tap to flush later atomically with server update
        val sid = currentStream?.id
        if (sid.isNullOrEmpty()) {
            queuedTapCount += 1
            return
        }
        // Increment taps in DB, then broadcast a LiveKit 'tap' only on success
        lifecycleScope.launch(Dispatchers.IO) {
            var ok = false
            try {
                val resp = RetrofitClient.liveStreamApi.incrementLiveTaps(mapOf("in_stream_id" to sid, "in_inc" to 1))
                ok = resp.isSuccessful
            } catch (_: Exception) {
                try {
                    val url = club.gifters.giftersclub.SupabaseConfig.SUPABASE_URL + "/rest/v1/rpc/increment_live_taps"
                    val body = org.json.JSONObject().apply { put("in_stream_id", sid); put("in_inc", 1) }.toString()
                    val req = okhttp3.Request.Builder()
                        .url(url)
                        .post(body.toRequestBody("application/json".toMediaTypeOrNull()))
                        .addHeader("apikey", club.gifters.giftersclub.SupabaseConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + (getSharedPreferences("supabase", Context.MODE_PRIVATE).getString("access_token", null) ?: club.gifters.giftersclub.SupabaseConfig.SUPABASE_ANON_KEY))
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "application/json")
                        .addHeader("Prefer", "params=single-object,return=representation")
                        .build()
                    val client = okhttp3.OkHttpClient()
                    client.newCall(req).execute().use { ok = it.isSuccessful }
                } catch (_: Exception) { ok = false }
            }
            if (ok) {
                // Bump local total immediately
                withContext(Dispatchers.Main) {
                    lastTapsValue += 1
                    tvTapCount?.text = formatCount(lastTapsValue)
                    tapsCountTv?.text = formatCount(lastTapsValue)
                }
                // Fetch authoritative taps to reconcile quickly
                try {
                    val rows = RetrofitClient.liveStreamApi.getLiveStreamById("id,taps", "eq.$sid")
                    val taps = rows.firstOrNull()?.taps ?: -1
                    if (taps >= 0) withContext(Dispatchers.Main) {
                        lastTapsValue = taps
                        tvTapCount?.text = formatCount(taps)
                        tapsCountTv?.text = formatCount(taps)
                    }
                } catch (_: Exception) { }
                try {
                    val room = liveKitRoom
                    if (room != null) {
                        val json = JSONObject().apply { put("type", "tap"); put("ts", System.currentTimeMillis() / 1000) }
                        withContext(Dispatchers.Main) {
                            try { room.localParticipant.publishData(json.toString().toByteArray(Charsets.UTF_8)) } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        // Consume future server deltas for my own taps to avoid redundant bottom-right hearts
        pendingLocalTapIncrements += 1
        // Simple explosion when reaching 300
        if (localTapCount == 300) {
            // Hide tapsDetails progress/fraction and spawn chaff particles near the counter
            tapsProgressBar?.visibility = View.GONE
            tapsFractionTv?.visibility = View.GONE
            spawnChaffAtCounter()
        }
    }

    private fun spawnHeart(x: Float, y: Float) {
        val root = this@LiveStreamActivity.findViewById<FrameLayout>(R.id.flLiveStream)
        val tv = TextView(this).apply {
            text = "❤"
            textSize = 24f
            setTextColor(0xFFFF0000.toInt())
            x.also { this.x = it }
            y.also { this.y = it }
        }
        root.addView(tv)
        tv.animate().translationYBy(-200f).alpha(0f).setDuration(1200).withEndAction { root.removeView(tv) }.start()
    }

    private fun spawnChaffAtCounter() {
        val root = findViewById<FrameLayout>(R.id.flLiveStream)
        val loc = IntArray(2)
        // Fallback position if view not laid out
        var originX = root.width - 160f
        var originY = (64 * resources.displayMetrics.density)
        try {
            (tvTapCount as? View)?.getLocationOnScreen(loc)
            originX = (loc[0]).toFloat()
            originY = (loc[1]).toFloat()
        } catch (_: Exception) {}
        for (i in 0 until 24) {
            val dot = TextView(this).apply { text = "•"; textSize = 12f; setTextColor(0xFFFFFFFF.toInt()) }
            dot.x = originX + (0..80).random()
            dot.y = originY
            root.addView(dot)
            dot.animate()
                .translationYBy((140..260).random().toFloat())
                .translationXBy(((-30)..30).random().toFloat())
                .alpha(0f)
                .setDuration(1300)
                .withEndAction { root.removeView(dot) }
                .setStartDelay((i * 20).toLong())
                .start()
        }
    }

    /** Flush any queued taps that happened before LiveKit was ready: write to DB and emit hearts. */
    private fun flushQueuedTaps() {
        val count = queuedTapCount
        val sid = currentStream?.id
        if (count <= 0 || sid.isNullOrEmpty()) return
        queuedTapCount = 0
        // Increment DB by the queued count (single RPC)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val resp = RetrofitClient.liveStreamApi.incrementLiveTaps(mapOf("in_stream_id" to sid, "in_inc" to count))
                if (!resp.isSuccessful) throw Exception("rpc failed")
            } catch (_: Exception) {
                try {
                    val url = club.gifters.giftersclub.SupabaseConfig.SUPABASE_URL + "/rest/v1/rpc/increment_live_taps"
                    val body = org.json.JSONObject().apply { put("in_stream_id", sid); put("in_inc", count) }.toString()
                    val req = okhttp3.Request.Builder()
                        .url(url)
                        .post(body.toRequestBody("application/json".toMediaTypeOrNull()))
                        .addHeader("apikey", club.gifters.giftersclub.SupabaseConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + (getSharedPreferences("supabase", Context.MODE_PRIVATE).getString("access_token", null) ?: club.gifters.giftersclub.SupabaseConfig.SUPABASE_ANON_KEY))
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "application/json")
                        .addHeader("Prefer", "params=single-object,return=representation")
                        .build()
                    val client = okhttp3.OkHttpClient()
                    client.newCall(req).execute().use { }
                } catch (_: Exception) { }
            }
        }
        // If connected, publish `count` tap signals so others animate hearts
        val room = liveKitRoom
        if (room != null) {
            repeat(count) {
                val json = JSONObject().apply { put("type", "tap"); put("ts", System.currentTimeMillis() / 1000) }
                lifecycleScope.launch { try { room.localParticipant.publishData(json.toString().toByteArray(Charsets.UTF_8)) } catch (_: Exception) {} }
            }
        }
        // No local counter changes; server updates drive the UI.
    }

    private fun initiateTopup(userId: String, amount: Int) {
        BillingManager.launchPurchase(this@LiveStreamActivity, amount)
    }

    /**
     * Format large counts into human-readable short form (e.g. 1.2k, 3.4m).
     */
    private fun formatCount(count: Int): String {
        return when {
            count >= 1_000_000 -> String.format("%.1fm", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fk", count / 1_000.0)
            else -> count.toString()
        }
    }
}
