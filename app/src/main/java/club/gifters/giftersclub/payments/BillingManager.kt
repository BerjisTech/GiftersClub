package club.gifters.giftersclub.payments

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.android.billingclient.api.*
import com.rollbar.android.Rollbar
import club.gifters.giftersclub.BuildConfig
import club.gifters.giftersclub.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Minimal Google Play Billing integration for token purchases.
 * - Queries consumable INAPP products for token packs.
 * - Launches purchase flow.
 * - Sends purchaseToken to backend for server verification + credit.
 * - Consumes the purchase after backend success.
 */
object BillingManager : PurchasesUpdatedListener {
    private lateinit var appContext: Context
    private var billingClient: BillingClient? = null
    private var connected = false

    // Cache of ProductDetails by productId
    private val productDetails = mutableMapOf<String, ProductDetails>()

    // Mapping productId -> token credits (must match Play Console product IDs)
    private val tokenPacks = linkedMapOf(
        // Smaller packs first so chooseProductId can pick the next >= desired
        "gift_50" to 50,
        "gift_100" to 100,
        "gift_250" to 250,
        "gift_500" to 500,
        "gift_1000" to 1000,
        "gift_1500" to 1500,
        "gift_2500" to 2500,
        "gift_5000" to 5000,
        "gift_15000" to 15000,
        "gift_40000" to 40000,
        // Optional: single-token product (very small top-up)
        "gift_tokens" to 1,
    )

    // Callback for the current purchase attempt
    private var onResult: ((Boolean) -> Unit)? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        if (billingClient == null) {
            billingClient = BillingClient.newBuilder(appContext)
                .enablePendingPurchases()
                .setListener(this)
                .build()
            try {
                val pm = appContext.packageManager
                val pkg = appContext.packageName
                var installing: String? = null
                var initiating: String? = null
                var originating: String? = null
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    try {
                        val info = pm.getInstallSourceInfo(pkg)
                        installing = info.installingPackageName
                        initiating = info.initiatingPackageName
                        originating = info.originatingPackageName
                    } catch (_: Throwable) {}
                } else {
                    try {
                        @Suppress("DEPRECATION")
                        installing = pm.getInstallerPackageName(pkg)
                    } catch (_: Throwable) {}
                }
                Rollbar.instance().log("Billing init: applicationId=${BuildConfig.APPLICATION_ID} pkg=$pkg installing=$installing initiating=$initiating originating=$originating")
            } catch (_: Throwable) {}
            // Warn if app isn't Play-Store installed; IAP queries will return empty
            try {
                if (!isPlayStoreInstall()) {
                    Toast.makeText(appContext, "Install from Play testing link for purchases", Toast.LENGTH_LONG).show()
                }
            } catch (_: Throwable) {}
            startConnection()
        }
    }

    private fun isPlayStoreInstall(): Boolean {
        val pm = appContext.packageManager
        val pkg = appContext.packageName
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val info = pm.getInstallSourceInfo(pkg)
                val installing = info.installingPackageName
                val initiating = info.initiatingPackageName
                val originating = info.originatingPackageName
                (installing == "com.android.vending") || (initiating == "com.android.vending") || (originating == "com.android.vending")
            } else {
                @Suppress("DEPRECATION") val installer = pm.getInstallerPackageName(pkg)
                installer == "com.android.vending"
            }
        } catch (_: Throwable) { false }
    }

    private fun startConnection(onReady: (() -> Unit)? = null) {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connected = result.responseCode == BillingClient.BillingResponseCode.OK
                try { Rollbar.instance().log("Billing setup finished: code=${result.responseCode}") } catch (_: Throwable) {}
                if (connected) {
                    CoroutineScope(Dispatchers.IO).launch {
                        queryProducts()
                        // If nothing returned, retry a few times with backoff
                        if (productDetails.isEmpty()) {
                            queryProductsRetry()
                        }
                    }
                    onReady?.invoke()
                }
            }

            override fun onBillingServiceDisconnected() {
                connected = false
            }
        })
    }

    private suspend fun ensureConnected(): Boolean = suspendCancellableCoroutine { cont ->
        if (connected) return@suspendCancellableCoroutine cont.resume(true)
        startConnection { cont.resume(true) }
    }

    private suspend fun queryProducts() {
        val list = tokenPacks.keys.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(list)
            .build()
        billingClient?.queryProductDetailsAsync(params) { result, detailsList ->
            try {
                val ids = detailsList.joinToString { it.productId }
                Rollbar.instance().log("queryProductDetails: code=${result.responseCode} count=${detailsList.size} ids=[$ids]")
            } catch (_: Throwable) {}
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails.clear()
                detailsList.forEach { pd -> productDetails[pd.productId] = pd }
            }
        }
    }

    private suspend fun queryProductsRetry(maxAttempts: Int = 3, delayMs: Long = 2000L) {
        repeat(maxAttempts) { attempt ->
            if (productDetails.isNotEmpty()) return
            try { Rollbar.instance().log("queryProductDetails retry attempt=${attempt + 1}") } catch (_: Throwable) {}
            kotlinx.coroutines.delay(delayMs)
            queryProducts()
        }
    }

    private suspend fun queryProductsAwait(): Map<String, ProductDetails> = suspendCancellableCoroutine { cont ->
        val list = tokenPacks.keys.map {
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(it)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(list)
            .build()
        billingClient?.queryProductDetailsAsync(params) { result, detailsList ->
            try {
                val ids = detailsList.joinToString { it.productId }
                Rollbar.instance().log("queryProductDetailsAwait: code=${result.responseCode} count=${detailsList.size} ids=[$ids]")
            } catch (_: Throwable) {}
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails.clear()
                detailsList.forEach { pd -> productDetails[pd.productId] = pd }
                cont.resume(productDetails.toMap())
            } else {
                cont.resume(emptyMap())
            }
        } ?: run { cont.resume(emptyMap()) }
    }

    /**
     * Launch a purchase flow for a token pack. If desiredTokens is provided, picks
     * the smallest pack that is >= desiredTokens, else defaults to the middle pack.
     */
    fun launchPurchase(activity: Activity, desiredTokens: Int? = null, onResult: ((Boolean) -> Unit)? = null) {
        this.onResult = onResult
        val client = billingClient ?: run {
            Toast.makeText(activity, "Billing unavailable", Toast.LENGTH_SHORT).show(); return
        }
        // Ensure connection and try to fetch product details before launching
        CoroutineScope(Dispatchers.Main).launch {
            ensureConnected()
            var details = productDetails.toMap()
            if (details.isEmpty()) {
                details = queryProductsAwait()
            }
            // Choose productId based on desired tokens but fall back to any available
            val chosenProductId = chooseProductId(desiredTokens)
            val pd = details[chosenProductId] ?: details.values.firstOrNull()
            try { Rollbar.instance().log("launchPurchase: chosen=$chosenProductId tokens=${tokenPacks[chosenProductId]} available=${details.keys}") } catch (_: Throwable) {}
            if (pd == null) {
                if (!isPlayStoreInstall()) {
                    Toast.makeText(activity, "Install from Play testing link to purchase", Toast.LENGTH_LONG).show()
                    try { Rollbar.instance().log("launchPurchase: not Play-installed; installer check failed") } catch (_: Throwable) {}
                } else {
                    Toast.makeText(activity, "Products not ready, retry shortly", Toast.LENGTH_SHORT).show()
                    try { Rollbar.instance().log("launchPurchase: empty ProductDetails despite Play install") } catch (_: Throwable) {}
                }
                return@launch
            }
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(pd)
                        .build()
                ))
                .build()
            client.launchBillingFlow(activity, flowParams)
        }
    }

    /**
     * Plan a combination of packs to reach at least the desired number of tokens.
     * Greedy from largest to smallest.
     */
    private fun planPacks(desired: Int): List<Int> {
        val packs = tokenPacks.values.distinct().sortedDescending()
        var remaining = desired
        val result = mutableListOf<Int>()
        for (p in packs) {
            if (p <= 0) continue
            val count = remaining / p
            repeat(count) { result += p }
            remaining %= p
            if (remaining == 0) break
        }
        // If remainder remains and we didn't exactly match, take one smallest pack to cover it
        if (remaining > 0) {
            val smallest = packs.lastOrNull() ?: 0
            if (smallest > 0) result += smallest
        }
        return result
    }

    /**
     * Sequentially launch multiple purchases to meet a large desired amount (e.g., > 40k).
     * Each flow is user-confirmed; aborts if any step fails or is canceled.
     */
    fun launchTopUp(activity: Activity, desiredTokens: Int, onFinished: ((Boolean) -> Unit)? = null) {
        val plan = planPacks(desiredTokens)
        if (plan.isEmpty()) { onFinished?.invoke(false); return }
        try { Rollbar.instance().log("launchTopUp: desired=$desiredTokens plan=$plan") } catch (_: Throwable) {}

        var index = 0
        fun next(successSoFar: Boolean) {
            if (!successSoFar) { onFinished?.invoke(false); return }
            if (index >= plan.size) { onFinished?.invoke(true); return }
            val pack = plan[index++]
            launchPurchase(activity, desiredTokens = pack) { ok -> next(ok) }
        }
        next(true)
    }

    private fun chooseProductId(desired: Int?): String {
        if (desired == null) return tokenPacks.keys.elementAtOrNull(1) ?: tokenPacks.keys.first()
        return tokenPacks.entries.firstOrNull { desired <= it.value }?.key
            ?: tokenPacks.keys.last()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        try { Rollbar.instance().log("onPurchasesUpdated: code=${result.responseCode} count=${purchases?.size ?: 0}") } catch (_: Throwable) {}
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { handlePurchase(it) }
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            onResult?.invoke(false)
        } else {
            onResult?.invoke(false)
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            onResult?.invoke(false); return
        }
        // For consumables, immediately send to backend to verify, then consume
        CoroutineScope(Dispatchers.IO).launch {
            val ok = verifyOnServerAndConsume(purchase)
            CoroutineScope(Dispatchers.Main).launch {
                onResult?.invoke(ok)
            }
        }
    }

    private suspend fun verifyOnServerAndConsume(purchase: Purchase): Boolean {
        // Map productId -> tokens
        val productId = purchase.products.firstOrNull() ?: return false
        val tokens = tokenPacks[productId] ?: 0
        if (tokens <= 0) return false

        // Send to backend for verification and credit
        val body = mapOf(
            "productId" to productId,
            "purchaseToken" to purchase.purchaseToken,
            "orderId" to (try { purchase.orderId } catch (_: Throwable) { null }),
            "packageName" to appContext.packageName,
            "tokens" to tokens,
        ).filterValues { it != null }

        return try {
            val resp = RetrofitClient.functionsApi.processGooglePurchaseTokensRpc(body as Map<String, Any>)
            try { Rollbar.instance().log("verifyOnServer: http=${resp.code()} success=${resp.isSuccessful}") } catch (_: Throwable) {}
            if (resp.isSuccessful) {
                // Consume after server success
                consume(purchase.purchaseToken)
                true
            } else {
                false
            }
        } catch (t: Throwable) {
            try { Rollbar.instance().error(t) } catch (_: Throwable) {}
            false
        }
    }

    private suspend fun consume(purchaseToken: String) = suspendCancellableCoroutine { cont ->
        val client = billingClient ?: return@suspendCancellableCoroutine cont.resume(Unit)
        val params = ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build()
        client.consumeAsync(params) { br, _ ->
            try { Rollbar.instance().log("consumeAsync: code=${br.responseCode}") } catch (_: Throwable) {}
            cont.resume(Unit)
        }
    }
}
