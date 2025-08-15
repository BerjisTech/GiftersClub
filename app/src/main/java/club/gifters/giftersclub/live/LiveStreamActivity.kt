package club.gifters.giftersclub.live

import android.content.Context
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import club.gifters.giftersclub.BaseActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.Gift
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.payments.PaymentWebViewActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.launch
import android.util.Log
import org.json.JSONObject
import java.text.NumberFormat
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import club.gifters.giftersclub.model.LiveStreamCommentRequest

import android.Manifest
import android.content.pm.PackageManager
import android.widget.ImageButton
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.LinearLayout
import com.google.android.material.imageview.ShapeableImageView
import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.LiveKitConfig
import club.gifters.giftersclub.model.CreateLiveStreamRequest
import club.gifters.giftersclub.model.LiveStream
import coil.load
import com.google.android.material.button.MaterialButton
import io.livekit.android.LiveKit
import io.livekit.android.ConnectOptions
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.room.track.LocalAudioTrackOptions
import io.livekit.android.room.track.LocalVideoTrackOptions

/**
 * Activity displaying and managing a live streaming session (camera preview, comments, and gifts).
 */
class LiveStreamActivity : BaseActivity() {

    companion object {
        private const val TAG = "LiveStreamActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        private const val REQUEST_CODE_PERMISSIONS = 1001
    }

    private lateinit var rvLiveComments: RecyclerView
    private lateinit var commentsAdapter: CommentsAdapter
    private lateinit var btnFollowStreamer: MaterialButton
    private lateinit var tvFollowerCount: TextView

    private var currentStream: LiveStream? = null

    // UI references for dynamic live stream controls
    private lateinit var liveTopBar: ConstraintLayout
    private lateinit var ivStreamerImage: ShapeableImageView
    private lateinit var tvStreamerName: TextView
    private lateinit var btnCloseLive: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_stream)
        // comments list overlay (bottom-up) – max half-screen height, bring above video
        rvLiveComments = findViewById<RecyclerView>(R.id.rvLiveComments).also { rv ->
            commentsAdapter = CommentsAdapter()
            rv.layoutManager = LinearLayoutManager(this).apply { reverseLayout = true }
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
        val btnEndLive = findViewById<MaterialButton>(R.id.btnEndLive)
        // follower count & follow button
        tvFollowerCount = findViewById(R.id.tvFollowerCount)
        btnFollowStreamer = findViewById(R.id.btnFollowStreamer)

        // Request camera and audio permissions
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        } else {
            showCreateStreamDialog()
        }

        // Bottom sheet for gifts, hidden initially until user clicks gift icon
        val btnOpenGifts = findViewById<ImageView>(R.id.btnOpenGifts)
        val flGiftsBottomSheet = findViewById<FrameLayout>(R.id.flGiftsBottomSheet)
        val giftsBottomSheetBehavior = BottomSheetBehavior.from(flGiftsBottomSheet).apply {
            isHideable = true
            state = BottomSheetBehavior.STATE_HIDDEN
        }
        btnOpenGifts.setOnClickListener {
            giftsBottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
        }

        // End stream when user taps close; ask for confirmation
        val endDialog = AlertDialog.Builder(this)
            .setTitle(R.string.end_live_stream)
            .setMessage(R.string.confirm_end_live_stream)
            .setPositiveButton(R.string.yes) { _, _ -> endLiveSession() }
            .setNegativeButton(R.string.no, null)
        btnEndLive.setOnClickListener { endDialog.show() }
        btnCloseLive.setOnClickListener { finish() }

        // Enter key sends comment
        val etLiveComment = findViewById<EditText>(R.id.etLiveComment)
        etLiveComment.imeOptions = EditorInfo.IME_ACTION_SEND
        etLiveComment.setRawInputType(InputType.TYPE_CLASS_TEXT)
        etLiveComment.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)
            ) {
                sendLiveComment()
                true
            } else false
        }

        // Existing UI setup for gifts carousel, comments, and viewer count
        val rvLiveGifts = findViewById<RecyclerView>(R.id.rvLiveGifts)
        rvLiveGifts.layoutManager = GridLayoutManager(this, 4)
        val giftsAdapter = LiveGiftAdapter { gift: Gift ->
            // TODO: handle gift selection
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

        // Sort toggle: default=original (popularity/random), asc, desc
        val flSortGifts = findViewById<FrameLayout>(R.id.flSortGifts)
        val ivSortAsc = findViewById<ImageView>(R.id.ivSortAsc)
        val ivSortDesc = findViewById<ImageView>(R.id.ivSortDesc)
        ivSortAsc.visibility = View.GONE
        ivSortDesc.visibility = View.GONE
        var sortState = 0
        flSortGifts.setOnClickListener {
            sortState = (sortState + 1) % 3
            when (sortState) {
                0 -> {
                    giftsAdapter.submitList(originalGifts)
                    ivSortAsc.visibility = View.GONE
                    ivSortDesc.visibility = View.GONE
                }
                1 -> {
                    giftsAdapter.submitList(originalGifts.sortedBy { it.tokens })
                    ivSortAsc.visibility = View.VISIBLE
                    ivSortDesc.visibility = View.GONE
                }
                else -> {
                    giftsAdapter.submitList(originalGifts.sortedByDescending { it.tokens })
                    ivSortAsc.visibility = View.GONE
                    ivSortDesc.visibility = View.VISIBLE
                }
            }
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
        val view = layoutInflater.inflate(R.layout.dialog_create_stream, null)
        val etTitle = view.findViewById<EditText>(R.id.etStreamTitle)
        val etDesc = view.findViewById<EditText>(R.id.etStreamDescription)
        AlertDialog.Builder(this)
            .setTitle(R.string.start_live_stream)
            .setView(view)
            .setPositiveButton(R.string.start) { _, _ ->
                val title = etTitle.text.toString().trim()
                val desc = etDesc.text.toString().trim()
                if (title.isEmpty()) {
                    Toast.makeText(this, R.string.stream_title_required, Toast.LENGTH_SHORT).show()
                } else {
                    startLiveSession(title, desc)
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun startLiveSession(title: String, description: String) {
        val userId = AuthUtils.getCurrentUserId(this) ?: return
        lifecycleScope.launch {
            try {
                val resp = RetrofitClient.functionsApi.createLiveSession(
                    CreateLiveStreamRequest(userId, title, description)
                )
                val errorBody = resp.errorBody()?.string().orEmpty()
                Log.e(TAG, "createLiveSession() HTTP ${resp.code()}: $errorBody")
                if (resp.isSuccessful) {
                    currentStream = resp.body()
                    // Populate top bar and show streamer info, follower count, and follow button
                    resp.body()?.hostId?.let { hostId ->
                        lifecycleScope.launch {
                            val profiles = RetrofitClient.profileApi.getProfileByUserId(
                                "*", "eq.$hostId"
                            )
                            if (profiles.isNotEmpty()) {
                                val p = profiles[0]
                                tvStreamerName.text = p.name ?: p.username
                                ivStreamerImage.load(p.image)
                            }
                            // follower count
                            val followers = RetrofitClient.followsApi.getFollowers(
                                select = "follower_id", followedIdFilter = "eq.$hostId"
                            )
                            tvFollowerCount.text = formatCount(followers.size)
                            // follow/unfollow button for viewers
                            btnFollowStreamer = findViewById(R.id.btnFollowStreamer)
                            val currentId = AuthUtils.getCurrentUserId(this@LiveStreamActivity)
                            if (currentId != null && currentId != hostId) {
                                val header = RetrofitClient.followsApi.isFollowingUser(
                                    followedIdFilter = "eq.$hostId", followerIdFilter = "eq.$currentId"
                                ).headers()["Content-Range"]
                                val following = header?.substringAfterLast("/")?.toIntOrNull() ?: 0 > 0
                                btnFollowStreamer.apply {
                                    visibility = View.VISIBLE
                                    text = if (following) getString(R.string.unfollow) else getString(R.string.follow)
                                    setOnClickListener {
                                        lifecycleScope.launch {
                                            if (following) RetrofitClient.followsApi.unfollowUser(
                                                    followedIdFilter = "eq.$hostId", followerIdFilter = "eq.$currentId"
                                                ) else RetrofitClient.followsApi.followUser(
                                                    mapOf("followed_id" to hostId, "follower_id" to currentId)
                                                )
                                            text = if (!following) getString(R.string.unfollow) else getString(R.string.follow)
                                        }
                                    }
                                }
                            }
                            liveTopBar.visibility = View.VISIBLE
                            liveTopBar.bringToFront()
                            liveTopBar.bringToFront()
                            // initial comments load
                            currentStream?.id?.let { sid ->
                                val initial = RetrofitClient.liveStreamApi.getLiveStreamComments(
                                    select = "*,profile:profiles(*)",
                                    streamFilter = "eq.$sid"
                                )
                                commentsAdapter.submitList(initial)
                                if (initial.isNotEmpty()) {
                                    rvLiveComments.scrollToPosition(initial.size - 1)
                                }
                            }
                        }
                    }
                    // Start local camera preview
                    startCamera()
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
                                // enable camera and microphone publishing
                                room.localParticipant.setCameraEnabled(true)
                                room.localParticipant.setMicrophoneEnabled(true)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "LiveKit v2 connect failed", e)
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

    private fun startCamera() {
        val previewView = PreviewView(this)
        val container = findViewById<FrameLayout>(R.id.flLiveStream)
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
            val selector = CameraSelector.DEFAULT_FRONT_CAMERA
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
        val email = ""
        lifecycleScope.launch {
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