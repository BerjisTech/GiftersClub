package club.gifters.giftersclub.explore

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.FrameLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.LiveStream
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.LiveKitConfig
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalAudioTrackOptions
import io.livekit.android.room.track.LocalVideoTrackOptions
import io.livekit.android.room.track.RemoteVideoTrack
import kotlinx.coroutines.*

/**
 * Adapter for showing live stream search results in Explore.
 */
class ExploreLiveAdapter : ListAdapter<LiveStream, ExploreLiveAdapter.VH>(Diff) {
    private val scope = MainScope()
    companion object {
        private val Diff = object : DiffUtil.ItemCallback<LiveStream>() {
            override fun areItemsTheSame(old: LiveStream, new: LiveStream) = old.id == new.id
            override fun areContentsTheSame(old: LiveStream, new: LiveStream) = old == new
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_explore_live, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        private val tvViewerCount: TextView = view.findViewById(R.id.tvViewerCount)
        private val preview: FrameLayout = view.findViewById(R.id.previewLive)
        private var renderer: SurfaceViewRenderer? = null
        private var room: Room? = null
        private var job: Job? = null
        init {
            view.setOnClickListener {
                (getItem(bindingAdapterPosition))?.let { ls ->
                    val ctx = itemView.context
                    val uri = Uri.parse("https://gifters.club/live/${ls.id}")
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    intent.setClassName(ctx, "club.gifters.giftersclub.live.LiveStreamActivity")
                    ctx.startActivity(intent)
                }
            }
        }
        fun bind(item: LiveStream) {
            tvTitle.text = item.title
            tvViewerCount.text = "${item.viewerCount} watching"
            startPreview(item)
        }

        private fun startPreview(item: LiveStream) {
            stopPreview()
            val r = SurfaceViewRenderer(itemView.context)
            r.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            renderer = r
            preview.removeAllViews(); preview.addView(r)
            job = scope.launch(Dispatchers.Main) {
                try {
                    val resp = withContext(Dispatchers.IO) { RetrofitClient.functionsApi.getLiveSession(item.id) }
                    if (!resp.isSuccessful) return@launch
                    val token = resp.body()?.let { it.token ?: it.id } ?: return@launch
                    val opts = RoomOptions(
                        false, false, null,
                        LocalAudioTrackOptions(),
                        LocalVideoTrackOptions(),
                        null, null
                    )
                    val rm = LiveKit.create(itemView.context, opts, LiveKitOverrides())
                    rm.initVideoRenderer(r)
                    room = rm
                    withContext(Dispatchers.IO) { rm.connect(LiveKitConfig.WS_URL, token, io.livekit.android.ConnectOptions()) }
                    rm.remoteParticipants.values.forEach { p ->
                        p.videoTrackPublications.forEach { pair -> (pair.second as? RemoteVideoTrack)?.addRenderer(r) }
                    }
                    scope.launch {
                        rm.events.collect { e ->
                            if (e is RoomEvent.TrackSubscribed && e.track is RemoteVideoTrack) {
                                (e.track as RemoteVideoTrack).addRenderer(r)
                            }
                        }
                    }
                } catch (_: Exception) { }
            }
        }

        private fun stopPreview() {
            job?.cancel(); job = null
            try { room?.disconnect() } catch (_: Exception) {}
            room = null
            renderer?.release(); renderer = null
            preview.removeAllViews()
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is VH) {
            holder.apply {
                try { room?.disconnect() } catch (_: Exception) {}
                room = null
                renderer?.release(); renderer = null
                preview.removeAllViews()
            }
        }
    }
}
