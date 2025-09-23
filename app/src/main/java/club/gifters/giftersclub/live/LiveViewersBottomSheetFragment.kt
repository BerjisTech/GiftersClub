package club.gifters.giftersclub.live

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.Profile
import club.gifters.giftersclub.network.RetrofitClient
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.load

/**
 * Bottom sheet showing the list of current viewers for a live stream.
 */
class LiveViewersBottomSheetFragment : BottomSheetDialogFragment() {

    private var streamId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        streamId = arguments?.getString(ARG_STREAM_ID)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dlg ->
            val bottomSheet = (dlg as BottomSheetDialog)
                .findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED
                it.setBackgroundResource(android.R.color.transparent)
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_live_viewers_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rv = view.findViewById<RecyclerView>(R.id.rvViewers)
        val empty = view.findViewById<TextView>(R.id.tvEmpty)
        val close = view.findViewById<TextView>(R.id.btnCloseViewers)
        val adapter = ViewerAdapter()
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        close.setOnClickListener { dismiss() }

        val sid = streamId ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Fetch viewer rows for this stream (full row, so our model parses)
                val rows = RetrofitClient.liveStreamApi.getLiveStreamViewers("*", "eq.$sid")
                val ids = rows.mapNotNull { it.viewerId }.distinct()
                val profiles: List<Profile> = if (ids.isNotEmpty())
                    RetrofitClient.profileApi.getProfilesByUserIds("user_id,username,name,image", "in.(${ids.joinToString(",")})")
                else emptyList()
                withContext(Dispatchers.Main) {
                    empty.isVisible = profiles.isEmpty()
                    adapter.submit(profiles)
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    empty.isVisible = true
                }
            }
        }
    }

    private class ViewerAdapter : RecyclerView.Adapter<ViewerVH>() {
        private val items = mutableListOf<Profile>()
        fun submit(list: List<Profile>) { items.clear(); items.addAll(list.sortedBy { it.username ?: it.name ?: it.userId }); notifyDataSetChanged() }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewerVH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_viewer_profile, parent, false)
            return ViewerVH(v)
        }
        override fun getItemCount(): Int = items.size
        override fun onBindViewHolder(holder: ViewerVH, position: Int) { holder.bind(items[position]) }
    }

    private class ViewerVH(view: View) : RecyclerView.ViewHolder(view) {
        private val avatar: ImageView = view.findViewById(R.id.ivViewerAvatar)
        private val name: TextView = view.findViewById(R.id.tvViewerName)
        fun bind(p: Profile) {
            val uname = p.username ?: p.name ?: p.userId.take(6)
            name.text = "@$uname"
            val url = p.image
            if (!url.isNullOrEmpty()) avatar.load(url) else avatar.setImageResource(R.drawable.profile)
        }
    }

    companion object {
        const val TAG = "LiveViewersBottomSheet"
        private const val ARG_STREAM_ID = "stream_id"
        fun newInstance(streamId: String): LiveViewersBottomSheetFragment = LiveViewersBottomSheetFragment().apply {
            arguments = Bundle().apply { putString(ARG_STREAM_ID, streamId) }
        }
    }
}
