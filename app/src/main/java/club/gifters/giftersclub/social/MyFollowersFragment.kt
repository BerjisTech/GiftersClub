package club.gifters.giftersclub.social

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R

/**
 * Fragment showing list of followers for the current user.
 */
class MyFollowersFragment : Fragment(R.layout.fragment_my_followers) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rvMyFollowers)
        rv.layoutManager = LinearLayoutManager(requireContext())
        // TODO: load and display followers
    }
}