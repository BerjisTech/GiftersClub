package club.gifters.giftersclub.live

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import android.content.ClipData
import android.view.DragEvent
import androidx.lifecycle.lifecycleScope
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.BattleParticipant
import club.gifters.giftersclub.model.Profile
import club.gifters.giftersclub.network.RetrofitClient
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MatchSetupBottomSheetFragment : BottomSheetDialogFragment() {
    data class Invitee(var username: String = "", var userId: String = "", var team: Int = 2)
    private val invitees = mutableListOf(Invitee())
    private var searchJob: Job? = null
    private var requestsJob: Job? = null
    private var partsJob: Job? = null
    private var battleId: String? = null
    private var isActiveBattle = false
    private var hostTeam = 1

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dlg ->
            val sheet = (dlg as BottomSheetDialog).findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.isHideable = true
                behavior.isDraggable = true
                it.setBackgroundResource(android.R.color.transparent)
            }
        }
        val v = requireActivity().layoutInflater.inflate(R.layout.fragment_match_setup_bottom_sheet, null)
        dialog.setContentView(v)
        val streamId = arguments?.getString(ARG_STREAM_ID) ?: ""
        val hostId = arguments?.getString(ARG_HOST_ID) ?: ""
        val btnStart = v.findViewById<Button>(R.id.btnStartMatch)
        val btnEnd = v.findViewById<Button>(R.id.btnEndMatch)
        val tvManage = v.findViewById<TextView>(R.id.tvManageTitle)
        val tvRequests = v.findViewById<TextView>(R.id.tvRequestsTitle)
        val requestsContainer = v.findViewById<LinearLayout>(R.id.requestsContainer)
        val manageZones = v.findViewById<LinearLayout>(R.id.manageZones)
        val zoneIndividual = v.findViewById<LinearLayout>(R.id.containerIndividual)
        val zoneTeamA = v.findViewById<LinearLayout>(R.id.containerTeamA)
        val zoneTeamB = v.findViewById<LinearLayout>(R.id.containerTeamB)
        val switchTeam = v.findViewById<Switch>(R.id.switchHostTeam)
        switchTeam.setOnCheckedChangeListener { _, isChecked -> hostTeam = if (isChecked) 2 else 1 }

        // Setup invitees UI (search + list)
        val container = v.findViewById<LinearLayout>(R.id.inviteesContainer)
        fun renderInvitees() {
            container.removeAllViews()
            invitees.forEachIndexed { idx, inv ->
                val row = requireActivity().layoutInflater.inflate(R.layout.item_invitee_row, container, false)
                val et = row.findViewById<EditText>(R.id.etInviteeUsername)
                et.setText(inv.username)
                et.addTextChangedListener(object: TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        inv.username = s?.toString()?.trim() ?: ""
                    }
                    override fun afterTextChanged(s: Editable?) { searchProfiles(et, inv) }
                })
                val sp = row.findViewById<Spinner>(R.id.spInviteeTeam)
                sp.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, arrayOf("Team A","Team B"))
                sp.setSelection(if (inv.team == 1) 0 else 1)
                sp.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) { inv.team = if (position==0) 1 else 2 }
                    override fun onNothingSelected(parent: AdapterView<*>) {}
                }
                val btnRemove = row.findViewById<ImageButton>(R.id.btnRemoveInvitee)
                btnRemove.setOnClickListener { invitees.removeAt(idx); renderInvitees() }
                container.addView(row)
            }
        }
        renderInvitees()
        v.findViewById<ImageButton>(R.id.btnAddInvitee).setOnClickListener {
            if (invitees.size < 3) { invitees.add(Invitee()); renderInvitees() }
        }

        partsJob?.cancel(); partsJob = lifecycleScope.launch {
            try {
                val list = RetrofitClient.liveStreamApi.getActiveBattleForStream("*", "eq.$streamId")
                isActiveBattle = list.isNotEmpty()
                battleId = list.firstOrNull()?.id
                btnStart.visibility = if (isActiveBattle) View.GONE else View.VISIBLE
                btnEnd.visibility = if (isActiveBattle) View.VISIBLE else View.GONE
                tvManage.visibility = if (isActiveBattle) View.VISIBLE else View.GONE
                manageZones.visibility = if (isActiveBattle) View.VISIBLE else View.GONE
                if (isActiveBattle) {
                    setZoneDragListeners(zoneIndividual, 0)
                    setZoneDragListeners(zoneTeamA, 1)
                    setZoneDragListeners(zoneTeamB, 2)
                    renderParticipants(zoneIndividual, zoneTeamA, zoneTeamB)
                }
                // Load pending requests
                requestsJob?.cancel(); requestsJob = lifecycleScope.launch {
                    while (isActive) {
                        renderRequests(requestsContainer, tvRequests, streamId)
                        delay(3000)
                    }
                }
            } catch (_: Exception) {}
        }

        btnStart.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val now = java.time.Instant.now().toString()
                    val resp = RetrofitClient.battleApi.createBattle("*", mapOf("live_stream_id" to streamId, "status" to "active", "started_at" to now))
                    val bid = resp.body()?.firstOrNull()?.id ?: return@launch
                    // host participant
                    RetrofitClient.battleApi.addParticipant("*", mapOf("battle_id" to bid, "user_id" to hostId, "live_stream_id" to streamId, "team" to hostTeam))
                    invitees.forEach { inv ->
                        if (inv.username.isBlank()) return@forEach
                        try {
                            val profs = RetrofitClient.profileApi.getProfileByUsername("*", "eq.${inv.username}")
                            val userId = profs.firstOrNull()?.userId ?: return@forEach
                            RetrofitClient.functionsApi.liveInvite(mapOf("action" to "invite", "streamId" to streamId, "username" to inv.username))
                            val prov = RetrofitClient.functionsApi.liveInviteRaw(mapOf("action" to "provision", "streamId" to streamId, "inviteeId" to userId))
                            val guestId = if (prov.isSuccessful) org.json.JSONObject(prov.body()?.string() ?: "{}").optString("id") else ""
                            if (guestId.isNotEmpty()) {
                                RetrofitClient.battleApi.addParticipant("*", mapOf("battle_id" to bid, "user_id" to userId, "live_stream_id" to guestId, "team" to inv.team))
                            }
                        } catch (_: Exception) {}
                    }
                    dismiss()
                } catch (_: Exception) {}
            }
        }
        btnEnd.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val bid = battleId ?: return@launch
                    RetrofitClient.battleApi.updateBattle("*", "eq.$bid", mapOf("status" to "ended", "ends_at" to java.time.Instant.now().toString()))
                    dismiss()
                } catch (_: Exception) {}
            }
        }

        return dialog
    }

    private fun renderRequests(container: LinearLayout, title: TextView, streamId: String) {
        lifecycleScope.launch {
            try {
                val resp = RetrofitClient.functionsApi.liveInviteRaw(mapOf("action" to "list", "streamId" to streamId))
                if (!resp.isSuccessful) { title.visibility = View.GONE; container.visibility = View.GONE; return@launch }
                val arr = org.json.JSONArray(resp.body()?.string() ?: "[]")
                if (arr.length() == 0) { title.visibility = View.GONE; container.visibility = View.GONE; return@launch }
                title.visibility = View.VISIBLE
                container.visibility = View.VISIBLE
                container.removeAllViews()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val uname = obj.optJSONObject("profiles")?.optString("username") ?: obj.optString("invitee_id").take(6)
                    val inviteId = obj.optString("id")
                    val row = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.HORIZONTAL
                        val tv = TextView(context).apply { text = uname; textSize = 14f }
                        val btn = Button(context).apply { text = "Accept" }
                        btn.setOnClickListener {
                            lifecycleScope.launch {
                                try { RetrofitClient.functionsApi.liveInvite(mapOf("action" to "accept", "inviteId" to inviteId)) } catch (_: Exception) {}
                                renderRequests(container, title, streamId)
                            }
                        }
                        addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                        addView(btn)
                    }
                    container.addView(row)
                }
            } catch (_: Exception) { title.visibility = View.GONE; container.visibility = View.GONE }
        }
    }

    private fun renderParticipants(zoneIndividual: LinearLayout, zoneTeamA: LinearLayout, zoneTeamB: LinearLayout) {
        zoneIndividual.removeAllViews(); zoneTeamA.removeAllViews(); zoneTeamB.removeAllViews()
        partsJob?.cancel(); partsJob = lifecycleScope.launch {
            try {
                val bid = battleId ?: return@launch
                val parts = RetrofitClient.battleApi.getParticipants("*", "eq.$bid")
                parts.forEach { p ->
                    val row = buildParticipantRow(p)
                    when (p.team ?: 0) {
                        1 -> zoneTeamA.addView(row)
                        2 -> zoneTeamB.addView(row)
                        else -> zoneIndividual.addView(row)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun buildParticipantRow(p: BattleParticipant): View {
        val row = requireActivity().layoutInflater.inflate(R.layout.item_invitee_row, null, false)
        row.tag = p
        val et = row.findViewById<EditText>(R.id.etInviteeUsername)
        et.isEnabled = false
        lifecycleScope.launch {
            try {
                val user = RetrofitClient.profileApi.getProfileByUserId("*", "eq.${p.userId}").firstOrNull()
                et.setText(user?.username ?: p.userId.take(6))
            } catch (_: Exception) { et.setText(p.userId.take(6)) }
        }
        // Hide spinner and remove button (drag manages team; remove via long-click menu could be added)
        row.findViewById<Spinner>(R.id.spInviteeTeam).visibility = View.GONE
        val btnRemove = row.findViewById<ImageButton>(R.id.btnRemoveInvitee)
        btnRemove.setOnClickListener {
            lifecycleScope.launch {
                try { RetrofitClient.battleApi.deleteParticipant("eq.${p.id}") } catch (_: Exception) {}
                val parent = row.parent as? LinearLayout
                parent?.removeView(row)
            }
        }
        row.setOnLongClickListener {
            val data = ClipData.newPlainText("pid", p.id)
            it.startDragAndDrop(data, View.DragShadowBuilder(it), it, 0)
            true
        }
        return row
    }

    private fun setZoneDragListeners(zone: LinearLayout, teamCode: Int) {
        zone.setOnDragListener { v, e ->
            when (e.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> { v.alpha = 0.9f; true }
                DragEvent.ACTION_DRAG_EXITED -> { v.alpha = 1f; true }
                DragEvent.ACTION_DROP -> {
                    v.alpha = 1f
                    val row = e.localState as? View ?: return@setOnDragListener false
                    val p = row.tag as? BattleParticipant ?: return@setOnDragListener false
                    (row.parent as? LinearLayout)?.removeView(row)
                    (v as LinearLayout).addView(row)
                    val newTeam = when (teamCode) { 1 -> 1; 2 -> 2; else -> null }
                    lifecycleScope.launch {
                        try { RetrofitClient.battleApi.updateParticipant("*", "eq.${p.id}", mapOf("team" to newTeam)) } catch (_: Exception) {}
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> { v.alpha = 1f; true }
                else -> true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try { requestsJob?.cancel() } catch (_: Exception) {}
        try { partsJob?.cancel() } catch (_: Exception) {}
        try { searchJob?.cancel() } catch (_: Exception) {}
    }

    private fun searchProfiles(et: EditText, inv: Invitee) {
        searchJob?.cancel()
        val q = et.text?.toString()?.trim() ?: return
        if (q.length < 2) return
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(200)
            try {
                val res = RetrofitClient.profileApi.searchProfiles("*", "username.ilike.%$q%,name.ilike.%$q%")
                if (res.isNotEmpty()) {
                    // pick first match for now
                    val p: Profile = res[0]
                    inv.username = p.username ?: q
                    inv.userId = p.userId
                }
            } catch (_: Exception) {}
        }
    }

    companion object {
        private const val ARG_STREAM_ID = "arg_stream_id"
        private const val ARG_HOST_ID = "arg_host_id"
        fun newInstance(streamId: String, hostId: String): MatchSetupBottomSheetFragment {
            val f = MatchSetupBottomSheetFragment()
            f.arguments = Bundle().apply { putString(ARG_STREAM_ID, streamId); putString(ARG_HOST_ID, hostId) }
            return f
        }
    }
}
