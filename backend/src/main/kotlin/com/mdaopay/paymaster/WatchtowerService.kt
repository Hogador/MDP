package com.mdaopay.paymaster

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import org.web3j.abi.*
import org.web3j.abi.datatypes.*
import org.web3j.abi.datatypes.generated.*
import com.mdaopay.paymaster.util.LogSanitizer
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.DefaultBlockParameterNumber
import org.web3j.protocol.core.methods.request.EthFilter
import org.web3j.protocol.core.methods.response.Log
import org.web3j.utils.Numeric
import java.math.BigInteger

private val watchLog = LoggerFactory.getLogger("Watchtower")

data class WatchtowerConfig(
    val recoveryModuleAddress: String,
    val webhookUrl: String? = null,
    val pollIntervalSec: Long = 60,
    val balanceDropThreshold: Double = 0.5,
)

data class RecoveryEvent(
    val wallet: String,
    val newPasskeyHash: String,
    val deadline: BigInteger,
    val nonce: BigInteger,
)

class WatchtowerService(
    private val config: WatchtowerConfig,
    private val web3j: Web3j,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeRecoveries = mutableMapOf<String, RecoveryEvent>()
    private val watchedBalances = mutableMapOf<String, BigInteger>()
    private var lastPollBlock = BigInteger.ZERO
    private val httpClient = HttpClient(CIO) { expectSuccess = false }

    private val recoveryInitiatedTopic = EventEncoder.encode(Event(
        "RecoveryInitiated",
        listOf(
            TypeReference.create(Address::class.java, true),
            TypeReference.create(Bytes32::class.java, true),
            TypeReference.create(Uint256::class.java),
            TypeReference.create(Uint256::class.java),
        ),
    ))
    private val recoveryExecutedTopic = EventEncoder.encode(Event(
        "RecoveryExecutedEv",
        listOf(
            TypeReference.create(Address::class.java, true),
            TypeReference.create(Bytes32::class.java, true),
        ),
    ))

    fun start() {
        scope.launch {
            try {
                lastPollBlock = web3j.ethBlockNumber().send().blockNumber
                watchLog.info("Watchtower started from block {}", lastPollBlock)
            } catch (e: Exception) {
                watchLog.warn("Failed to get initial block reason={}", LogSanitizer.sanitizeError(e))
                if (watchLog.isDebugEnabled) watchLog.debug("Failed to get initial block details", e)
            }

            while (isActive) {
                try {
                    pollEvents()
                    pollActiveRecoveries()
                    pollBalances()
                } catch (e: Exception) {
                    watchLog.error("Watchtower poll error reason={}", LogSanitizer.sanitizeError(e))
                    if (watchLog.isDebugEnabled) watchLog.debug("Watchtower poll error details", e)
                }
                delay(config.pollIntervalSec * 1000)
            }
        }
        watchLog.info("Watchtower scheduled every {}s", config.pollIntervalSec)
    }

    fun stop() {
        scope.cancel()
        httpClient.close()
        watchLog.info("Watchtower stopped")
    }

    fun watchWallet(wallet: String) {
        watchedBalances.putIfAbsent(wallet.lowercase(), BigInteger.ZERO)
    }

    private suspend fun pollEvents() {
        try {
            val currentBlock = web3j.ethBlockNumber().send().blockNumber
            if (currentBlock <= lastPollBlock) return

            val filter = EthFilter(
                DefaultBlockParameterNumber(lastPollBlock.add(BigInteger.ONE)),
                DefaultBlockParameterNumber(currentBlock),
                config.recoveryModuleAddress,
            )

            val logs = web3j.ethGetLogs(filter).send().logs
            for (logResult in logs) {
                val log = logResult as? Log ?: continue
                when (log.topics[0]) {
                    recoveryInitiatedTopic -> handleRecoveryInitiated(log)
                    recoveryExecutedTopic -> handleRecoveryExecuted(log)
                }
            }
            lastPollBlock = currentBlock
        } catch (e: Exception) {
            watchLog.warn("Event poll failed reason={}", LogSanitizer.sanitizeError(e))
            if (watchLog.isDebugEnabled) watchLog.debug("Event poll failed details", e)
        }
    }

    private fun handleRecoveryInitiated(log: Log) {
        try {
            val wallet = "0x" + log.topics[1].substring(26)
            val newKeyHash = log.topics[2]
            val deadline = Numeric.toBigInt(log.data.substring(2, 66))
            val nonce = Numeric.toBigInt(log.data.substring(66, 130))

            val event = RecoveryEvent(wallet, newKeyHash, deadline, nonce)
            activeRecoveries[wallet] = event

            watchLog.warn("Recovery initiated wallet={} nonce={} deadline={}", LogSanitizer.sanitizeAddress(wallet), nonce, deadline)
            notifyWebhook("recovery_initiated", mapOf(
                "wallet" to wallet,
                "deadline" to deadline.toString(),
                "nonce" to nonce.toString(),
            ))
        } catch (e: Exception) {
            watchLog.warn("Failed to parse RecoveryInitiated reason={}", LogSanitizer.sanitizeError(e))
            if (watchLog.isDebugEnabled) watchLog.debug("Failed to parse RecoveryInitiated details", e)
        }
    }

    private fun handleRecoveryExecuted(log: Log) {
        val wallet = "0x" + log.topics[1].substring(26)
        activeRecoveries.remove(wallet)
        watchLog.warn("Recovery executed wallet={}", LogSanitizer.sanitizeAddress(wallet))
        notifyWebhook("recovery_executed", mapOf("wallet" to wallet))
    }

    private suspend fun pollActiveRecoveries() {
        if (activeRecoveries.isEmpty()) return
        val iterator = activeRecoveries.entries.iterator()
        while (iterator.hasNext()) {
            val (wallet, _) = iterator.next()
            try {
                val function = Function(
                    "getRecoveryRequest",
                    listOf(Address(wallet)),
                    listOf(
                        TypeReference.create(DynamicBytes::class.java),
                        TypeReference.create(Uint256::class.java),
                        TypeReference.create(Uint256::class.java),
                        TypeReference.create(Uint256::class.java),
                        TypeReference.create(Bool::class.java),
                        TypeReference.create(Bool::class.java),
                        TypeReference.create(Uint256::class.java),
                        TypeReference.create(Uint256::class.java),
                    ),
                )
                val encoded = FunctionEncoder.encode(function)
                val call = org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(null, config.recoveryModuleAddress, encoded)
                val walletShort = LogSanitizer.sanitizeAddress(wallet)
                val response = web3j.ethCall(call, DefaultBlockParameterName.LATEST).send()
                if (response.hasError()) {
                    watchLog.warn("getRecoveryRequest failed wallet={}", walletShort)
                    iterator.remove()
                    continue
                }
                val result = FunctionReturnDecoder.decode(response.value, function.outputParameters)
                if (result.size < 8) {
                    iterator.remove()
                    continue
                }
                val startedAt = (result[1] as Uint256).value
                val vetoed = (result[4] as Bool).value
                val executed = (result[5] as Bool).value

                if (startedAt == BigInteger.ZERO || vetoed || executed) {
                    watchLog.info("Recovery resolved wallet={} (vetoed={}, executed={})", walletShort, vetoed, executed)
                    iterator.remove()
                } else {
                    val approvals = (result[2] as Uint256).value
                    val deadline = (result[6] as Uint256).value
                    if (approvals >= BigInteger.valueOf(3)) {
                        watchLog.info("Recovery approvals threshold reached wallet={} approvals={}", walletShort, approvals)
                    }
                    notifyWebhook("recovery_pending", mapOf(
                        "wallet" to wallet,
                        "approvals" to approvals.toString(),
                        "deadline" to deadline.toString(),
                    ))
                }
            } catch (e: Exception) {
                watchLog.warn("Failed to poll recovery wallet={} reason={}", LogSanitizer.sanitizeAddress(wallet), LogSanitizer.sanitizeError(e))
                if (watchLog.isDebugEnabled) watchLog.debug("Failed to poll recovery details wallet={}", wallet, e)
            }
        }
    }

    private suspend fun pollBalances() {
        for ((wallet, lastBalance) in watchedBalances.toMap()) {
            try {
                val balance = web3j.ethGetBalance(wallet, DefaultBlockParameterName.LATEST).send().balance
                if (lastBalance > BigInteger.ZERO && balance < lastBalance) {
                    val droppedFraction = 1.0 - balance.toDouble() / lastBalance.toDouble()
                    if (droppedFraction >= config.balanceDropThreshold) {
                        watchLog.warn("Large balance drop wallet={} ({}%)", LogSanitizer.sanitizeAddress(wallet), (droppedFraction * 100).toInt())
                        notifyWebhook("balance_drop", mapOf(
                            "wallet" to wallet,
                            "previous" to lastBalance.toString(),
                            "current" to balance.toString(),
                            "dropPercent" to (droppedFraction * 100).toInt().toString(),
                        ))
                    }
                }
                watchedBalances[wallet] = balance
            } catch (e: Exception) {
                watchLog.warn("Balance poll failed reason={}", LogSanitizer.sanitizeError(e))
                if (watchLog.isDebugEnabled) watchLog.debug("Balance poll failed details", e)
            }
        }
    }

    private fun notifyWebhook(event: String, data: Map<String, String>) {
        val url = config.webhookUrl ?: return
        val payload = JsonObject(
            mapOf("event" to JsonPrimitive(event)) +
            data.mapValues { JsonPrimitive(it.value) }
        )
        // F-053: use class-level scope with SupervisorJob + withTimeout
        scope.launch {
            try {
                withTimeout(5000) {
                    httpClient.post(url) {
                        contentType(ContentType.Application.Json)
                        setBody(payload.toString())
                    }
                }
                watchLog.info("Webhook sent: $event")
            } catch (e: Exception) {
                watchLog.warn("Webhook request failed event={} reason={}", event, LogSanitizer.sanitizeError(e))
                if (watchLog.isDebugEnabled) watchLog.debug("Webhook request failed details", e)
            }
        }
    }
}
