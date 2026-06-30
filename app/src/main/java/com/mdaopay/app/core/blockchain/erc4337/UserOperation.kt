package com.mdaopay.app.core.blockchain.erc4337

import org.web3j.crypto.Hash
import org.web3j.utils.Numeric
import java.io.ByteArrayOutputStream
import java.math.BigInteger

data class UserOperation(
    val sender: String,
    val nonce: BigInteger,
    val initCode: ByteArray,
    val callData: ByteArray,
    val callGasLimit: BigInteger,
    val verificationGasLimit: BigInteger,
    val preVerificationGas: BigInteger,
    val maxFeePerGas: BigInteger,
    val maxPriorityFeePerGas: BigInteger,
    val paymasterAndData: ByteArray
) {
    var signature: ByteArray = ByteArray(0)

    fun computeUserOpHash(entryPoint: String, chainId: Long): ByteArray {
        val initCodeHash = Hash.sha3(initCode)
        val callDataHash = Hash.sha3(callData)
        val paymasterAndDataHash = Hash.sha3(paymasterAndData)

        val packed = ByteArrayOutputStream()
        packed.write(padTo32(Numeric.toBigInt(sender)))
        packed.write(padTo32(nonce))
        packed.write(initCodeHash)
        packed.write(callDataHash)
        packed.write(padTo32(callGasLimit))
        packed.write(padTo32(verificationGasLimit))
        packed.write(padTo32(preVerificationGas))
        packed.write(padTo32(maxFeePerGas))
        packed.write(padTo32(maxPriorityFeePerGas))
        packed.write(paymasterAndDataHash)

        val packedHash = Hash.sha3(packed.toByteArray())

        val final = ByteArrayOutputStream()
        final.write(packedHash)
        final.write(padTo32(Numeric.toBigInt(entryPoint)))
        final.write(padTo32(BigInteger.valueOf(chainId)))

        return Hash.sha3(final.toByteArray())
    }

    fun toMap(): Map<String, Any> = mapOf(
        "sender" to sender,
        "nonce" to Numeric.toHexStringWithPrefix(nonce),
        "initCode" to Numeric.toHexString(initCode),
        "callData" to Numeric.toHexString(callData),
        "callGasLimit" to Numeric.toHexStringWithPrefix(callGasLimit),
        "verificationGasLimit" to Numeric.toHexStringWithPrefix(verificationGasLimit),
        "preVerificationGas" to Numeric.toHexStringWithPrefix(preVerificationGas),
        "maxFeePerGas" to Numeric.toHexStringWithPrefix(maxFeePerGas),
        "maxPriorityFeePerGas" to Numeric.toHexStringWithPrefix(maxPriorityFeePerGas),
        "paymasterAndData" to Numeric.toHexString(paymasterAndData),
        "signature" to Numeric.toHexString(signature)
    )

    companion object {
        private fun padTo32(value: BigInteger): ByteArray = Numeric.toBytesPadded(value, 32)
        private fun padTo32(value: ByteArray): ByteArray {
            if (value.size == 32) return value
            val padded = ByteArray(32)
            val offset = 32 - value.size.coerceAtMost(32)
            value.copyInto(padded, offset, 0, value.size.coerceAtMost(32))
            return padded
        }
    }
}
