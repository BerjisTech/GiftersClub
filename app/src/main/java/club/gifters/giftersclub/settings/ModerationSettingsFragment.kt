package club.gifters.giftersclub.settings

import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import club.gifters.giftersclub.AuthUtils
import club.gifters.giftersclub.R
import club.gifters.giftersclub.settings.FilteredWordsAdapter
import club.gifters.giftersclub.network.RetrofitClient
import kotlinx.coroutines.launch

class ModerationSettingsFragment : Fragment(R.layout.fragment_moderation_settings) {
    private val profileApi = RetrofitClient.profileApi
    private var userId: String = ""
    private lateinit var etNewWord: TextInputEditText
    private lateinit var btnAdd: MaterialButton
    private lateinit var btnRemoveSelected: MaterialButton
    private lateinit var rvFilteredWords: androidx.recyclerview.widget.RecyclerView
    private lateinit var adapter: FilteredWordsAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        userId = AuthUtils.getCurrentUserId(requireContext()) ?: return

        etNewWord = view.findViewById(R.id.etNewFilteredWord)
        btnAdd = view.findViewById(R.id.btnAddFilteredWord)
        btnRemoveSelected = view.findViewById(R.id.btnRemoveSelectedWords)
        rvFilteredWords = view.findViewById(R.id.rvFilteredWords)

        adapter = FilteredWordsAdapter(
            onItemSelectionChanged = { count -> btnRemoveSelected.visibility = if (count > 0) View.VISIBLE else View.GONE },
            onRemoveClick = { word -> removeWords(listOf(word)) }
        )
        rvFilteredWords.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        rvFilteredWords.adapter = adapter

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

        btnRemoveSelected.setOnClickListener {
            val words = adapter.getSelectedWords()
            if (words.isNotEmpty()) removeWords(words)
        }

        loadFilteredWords()
    }

    private fun loadFilteredWords() {
        lifecycleScope.launch {
            try {
                val words = profileApi.getFilteredWords("word", "eq.$userId").map { it.word }
                adapter.setItems(words)
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Failed to load words", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun removeWords(words: List<String>) {
        lifecycleScope.launch {
            try {
                words.forEach { w ->
                    profileApi.deleteFilteredWord(userIdFilter = "eq.$userId", wordFilter = "eq.$w")
                }
                Toast.makeText(requireContext(), "Word(s) removed", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Failed to remove word(s)", Toast.LENGTH_SHORT).show()
            } finally {
                loadFilteredWords()
            }
        }
    }
}