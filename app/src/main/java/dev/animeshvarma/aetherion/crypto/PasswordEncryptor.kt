package dev.animeshvarma.aetherion.crypto

import android.util.Base64
import dev.animeshvarma.aetherion.network.interceptors.LoggingInterceptor
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object PasswordEncryptor {

    private const val VERSION = 4
    private const val SESSION_KEY_SIZE = 32
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    private const val TAG_SIZE = 16

    @Volatile private var keyId: Int? = null
    @Volatile private var pubKeyBase64: String? = null

    fun syncFromHeaders(keyIdHeader: String?, pubKeyHeader: String?) {
        keyIdHeader?.trim()?.toIntOrNull()?.let { keyId = it }
        pubKeyHeader?.trim()?.takeIf { it.isNotEmpty() }?.let { pubKeyBase64 = it }

        // Surface what we actually received, so we can confirm the real format.
        val shown = pubKeyBase64?.take(24) ?: "MISSING"
        val len = pubKeyBase64?.length ?: 0
        LoggingInterceptor.logNetwork(
            "PWD",
            "pub-key header (b64 len=$len): '$shown...' keyId=$keyId"
        )
    }

    fun isAvailable(): Boolean = keyId != null && !pubKeyBase64.isNullOrBlank()
    fun debugKeyId(): String = keyId?.toString() ?: "none"

    fun encryptPassword(password: String, timestamp: String): String {
        val keyId = keyId ?: error("Password encryption key not synced")
        val publicKey = buildRsaPublicKey(pubKeyBase64!!)
            ?: throw IllegalStateException("Could not build RSA public key from IG header")

        // ---- everything below is unchanged & byte-correct ----
        val sr = SecureRandom()
        val sessionKey = ByteArray(SESSION_KEY_SIZE).also { sr.nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { sr.nextBytes(it) }

        val rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        rsa.init(Cipher.ENCRYPT_MODE, publicKey)
        val rsaEncrypted = rsa.doFinal(sessionKey)

        val aes = Cipher.getInstance("AES/GCM/NoPadding")
        aes.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sessionKey, "AES"),
            GCMParameterSpec(TAG_BITS, iv))
        aes.updateAAD(timestamp.toByteArray(Charsets.UTF_8))
        val out = aes.doFinal(password.toByteArray(Charsets.UTF_8))
        val tag = out.copyOfRange(out.size - TAG_SIZE, out.size)
        val ciphertext = out.copyOfRange(0, out.size - TAG_SIZE)

        val rsaLen = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(rsaEncrypted.size.toShort()).array()

        val payload = ByteBuffer.allocate(
            2 + IV_SIZE + 2 + rsaEncrypted.size + TAG_SIZE + ciphertext.size
        )
            .put(0x01.toByte())
            .put(keyId.toByte())
            .put(iv)
            .put(rsaLen)
            .put(rsaEncrypted)
            .put(tag)
            .put(ciphertext)
            .array()

        return "#PWD_INSTAGRAM:$VERSION:$timestamp:" +
                Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    /** Robust loader: PEM text, DER-SPKI, or DER-PKCS#1. Returns null on failure. */
    fun buildRsaPublicKey(b64: String): PublicKey? {
        return try {
            val raw = Base64.decode(b64, Base64.DEFAULT)

            // 1) If it's PEM text, extract the base64 body -> DER.
            val der = if (raw.isPem()) pemBodyToDer(raw) else raw

            // 2) Try full X.509 SubjectPublicKeyInfo.
            try {
                KeyFactory.getInstance("RSA")
                    .generatePublic(X509EncodedKeySpec(der))
            } catch (_: Exception) {
                // 3) Fall back to PKCS#1 RSAPublicKey (modulus + exponent only).
                val (n, e) = parsePkcs1(der)
                KeyFactory.getInstance("RSA")
                    .generatePublic(RSAPublicKeySpec(n, e))
            }
        } catch (e: Exception) {
            LoggingInterceptor.logNetwork(
                "PWD", "buildRsaPublicKey failed: ${e.javaClass.simpleName}: ${e.message}"
            )
            null
        }
    }

    // ------------- PEM helpers -------------
    private fun ByteArray.isPem(): Boolean {
        val s = String(this, 0, minOf(16, size), Charsets.US_ASCII)
        return s.startsWith("-----BEGIN ")
    }

    private fun pemBodyToDer(pemBytes: ByteArray): ByteArray {
        val text = String(pemBytes, Charsets.US_ASCII)
        val body = text
            .replace(Regex("-----BEGIN [A-Z ]+-----"), "")
            .replace(Regex("-----END [A-Z ]+-----"), "")
            .replace(Regex("\\s"), "")
        return Base64.decode(body, Base64.DEFAULT)
    }

    // ------------- Minimal correct DER reader for PKCS#1 -------------
    private class Cur(val b: ByteArray, var p: Int = 0) {
        fun u8(): Int = b[p++].toInt() and 0xff
        fun len(): Int {
            val first = u8()
            if (first and 0x80 == 0) return first
            var l = 0
            repeat(first and 0x7f) { l = (l shl 8) or u8() }
            return l
        }
        fun seqBody(): Cur {
            u8()          // SEQUENCE tag 0x30
            val len = len()
            return Cur(b, p).also { _ -> this.p += len }
        }
        fun int(): BigInteger {
            u8()          // INTEGER tag 0x02
            val len = len()
            val bytes = b.copyOfRange(p, p + len)
            p += len
            val sign = if (bytes[0].toInt() and 0x80 == 0) 1 else 2
            return BigInteger(sign, bytes)
        }
    }

    private fun parsePkcs1(der: ByteArray): Pair<BigInteger, BigInteger> {
        val seq = Cur(der).seqBody()
        val modulus = seq.int()
        val exponent = seq.int()
        return modulus to exponent
    }

    /** Fallback if the qe/sync header is absent or unparsable. */
    fun plaintextPassword(password: String, timestamp: String): String =
        "#PWD_INSTAGRAM:$VERSION:$timestamp:$password"
}