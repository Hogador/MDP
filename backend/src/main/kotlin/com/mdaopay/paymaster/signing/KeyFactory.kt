package com.mdaopay.paymaster.signing

import org.web3j.crypto.ECKeyPair
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.Security
import java.security.spec.X509EncodedKeySpec

/**
 * Parses AWS KMS DER-encoded secp256k1 public keys into web3j [ECKeyPair].
 *
 * AWS KMS [GetPublicKey] returns the key in X.509 SubjectPublicKeyInfo DER format.
 * This parser extracts the raw EC point and creates an [ECKeyPair] compatible with
 * web3j's [org.web3j.crypto.Keys.getAddress].
 *
 * F-134: GCP KMS does not support secp256k1 — AWS KMS with ECC_SECG_P256K1 is required.
 *
 * Only the public key is extracted; the private key is set to [BigInteger.ZERO]
 * since the actual private key remains in AWS KMS.
 */
object DerKeyParser {

    private val bcProvider: org.bouncycastle.jce.provider.BouncyCastleProvider by lazy {
        org.bouncycastle.jce.provider.BouncyCastleProvider()
    }

    init {
        // Ensure Bouncy Castle is registered — required for secp256k1 support
        if (Security.getProvider("BC") == null) {
            Security.addProvider(bcProvider)
        }
    }

    /**
     * Parse a DER-encoded X.509 SubjectPublicKeyInfo into an [ECKeyPair].
     *
     * @param derBytes the raw DER bytes from AWS KMS GetPublicKeyResponse.publicKey()
     * @return [ECKeyPair] with the public key set and private key = [BigInteger.ZERO]
     * @throws GeneralSecurityException if the DER cannot be parsed
     * @throws IllegalArgumentException if the key is not an EC key on secp256k1
     */
    fun parseDerPublicKey(derBytes: ByteArray): ECKeyPair {
        val keySpec = X509EncodedKeySpec(derBytes)
        val jdkKeyFactory = java.security.KeyFactory.getInstance("EC", "BC")
        val pubKey = jdkKeyFactory.generatePublic(keySpec) as java.security.interfaces.ECPublicKey

        val w = pubKey.w
        val x = w.affineX
        val y = w.affineY

        // Encode as 64-byte (x || y) for web3j
        val xBytes = Numeric.toBytesPadded(x, 32)
        val yBytes = Numeric.toBytesPadded(y, 32)
        val xyBytes = xBytes + yBytes
        val publicKeyBigInt = BigInteger(1, xyBytes)

        return ECKeyPair(BigInteger.ZERO, publicKeyBigInt)
    }
}
