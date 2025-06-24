package club.gifters.giftersclub.gifts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import coil.load
import club.gifters.giftersclub.R
import club.gifters.giftersclub.SupabaseConfig
import club.gifters.giftersclub.model.Profile
import club.gifters.giftersclub.model.WishlistItem
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.payments.PaymentWebViewActivity
import club.gifters.giftersclub.gifts.WithdrawalsFragment
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.text.NumberFormat

/**
 * Fragment displaying the user's account info and stats.
 */
class AccountFragment : Fragment(R.layout.fragment_account) {
    private val profileApi = RetrofitClient.profileApi
    private lateinit var ivProfileImage: ImageView
    private lateinit var btnEditImage: TextView
    private lateinit var tvUsername: TextView
    private lateinit var btnEditUsername: TextView
    private lateinit var tvFullName: TextView
    private lateinit var btnEditFullName: TextView
    private lateinit var tvTokenBalance: TextView
    private lateinit var tvTokensReceived: TextView
    private lateinit var tvTokensSent: TextView
    private lateinit var tvGiftsReceived: TextView
    private lateinit var tvGiftsSent: TextView
    private lateinit var tvWishlistsOpen: TextView
    private lateinit var tvWishlistsFulfilled: TextView
    private lateinit var btnWithdrawals: Button
    private lateinit var btnShareProfile: Button
    private lateinit var btnBuyTokens: Button
    private var profile: Profile? = null

    private var userId: String = ""
    private var hasRetry = false
    private val REQUEST_PICK_IMAGE = 2001

    companion object {
        private const val TAG = "AccountFragment"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().title = getString(R.string.account)
        ivProfileImage = view.findViewById(R.id.ivProfileImage)
        btnEditImage = view.findViewById(R.id.btnEditImage)
        tvUsername = view.findViewById(R.id.tvUsername)
        btnEditUsername = view.findViewById(R.id.btnEditUsername)
        tvFullName = view.findViewById(R.id.tvFullName)
        btnEditFullName = view.findViewById(R.id.btnEditFullName)
        tvTokenBalance = view.findViewById(R.id.tvTokenBalance)
        tvTokensReceived = view.findViewById(R.id.tvTokensReceived)
        tvTokensSent = view.findViewById(R.id.tvTokensSent)
        tvGiftsReceived = view.findViewById(R.id.tvGiftsReceived)
        tvGiftsSent = view.findViewById(R.id.tvGiftsSent)
        tvWishlistsOpen = view.findViewById(R.id.tvWishlistsOpen)
        tvWishlistsFulfilled = view.findViewById(R.id.tvWishlistsFulfilled)
        btnWithdrawals = view.findViewById(R.id.btnWithdrawals)
        btnShareProfile = view.findViewById(R.id.btnShareProfile)
        btnBuyTokens = view.findViewById(R.id.btnBuyTokens)

        // Extract user_id from stored access token
        requireContext().getSharedPreferences("supabase", Context.MODE_PRIVATE)
            .getString("access_token", "")
            ?.takeIf { it.isNotBlank() }
            ?.let {
                val parts = it.split('.')
                if (parts.size > 1) {
                    val decoded = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE))
                    userId = org.json.JSONObject(decoded).optString("sub")
                }
            }

        loadProfile()

        btnEditImage.setOnClickListener { pickImage() }
        btnEditUsername.setOnClickListener { promptEdit("username") }
        btnEditFullName.setOnClickListener { promptEdit("name") }
        btnWithdrawals.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainContentContainer, WithdrawalsFragment())
                .addToBackStack(null)
                .commit()
        }
        btnShareProfile.setOnClickListener { shareProfile() }
        btnBuyTokens.setOnClickListener { showBuyTokensDialog() }
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
                        .edit().remove("access_token").remove("refresh_token").apply()
                    loadProfile()
                } else Log.e(TAG, "Failed to load profile", e)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load profile", e)
            }
        }
    }

    private fun bindProfile(profile: Profile) {
        this.profile = profile
        // Load image
        if (profile.image.isNotBlank()) {
            ivProfileImage.load(profile.image) {
                placeholder(android.R.color.darker_gray)
                error(android.R.color.darker_gray)
            }
        } else {
            ivProfileImage.setImageResource(android.R.color.darker_gray)
        }
        tvUsername.text = profile.username
        tvFullName.text = profile.name.orEmpty()
        // Bind token and gift stats from profile
        tvTokenBalance.text   = NumberFormat.getInstance().format(profile.tokenBalance ?: 0)
        tvTokensReceived.text = NumberFormat.getInstance().format(profile.tokensReceived ?: 0)
        tvTokensSent.text     = NumberFormat.getInstance().format(profile.tokensSent ?: 0)
        tvGiftsReceived.text  = NumberFormat.getInstance().format(profile.giftsReceived ?: 0)
        tvGiftsSent.text      = NumberFormat.getInstance().format(profile.giftsSent ?: 0)
        // Load wishlist counts for this user
        lifecycleScope.launch {
            try {
                val items: List<WishlistItem> = profileApi.listWishlistsByUser(
                    select = "id,is_fulfilled",
                    userIdFilter = "eq.$userId"
                )
                val total = items.size
                val fulfilled = items.count { it.isFulfilled }
                tvWishlistsOpen.text      = NumberFormat.getInstance().format(total - fulfilled)
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
                    Toast.makeText(requireContext(), R.string.invalid_amount, Toast.LENGTH_SHORT).show()
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
                    Log.e(TAG, "Failed to record token transaction: HTTP ${resp.code()} body=$errorBody")
                    Toast.makeText(
                        requireContext(),
                        "Failed to record transaction (${resp.code()}): $errorBody",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error recording initial token transaction", e)
            }
            if (!lastTxId.isNullOrBlank()) {
                PaymentWebViewActivity.start(requireContext(), userId, email, amount, txRef, lastTxId)
            } else {
                Toast.makeText(requireContext(), "Failed to initiate token purchase", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, REQUEST_PICK_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                ivProfileImage.setImageURI(uri)
                uploadProfileImage(uri)
            }
        }
    }

    private fun uploadProfileImage(uri: Uri) {
        lifecycleScope.launch {
            try {
                // Upload image to storage
                val type = requireContext().contentResolver.getType(uri) ?: "application/octet-stream"
                val ext = type.substringAfterLast('/', "bin")
                val filename = "profile-${userId}.${ext}"
                requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                    val bytes = stream.readBytes()
                    val body = bytes.toRequestBody(type.toMediaTypeOrNull())
                    RetrofitClient.storageApi.uploadPostMedia(filename, body, type)
                }
                val publicUrl = "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/${SupabaseConfig.AVATARS_BUCKET}/$filename"
                // Update profile.image
                updateProfileField(mapOf("image" to publicUrl))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload profile image", e)
                Toast.makeText(requireContext(), "Failed to upload image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun promptEdit(field: String) {
        val current = if (field == "username") tvUsername.text.toString() else tvFullName.text.toString()
        val input = EditText(requireContext()).apply { setText(current) }
        AlertDialog.Builder(requireContext())
            .setTitle("Edit ${field.replaceFirstChar { it.uppercase() }}")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                updateProfileField(mapOf(field to input.text.toString().trim()))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateProfileField(updates: Map<String, Any>) {
        lifecycleScope.launch {
            try {
                val resp = profileApi.updateProfile(
                    userIdFilter = "eq.$userId",
                    updates = updates
                )
                if (resp.isSuccessful) bindProfile(resp.body()!![0])
                else Log.e(TAG, "Failed to update profile: HTTP ${resp.code()}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update profile", e)
                Toast.makeText(requireContext(), "Failed to update profile", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareProfile() {
        val shareUrl = "${SupabaseConfig.SUPABASE_URL.replace(".supabase.co", ".supabase.co/profile/")}${tvUsername.text}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareUrl)
        }
        startActivity(Intent.createChooser(intent, "Share Profile"))
    }
}