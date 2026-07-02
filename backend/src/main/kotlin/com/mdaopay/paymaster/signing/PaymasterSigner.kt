package com.mdaopay.paymaster.signing

import org.web3j.crypto.Sign

/**
 * Abstraction over paymaster signing — supports local ECKeyPair and AWS KMS remote signing.
 *
 * F-129 / D-1: production must use [KmsPaymasterSigner], never [LocalPaymasterSigner].
 * F-134: GCP KMS does NOT support secp256k1 — AWS KMS with ECC_SECG_P256K1 is required.
 */
interface PaymasterSigner {
    /**
     * Sign a 32-byte EIP-712 digest using ECDSA on secp256k1.
     * Returns [Sign.SignatureData] where:
     * - v is a 1-byte array containing (recoveryId + 27), i.e. 27 or 28
     * - r is a 32-byte array (big-endian)
     * - s is a 32-byte array (big-endian)
     *
     * @param digest the 32-byte digest to sign (must NOT be hashed again by implementation)
     */
    fun signDigest(digest: ByteArray): Sign.SignatureData

    /**
     * Return the Ethereum address (0x-prefixed, checksummed) of the signer's public key.
     */
    fun getAddress(): String
}
