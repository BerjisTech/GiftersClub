package club.gifters.giftersclub.gifts

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.fragment.app.Fragment
import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.appcompat.app.AlertDialog
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import org.json.JSONObject
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.network.GiftApi
import club.gifters.giftersclub.model.Gift
import club.gifters.giftersclub.network.RetrofitClient
import kotlinx.coroutines.launch

/**
 * Fragment displaying a grid of gifts with sorting options.
 */
class GiftFragment : Fragment(R.layout.fragment_gifts) {
    companion object {
        private const val ARG_RECIPIENT_ID = "recipient_id"
        private const val ARG_RECIPIENT_USERNAME = "recipient_username"

        /**
         * Create a new instance targeting a specific recipient.
         */
        fun newInstance(recipientId: String, recipientUsername: String) = GiftFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_RECIPIENT_ID, recipientId)
                putString(ARG_RECIPIENT_USERNAME, recipientUsername)
            }
        }
    }

    private var recipientUserId: String? = null
    private var recipientUsername: String? = null

    private val giftApi: GiftApi = RetrofitClient.giftApi
    private val TAG = "GiftFragment"
    private lateinit var adapter: GiftAdapter
    private var sortKey: String = "id.desc"
    private var page = 0
    private val limit = 12
    private var isLoading = false
    private var isLastPage = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arguments?.let {
            recipientUserId = it.getString(ARG_RECIPIENT_ID)
            recipientUsername = it.getString(ARG_RECIPIENT_USERNAME)
        }
        val spinner = view.findViewById<Spinner>(R.id.spinnerSort)
        val options = resources.getStringArray(R.array.gift_sort_options)
        spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                sortKey = when (pos) {
                    1 -> "id.asc"          // Oldest
                    2 -> "is_popular.desc"  // Most Popular
                    3 -> "tokens.asc"       // Price: Low to High
                    4 -> "tokens.desc"      // Price: High to Low
                    else -> "id.desc"       // Newest
                }
                page = 0
                isLastPage = false
                loadGifts(clear = true)
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val recycler = view.findViewById<RecyclerView>(R.id.recyclerGifts)
        recycler.layoutManager = GridLayoutManager(context, 2)
        adapter = GiftAdapter { gift ->
            recipientUserId?.let { showSendGiftDialog(gift) }
        }
        recycler.adapter = adapter
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                val layout = rv.layoutManager as GridLayoutManager
                val visible = layout.childCount
                val total = layout.itemCount
                val first = layout.findFirstVisibleItemPosition()
                if (!isLoading && !isLastPage
                    && visible + first >= total
                    && first >= 0
                    && total >= limit
                ) {
                    loadGifts(clear = false)
                }
            }
        })
        loadGifts(clear = true)
    }

    private fun loadGifts(clear: Boolean = false) {
        if (isLoading || isLastPage) return
        isLoading = true
        lifecycleScope.launch {
            val items = giftApi.getGifts("*", sortKey, limit, page * limit)
            if (clear) adapter.submitList(items)
            else adapter.submitList(adapter.currentList + items)
            if (items.size < limit) isLastPage = true else page++
            isLoading = false
        }
    }

    private fun showSendGiftDialog(gift: Gift) {
        AlertDialog.Builder(requireContext())
            .setTitle("Send Gift")
            .setMessage(
                "Send ${gift.name} for ${gift.tokens} tokens to ${recipientUsername ?: "user"}?"
            )
            .setPositiveButton("Send") { _, _ -> sendGift(gift) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun sendGift(gift: Gift) {
        val gifterId = getCurrentUserId() ?: run {
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }
        val recipientId = recipientUserId ?: return
        val txRef = "gift_${gifterId}_${System.currentTimeMillis()}"
        lifecycleScope.launch {
            try {
                RetrofitClient.functionsApi.processGiftSendRpc(
                    mapOf(
                        "giftId" to gift.id,
                        "gifterId" to gifterId,
                        "recipientId" to recipientId,
                        "tokens" to gift.tokens,
                        "txRef" to txRef
                    )
                )
                Toast.makeText(requireContext(), "Gift sent successfully!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error sending gift", e)
                Toast.makeText(requireContext(), "Failed to send gift", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getCurrentUserId(): String? {
        val prefs = requireContext().getSharedPreferences("supabase", Context.MODE_PRIVATE)
        val token = prefs.getString("access_token", null) ?: return null
        val parts = token.split('.')
        if (parts.size < 2) return null
        val decoded = String(Base64.decode(parts[1], Base64.URL_SAFE))
        return JSONObject(decoded).optString("sub")
    }
}