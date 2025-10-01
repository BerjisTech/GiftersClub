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
import club.gifters.giftersclub.AuthUtils
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
        val layoutCategoryPills = content.findViewById<com.google.android.flexbox.FlexboxLayout>(R.id.layoutCategoryPills)
        val rgAccess = content.findViewById<android.widget.RadioGroup>(R.id.rgLiveAccessType)
        val cbMatch = content.findViewById<android.widget.CheckBox>(R.id.cbThisIsMatch)
        val etPrice = content.findViewById<EditText>(R.id.etLivePrice)
        val layoutPlanPicker = content.findViewById<android.widget.LinearLayout>(R.id.layoutPlanPicker)
        val actvPlan = content.findViewById<AutoCompleteTextView>(R.id.actvPlan)
        val layoutNoPlans = content.findViewById<android.widget.LinearLayout>(R.id.layoutNoPlans)
        val btnOpenSettings = content.findViewById<Button>(R.id.btnOpenSubscriptionSettingsFromLive)
        val btnCancel = content.findViewById<Button>(R.id.btnCancelLive)
        val btnSchedule = content.findViewById<Button>(R.id.btnScheduleLive)
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
                // Build up to 4 quick-select category pills
                layoutCategoryPills.removeAllViews()
                val top = (if (categories.size >= 4) categories.take(4) else categories.take(4))
                val density = resources.displayMetrics.density
                top.forEach { cat ->
                    val tv = android.widget.TextView(ctx).apply {
                        text = cat.name
                        setPadding((12*density).toInt(), (6*density).toInt(), (12*density).toInt(), (6*density).toInt())
                        setTextColor(android.graphics.Color.WHITE)
                        background = android.graphics.drawable.GradientDrawable().apply {
                            cornerRadius = 16f * density
                            setColor(0x66444444)
                        }
                        setOnClickListener { actvCategory.setText(cat.name, false) }
                    }
                    val lp = com.google.android.flexbox.FlexboxLayout.LayoutParams(
                        com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT,
                        com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT
                    ).apply { rightMargin = (8*density).toInt(); bottomMargin = (8*density).toInt() }
                    layoutCategoryPills.addView(tv, lp)
                }
            } catch (_: Exception) {}
            try {
                val uid = AuthUtils.getCurrentUserId(ctx) ?: ""
                if (uid.isNotEmpty()) {
                    val plans = RetrofitClient.subscriptionPlanApi.getSubscriptionPlans("eq.$uid")
                    if (plans.isNotEmpty()) {
                        val planNames = listOf("All") + plans.map { it.name }
                        planIdByName = plans.associate { it.name to it.id }
                        actvPlan.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, planNames))
                        actvPlan.threshold = 0
                        actvPlan.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) actvPlan.showDropDown() }
                        actvPlan.setOnClickListener { actvPlan.showDropDown() }
                        actvPlan.setText("All", false)
                    } else {
                        // Keep hidden by default; only show if Subscriber Only is selected
                        btnOpenSettings.setOnClickListener {
                            val intent = Intent(requireContext(), MainActivity::class.java)
                            intent.putExtra(MainActivity.EXTRA_OPEN_SETTINGS_TAB, 4)
                            startActivity(intent)
                        }
                    }
                }
                // Ensure initial visibility matches current selection (default is Free)
                when (rgAccess.checkedRadioButtonId) {
                    R.id.rbLivePaid -> {
                        etPrice.visibility = View.VISIBLE
                        layoutPlanPicker.visibility = View.GONE
                        layoutNoPlans.visibility = View.GONE
                    }
                    R.id.rbLiveSubscriberOnly -> {
                        etPrice.visibility = View.GONE
                        if (planIdByName.isNotEmpty()) {
                            layoutPlanPicker.visibility = View.VISIBLE
                            layoutNoPlans.visibility = View.GONE
                        } else {
                            layoutPlanPicker.visibility = View.GONE
                            layoutNoPlans.visibility = View.VISIBLE
                        }
                    }
                    else -> {
                        etPrice.visibility = View.GONE
                        layoutPlanPicker.visibility = View.GONE
                        layoutNoPlans.visibility = View.GONE
                    }
                }
            } catch (_: Exception) {}
        }

        // Toggle price or plan picker based on access selection
        rgAccess.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbLivePaid -> {
                    etPrice.visibility = View.VISIBLE
                    layoutPlanPicker.visibility = View.GONE
                    layoutNoPlans.visibility = View.GONE
                }
                R.id.rbLiveSubscriberOnly -> {
                    etPrice.visibility = View.GONE
                    // Show dropdown only if plans exist
                    if (planIdByName.isNotEmpty()) {
                        layoutPlanPicker.visibility = View.VISIBLE
                        layoutNoPlans.visibility = View.GONE
                    } else {
                        layoutPlanPicker.visibility = View.GONE
                        layoutNoPlans.visibility = View.VISIBLE
                    }
                }
                else -> {
                    etPrice.visibility = View.GONE
                    layoutPlanPicker.visibility = View.GONE
                    layoutNoPlans.visibility = View.GONE
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
                    requiredPlanId,
                    cbMatch.isChecked
                )
                dismiss()
            }
        }

        btnSchedule.setOnClickListener {
            val title = etTitle.text.toString().trim()
            if (title.isEmpty()) {
                etTitle.error = getString(R.string.stream_title_required)
                return@setOnClickListener
            }
            val categoryName = actvCategory.text.toString().trim()
            val matched = categories.firstOrNull { it.name.equals(categoryName, ignoreCase = true) }
            if (matched == null) {
                actvCategory.error = getString(R.string.stream_title_required).replace("title","category")
                return@setOnClickListener
            }
            val desc = etDesc.text.toString().trim()
            val tags = etTags.text.toString().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val accessType = when (rgAccess.checkedRadioButtonId) {
                R.id.rbLivePaid -> "paid"
                R.id.rbLiveSubscriberOnly -> "subscription"
                else -> "free"
            }
            val priceTokens = if (accessType == "paid") etPrice.text.toString().toIntOrNull() else null
            val selectedPlanName = actvPlan.text.toString()
            val requiredPlanId = if (accessType == "subscription") planIdByName[selectedPlanName] else null
            // Pick date & time, then schedule
            showDateTimePicker { iso ->
                (requireActivity() as? LiveStreamActivity)?.scheduleLiveSession(
                    title, desc, matched.id, tags, accessType, priceTokens, requiredPlanId, iso
                )
                dismiss()
            }
        }
        
        return dialog
    }

    companion object {
        const val TAG = "LiveStreamSetupBottomSheet"
    }

    private fun showDateTimePicker(onPicked: (String) -> Unit) {
        val cal = java.util.Calendar.getInstance()
        val dp = android.app.DatePickerDialog(requireContext(), { _, y, m, d ->
            val tp = android.app.TimePickerDialog(requireContext(), { _, h, min ->
                val c = java.util.Calendar.getInstance()
                c.set(y, m, d, h, min, 0)
                val iso = java.time.Instant.ofEpochMilli(c.timeInMillis).toString()
                onPicked(iso)
            }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), true)
            tp.show()
        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH))
        dp.show()
    }
}
