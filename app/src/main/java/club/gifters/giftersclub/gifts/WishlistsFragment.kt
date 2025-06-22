package club.gifters.giftersclub.gifts

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.network.RetrofitClient
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

/**
 * Fragment showing the current user's wishlists.
 */
class WishlistsFragment : Fragment(R.layout.fragment_wishlists) {
    private val wishlistApi = RetrofitClient.wishlistApi
    private val profileApi = RetrofitClient.profileApi
    private var page = 0
    private val pageSize = 30
    private var isLoading = false
    private var isLastPage = false
    private var searchQuery: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etSearch = view.findViewById<EditText>(R.id.etSearchWishlists)
        etSearch.doAfterTextChanged { editable ->
            searchQuery = editable?.toString().orEmpty().trim()
            page = 0
            isLastPage = false
            loadWishlists(view, clear = true)
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
        // infinite scroll
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layout = recyclerView.layoutManager as LinearLayoutManager
                val visible = layout.childCount
                val total = layout.itemCount
                val first = layout.findFirstVisibleItemPosition()
                if (!isLoading && !isLastPage
                    && visible + first >= total
                    && first >= 0
                    && total >= pageSize
                ) {
                    loadWishlists(view, clear = false)
                }
            }
        })

        val tvEmpty = view.findViewById<TextView>(R.id.tvEmptyWishlists)
        val fab = view.findViewById<FloatingActionButton>(R.id.fabCreateWishlist)
        fab.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainContentContainer, CreateWishlistFragment())
                .addToBackStack(null)
                .commit()
        }

        // initial load
        loadWishlists(view, clear = true)
    }

    private fun loadWishlists(view: View, clear: Boolean) {
        if (isLoading || isLastPage) return
        isLoading = true
        val rv = view.findViewById<RecyclerView>(R.id.rvWishlists)
        val adapter = rv.adapter as WishlistAdapter
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmptyWishlists)
        lifecycleScope.launch {
            var orFilter: String? = null
            try {
                if (searchQuery.isNotBlank()) {
                    val term = searchQuery.trim()
                    val wild = "*$term*"
                    val profiles = profileApi.searchProfiles(
                        orFilter = "(username.ilike.$wild,name.ilike.$wild)"
                    )
                    val userIds = profiles.map { it.userId }
                    val filters = mutableListOf(
                        "name.ilike.$wild",
                        "description.ilike.$wild",
                        "link.ilike.$wild"
                    )
                    if (userIds.isNotEmpty()) {
                        filters.add("user_id.in.(${userIds.joinToString(",")})")
                    }
                    orFilter = "(${filters.joinToString(",")})"
                }
                Log.i("WishlistsFragment", "Loading page=$page size=$pageSize orFilter=${orFilter ?: "<none>"}")
                val joined = wishlistApi.getWishlists(
                    select   = "*,profile:profiles(id,user_id,username,name)",
                    orFilter = orFilter,
                    order    = "created_at.desc",
                    limit    = pageSize,
                    offset   = page * pageSize
                )
                Log.i("WishlistsFragment", "Fetched ${joined.size} raw results")
                val wishlists = joined.map { it.toWishlist() }
                if (clear) adapter.submitList(wishlists)
                else adapter.submitList(adapter.currentList + wishlists)
                if (joined.size < pageSize) isLastPage = true else page++
                tvEmpty.visibility = if (adapter.currentList.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                Log.e("WishlistsFragment", "Error loading wishlists (orFilter=$orFilter)", e)
                tvEmpty.visibility = View.VISIBLE
            } finally {
                isLoading = false
            }
        }
    }
}