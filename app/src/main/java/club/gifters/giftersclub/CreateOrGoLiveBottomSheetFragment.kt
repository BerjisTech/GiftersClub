package club.gifters.giftersclub

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.cardview.widget.CardView
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import android.widget.FrameLayout
import club.gifters.giftersclub.gifts.CreatePostFragment
import club.gifters.giftersclub.live.LiveStreamActivity

class CreateOrGoLiveBottomSheetFragment : BottomSheetDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        // Ensure the sheet starts expanded to show all options
        dialog.setOnShowListener { dlg ->
            val bottomSheet = (dlg as BottomSheetDialog)
                .findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED
                // remove default sheet background to let rounded corners show cleanly
                it.setBackgroundResource(android.R.color.transparent)
            }
        }
        val contentView = requireActivity().layoutInflater.inflate(R.layout.activity_create_or_go_live, null)
        dialog.setContentView(contentView)

        val goLive = contentView.findViewById<CardView>(R.id.goLive)
        val createPost = contentView.findViewById<CardView>(R.id.createPost)
        val container = contentView.findViewById<LinearLayout>(R.id.main)

        goLive.setOnClickListener {
            startActivity(Intent(requireContext(), LiveStreamActivity::class.java))
            dismiss()
        }
        createPost.setOnClickListener {
            childFragmentManager.beginTransaction()
                .replace(R.id.main, CreatePostFragment())
                .addToBackStack(null)
                .commit()
        }

        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        return dialog
    }

    companion object {
        const val TAG = "CreateOrGoLiveBottomSheet"
    }
}