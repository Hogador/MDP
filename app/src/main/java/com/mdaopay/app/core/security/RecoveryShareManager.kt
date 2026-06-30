package com.mdaopay.app.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecoveryShareManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy { buildSharePrefs() }

    fun hasShare1(): Boolean = getShare1File().exists()
    fun hasShare2(): Boolean = prefs.contains(KEY_SHARE_2)
    fun hasShare3(): Boolean = prefs.contains(KEY_SHARE_3)
    fun hasShare4(): Boolean = prefs.contains(KEY_SHARE_4)

    fun saveShare1(share: Share): Boolean {
        return try {
            val key = KeystoreCrypto.getOrCreateKey(SHARE1_KEY_ALIAS)
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val plaintext = share.toByteArray()
            val ciphertext = cipher.doFinal(plaintext)
            val iv = cipher.iv
            val file = getShare1File()
            file.parentFile?.mkdirs()
            file.writeBytes(iv + ciphertext)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getShare1(): Share? {
        return try {
            val file = getShare1File()
            if (!file.exists()) return null
            val data = file.readBytes()
            val iv = data.copyOfRange(0, GCM_IV_LEN)
            val ciphertext = data.copyOfRange(GCM_IV_LEN, data.size)
            val key = KeystoreCrypto.getOrCreateKey(SHARE1_KEY_ALIAS)
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LEN, iv))
            val plaintext = cipher.doFinal(ciphertext)
            Share.fromByteArray(plaintext)
        } catch (e: AEADBadTagException) {
            Log.e("RecoveryShareManager", "Share1 tampered: AEAD tag mismatch")
            null
        } catch (e: Exception) {
            Log.w("RecoveryShareManager", "getShare1 failed: ${e.message}")
            null
        }
    }

    fun saveShare2(share: Share): Boolean {
        return try {
            val encrypted = KeystoreCrypto.encrypt(SHARE2_KEY_ALIAS, share.toByteArray())
            prefs.edit().putString(KEY_SHARE_2, encrypted.toHexString()).commit()
        } catch (e: Exception) {
            false
        }
    }

    fun getShare2(): Share? {
        val hex = prefs.getString(KEY_SHARE_2, null) ?: return null
        return try {
            val decrypted = KeystoreCrypto.decrypt(SHARE2_KEY_ALIAS, hex.hexToByteArray()) ?: return null
            Share.fromByteArray(decrypted)
        } catch (e: AEADBadTagException) {
            Log.e("RecoveryShareManager", "Share2 tampered: AEAD tag mismatch")
            null
        } catch (e: Exception) {
            Log.w("RecoveryShareManager", "getShare2 failed: ${e.message}")
            null
        }
    }

    fun saveShare2Encrypted(share: Share, key: ByteArray): Boolean {
        return try {
            val cipher = javax.crypto.Cipher.getInstance(AES_GCM_NO_PADDING)
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, 
                javax.crypto.spec.SecretKeySpec(key, "AES"))
            val ciphertext = cipher.doFinal(share.toByteArray())
            val iv = cipher.iv
            prefs.edit().putString(KEY_SHARE_2, (iv + ciphertext).toHexString()).commit()
        } catch (e: Exception) {
            false
        }
    }

    fun getShare2Encrypted(key: ByteArray): Share? {
        return try {
            val hex = prefs.getString(KEY_SHARE_2, null) ?: return null
            val data = hex.hexToByteArray()
            val iv = data.copyOfRange(0, GCM_IV_LEN)
            val ciphertext = data.copyOfRange(GCM_IV_LEN, data.size)
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            cipher.init(Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(GCM_TAG_LEN, iv))
            val plaintext = cipher.doFinal(ciphertext)
            Share.fromByteArray(plaintext)
        } catch (e: AEADBadTagException) {
            Log.e("RecoveryShareManager", "Share2Encrypted tampered: AEAD tag mismatch")
            null
        } catch (e: Exception) {
            Log.w("RecoveryShareManager", "getShare2Encrypted failed: ${e.message}")
            null
        }
    }

    // F-061: encrypt evalInput via AES/GCM with KeystoreCrypto key (no biometric auth — needed during recovery flow)
    fun saveEvalInput(input: ByteArray) {
        try {
            val key = KeystoreCrypto.getOrCreateKey(EVAL_INPUT_KEY_ALIAS)
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val ciphertext = cipher.doFinal(input)
            val iv = cipher.iv
            prefs.edit().putString(KEY_EVAL_INPUT, (iv + ciphertext).toHexString()).apply()
        } catch (e: Exception) {
            Log.w("RecoveryShareManager", "saveEvalInput failed: ${e.message}")
        }
    }

    fun getEvalInput(): ByteArray? {
        val hex = prefs.getString(KEY_EVAL_INPUT, null) ?: return null
        return try {
            val data = hex.hexToByteArray()
            val iv = data.copyOfRange(0, GCM_IV_LEN)
            val ciphertext = data.copyOfRange(GCM_IV_LEN, data.size)
            val key = KeystoreCrypto.getOrCreateKey(EVAL_INPUT_KEY_ALIAS)
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LEN, iv))
            cipher.doFinal(ciphertext)
        } catch (_: Exception) { null }
    }

    fun saveShare3(share: Share): Boolean {
        return try {
            val key = KeystoreCrypto.getOrCreateKey(SHARE3_KEY_ALIAS)
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val plaintext = share.toByteArray()
            val ciphertext = cipher.doFinal(plaintext)
            val iv = cipher.iv
            val file = getShare3File()
            file.parentFile?.mkdirs()
            file.writeBytes(iv + ciphertext)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getShare3(): Share? {
        return try {
            val file = getShare3File()
            if (!file.exists()) return null
            val data = file.readBytes()
            val iv = data.copyOfRange(0, GCM_IV_LEN)
            val ciphertext = data.copyOfRange(GCM_IV_LEN, data.size)
            val key = KeystoreCrypto.getOrCreateKey(SHARE3_KEY_ALIAS)
            val cipher = Cipher.getInstance(AES_GCM_NO_PADDING)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LEN, iv))
            val plaintext = cipher.doFinal(ciphertext)
            Share.fromByteArray(plaintext)
        } catch (e: AEADBadTagException) {
            Log.e("RecoveryShareManager", "Share3 tampered: AEAD tag mismatch")
            null
        } catch (e: Exception) {
            Log.w("RecoveryShareManager", "getShare3 failed: ${e.message}")
            null
        }
    }

    fun saveShare4(share: Share): Boolean {
        return try {
            val encrypted = KeystoreCrypto.encrypt(SHARE4_KEY_ALIAS, share.toByteArray())
            prefs.edit().putString(KEY_SHARE_4, encrypted.toHexString()).commit()
        } catch (e: AEADBadTagException) {
            Log.e("RecoveryShareManager", "Share4 save tampered: AEAD tag mismatch")
            false
        } catch (e: Exception) {
            Log.w("RecoveryShareManager", "saveShare4 failed: ${e.message}")
            false
        }
    }

    fun getShare4(): Share? {
        val hex = prefs.getString(KEY_SHARE_4, null) ?: return null
        return try {
            val decrypted = KeystoreCrypto.decrypt(SHARE4_KEY_ALIAS, hex.hexToByteArray()) ?: return null
            Share.fromByteArray(decrypted)
        } catch (e: AEADBadTagException) {
            Log.e("RecoveryShareManager", "Share4 tampered: AEAD tag mismatch")
            null
        } catch (e: Exception) {
            Log.w("RecoveryShareManager", "getShare4 failed: ${e.message}")
            null
        }
    }

    fun exportShare4Hex(): String? {
        val share = getShare4() ?: return null
        return share.toByteArray().toHexString()
    }

    fun importShare4Hex(hex: String): Boolean {
        val clean = hex.trim()
        if (clean.length < 2) return false
        return try {
            val bytes = clean.hexToByteArray()
            val share = Share.fromByteArray(bytes)
            saveShare4(share)
        } catch (e: Exception) {
            false
        }
    }

    fun exportShare3Hex(): String? {
        return try {
            val share = getShare3() ?: return null
            share.toByteArray().toHexString()
        } catch (e: Exception) {
            null
        }
    }

    fun importShare3Hex(hex: String): Boolean {
        val clean = hex.trim()
        if (clean.length < 2) return false
        return try {
            val bytes = clean.hexToByteArray()
            val share = Share.fromByteArray(bytes)
            saveShare3(share)
        } catch (e: Exception) {
            false
        }
    }

    fun deleteShares() {
        getShare1File().delete()
        prefs.edit()
            .remove(KEY_SHARE_2)
            .remove(KEY_SHARE_3)
            .remove(KEY_SHARE_4)
            .remove(KEY_EVAL_INPUT)
            .apply()
        getShare3File().delete()
    }

    private fun getShare1File(): File = File(context.filesDir, "shares/share1.enc")
    private fun getShare3File(): File = File(context.filesDir, "shares/share3.enc")

    private fun buildSharePrefs(): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val PREFS_NAME = "mdaopay_recovery_shares"
        private const val KEY_SHARE_2 = "share_2"
        private const val KEY_SHARE_3 = "share_3"
        private const val KEY_SHARE_4 = "share_4"
        private const val KEY_EVAL_INPUT = "eval_input"
        private const val EVAL_INPUT_KEY_ALIAS = "mdaopay_eval_input_key"
        private const val SHARE1_KEY_ALIAS = "mdaopay_share1_key"
        private const val SHARE2_KEY_ALIAS = "mdaopay_share2_key"
        private const val SHARE3_KEY_ALIAS = "mdaopay_share3_key"
        private const val SHARE4_KEY_ALIAS = "mdaopay_share4_key"
        private const val AES_GCM_NO_PADDING = "AES/GCM/NoPadding"
        private const val GCM_IV_LEN = 12
        private const val GCM_TAG_LEN = 128
        private const val REQUIRED = 3
        private const val TOTAL = 4

        fun split(secret: ByteArray, required: Int = REQUIRED, total: Int = TOTAL): List<Share> {
            return ShamirSecretSharing.split(secret, required, total)
        }

        fun join(shares: List<Share>, required: Int = REQUIRED): ByteArray? {
            if (shares.size < required) return null
            return try {
                ShamirSecretSharing.join(shares)
            } catch (e: Exception) {
                null
            }
        }

        const val HERMIT_REQUIRED = 2
        const val HERMIT_TOTAL = 3
    }
}

private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

private fun String.hexToByteArray(): ByteArray {
    val len = length / 2
    return ByteArray(len) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
