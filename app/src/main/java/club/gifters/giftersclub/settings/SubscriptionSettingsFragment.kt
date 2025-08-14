package club.gifters.giftersclub.settings

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import club.gifters.giftersclub.R
import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.model.SubscriptionPlan
import club.gifters.giftersclub.network.RetrofitClient
import android.widget.ArrayAdapter
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Subscription settings UI for creators to manage their subscription plans.
 */
class SubscriptionSettingsFragment : Fragment(R.layout.fragment_subscription_settings) {
  private lateinit var plansContainer: LinearLayout
  private lateinit var etName: EditText
  private lateinit var etDescription: EditText
  private lateinit var etTokens: EditText
  private lateinit var spinnerDuration: Spinner
  private lateinit var btnSave: MaterialButton
  private lateinit var btnCancel: MaterialButton
  private var editingPlanId: String? = null

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    plansContainer = view.findViewById(R.id.plansContainer)
    etName = view.findViewById(R.id.etPlanName)
    etDescription = view.findViewById(R.id.etPlanDescription)
    etTokens = view.findViewById(R.id.etPlanTokens)
    spinnerDuration = view.findViewById(R.id.spinnerDurationType)
    btnSave = view.findViewById(R.id.btnSavePlan)
    btnCancel = view.findViewById(R.id.btnCancelEdit)

    ArrayAdapter.createFromResource(
      requireContext(),
      R.array.subscription_duration_types,
      android.R.layout.simple_spinner_item
    ).also { adapter ->
      adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
      spinnerDuration.adapter = adapter
    }

    lifecycleScope.launch {
      val userId = AuthUtils.getCurrentUserId(requireContext()) ?: return@launch
      loadPlans(userId)
    }

    btnSave.setOnClickListener {
      lifecycleScope.launch {
        val name = etName.text.toString().trim()
        val desc = etDescription.text.toString().trim().ifEmpty { null }
        val tokens = etTokens.text.toString().toIntOrNull() ?: 0
        val duration = spinnerDuration.selectedItem.toString()
        val userId = AuthUtils.getCurrentUserId(requireContext()) ?: return@launch
        if (name.isEmpty() || tokens <= 0) {
          Toast.makeText(context, "Name and valid token amount required", Toast.LENGTH_SHORT).show()
          return@launch
        }
        val success = withContext(Dispatchers.IO) {
          if (editingPlanId != null) {
            val updates = mutableMapOf<String, Any>(
              "name" to name,
              "tokens" to tokens,
              "duration_type" to duration
            )
            desc?.let { updates["description"] = it }
            RetrofitClient.subscriptionPlanApi.updateSubscriptionPlan(
              editingPlanId!!,
              updates
            ).isSuccessful
          } else {
            val plan = SubscriptionPlan(
              id = "",
              creator_id = userId,
              name = name,
              description = desc,
              tokens = tokens,
              duration_type = duration,
              created_at = "",
              updated_at = ""
            )
            RetrofitClient.subscriptionPlanApi.createSubscriptionPlan(plan).isSuccessful
          }
        }
        Toast.makeText(context,
          if (success) "Plan saved" else "Failed to save plan",
          Toast.LENGTH_SHORT
        ).show()
        editingPlanId = null
        resetForm()
        loadPlans(userId)
      }
    }

    btnCancel.setOnClickListener {
      editingPlanId = null
      resetForm()
    }
  }

  private suspend fun loadPlans(creatorId: String) {
    val plans = withContext(Dispatchers.IO) {
      RetrofitClient.subscriptionPlanApi.getSubscriptionPlans("eq.$creatorId")
    }
    plansContainer.removeAllViews()
    for (plan in plans) {
      addPlanRow(plan)
    }
  }

  private fun addPlanRow(plan: SubscriptionPlan) {
    val item = layoutInflater.inflate(R.layout.item_subscription_plan, plansContainer, false)
    item.findViewById<TextView>(R.id.tvPlanName).text = plan.name
    item.findViewById<TextView>(R.id.tvPlanTokens).text = plan.tokens.toString()
    item.findViewById<TextView>(R.id.tvPlanDuration).text = plan.duration_type
    item.findViewById<ImageButton>(R.id.btnEditPlan).setOnClickListener {
      editingPlanId = plan.id
      etName.setText(plan.name)
      etDescription.setText(plan.description ?: "")
      etTokens.setText(plan.tokens.toString())
      val idx = when (plan.duration_type) {
        "monthly" -> 1
        "annual" -> 2
        else -> 0
      }
      spinnerDuration.setSelection(idx)
      btnCancel.visibility = View.VISIBLE
    }
    item.findViewById<ImageButton>(R.id.btnDeletePlan).setOnClickListener {
      lifecycleScope.launch {
        val userId = AuthUtils.getCurrentUserId(requireContext()) ?: return@launch
        val ok = withContext(Dispatchers.IO) {
          RetrofitClient.subscriptionPlanApi.deleteSubscriptionPlan(plan.id).isSuccessful
        }
        Toast.makeText(context,
          if (ok) "Plan deleted" else "Failed to delete plan",
          Toast.LENGTH_SHORT
        ).show()
        loadPlans(userId)
      }
    }
    plansContainer.addView(item)
  }

  private fun resetForm() {
    etName.text?.clear()
    etDescription.text?.clear()
    etTokens.text?.clear()
    spinnerDuration.setSelection(0)
    btnCancel.visibility = View.GONE
  }
}