package club.gifters.giftersclub.gifts

import android.content.Context
import android.os.Bundle
import android.util.Log.*
import android.util.Base64
import org.json.JSONObject
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.widget.FrameLayout
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.ContributorSummary
import club.gifters.giftersclub.model.Notification
import club.gifters.giftersclub.model.Profile
import club.gifters.giftersclub.model.Wishlist
import club.gifters.giftersclub.model.WishlistContribution
import club.gifters.giftersclub.network.ProfileApi
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.network.WishlistApi
import club.gifters.giftersclub.payments.PaymentWebViewActivity
import coil.load
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val ARG_WISHLIST_ID = "wishlist_id"

/**
 * Fragment displaying details for a single wishlist, including contributions.
 */
class WishlistDetailFragment : Fragment(R.layout.fragment_wishlist_detail) {
    private val wishlistApi: WishlistApi = RetrofitClient.wishlistApi
    private val profileApi: ProfileApi = RetrofitClient.profileApi

    private var wishlistId: String = ""

    companion object {
        fun newInstance(wishlistId: String): WishlistDetailFragment {
            val args = Bundle().apply { putString(ARG_WISHLIST_ID, wishlistId) }
            return WishlistDetailFragment().apply { arguments = args }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        wishlistId = arguments?.getString(ARG_WISHLIST_ID) ?: ""
        // Remove fragment-level back button; use toolbar back arrow only
        val ivOwner = view.findViewById<ImageView>(R.id.ivOwnerAvatar)
        val tvOwner = view.findViewById<TextView>(R.id.tvOwnerName)
        val ivBanner = view.findViewById<ImageView>(R.id.ivWishlistBanner)
        val tvTitle = view.findViewById<TextView>(R.id.tvWishlistTitle)
        val tvDesc = view.findViewById<TextView>(R.id.tvWishlistDescription)
        val tvCreated = view.findViewById<TextView>(R.id.tvCreatedOn)
        val tvCount = view.findViewById<TextView>(R.id.tvContributorsCount)
        val tvProgressFrac = view.findViewById<TextView>(R.id.tvProgressFraction)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val rvContrib = view.findViewById<RecyclerView>(R.id.rvContributors)

        // toolbar back arrow handles navigation

        rvContrib.layoutManager = LinearLayoutManager(requireContext())
        val contribAdapter = ContributorAdapter()
        rvContrib.adapter = contribAdapter

        lifecycleScope.launch {
            try {
                // load wishlist details
                val list = wishlistApi.getWishlistById(
                    select = "*",
                    idFilter = "eq.$wishlistId"
                )
                if (list.isEmpty()) throw IllegalStateException("Wishlist not found")
                val wish = list[0]

                // load owner profile
                val owners = profileApi.getProfileByUserId(
                    select = "*",
                    userIdFilter = "eq.${wish.userId}"
                )
                val owner = owners.firstOrNull()

                // bind header
                tvTitle.text = wish.name
                // Update toolbar title to wishlist name (or generic)
                requireActivity().title = wish.name.ifBlank { getString(R.string.wishlist) }
                tvDesc.text = wish.description
                // format creation date
                val formattedDate = wish.createdAt?.let { raw ->
                    try {
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                            .format(Instant.parse(raw).atZone(ZoneId.systemDefault()))
                    } catch (_: Exception) {
                        raw
                    }
                } ?: ""
                tvCreated.text = getString(R.string.created_on, formattedDate)
                owner?.let {
                    // banner image
                    wish.image.takeIf(String::isNotBlank)?.let { img ->
                        ivBanner.visibility = View.VISIBLE
                        ivBanner.load(img) { placeholder(android.R.color.darker_gray) }
                    }
                    // owner avatar and name link
                    ivOwner.visibility = View.VISIBLE
                    ivOwner.load(it.image) { placeholder(android.R.color.darker_gray) }
                    tvOwner.text = it.name ?: it.username
                    tvOwner.setOnClickListener { _ ->
                        parentFragmentManager.beginTransaction()
                            .replace(R.id.mainContentContainer,
                                GifterFragment.newInstance(it.username)
                            )
                            .addToBackStack(null)
                            .commit()
                    }
                }

                // contributions
                val contribs = wishlistApi.getWishlistContributions(
                    select = "*",
                    wishlistIdFilter = "eq.$wishlistId"
                )
                val total = contribs.sumOf(WishlistContribution::tokens)
                val max = wish.tokens
                val percent = if (max > 0) (total * 100 / max) else 0
                progressBar.progress = percent
                // format numbers with commas
                val nf = NumberFormat.getNumberInstance()
                tvProgressFrac.text = "${nf.format(total)} / ${nf.format(max)}"

                // contributors list
                val ids = contribs.map { it.contributorId }.distinct()
                val profiles = if (ids.isNotEmpty()) {
                    profileApi.getProfilesByUserIds(
                        select = "*",
                        userIdsFilter = "in.(${ids.joinToString(",")})"
                    )
                } else emptyList<Profile>()
                val summary = profiles.map { profile ->
                    val userContribs = contribs.filter { it.contributorId == profile.userId }
                    val sumTokens = userContribs.sumOf(WishlistContribution::tokens)
                    val last = userContribs.maxByOrNull(WishlistContribution::createdAt)?.createdAt ?: ""
                    ContributorSummary(profile, sumTokens, last)
                }
                contribAdapter.submitList(summary)
                tvCount.text = getString(R.string.contributors_title) + " (${summary.size})"
                // Disable contribute on own wishlist; otherwise show contribution drawer
                val currentUser = getCurrentUserId()
                val contributeBtn = view.findViewById<Button>(R.id.btnContribute)
                if (currentUser != null && currentUser == wish.userId) {
                    contributeBtn.visibility = View.GONE
                } else {
                    contributeBtn.visibility = View.VISIBLE
                    contributeBtn.setOnClickListener { showContributeBottomSheet(wish, total) }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to load wishlist details", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getCurrentUserId(): String? {
        val prefs = requireContext().getSharedPreferences("supabase", Context.MODE_PRIVATE)
        val token = prefs.getString("access_token", null) ?: return null
        val parts = token.split('.')
        if (parts.size < 2) return null
        val decoded = String(Base64.decode(parts[1], Base64.URL_SAFE))
        return JSONObject(decoded).optString("sub")
    }

    private fun showContributeBottomSheet(wishlist: Wishlist, contributed: Int) {
        val remaining = wishlist.tokens - contributed
        val sheet = BottomSheetDialog(requireContext())
        sheet.setOnShowListener { dialog ->
            (dialog as BottomSheetDialog)
                .findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundResource(R.drawable.bg_rounded_top)
        }
        val content = layoutInflater.inflate(R.layout.fragment_contribute_bottom_sheet, null)
        sheet.setContentView(content)
        (sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet))?.let { sheetView ->
            BottomSheetBehavior.from(sheetView).apply {
                isFitToContents = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        val input = content.findViewById<EditText>(R.id.etContributeAmount).apply {
            hint = "Enter amount (max $remaining)"
        }
        content.findViewById<Button>(R.id.btnCancelContribute).setOnClickListener {
            sheet.dismiss()
        }
        content.findViewById<Button>(R.id.btnConfirmContribute).setOnClickListener {
            val amount = input.text.toString().toIntOrNull() ?: 0
            when {
                amount <= 0 -> Toast.makeText(requireContext(), 
                    "Enter a valid contribution amount.", Toast.LENGTH_SHORT).show()
                amount > remaining -> Toast.makeText(requireContext(), 
                    "Cannot contribute more than $remaining tokens.", Toast.LENGTH_SHORT).show()
                else -> {
                    sheet.dismiss()
                    continueContributionFlow(wishlist, amount)
                }
            }
        }
        sheet.show()
    }

    private fun continueContributionFlow(wishlist: Wishlist, amount: Int) {
        val contributorId = getCurrentUserId() ?: run {
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }
        val overlay = requireView().findViewById<View>(R.id.flLoadingOverlay)
        overlay.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val profiles = RetrofitClient.profileApi.getProfileByUserId("*", "eq.$contributorId")
                val profile = profiles.firstOrNull()
                val balance = profile?.tokenBalance ?: 0
                if (balance < amount) {
                    val needed = amount - balance
                    showTopUpPrompt(contributorId, profile?.email.orEmpty(), needed)
                    return@launch
                }
                // Contribute via Edge Function
                RetrofitClient.functionsApi.processWishlistContributionRpc(
                    mapOf(
                        "wishlistId" to wishlist.id,
                        "contributorId" to contributorId,
                        "tokens" to amount
                    )
                )
                // Notifications
                val ownerId = wishlist.userId
                val username = profile?.username.orEmpty()
                if (username.isNotBlank()) {
                    RetrofitClient.notificationApi.createNotification(
                        Notification(
                            id = "",
                            userId = ownerId,
                            type = "wishlist_contribution",
                            referenceId = wishlist.id,
                            message = "$username contributed $amount tokens to your ${wishlist.name}",
                            isRead = false,
                            createdAt = "",
                            updatedAt = null,
                            senderId = contributorId
                        )
                    )
                    RetrofitClient.notificationApi.createNotification(
                        Notification(
                            id = "",
                            userId = contributorId,
                            type = "wishlist_contribution",
                            referenceId = wishlist.id,
                            message = "You contributed $amount tokens to ${wishlist.name}",
                            isRead = false,
                            createdAt = "",
                            updatedAt = null,
                            senderId = contributorId
                        )
                    )
                }
                Toast.makeText(requireContext(), "Contribution successful", Toast.LENGTH_SHORT).show()
                // Refresh UI
                onViewCreated(requireView(), null)
            } catch (e: HttpException) {
                e("WishlistDetail", "Error contributing to wishlist", e)
                Toast.makeText(requireContext(), "Failed to contribute. Please try again later.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e("WishlistDetail", "Error contributing to wishlist", e)
                Toast.makeText(requireContext(), "Failed to contribute. Please try again later.", Toast.LENGTH_SHORT).show()
            } finally {
                overlay.visibility = View.GONE
            }
        }
    }

    private fun showTopUpPrompt(userId: String, email: String, needed: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("Insufficient tokens")
            .setMessage("You have insufficient tokens. You need $needed more to contribute. Top up now?")
            .setPositiveButton("Buy Tokens") { _, _ ->
                val txRef = "topup_${userId}_${System.currentTimeMillis()}"
                PaymentWebViewActivity.start(requireContext(), userId, email, needed, txRef, "")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}