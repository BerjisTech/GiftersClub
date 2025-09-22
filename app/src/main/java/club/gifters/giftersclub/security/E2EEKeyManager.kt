package club.gifters.giftersclub.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.security.SecureRandom
import android.util.Base64

object E2EEKeyManager {
    private const val PREFS_NAME = "e2ee_prefs"
    private const val KEY_PRIV = "x25519_priv"
    private const val SALT = "giftersclub-chat"
    private val rng = SecureRandom()

    private fun prefs(ctx: Context) : EncryptedSharedPreferences {
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as EncryptedSharedPreferences
    }

    // Export/import helpers to move key across app flavors (dev/prod) on the same device
    fun exportKey(ctx: Context): String? {
        val p = prefs(ctx)
        val priv = p.getString(KEY_PRIV, null) ?: return null
        val pub = getOrCreatePublicKeyBase64(ctx)
        val json = "{" + "\"v\":1,\"pub\":\"$pub\",\"priv\":\"$priv\"}"
        return Base64.encodeToString(json.toByteArray(), Base64.NO_WRAP)
    }
    fun importKey(ctx: Context, blobBase64: String): Boolean {
        return try {
            val json = String(Base64.decode(blobBase64, Base64.NO_WRAP))
            val priv = Regex("\"priv\":\"([^\"]+)\"").find(json)?.groupValues?.getOrNull(1) ?: return false
            val p = prefs(ctx)
            p.edit().putString(KEY_PRIV, priv).apply()
            true
        } catch (_: Exception) { false }
    }

    fun getOrCreatePublicKeyBase64(ctx: Context): String {
        val pr = getOrCreatePrivate(ctx)
        val pubBytes = ByteArray(32)
        pr.generatePublicKey().encode(pubBytes, 0)
        return Base64.encodeToString(pubBytes, Base64.NO_WRAP)
    }

    private fun getOrCreatePrivate(ctx: Context): X25519PrivateKeyParameters {
        val p = prefs(ctx)
        val existing = p.getString(KEY_PRIV, null)
        if (existing != null) {
            val bytes = Base64.decode(existing, Base64.NO_WRAP)
            return X25519PrivateKeyParameters(bytes, 0)
        }
        val seed = ByteArray(32)
        rng.nextBytes(seed)
        val priv = X25519PrivateKeyParameters(seed, 0)
        p.edit().putString(KEY_PRIV, Base64.encodeToString(seed, Base64.NO_WRAP)).apply()
        return priv
    }

    private fun deriveKey(sharedSecret: ByteArray): ByteArray {
        val hkdf = HKDFBytesGenerator(org.bouncycastle.crypto.digests.SHA256Digest())
        hkdf.init(HKDFParameters(sharedSecret, SALT.toByteArray(), null))
        val out = ByteArray(32)
        hkdf.generateBytes(out, 0, out.size)
        return out
    }

    private fun sharedSecret(ctx: Context, peerPubB64: String): ByteArray {
        val priv = getOrCreatePrivate(ctx)
        val peerBytes = Base64.decode(peerPubB64, Base64.NO_WRAP)
        val peer = X25519PublicKeyParameters(peerBytes, 0)
        val agree = X25519Agreement()
        agree.init(priv)
        val z = ByteArray(32)
        agree.calculateAgreement(peer, z, 0)
        return z
    }

    fun encrypt(ctx: Context, peerPubB64: String, plaintext: String): String? {
        return try {
            val z = sharedSecret(ctx, peerPubB64)
            val keyBytes = deriveKey(z)
            val nonce = ByteArray(12).also { rng.nextBytes(it) }
            val aead = ChaCha20Poly1305()
            aead.init(true, AEADParameters(KeyParameter(keyBytes), 128, nonce, null))
            val pt = plaintext.toByteArray()
            val out = ByteArray(pt.size + 16)
            val len = aead.processBytes(pt, 0, pt.size, out, 0)
            aead.doFinal(out, len)
            // combined: nonce + ciphertext+tag
            val combined = ByteArray(12 + out.size)
            System.arraycopy(nonce, 0, combined, 0, 12)
            System.arraycopy(out, 0, combined, 12, out.size)
            val ct = Base64.encodeToString(combined, Base64.NO_WRAP)
            val json = "{" +
                    "\"v\":1,\"alg\":\"chacha20poly1305\",\"ct\":\"$ct\"}"
            json
        } catch (_: Exception) { null }
    }

    fun decrypt(ctx: Context, peerPubB64: String, blob: String): String? {
        return try {
            val ctB64 = Regex("\"ct\":\"([^\"]+)\"").find(blob)?.groupValues?.getOrNull(1)
                ?: return null
            val combined = Base64.decode(ctB64, Base64.NO_WRAP)
            if (combined.size < 12 + 16) return null
            val nonce = combined.copyOfRange(0, 12)
            val ct = combined.copyOfRange(12, combined.size)
            val z = sharedSecret(ctx, peerPubB64)
            val keyBytes = deriveKey(z)
            val aead = ChaCha20Poly1305()
            aead.init(false, AEADParameters(KeyParameter(keyBytes), 128, nonce, null))
            val out = ByteArray(ct.size - 16)
            val len = aead.processBytes(ct, 0, ct.size, out, 0)
            val finalLen = aead.doFinal(out, len)
            String(out, 0, finalLen)
        } catch (_: Exception) { null }
    }
}
