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
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.Gift
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.payments.PaymentWebViewActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.NumberFormat

/**
 * Activity displaying the live stream UI (stream view, comments, and gift drawer).
 * UI only; functionality to be implemented.
 */
class LiveStreamActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_stream)

        val btnOpenGifts = findViewById<ImageView>(R.id.btnOpenGifts)
        val flGiftsBottomSheet = findViewById<FrameLayout>(R.id.flGiftsBottomSheet)
        val giftsBottomSheetBehavior = BottomSheetBehavior.from(flGiftsBottomSheet)
        // configure to expand to half the screen (max):
        giftsBottomSheetBehavior.isFitToContents = false
        giftsBottomSheetBehavior.halfExpandedRatio = 0.5f
        btnOpenGifts.setOnClickListener {
            giftsBottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
        }

        // Setup gifts grid
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
}