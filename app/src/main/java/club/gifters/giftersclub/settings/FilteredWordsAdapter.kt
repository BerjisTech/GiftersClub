package club.gifters.giftersclub.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import com.google.android.material.button.MaterialButton

/**
 * Adapter for displaying filtered words with multi-select and remove action.
 */
class FilteredWordsAdapter(
    private val onItemSelectionChanged: (selectedCount: Int) -> Unit,
    private val onRemoveClick: (word: String) -> Unit
) : RecyclerView.Adapter<FilteredWordsAdapter.ViewHolder>() {

    private val items = mutableListOf<String>()
    private val selected = mutableSetOf<String>()

    /** Set the words list and clear any selections. */
    fun setItems(words: List<String>) {
        items.clear()
        items.addAll(words)
        selected.clear()
        notifyDataSetChanged()
        onItemSelectionChanged(0)
    }

    /** Get selected words for bulk removal. */
    fun getSelectedWords(): List<String> = selected.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_filtered_word, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val checkBox: CheckBox = view.findViewById(R.id.cbSelect)
        private val tvWord: TextView = view.findViewById(R.id.tvWord)
        private val btnRemove: MaterialButton = view.findViewById(R.id.btnRemove)

        fun bind(word: String) {
            tvWord.text = word
            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = selected.contains(word)
            checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selected.add(word) else selected.remove(word)
                onItemSelectionChanged(selected.size)
            }
            btnRemove.setOnClickListener {
                onRemoveClick(word)
            }
        }
    }
}