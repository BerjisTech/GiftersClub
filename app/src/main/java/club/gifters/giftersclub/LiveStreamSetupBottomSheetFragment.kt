package club.gifters.giftersclub

import android.app.Dialog
import android.os.Bundle
import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import club.gifters.giftersclub.model.SystemCategory
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.R
import club.gifters.giftersclub.MainActivity
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
        // Prevent outside touch, back-press or swipe dismissal; only Cancel/Start handle dismissal
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        // Expand fully and clear default background for rounded corners
        dialog.setOnShowListener { dlg ->
            val bottomSheet = (dlg as BottomSheetDialog)
                .findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.isHideable = false
                behavior.isDraggable = false
                it.setBackgroundResource(android.R.color.transparent)
            }
        }
        val content = requireActivity().layoutInflater
            .inflate(R.layout.fragment_create_stream_bottom_sheet, null)
        dialog.setContentView(content)

        val etTitle = content.findViewById<EditText>(R.id.etStreamTitle)
        val etDesc = content.findViewById<EditText>(R.id.etStreamDescription)
        val actvCategory = content.findViewById<AutoCompleteTextView>(R.id.actvCategory)
        val etTags = content.findViewById<EditText>(R.id.etStreamTags)
        val rgAccess = content.findViewById<android.widget.RadioGroup>(R.id.rgLiveAccessType)
        val etPrice = content.findViewById<EditText>(R.id.etLivePrice)
        val layoutPlanPicker = content.findViewById<android.widget.LinearLayout>(R.id.layoutPlanPicker)
        val actvPlan = content.findViewById<AutoCompleteTextView>(R.id.actvPlan)
        val btnCancel = content.findViewById<Button>(R.id.btnCancelLive)
        val btnStart = content.findViewById<Button>(R.id.btnStartLive)

        // Load categories and setup search suggestions
        val ctx = requireContext()
        var categories: List<SystemCategory> = emptyList()
        // subscription plans for this creator (optional)
        var planIdByName: Map<String, String> = emptyMap()
        (requireActivity() as? LiveStreamActivity)?.lifecycleScope?.launchWhenStarted {
            try {
                categories = RetrofitClient.systemCategoryApi.getCategories()
                val names = categories.map { it.name }
                actvCategory.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, names))
            } catch (_: Exception) {}
            try {
                val uid = club.gifters.giftersclub.AuthUtils.getCurrentUserId(ctx) ?: ""
                if (uid.isNotEmpty()) {
                    val plans = RetrofitClient.subscriptionPlanApi.getSubscriptionPlans("eq.$uid")
                    val planNames = plans.map { it.name }
                    planIdByName = plans.associate { it.name to it.id }
                    actvPlan.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, planNames))
                }
            } catch (_: Exception) {}
        }

        // Toggle price or plan picker based on access selection
        rgAccess.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbLivePaid -> {
                    etPrice.visibility = View.VISIBLE
                    layoutPlanPicker.visibility = View.GONE
                }
                R.id.rbLiveSubscriberOnly -> {
                    etPrice.visibility = View.GONE
                    layoutPlanPicker.visibility = View.VISIBLE
                }
                else -> {
                    etPrice.visibility = View.GONE
                    layoutPlanPicker.visibility = View.GONE
                }
            }
        }

        btnCancel.setOnClickListener {
            // Canceling should finish this activity and go back
            requireActivity().finish()
            dismiss()
        }
        var hasStarted = false
        btnStart.setOnClickListener {
            val title = etTitle.text.toString().trim()
            if (title.isEmpty()) {
                etTitle.error = getString(R.string.stream_title_required)
            } else {
                hasStarted = true
                val desc = etDesc.text.toString().trim()
                val categoryName = actvCategory.text.toString().trim()
                val matched = categories.firstOrNull { it.name.equals(categoryName, ignoreCase = true) }
                if (matched == null) {
                    actvCategory.error = getString(R.string.stream_title_required).replace("title","category")
                    hasStarted = false
                    return@setOnClickListener
                }
                val tags = etTags.text.toString()
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val accessType = when (rgAccess.checkedRadioButtonId) {
                    R.id.rbLivePaid -> "paid"
                    R.id.rbLiveSubscriberOnly -> "subscription"
                    else -> "free"
                }
                val priceTokens = if (accessType == "paid") etPrice.text.toString().toIntOrNull() else null
                val selectedPlanName = actvPlan.text.toString()
                val requiredPlanId = if (accessType == "subscription") planIdByName[selectedPlanName] else null
                if (accessType == "subscription" && requiredPlanId.isNullOrEmpty()) {
                    actvPlan.error = "Required"
                    hasStarted = false
                    return@setOnClickListener
                }

                (requireActivity() as? LiveStreamActivity)?.startLiveSession(
                    title,
                    desc,
                    matched.id,
                    tags,
                    accessType,
                    priceTokens,
                    requiredPlanId
                )
                dismiss()
            }
        }
        
        return dialog
    }

    companion object {
        const val TAG = "LiveStreamSetupBottomSheet"
    }
}
