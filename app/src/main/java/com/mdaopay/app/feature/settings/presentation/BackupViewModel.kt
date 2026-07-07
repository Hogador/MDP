package com.mdaopay.app.feature.settings.presentation

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.mdaopay.app.core.blockchain.WalletManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.web3j.crypto.Wallet
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val walletManager: WalletManager
) : ViewModel() {

    private val _mnemonicWords = MutableStateFlow<List<String>>(emptyList())
    val mnemonicWords: StateFlow<List<String>> = _mnemonicWords.asStateFlow()

    private val _hasWallet = MutableStateFlow(false)
    val hasWallet: StateFlow<Boolean> = _hasWallet.asStateFlow()

    init {
        val wallet = walletManager.getWalletData()
        if (wallet != null) {
            _mnemonicWords.value = wallet.mnemonic.split(" ")
            _hasWallet.value = true
        }
    }

    fun verifyWord(index: Int, input: String): Boolean {
        val words = _mnemonicWords.value
        return index in words.indices && words[index] == input.trim().lowercase()
    }

    /** Generate encrypted JSON keystore as a string. Returns JSON or null on failure. */
    fun exportKeystoreJson(password: String): String? {
        val wallet = walletManager.getWalletData() ?: return null
        return try {
            val walletFile = Wallet.createLight(password, wallet.keyPair)
            ObjectMapper().writeValueAsString(walletFile)
        } catch (e: Exception) {
            null
        }
    }

    /** Generate a QR code bitmap from the recovery phrase. */
    fun generateQrBitmap(size: Int = 512): Bitmap? {
        val phrase = _mnemonicWords.value.joinToString(" ") ?: return null
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(phrase, BarcodeFormat.QR_CODE, size, size)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            null
        }
    }
}
