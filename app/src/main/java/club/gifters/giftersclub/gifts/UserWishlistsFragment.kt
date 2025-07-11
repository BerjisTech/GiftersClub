package club.gifters.giftersclub.gifts

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.Wishlist
import club.gifters.giftersclub.network.RetrofitClient
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.gifts.CreateWishlistFragment

private const val ARG_USER_ID = "user_id"

/**
 * Fragment displaying wishlists for any given user (read-only).
 */
class UserWishlistsFragment : Fragment(R.layout.fragment_wishlists) {
    private val wishlistApi = RetrofitClient.wishlistApi
    private var userId: String = ""

    companion object {
        fun newInstance(userId: String): UserWishlistsFragment {
            val args = Bundle().apply { putString(ARG_USER_ID, userId) }
            return UserWishlistsFragment().apply { arguments = args }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userId = arguments?.getString(ARG_USER_ID) ?: ""

        val rv = view.findViewById<RecyclerView>(R.id.rvWishlists)
        rv.layoutManager = LinearLayoutManager(requireContext())
        val adapter = WishlistAdapter { wishlist ->
        // Use Activity's FragmentManager so the mainContentContainer exists
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.mainContentContainer,
                WishlistDetailFragment.newInstance(wishlist.id)
            )
            .addToBackStack(null)
            .commit()
        }
        rv.adapter = adapter

        // Show create button only for list owner
        val fab = view.findViewById<FloatingActionButton>(R.id.fabCreateWishlist)
        val currentUser = AuthUtils.getCurrentUserId(requireContext())
        if (currentUser != null && currentUser == userId) {
            fab.visibility = View.VISIBLE
            fab.setOnClickListener {
                // Use Activity's FragmentManager to swap into main content
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.mainContentContainer, CreateWishlistFragment.newInstance())
                    .addToBackStack(null)
                    .commit()
            }
        } else {
            fab.visibility = View.GONE
        }
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmptyWishlists)

        lifecycleScope.launch {
            try {
                val joined = wishlistApi.getWishlists(
                    select = "*,profile:profiles(id,user_id,username,name),wishlist_contributions(tokens)",
                    userIdFilter = "eq.$userId",
                    order = "created_at.desc",
                    limit = Int.MAX_VALUE,
                    offset = 0
                )
                val items: List<Wishlist> = joined.map { it.toWishlist() }
                adapter.submitList(items)
                tvEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            } catch (_: Exception) {
                tvEmpty.visibility = View.VISIBLE
            }
        }
    }
}