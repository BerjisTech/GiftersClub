package club.gifters.giftersclub.live

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import club.gifters.giftersclub.R
import club.gifters.giftersclub.network.RetrofitClient
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class RequestsBottomSheetFragment : BottomSheetDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        val v = requireActivity().layoutInflater.inflate(R.layout.overlay_requests_panel, null)
        dialog.setContentView(v)
        dialog.setOnShowListener { dlg ->
            val sheet = (dlg as BottomSheetDialog)
                .findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            sheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.isHideable = true
                behavior.isDraggable = true
            }
        }

        v.findViewById<View>(R.id.btnCloseRequests).setOnClickListener { dismiss() }
        loadRequests(v)
        return dialog
    }

    private fun loadRequests(root: View) {
        val streamId = arguments?.getString(ARG_STREAM_ID) ?: return
        val list = root.findViewById<LinearLayout>(R.id.listRequests)
        list.removeAllViews()
        lifecycleScope.launch {
            try {
                val resp = RetrofitClient.functionsApi.liveInviteRaw(mapOf("action" to "list", "streamId" to streamId))
                if (!resp.isSuccessful) return@launch
                val body = resp.body()?.string() ?: return@launch
                val arr = org.json.JSONArray(body)
                val pendingSet = setOf("requested", "pending", "request")
                val items = mutableListOf<Pair<String,String>>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val st = obj.optString("status").lowercase()
                    if (!pendingSet.contains(st)) continue
                    val uname = obj.optJSONObject("profiles")?.optString("username")
                        ?: obj.optString("invitee_id").take(6)
                    val id = obj.optString("id")
                    items.add(uname to id)
                }
                if (items.isEmpty()) {
                    Toast.makeText(requireContext(), "No requests", Toast.LENGTH_SHORT).show()
                    dismiss()
                    return@launch
                }
                items.forEach { (uname, id) ->
                    val row = requireActivity().layoutInflater.inflate(R.layout.item_request_row, list, false)
                    row.findViewById<TextView>(R.id.tvUsername).text = uname
                    row.findViewById<Button>(R.id.btnAcceptRequest).setOnClickListener {
                        lifecycleScope.launch {
                            try {
                                RetrofitClient.functionsApi.liveInvite(mapOf("action" to "accept", "inviteId" to id))
                                Toast.makeText(requireContext(), "Accepted", Toast.LENGTH_SHORT).show()
                                loadRequests(root)
                            } catch (_: Exception) {
                                Toast.makeText(requireContext(), "Failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    row.findViewById<Button>(R.id.btnRejectRequest).setOnClickListener {
                        lifecycleScope.launch {
                            try {
                                RetrofitClient.functionsApi.liveInvite(mapOf("action" to "reject", "inviteId" to id))
                                Toast.makeText(requireContext(), "Rejected", Toast.LENGTH_SHORT).show()
                                loadRequests(root)
                            } catch (_: Exception) {
                                Toast.makeText(requireContext(), "Failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    list.addView(row)
                }
            } catch (_: Exception) { }
        }
    }

    companion object {
        private const val ARG_STREAM_ID = "arg_stream_id"
        fun newInstance(streamId: String): RequestsBottomSheetFragment {
            val f = RequestsBottomSheetFragment()
            f.arguments = Bundle().apply { putString(ARG_STREAM_ID, streamId) }
            return f
        }
    }
}

