package com.mdaopay.paymaster.signing

import org.web3j.crypto.ECDSASignature
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.GetPublicKeyRequest
import software.amazon.awssdk.services.kms.model.MessageType
import software.amazon.awssdk.services.kms.model.SignRequest
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec
import java.math.BigInteger

/**
 * AWS KMS implementation of [PaymasterSigner] for production.
 *
 * F-129: KMS remote signing for paymaster private key — prevents heap-dump / env-var leakage.
 * F-134: Uses AWS KMS with `ECC_SECG_P256K1` (GCP KMS does NOT support secp256k1).
 *
 * @param kmsClient the AWS KMS client (configured with region and credentials externally)
 * @param keyId the AWS KMS key identifier (key ID, key ARN, or alias ARN)
 */
class KmsPaymasterSigner(
    private val kmsClient: KmsClient,
    private val keyId: String,
) : PaymasterSigner {

    private var cachedAddress: String? = null
    private var cachedKeyPair: org.web3j.crypto.ECKeyPair? = null

    override fun signDigest(digest: ByteArray): Sign.SignatureData {
        val request = SignRequest.builder()
            .keyId(keyId)
            .message(SdkBytes.fromByteArray(digest))
            .messageType(MessageType.DIGEST)
            .signingAlgorithm(SigningAlgorithmSpec.ECDSA_SHA_256)
            .build()
        val response = kmsClient.sign(request)

        // AWS KMS returns DER-encoded ECDSA signature — decode to (r, s)
        val (r, s) = decodeDerSignature(response.signature().asByteArray())

        // Compute recovery ID by trying 0..3 and matching recovered key to our address
        val recId = recoverRecId(digest, ECDSASignature(r, s))

        return Sign.SignatureData(
            byteArrayOf((recId + 27).toByte()),
            Numeric.toBytesPadded(r, 32),
            Numeric.toBytesPadded(s, 32),
        )
    }

    override fun getAddress(): String {
        if (cachedAddress == null) {
            fetchPublicKey()
        }
        return cachedAddress!!
    }

    // ── Private helpers ──

    /**
     * Fetch the public key from AWS KMS and cache [cachedAddress] and [cachedKeyPair].
     */
    private fun fetchPublicKey() {
        val response = kmsClient.getPublicKey(
            GetPublicKeyRequest.builder().keyId(keyId).build()
        )
        val pubKeyBytes = response.publicKey().asByteArray()
        val keyPair = DerKeyParser.parseDerPublicKey(pubKeyBytes)
        cachedKeyPair = keyPair
        cachedAddress = Keys.getAddress(keyPair)
    }

    /**
     * Determine the recovery ID (0..3) by testing which recId recovers
     * the signature back to our known public key address.
     *
     * Only recId 0 and 1 are valid for secp256k1, but we check 0..3 for safety.
     */
    private fun recoverRecId(digest: ByteArray, sig: ECDSASignature): Int {
        for (i in 0..3) {
            try {
                val recoveredKey = Sign.recoverFromSignature(i, sig, digest)
                if (recoveredKey != null) {
                    val recoveredAddress = Keys.getAddress(recoveredKey)
                    if (recoveredAddress.equals(getAddress(), ignoreCase = true)) {
                        return i
                    }
                }
            } catch (_: RuntimeException) {
                // Skip invalid recId
            }
        }
        throw IllegalStateException(
            "Cannot determine recovery ID for KMS signature — none of recId 0..3 matched"
        )
    }

    /**
     * Decode a DER-encoded ECDSA signature to (r, s) BigIntegers.
     *
     * DER format for ECDSA signatures:
     *   SEQUENCE {
     *     INTEGER r
     *     INTEGER s
     *   }
     *
     * Each INTEGER may have a leading 0x00 byte if the high bit is set.
     *
     * @param der DER-encoded signature bytes from AWS KMS
     * @return Pair(r, s) as BigIntegers
     */
    private fun decodeDerSignature(der: ByteArray): Pair<BigInteger, BigInteger> {
        var offset = 0

        // SEQUENCE tag
        require(der[offset].toInt() and 0xFF == 0x30) {
            "Expected DER SEQUENCE (0x30), got 0x${(der[offset].toInt() and 0xFF).toString(16)}"
        }
        offset++

        // SEQUENCE length (could be multi-byte)
        val seqLen = readDerLength(der, offset).let { (len, consumed) ->
            offset += consumed
            len
        }
        require(offset + seqLen <= der.size) {
            "DER SEQUENCE length $seqLen exceeds buffer size ${der.size - offset}"
        }

        // INTEGER r
        require(der[offset].toInt() and 0xFF == 0x02) {
            "Expected INTEGER tag (0x02) for r, got 0x${(der[offset].toInt() and 0xFF).toString(16)}"
        }
        offset++
        val rLen = readDerLength(der, offset).let { (len, consumed) ->
            offset += consumed
            len
        }
        val rBytes = der.copyOfRange(offset, offset + rLen)
        offset += rLen

        // INTEGER s
        require(der[offset].toInt() and 0xFF == 0x02) {
            "Expected INTEGER tag (0x02) for s, got 0x${(der[offset].toInt() and 0xFF).toString(16)}"
        }
        offset++
        val sLen = readDerLength(der, offset).let { (len, consumed) ->
            offset += consumed
            len
        }
        val sBytes = der.copyOfRange(offset, offset + sLen)

        val r = BigInteger(1, rBytes) // unsigned
        val s = BigInteger(1, sBytes) // unsigned

        return Pair(r, s)
    }

    /**
     * Read a DER length value at [offset]. Returns (length, bytesConsumed).
     * Handles both short form (1 byte, < 128) and long form (multi-byte).
     */
    private fun readDerLength(der: ByteArray, offset: Int): Pair<Int, Int> {
        val first = der[offset].toInt() and 0xFF
        if (first < 0x80) {
            // Short form
            return Pair(first, 1)
        }
        // Long form
        val numBytes = first and 0x7F
        require(numBytes in 1..4) { "DER length too large: $numBytes bytes" }
        var length = 0
        for (i in 1..numBytes) {
            length = (length shl 8) or (der[offset + i].toInt() and 0xFF)
        }
        return Pair(length, 1 + numBytes)
    }
}
