package club.gifters.giftersclub.settings

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.FilteredWord
import club.gifters.giftersclub.network.RetrofitClient
import kotlinx.coroutines.launch

class ModerationSettingsFragment : Fragment(R.layout.fragment_moderation_settings) {
    private val profileApi = RetrofitClient.profileApi
    private var userId: String = ""
    private lateinit var etNewWord: TextInputEditText
    private lateinit var btnAdd: MaterialButton
    private lateinit var spinnerWords: Spinner
    private lateinit var btnRemove: MaterialButton

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userId = AuthUtils.getCurrentUserId(requireContext()) ?: return

        etNewWord = view.findViewById(R.id.etNewFilteredWord)
        btnAdd = view.findViewById(R.id.btnAddFilteredWord)
        spinnerWords = view.findViewById(R.id.spinnerFilteredWords)
        btnRemove = view.findViewById(R.id.btnRemoveFilteredWord)

        btnAdd.setOnClickListener {
            val word = etNewWord.text.toString().trim()
            if (word.isNotEmpty()) {
                lifecycleScope.launch {
                    try {
                        profileApi.insertFilteredWord(mapOf("user_id" to userId, "word" to word))
                        Toast.makeText(requireContext(), "Word added", Toast.LENGTH_SHORT).show()
                        etNewWord.text?.clear()
                        loadFilteredWords()
                    } catch (_: Exception) {
                        Toast.makeText(requireContext(), "Failed to add word", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnRemove.setOnClickListener {
            val selected = spinnerWords.selectedItem as? String ?: return@setOnClickListener
            lifecycleScope.launch {
                try {
                    profileApi.deleteFilteredWord(userIdFilter = "eq.$userId", wordFilter = "eq.$selected")
                    Toast.makeText(requireContext(), "Word removed", Toast.LENGTH_SHORT).show()
                    loadFilteredWords()
                } catch (_: Exception) {
                    Toast.makeText(requireContext(), "Failed to remove word", Toast.LENGTH_SHORT).show()
                }
            }
        }

        loadFilteredWords()
    }

    private fun loadFilteredWords() {
        lifecycleScope.launch {
            try {
                val words = profileApi.getFilteredWords("word", "eq.$userId").map { it.word }
                if (words.isEmpty()) {
                    spinnerWords.adapter = ArrayAdapter(
                        requireContext(), android.R.layout.simple_spinner_item,
                        listOf("You haven't filtered any words yet")
                    )
                    btnRemove.isEnabled = false
                } else {
                    spinnerWords.adapter = ArrayAdapter(
                        requireContext(), android.R.layout.simple_spinner_item,
                        words
                    )
                    btnRemove.isEnabled = true
                }
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Failed to load words", Toast.LENGTH_SHORT).show()
            }
        }
    }
}