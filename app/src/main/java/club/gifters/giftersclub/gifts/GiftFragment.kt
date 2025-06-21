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
import android.widget.EditText
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import coil.load
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.network.GiftApi
import club.gifters.giftersclub.model.Gift
import club.gifters.giftersclub.model.Profile
import club.gifters.giftersclub.network.RetrofitClient
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import retrofit2.HttpException
import club.gifters.giftersclub.payments.PaymentWebViewActivity
import org.json.JSONObject

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
        adapter = GiftAdapter { gift -> handleGiftClick(gift) }
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

    private fun showConfirmDialog(gift: Gift) {
        AlertDialog.Builder(requireContext())
            .setTitle("Send Gift")
            .setMessage(
                "Send ${gift.name} for ${gift.tokens} tokens to ${recipientUsername ?: "user"}?"
            )
            .setPositiveButton("Send") { _, _ -> sendGift(gift) }
            .setNeutralButton("Change") { _, _ -> showRecipientSearchDialog(gift) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun sendGift(gift: Gift) {
        val gifterId = getCurrentUserId() ?: run {
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }
        val recipientId = recipientUserId ?: return
        lifecycleScope.launch {
            try {
                // Fetch gifter profile to check token balance
                val list = RetrofitClient.profileApi.getProfileByUserId("*", "eq.$gifterId")
                val profile = list.firstOrNull()
                val balance = profile?.tokenBalance ?: 0
                if (balance < gift.tokens) {
                    showTopUpPrompt(gift, gifterId, profile?.email.orEmpty(), gift.tokens - balance)
                    return@launch
                }
                // Sufficient balance: proceed to send gift
                val txRef = "gift_${gifterId}_${System.currentTimeMillis()}"
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
            } catch (e: HttpException) {
                Log.e(TAG, "Error sending gift", e)
                Toast.makeText(requireContext(), "Failed to send gift", Toast.LENGTH_SHORT).show()
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
    
    private fun handleGiftClick(gift: Gift) {
        if (recipientUserId != null) showConfirmDialog(gift)
        else showRecipientSearchDialog(gift)
    }

    /**
     * Prompt user to search and select a recipient dynamically.
     */
    private fun showRecipientSearchDialog(gift: Gift) {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_search_user, null)
        val et = view.findViewById<EditText>(R.id.etSearch)
        val rv = view.findViewById<RecyclerView>(R.id.rvResults)
        val adapter = SearchUserAdapter { prof ->
            dialog.dismiss()
            recipientUserId = prof.userId
            recipientUsername = prof.username
            showConfirmDialog(gift)
        }
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        et.doAfterTextChanged { etxt ->
            val q = etxt.toString().trim()
            // Only search when input is at least 2 characters to avoid bad requests
            if (q.length >= 2) {
                lifecycleScope.launch {
                    val filter = "(username.ilike.*$q*,email.ilike.*$q*)"
                    Log.i(TAG, "Searching profiles with filter: $filter")
                    try {
                        val list = RetrofitClient.profileApi.searchProfiles("*", filter)
                        Log.i(TAG, "Search returned ${'$'}{list.size} profiles")
                        adapter.submitList(list)
                    } catch (e: HttpException) {
                        Log.w(TAG, "Search HTTP error (filter=$filter)", e)
                        if (e.code() == 401) {
                            Toast.makeText(requireContext(), "Please login to search users", Toast.LENGTH_SHORT).show()
                        }
                        adapter.submitList(emptyList())
                    } catch (e: Exception) {
                        Log.e(TAG, "Search error (filter=$filter)", e)
                        adapter.submitList(emptyList())
                    }
                }
            } else {
                adapter.submitList(emptyList())
            }
        }
        dialog.setContentView(view)
        dialog.show()
        // Auto-focus search field and show keyboard
        et.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(et, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }

}

/**
 * Prompt user to top up tokens before gifting.
 */
private fun Fragment.showTopUpPrompt(
    gift: Gift,
    userId: String,
    email: String,
    needed: Int
) {
    AlertDialog.Builder(requireContext())
        .setTitle("Insufficient tokens")
        .setMessage("You have insufficient tokens. You need $needed more to send this gift. Top up now?")
        .setPositiveButton("Buy Tokens") { _, _ ->
            // Launch token purchase flow
            val txRef = "topup_${userId}_${System.currentTimeMillis()}"
            PaymentWebViewActivity.start(requireContext(), userId, email, needed, txRef, "")
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}

/**
 * Adapter for showing profile search results in GiftFragment.
 */
private class SearchUserAdapter(
    private val onClick: (Profile) -> Unit
) : RecyclerView.Adapter<SearchUserAdapter.VH>() {
    private val items = mutableListOf<Profile>()
    fun submitList(list: List<Profile>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_user, parent, false)
        return VH(v)
    }
    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val iv: ImageView = view.findViewById(R.id.ivAvatar)
        private val tvName: TextView = view.findViewById(R.id.tvName)
        private val tvUsername: TextView = view.findViewById(R.id.tvUsername)
        init {
            view.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onClick(items[pos])
                }
            }
        }
        fun bind(p: Profile) {
            tvName.text = p.name.orEmpty()
            tvUsername.text = "@${p.username}"
            if (p.image.isNotBlank()) iv.load(p.image)
            else iv.setImageResource(android.R.color.darker_gray)
        }
    }
}