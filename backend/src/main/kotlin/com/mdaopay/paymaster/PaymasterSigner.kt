package com.mdaopay.paymaster

import org.web3j.crypto.ECDSASignature
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner

/**
 * Abstraction over paymaster signing — supports local ECKeyPair and KMS remote signing.
 * D-1 / F-129: production must use KmsPaymasterSigner, never LocalPaymasterSigner.
 */
interface PaymasterSigner {
    /**
     * Sign a 32-byte EIP-712 digest using ECDSA on secp256k1.
     * Returns (v, r, s) where v is 27 or 28 (Ethereum recovery ID + 27).
     * The caller provides digest without any hashing — implementation signs directly.
     */
    fun signDigest(digest: ByteArray): Triple<Byte, ByteArray, ByteArray>
}

/**
 * Local implementation using raw ECKeyPair.
 * F-001: uses Bouncy Castle ECDSASigner directly (NOT Sign.signMessage) to avoid double-hashing.
 * Production guard: AppConfig.init throws if !isTestnet && allowLocalSigning.
 */
class LocalPaymasterSigner(private val key: ECKeyPair) : PaymasterSigner {
    private val curveParams = Sign.CURVE_PARAMS
    private val domainParams = org.bouncycastle.crypto.params.ECDomainParameters(
        curveParams.curve, curveParams.g, curveParams.n, curveParams.h
    )

    override fun signDigest(digest: ByteArray): Triple<Byte, ByteArray, ByteArray> {
        val signer = ECDSASigner()
        val privKey = ECPrivateKeyParameters(key.privateKey, domainParams)
        signer.init(true, privKey)
        val (r, s) = signer.generateSignature(digest)
        val rBytes = Numeric.toBytesPadded(r, 32)
        val sBytes = Numeric.toBytesPadded(s, 32)
        val recId = (0..1).firstOrNull { rec ->
            try {
                val recoveredKey = Sign.recoverFromSignature(rec, ECDSASignature(r, s), digest)
                recoveredKey == key.publicKey
            } catch (_: RuntimeException) { false }
        } ?: error("Cannot determine recovery ID for signature")
        return Triple((27 + recId).toByte(), rBytes, sBytes)
    }
}

/**
 * GCP Cloud KMS implementation for production.
 * ponytail: requires google-cloud-kms dependency — add when deploying to production.
 * TODO: add implementation("com.google.cloud:google-cloud-kms:2.60.0") to build.gradle.kts
 *
class KmsPaymasterSigner(
    private val kmsClient: com.google.cloud.kms.v1.KeyManagementServiceClient,
    private val keyName: String,  // projects/{project}/locations/{location}/keyRings/{ring}/cryptoKeys/{key}/cryptoKeyVersions/{version}
) : PaymasterSigner {
    override fun signDigest(digest: ByteArray): Triple<Byte, ByteArray, ByteArray> {
        val req = com.google.cloud.kms.v1.AsymmetricSignRequest.newBuilder()
            .setName(keyName)
            .setDigest(com.google.cloud.kms.v1.Digest.newBuilder()
                .setSha256(com.google.protobuf.ByteString.copyFrom(digest)))
            .build()
        val resp = kmsClient.asymmetricSign(req)
        val derSig = resp.signature.toByteArray()
        // Parse DER-encoded ECDSA signature to (r, s)
        val (r, s) = parseDerSignature(derSig)
        val rBytes = Numeric.toBytesPadded(r, 32)
        val sBytes = Numeric.toBytesPadded(s, 32)
        // Determine recovery ID — KMS doesn't return it, so try both
        val recId = (0..1).firstOrNull { rec ->
            try {
                val pubKeyBytes = fetchPublicKeyBytes()
                val recovered = Sign.recoverFromSignature(rec, ECDSASignature(r, s), digest)
                recovered == pubKeyBytes
            } catch (_: RuntimeException) { false }
        } ?: error("Cannot determine recovery ID for KMS signature")
        return Triple((27 + recId).toByte(), rBytes, sBytes)
    }

    private fun fetchPublicKeyBytes(): ByteArray {
        val pubReq = com.google.cloud.kms.v1.GetPublicKeyRequest.newBuilder().setName(keyName).build()
        val pubResp = kmsClient.getPublicKey(pubReq)
        // PEM-encoded SPKI, extract the raw key bytes
        val pem = pubResp.pem
        val b64 = pem.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n", "").replace("\r", "")
        val der = java.util.Base64.getDecoder().decode(b64)
        // DER to raw EC public key (uncompressed 04 || x || y)
        return org.web3j.crypto.Keys.fromPem(pem).getOrThrow().publicKey
    }

    private fun parseDerSignature(der: ByteArray): Pair<BigInteger, BigInteger> {
        // Minimal DER parser for ECDSA signatures (SEQUENCE { INTEGER r, INTEGER s })
        var offset = 0
        if (der[offset++].toInt() != 0x30) error("Expected SEQUENCE")
        val seqLen = der[offset++].toInt() and 0xFF
        if (offset + seqLen > der.size) error("Invalid DER sequence length")
        if (der[offset++].toInt() != 0x02) error("Expected INTEGER r")
        val rLen = der[offset++].toInt() and 0xFF
        val rBytes = der.copyOfRange(offset, offset + rLen)
        offset += rLen
        if (der[offset++].toInt() != 0x02) error("Expected INTEGER s")
        val sLen = der[offset++].toInt() and 0xFF
        val sBytes = der.copyOfRange(offset, offset + sLen)
        return Pair(BigInteger(1, rBytes), BigInteger(1, sBytes))
    }
}
*/
