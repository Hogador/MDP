package com.mdaopay.app.core.guardian

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

/**
 * F-101 regression: Guardian on-chain флоу.
 *
 * Tests P-256 public key extraction from WebAuthn registration
 * and WebAuthn assertion extraction from authentication response.
 *
 * Real CBOR attestationObject with a P-256 COSE_Key is used for extraction tests.
 * The bundler/paymaster integration paths are tested via the builder methods
 * (unit-testable after DI mock setup).
 *
 * ponytail: pure-function methods live in companion object → callable directly.
 */
class GuardianUserOpBuilderTest {

    @Test
    fun `extractP256PublicKey returns null for empty json`() {
        assertNull(GuardianUserOpBuilder.extractP256PublicKey("{}"))
        assertNull(GuardianUserOpBuilder.extractP256PublicKey("{\"response\":{}}"))
        assertNull(GuardianUserOpBuilder.extractP256PublicKey("{\"response\":{\"attestationObject\":\"AAAA\"}}"))
    }

    @Test
    fun `extractWebAuthnAssertion returns null for empty json`() {
        assertNull(GuardianUserOpBuilder.extractWebAuthnAssertion("{}"))
        assertNull(GuardianUserOpBuilder.extractWebAuthnAssertion("{\"response\":{}}"))
    }

    @Test
    fun `extractWebAuthnAssertion returns null for missing fields`() {
        val json = """{
            "id": "test-id",
            "type": "public-key",
            "response": {
                "clientDataJSON": "dGVzdA"
            }
        }"""
        assertNull(GuardianUserOpBuilder.extractWebAuthnAssertion(json))
    }

    @Test
    fun `extractWebAuthnAssertion parses valid response`() {
        val b64enc = Base64.getUrlEncoder().withoutPadding()
        val authDataB64 = b64enc.encodeToString(ByteArray(37) { it.toByte() })
        val clientDataB64 = b64enc.encodeToString(
            "{\"type\":\"webauthn.get\",\"origin\":\"android:apk-key-hash:test\"}".encodeToByteArray()
        )
        val sigB64 = b64enc.encodeToString(ByteArray(64) { 0xAA.toByte() })

        val json = """{
            "id": "test-id",
            "type": "public-key",
            "response": {
                "authenticatorData": "$authDataB64",
                "clientDataJSON": "$clientDataB64",
                "signature": "$sigB64"
            }
        }"""

        val result = GuardianUserOpBuilder.extractWebAuthnAssertion(json)
        assertNotNull(result)
        assertEquals(37, result!!.authenticatorData.size)
        assertTrue(result.clientDataJSON.decodeToString().contains("\"type\":\"webauthn.get\""))
        assertEquals(64, result.signature.size)
    }

    @Test
    fun `extractWebAuthnAssertion rejects registration cross-ceremony`() {
        val b64enc = Base64.getUrlEncoder().withoutPadding()
        val authDataB64 = b64enc.encodeToString(ByteArray(37) { it.toByte() })
        // clientDataJSON with type=webauthn.create (registration, not authentication)
        val clientDataB64 = b64enc.encodeToString(
            "{\"type\":\"webauthn.create\",\"origin\":\"https://mdaopay.app\"}".encodeToByteArray()
        )
        val sigB64 = b64enc.encodeToString(ByteArray(64) { 0xAA.toByte() })

        val json = """{
            "id": "test-id",
            "type": "public-key",
            "response": {
                "authenticatorData": "$authDataB64",
                "clientDataJSON": "$clientDataB64",
                "signature": "$sigB64"
            }
        }"""

        assertNull(GuardianUserOpBuilder.extractWebAuthnAssertion(json))
    }

    @Test
    fun `buildWebAuthnProof produces 160 bytes in SRM format`() {
        // S19/4.3b: 160 bytes = messageHash(32) + r(32) + s(32) + x(32) + y(32),
        // mirroring SocialRecoveryModule._verifyWebAuthn (L648-680):
        //   clientDataHash = SHA-256(clientDataJSON)
        //   messageHash     = SHA-256(authenticatorData || clientDataHash)
        val authenticatorData = ByteArray(37) { it.toByte() }
        val clientDataJSON = "{\"type\":\"webauthn.get\",\"origin\":\"android:apk-key-hash:test\"}".encodeToByteArray()
        val signature = ByteArray(64) { (it + 1).toByte() }
        val assertion = GuardianUserOpBuilder.WebAuthnAssertion(authenticatorData, clientDataJSON, signature)
        val keyData = GuardianKeyData(pubKeyXHex = "aa".repeat(32), pubKeyYHex = "bb".repeat(32))

        val proof = GuardianUserOpBuilder.buildWebAuthnProof(assertion, keyData)

        assertNotNull("Should build 160-byte proof from valid assertion", proof)
        assertEquals(160, proof!!.size)

        val md = java.security.MessageDigest.getInstance("SHA-256")
        val clientDataHash = md.digest(clientDataJSON)
        val expectedMessageHash = md.digest(authenticatorData + clientDataHash)

        assertArrayEquals("hash(32)", expectedMessageHash, proof.copyOfRange(0, 32))
        assertArrayEquals("r(32)", signature.copyOfRange(0, 32), proof.copyOfRange(32, 64))
        assertArrayEquals("s(32)", signature.copyOfRange(32, 64), proof.copyOfRange(64, 96))
        assertArrayEquals("x(32)", ByteArray(32) { 0xAA.toByte() }, proof.copyOfRange(96, 128))
        assertArrayEquals("y(32)", ByteArray(32) { 0xBB.toByte() }, proof.copyOfRange(128, 160))
    }

    @Test
    fun `buildWebAuthnProof returns null for non-64-byte signature`() {
        // DER-encoded or truncated signatures are not raw ES256 r||s — fail closed
        val assertion = GuardianUserOpBuilder.WebAuthnAssertion(
            authenticatorData = ByteArray(37),
            clientDataJSON = ByteArray(10),
            signature = ByteArray(32)
        )
        assertNull(
            GuardianUserOpBuilder.buildWebAuthnProof(
                assertion,
                GuardianKeyData(pubKeyXHex = "aa".repeat(32), pubKeyYHex = "bb".repeat(32))
            )
        )
    }

    @Test
    fun `extractP256PublicKey parses real CBOR attestation`() {
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
        val credId = ByteArray(16) { it.toByte() }
        val credIdLenBytes = byteArrayOf(0x00, 0x10) // 16

        val authData = rpIdHash + flags + signCount + aaguid + credIdLenBytes + credId + coseKeyBytes

        // Build attestation CBOR
        val attestationBytes = buildAttestationCbor("none", authData)

        val attestationB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(attestationBytes)

        val registrationJson = """{
            "id": "test-cred-id",
            "type": "public-key",
            "response": {
                "clientDataJSON": "eyJ0eXBlIjogIndlYmVyYXV0aC5jcmVhdGUifQ",
                "attestationObject": "$attestationB64"
            }
        }"""

        val keyData = GuardianUserOpBuilder.extractP256PublicKey(registrationJson)
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
        val mapHeader = byteArrayOf(0xA5.toByte()) // major 5, 5 items

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
        val mapHeader = byteArrayOf(0xA3.toByte()) // major 5, 3 items

        // fmt (text): "none"
        val fmtKey = byteArrayOf(0x63) + "fmt".encodeToByteArray()
        val fmtVal = byteArrayOf(0x64) + fmt.encodeToByteArray() // text(4): "none"

        // attStmt (map): {}
        val attStmtKey = byteArrayOf(0x67) + "attStmt".encodeToByteArray()
        val attStmtVal = byteArrayOf(0xA0.toByte()) // empty map

        // authData (bytes)
        val authDataKey = byteArrayOf(0x68) + "authData".encodeToByteArray()
        val authDataLen = authData.size
        val authDataHeader = when {
            authDataLen <= 23 -> byteArrayOf((0x40 + authDataLen).toByte()) // major 2
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
