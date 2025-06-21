package club.gifters.giftersclub.model

import com.google.gson.annotations.SerializedName

data class TokenTransaction(
    val id: String,
    @SerializedName("user_id") val userId: String,
    @SerializedName("transaction_type") val transactionType: String,
    val tokens: Int,
    @SerializedName("kes_amount") val kesAmount: Int,
    @SerializedName("flutterwave_transaction_id") val flutterwaveTransactionId: String,
    @SerializedName("flutterwave_transaction_status") val flutterwaveTransactionStatus: String,
    @SerializedName("reference_id") val referenceId: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("updated_at") val updatedAt: String?
)