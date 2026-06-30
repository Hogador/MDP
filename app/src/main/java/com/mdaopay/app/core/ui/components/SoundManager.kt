package com.mdaopay.app.core.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.mdaopay.app.R

object SoundManager {
    private var audioManager: AudioManager? = null
    private var soundPool: SoundPool? = null
    private var clickId = 0
    private var successId = 0
    private var errorId = 0
    private var sendId = 0
    private var scanId = 0
    private var swipeId = 0
    private var customLoaded = false
    private var enabled = true

    fun init(context: Context) {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            soundPool = SoundPool.Builder()
                .setMaxStreams(6)
                .setAudioAttributes(attrs)
                .build()
            soundPool?.setOnLoadCompleteListener { _, _, status ->
                if (status == 0) customLoaded = true
            }
            clickId = soundPool?.load(context, R.raw.sfx_click, 1) ?: 0
            successId = soundPool?.load(context, R.raw.sfx_success, 1) ?: 0
            errorId = soundPool?.load(context, R.raw.sfx_error, 1) ?: 0
            sendId = soundPool?.load(context, R.raw.sfx_send, 1) ?: 0
            scanId = soundPool?.load(context, R.raw.sfx_scan, 1) ?: 0
            swipeId = soundPool?.load(context, R.raw.sfx_swipe, 1) ?: 0
        } catch (_: Exception) {
            soundPool = null
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        customLoaded = false
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun playClick() {
        if (!enabled) return
        audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK)
    }

    fun playSuccess() {
        if (!enabled) return
        if (customLoaded && successId != 0) {
            soundPool?.play(successId, 0.7f, 0.7f, 1, 0, 1f)
        } else {
            HapticManager.sendTransaction()
        }
    }

    fun playError() {
        if (!enabled) return
        if (customLoaded && errorId != 0) {
            soundPool?.play(errorId, 0.8f, 0.8f, 1, 0, 1f)
        } else {
            audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_DELETE)
            HapticManager.error()
        }
    }

    fun playSend() {
        if (!enabled) return
        if (customLoaded && sendId != 0) {
            soundPool?.play(sendId, 0.7f, 0.7f, 1, 0, 1f)
        } else {
            HapticManager.sendTransaction()
        }
    }

    fun playScan() {
        if (!enabled) return
        if (customLoaded && scanId != 0) {
            soundPool?.play(scanId, 0.5f, 0.5f, 1, 0, 1f)
        } else {
            audioManager?.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_UP)
        }
    }

    fun playSwipe() {
        if (!enabled) return
        if (customLoaded && swipeId != 0) {
            soundPool?.play(swipeId, 0.4f, 0.4f, 1, 0, 1f)
        } else {
            audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
        }
    }
}

@Composable
fun rememberSoundManager(): SoundManager {
    val context = LocalContext.current
    val sm = remember(context) { SoundManager.also { it.init(context) } }
    DisposableEffect(Unit) {
        onDispose { sm.release() }
    }
    return sm
}
