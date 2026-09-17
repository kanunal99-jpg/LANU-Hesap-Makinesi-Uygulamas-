package com.example.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Robust AEAD encryption engine using AES-GCM.
 * This class ensures that all communication payloads are strongly encrypted 
 * with a unique IV (Nonce) per message and verified with an Authentication Tag (GCM).
 */
object AEADEngine {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12

    /**
     * Derives a secure 256-bit SecretKeySpec from a user passphrase.
     */
    fun deriveKey(passphrase: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(passphrase.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Encrypts the plaintext using AES-GCM-128 with a randomly generated 12-byte IV.
     * Throws an exception if encryption fails, preventing plaintext leaks.
     */
    fun encrypt(plainText: String, secretKey: SecretKeySpec): String {
        val cipher = Cipher.getInstance(ALGORITHM)
        val iv = ByteArray(IV_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // Combine IV (12 bytes) + CipherText (contains payload + 16-byte GCM Auth Tag)
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypts the GCM-encrypted Base64 ciphertext.
     * Strictly verifies the Auth Tag; fails and throws on modification, wrong key, or truncation.
     */
    fun decrypt(cipherTextBase64: String, secretKey: SecretKeySpec): String {
        val combined = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
        if (combined.size < IV_LENGTH_BYTES + 16) {
            throw IllegalArgumentException("Ciphertext is too short or corrupted")
        }

        val iv = ByteArray(IV_LENGTH_BYTES)
        val cipherText = ByteArray(combined.size - IV_LENGTH_BYTES)
        System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES)
        System.arraycopy(combined, IV_LENGTH_BYTES, cipherText, 0, cipherText.size)

        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val decryptedBytes = cipher.doFinal(cipherText)

        return String(decryptedBytes, Charsets.UTF_8)
    }
}
