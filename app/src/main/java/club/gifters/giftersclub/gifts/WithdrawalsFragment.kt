package club.gifters.giftersclub.gifts

import android.content.Context
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.*
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.network.RetrofitClient
import club.gifters.giftersclub.WithdrawalsConstants
import club.gifters.giftersclub.gifts.WithdrawalAdapter
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Fragment for requesting withdrawals and viewing withdrawal history.
 */
class WithdrawalsFragment : Fragment(R.layout.fragment_withdrawals) {
    private val profileApi = RetrofitClient.profileApi
    private val withdrawalApi = RetrofitClient.withdrawalApi

    private lateinit var tvAvailTokens: TextView
    private lateinit var tvAvailCurrency: TextView
    private lateinit var tvAvailSummary: TextView
    private lateinit var etAmount: EditText
    private lateinit var spinnerMethod: Spinner
    private lateinit var tvDetailsLabel: TextView
    private lateinit var etDetails: EditText
    private lateinit var tvSummary: TextView
    private lateinit var btnSubmit: Button
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvEmpty: TextView

    private lateinit var adapter: WithdrawalAdapter
    private var userId: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().title = getString(R.string.withdrawals)
        tvAvailTokens = view.findViewById(R.id.tvAvailableBalanceTokens)
        tvAvailCurrency = view.findViewById(R.id.tvAvailableBalanceCurrency)
        tvAvailSummary = view.findViewById(R.id.tvAvailableBalanceSummary)
        etAmount = view.findViewById(R.id.etWithdrawAmount)
        spinnerMethod = view.findViewById(R.id.spinnerPaymentMethod)
        tvDetailsLabel = view.findViewById(R.id.tvPaymentDetailsLabel)
        etDetails = view.findViewById(R.id.etPaymentDetails)
        tvSummary = view.findViewById(R.id.tvWithdrawalSummary)
        btnSubmit = view.findViewById(R.id.btnSubmitWithdrawal)
        rvHistory = view.findViewById(R.id.rvWithdrawals)
        tvEmpty = view.findViewById(R.id.tvEmptyWithdrawals)

        // Extract userId from stored access token
        requireContext().getSharedPreferences("supabase", Context.MODE_PRIVATE)
            .getString("access_token", null)
            ?.split('.')
            ?.getOrNull(1)
            ?.let { part ->
                String(Base64.decode(part, Base64.URL_SAFE))
            }
            ?.let { decoded ->
                userId = JSONObject(decoded).optString("sub")
            }

        adapter = WithdrawalAdapter()
        rvHistory.layoutManager = LinearLayoutManager(requireContext())
        rvHistory.adapter = adapter

        spinnerMethod.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            WithdrawalsConstants.PAYMENT_METHODS
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        spinnerMethod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                val method = parent.getItemAtPosition(pos) as String
                if (WithdrawalsConstants.MOBILE_MONEY_METHODS
                        .map { it.lowercase() }
                        .contains(method.lowercase())
                ) {
                    tvDetailsLabel.visibility = View.VISIBLE
                    etDetails.visibility = View.VISIBLE
                } else {
                    tvDetailsLabel.visibility = View.GONE
                    etDetails.visibility = View.GONE
                }
                updateSummary()
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        etAmount.doAfterTextChanged { updateSummary() }
        btnSubmit.setOnClickListener { submitWithdrawal() }

        loadProfileAndHistory()
    }

    private fun updateSummary() {
        val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0
        if (amount <= 0.0) {
            tvSummary.text = ""
            return
        }
        val net = amount * 0.7
        val fee = amount * 0.3
        val method = spinnerMethod.selectedItem as String
        val currency = WithdrawalsConstants.METHOD_CURRENCY_MAP[method] ?: "USD"
        val rate = WithdrawalsConstants.EXCHANGE_RATES[currency] ?: 1.0
        val receive = net * rate
        val feeCur = fee * rate
        tvSummary.text = "You will receive: %.2f %s (• Fee: %.2f %s)"
            .format(receive, currency, feeCur, currency)
    }

    private fun loadProfileAndHistory() {
        lifecycleScope.launch {
            try {
                // Load profile to get balance
                val list = profileApi.getProfileByUserId("*", "eq.$userId")
                list.firstOrNull()?.let { profile ->
                    val bal = profile.tokenBalance ?: 0
                    tvAvailTokens.text = "$bal tokens"
                    val curVal = bal * WithdrawalsConstants.KES_USD_RATE
                    tvAvailCurrency.text = "$%.2f".format(curVal)
                    val total = curVal * 0.7
                    val fee = curVal * 0.3
                    tvAvailSummary.text = "Total received: %.2f • Transaction fee: %.2f"
                        .format(total, fee)
                }
                // Load withdrawal history
                val history = withdrawalApi.getWithdrawalsByUser("*", "eq.$userId")
                if (history.isNotEmpty()) {
                    adapter.submitList(history)
                    tvEmpty.visibility = View.GONE
                    rvHistory.visibility = View.VISIBLE
                } else {
                    tvEmpty.visibility = View.VISIBLE
                    rvHistory.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("WithdrawalsFragment", "Error loading profile/history", e)
            }
        }
    }

    private fun submitWithdrawal() {
        val amount = etAmount.text.toString().toIntOrNull()
        if (amount == null || amount < WithdrawalsConstants.MIN_WITHDRAWAL_KES) {
            Toast.makeText(
                requireContext(),
                "Minimum withdrawal is ${WithdrawalsConstants.MIN_WITHDRAWAL_KES} KES",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val method = spinnerMethod.selectedItem as? String
        if (method.isNullOrBlank()) {
            Toast.makeText(requireContext(), "Please select a payment method", Toast.LENGTH_SHORT).show()
            return
        }
        val details = etDetails.text.toString().takeIf { etDetails.visibility == View.VISIBLE && it.isNotBlank() }
        val targetCurrency = WithdrawalsConstants.METHOD_CURRENCY_MAP[method] ?: "USD"
        val exchangeRate = WithdrawalsConstants.EXCHANGE_RATES[targetCurrency] ?: 1.0
        val params = mutableMapOf<String, Any>(
            "p_user_id" to userId,
            "p_tokens" to amount,
            "p_target_currency" to targetCurrency,
            "p_exchange_rate" to exchangeRate,
            "p_payment_method" to method
        )
        details?.let { params["p_payment_details"] = mapOf("details" to it) }
        lifecycleScope.launch {
            try {
                val result = withdrawalApi.requestWithdrawal(params)
                if (result.isNotEmpty()) {
                    loadProfileAndHistory()
                    etAmount.text?.clear()
                    etDetails.text?.clear()
                } else {
                    Toast.makeText(requireContext(), "Withdrawal request failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("WithdrawalsFragment", "Error submitting withdrawal", e)
                Toast.makeText(requireContext(), "Error submitting withdrawal", Toast.LENGTH_SHORT).show()
            }
        }
    }
}