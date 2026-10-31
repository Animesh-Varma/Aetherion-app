package dev.animeshvarma.aetherion.crypto

import android.util.Base64
import dev.animeshvarma.aetherion.network.interceptors.LoggingInterceptor
import dev.animeshvarma.aetherion.util.DeviceFingerprint
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

/**
 * #PWD_INSTAGRAM:4
 * payload = 0x01 | keyId(1B) | iv(12B) | rsaLen(2B LE) | rsa(sessionKey) | gcmTag(16B) | aesGcmCt
 * RSA/ECB/PKCS1Padding over the 32-byte session key with the pubkey from
 * `ig-set-password-encryption-pub-key` (delivered by GET qe/sync).
 */
object PasswordEncryptor {

    private const val SESSION_KEY_SIZE = 32
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    private const val TAG_SIZE = 16

    @Volatile private var keyId: Int? = null
    @Volatile private var pubKeyBase64: String? = null

    fun restoreFromDeviceFingerprint(fp: DeviceFingerprint) {
        if (keyId == null && fp.pwdKeyId.isNotBlank()) keyId = fp.pwdKeyId.toIntOrNull()
        if (pubKeyBase64.isNullOrBlank() && fp.pwdPubKey.isNotBlank()) pubKeyBase64 = fp.pwdPubKey
    }

    fun syncFromHeaders(keyIdHeader: String?, pubKeyHeader: String?, fp: DeviceFingerprint?) {
        keyIdHeader?.trim()?.toIntOrNull()?.let { keyId = it; fp?.pwdKeyId = it.toString() }
        pubKeyHeader?.trim()?.takeIf { it.isNotEmpty() }?.let { pubKeyBase64 = it; fp?.pwdPubKey = it }
        if (!keyIdHeader.isNullOrBlank() || !pubKeyHeader.isNullOrBlank()) {
            LoggingInterceptor.logNetwork(
                "PWD", "RSA key synced: keyId=$keyId, pubKeyLen=${pubKeyBase64?.length ?: 0}"
            )
        }
    }

    fun isAvailable(): Boolean = keyId != null && !pubKeyBase64.isNullOrBlank()

    fun encryptPassword(password: String, timestamp: String): String {
        val currentKeyId = keyId ?: error("pwd key id not synced")
        val publicKey = buildRsaPublicKey(pubKeyBase64!!)
            ?: error("cannot parse ig-set-password-encryption-pub-key")

        val sr = SecureRandom()
        val sessionKey = ByteArray(SESSION_KEY_SIZE).also { sr.nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { sr.nextBytes(it) }

        val rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        rsa.init(Cipher.ENCRYPT_MODE, publicKey)
        val rsaEncrypted = rsa.doFinal(sessionKey)

        val aes = Cipher.getInstance("AES/GCM/NoPadding")
        aes.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sessionKey, "AES"), GCMParameterSpec(TAG_BITS, iv))
        aes.updateAAD(timestamp.toByteArray(Charsets.UTF_8))
        val gcm = aes.doFinal(password.toByteArray(Charsets.UTF_8))
        val tag = gcm.copyOfRange(gcm.size - TAG_SIZE, gcm.size)
        val cipherText = gcm.copyOfRange(0, gcm.size - TAG_SIZE)

        val rsaLen = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(rsaEncrypted.size.toShort()).array()

        val payload = ByteBuffer.allocate(
            2 + IV_SIZE + 2 + rsaEncrypted.size + TAG_SIZE + cipherText.size
        )
            .put(0x01.toByte())
            .put((currentKeyId and 0xFF).toByte())
            .put(iv)
            .put(rsaLen)
            .put(rsaEncrypted)
            .put(tag)
            .put(cipherText)
            .array()

        return "#PWD_INSTAGRAM:4:$timestamp:" + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    /** Fallback when the RSA key could not be fetched. Version 0 = plaintext. */
    fun plaintextPassword(password: String, timestamp: String): String =
        "#PWD_INSTAGRAM:0:$timestamp:$password"

    fun buildRsaPublicKey(b64: String): PublicKey? = try {
        val decoded = Base64.decode(b64.trim(), Base64.DEFAULT)
        val asText = runCatching { String(decoded, Charsets.US_ASCII) }.getOrNull()
        val der = if (asText != null && asText.contains("-----BEGIN")) pemToDer(asText) else decoded

        runCatching {
            KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
        }.getOrElse {
            val (modulus, exponent) = parsePkcs1(der)
            KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
        }
    } catch (e: Exception) {
        LoggingInterceptor.logNetwork("PWD", "buildRsaPublicKey failed: ${e.message}")
        null
    }

    private fun pemToDer(pem: String): ByteArray {
        val body = pem
            .replace(Regex("-----BEGIN [A-Z ]+-----"), "")
            .replace(Regex("-----END [A-Z ]+-----"), "")
            .replace("\\n", "")
            .replace(Regex("\\s"), "")
        return Base64.decode(body, Base64.DEFAULT)
    }

    private class Der(private val b: ByteArray) {
        var pos = 0
        fun tag(): Int = b[pos++].toInt() and 0xFF
        fun length(): Int {
            val f = b[pos++].toInt() and 0xFF
            if (f and 0x80 == 0) return f
            var v = 0
            repeat(f and 0x7F) { v = (v shl 8) or (b[pos++].toInt() and 0xFF) }
            return v
        }
        fun integer(): BigInteger {
            require(tag() == 0x02) { "expected INTEGER" }
            val len = length()
            val bytes = b.copyOfRange(pos, pos + len)
            pos += len
            return BigInteger(1, bytes)
        }
    }

    private fun parsePkcs1(der: ByteArray): Pair<BigInteger, BigInteger> {
        val r = Der(der)
        require(r.tag() == 0x30) { "expected SEQUENCE" }
        r.length()
        return r.integer() to r.integer()
    }
}