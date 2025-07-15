package club.gifters.giftersclub

/**
 * Constants for withdrawal functionality: limits, exchange rates, and payment methods.
 */
object WithdrawalsConstants {
    const val MIN_WITHDRAWAL_KES = 500
    const val WITHDRAWAL_PROCESSING_DAYS = 3

    const val KES_USD_RATE = 0.0078
    val EXCHANGE_RATES: Map<String, Double> = mapOf(
        "USD" to KES_USD_RATE,
        "NGN" to 6.96,
        "GHS" to 0.09,
        "TZS" to 18.2,
        "RWF" to 7.8
    )

    val PAYMENT_METHODS: List<String> = listOf(
        "PayPal",
        "Bank Transfer",
        "Mpesa",
        "Paga (Nigeria)",
        "MTN MoMo (Nigeria)",
        "MTN MoMo (Ghana)",
        "Airtel Money (Ghana)",
        "Vodacom M-Pesa (Tanzania)",
        "Tigo Pesa (Tanzania)",
        "MTN Momo (Rwanda)"
    )

    val MOBILE_MONEY_METHODS: List<String> = listOf(
        "Mpesa",
        "Paga (Nigeria)",
        "MTN MoMo (Nigeria)",
        "MTN MoMo (Ghana)",
        "Airtel Money (Ghana)",
        "Vodacom M-Pesa (Tanzania)",
        "Tigo Pesa (Tanzania)",
        "MTN Momo (Rwanda)"
    )

    val METHOD_CURRENCY_MAP: Map<String, String> = mapOf(
        "PayPal" to "USD",
        "Bank Transfer" to "KES",
        "Mpesa" to "KES",
        "Paga (Nigeria)" to "NGN",
        "MTN MoMo (Nigeria)" to "NGN",
        "MTN MoMo (Ghana)" to "GHS",
        "Airtel Money (Ghana)" to "GHS",
        "Vodacom M-Pesa (Tanzania)" to "TZS",
        "Tigo Pesa (Tanzania)" to "TZS",
        "MTN Momo (Rwanda)" to "RWF"
    )
}