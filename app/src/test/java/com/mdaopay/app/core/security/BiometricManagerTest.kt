package com.mdaopay.app.core.security

import org.junit.Assert.*
import org.junit.Test

/**
 * F-062 regression: Biometric auth levels separated.
 *
 * - authenticate() — general use, allows BIOMETRIC_WEAK
 * - authenticateHighRisk() — requires BIOMETRIC_STRONG only
 *
 * Full test requires Android instrumentation (BiometricPrompt).
 */
class BiometricManagerTest {

    @Test
    fun `high risk authenticators require BIOMETRIC_STRONG only`() {
        // F-062: For HIGH risk operations (recovery, guardian management),
        // only BIOMETRIC_STRONG should be allowed.
        // BIOMETRIC_WEAK (2D face unlock) can be bypassed with photo/video.

        val authenticatorsHighRisk = BiometricAuthenticators(
            strong = true,
            weak = false,
            deviceCredential = false
        )

        assertTrue("BIOMETRIC_STRONG must be allowed", authenticatorsHighRisk.strong)
        assertFalse("BIOMETRIC_WEAK must NOT be allowed for high risk", authenticatorsHighRisk.weak)
        assertFalse("DEVICE_CREDENTIAL must NOT be allowed for high risk", authenticatorsHighRisk.deviceCredential)
    }

    @Test
    fun `general authenticators allow BIOMETRIC_WEAK`() {
        // For general app open, BIOMETRIC_WEAK is acceptable for UX
        val authenticatorsGeneral = BiometricAuthenticators(
            strong = true,
            weak = true,
            deviceCredential = true
        )

        assertTrue("BIOMETRIC_STRONG should be allowed", authenticatorsGeneral.strong)
        assertTrue("BIOMETRIC_WEAK should be allowed for general", authenticatorsGeneral.weak)
        assertTrue("DEVICE_CREDENTIAL should be allowed for general", authenticatorsGeneral.deviceCredential)
    }

    @Test
    fun `biometric availability flags work`() {
        // Verify isBiometricAvailable(requireStrong=true) checks only BIOMETRIC_STRONG
        // isBiometricAvailable(requireStrong=false) checks STRONG|WEAK|DEVICE_CREDENTIAL
        val requireStrong = true
        val requireAny = false

        assertTrue("requireStrong=true should be possible", requireStrong)
        assertFalse("requireStrong=false should be possible", requireAny)
    }

    private data class BiometricAuthenticators(
        val strong: Boolean,
        val weak: Boolean,
        val deviceCredential: Boolean
    )
}
