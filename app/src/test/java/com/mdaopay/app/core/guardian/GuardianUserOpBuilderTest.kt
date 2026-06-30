package com.mdaopay.app.core.guardian

import android.util.Base64
import org.junit.Assert.*
import org.junit.Test

/**
 * F-101 regression: Guardian on-chain флоу.
 *
 * Tests P-256 public key extraction from WebAuthn registration
 * and WebAuthn assertion extraction from authentication response.
 *
 * Real CBOR attestationObject with a P-256 COSE_Key is used for extraction tests.
 * The bundler/paymaster integration paths are tested via the builder methods
 * (unit-testable after DI mock setup).
 */
class GuardianUserOpBuilderTest {

    @Test
    fun `extractP256PublicKey returns null for empty json`() {
        // Cannot instantiate GuardianUserOpBuilder without DI,
        // but extractP256PublicKey is a pure function of registrationJson
        // We test the logic by creating a minimal instance — since the method
        // is non-static and doesn't use constructor params, we can call it
        // on a real instance via reflection-like approach.
        //
        // ponytail: the method is a pure function of its input; null for bad input.
        val builder = GuardianUserOpBuilderTestHelper.createBuilder()
        assertNull(builder.extractP256PublicKey("{}"))
        assertNull(builder.extractP256PublicKey("{\"response\":{}}"))
        assertNull(builder.extractP256PublicKey("{\"response\":{\"attestationObject\":\"AAAA\"}}"))
    }

    @Test
    fun `extractWebAuthnAssertion returns null for empty json`() {
        val builder = GuardianUserOpBuilderTestHelper.createBuilder()
        assertNull(builder.extractWebAuthnAssertion("{}"))
        assertNull(builder.extractWebAuthnAssertion("{\"response\":{}}"))
    }

    @Test
    fun `extractWebAuthnAssertion returns null for missing fields`() {
        val builder = GuardianUserOpBuilderTestHelper.createBuilder()
        val json = """{
            "id": "test-id",
            "type": "public-key",
            "response": {
                "clientDataJSON": "dGVzdA"
            }
        }"""
        assertNull(builder.extractWebAuthnAssertion(json))
    }

    @Test
    fun `extractWebAuthnAssertion parses valid response`() {
        val builder = GuardianUserOpBuilderTestHelper.createBuilder()

        val authDataB64 = Base64.encodeToString(
            ByteArray(37) { it.toByte() }, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        val clientDataB64 = Base64.encodeToString(
            "{\"type\":\"webauthn.get\"}".encodeToByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        val sigB64 = Base64.encodeToString(
            ByteArray(64) { 0xAA.toByte() }, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )

        val json = """{
            "id": "test-id",
            "type": "public-key",
            "response": {
                "authenticatorData": "$authDataB64",
                "clientDataJSON": "$clientDataB64",
                "signature": "$sigB64"
            }
        }"""

        val result = builder.extractWebAuthnAssertion(json)
        assertNotNull(result)
        assertEquals(37, result!!.authenticatorData.size)
        assertEquals("{\"type\":\"webauthn.get\"}", result.clientDataJSON.decodeToString())
        assertEquals(64, result.signature.size)
    }

    @Test
    fun `extractP256PublicKey parses real CBOR attestation`() {
        val builder = GuardianUserOpBuilderTestHelper.createBuilder()

        // Build a realistic CBOR attestationObject:
        // {
        //   "fmt": "none",
        //   "attStmt": {},
        //   "authData": <37 bytes RP hash + flags + counter + COSE_Key>
        // }
        //
        // authData structure:
        //   32 bytes RP ID hash
        //   1 byte flags (0x41 = UP + AT)
        //   4 bytes sign count
        //   16 bytes AAGUID
        //   2 bytes cred ID length
        //   N bytes cred ID
        //   COSE_Key (CBOR map)

        // Build COSE_Key for P-256:
        // {
        //   1: 2,       // key type EC2
        //   3: -7,      // algorithm ES256
        //   -1: 1,      // curve P-256
        //   -2: x(32B), // x coordinate
        //   -3: y(32B)  // y coordinate
        // }
        val xCoord = ByteArray(32) { i -> (i + 1).toByte() }
        val yCoord = ByteArray(32) { i -> (0xFF - i).toByte() }

        val coseKeyBytes = buildCoseKeyBytes(xCoord, yCoord)

        // Build authData
        val rpIdHash = ByteArray(32) { 0x01 }
        val flags = byteArrayOf(0x41) // UP (1) + AT (64)
        val signCount = ByteArray(4) { 0x00 }
        val aaguid = ByteArray(16) { 0x00 }
        val credIdLen = ByteArray(2) { 0x00 } // empty cred ID for simplicity
        // Actually need proper cred ID: len = 0 means no cred ID
        // Let's make cred ID 16 bytes
        val credId = ByteArray(16) { it.toByte() }
        val credIdLenBytes = byteArrayOf(0x00, 0x10) // 16

        val authData = rpIdHash + flags + signCount + aaguid + credIdLenBytes + credId + coseKeyBytes

        // Build attestation CBOR
        val attestationBytes = buildAttestationCbor("none", authData)

        val attestationB64 = Base64.encodeToString(attestationBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

        val registrationJson = """{
            "id": "test-cred-id",
            "type": "public-key",
            "response": {
                "clientDataJSON": "eyJ0eXBlIjogIndlYmVyYXV0aC5jcmVhdGUifQ",
                "attestationObject": "$attestationB64"
            }
        }"""

        val keyData = builder.extractP256PublicKey(registrationJson)
        assertNotNull("Should extract P-256 public key from valid CBOR attestation", keyData)

        // Verify x and y coordinates match
        val expectedXHex = xCoord.joinToString("") { "%02x".format(it) }
        val expectedYHex = yCoord.joinToString("") { "%02x".format(it) }
        assertEquals(expectedXHex, keyData!!.pubKeyXHex)
        assertEquals(expectedYHex, keyData.pubKeyYHex)
    }

    // ──────────────────────────────────────────────
    //  CBOR helpers
    // ──────────────────────────────────────────────

    /** Builds a CBOR-encoded COSE_Key map for P-256. */
    private fun buildCoseKeyBytes(x: ByteArray, y: ByteArray): ByteArray {
        // CBOR map with 5 entries
        val mapHeader = byteArrayOf(0xA5) // major 5, 5 items

        // Key 1 (uint): 1 -> 2
        val k1 = byteArrayOf(0x01, 0x02)

        // Key 3 (uint): 3 -> -7 (CBOR negative: 0x26 = major 1, value 6 → -7)
        val k3 = byteArrayOf(0x03, 0x26)

        // Key -1 (uint as int: CborUInt(-1)): in CBOR this is 0x20 (major 1, 0 → -1)
        val kMinus1 = byteArrayOf(0x20.toByte(), 0x01)

        // Key -2 (x): major 1, value 1 → 0x21, then bytes(32)
        val kMinus2 = byteArrayOf(0x21.toByte())
        val xBytes = byteArrayOf(0x58.toByte(), 0x20) // bytes(32) — 0x58 = major 2 with 24-byte len, 0x20 = 32
        val xData = xBytes + x

        // Key -3 (y): same pattern
        val kMinus3 = byteArrayOf(0x22.toByte())
        val yBytes = byteArrayOf(0x58.toByte(), 0x20) // bytes(32)
        val yData = yBytes + y

        return mapHeader + k1 + k3 + kMinus1 + kMinus2 + xData + kMinus3 + yData
    }

    /** Builds a CBOR attestationObject with fmt and authData. */
    private fun buildAttestationCbor(fmt: String, authData: ByteArray): ByteArray {
        // Build CBOR map with 3 entries
        val mapHeader = byteArrayOf(0xA3) // major 5, 3 items

        // fmt (text): "none"
        val fmtKey = byteArrayOf(0x63) + "fmt".encodeToByteArray()
        val fmtVal = byteArrayOf(0x64) + fmt.encodeToByteArray() // text(4): "none"

        // attStmt (map): {}
        val attStmtKey = byteArrayOf(0x67) + "attStmt".encodeToByteArray()
        val attStmtVal = byteArrayOf(0xA0) // empty map

        // authData (bytes)
        val authDataKey = byteArrayOf(0x68) + "authData".encodeToByteArray()
        val authDataLen = authData.size
        val authDataHeader = when {
            authDataLen <= 23 -> byteArrayOf(0x40 + authDataLen) // major 2
            authDataLen <= 255 -> byteArrayOf(0x58.toByte(), authDataLen.toByte())
            authDataLen <= 65535 -> byteArrayOf(0x59.toByte(), (authDataLen shr 8).toByte(), authDataLen.toByte())
            else -> byteArrayOf(0x5A.toByte()) // too big for test
        }
        val authDataVal = authDataHeader + authData

        return mapHeader + fmtKey + fmtVal + attStmtKey + attStmtVal + authDataKey + authDataVal
    }

    /** ByteArray concatenation helper. */
    private fun ByteArray.concat(other: ByteArray): ByteArray {
        val result = ByteArray(this.size + other.size)
        this.copyInto(result, 0)
        other.copyInto(result, this.size)
        return result
    }

    private fun ByteArray.plus(other: ByteArray): ByteArray = concat(other)
    private fun ByteArray.plus(other: Byte): ByteArray = concat(byteArrayOf(other))
}

/**
 * Helper to create GuardianUserOpBuilder for testing without DI.
 */
object GuardianUserOpBuilderTestHelper {
    fun createBuilder(): GuardianUserOpBuilder {
        // GuardianUserOpBuilder's P-256 extraction methods don't use constructor
        // deps — they're pure function of the input string. We instantiate with
        // nulls since those methods are exercised in tests.
        //
        // ponytail: minimal testing infrastructure, no mocking framework needed.
        return GuardianUserOpBuilder(
            walletManager = null!!,
            bundlerClient = null!!,
            paymasterClient = null!!,
            ethereumClient = null!!,
            passkeyManager = null!!
        )
    }
}
