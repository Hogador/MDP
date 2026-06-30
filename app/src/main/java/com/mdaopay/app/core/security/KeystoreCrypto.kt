package com.mdaopay.app.core.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object KeystoreCrypto {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val GCM_IV_LEN = 12
    private const val GCM_TAG_LEN = 128
    private const val AUTH_DURATION_SEC = 300

    private val keystore by lazy { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } }

    fun getOrCreateBiometricKey(alias: String): SecretKey {
        if (keystore.containsAlias(alias)) {
            return keystore.getKey(alias, null) as SecretKey
        }
        return generateKey(alias, requireAuth = true)
    }

    fun getOrCreateKey(alias: String): SecretKey {
        if (keystore.containsAlias(alias)) {
            return keystore.getKey(alias, null) as SecretKey
        }
        return generateKey(alias, requireAuth = false)
    }

    private fun generateKey(alias: String, requireAuth: Boolean): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val specBuilder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)

        if (requireAuth) {
            specBuilder
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                specBuilder.setUserAuthenticationParameters(
                    AUTH_DURATION_SEC,
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
                        or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
            } else {
                @Suppress("DEPRECATION")
                specBuilder.setUserAuthenticationValidityDurationSeconds(AUTH_DURATION_SEC)
            }
        }

        generator.init(specBuilder.build())
        return generator.generateKey()
    }

    fun hasAlias(alias: String): Boolean = keystore.containsAlias(alias)

    fun encrypt(keyAlias: String, plaintext: ByteArray): ByteArray {
        val key = getOrCreateBiometricKey(keyAlias)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plaintext)
        return cipher.iv + ciphertext
    }

    fun decrypt(keyAlias: String, data: ByteArray): ByteArray? {
        return try {
            val key = keystore.getKey(keyAlias, null) as? SecretKey ?: return null
            val cipher = Cipher.getInstance(AES_GCM)
            val iv = data.copyOfRange(0, GCM_IV_LEN)
            val ct = data.copyOfRange(GCM_IV_LEN, data.size)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LEN, iv))
            cipher.doFinal(ct)
        } catch (e: java.security.InvalidKeyException) {
            Log.w("KeystoreCrypto", "decrypt failed for key $keyAlias: auth required or key invalidated")
            null
        } catch (e: javax.crypto.IllegalBlockSizeException) {
            Log.e("KeystoreCrypto", "decrypt failed for key $keyAlias: wrong key / invalidated")
            null
        } catch (e: Exception) {
            Log.e("KeystoreCrypto", "decrypt failed for key $keyAlias: ${e.message}")
            null
        }
    }
}

