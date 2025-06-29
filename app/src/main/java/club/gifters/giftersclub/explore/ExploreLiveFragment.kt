package club.gifters.giftersclub.explore

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import club.gifters.giftersclub.R
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.model.LiveStream
import kotlinx.coroutines.launch

/**
 * Fragment for displaying live stream search results in Explore.
 */
class ExploreLiveFragment : Fragment(R.layout.fragment_explore_live) {
    companion object {
        private const val ARG_QUERY = "query"

        fun newInstance(query: String): ExploreLiveFragment {
            return ExploreLiveFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_QUERY, query)
                }
            }
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
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val wild = "*${'$'}{query}*"
                val orFilter = "(title.ilike.${'$'}wild,description.ilike.${'$'}wild)"
                val streams: List<LiveStream> = RetrofitClient.liveStreamApi.searchLiveStreams("*", orFilter)
                adapter.submitList(streams)
            } catch (e: Exception) {
                adapter.submitList(emptyList())
            } finally {
                swipe.isRefreshing = false
            }
        }
    }
}