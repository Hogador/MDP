package com.mdaopay.app.core.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.mdaopay.app.core.common.AppError
import com.mdaopay.app.core.common.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// ponytail: Two biometric levels:
//   authenticate() — general use (BIOMETRIC_WEAK allowed for UX)
//   authenticateHighRisk() — HIGH risk ops (BIOMETRIC_STRONG only, per F-062)
//   High-risk has 30s grace window: once authenticated, subsequent calls within 30s skip prompt.
@Singleton
class BiometricAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** F-062: reset 30s window (for testing, or explicit re-auth). */
    fun resetHighRiskWindow() { lastHighRiskAuthMs = 0L }

    companion object {
        /** F-062: 30-second window for high-risk operations (not 300s like general auth). */
        const val HIGH_RISK_WINDOW_MS = 30_000L
        @Volatile
        var lastHighRiskAuthMs: Long = 0L
    }

    fun isBiometricAvailable(requireStrong: Boolean = false): BiometricAvailability {
        val manager = BiometricManager.from(context)
        val authenticators = if (requireStrong) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
        return when (manager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Available
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NoneEnrolled
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NoHardware
            else -> BiometricAvailability.Available
        }
    }

    /** General authentication — allows BIOMETRIC_WEAK and DEVICE_CREDENTIAL */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        authenticateInternal(
            activity = activity,
            title = title,
            subtitle = subtitle,
            requireStrong = false,
            onResult = onResult
        )
    }

    /** High-risk authentication — requires BIOMETRIC_STRONG only (F-062).
     * Has 30s grace window: if user authenticated within last 30s, skips prompt. */
    fun authenticateHighRisk(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val now = System.currentTimeMillis()
        if (now - lastHighRiskAuthMs < HIGH_RISK_WINDOW_MS) {
            onResult(Result.Success(Unit))
            return
        }
        authenticateInternal(
            activity = activity,
            title = title,
            subtitle = subtitle,
            requireStrong = true,
            onResult = { result ->
                if (result is Result.Success) {
                    lastHighRiskAuthMs = System.currentTimeMillis()
                }
                onResult(result)
            }
        )
    }

    private fun authenticateInternal(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        requireStrong: Boolean,
        onResult: (Result<Unit>) -> Unit
    ) {
        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(Result.Success(Unit))
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    val error = when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> AppError.BiometricCancelled
                        BiometricPrompt.ERROR_HW_NOT_PRESENT,
                        BiometricPrompt.ERROR_HW_UNAVAILABLE -> AppError.BiometricNotAvailable
                        else -> AppError.BiometricFailed
                    }
                    onResult(Result.Error(error))
                }
                override fun onAuthenticationFailed() { }
            }
            val authenticators = if (requireStrong) {
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            } else {
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            }
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)
                .build()
            BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
        } catch (e: Exception) {
            onResult(Result.Error(AppError.BiometricFailed))
        }
    }
}
