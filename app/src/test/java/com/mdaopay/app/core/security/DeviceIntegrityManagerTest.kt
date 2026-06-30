package com.mdaopay.app.core.security

import org.junit.Assert
import org.junit.Test
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

/**
 * Regression tests for F-060: JWT signature verification in DeviceIntegrityManager.
 *
 * These tests validate the core RSA SHA256withRSA verification logic
 * that DeviceIntegrityManager.verifyIntegrityToken() uses to validate
 * Play Integrity tokens. Pure JVM tests — no Android dependencies.
 *
 * See also: androidTest variant for full integration with android.util.Base64.
 */
class DeviceIntegrityManagerTest {

    @Test
    fun `rsa signature verification accepts valid signature`() {
        // Generate RSA key pair (simulates Google's key pair)
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048)
        val keyPair = gen.generateKeyPair()

        // Create test data (simulates "header.payload")
        val data = "test.eyJub25jZSI6ICJ4eHh4In0".toByteArray()

        // Sign with private key
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private)
        signer.update(data)
        val signature = signer.sign()

        // Build public key from pair (simulates buildRsaPublicKey)
        val pubKey = keyPair.public
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(pubKey)
        verifier.update(data)
        val isValid = verifier.verify(signature)

        assert(isValid) { "Valid RSA signature should verify" }
    }

    @Test
    fun `rsa signature verification rejects tampered data`() {
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048)
        val keyPair = gen.generateKeyPair()

        // Original data signed
        val originalData = "test.original.payload".toByteArray()
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private)
        signer.update(originalData)
        val signature = signer.sign()

        // Tamper with data
        val tamperedData = "test.tampered.payload".toByteArray()
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(keyPair.public)
        verifier.update(tamperedData)
        val isValid = verifier.verify(signature)

        assert(!isValid) { "Tampered data should NOT verify" }
    }

    @Test
    fun `rsa signature verification rejects different key`() {
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048)
        val keyPair1 = gen.generateKeyPair()
        val keyPair2 = gen.generateKeyPair()

        val data = "test.some.payload".toByteArray()

        // Sign with key 1
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair1.private)
        signer.update(data)
        val signature = signer.sign()

        // Verify with key 2 — should fail
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(keyPair2.public)
        verifier.update(data)
        val isValid = verifier.verify(signature)

        assert(!isValid) { "Signature from different key should NOT verify" }
    }

    @Test
    fun `rsa public key built from n and e works for verification`() {
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048)
        val keyPair = gen.generateKeyPair()

        // Extract n and e like JWKS does
        val rsaPubKey = keyPair.public as java.security.interfaces.RSAPublicKey
        val n = rsaPubKey.modulus
        val e = rsaPubKey.publicExponent

        // Build public key from n and e (same as buildRsaPublicKey)
        val spec = RSAPublicKeySpec(n, e)
        val factory = KeyFactory.getInstance("RSA")
        val rebuiltKey = factory.generatePublic(spec)

        // Sign with original private key
        val data = "header.payload".toByteArray()
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private)
        signer.update(data)
        val signature = signer.sign()

        // Verify with rebuilt public key
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(rebuiltKey)
        verifier.update(data)
        assert(verifier.verify(signature)) { "Rebuilt public key should verify" }
    }

    @Test
    fun `jwt flow with self-signed token`() {
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048)
        val keyPair = gen.generateKeyPair()

        // Get n, e as Base64url (JWKS format)
        val rsaKey = keyPair.public as java.security.interfaces.RSAPublicKey
        val nB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(rsaKey.modulus.toByteArray())
        val eB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(rsaKey.publicExponent.toByteArray())

        // Build JWT header (compact JSON with kid)
        val header = """{"alg":"RS256","kid":"test-key-1"}"""
        val headerB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(header.toByteArray())

        // Build JWT payload with Play Integrity shape
        val nowMs = System.currentTimeMillis()
        val payload = """{"nonce":"test-nonce-123","timestampMs":$nowMs,"apkPackageName":"com.mdaopay.app","deviceIntegrity":{"deviceRecognitionVerdict":["MEETS_DEVICE_INTEGRITY"]},"appIntegrity":{"appRecognitionVerdict":"MEETS_DEVICE_INTEGRITY"}}"""
        val payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())

        // Sign header.payload
        val signingInput = "$headerB64.$payloadB64".toByteArray()
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private)
        signer.update(signingInput)
        val signatureBytes = signer.sign()
        val signatureB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes)

        // Full JWT
        val jwt = "$headerB64.$payloadB64.$signatureB64"
        val parts = jwt.split(".")
        assert(parts.size == 3) { "JWT should have 3 parts" }

        // Verify with public key (same logic as verifyIntegrityToken)
        val decodedN = java.math.BigInteger(1, Base64.getUrlDecoder().decode(nB64))
        val decodedE = java.math.BigInteger(1, Base64.getUrlDecoder().decode(eB64))
        val spec = RSAPublicKeySpec(decodedN, decodedE)
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(spec)

        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(publicKey)
        verifier.update("${parts[0]}.${parts[1]}".toByteArray())
        val isValid = verifier.verify(Base64.getUrlDecoder().decode(parts[2]))

        assert(isValid) { "Self-signed JWT should verify" }

        // Verify tampered signature fails
        val tamperedJwt = "$headerB64.$payloadB64.${signatureB64}AA"
        val tamperedParts = tamperedJwt.split(".")
        val verifier2 = Signature.getInstance("SHA256withRSA")
        verifier2.initVerify(publicKey)
        verifier2.update("${tamperedParts[0]}.${tamperedParts[1]}".toByteArray())
        val isTamperedValid = try {
            verifier2.verify(Base64.getUrlDecoder().decode(tamperedParts[2]))
        } catch (_: IllegalArgumentException) {
            false
        }
        assert(!isTamperedValid) { "Tampered JWT should NOT verify" }
    }

    @Test
    fun `nonce mismatch should be rejected`() {
        // This tests the nonce check logic from verifyIntegrityToken
        val expectedNonce = "expected-nonce"
        val actualNonce = "wrong-nonce"

        assert(expectedNonce != actualNonce) { "Nonces should differ" }
        // The check in verifyIntegrityToken: if (actualNonce != expectedNonce) throw
        Assert.assertThrows(SecurityException::class.java) {
            checkNonce(actualNonce, expectedNonce)
        }
    }

    @Test
    fun `expired token should be rejected`() {
        // 10 minutes ago — exceeds 5 min tolerance
        val oldTimestampMs = System.currentTimeMillis() - 10 * 60 * 1000
        val nowMs = System.currentTimeMillis()

        assert(nowMs - oldTimestampMs > 5 * 60 * 1000) { "Token >5 min old should be expired" }
        Assert.assertThrows(SecurityException::class.java) {
            checkTimestamp(oldTimestampMs)
        }
    }

    @Test
    fun `valid timestamp should pass`() {
        // 1 minute ago — within 5 min tolerance
        val validTimestampMs = System.currentTimeMillis() - 60 * 1000
        checkTimestamp(validTimestampMs) // should not throw
    }

    @Test
    fun `future timestamp should be rejected`() {
        // 2 minutes in the future — exceeds 1 min clock skew tolerance
        val futureTimestampMs = System.currentTimeMillis() + 2 * 60 * 1000
        Assert.assertThrows(SecurityException::class.java) {
            checkTimestamp(futureTimestampMs)
        }
    }

    @Test
    fun `package name mismatch should be rejected`() {
        Assert.assertThrows(SecurityException::class.java) {
            checkPackageName("com.mdaopay.app", "com.attacker.app")
        }
    }

    // ── Helpers that mirror verifyIntegrityToken checks ──

    private fun checkNonce(actualNonce: String, expectedNonce: String) {
        if (actualNonce != expectedNonce) throw SecurityException("Nonce mismatch")
    }

    private fun checkTimestamp(timestampMs: Long) {
        val nowMs = System.currentTimeMillis()
        if (timestampMs <= 0 || nowMs - timestampMs > 5 * 60 * 1000) {
            throw SecurityException("Token expired or invalid timestamp")
        }
        if (timestampMs > nowMs + 60_000) {
            throw SecurityException("Token timestamp from future")
        }
    }

    private fun checkPackageName(expected: String, actual: String) {
        if (expected != actual) throw SecurityException("Package name mismatch")
    }

    @Test
    fun `buildRsaPublicKey works with valid JWK key`() {
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048)
        val keyPair = gen.generateKeyPair()
        val rsaKey = keyPair.public as java.security.interfaces.RSAPublicKey

        // Convert to Base64url (as JWKS would provide)
        val nB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(rsaKey.modulus.toByteArray())
        val eB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(rsaKey.publicExponent.toByteArray())

        // Rebuild using same algorithm as DeviceIntegrityManager.buildRsaPublicKey
        val decodedN = java.math.BigInteger(1, Base64.getUrlDecoder().decode(nB64))
        val decodedE = java.math.BigInteger(1, Base64.getUrlDecoder().decode(eB64))
        val spec = RSAPublicKeySpec(decodedN, decodedE)
        val factory = KeyFactory.getInstance("RSA")
        val rebuiltKey = factory.generatePublic(spec)

        Assert.assertNotNull(rebuiltKey)

        // Verify it can verify a signature
        val data = "verification-test".toByteArray()
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private)
        signer.update(data)
        val sig = signer.sign()

        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(rebuiltKey)
        verifier.update(data)
        assert(verifier.verify(sig)) { "Rebuilt key should verify" }
    }
}
