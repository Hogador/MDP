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
@Singleton
class BiometricAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
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

    /** High-risk authentication — requires BIOMETRIC_STRONG only (F-062) */
    fun authenticateHighRisk(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        authenticateInternal(
            activity = activity,
            title = title,
            subtitle = subtitle,
            requireStrong = true,
            onResult = onResult
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
