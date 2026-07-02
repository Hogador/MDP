package com.mdaopay.paymaster.signing

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.web3j.crypto.*
import org.web3j.utils.Numeric
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.*
import java.math.BigInteger
import java.security.Security
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.spec.ECPublicKeySpec

class PaymasterSignerTest {

    // ── LocalPaymasterSigner ──

    @Test
    fun `local signer getAddress matches ECKeyPair`() {
        val keyPair = Keys.createEcKeyPair()
        val signer = LocalPaymasterSigner(keyPair)
        Assertions.assertEquals(Keys.getAddress(keyPair), signer.getAddress())
    }

    @Test
    fun `local signer signDigest produces recoverable signature`() {
        val keyPair = Keys.createEcKeyPair()
        val signer = LocalPaymasterSigner(keyPair)
        val digest = Hash.sha3("hello-world".toByteArray())
        val sig = signer.signDigest(digest)

        val recId = sig.v[0].toInt() - 27
        val recoveredKey = Sign.recoverFromSignature(
            recId,
            ECDSASignature(BigInteger(1, sig.r), BigInteger(1, sig.s)),
            digest
        )
        Assertions.assertNotNull(recoveredKey, "Recovered key must not be null")
        Assertions.assertEquals(keyPair.publicKey, recoveredKey, "Recovered key must match original public key")
    }

    @Test
    fun `local signer signature v is 27 or 28`() {
        val keyPair = Keys.createEcKeyPair()
        val signer = LocalPaymasterSigner(keyPair)
        val digest = Hash.sha3("v-test".toByteArray())
        val sig = signer.signDigest(digest)

        val v = sig.v[0].toInt() and 0xFF
        Assertions.assertTrue(v == 27 || v == 28, "v must be 27 or 28, got $v")
    }

    @Test
    fun `local signer r and s are 32 bytes`() {
        val keyPair = Keys.createEcKeyPair()
        val signer = LocalPaymasterSigner(keyPair)
        val digest = Hash.sha3("size-test".toByteArray())
        val sig = signer.signDigest(digest)

        Assertions.assertEquals(32, sig.r.size, "r must be 32 bytes")
        Assertions.assertEquals(32, sig.s.size, "s must be 32 bytes")
    }

    // ── DerKeyParser ──

    @Test
    fun `der key parser produces matching address`() {
        val keyPair = Keys.createEcKeyPair()
        val derBytes = createDerPublicKey(keyPair)
        val parsed = DerKeyParser.parseDerPublicKey(derBytes)
        Assertions.assertEquals(Keys.getAddress(keyPair), Keys.getAddress(parsed))
    }

    @Test
    fun `der key parser preserves public key`() {
        val keyPair = Keys.createEcKeyPair()
        val derBytes = createDerPublicKey(keyPair)
        val parsed = DerKeyParser.parseDerPublicKey(derBytes)
        Assertions.assertEquals(keyPair.publicKey, parsed.publicKey)
    }

    @Test
    fun `der key parser throws on invalid DER`() {
        Assertions.assertThrows(
            java.security.GeneralSecurityException::class.java
        ) {
            DerKeyParser.parseDerPublicKey(byteArrayOf(0x00, 0x01, 0x02))
        }
    }

    @Test
    fun `der key parser throws on wrong key data`() {
        // Random bytes that are not a valid DER-encoded public key
        val junk = byteArrayOf(0x30, 0x0A, 0x06.toByte(), 0x08, 0x2A, 0x86.toByte(), 0x48.toByte(), 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01)
        Assertions.assertThrows(Throwable::class.java) {
            DerKeyParser.parseDerPublicKey(junk)
        }
    }

    // ── KmsPaymasterSigner ──

    @Test
    fun `kms signer getAddress fetches from KMS and caches`() {
        val keyPair = Keys.createEcKeyPair()
        val derBytes = createDerPublicKey(keyPair)
        var fetchCount = 0
        val kmsClient = mockk<KmsClient>()

        every { kmsClient.getPublicKey(any<GetPublicKeyRequest>()) } answers {
            fetchCount++
            GetPublicKeyResponse.builder()
                .publicKey(SdkBytes.fromByteArray(derBytes))
                .build()
        }

        val signer = KmsPaymasterSigner(kmsClient, "test-key-id")
        val addr1 = signer.getAddress()
        val addr2 = signer.getAddress()

        Assertions.assertEquals(Keys.getAddress(keyPair), addr1)
        Assertions.assertEquals(addr1, addr2)
        Assertions.assertEquals(1, fetchCount, "getPublicKey should be called only once (cached)")
    }

    @Test
    fun `kms signer signDigest with mocked KMS produces recoverable signature`() {
        val keyPair = Keys.createEcKeyPair()
        val derBytes = createDerPublicKey(keyPair)
        val kmsClient = mockk<KmsClient>()

        every { kmsClient.getPublicKey(any<GetPublicKeyRequest>()) } returns
            GetPublicKeyResponse.builder()
                .publicKey(SdkBytes.fromByteArray(derBytes))
                .build()

        val digest = Hash.sha3("kms-test-digest".toByteArray())
        val localSig = Sign.signMessage(digest, keyPair, false)
        val derSig = encodeDerSignature(BigInteger(1, localSig.r), BigInteger(1, localSig.s))

        every { kmsClient.sign(any<SignRequest>()) } returns
            SignResponse.builder()
                .keyId("test-key-id")
                .signature(SdkBytes.fromByteArray(derSig))
                .signingAlgorithm(SigningAlgorithmSpec.ECDSA_SHA_256)
                .build()

        val signer = KmsPaymasterSigner(kmsClient, "test-key-id")
        val result = signer.signDigest(digest)

        val recId = result.v[0].toInt() - 27
        val recoveredKey = Sign.recoverFromSignature(
            recId,
            ECDSASignature(BigInteger(1, result.r), BigInteger(1, result.s)),
            digest
        )
        Assertions.assertNotNull(recoveredKey, "Recovered key must not be null")
        Assertions.assertEquals(keyPair.publicKey, recoveredKey, "Recovered key must match original public key")
    }

    @Test
    fun `kms signer signDigest with leading zero bytes in r`() {
        val keyPair = Keys.createEcKeyPair()
        val derBytes = createDerPublicKey(keyPair)
        val kmsClient = mockk<KmsClient>()

        every { kmsClient.getPublicKey(any<GetPublicKeyRequest>()) } returns
            GetPublicKeyResponse.builder()
                .publicKey(SdkBytes.fromByteArray(derBytes))
                .build()

        val digest = Hash.sha3("leading-zero-test".toByteArray())
        val localSig = Sign.signMessage(digest, keyPair, false)
        val r = BigInteger(1, localSig.r)
        val s = BigInteger(1, localSig.s)

        val derSig = encodeDerSignatureLeadingZero(r, s)

        every { kmsClient.sign(any<SignRequest>()) } returns
            SignResponse.builder()
                .keyId("test-key-id")
                .signature(SdkBytes.fromByteArray(derSig))
                .signingAlgorithm(SigningAlgorithmSpec.ECDSA_SHA_256)
                .build()

        val signer = KmsPaymasterSigner(kmsClient, "test-key-id")
        val result = signer.signDigest(digest)

        val recId = result.v[0].toInt() - 27
        val recoveredKey = Sign.recoverFromSignature(
            recId,
            ECDSASignature(BigInteger(1, result.r), BigInteger(1, result.s)),
            digest
        )
        Assertions.assertEquals(keyPair.publicKey, recoveredKey)
    }

    @Test
    fun `kms signer fails when signing algorithm is wrong`() {
        val keyPair = Keys.createEcKeyPair()
        val derBytes = createDerPublicKey(keyPair)
        val kmsClient = mockk<KmsClient>()

        every { kmsClient.getPublicKey(any<GetPublicKeyRequest>()) } returns
            GetPublicKeyResponse.builder()
                .publicKey(SdkBytes.fromByteArray(derBytes))
                .build()

        every { kmsClient.sign(any<SignRequest>()) } throws
            KmsException.builder().message("Invalid signing algorithm").build()

        val signer = KmsPaymasterSigner(kmsClient, "test-key-id")
        Assertions.assertThrows(KmsException::class.java) {
            signer.signDigest(Hash.sha3("fail".toByteArray()))
        }
    }

    // ── Test helpers ──

    companion object {
        /**
         * Create DER-encoded SubjectPublicKeyInfo (X.509) for a secp256k1 ECKeyPair.
         * Uses Bouncy Castle (available via web3j) to construct the encoding.
         */
        fun createDerPublicKey(keyPair: ECKeyPair): ByteArray {
            Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())

            val bcSpec = ECNamedCurveTable.getParameterSpec("secp256k1")
            val raw64 = Numeric.toBytesPadded(keyPair.publicKey, 64)
            val x = BigInteger(1, raw64.copyOfRange(0, 32))
            val y = BigInteger(1, raw64.copyOfRange(32, 64))

            val bcPoint = bcSpec.curve.createPoint(x, y)
            val bcPubSpec = org.bouncycastle.jce.spec.ECPublicKeySpec(bcPoint, bcSpec)

            val kf = java.security.KeyFactory.getInstance("EC", "BC")
            val pubKey = kf.generatePublic(bcPubSpec)
            return pubKey.encoded
        }

        /**
         * DER-encode an ECDSA signature (r, s) — matching AWS KMS output format.
         * SEQUENCE { INTEGER r, INTEGER s }
         */
        fun encodeDerSignature(r: BigInteger, s: BigInteger): ByteArray {
            val derR = encodeDerInteger(r.toByteArray())
            val derS = encodeDerInteger(s.toByteArray())
            val seqContent = derR + derS
            val seqLen = encodeDerLength(seqContent.size)

            return byteArrayOf(0x30) + seqLen + seqContent
        }

        /**
         * DER-encode with forced leading zero in r to test edge case.
         */
        fun encodeDerSignatureLeadingZero(r: BigInteger, s: BigInteger): ByteArray {
            val rBytesRaw = r.toByteArray()
            val rBytes = byteArrayOf(0x00) + rBytesRaw
            val derR = encodeDerInteger(rBytes)
            val derS = encodeDerInteger(s.toByteArray())
            val seqContent = derR + derS
            val seqLen = encodeDerLength(seqContent.size)

            return byteArrayOf(0x30) + seqLen + seqContent
        }

        private fun encodeDerInteger(valueBytes: ByteArray): ByteArray {
            var stripped = valueBytes
            var i = 0
            while (i < stripped.size - 1 && stripped[i] == 0x00.toByte() && stripped[i + 1] >= 0) {
                i++
            }
            stripped = if (i > 0) stripped.copyOfRange(i, stripped.size) else stripped

            if (stripped[0].toInt() and 0x80 != 0) {
                stripped = byteArrayOf(0x00) + stripped
            }

            val len = encodeDerLength(stripped.size)
            return byteArrayOf(0x02) + len + stripped
        }

        private fun encodeDerLength(length: Int): ByteArray {
            if (length < 128) {
                return byteArrayOf(length.toByte())
            }
            val bytes = ByteArray(4)
            var n = length
            var count = 0
            while (n > 0) {
                bytes[3 - count] = (n and 0xFF).toByte()
                n = n shr 8
                count++
            }
            val lenBytes = bytes.copyOfRange(4 - count, 4)
            return byteArrayOf((0x80 or count).toByte()) + lenBytes
        }
    }
}
