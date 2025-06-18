package club.gifters.giftersclub.gifts

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.network.GiftApi
import club.gifters.giftersclub.network.RetrofitClient
import kotlinx.coroutines.launch

/**
 * Fragment displaying a grid of gifts with sorting options.
 */
class GiftFragment : Fragment(R.layout.fragment_gifts) {

    private val giftApi: GiftApi = RetrofitClient.giftApi
    private lateinit var adapter: GiftAdapter
    private var sortKey: String = "id.desc"
    private var page = 0
    private val limit = 12
    private var isLoading = false
    private var isLastPage = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
        adapter = GiftAdapter { /* TODO: handle gift clicks */ }
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
}