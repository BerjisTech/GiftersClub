package club.gifters.giftersclub

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import club.gifters.giftersclub.R
import club.gifters.giftersclub.live.LiveStreamActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Bottom sheet for entering stream title and optional description before going live.
 */
class LiveStreamSetupBottomSheetFragment : BottomSheetDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        // Expand fully and clear default background for rounded corners
        dialog.setOnShowListener { dlg ->
            val bottomSheet = (dlg as BottomSheetDialog)
                .findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED
                it.setBackgroundResource(android.R.color.transparent)
            }
        }
        val content = requireActivity().layoutInflater
            .inflate(R.layout.fragment_create_stream_bottom_sheet, null)
        dialog.setContentView(content)

        val etTitle = content.findViewById<EditText>(R.id.etStreamTitle)
        val etDesc = content.findViewById<EditText>(R.id.etStreamDescription)
        val btnCancel = content.findViewById<Button>(R.id.btnCancelLive)
        val btnStart = content.findViewById<Button>(R.id.btnStartLive)

        btnCancel.setOnClickListener {
            requireActivity().finish()
            dismiss()
        }
        btnStart.setOnClickListener {
            val title = etTitle.text.toString().trim()
            if (title.isEmpty()) {
                etTitle.error = getString(R.string.stream_title_required)
            } else {
                val desc = etDesc.text.toString().trim()
                (requireActivity() as? LiveStreamActivity)?.
                startLiveSession(title, desc)
                dismiss()
            }
        }
        return dialog
    }

    companion object {
        const val TAG = "LiveStreamSetupBottomSheet"
    }
}