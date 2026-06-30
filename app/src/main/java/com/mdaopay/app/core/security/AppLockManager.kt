package com.mdaopay.app.core.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLockManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val biometricManager: BiometricAuthManager
) : DefaultLifecycleObserver {

    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private val _isOnboarded = MutableStateFlow(false)
    val isOnboarded: StateFlow<Boolean> = _isOnboarded.asStateFlow()

    private var lockEnabled = true

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                forceLock()
            }
        }
    }

    fun init() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        context.registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    fun restoreState(isOnboarded: Boolean, lockEnabled: Boolean) {
        this.lockEnabled = lockEnabled
        _isOnboarded.value = isOnboarded
        _isLocked.value = isOnboarded && lockEnabled
    }

    fun setOnboarded() {
        _isOnboarded.value = true
        _isLocked.value = false
    }

    override fun onStop(owner: LifecycleOwner) {
        if (_isOnboarded.value && lockEnabled) {
            _isLocked.value = true
        }
    }

    fun unlock() {
        _isLocked.value = false
    }

    fun forceLock() {
        if (_isOnboarded.value && lockEnabled) {
            _isLocked.value = true
        }
    }

    fun setLockEnabled(enabled: Boolean) {
        lockEnabled = enabled
        if (enabled) _isLocked.value = true
    }
}
