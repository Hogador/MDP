package com.mdaopay.app.core.blockchain

import android.content.Context
import android.content.SharedPreferences
import com.mdaopay.app.core.security.KeystoreCrypto
import dagger.hilt.android.qualifiers.ApplicationContext
import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Keys
import org.web3j.crypto.MnemonicUtils
import java.math.BigInteger
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

data class WalletData(
    val mnemonic: String,
    val address: String,
    val publicKeyBytes: ByteArray,
    val privateKeyBytes: ByteArray
) {
    val keyPair: ECKeyPair
        get() = ECKeyPair(BigInteger(privateKeyBytes), BigInteger(publicKeyBytes))
}

sealed class WalletResult {
    data class Success(val wallet: WalletData) : WalletResult()
    data class Error(val message: String) : WalletResult()

    inline fun <T> mapNullable(selector: (WalletData) -> T?): T? = when (this) {
        is Success -> selector(wallet)
        is Error -> null
    }
}

@Singleton
class WalletManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val secureRandom = SecureRandom()
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun generateWallet(): WalletResult {
        return try {
            val entropy = ByteArray(32)
            secureRandom.nextBytes(entropy)
            val mnemonic = MnemonicUtils.generateMnemonic(entropy)
            deriveWallet(mnemonic)
        } catch (e: Exception) {
            WalletResult.Error("Wallet generation failed: ${e.message}")
        }
    }

    fun loadWallet(): WalletResult {
        val hex = prefs.getString(KEY_MNEMONIC, null) ?:
            return WalletResult.Error("No wallet found")
        val encrypted = hex.hexToByteArray()
        val plaintext = KeystoreCrypto.decrypt(KEY_ALIAS, encrypted) ?:
            return WalletResult.Error("Decryption failed")
        return deriveWallet(plaintext.decodeToString())
    }

    fun getWalletData(): WalletData? {
        return when (val result = loadWallet()) {
            is WalletResult.Success -> result.wallet
            is WalletResult.Error -> null
        }
    }

    fun hasWallet(): Boolean = prefs.contains(KEY_MNEMONIC)

    fun saveMnemonic(mnemonic: String): Boolean {
        // ponytail: encrypt uses biometric-bound key (setUserAuthenticationRequired=true).
        // Must be preceded by BiometricPrompt within 300s auth window (set by getOrCreateBiometricKey).
        // Called from OnboardingNicknameViewModel.confirmNickname — right after biometric screen.
        return try {
            val encrypted = KeystoreCrypto.encrypt(KEY_ALIAS, mnemonic.encodeToByteArray())
            prefs.edit().putString(KEY_MNEMONIC, encrypted.toHexString()).commit()
        } catch (e: Exception) {
            false
        }
    }

    fun clearWallet() {
        prefs.edit().clear().apply()
    }

    private fun deriveWallet(mnemonic: String): WalletResult {
        return try {
            val seed = MnemonicUtils.generateSeed(mnemonic, "")
            val masterKey = Bip32ECKeyPair.generateKeyPair(seed)
            val path = intArrayOf(
                Bip32ECKeyPair.HARDENED_BIT + 44,
                Bip32ECKeyPair.HARDENED_BIT + 60,
                Bip32ECKeyPair.HARDENED_BIT + 0,
                0,
                0
            )
            val keyPair = Bip32ECKeyPair.deriveKeyPair(masterKey, path)
            val address = Keys.toChecksumAddress(Keys.getAddress(keyPair))
            val publicKeyBytes = keyPair.publicKey.toByteArray()
            val privateKeyBytes = keyPair.privateKey.toByteArray()

            WalletResult.Success(
                WalletData(
                    mnemonic = mnemonic,
                    address = address,
                    publicKeyBytes = publicKeyBytes,
                    privateKeyBytes = privateKeyBytes
                )
            )
        } catch (e: Exception) {
            WalletResult.Error("Key derivation failed: ${e.message}")
        }
    }

    companion object {
        private const val PREFS_NAME = "mdaopay_wallet"
        private const val KEY_MNEMONIC = "encrypted_mnemonic"
        private const val KEY_ALIAS = "mdaopay_wallet_key"
    }
}

private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

private fun String.hexToByteArray(): ByteArray {
    val len = length / 2
    return ByteArray(len) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
