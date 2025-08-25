package club.gifters.giftersclub.payments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Toast
import club.gifters.giftersclub.BaseActivity
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import club.gifters.giftersclub.SupabaseConfig
import club.gifters.giftersclub.network.RetrofitClient
import kotlinx.coroutines.launch

class PaymentWebViewActivity : BaseActivity() {
    companion object {
        private const val TAG = "PaymentWebView"
        private const val EXTRA_USER_ID = "extra_user_id"
        private const val EXTRA_EMAIL = "extra_email"
        private const val EXTRA_TX_REF = "extra_tx_ref"
        private const val EXTRA_LAST_TX_ID = "extra_last_tx_id"
        private const val EXTRA_AMOUNT = "extra_amount"

        fun start(
            context: Context,
            userId: String,
            email: String,
            amount: Int,
            txRef: String,
            lastTxId: String
        ) {
            Intent(context, PaymentWebViewActivity::class.java).apply {
                putExtra(EXTRA_USER_ID, userId)
                putExtra(EXTRA_EMAIL, email)
                putExtra(EXTRA_TX_REF, txRef)
                putExtra(EXTRA_LAST_TX_ID, lastTxId)
                putExtra(EXTRA_AMOUNT, amount)
                context.startActivity(this)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()
        val email = intent.getStringExtra(EXTRA_EMAIL).orEmpty()
        val txRef = intent.getStringExtra(EXTRA_TX_REF).orEmpty()
        val lastTxId = intent.getStringExtra(EXTRA_LAST_TX_ID).orEmpty()
        val amount = intent.getIntExtra(EXTRA_AMOUNT, 0)

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            // Improve modern checkout compatibility
            // settings.domStorageEnabled = true
            // settings.useWideViewPort = true
            // settings.loadWithOverviewMode = true
            webChromeClient = WebChromeClient()
            addJavascriptInterface(object {
                @JavascriptInterface
                fun onPaymentSuccess(data: String) {
                    lifecycleScope.launch {
                        try {
                            RetrofitClient.tokenApi.updateTokenTransaction(
                                lastTxId,
                                mapOf("flutterwave_transaction_status" to "successful")
                            )
                            RetrofitClient.functionsApi.processPurchaseTokensRpc(
                                mapOf("userId" to userId, "tokens" to amount, "txRef" to txRef)
                            )
                            runOnUiThread {
                                Toast.makeText(
                                    this@PaymentWebViewActivity,
                                    "Tokens purchased successfully!",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } catch (e: Exception) {
                            // Log.e(TAG, "Error processing token purchase", e)
                            runOnUiThread {
                                Toast.makeText(
                                    this@PaymentWebViewActivity,
                                    "Failed to process purchase",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } finally {
                            finish()
                        }
                    }
                }

                @JavascriptInterface
                fun onPaymentCancel() {
                    runOnUiThread {
                        Toast.makeText(
                            this@PaymentWebViewActivity,
                            "Payment cancelled",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                }
            }, "Android")
        }
        setContentView(webView)
        val html = """
            <html>
            <head>
                <script src="https://checkout.flutterwave.com/v3.js"></script>
            </head>
            <body onload="makePayment()">
            <script>
            function makePayment() {
                FlutterwaveCheckout({
                    public_key: "${SupabaseConfig.FLUTTERWAVE_PUBLIC_KEY}",
                    tx_ref: "$txRef",
                    amount: $amount,
                    currency: "KES",
                    customer: { email: "$email", name: "" },
                    customizations: { title: "Token Top-Up", description: "Purchase tokens for your wallet" },
                    callback: function(data) { Android.onPaymentSuccess(JSON.stringify(data)); },
                    onclose: function() { Android.onPaymentCancel(); }
                });
            }
            </script>
            </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }
}