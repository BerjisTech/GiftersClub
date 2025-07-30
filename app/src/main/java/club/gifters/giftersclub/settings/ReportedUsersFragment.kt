package club.gifters.giftersclub.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.R
import club.gifters.giftersclub.network.RetrofitClient
import kotlinx.coroutines.launch

/**
 * Fragment to display reported users with infinite scroll.
 */
class ReportedUsersFragment : Fragment(R.layout.fragment_reported_users) {

    private val profileApi = RetrofitClient.profileApi
    private var userId: String = ""
    private lateinit var adapter: ReportedUsersAdapter
    private var isLoading = false
    private var hasMore = true
    private var offset = 0
    private val pageSize = 50

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userId = AuthUtils.getCurrentUserId(requireContext()) ?: return

        val recycler = view.findViewById<RecyclerView>(R.id.rvReportedUsers)
        adapter = ReportedUsersAdapter()
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                if (!rv.canScrollVertically(1)) loadNextPage()
            }
        })
        loadNextPage()
    }

    private fun loadNextPage() {
        if (isLoading || !hasMore) return
        isLoading = true
        lifecycleScope.launch {
            try {
                val items = profileApi.getReportedUserItems(
                    reporterFilter = "eq.$userId",
                    limit = pageSize,
                    offset = offset
                )
                if (items.size < pageSize) hasMore = false
                offset += items.size
                val viewItems = items.map {
                    ReportedUserItemView(
                        username = it.reportedUser.username,
                        reason = it.reason,
                        status = it.status
                    )
                }
                adapter.addItems(viewItems)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to load reported users", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Refresh the list from parent.
     */
    fun refreshList() {
        offset = 0
        hasMore = true
        adapter.clear()
        loadNextPage()
    }
}