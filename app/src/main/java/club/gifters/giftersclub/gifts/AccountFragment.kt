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
import club.gifters.giftersclub.R
import club.gifters.giftersclub.SupabaseConfig
import club.gifters.giftersclub.model.Profile
import club.gifters.giftersclub.model.WishlistItem
import club.gifters.giftersclub.network.RetrofitClient
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

/**
 * Fragment displaying the user's account info and stats.
 */
class AccountFragment : Fragment(R.layout.fragment_account) {
    private val profileApi = RetrofitClient.profileApi
    private lateinit var ivProfileImage: ImageView
    private lateinit var btnEditImage: ImageButton
    private lateinit var tvUsername: TextView
    private lateinit var btnEditUsername: ImageButton
    private lateinit var tvFullName: TextView
    private lateinit var btnEditFullName: ImageButton
    private lateinit var tvTokenBalance: TextView
    private lateinit var tvTokensReceived: TextView
    private lateinit var tvTokensSent: TextView
    private lateinit var tvGiftsReceived: TextView
    private lateinit var tvGiftsSent: TextView
    private lateinit var tvWishlistsOpen: TextView
    private lateinit var tvWishlistsFulfilled: TextView
    private lateinit var btnWithdrawals: Button
    private lateinit var btnShareProfile: Button

    private var userId: String = ""
    private var hasRetry = false
    private val REQUEST_PICK_IMAGE = 2001

    companion object {
        private const val TAG = "AccountFragment"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ivProfileImage    = view.findViewById(R.id.ivProfileImage)
        btnEditImage      = view.findViewById(R.id.btnEditImage)
        tvUsername        = view.findViewById(R.id.tvUsername)
        btnEditUsername   = view.findViewById(R.id.btnEditUsername)
        tvFullName        = view.findViewById(R.id.tvFullName)
        btnEditFullName   = view.findViewById(R.id.btnEditFullName)
        tvTokenBalance    = view.findViewById(R.id.tvTokenBalance)
        tvTokensReceived  = view.findViewById(R.id.tvTokensReceived)
        tvTokensSent      = view.findViewById(R.id.tvTokensSent)
        tvGiftsReceived   = view.findViewById(R.id.tvGiftsReceived)
        tvGiftsSent       = view.findViewById(R.id.tvGiftsSent)
        tvWishlistsOpen   = view.findViewById(R.id.tvWishlistsOpen)
        tvWishlistsFulfilled = view.findViewById(R.id.tvWishlistsFulfilled)
        btnWithdrawals    = view.findViewById(R.id.btnWithdrawals)
        btnShareProfile   = view.findViewById(R.id.btnShareProfile)

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
            // TODO: navigate to withdrawals page
            Toast.makeText(requireContext(), "Withdrawals page", Toast.LENGTH_SHORT).show()
        }
        btnShareProfile.setOnClickListener { shareProfile() }
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
        // Load image (may require an image loader library)
        if (profile.image.isNotBlank()) {
            ivProfileImage.setImageURI(Uri.parse(profile.image))
        }
        tvUsername.text = profile.username
        tvFullName.text = profile.name.orEmpty()
        // Bind token and gift stats from profile
        tvTokenBalance.text   = "Balance: ${profile.tokenBalance ?: 0}"
        tvTokensReceived.text = "Received: ${profile.tokensReceived ?: 0}"
        tvTokensSent.text     = "Sent: ${profile.tokensSent ?: 0}"
        tvGiftsReceived.text  = "Received: ${profile.giftsReceived ?: 0}"
        tvGiftsSent.text      = "Sent: ${profile.giftsSent ?: 0}"
        // Load wishlist counts for this user
        lifecycleScope.launch {
            try {
                val items = profileApi.listWishlistsByUser(
                    select = "id,is_fulfilled",
                    userIdFilter = "eq.$userId"
                )
                val total = items.size
                val fulfilled = items.count { it.isFulfilled }
                tvWishlistsOpen.text      = "Open: ${total - fulfilled}"
                tvWishlistsFulfilled.text = "Fulfilled: $fulfilled"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load wishlist count", e)
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