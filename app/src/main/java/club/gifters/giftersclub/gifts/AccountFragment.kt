package club.gifters.giftersclub.gifts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import club.gifters.giftersclub.AuthActivity
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.Profile
import club.gifters.giftersclub.model.WishlistItem
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.payments.PaymentWebViewActivity
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId

/**
 * Fragment displaying the user's account info and stats.
 */
class AccountFragment : Fragment(R.layout.fragment_account) {
    private val profileApi = RetrofitClient.profileApi
    private lateinit var tvTokenBalance: TextView
    private lateinit var tvTokensReceived: TextView
    private lateinit var tvTokensSent: TextView
    private lateinit var tvGiftsReceived: TextView
    private lateinit var tvGiftsSent: TextView
    private lateinit var tvWishlistsOpen: TextView
    private lateinit var tvWishlistsFulfilled: TextView
    private lateinit var btnWithdrawals: Button
    private lateinit var btnBuyTokens: Button
    private var profile: Profile? = null
    private lateinit var tvActivitySummary: TextView

    private var userId: String = ""
    private var hasRetry = false
    private val REQUEST_PICK_IMAGE = 2001

    companion object {
        private const val TAG = "AccountFragment"
    }

    /**
     * Load today's activity summary: new gifts or wishlist contributions, or none.
     */
    private fun loadActivitySummary(tvActivitySummary: TextView) {
        lifecycleScope.launch {
            try {
                // Gifts received today
                val gifts = RetrofitClient.recentGiftsApi.listRecentGifts(
                    select = "*",
                    receiverFilter = "eq.$userId"
                )
                val today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate().toString()
                val giftsToday = gifts.count { it.createdAt?.startsWith(today) == true }
                if (giftsToday > 0) {
                    tvActivitySummary.text = getString(R.string.you_have_n_gifts_today, giftsToday)
                    return@launch
                }
                // Wishlist contributions to user's wishlists today
                val contribs = RetrofitClient.wishlistApi.getWishlistContributionsByOwner(
                    select = "*",
                    ownerFilter = "eq.$userId",
                    createdAtFilter = "gte.${today}T00:00:00Z"
                )
                val contribCount = contribs.size
                if (contribCount > 0) {
                    tvActivitySummary.text =
                        getString(R.string.you_have_n_contribs_today, contribCount)
                } else {
                    tvActivitySummary.text = getString(R.string.no_activity_today)
                }
            } catch (e: Exception) {
                // Log.e(TAG, "Error loading activity summary", e)
                tvActivitySummary.text = getString(R.string.no_activity_today)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().title = getString(R.string.account)
        val swipeRefresh = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
        val tvActivitySummary = view.findViewById<TextView>(R.id.tvActivitySummary)
        swipeRefresh.setOnRefreshListener {
            loadProfile()
            loadActivitySummary(tvActivitySummary)
            swipeRefresh.isRefreshing = false
        }
        // initial activity summary
        loadActivitySummary(tvActivitySummary)
        tvTokenBalance = view.findViewById(R.id.tvTokenBalance)
        tvTokensReceived = view.findViewById(R.id.tvTokensReceived)
        tvTokensSent = view.findViewById(R.id.tvTokensSent)
        tvGiftsReceived = view.findViewById(R.id.tvGiftsReceived)
        tvGiftsSent = view.findViewById(R.id.tvGiftsSent)
        tvWishlistsOpen = view.findViewById(R.id.tvWishlistsOpen)
        tvWishlistsFulfilled = view.findViewById(R.id.tvWishlistsFulfilled)
        btnWithdrawals = view.findViewById(R.id.btnWithdrawals)
        btnBuyTokens = view.findViewById(R.id.btnBuyTokens)

        // Extract user_id from stored access token
        requireContext().getSharedPreferences("supabase", Context.MODE_PRIVATE)
            .getString("access_token", "")
            ?.takeIf { it.isNotBlank() }
            ?.let {
                val parts = it.split('.')
                if (parts.size > 1) {
                    val decoded =
                        String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE))
                    userId = org.json.JSONObject(decoded).optString("sub")
                }
            }

        loadProfile()

        // Profile editing moved to Settings; remove edit buttons.
        btnWithdrawals.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainContentContainer, WithdrawalsFragment())
                .addToBackStack(null)
                .commit()
        }
        btnBuyTokens.setOnClickListener { showBuyTokensDialog() }

        // Logout
        val btnLogout = view.findViewById<Button>(R.id.btnLogout)
        btnLogout.setOnClickListener {
            // clear stored Supabase tokens and return to AuthActivity
            requireContext().getSharedPreferences("supabase", Context.MODE_PRIVATE)
                .edit { remove("access_token").remove("refresh_token") }
            startActivity(Intent(requireContext(), AuthActivity::class.java))
            requireActivity().finish()
        }
    }

    private fun loadProfile() {
        lifecycleScope.launch {
            try {
                val list = profileApi.getProfileByUserId(
                    select = "*",
                    userIdFilter = "eq.$userId"
                )
                if (list.isNotEmpty()) bindProfile(list[0])
            } catch (e: HttpException) {
                if (e.code() == 401 && !hasRetry) {
                    hasRetry = true
                    requireContext().getSharedPreferences("supabase", Context.MODE_PRIVATE)
                        .edit { remove("access_token").remove("refresh_token") }
                    loadProfile()
                } else {
                    // Log.e(TAG, "Failed to load profile", e)
                }
            } catch (e: Exception) {
                // Log.e(TAG, "Failed to load profile", e)
            }
        }
    }

    private fun bindProfile(profile: Profile) {
        this.profile = profile

        // Bind token and gift stats from profile
        tvTokenBalance.text = NumberFormat.getInstance().format(profile.tokenBalance ?: 0)
        tvTokensReceived.text = NumberFormat.getInstance().format(profile.tokensReceived ?: 0)
        tvTokensSent.text = NumberFormat.getInstance().format(profile.tokensSent ?: 0)
        tvGiftsReceived.text = NumberFormat.getInstance().format(profile.giftsReceived ?: 0)
        tvGiftsSent.text = NumberFormat.getInstance().format(profile.giftsSent ?: 0)
        // Load wishlist counts for this user
        lifecycleScope.launch {
            try {
                val items: List<WishlistItem> = profileApi.listWishlistsByUser(
                    select = "id,is_fulfilled",
                    userIdFilter = "eq.$userId"
                )
                val total = items.size
                val fulfilled = items.count { it.isFulfilled }
                tvWishlistsOpen.text = NumberFormat.getInstance().format(total - fulfilled)
                tvWishlistsFulfilled.text = NumberFormat.getInstance().format(fulfilled)
            } catch (_: Exception) {
            }
        }
    }

    private fun showBuyTokensDialog() {
        val input = EditText(requireContext()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.enter_token_amount)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.buy_tokens)
            .setView(input)
            .setPositiveButton(R.string.buy) { _, _ ->
                val amount = input.text.toString().toIntOrNull()
                if (amount == null || amount <= 0) {
                    Toast.makeText(requireContext(), R.string.invalid_amount, Toast.LENGTH_SHORT)
                        .show()
                } else {
                    initiateTopup(amount)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun initiateTopup(amount: Int) {
        val userId = this.userId
        val email = profile?.email.orEmpty()
        val txRef = "topup_${userId}_${System.currentTimeMillis()}"
        lifecycleScope.launch {
            var lastTxId: String? = null
            try {
                val resp = RetrofitClient.tokenApi.recordTokenTransaction(
                    mapOf(
                        "user_id" to userId,
                        "transaction_type" to "purchase",
                        "tokens" to amount,
                        "kes_amount" to amount,
                        "flutterwave_transaction_id" to txRef,
                        "flutterwave_transaction_status" to "initiated"
                    )
                )
                if (resp.isSuccessful) {
                    lastTxId = resp.body()?.firstOrNull()?.id
                } else {
                    val errorBody = resp.errorBody()?.string().orEmpty()
                    // Log.e(TAG, "Failed to record token transaction: HTTP ${resp.code()} body=$errorBody")
                    Toast.makeText(
                        requireContext(),
                        "Failed to record transaction (${resp.code()}): $errorBody",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                // Log.e(TAG, "Error recording initial token transaction", e)
            }
            if (!lastTxId.isNullOrBlank()) {
                PaymentWebViewActivity.start(
                    requireContext(),
                    userId,
                    email,
                    amount,
                    txRef,
                    lastTxId
                )
            } else {
                Toast.makeText(
                    requireContext(),
                    "Failed to initiate token purchase",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}