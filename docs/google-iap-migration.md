# Migrating Token Purchases to Google Play In‑App Billing (IAP)

This guide explains how to switch Android token purchases from Flutterwave to Google Play In‑App Billing exclusively. It covers Play Console setup, Android client changes, backend/Edge Function updates, and a pragmatic rollout plan.

---

## Goals
- Replace Flutterwave WebView token top‑ups with Google Play IAP consumables.
- Verify purchases server‑side and credit tokens in Supabase.
- Handle lifecycle (refunds/cancellations) via Google RTDN or periodic rechecks.
- Remove `PaymentWebViewActivity` and related code/strings over time.

## What Changes
- Client: Use `BillingClient` to launch purchases and consume tokens. Stop invoking `PaymentWebViewActivity`.
- Backend: Verify `purchaseToken` with Google Play Developer API before crediting tokens.
- Schema: Add fields to record Google receipts, or generalize existing `token_transactions` table.

---

## 1) Play Console Setup
1. Create consumable in‑app products (INAPP) for token packs (e.g., `tokens_100`, `tokens_500`, `tokens_1000`).
2. Price and activate each product in all target regions.
3. Add license testers (Settings → Developer Account → License Testing).
4. Use Internal Testing or Closed Testing to distribute builds for QA.
5. (Optional) Set up Real‑Time Developer Notifications (RTDN) via Pub/Sub for ongoing purchase state changes.

Notes
- Tokens are consumables; you will consume each purchase after crediting tokens.
- For subscriptions (recurring access), use Google Play SUBS; this doc focuses on consumables.

---

## 2) Android Client Changes

Dependencies and permissions
- `com.android.billingclient:billing-ktx` is already added in `app/build.gradle.kts`.
- `com.android.vending.BILLING` is present in `AndroidManifest.xml`.

Create a Billing helper
- Add a `BillingManager` (e.g., `club.gifters.giftersclub.payments.BillingManager`) that encapsulates:
  - Connecting `BillingClient` and querying product details.
  - Launching `BillingFlowParams` for a selected token pack.
  - Handling `PurchasesUpdatedListener` events.
  - Verifying the purchase server‑side, then consuming the purchase token.

Client flow summary
1. User taps “Buy Tokens” with a selected pack (or needed top‑up amount → map to SKU).
2. Build and launch billing flow for the corresponding productId.
3. On success, receive `Purchase` with `purchaseToken`, `orderId`, `productIds`.
4. Send payload to backend for verification and credit:
   - `userId`, `productId`, `purchaseToken`, `orderId`, `packageName`, `tokens`.
5. If backend verification succeeds, call `consumeAsync(purchaseToken)` and update local UI.

Replace WebView entry points
- Replace all usages of `PaymentWebViewActivity.start(...)` with a call to your new `BillingManager.launchPurchase(productId)` (or a small wrapper that maps KES/needed tokens → productId):
  - `GiftersClub/app/src/main/java/club/gifters/giftersclub/gifts/AccountFragment.kt:1`
  - `GiftersClub/app/src/main/java/club/gifters/giftersclub/gifts/GiftFragment.kt:1`
  - `GiftersClub/app/src/main/java/club/gifters/giftersclub/gifts/PostsFragment.kt:260`
  - `GiftersClub/app/src/main/java/club/gifters/giftersclub/gifts/WishlistDetailFragment.kt:360`
  - `GiftersClub/app/src/main/java/club/gifters/giftersclub/live/LiveStreamActivity.kt:2140`

Product → tokens mapping
- Define a single source of truth that maps `productId` → `tokenAmount`.
- Options:
  - Hardcode in app (quick start); or
  - Fetch from Supabase (dynamic, avoids app updates to change prices/packs).

Error handling
- Always acknowledge/consume purchases after successful server verification.
- If server verification fails, do not consume; show a retry and keep purchase pending in the queue.

---

## 3) Backend/Edge Function Changes

Use existing Functions base URL
- The app already calls Supabase Edge Functions via `RetrofitClient.functionsApi`.
- Either reuse `purchase-tokens` endpoint to accept Google payloads, or add a dedicated `google-purchase-tokens` endpoint.

Expected request body (example)
```
{
  "userId": "<supabase_user_id>",
  "productId": "tokens_500",
  "purchaseToken": "<google_purchase_token>",
  "orderId": "GPA.XXXX-XXXX-XXXX-XXXXX",
  "packageName": "club.gifters.giftersclub",
  "tokens": 500
}
```

Server actions
1. Verify purchase with Google Play Developer API:
   - For INAPP: `purchases.products.get(packageName, productId, purchaseToken)`
   - Ensure `purchaseState` indicates a completed purchase and not consumed/refunded.
2. Ensure idempotency:
   - Check if `purchaseToken` (or `orderId`) was already processed.
   - If processed, return success without double‑crediting.
3. Credit tokens to the user and record a transaction row.
4. Mark the row as verified and store Google metadata.
5. Return success to client only after DB commit.
6. Client then calls `consumeAsync` to finalize the Play purchase.

DB schema updates (Supabase)
- Current model tracks Flutterwave fields:
  - `flutterwave_transaction_id`, `flutterwave_transaction_status`.
- Add Google fields (suggested):
  - `google_purchase_token` (unique), `google_order_id`, `google_product_id`, `google_package_name`, `google_purchase_time`, `google_payload` (JSON), `status` (e.g., verified, refunded).
- Alternatively, generalize columns to provider‑agnostic fields:
  - `provider` (flutterwave|google), `provider_tx_id`, `provider_status`, `provider_payload` JSON.

Lifecycle handling
- RTDN (Real‑Time Developer Notifications) via Pub/Sub:
  - Receive messages for refunds/chargebacks and adjust balances accordingly.
- (If skipping RTDN initially) schedule periodic verification for recent purchases.

Security
- Never trust the client. Only credit on server after Google API verification.
- Require Supabase JWT auth; map `userId` from JWT subject.
- Enforce idempotency to prevent replay of `purchaseToken`.

---

## 4) Incremental Rollout Plan
1. Ship Billing flow under a feature flag; keep Flutterwave as fallback for one build.
2. Verify end‑to‑end with license testers (success, cancel) and with Internal Testing track.
3. Monitor error reporting (Rollbar) for billing flow events.
4. Remove Flutterwave WebView and strings; delete `PaymentWebViewActivity` in a subsequent release.
5. Clean up schema fields after you confirm no active clients depend on them.

---

## 5) Testing Checklist
- Play Console
  - Products are ACTIVE and priced in regions.
  - Testers added; app installed via testing track.
- Client
  - Purchase success → server credits tokens → client consumes purchase.
  - Purchase canceled → no credit.
  - Network failure after success → app retries verification; idempotency OK.
  - Multiple quick taps → single credit only.
- Backend
  - Rejects invalid/expired tokens.
  - Prevents double‑credit (idempotency on `purchaseToken`).
  - Logs enough details for debugging.

---

## 6) Code Touchpoints (Android)
- Remove or replace WebView payment entry points:
  - `GiftersClub/app/src/main/java/club/gifters/giftersclub/payments/PaymentWebViewActivity.kt:1`
  - `GiftersClub/app/src/main/java/club/gifters/giftersclub/gifts/AccountFragment.kt:1`
  - `GiftersClub/app/src/main/java/club/gifters/giftersclub/gifts/GiftFragment.kt:1`
  - `GiftersClub/app/src/main/java/club/gifters/giftersclub/gifts/PostsFragment.kt:260`
  - `GiftersClub/app/src/main/java/club/gifters/giftersclub/gifts/WishlistDetailFragment.kt:360`
  - `GiftersClub/app/src/main/java/club/gifters/giftersclub/live/LiveStreamActivity.kt:2140`
- Add billing integration files (suggested):
  - `GiftersClub/app/src/main/java/club/gifters/giftersclub/payments/BillingManager.kt`
  - `GiftersClub/app/src/main/java/club/gifters/giftersclub/payments/BillingExtensions.kt` (optional helpers)

---

## 7) Minimal Client Pseudocode

Initialize
```
val billingClient = BillingClient.newBuilder(context)
  .enablePendingPurchases()
  .setListener(purchasesUpdatedListener)
  .build()

billingClient.startConnection(object: BillingClientStateListener { ... })
```

Query products
```
val products = listOf(
  QueryProductDetailsParams.Product.newBuilder()
    .setProductId("tokens_500").setProductType(BillingClient.ProductType.INAPP).build(),
  // ...
)
val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()
billingClient.queryProductDetailsAsync(params) { result, details -> /* cache details */ }
```

Launch purchase
```
val productDetails = cache["tokens_500"]
val offer = productDetails.oneTimePurchaseOfferDetails
val flowParams = BillingFlowParams.newBuilder()
  .setProductDetailsParamsList(listOf(
    BillingFlowParams.ProductDetailsParams.newBuilder()
      .setProductDetails(productDetails)
      .build()
  ))
  .build()
billingClient.launchBillingFlow(activity, flowParams)
```

Handle purchase
```
override fun onPurchasesUpdated(result, purchases) {
  if (result.responseCode == OK && !purchases.isNullOrEmpty()) {
    for (p in purchases) if (p.purchaseState == PURCHASED) {
      // Send to backend
      functions.purchaseTokens(
        userId, p.products.first(), p.purchaseToken, p.orderId, packageName, tokenAmount
      )
      // On success, consume
      val params = ConsumeParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
      billingClient.consumeAsync(params) { consumeResult, _ -> /* update UI */ }
    }
  }
}
```

---

## 8) Cleanup (Post‑Migration)
- Delete `PaymentWebViewActivity` and Flutterwave keys in `SupabaseConfig`.
- Remove Flutterwave transaction fields from client model if no longer used.
- Update Play release notes and support docs accordingly.

---

## FAQ
- Do we still need Edge Functions? Yes. Use them to verify `purchaseToken` with Google and securely credit tokens.
- Can we reflect Android purchases on web? Yes. Store credits in Supabase; web reads the same balances.
- How do we handle refunds? Use RTDN to detect and reverse credits, or add periodic verification for recent transactions.

---

Owner: Android + Backend
Status: Draft ready for implementation

---

## Must‑Haves Addendum (Close the Gaps)

These additions make the migration production‑ready with minimal surprises.

### Play Developer API Access (Server)
- Create a Google Cloud service account and grant it Play Developer API access to your app.
- Store the service account JSON in your secrets (e.g., `GOOGLE_PLAY_SA_JSON`).
- Verify purchases server‑side; do not credit on client‑only receipts.

Supabase Edge Function (Deno) verification sketch
```
// deno.json deploy target: supabase edge functions
// Env: SUPABASE_URL, SUPABASE_SERVICE_KEY, GOOGLE_PLAY_SA_JSON
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

interface VerifyReq {
  userId: string;
  productId: string;
  purchaseToken: string;
  packageName: string;
  tokens: number;
}

async function getGoogleAccessToken(sa: any): Promise<string> {
  // OAuth2 JWT flow for service accounts
  const now = Math.floor(Date.now() / 1000);
  const claim = {
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  };
  const encoder = new TextEncoder();
  const header = btoa(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const payload = btoa(JSON.stringify(claim));
  const input = `${header}.${payload}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    ((): Uint8Array => {
      // Convert PEM to ArrayBuffer
      const pem = sa.private_key.replace(/-----[^-]+-----/g, "").replace(/\n/g, "");
      const raw = atob(pem);
      const buf = new Uint8Array(raw.length);
      for (let i = 0; i < raw.length; i++) buf[i] = raw.charCodeAt(i);
      return buf;
    })(),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, encoder.encode(input));
  const signature = btoa(String.fromCharCode(...new Uint8Array(sig)));
  const jwt = `${input}.${signature}`;

  const tokenResp = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: jwt }),
  }).then(r => r.json());
  return tokenResp.access_token as string;
}

export default async function handler(req: Request): Promise<Response> {
  if (req.method !== "POST") return new Response(null, { status: 405 });
  const body = (await req.json()) as VerifyReq;

  const sa = JSON.parse(Deno.env.get("GOOGLE_PLAY_SA_JSON")!);
  const accessToken = await getGoogleAccessToken(sa);
  const url = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${body.packageName}/purchases/products/${body.productId}/tokens/${body.purchaseToken}`;
  const verify = await fetch(url, { headers: { Authorization: `Bearer ${accessToken}` } }).then(r => r.json());
  // purchaseState: 0=Purchased, 1=Canceled, 2=Pending
  if (verify.purchaseState !== 0) return new Response(JSON.stringify({ ok: false, reason: "not completed" }), { status: 400 });

  const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_KEY")!);
  // idempotency guard
  const existing = await supabase.from("token_transactions").select("id").eq("google_purchase_token", body.purchaseToken).maybeSingle();
  if (existing.data) return new Response(JSON.stringify({ ok: true, alreadyProcessed: true }));

  const credit = await supabase.rpc("credit_tokens_atomic", {
    p_user_id: body.userId,
    p_amount: body.tokens,
    p_meta: {
      provider: "google",
      google_purchase_token: body.purchaseToken,
      google_order_id: verify.orderId ?? null,
      google_product_id: body.productId,
      google_package_name: body.packageName,
      google_payload: verify,
    },
  });
  if (credit.error) return new Response(JSON.stringify({ ok: false, error: credit.error.message }), { status: 500 });
  return new Response(JSON.stringify({ ok: true }));
}
```

Node alternative (if not using Supabase Edge runtime)
```
import { google } from "googleapis";
// ...use google.auth.GoogleAuth + androidpublisher.purchases.products.get(...)
```

### Idempotency Guard (DB)
Prevent duplicate credits from retries or callbacks.
```
-- prevents duplicate token credits from same Google purchase
alter table token_transactions
add column if not exists provider text,
add column if not exists google_purchase_token text,
add column if not exists google_order_id text,
add column if not exists google_product_id text,
add column if not exists google_package_name text,
add column if not exists google_payload jsonb,
add column if not exists status text default 'verified';

create unique index if not exists uq_token_tx_google_purchase_token
on token_transactions (google_purchase_token)
where google_purchase_token is not null;
```

Atomic credit helper (Supabase function)
```
-- Atomic token credit + transaction row insert
create or replace function credit_tokens_atomic(
  p_user_id uuid,
  p_amount int,
  p_meta jsonb
) returns void language plpgsql as $$
begin
  update public.profiles
    set token_balance = coalesce(token_balance, 0) + p_amount
    where id = p_user_id;

  insert into token_transactions(
    user_id, transaction_type, tokens,
    provider, google_purchase_token, google_order_id, google_product_id, google_package_name, google_payload, status
  ) values (
    p_user_id, 'purchase', p_amount,
    p_meta->>'provider', p_meta->>'google_purchase_token', p_meta->>'google_order_id', p_meta->>'google_product_id', p_meta->>'google_package_name', p_meta, 'verified'
  );
end$$;
```

### Pending / Deferred Purchases
- On `onPurchasesUpdated`, handle:
  - PURCHASED → verify → consume on success.
  - PENDING → show "Pending" UI; poll `queryPurchasesAsync()` on app resume to reconcile.

Resume reconcile snippet
```
fun reconcileUnconsumed() {
    val params = QueryPurchasesParams.newBuilder()
        .setProductType(BillingClient.ProductType.INAPP)
        .build()
    billingClient.queryPurchasesAsync(params) { _, purchases ->
        purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { p ->
            verifyThenConsume(p) // server sees processed → idempotent OK; then consume
        }
    }
}
```

### Obfuscation IDs (Fraud Linkage)
Pass hashed identifiers when launching billing.
```
val flowParams = BillingFlowParams.newBuilder()
  .setProductDetailsParamsList(listOf(
    BillingFlowParams.ProductDetailsParams.newBuilder()
      .setProductDetails(productDetails)
      .build()
  ))
  .setObfuscatedAccountId(userIdHash)
  .setObfuscatedProfileId(deviceScopedId) // optional
  .build()
```

### Consume After Server OK
```
fun consume(purchaseToken: String, onDone: () -> Unit) {
    val params = ConsumeParams.newBuilder().setPurchaseToken(purchaseToken).build()
    billingClient.consumeAsync(params) { _, _ -> onDone() }
}
```

### SKU → Tokens Single Source of Truth
- Centralize mapping in one place (config table in Supabase or a versioned JSON from an Edge Function).
- Client uses this mapping to choose `productId`; server uses it to validate `tokens` credited.

### Refunds/Revokes Lifecycle
- Prefer RTDN via Pub/Sub and an HTTPS handler to mark transactions refunded and deduct tokens (or insert negative adjustments).
- If deferring RTDN, schedule periodic re‑verification of recent purchases.

### QA / Release Hygiene
- Ensure testers install via Play testing track (not side‑loaded) or Billing fails.
- Feature flag rollout (e.g., hide Flutterwave UI while keeping server endpoints accessible for admin/manual credits).
- Instrument analytics: `iap_purchase_started/succeeded/failed/consumed` with productId and latency.

---

## 9) SKU → Tokens Mapping (Packs + Per‑Gift)

This locks down how amounts are decided on the server so clients never dictate credits.

Rules
- Token packs: any `productId` that matches `gift_<number>` credits exactly `<number>` tokens (e.g., `gift_2500` → 2500 tokens).
- Generic pack: `gift_tokens` credits a default pack size (e.g., 100) from config.
- Per‑gift SKUs (e.g., `gift_rose`): look up `public.gifts.sku = productId` and credit `public.gifts.tokens`.

Server helper (sync logic)
```
// mapSkuToTokens.ts
export function mapSkuToTokensSync(productId: string): number | null {
  // 1) Token packs like gift_50, gift_1000, gift_15000, gift_40000
  const m = /^gift_(\d+)$/.exec(productId);
  if (m) return parseInt(m[1], 10);

  // 2) Generic token SKU (fallback to config)
  if (productId === "gift_tokens") return 100; // default; read from app_config in prod

  // 3) Per-gift SKU: resolve via DB (return null to signal lookup)
  if (productId.startsWith("gift_")) return null;

  return null; // unknown
}
```

Edge Function: integrate mapping
```
let tokensToCredit = mapSkuToTokensSync(body.productId);
if (tokensToCredit === null) {
  // per-gift SKU: exact match by gifts.sku
  const { data: giftRow, error } = await supabase
    .from('gifts')
    .select('id,tokens')
    .eq('sku', body.productId)
    .maybeSingle();
  if (error || !giftRow) throw new Error('Unknown per-gift SKU: ' + body.productId);
  tokensToCredit = giftRow.tokens;
}
```

Recommended Android IAP IDs list (token packs + sample per‑gift)
```
private val IAP_IDS = listOf(
  // Token packs
  "gift_50","gift_100","gift_250","gift_500","gift_1000",
  "gift_1500","gift_2500","gift_5000","gift_15000","gift_40000",
  "gift_tokens", // optional generic
  // Per-gift SKUs (add as you enable)
  "gift_rose","gift_coffee","gift_galaxy"
)
```

---

## 10) Gifts.sku Column + Backfill

Add an exact `sku` on `public.gifts` to match Play product IDs for per‑gift SKUs.

DDL
```
alter table public.gifts add column if not exists sku text unique;
create unique index if not exists uq_gifts_sku on public.gifts(sku);
```

Backfill (precise, by ID)
- Replace the example UUIDs with your actual gift IDs if they differ.
- Keeps values stable and exact across environments.
```
begin;

-- High-tier / Cosmic (examples; replace IDs to match your DB)
update public.gifts set sku = 'gift_black_hole', updated_at = now() where id = '00000000-0000-0000-0000-000000000001';
update public.gifts set sku = 'gift_galaxy',     updated_at = now() where id = '00000000-0000-0000-0000-000000000002';
update public.gifts set sku = 'gift_unicorn',    updated_at = now() where id = '00000000-0000-0000-0000-000000000003';
-- … add the rest (rose, coffee, bouquet, diamond, etc.)

-- Optional safety: auto-generate any remaining NULL skus from name
-- Requires unaccent extension for best results; skip if not available.
-- create extension if not exists unaccent;
update public.gifts
   set sku = 'gift_' || regexp_replace(lower(coalesce(unaccent(name), name)), '[^a-z0-9]+', '_', 'g'),
       updated_at = now()
 where sku is null;

commit;
```

Sanity checks (duplicates / nulls)
```
-- Duplicates (should return 0 rows)
select sku, count(*) from public.gifts where sku is not null group by sku having count(*) > 1;

-- Missing skus (should return 0 rows after backfill)
select id, name from public.gifts where sku is null;
```

---

## 11) Large Gifts & Country Price Caps (e.g., KRW)

Background
- Some markets have upper price caps; very high KES equivalents can be rejected in those locales, blocking a SKU from activation.

Strategy
- Keep per‑gift SKUs only for gifts within global caps (typically ≤ 40k tokens).
- For larger gifts, rely on token‑pack top‑ups instead of a single per‑gift SKU.
- Use a smart pack picker to propose the minimal number of packs to reach the needed balance.

Smart pack picker (Android)
```
// Available packs (descending)
private val PACKS = listOf(40000, 15000, 5000, 2500, 1500, 1000, 500, 250, 100, 50)

fun suggestPacks(needed: Int): List<Int> {
    var rem = needed
    val picks = mutableListOf<Int>()
    for (p in PACKS) {
        while (rem >= p) {
            picks += p
            rem -= p
        }
    }
    if (rem > 0) {
        // cover small remainder with smallest pack
        picks += PACKS.last()
    }
    return picks
}

suspend fun topUpAndSendGift(giftTokens: Int, currentBalance: Int) {
    var balance = currentBalance
    val needed = (giftTokens - balance).coerceAtLeast(0)
    if (needed == 0) { sendGiftNow(); return }
    val plan = suggestPacks(needed)
    for (pack in plan) {
        val productId = "gift_${'$'}pack"
        val ok = purchasePack(productId) // verify on server → consume on success
        if (!ok) return // user canceled / failed; keep credited packs
        balance += pack
    }
    sendGiftNow()
}
```

Play Console tips
- Set local prices for outlier markets; if still blocked, omit per‑gift SKUs for those large gifts and rely on packs.
- UX copy when splitting into packs: “We’ll top you up with 40,000 + 15,000 tokens, then send the gift.”

QA must cover
- High‑value gift path (stacked packs), cancel mid‑sequence, pending purchases, and resume reconciliation.
