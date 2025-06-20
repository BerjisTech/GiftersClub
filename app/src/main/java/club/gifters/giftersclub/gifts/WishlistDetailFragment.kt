package club.gifters.giftersclub.gifts

import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import club.gifters.giftersclub.R
import club.gifters.giftersclub.gifts.GifterFragment
import club.gifters.giftersclub.model.ContributorSummary
import club.gifters.giftersclub.model.Profile
import club.gifters.giftersclub.model.Wishlist
import club.gifters.giftersclub.model.WishlistContribution
import club.gifters.giftersclub.network.ProfileApi
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.network.WishlistApi
import kotlinx.coroutines.launch
import org.json.JSONObject
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
        val btnBack = view.findViewById<ImageButton>(R.id.btnBack)
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

        btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

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
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to load wishlist details", Toast.LENGTH_SHORT).show()
            }
        }
    }
}