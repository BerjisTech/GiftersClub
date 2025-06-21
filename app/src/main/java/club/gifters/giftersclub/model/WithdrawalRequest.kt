package club.gifters.giftersclub.model

import com.google.gson.annotations.SerializedName

/**
 * Data model representing a withdrawal request record.
 */
data class WithdrawalRequest(
    val id: String,
    @SerializedName("user_id") val userId: String,
    val tokens: Int,
    @SerializedName("kes_amount") val kesAmount: Int,
    @SerializedName("target_currency") val targetCurrency: String,
    @SerializedName("exchange_rate") val exchangeRate: Double,
    @SerializedName("converted_amount") val convertedAmount: Double,
    val status: String,
    @SerializedName("rejection_reason") val rejectionReason: String?,
    @SerializedName("payment_method") val paymentMethod: String,
    @SerializedName("payment_details") val paymentDetails: Map<String, Any>?,
    @SerializedName("processed_by") val processedBy: String?,
    @SerializedName("processed_at") val processedAt: String?,
    @SerializedName("transaction_reference") val transactionReference: String?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
)