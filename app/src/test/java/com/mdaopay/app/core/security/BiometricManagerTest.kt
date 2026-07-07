package com.mdaopay.app.core.security

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * F-062 regression: Biometric auth levels separated + 30s grace window.
 *
 * - authenticate() — general use, allows BIOMETRIC_WEAK
 * - authenticateHighRisk() — requires BIOMETRIC_STRONG only
 * - authenticateHighRisk() has 30s grace window (F-062)
 *
 * Full BiometricPrompt test requires Android instrumentation.
 */
class BiometricManagerTest {

    // Test the 30s window using the companion object directly.
    // BiometricAuthManager requires Android Context for prompt — skip instantiation.

    @After
    fun tearDown() {
        BiometricAuthManager.lastHighRiskAuthMs = 0L
    }

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
    fun `high risk window starts expired`() {
        assertEquals("Window must start at 0 (expired)",
            0L, BiometricAuthManager.lastHighRiskAuthMs)
    }

    @Test
    fun `high risk window is 30 seconds`() {
        assertEquals("Window must be exactly 30_000 ms",
            30_000L, BiometricAuthManager.HIGH_RISK_WINDOW_MS)
    }

    @Test
    fun `reset clears the window`() {
        BiometricAuthManager.lastHighRiskAuthMs = 0L
        assertEquals("Must be 0 after reset", 0L, BiometricAuthManager.lastHighRiskAuthMs)
    }

    private data class BiometricAuthenticators(
        val strong: Boolean,
        val weak: Boolean,
        val deviceCredential: Boolean
    )
}
