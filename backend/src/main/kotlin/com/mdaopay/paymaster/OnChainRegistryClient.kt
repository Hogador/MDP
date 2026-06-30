package com.mdaopay.paymaster

import org.slf4j.LoggerFactory
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Bytes32
import org.web3j.crypto.Hash
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.utils.Numeric

class OnChainRegistryClient(
    private val registryAddress: String,
    private val web3j: Web3j,
) {
    private val log = LoggerFactory.getLogger(OnChainRegistryClient::class.java)

    fun isIdentityRegistered(address: String): Boolean {
        val identityHash = Hash.sha3(Numeric.hexStringToByteArray(address))
        return resolveIdentity(identityHash) != null
    }

    fun resolveIdentity(identityHash: ByteArray): String? {
        try {
            val function = Function(
                "resolve",
                listOf(Bytes32(identityHash)),
                listOf(TypeReference.create(Address::class.java)),
            )
            val encoded = FunctionEncoder.encode(function)
            val call = Transaction.createEthCallTransaction(null, registryAddress, encoded)
            val response = web3j.ethCall(call, DefaultBlockParameterName.LATEST).send()

            if (response.hasError()) {
                return null
            }

            val result = FunctionReturnDecoder.decode(
                response.value,
                function.outputParameters
            )
            if (result.isNotEmpty()) {
                val addr = (result[0] as Address).value
                if (addr != "0x0000000000000000000000000000000000000000") {
                    return addr
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to resolve identity {}: {}",
                Numeric.toHexString(identityHash), e.message)
        }
        return null
    }
}
