package club.gifters.giftersclub.gifts

import android.content.Context
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import club.gifters.giftersclub.R
import club.gifters.giftersclub.SupabaseConfig
import club.gifters.giftersclub.model.CreateWishlistRequest
import club.gifters.giftersclub.network.RetrofitClient
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

/**
 * Fragment for creating a new wishlist.
 */
class CreateWishlistFragment : Fragment(R.layout.fragment_create_wishlist) {
    companion object {
        private const val ARG_WISHLIST_ID = "wishlist_id"

        /**
         * Create mode (no args) or edit mode (with wishlistId)
         */
        fun newInstance(wishlistId: String? = null): CreateWishlistFragment {
            return CreateWishlistFragment().apply {
                arguments = Bundle().apply {
                    wishlistId?.let { putString(ARG_WISHLIST_ID, it) }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Title and button label for create vs edit modes
        val tvTitle = view.findViewById<TextView>(R.id.tvTitle)
        val btnCreate = view.findViewById<Button>(R.id.btnCreateWishlist)
        val editId = arguments?.getString(ARG_WISHLIST_ID)
        if (editId.isNullOrBlank()) {
            tvTitle.text = getString(R.string.create_wishlist)
            btnCreate.text = getString(R.string.create_wishlist)
        } else {
            tvTitle.text = getString(R.string.edit_wishlist)
            btnCreate.text = getString(R.string.save_changes)
        }
        // Remove fragment-level cancel button; use toolbar back arrow only
        val etName = view.findViewById<EditText>(R.id.etWishlistName)
        val etDescription = view.findViewById<EditText>(R.id.etWishlistDescription)
        val etLink = view.findViewById<EditText>(R.id.etWishlistLink)
        val etImage = view.findViewById<EditText>(R.id.etWishlistImage)
        val etTokens = view.findViewById<EditText>(R.id.etWishlistTokens)
        val tvCost = view.findViewById<TextView>(R.id.tvWishlistCost)
        val tvNote = view.findViewById<TextView>(R.id.tvWishlistNote)

        // Prefill fields when editing existing wishlist
        if (!editId.isNullOrBlank()) {
            lifecycleScope.launch {
                try {
                    val existing = RetrofitClient.wishlistApi.getWishlistById(
                        select = "*", idFilter = "eq.$editId"
                    ).firstOrNull()
                    existing?.let {
                        etName.setText(it.name)
                        etDescription.setText(it.description)
                        etLink.setText(it.link)
                        etImage.setText(it.image)
                        etTokens.setText(it.tokens.toString())
                    }
                } catch (_: Exception) {}
            }
        }

        // Decode current user ID from stored JWT
        var userId = ""
        requireContext().getSharedPreferences("supabase", Context.MODE_PRIVATE)
            .getString("access_token", "")
            ?.takeIf { it.isNotBlank() }
            ?.let { token ->
                token.split('.')
                    .getOrNull(1)
                    ?.let { payload ->
                        val json = String(Base64.decode(payload, Base64.URL_SAFE))
                        userId = JSONObject(json).optString("sub")
                    }
            }

        // Currency conversion based on locale
        val isKenya = Locale.getDefault().country.equals("KE", true)
        etTokens.doAfterTextChanged { editable ->
            val tokens = editable?.toString()?.toIntOrNull() ?: 0
            if (tokens > 0) {
                tvCost.visibility = View.VISIBLE
                if (isKenya) {
                    tvCost.text = "Cost: $tokens KES"
                } else {
                    val usd = tokens * SupabaseConfig.KES_USD_RATE
                    tvCost.text = String.format("Approximate cost: $%.2f", usd)
                }
            } else {
                tvCost.visibility = View.GONE
            }
        }

        btnCreate.setOnClickListener {
            val name = etName.text.toString().trim()
            val description = etDescription.text.toString().trim()
            val link = etLink.text.toString().trim()
            val image = etImage.text.toString().trim()
            val tokens = etTokens.text.toString().toIntOrNull() ?: 0
            if (name.isEmpty() || description.isEmpty()) {
                Toast.makeText(requireContext(), "Name and description are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
        
            lifecycleScope.launch {
                try {
                    if (editId.isNullOrBlank()) {
                        // Create new wishlist
                        val request = CreateWishlistRequest(
                            userId = userId,
                            name = name,
                            description = description,
                            link = link,
                            image = image,
                            tokens = tokens,
                            isFulfilled = false
                        )
                        val resp = RetrofitClient.wishlistApi.createWishlist(createWishlist = request)
                        if (resp.isSuccessful && resp.body().orEmpty().isNotEmpty()) {
                            Toast.makeText(requireContext(), "Wishlist created", Toast.LENGTH_SHORT).show()
                            parentFragmentManager.popBackStack()
                        } else {
                            Toast.makeText(requireContext(), "Failed to create wishlist", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // Update existing wishlist
                        val updates = mapOf(
                            "name" to name,
                            "description" to description,
                            "link" to link,
                            "image" to image,
                            "tokens" to tokens,
                            "is_fulfilled" to false
                        )
                        RetrofitClient.wishlistApi.updateWishlist("eq.$editId", updates)
                        Toast.makeText(requireContext(), "Wishlist updated", Toast.LENGTH_SHORT).show()
                        parentFragmentManager.popBackStack()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error saving wishlist", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}