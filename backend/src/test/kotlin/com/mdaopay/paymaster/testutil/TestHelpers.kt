package com.mdaopay.paymaster.testutil

import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import java.math.BigInteger

object TestHelpers {
    fun createTestKeyPair(): ECKeyPair {
        return Keys.createEcKeyPair()
    }

    fun addressFromKeyPair(keyPair: ECKeyPair): String {
        return Keys.toChecksumAddress("0x" + Keys.getAddress(keyPair))
    }

    fun signNicknameMessage(nickname: String, address: String, nonce: Long, keyPair: ECKeyPair): String {
        val message = "Register nickname $nickname for ${address.lowercase()} (nonce: $nonce)"
        val sigData = Sign.signPrefixedMessage(message.encodeToByteArray(), keyPair)
        val canonical = canonicalizeS(sigData)
        val combined = ByteArray(canonical.r.size + canonical.s.size + canonical.v.size).apply {
            canonical.r.copyInto(this, 0)
            canonical.s.copyInto(this, canonical.r.size)
            canonical.v.copyInto(this, canonical.r.size + canonical.s.size)
        }
        return Numeric.toHexString(combined)
    }

    private fun canonicalizeS(sigData: Sign.SignatureData): Sign.SignatureData {
        val s = Numeric.toBigInt(sigData.s)
        val halfOrder = BigInteger("7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF5D576E7357A4501DDFE92F46681B20A0", 16)
        if (s > halfOrder) {
            val curveOrder = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)
            val newS = curveOrder.subtract(s)
            val newSBytes = Numeric.toBytesPadded(newS, 32)
            return Sign.SignatureData(sigData.v, sigData.r, newSBytes)
        }
        return sigData
    }
}
