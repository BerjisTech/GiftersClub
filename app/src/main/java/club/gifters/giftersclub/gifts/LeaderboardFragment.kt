package club.gifters.giftersclub.gifts

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.gifts.GifterFragment
import kotlinx.coroutines.launch

/**
 * Fragment for displaying the Top Gifter leaderboard.
 */
class LeaderboardFragment : Fragment(R.layout.fragment_leaderboard) {

    private val api = RetrofitClient.leaderboardApi
    private lateinit var adapter: TopGifterAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerLeaderboard)
        recycler.layoutManager = LinearLayoutManager(context)
        adapter = TopGifterAdapter { gifter ->
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainContentContainer, GifterFragment.newInstance(gifter.username))
                .addToBackStack(null)
                .commit()
        }
        recycler.adapter = adapter

        loadLeaderboard()
    }

    private fun loadLeaderboard() {
        lifecycleScope.launch {
            val list = api.getTopGifters()
            adapter.submitList(list)
        }
    }
}