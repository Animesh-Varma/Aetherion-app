package dev.animeshvarma.aetherion.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.animeshvarma.aetherion.util.DeviceFingerprint
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class KeystoreManager(
    private val context: Context,
    val deviceFingerprint: DeviceFingerprint = DeviceFingerprint(context)
) {

    private val keystore: KeyStore? = try {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    } catch (_: Exception) {
        null
    }

    private var fallbackKeyPair: KeyPair? = null

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ig_attestation_key"
    }

    init {
        ensureKeyExists(null)
        deviceFingerprint.keyHash = getKeyHash()
    }

    private fun ensureKeyExists(challengeBytes: ByteArray?) {
        try {
            if (keystore != null && (!keystore.containsAlias(KEY_ALIAS) || challengeBytes != null)) {
                generateHardwareKeyPair(challengeBytes)
            }
        } catch (_: Exception) {
            try {
                if (keystore != null && !keystore.containsAlias(KEY_ALIAS)) {
                    generateHardwareKeyPair(null)
                }
            } catch (_: Exception) {
                if (fallbackKeyPair == null) generateSoftwareFallbackKeyPair()
            }
        }
    }

    private fun generateHardwareKeyPair(challengeBytes: ByteArray?) {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE
        )

        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)

        if (challengeBytes != null && challengeBytes.isNotEmpty()) {
            builder.setAttestationChallenge(challengeBytes)
        }

        keyPairGenerator.initialize(builder.build())
        keyPairGenerator.generateKeyPair()
    }

    private fun generateSoftwareFallbackKeyPair() {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        fallbackKeyPair = kpg.generateKeyPair()
    }

    fun getPublicKeyUncompressed(): ByteArray {
        val publicKey: ECPublicKey = try {
            (keystore?.getCertificate(KEY_ALIAS)?.publicKey as? ECPublicKey)
                ?: (fallbackKeyPair?.public as? ECPublicKey)
                ?: run {
                    generateSoftwareFallbackKeyPair()
                    fallbackKeyPair!!.public as ECPublicKey
                }
        } catch (_: Exception) {
            if (fallbackKeyPair == null) generateSoftwareFallbackKeyPair()
            fallbackKeyPair!!.public as ECPublicKey
        }

        val params = publicKey.params
        val curve = params.curve
        val fieldSize = (curve.field.fieldSize + 7) / 8

        val x = publicKey.w.affineX.toByteArrayUnsigned()
        val y = publicKey.w.affineY.toByteArrayUnsigned()

        val xPadded = padLeft(x, fieldSize)
        val yPadded = padLeft(y, fieldSize)

        return ByteArray(1 + 2 * fieldSize).apply {
            this[0] = 0x04
            System.arraycopy(xPadded, 0, this, 1, fieldSize)
            System.arraycopy(yPadded, 0, this, 1 + fieldSize, fieldSize)
        }
    }

    fun getKeyHash(): String {
        val publicKeyBytes = getPublicKeyUncompressed()
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(publicKeyBytes).joinToString("") { "%02x".format(it) }
        deviceFingerprint.keyHash = hash
        return hash
    }

    fun signData(data: ByteArray): ByteArray {
        val privateKey: PrivateKey = try {
            (keystore?.getKey(KEY_ALIAS, null) as? PrivateKey)
                ?: fallbackKeyPair?.private
                ?: run {
                    generateSoftwareFallbackKeyPair()
                    fallbackKeyPair!!.private
                }
        } catch (_: Exception) {
            if (fallbackKeyPair == null) generateSoftwareFallbackKeyPair()
            fallbackKeyPair!!.private
        }

        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }

    fun getAttestationCertificates(): Array<java.security.cert.Certificate> {
        return try {
            keystore?.getCertificateChain(KEY_ALIAS) ?: emptyArray()
        } catch (_: Exception) {
            emptyArray()
        }
    }

    /**
     * Produces genuine x-ig-attest-params matching Instagram Android's wire schema:
     * {"attestation": [{"version": 2, "type": "keystore", "errors": [0], "challenge_nonce": "...", "signed_nonce": "...", "key_hash": "..."}]}
     */
    fun buildAttestParamsHeader(challengeNonce: String): String {
        val nonceToSign = challengeNonce.ifBlank { DeviceFingerprint.randomHex(32) }
        val sigBytes = signData(nonceToSign.toByteArray(Charsets.UTF_8))
        val sigB64 = Base64.encodeToString(sigBytes, Base64.NO_WRAP)

        return buildJsonObject {
            put(
                "attestation",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("version", 2)
                            put("type", "keystore")
                            put("errors", buildJsonArray { add(JsonPrimitive(0)) })
                            put("challenge_nonce", nonceToSign)
                            put("signed_nonce", sigB64)
                            put("key_hash", getKeyHash())
                        }
                    )
                }
            )
        }.toString()
    }

    private fun padLeft(bytes: ByteArray, size: Int): ByteArray {
        if (bytes.size >= size) return bytes.copyOf(size)
        return ByteArray(size).apply {
            System.arraycopy(bytes, 0, this, size - bytes.size, bytes.size)
        }
    }

    private fun BigInteger.toByteArrayUnsigned(): ByteArray {
        val bytes = this.toByteArray()
        if (bytes[0] == 0x00.toByte() && bytes.size > 1) {
            return bytes.copyOfRange(1, bytes.size)
        }
        return bytes
    }

    fun regenerateWithChallenge(challengeNonce: String) {
        if (challengeNonce.isBlank()) return
        runCatching { ensureKeyExists(challengeNonce.toByteArray(Charsets.UTF_8)) }
    }
}