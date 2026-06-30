package com.mdaopay.app.core.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager


object HapticManager {
    private var vibrator: Vibrator? = null
    private var enabled = true

    fun init(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun light() {
        vibratePredefined(VibrationEffect.EFFECT_TICK, Build.VERSION_CODES.Q)
    }

    fun click() {
        vibratePredefined(VibrationEffect.EFFECT_CLICK, Build.VERSION_CODES.Q)
    }

    fun heavy() {
        vibratePredefined(VibrationEffect.EFFECT_HEAVY_CLICK, Build.VERSION_CODES.Q)
    }

    fun doubleTap() {
        vibratePredefined(VibrationEffect.EFFECT_DOUBLE_CLICK, Build.VERSION_CODES.Q)
    }

    fun success() {
        heavy()
    }

    fun error() {
        vibrateWaveform(longArrayOf(0, 80, 40, 120))
    }

    fun sendTransaction() {
        vibrateWaveform(longArrayOf(0, 50, 30, 50, 30, 80))
    }

    private fun vibratePredefined(effectId: Int, minSdk: Int) {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= minSdk) {
            vibrator?.vibrate(VibrationEffect.createPredefined(effectId))
        }
    }

    private fun vibrateWaveform(pattern: LongArray) {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }
}
