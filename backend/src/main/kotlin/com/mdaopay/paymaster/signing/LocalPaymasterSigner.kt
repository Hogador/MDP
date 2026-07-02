package com.mdaopay.paymaster.signing

import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign

/**
 * Local implementation using raw [ECKeyPair] in memory.
 *
 * Uses [Sign.signMessage] with `needToHash=false` to avoid double-hashing
 * (the digest is already a 32-byte EIP-712 hash).
 *
 * F-111: [AppConfig.allowLocalSigning] throws in production if this is used.
 * F-129: this implementation is ONLY for dev/testnets — production requires [KmsPaymasterSigner].
 */
class LocalPaymasterSigner(private val keyPair: ECKeyPair) : PaymasterSigner {

    override fun signDigest(digest: ByteArray): Sign.SignatureData {
        return Sign.signMessage(digest, keyPair, false)
    }

    override fun getAddress(): String {
        return Keys.getAddress(keyPair)
    }
}
