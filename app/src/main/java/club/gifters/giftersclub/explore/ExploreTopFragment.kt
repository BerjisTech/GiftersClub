package club.gifters.giftersclub.explore

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.explore.ExploreTopAdapter
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.LiveStream
import club.gifters.giftersclub.model.Post
import club.gifters.giftersclub.model.Profile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Fragment for displaying a mixed 'Top' feed of posts, users, and live streams.
 */
class ExploreTopFragment : Fragment(R.layout.fragment_explore_top) {
    companion object {
        private const val ARG_TOP = "arg_top"
        private const val ARG_USERS = "arg_users"
        private const val ARG_LIVE = "arg_live"
        private val gson = Gson()

        fun newInstance(top: List<Post>, users: List<Profile>, live: List<LiveStream>): ExploreTopFragment {
            return ExploreTopFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TOP, gson.toJson(top))
                    putString(ARG_USERS, gson.toJson(users))
                    putString(ARG_LIVE, gson.toJson(live))
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rvTop)
        val grid = GridLayoutManager(requireContext(), 2)
        grid.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int =
                if (rv.adapter?.getItemViewType(position) == ExploreTopAdapter.TYPE_POST) 1 else 2
        }
        rv.layoutManager = grid
        val adapter = ExploreTopAdapter()
        rv.adapter = adapter

        arguments?.let { args ->
            val postType = object : TypeToken<List<Post>>() {}.type
            val userType = object : TypeToken<List<Profile>>() {}.type
            val liveType = object : TypeToken<List<LiveStream>>() {}.type
            val posts = gson.fromJson<List<Post>>(args.getString(ARG_TOP), postType)
            val users = gson.fromJson<List<Profile>>(args.getString(ARG_USERS), userType)
            val streams = gson.fromJson<List<LiveStream>>(args.getString(ARG_LIVE), liveType)

            // Interleave: 4 posts, 1 user, 1 live, repeat
            val mixed = mutableListOf<Any>()
            var pi = 0; var ui = 0; var li = 0
            while (pi < posts.size || ui < users.size || li < streams.size) {
                repeat(4) { if (pi < posts.size) mixed += posts[pi++] }
                if (ui < users.size) mixed += users[ui++]
                if (li < streams.size) mixed += streams[li++]
            }
            adapter.submitList(mixed)
        }
    }
}