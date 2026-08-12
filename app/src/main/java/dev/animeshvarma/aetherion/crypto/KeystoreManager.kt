package dev.animeshvarma.aetherion.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.animeshvarma.aetherion.util.DeviceFingerprint
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

class KeystoreManager(private val context: Context) {

    private val keystore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val deviceFingerprint = DeviceFingerprint(context)

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ig_attestation_key"
    }

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        if (!keystore.containsAlias(KEY_ALIAS)) {
            generateKeyPair()
        }
    }

    private fun generateKeyPair() {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE
        )

        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            // EC keys do not use padding constants like RSA, so we omit setSignaturePaddings
            .setAttestationChallenge("instagram_attestation".toByteArray())
            .setUserAuthenticationRequired(false)
            .build()

        keyPairGenerator.initialize(spec)
        keyPairGenerator.generateKeyPair()
    }

    fun getPublicKeyUncompressed(): ByteArray {
        val cert = keystore.getCertificate(KEY_ALIAS)
        val publicKey = cert.publicKey as ECPublicKey
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
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(publicKeyBytes).joinToString("") { "%02x".format(it) }
    }

    fun signData(data: ByteArray): ByteArray {
        val privateKey = keystore.getKey(KEY_ALIAS, null) as java.security.PrivateKey
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }

    fun getAttestationCertificates(): Array<java.security.cert.Certificate> {
        return keystore.getCertificateChain(KEY_ALIAS) ?: emptyArray()
    }

    private fun padLeft(bytes: ByteArray, size: Int): ByteArray {
        if (bytes.size >= size) return bytes.copyOf(size)
        return ByteArray(size).apply {
            System.arraycopy(bytes, 0, this, size - bytes.size, bytes.size)
        }
    }

    // Helper extension to strip the sign bit from Java's BigInteger
    private fun BigInteger.toByteArrayUnsigned(): ByteArray {
        val bytes = this.toByteArray()
        if (bytes[0] == 0x00.toByte() && bytes.size > 1) {
            return bytes.copyOfRange(1, bytes.size)
        }
        return bytes
    }
}