package club.gifters.giftersclub.live

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.cardview.widget.CardView
import club.gifters.giftersclub.R
import com.google.android.flexbox.FlexboxLayout
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class TokenPurchaseBottomSheetFragment : BottomSheetDialogFragment() {

    interface Listener {
        fun onPurchase(amount: Int)
    }

    private var listener: Listener? = null

    fun setListener(l: Listener): TokenPurchaseBottomSheetFragment { listener = l; return this }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_purchase_tokens_bottom_sheet, container, false)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val presets = arguments?.getIntegerArrayList(ARG_PRESETS) ?: arrayListOf(10, 50, 500, 1200, 3000)
        val ll = v.findViewById<FlexboxLayout>(R.id.llPresetAmounts)
        ll.removeAllViews()
        presets.forEach { amt ->
            val btn = Button(requireContext()).apply {
                text = amt.toString()
                val bg = when {
                    amt >= 1200 -> R.drawable.bg_chip_amount_high
                    amt >= 100 -> R.drawable.bg_chip_amount_mid
                    else -> R.drawable.bg_chip_amount_low
                }
                setBackgroundResource(bg)
                setTextColor(resources.getColor(android.R.color.white, null))
                val drawable = resources.getDrawable(R.drawable.token, null)
                val size = resources.getDimensionPixelSize(R.dimen.size_16dp)
                drawable.setBounds(0, 0, size, size)
                setCompoundDrawables(drawable, null, null, null)
                compoundDrawablePadding = 12
                gravity = android.view.Gravity.CENTER_VERTICAL
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.marginEnd = resources.getDimensionPixelSize(R.dimen.spacing_small)
                layoutParams = params
                setPadding(32, 12, 32, 12)
                setOnClickListener { listener?.onPurchase(amt); dismiss() }
            }
            ll.addView(btn)
        }
        val etCustom = v.findViewById<EditText>(R.id.etCustomAmount)
        v.findViewById<Button>(R.id.btnBuyCustom).setOnClickListener {
            val valStr = etCustom.text?.toString()?.trim()
            val amt = valStr?.toIntOrNull() ?: 0
            if (amt > 0) { listener?.onPurchase(amt); dismiss() }
        }
        v.findViewById<CardView>(R.id.btnClosePurchase).setOnClickListener { dismiss() }
        return v
    }

    companion object {
        private const val ARG_PRESETS = "presets"
        fun newInstance(presets: ArrayList<Int>): TokenPurchaseBottomSheetFragment {
            val f = TokenPurchaseBottomSheetFragment()
            f.arguments = Bundle().apply { putIntegerArrayList(ARG_PRESETS, presets) }
            return f
        }
    }
}
