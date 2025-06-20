package club.gifters.giftersclub.gifts

import android.content.Context
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.TextView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.gifts.CreateWishlistFragment
import club.gifters.giftersclub.gifts.WishlistDetailFragment
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Fragment showing the current user's wishlists.
 */
class WishlistsFragment : Fragment(R.layout.fragment_wishlists) {
    private val wishlistApi = RetrofitClient.wishlistApi
    private var userId: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Decode current user ID from stored JWT
        requireContext().getSharedPreferences("supabase", Context.MODE_PRIVATE)
            .getString("access_token", "")
            ?.takeIf { it.isNotBlank() }
            ?.let { token ->
                token.split('.').getOrNull(1)?.let { payload ->
                    val json = String(Base64.decode(payload, Base64.URL_SAFE))
                    userId = JSONObject(json).optString("sub")
                }
            }

        val rv = view.findViewById<RecyclerView>(R.id.rvWishlists)
        rv.layoutManager = LinearLayoutManager(requireContext())
        val adapter = WishlistAdapter { wishlist ->
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainContentContainer,
                    WishlistDetailFragment.newInstance(wishlist.id)
                )
                .addToBackStack(null)
                .commit()
        }
        rv.adapter = adapter

        val tvEmpty = view.findViewById<TextView>(R.id.tvEmptyWishlists)
        val fab = view.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabCreateWishlist)
        fab.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainContentContainer, CreateWishlistFragment())
                .addToBackStack(null)
                .commit()
        }

        lifecycleScope.launch {
            try {
                val items = wishlistApi.getWishlists(
                    select = "*",
                    userIdFilter = "eq.$userId"
                )
                adapter.submitList(items)
                val empty = items.isEmpty()
                tvEmpty.setVisibility(if (empty) View.VISIBLE else View.GONE)
            } catch (_: Exception) {
                tvEmpty.setVisibility(View.VISIBLE)
            }
        }
    }
}