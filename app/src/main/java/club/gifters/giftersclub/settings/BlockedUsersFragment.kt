package club.gifters.giftersclub.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.BlockedUserItem
import club.gifters.giftersclub.network.RetrofitClient
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

/**
 * Fragment to display blocked users with infinite scroll, multi-select, and unblock actions.
 */
class BlockedUsersFragment : Fragment(R.layout.fragment_blocked_users) {

    private val profileApi = RetrofitClient.profileApi
    private var userId: String = ""
    private lateinit var adapter: BlockedUsersAdapter
    private var isLoading = false
    private var hasMore = true
    private var offset = 0
    private val pageSize = 50

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userId = AuthUtils.getCurrentUserId(requireContext()) ?: return

        val btnUnblockSelected = view.findViewById<MaterialButton>(R.id.btnUnblockSelected)
        val btnUnblockAll = view.findViewById<MaterialButton>(R.id.btnUnblockAll)
        val recycler = view.findViewById<RecyclerView>(R.id.rvBlockedUsers)

        adapter = BlockedUsersAdapter(
            onItemSelectionChanged = { count -> btnUnblockSelected.isEnabled = count > 0 },
            onUnblockClick = { item -> confirmUnblock(listOf(item.userId)) }
        )
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                if (!rv.canScrollVertically(1)) loadNextPage()
            }
        })

        btnUnblockSelected.setOnClickListener {
            val ids = adapter.getSelectedUserIds()
            if (ids.isNotEmpty()) confirmUnblock(ids)
        }
        btnUnblockAll.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Unblock all")
                .setMessage("Are you sure you want to unblock all users?")
                .setPositiveButton("Yes") { _, _ -> unblockAll() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        loadNextPage()
    }

    private fun loadNextPage() {
        if (isLoading || !hasMore) return
        isLoading = true
        lifecycleScope.launch {
            try {
                val items = profileApi.getBlockedUserItems(
                    blockerFilter = "eq.$userId",
                    limit = pageSize,
                    offset = offset
                )
                if (items.size < pageSize) hasMore = false
                offset += items.size
                val viewItems = items.map {
                    BlockedUserItemView(
                        userId = it.blockedUser.user_id,
                        username = it.blockedUser.username
                    )
                }
                adapter.addItems(viewItems)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to load blocked users", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    private fun confirmUnblock(ids: List<String>) {
        AlertDialog.Builder(requireContext())
            .setTitle("Unblock")
            .setMessage("Are you sure you want to unblock the selected user(s)?")
            .setPositiveButton("Yes") { _, _ -> unblockUsers(ids) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun unblockUsers(ids: List<String>) {
        lifecycleScope.launch {
            try {
                ids.forEach { profileApi.unblockUser("eq.$userId", it) }
                Toast.makeText(requireContext(), "User(s) unblocked", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to unblock users", Toast.LENGTH_SHORT).show()
            } finally {
                adapter.clearSelection()
                resetAndReload()
            }
        }
    }

    private fun unblockAll() {
        lifecycleScope.launch {
            try {
                profileApi.unblockAll("eq.$userId")
                Toast.makeText(requireContext(), "All users unblocked", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to unblock all users", Toast.LENGTH_SHORT).show()
            } finally {
                adapter.clear()
                resetPaging()
            }
        }
    }

    private fun resetAndReload() {
        resetPaging()
        adapter.clear()
        loadNextPage()
    }

    private fun resetPaging() {
        offset = 0
        hasMore = true
    }

    /**
     * Refresh the list from parent.
     */
    fun refreshList() {
        resetAndReload()
    }
}