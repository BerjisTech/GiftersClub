package club.gifters.giftersclub.explore

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.LiveStream
import club.gifters.giftersclub.network.RetrofitClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

/**
 * Fragment for displaying live stream search results in Explore.
 */
class ExploreLiveFragment : Fragment(R.layout.fragment_explore_live) {
    companion object {
        private const val TAG = "ExploreLiveFragment"
        private const val ARG_QUERY = "query"
        private const val ARG_LIST = "arg_list"

        fun newInstance(query: String): ExploreLiveFragment = ExploreLiveFragment().apply {
            arguments = Bundle().apply { putString(ARG_QUERY, query) }
        }

        fun newInstanceFromList(list: List<LiveStream>): ExploreLiveFragment = ExploreLiveFragment().apply {
            arguments = Bundle().apply { putString(ARG_LIST, Gson().toJson(list)) }
        }
    }

    private val query: String by lazy { requireArguments().getString(ARG_QUERY).orEmpty() }
    private lateinit var adapter: ExploreLiveAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val swipe = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
        val rv = view.findViewById<RecyclerView>(R.id.rvStreams)
        adapter = ExploreLiveAdapter()
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        swipe.isRefreshing = true
        arguments?.getString(ARG_LIST)?.let { json ->
            val type = object : TypeToken<List<LiveStream>>() {}.type
            adapter.submitList(Gson().fromJson(json, type))
            swipe.isRefreshing = false
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 1) direct live_stream matches for live status
                val wild = "*${query}*"
                val directFilter = "(title.ilike.${wild},description.ilike.${wild})"
                // Log.d(TAG, "Live directFilter=$directFilter")
                val direct = RetrofitClient.liveStreamApi.searchLiveStreams(
                    select = "*",
                    orFilter = directFilter,
                    statusFilter = "eq.live",
                    order = "live_stream_viewer_count.desc,live_stream_comment_count_so_far.desc"
                )
                // 2) live_streams where host matches profiles and status live
                val userFilter = "(username.ilike.*${query}*,name.ilike.*${query}*)"
                // Log.d(TAG, "Live userFilter=$userFilter")
                val profiles = RetrofitClient.profileApi.searchProfiles("*", userFilter)
                val hostIds = profiles.map { it.userId }.distinct()
                val userStreams = if (hostIds.isNotEmpty()) {
                    RetrofitClient.liveStreamApi.getLiveStreamsByHosts(
                        select = "*",
                        hostFilter = "in.(${hostIds.joinToString(",")})",
                        statusFilter = "eq.live",
                        order = "live_stream_viewer_count.desc,live_stream_comment_count_so_far.desc"
                    )
                } else emptyList()
                // merge unique, direct first then user
                // Log.d(TAG, "Live results direct=${direct.size}, hostStreams=${userStreams.size}")
                val seen = mutableSetOf<String>()
                val merged = mutableListOf<LiveStream>()
                direct.filter { seen.add(it.id) }.let { merged += it }
                userStreams.filter { seen.add(it.id) }.let { merged += it }
                // Log.d(TAG, "Live merged count=${merged.size}")
                if (merged.isEmpty()) {
                    // Log.d(TAG, "Live merged empty, falling back to direct title/description search")
                    val fallbackFilter = "(title.ilike.*${query}*,description.ilike.*${query}*)"
                    val fallback = RetrofitClient.liveStreamApi.searchLiveStreams(
                        select = "*",
                        orFilter = fallbackFilter,
                        statusFilter = "eq.live"
                    )
                    adapter.submitList(fallback)
                } else {
                    adapter.submitList(merged)
                }
            } catch (e: Exception) {
                // Log.w(TAG, "Live search failed", e)
                adapter.submitList(emptyList())
            } finally {
                swipe.isRefreshing = false
            }
        }
    }
}