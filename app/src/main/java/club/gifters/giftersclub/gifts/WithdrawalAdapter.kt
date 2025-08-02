package club.gifters.giftersclub.gifts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import club.gifters.giftersclub.R
import club.gifters.giftersclub.model.WithdrawalRequest
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Adapter for displaying user withdrawal request history.
 */
class WithdrawalAdapter(
    private val items: MutableList<WithdrawalRequest> = mutableListOf()
) : RecyclerView.Adapter<WithdrawalAdapter.VH>() {

    fun submitList(list: List<WithdrawalRequest>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_withdrawal_request, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvInfo: TextView = view.findViewById(R.id.tvWithdrawalInfo)
        private val tvDate: TextView = view.findViewById(R.id.tvWithdrawalDate)
        private val tvStatus: TextView = view.findViewById(R.id.tvWithdrawalStatus)
        private val tvReason: TextView = view.findViewById(R.id.tvWithdrawalReason)
        private val tvConverted: TextView = view.findViewById(R.id.tvWithdrawalConverted)

        fun bind(wr: WithdrawalRequest) {
            // Format amounts: KES with grouping, converted with two decimals
            val nf = NumberFormat.getNumberInstance()
            val kes = nf.format(wr.kesAmount)
            val converted = String.format(Locale.getDefault(), "%.2f", wr.convertedAmount)
            tvInfo.text = "$kes KES (~ KES ${nf.format(wr.kesAmount * 0.7)}) via ${wr.paymentMethod}})"
            // Format the date
            val raw = wr.createdAt
            tvDate.text = try {
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    .format(OffsetDateTime.parse(raw).toLocalDate())
            } catch (_: Exception) {
                raw.substringBefore('T')
            }
            tvStatus.text = wr.status.replaceFirstChar { it.uppercase() }
            val ctx = tvStatus.context
            val colorRes = when (wr.status) {
                "disbursed" -> R.color.green_300
                "requested", "processing" -> R.color.orange_300
                "rejected" -> R.color.red_400
                else -> android.R.color.darker_gray
            }
            tvStatus.setBackgroundColor(ContextCompat.getColor(ctx, colorRes))
            if (wr.status == "rejected") {
                tvReason.visibility = View.VISIBLE
                tvReason.text = "Reason: ${wr.rejectionReason}"
            } else {
                tvReason.visibility = View.GONE
            }
            if (wr.status == "disbursed") {
                tvConverted.visibility = View.VISIBLE
                tvConverted.text = "Ref: ${wr.transactionReference ?: "-"}"
            } else {
                tvConverted.visibility = View.GONE
            }
        }
    }
}