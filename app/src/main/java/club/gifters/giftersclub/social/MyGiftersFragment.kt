package club.gifters.giftersclub.social

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.model.RecentGiftEntry
import coil.load
import kotlinx.coroutines.launch
import club.gifters.giftersclub.social.RecentGifterAdapter
import club.gifters.giftersclub.gifts.GifterFragment

/**
 * Fragment showing list of users the current user has gifted.
 */
class MyGiftersFragment : Fragment(R.layout.fragment_my_gifters) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rvMyGifters)
        rv.layoutManager = LinearLayoutManager(requireContext())
        val adapter = RecentGifterAdapter { entry ->
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainContentContainer,
                    GifterFragment.newInstance(entry.gifterUsername ?: ""))
                .addToBackStack(null)
                .commit()
        }
        rv.adapter = adapter

        // load and display recent gifters via recent_gifts view
        lifecycleScope.launch {
            val currentUserId = AuthUtils.getCurrentUserId(requireContext()) ?: return@launch
            try {
                val entries = RetrofitClient.recentGiftsApi.listRecentGifts(
                    receiverFilter = "eq.$currentUserId"
                )
                adapter.submitList(entries.distinctBy { it.gifterId })
            } catch (_: Exception) {
                adapter.submitList(emptyList())
            }
        }
    }
}

/**
 * Adapter for rendering recent gifters (senders of gifts) in the MyGifters tab.
 */
private class RecentGifterAdapter(
    private val onClick: (RecentGiftEntry) -> Unit
) : androidx.recyclerview.widget.ListAdapter<RecentGiftEntry, RecentGifterAdapter.VH>(Diff) {

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_user, parent, false)
        return VH(view, onClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(
        view: android.view.View,
        private val onClick: (RecentGiftEntry) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        private val ivAvatar: android.widget.ImageView = view.findViewById(R.id.ivAvatar)
        private val tvName: android.widget.TextView = view.findViewById(R.id.tvName)
        private val tvUsername: android.widget.TextView = view.findViewById(R.id.tvUsername)
        private var current: RecentGiftEntry? = null

        init {
            view.setOnClickListener { current?.let(onClick) }
        }

        fun bind(entry: RecentGiftEntry) {
            current = entry
            tvName.text = entry.gifterUsername.orEmpty()
            tvUsername.text = ""
            if (!entry.gifterImage.isNullOrBlank()) {
                ivAvatar.load(entry.gifterImage) {
                    placeholder(android.R.color.darker_gray)
                    error(android.R.color.darker_gray)
                }
            } else {
                ivAvatar.setImageResource(android.R.color.darker_gray)
            }
        }
    }

    private object Diff : androidx.recyclerview.widget.DiffUtil.ItemCallback<RecentGiftEntry>() {
        override fun areItemsTheSame(old: RecentGiftEntry, new: RecentGiftEntry) =
            old.gifterId == new.gifterId

        override fun areContentsTheSame(old: RecentGiftEntry, new: RecentGiftEntry) = old == new
    }
}