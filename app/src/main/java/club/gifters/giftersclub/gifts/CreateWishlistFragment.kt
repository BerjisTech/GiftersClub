package club.gifters.giftersclub.gifts

import android.content.Context
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
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
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val btnCancel = view.findViewById<ImageButton>(R.id.btnCancel)
        val etName = view.findViewById<EditText>(R.id.etWishlistName)
        val etDescription = view.findViewById<EditText>(R.id.etWishlistDescription)
        val etLink = view.findViewById<EditText>(R.id.etWishlistLink)
        val etImage = view.findViewById<EditText>(R.id.etWishlistImage)
        val etTokens = view.findViewById<EditText>(R.id.etWishlistTokens)
        val tvCost = view.findViewById<TextView>(R.id.tvWishlistCost)
        val tvNote = view.findViewById<TextView>(R.id.tvWishlistNote)
        val btnCreate = view.findViewById<Button>(R.id.btnCreateWishlist)

        btnCancel.setOnClickListener {
            parentFragmentManager.popBackStack()
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
                    if (!resp.isSuccessful) {
                        Toast.makeText(requireContext(), "Failed to create wishlist", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val createdList = resp.body().orEmpty()
                    if (createdList.isNotEmpty()) {
                        Toast.makeText(requireContext(), "Wishlist created", Toast.LENGTH_SHORT).show()
                        parentFragmentManager.popBackStack()
                    } else {
                        Toast.makeText(requireContext(), "Failed to create wishlist", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error creating wishlist", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}