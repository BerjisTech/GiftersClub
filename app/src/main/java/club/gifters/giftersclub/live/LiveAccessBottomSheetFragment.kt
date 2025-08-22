package club.gifters.giftersclub.live

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import club.gifters.giftersclub.R

class LiveAccessBottomSheetFragment : BottomSheetDialogFragment() {

    interface Listener {
        fun onPurchase(streamId: String)
        fun onSubscribe(creatorId: String, planId: String, planTokens: Int)
    }

    private var listener: Listener? = null

    fun setListener(l: Listener): LiveAccessBottomSheetFragment { listener = l; return this }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_live_access_bottom_sheet, container, false)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val tvTitle = v.findViewById<TextView>(R.id.tvAccessTitle)
        val tvMsg = v.findViewById<TextView>(R.id.tvAccessMessage)
        val llBenefits = v.findViewById<LinearLayout>(R.id.llBenefits)
        val btnPrimary = v.findViewById<Button>(R.id.btnPrimaryAction)
        val btnClose = v.findViewById<CardView>(R.id.btnClosePaywall)

        val mode = arguments?.getString(ARG_MODE) ?: "subscription"
        val message = arguments?.getString(ARG_MESSAGE) ?: "Access required"
        val benefits = arguments?.getStringArrayList(ARG_BENEFITS) ?: arrayListOf()
        val price = arguments?.getInt(ARG_PRICE) ?: 0
        val streamId = arguments?.getString(ARG_STREAM_ID) ?: ""
        val creatorId = arguments?.getString(ARG_CREATOR_ID) ?: ""
        val planId = arguments?.getString(ARG_PLAN_ID) ?: ""
        val planTokens = arguments?.getInt(ARG_PLAN_TOKENS) ?: 0

        tvTitle.text = getString(R.string.access_required)
        tvMsg.text = message
        llBenefits.removeAllViews()
        if (benefits.isNotEmpty()) {
            benefits.forEach { b ->
                val row = TextView(requireContext()).apply {
                    text = "• $b"
                    setTextColor(resources.getColor(android.R.color.white, null))
                    textSize = 12f
                }
                llBenefits.addView(row)
            }
        } else {
            llBenefits.visibility = View.GONE
        }

        if (mode == "paid") {
            btnPrimary.text = getString(R.string.purchase_for_tokens, price)
            btnPrimary.setOnClickListener {
                listener?.onPurchase(streamId)
                dismiss()
            }
        } else {
            btnPrimary.text = getString(R.string.subscribe_upgrade)
            btnPrimary.setOnClickListener {
                if (creatorId.isNotBlank() && planId.isNotBlank()) listener?.onSubscribe(creatorId, planId, planTokens)
                dismiss()
            }
        }

        btnClose.setOnClickListener { dismiss() }
        return v
    }

    companion object {
        private const val ARG_MODE = "mode"
        private const val ARG_MESSAGE = "message"
        private const val ARG_BENEFITS = "benefits"
        private const val ARG_PRICE = "price"
        private const val ARG_STREAM_ID = "streamId"
        private const val ARG_CREATOR_ID = "creatorId"
        private const val ARG_PLAN_ID = "planId"
        private const val ARG_PLAN_TOKENS = "planTokens"

        fun newInstance(
            mode: String,
            message: String,
            benefits: ArrayList<String>?,
            price: Int?,
            streamId: String,
            creatorId: String,
            planId: String?,
            planTokens: Int?
        ): LiveAccessBottomSheetFragment {
            val f = LiveAccessBottomSheetFragment()
            f.arguments = Bundle().apply {
                putString(ARG_MODE, mode)
                putString(ARG_MESSAGE, message)
                putStringArrayList(ARG_BENEFITS, benefits ?: arrayListOf())
                putInt(ARG_PRICE, price ?: 0)
                putString(ARG_STREAM_ID, streamId)
                putString(ARG_CREATOR_ID, creatorId)
                putString(ARG_PLAN_ID, planId ?: "")
                putInt(ARG_PLAN_TOKENS, planTokens ?: 0)
            }
            return f
        }
    }
}

