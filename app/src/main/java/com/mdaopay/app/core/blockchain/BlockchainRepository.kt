package com.mdaopay.app.core.blockchain

import com.mdaopay.app.core.common.Result
import com.mdaopay.app.core.common.safeCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.Transaction
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockchainRepository @Inject constructor(
    private val ethereumClient: EthereumClient
) {
    suspend fun getEthBalance(address: String): Result<BigDecimal> = withContext(Dispatchers.IO) {
        safeCall {
            val web3j = ethereumClient.getWeb3j()
            val wei = web3j.ethGetBalance(
                address, DefaultBlockParameterName.LATEST
            ).send().balance
            BigDecimal(wei, 18)
        }
    }

    suspend fun getUsdtBalance(address: String): Result<BigDecimal> {
        return fetchErc20Balance(address, NetworkConfig.USDT_CONTRACT)
    }

    suspend fun getMdaoBalance(address: String): Result<BigDecimal> {
        return fetchErc20Balance(address, NetworkConfig.MDAO_CONTRACT)
    }

    private suspend fun fetchErc20Balance(
        owner: String,
        token: String
    ): Result<BigDecimal> = withContext(Dispatchers.IO) {
        safeCall {
            val web3j = ethereumClient.getWeb3j()
            val function = Function(
                FUNC_BALANCE_OF,
                listOf(Address(owner)),
                listOf(TypeReference.create(Uint256::class.java))
            )
            val data = FunctionEncoder.encode(function)
            val response = web3j.ethCall(
                Transaction.createEthCallTransaction(owner, token, data),
                DefaultBlockParameterName.LATEST
            ).send()

            if (response.hasError()) {
                throw BlockchainException(response.error.message)
            }

            val decoded = FunctionReturnDecoder.decode(
                response.value,
                function.outputParameters
            )
            if (decoded.isEmpty()) {
                throw BlockchainException("Empty balance response")
            }

            val rawBalance = (decoded[0] as Uint256).value
            BigDecimal(rawBalance, TOKEN_DECIMALS)
        }
    }

    private class BlockchainException(message: String) : Exception(message)

    companion object {
        private const val FUNC_BALANCE_OF = "balanceOf"
        private const val TOKEN_DECIMALS = 18
    }
}
