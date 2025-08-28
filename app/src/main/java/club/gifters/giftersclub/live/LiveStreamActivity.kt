package club.gifters.giftersclub.live

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.util.Base64
import kotlinx.coroutines.withContext
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
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
import club.gifters.giftersclub.payments.PaymentWebViewActivity
import coil.load
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
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
import org.json.JSONObject
import java.text.NumberFormat
import retrofit2.HttpException

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
    private var isEnded: Boolean = false
    private var lastGiftAt: String? = null
    private val giftCombos = mutableMapOf<String, Pair<Int, Int>>() // key -> (count, commentIndex)
    private var paywallPlanId: String? = null
    private var paywallPlanTokens: Int = 0
    // Multi-host: map participant/track to its renderer for tiling
    private val videoViews: MutableMap<String, SurfaceViewRenderer> = mutableMapOf()

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

    // Battle/Match state (multi-host matches)
    private var isMatch: Boolean = false
    private var battleId: String? = null
    private var battleStartedAt: String? = null
    private val matchParticipants: MutableList<club.gifters.giftersclub.model.BattleParticipant> = mutableListOf()
    private val userIdToStreamId: MutableMap<String, String> = mutableMapOf() // also stores usernames under key "uname:<userId>"
    private val tokenTallies: MutableMap<String, Int> = mutableMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_stream)
        // Deep-link support: if URL is https://gifters.club/live/{streamId}
        val deepId = intent.data?.lastPathSegment?.takeIf { it.isNotEmpty() }
        deepId?.let { handleDeepLinkStream(it) }
        // comments list overlay (bottom-up) – max half-screen height, bring above video
        rvLiveComments = findViewById<RecyclerView>(R.id.rvLiveComments).also { rv ->
            commentsAdapter = CommentsAdapter()
            rv.layoutManager = LinearLayoutManager(this).apply { reverseLayout = false }
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

        if (deepId == null) {
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
                tvViewerCount.setOnClickListener {
                    lifecycleScope.launch {
                        try {
                            val resp = RetrofitClient.functionsApi.liveInviteRaw(mapOf("action" to "list", "streamId" to (currentStream?.id ?: return@launch)))
                            if (!resp.isSuccessful) return@launch
                            val body = resp.body()?.string() ?: return@launch
                            val arr = org.json.JSONArray(body)
                            val items = Array(arr.length()) { i ->
                                val obj = arr.getJSONObject(i)
                                val uname = obj.optJSONObject("profiles")?.optString("username") ?: obj.optString("invitee_id").take(6)
                                val id = obj.optString("id")
                                Pair(uname, id)
                            }
                            if (items.isEmpty()) {
                                Toast.makeText(this@LiveStreamActivity, "No requests", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            val names = items.map { it.first }.toTypedArray()
                            androidx.appcompat.app.AlertDialog.Builder(this@LiveStreamActivity)
                                .setTitle("Requests to join")
                                .setItems(names) { _, which ->
                                    val inviteId = items[which].second
                                    lifecycleScope.launch {
                                        try { RetrofitClient.functionsApi.liveInvite(mapOf("action" to "accept", "inviteId" to inviteId)) } catch (_: Exception) {}
                                        Toast.makeText(this@LiveStreamActivity, "Accepted", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .setNegativeButton(android.R.string.cancel, null)
                                .show()
                        } catch (_: Exception) { }
                    }
                }
                // Show invite icon in matches
                btnInvite.visibility = if (isMatch) View.VISIBLE else View.GONE
            }
            // Load and show comments
            currentStream?.id?.let { sid ->
                val initial = RetrofitClient.liveStreamApi.getLiveStreamComments(
                    select = "*,profile:profiles(*)",
                    streamFilter = "eq.$sid"
                )
                commentsAdapter.submitList(initial)
                if (initial.isNotEmpty()) rvLiveComments.scrollToPosition(initial.size - 1)
                // Cohost comments: if room_id exists, fetch siblings and merge
                commentsJob = lifecycleScope.launch {
                    val streams = try {
                        val rows = RetrofitClient.liveStreamApi.getLiveStreamById("id,room_id", "eq.$sid")
                        val room = rows.firstOrNull()?.roomId
                        if (!room.isNullOrEmpty()) RetrofitClient.liveStreamApi.getLiveStreamsByRoomId("id", "eq.$room").map { it.id } else listOf(sid)
                    } catch (_: Exception) { listOf(sid) }
                    while (isActive) {
                        delay(2000)
                        val merged = mutableListOf<club.gifters.giftersclub.model.LiveStreamComment>()
                        for (s in streams) {
                            try {
                                merged += RetrofitClient.liveStreamApi.getLiveStreamComments("*,profile:profiles(*)", "eq.$s")
                            } catch (_: Exception) {}
                        }
                        commentsAdapter.submitList(merged.sortedBy { it.createdAt })
                        if (merged.isNotEmpty()) rvLiveComments.scrollToPosition(merged.size - 1)
                    }
                }
            }
        }

        // Prepare video container (we create one renderer per remote video)
        val container = findViewById<FrameLayout>(R.id.flLiveStream)
        container.removeAllViews()
        videoViews.values.forEach { it.release() }
        videoViews.clear()

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
                    room.connect(LiveKitConfig.WS_URL, lkToken, ConnectOptions())
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
                                    }
                                } catch (_: Exception) { }
                            }
                        }
                    }
                    // Attach already-subscribed remote videos
                    room.remoteParticipants.values.forEach { participant ->
                        participant.videoTrackPublications.forEach { pubPair ->
                            val track = pubPair.second as? RemoteVideoTrack
                            if (track != null) {
                                val key = "${participant.sid}_${track.sid}"
                                addVideoTile(container, key, track)
                            }
                        }
                    }
                    // Listen for subscribe/unsubscribe to manage tiles
                    launch {
                        room.events.collect { event ->
                            when (event) {
                                is RoomEvent.TrackSubscribed -> {
                                    val rt = event.track as? RemoteVideoTrack
                                    // Key by participant + track sid (avoid relying on publication field)
                                    if (rt != null) {
                                        val key = "${event.participant.sid}_${rt.sid}"
                                        addVideoTile(container, key, rt)
                                    }
                                }
                                is RoomEvent.TrackUnsubscribed -> {
                                    val rt = event.track as? RemoteVideoTrack
                                    if (rt != null) {
                                        val key = "${event.participant.sid}_${rt.sid}"
                                        removeVideoTile(container, key)
                                    }
                                }
                                is RoomEvent.ParticipantDisconnected -> {
                                    // remove all tiles for this participant
                                    val keys = videoViews.keys.filter { it.startsWith("${event.participant.sid}_") }
                                    keys.forEach { k -> removeVideoTile(container, k) }
                                }
                                else -> Unit
                            }
                        }
                    }
                    // Start status polling to exit when stream ends
                    val sid = currentStream?.id
                    if (sid != null) {
                        statusJob?.cancel()
                        statusJob = launch {
                            while (isActive && !isEnded) {
                                delay(3000)
                                try {
                                    val rows = RetrofitClient.liveStreamApi.getLiveStreamById("id,status,viewer_count", "eq.$sid")
                                    val row = rows.firstOrNull()
                                    if (row != null) {
                                        tvViewerCount.text = (row.viewerCount).toString()
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
                                            val giftName = e.gift?.name ?: "gift"
                                            val key = e.gifterId + "_" + e.giftId
                                            val combo = giftCombos[key]
                                            if (combo != null) {
                                                val newCount = combo.first + 1
                                                val idx = combo.second
                                                commentsAdapter.updateContentAt(idx, "sent ${newCount} $giftName combo")
                                                giftCombos[key] = Pair(newCount, idx)
                                            } else {
                                                commentsAdapter.addSyntheticComment(e.gifterId, "sent a $giftName", e.gifter)
                                                val idx = commentsAdapter.itemCount - 1
                                                giftCombos[key] = Pair(1, idx)
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
        try { liveKitRoom?.initVideoRenderer(v) } catch (_: Exception) {}
        v.setZOrderMediaOverlay(true)
        val tile = FrameLayout(this)
        val tileLp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        tile.layoutParams = tileLp
        // set border (1dp gray for non-match; colored when match)
        val strokeColor = if (isMatch) android.graphics.Color.TRANSPARENT else 0x55FFFFFF.toInt()
        val gd = android.graphics.drawable.GradientDrawable()
        gd.setColor(0x00000000)
        gd.setStroke((resources.displayMetrics.density).toInt(), strokeColor)
        gd.cornerRadius = 8 * resources.displayMetrics.density
        tile.background = gd
        tile.addView(v, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        container.addView(tile)
        layoutTiles(container)
        videoViews[key] = v
        track.addRenderer(v)
    }

    private fun removeVideoTile(container: FrameLayout, key: String) {
        val v = videoViews.remove(key) ?: return
        try { v.release() } catch (_: Exception) {}
        container.removeView(v)
        layoutTiles(container)
    }

    private fun layoutTiles(container: FrameLayout) {
        val n = container.childCount
        if (n == 0) return
        val cols = kotlin.math.ceil(kotlin.math.sqrt(n.toDouble())).toInt()
        val rows = kotlin.math.ceil(n / cols.toDouble()).toInt()
        val w = container.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val h = container.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val cellW = w / cols
        val cellH = h / rows
        for (i in 0 until n) {
            val child = container.getChildAt(i)
            val r = i / cols
            val c = i % cols
            val left = c * cellW
            val top = r * cellH
            child.layout(left, top, left + cellW, top + cellH)
        }
        container.requestLayout()
    }

    /**
     * Switch primary focus to a participant's stream. For now, this is a stub
     * until we add explicit track pinning/selection logic.
     */
    private fun switchTo(streamId: String) {
        Toast.makeText(this, "Switching to stream $streamId", Toast.LENGTH_SHORT).show()
        // TODO: Implement track pinning/layout prioritization using mapping of streamId -> participant/track.
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
                // Insert a comment row: user_id = gifterId; content = "sent a {giftName}"
                try {
                    RetrofitClient.liveStreamApi.createLiveStreamComment(
                        select = "*,profile:profiles(*)",
                        comment = LiveStreamCommentRequest(
                            liveStreamId = stream.id,
                            parentCommentId = null,
                            userId = gifterId,
                            content = "sent a ${gift.name}"
                        )
                    )
                } catch (_: Exception) { }
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
                        resumeHostSession(ls)
                        return@launch
                    }
                    // viewer flow: register as viewer then init viewer UI
                    RetrofitClient.liveStreamApi.joinLiveStream(
                        select = "*",
                        viewer = LiveStreamViewerRequest(ls.id, currentId)
                    )
                }
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
            ActivityCompat.requestPermissions(
                this@LiveStreamActivity,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
            return
        }
        currentStream = live
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
            // load comments and poll
            live.id.let { sid ->
                val initial = RetrofitClient.liveStreamApi.getLiveStreamComments(
                    select = "*,profile:profiles(*)",
                    streamFilter = "eq.$sid"
                )
                commentsAdapter.submitList(initial)
                if (initial.isNotEmpty()) rvLiveComments.scrollToPosition(initial.size - 1)
                commentsJob = launch {
                    while (isActive) {
                        delay(3000)
                        val updated = RetrofitClient.liveStreamApi.getLiveStreamComments(
                            select = "*,profile:profiles(*)",
                            streamFilter = "eq.$sid"
                        )
                        commentsAdapter.submitList(updated)
                        if (updated.isNotEmpty()) rvLiveComments.scrollToPosition(updated.size - 1)
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
        preview.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        preview.setMirror(isFrontFacing)
        container.addView(preview)
        previewView = preview

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
            lifecycleScope.launch {
                try {
                    room.connect(LiveKitConfig.WS_URL, lkToken, ConnectOptions())
                    // enable camera/mic and attach local preview
                    room.localParticipant.setCameraEnabled(true)
                    room.localParticipant.setMicrophoneEnabled(true)
                    room.initVideoRenderer(preview)
                    val localPubPair = room.localParticipant.videoTrackPublications.firstOrNull()
                    val localTrack = localPubPair?.second as? LocalVideoTrack
                    localTrack?.addRenderer(preview)
                } catch (_: Exception) { }
                // Load battle state after connect
                lifecycleScope.launch { loadBattleState() }
            }
            // attach remote participants tracks if any
            room.remoteParticipants.values.forEach { participant ->
                participant.videoTrackPublications.forEach { (_, track) ->
                    (track as? RemoteVideoTrack)?.addRenderer(preview)
                }
            }
            // subscribe to new participants
            lifecycleScope.launch {
                room.events.collect { evt ->
                    if (evt is RoomEvent.TrackSubscribed && evt.track is RemoteVideoTrack) {
                        (evt.track as RemoteVideoTrack).addRenderer(preview)
                    }
                }
            }
            // status polling to end when stream ends
            statusJob?.cancel()
            statusJob = lifecycleScope.launch(Dispatchers.IO) {
                while (isActive) {
                    delay(3000)
                    val rows = RetrofitClient.liveStreamApi.getLiveStreamById(
                        "id,status,viewer_count", "eq.${live.id}"
                    )
                    val row = rows.firstOrNull() ?: break
                    withContext(Dispatchers.Main) {
                        tvViewerCount.text = row.viewerCount.toString()
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
            renderMatchOverlay()
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
                            try { RetrofitClient.functionsApi.liveInvite(mapOf("action" to "accept", "inviteId" to inviteId)) } catch (_: Exception) {}
                            Toast.makeText(this@LiveStreamActivity, "Accepted", Toast.LENGTH_SHORT).show()
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
        requiredPlanId: String? = null
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
                            btnFollowStreamer.visibility = View.GONE
                        }
                        // Load and show initial comments and start polling for new comments
                        currentStream?.id?.let { sid ->
                            val initial = RetrofitClient.liveStreamApi.getLiveStreamComments(
                                select = "*,profile:profiles(*)",
                                streamFilter = "eq.$sid"
                            )
                            commentsAdapter.submitList(initial)
                            if (initial.isNotEmpty()) rvLiveComments.scrollToPosition(initial.size - 1)
                            commentsJob = lifecycleScope.launch {
                                while (isActive) {
                                    delay(3000)
                                    val updated = RetrofitClient.liveStreamApi.getLiveStreamComments(
                                        select = "*,profile:profiles(*)",
                                        streamFilter = "eq.$sid"
                                    )
                                    commentsAdapter.submitList(updated)
                                    if (updated.isNotEmpty()) rvLiveComments.scrollToPosition(updated.size - 1)
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
                            room.localParticipant.setCameraEnabled(true)
                            room.localParticipant.setMicrophoneEnabled(true)

                            // Attach local preview to container using SurfaceViewRenderer
                            val container = findViewById<FrameLayout>(R.id.flLiveStream)
                            container.removeAllViews()
                            val preview = SurfaceViewRenderer(this@LiveStreamActivity)
                            preview.layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
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
            while (isActive) {
                try {
                    // fetch invites filtered via REST: invitee_id=eq.userId & streamId
                    val resp = RetrofitClient.functionsApi.liveInviteRaw(mapOf("action" to "list", "streamId" to streamId))
                    if (resp.isSuccessful) {
                        val body = resp.body()?.string() ?: "[]"
                        val arr = org.json.JSONArray(body)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            if (obj.optString("invitee_id") == userId && obj.optString("status") == "accepted") {
                                // Get guest token and re-connect as publisher
                                try {
                                    val tk = RetrofitClient.functionsApi.liveSessionAction(mapOf("action" to "token", "streamId" to streamId, "type" to "guest"))
                                    if (tk.isSuccessful) {
                                        val token = tk.body()?.get("token") as? String
                                        if (!token.isNullOrEmpty()) {
                                            try { liveKitRoom?.disconnect() } catch (_: Exception) {}
                                            val roomOptions = RoomOptions(true, true, null, LocalAudioTrackOptions(), LocalVideoTrackOptions(), null, null)
                                            val room = LiveKit.create(this@LiveStreamActivity, roomOptions, LiveKitOverrides())
                                            liveKitRoom = room
                                            room.connect(LiveKitConfig.WS_URL, token, ConnectOptions())
                                            room.localParticipant.setCameraEnabled(true)
                                            room.localParticipant.setMicrophoneEnabled(true)
                                            invitePollJob?.cancel()
                                            break
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                } catch (_: Exception) {}
                delay(3000)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        commentsJob?.start()  // ensure polling resumes if already started
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
                        commentsAdapter.submitList(updated)
                        if (updated.isNotEmpty()) rvLiveComments.scrollToPosition(updated.size - 1)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun initiateTopup(userId: String, amount: Int) {
        lifecycleScope.launch {
            // Fetch user's email for Flutterwave (customer_email is required)
            val email: String = try {
                val profs = RetrofitClient.profileApi.getProfileByUserId("*", "eq.$userId")
                profs.firstOrNull()?.email?.takeIf { it.isNotBlank() } ?: "${userId}@gifters.club"
            } catch (_: Exception) { "${userId}@gifters.club" }
            var lastTxId: String? = null
            try {
                val resp = RetrofitClient.tokenApi.recordTokenTransaction(
                    mapOf(
                        "user_id" to userId,
                        "transaction_type" to "purchase",
                        "tokens" to amount,
                        "kes_amount" to amount,
                        "flutterwave_transaction_id" to "topup_${userId}_${System.currentTimeMillis()}",
                        "flutterwave_transaction_status" to "initiated"
                    )
                )
                if (resp.isSuccessful) {
                    lastTxId = resp.body()?.firstOrNull()?.id
                }
            } catch (_: Exception) {
            }
            if (!lastTxId.isNullOrBlank()) {
                PaymentWebViewActivity.start(this@LiveStreamActivity, userId, email, amount,
                    "topup_${userId}_${System.currentTimeMillis()}", lastTxId!!)
            } else {
                Toast.makeText(this@LiveStreamActivity, R.string.failed_to_initiate_purchase,
                    Toast.LENGTH_SHORT).show()
            }
        }
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
