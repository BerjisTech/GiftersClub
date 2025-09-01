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
import android.view.LayoutInflater
import org.json.JSONArray
import org.json.JSONObject
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
  private lateinit var offeringsContainer: LinearLayout
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
    offeringsContainer = view.findViewById(R.id.offeringsContainer)

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

    // initialize with one offering row
    addOfferingRow("")

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
        // Build description, embedding offerings as JSON if provided
        val offerings = getOfferings()
        val descToPersist: String? = if (offerings.isNotEmpty()) {
          val json = JSONObject().apply {
            put("text", desc ?: "")
            put("features", JSONArray(offerings))
          }
          json.toString()
        } else {
          desc
        }

        val success = withContext(Dispatchers.IO) {
          if (editingPlanId != null) {
            val updates = mutableMapOf<String, Any>(
              "name" to name,
              "tokens" to tokens,
              "duration_type" to duration
            )
            descToPersist?.let { updates["description"] = it }
            RetrofitClient.subscriptionPlanApi.updateSubscriptionPlan(
              "eq.${editingPlanId!!}",
              updates
            ).isSuccessful
          } else {
            val body = mutableMapOf<String, Any>(
              "creator_id" to userId,
              "name" to name,
              "tokens" to tokens,
              "duration_type" to duration
            )
            descToPersist?.let { body["description"] = it }
            RetrofitClient.subscriptionPlanApi.createSubscriptionPlanMap(body).isSuccessful
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
      // If description is JSON with features, populate UI accordingly
      val (descText, features) = parseDescription(plan.description)
      etDescription.setText(descText)
      setOfferings(features)
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
          RetrofitClient.subscriptionPlanApi.deleteSubscriptionPlan("eq.${plan.id}").isSuccessful
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
    setOfferings(emptyList())
  }

  private fun addOfferingRow(text: String?) {
    val row = LayoutInflater.from(requireContext())
      .inflate(R.layout.item_offering_input, offeringsContainer, false)
    val et = row.findViewById<EditText>(R.id.etOffering)
    val btn = row.findViewById<ImageButton>(R.id.btnAddOffering)
    et.setText(text ?: "")
    btn.setOnClickListener {
      addOfferingRow("")
      updateOfferingButtons()
    }
    offeringsContainer.addView(row)
    updateOfferingButtons()
  }

  private fun updateOfferingButtons() {
    val count = offeringsContainer.childCount
    for (i in 0 until count) {
      val row = offeringsContainer.getChildAt(i)
      val btn = row.findViewById<ImageButton>(R.id.btnAddOffering)
      btn.visibility = if (i == count - 1) View.VISIBLE else View.INVISIBLE
    }
  }

  private fun getOfferings(): List<String> {
    val list = mutableListOf<String>()
    for (i in 0 until offeringsContainer.childCount) {
      val row = offeringsContainer.getChildAt(i)
      val et = row.findViewById<EditText>(R.id.etOffering)
      val value = et.text?.toString()?.trim().orEmpty()
      if (value.isNotEmpty()) list.add(value)
    }
    return list
  }

  private fun setOfferings(items: List<String>) {
    offeringsContainer.removeAllViews()
    if (items.isEmpty()) {
      addOfferingRow("")
    } else {
      items.forEach { addOfferingRow(it) }
    }
    updateOfferingButtons()
  }

  private fun parseDescription(desc: String?): Pair<String, List<String>> {
    if (desc.isNullOrBlank()) return "" to emptyList()
    return try {
      val obj = JSONObject(desc)
      val text = obj.optString("text", desc)
      val features = obj.optJSONArray("features")?.let { ja ->
        List(ja.length()) { idx -> ja.optString(idx).orEmpty() }.filter { it.isNotBlank() }
      } ?: emptyList()
      text to features
    } catch (_: Exception) {
      desc to emptyList()
    }
  }
}
