package com.mdaopay.paymaster

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import org.web3j.abi.*
import org.web3j.abi.datatypes.*
import org.web3j.abi.datatypes.generated.*
import org.web3j.crypto.Hash
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterNumber
import org.web3j.protocol.core.methods.request.EthFilter
import org.web3j.protocol.core.methods.response.Log
import org.web3j.utils.Numeric
import com.mdaopay.paymaster.util.LogSanitizer
import java.math.BigInteger
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import javax.sql.DataSource

private val idxLog = LoggerFactory.getLogger("EventIndexer")

/** Configuration for a single indexed contract. */
data class IndexedContract(
    val address: String,
    val events: List<IndexedEvent>,
)

/** Name + topic hash + indexed param names for one event type. */
data class IndexedEvent(
    val name: String,
    val topic: String,
    /** Number of indexed params (topics[1..N]). 0 for no-index events. */
    val indexedCount: Int,
    /** Names of non-indexed params for JSON serialization. */
    val paramNames: List<String>,
    /** Index of the "wallet"-like param (0-based across indexed+non-indexed), or -1. */
    val walletParamIdx: Int = -1,
)

/** Row stored in onchain_events table. */
data class OnChainEvent(
    val chainId: Long,
    val contractAddress: String,
    val eventName: String,
    val blockNumber: Long,
    val txHash: String,
    val logIndex: Int,
    val wallet: String?,
    val params: JsonObject,
)

class EventIndexer(
    private val contracts: List<IndexedContract>,
    private val web3j: Web3j,
    private val dataSource: DataSource,
    private val chainId: Long,
    private val pollIntervalSec: Long = 30,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lastPolled = mutableMapOf<String, BigInteger>() // contract -> last block

    fun start() {
        scope.launch {
            // Initialize from current block
            try {
                val current = web3j.ethBlockNumber().send().blockNumber
                contracts.forEach { lastPolled[it.address.lowercase()] = current }
                idxLog.info("EventIndexer started from block {} for {} contracts", current, contracts.size)
            } catch (e: Exception) {
                idxLog.warn("Failed to get initial block reason={}", LogSanitizer.sanitizeError(e))
                if (idxLog.isDebugEnabled) idxLog.debug("Failed to get initial block details", e)
            }

            while (isActive) {
                try {
                    pollAll()
                } catch (e: Exception) {
                    idxLog.error("Indexer poll error reason={}", LogSanitizer.sanitizeError(e))
                    if (idxLog.isDebugEnabled) idxLog.debug("Indexer poll error details", e)
                }
                delay(pollIntervalSec * 1000)
            }
        }
        idxLog.info("EventIndexer scheduled every {}s for {} contracts", pollIntervalSec, contracts.size)
    }

    fun stop() {
        scope.cancel()
        idxLog.info("EventIndexer stopped")
    }

    private suspend fun pollAll() {
        val currentBlock: BigInteger
        try {
            currentBlock = web3j.ethBlockNumber().send().blockNumber
        } catch (e: Exception) {
            idxLog.warn("Failed to get current block reason={}", LogSanitizer.sanitizeError(e))
            return
        }

        for (contract in contracts) {
            val addr = contract.address.lowercase()
            val fromBlock = lastPolled[addr] ?: currentBlock
            if (currentBlock <= fromBlock) continue

            try {
                val topics = contract.events.map { it.topic }.toSet()
                val filter = EthFilter(
                    DefaultBlockParameterNumber(fromBlock.add(BigInteger.ONE)),
                    DefaultBlockParameterNumber(currentBlock),
                    contract.address,
                )
                // Filter by any of our event topics (Web3j EthFilter supports topics list)
                // ponytail: use eth_getLogs with address filter only, then match topics in code
                val logs = web3j.ethGetLogs(filter).send().logs
                val parsed = mutableListOf<OnChainEvent>()

                for (logResult in logs) {
                    val log = logResult as? Log ?: continue
                    if (log.topics.isEmpty()) continue
                    val eventTopic = log.topics[0]
                    val eventDef = contract.events.find { it.topic == eventTopic } ?: continue
                    parseEvent(log, eventDef)?.let { parsed.add(it) }
                }

                if (parsed.isNotEmpty()) {
                    storeBatch(parsed)
                    idxLog.info("Indexed {} events from {}", parsed.size, LogSanitizer.sanitizeAddress(contract.address))
                }

                lastPolled[addr] = currentBlock
            } catch (e: Exception) {
                idxLog.warn("Poll failed for {} reason={}",
                    LogSanitizer.sanitizeAddress(contract.address),
                    LogSanitizer.sanitizeError(e))
                if (idxLog.isDebugEnabled) idxLog.debug("Poll failed details for {}", contract.address, e)
            }
        }
    }

    private fun parseEvent(log: Log, eventDef: IndexedEvent): OnChainEvent? {
        return try {
            val topics = log.topics
            val data = log.data

            // Parse indexed params from topics[1..N]
            val indexedParams = mutableListOf<String>()
            for (i in 1..eventDef.indexedCount) {
                if (i < topics.size) {
                    indexedParams.add(topics[i])
                } else {
                    indexedParams.add("")
                }
            }

            // Parse non-indexed params from data via FunctionReturnDecoder
            val allParamNames = eventDef.paramNames
            val nonIndexedCount = allParamNames.size - eventDef.indexedCount

            val nonIndexedValues = if (nonIndexedCount > 0 && data.isNotEmpty() && data != "0x") {
                val types = allParamNames.drop(eventDef.indexedCount).map { name ->
                    when {
                        name.contains("uint", ignoreCase = true) -> TypeReference.create(Uint256::class.java)
                        name.contains("address", ignoreCase = true) || name.contains("from", ignoreCase = true) -> TypeReference.create(Address::class.java)
                        name.contains("bytes32", ignoreCase = true) || name.contains("hash", ignoreCase = true) -> TypeReference.create(Bytes32::class.java)
                        name.contains("bool", ignoreCase = true) -> TypeReference.create(Bool::class.java)
                        name.contains("bytes", ignoreCase = true) -> TypeReference.create(DynamicBytes::class.java)
                        name.contains("string", ignoreCase = true) || name.contains("description", ignoreCase = true) -> TypeReference.create(Utf8String::class.java)
                        name.contains("uint8", ignoreCase = true) || name.contains("uint16", ignoreCase = true) -> TypeReference.create(Uint8::class.java)
                        else -> TypeReference.create(Uint256::class.java)
                    }
                }
                val decoded = FunctionReturnDecoder.decode(data, types.map { it as TypeReference<Type<*>> })
                decoded.map { it.value.toString() }
            } else {
                emptyList()
            }

            // Build params JSON: merge indexed param names with their values
            val paramMap = mutableMapOf<String, JsonElement>()
            val allValues = indexedParams + nonIndexedValues

            for (i in allParamNames.indices) {
                if (i < allValues.size) {
                    val name = allParamNames[i]
                    val raw = allValues[i]
                    // Clean up addresses (topics store full 32-byte, extract last 20 bytes)
                    val cleaned = if (raw.startsWith("0x") && raw.length == 66) {
                        "0x" + raw.substring(26)
                    } else {
                        raw
                    }
                    paramMap[name] = JsonPrimitive(cleaned)
                }
            }

            // Extract wallet address for fast lookup
            val wallet = if (eventDef.walletParamIdx >= 0 && eventDef.walletParamIdx < allValues.size) {
                val raw = allValues[eventDef.walletParamIdx]
                if (raw.startsWith("0x") && raw.length == 66) "0x" + raw.substring(26)
                else if (raw.startsWith("0x") && raw.length == 42) raw
                else null
            } else null

            OnChainEvent(
                chainId = chainId,
                contractAddress = "0x" + log.address.lowercase().removePrefix("0x"),
                eventName = eventDef.name,
                blockNumber = log.blockNumber.toLong(),
                txHash = log.transactionHash,
                logIndex = log.logIndex.toInt(),
                wallet = wallet,
                params = JsonObject(paramMap),
            )
        } catch (e: Exception) {
            idxLog.warn("Failed to parse event {} reason={}", eventDef.name, LogSanitizer.sanitizeError(e))
            if (idxLog.isDebugEnabled) idxLog.debug("Failed to parse event {} details", eventDef.name, e)
            null
        }
    }

    private fun storeBatch(events: List<OnChainEvent>) {
        val sql = """
            INSERT INTO onchain_events (chain_id, contract_address, event_name, block_number, tx_hash, log_index, wallet, params)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (chain_id, tx_hash, log_index) DO NOTHING
        """.trimIndent()

        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement(sql).use { stmt ->
                    for (event in events) {
                        stmt.setLong(1, event.chainId)
                        stmt.setString(2, event.contractAddress)
                        stmt.setString(3, event.eventName)
                        stmt.setLong(4, event.blockNumber)
                        stmt.setString(5, event.txHash)
                        stmt.setInt(6, event.logIndex)
                        if (event.wallet != null) stmt.setString(7, event.wallet)
                        else stmt.setNull(7, java.sql.Types.VARCHAR)
                        stmt.setString(8, event.params.toString())
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                idxLog.warn("Failed to store batch of {} events reason={}", events.size, LogSanitizer.sanitizeError(e))
                if (idxLog.isDebugEnabled) idxLog.debug("Failed to store batch details", e)
            }
        }
    }
}

/** Build event definitions for all indexed contracts. */
object EventDefinitions {

    /** Convert a solidity event signature to its topic hash (keccak256 of the signature string). */
    fun topic(signature: String): String = Hash.sha3String(signature)

    // ── Treasury ──
    val treasuryEvents = listOf(
        IndexedEvent("Deposited", topic("Deposited(address,uint256)"), 1, listOf("from", "amount"), walletParamIdx = 0),
        IndexedEvent("ERC20Deposited", topic("ERC20Deposited(address,address,uint256)"), 2, listOf("from", "token", "amount"), walletParamIdx = 0),
        IndexedEvent("AllocationCreated", topic("AllocationCreated(bytes32,address,uint256,uint256)"), 1, listOf("id", "token", "totalAmount", "recipientCount")),
        IndexedEvent("AllocationExecuted", topic("AllocationExecuted(bytes32)"), 1, listOf("id")),
        IndexedEvent("AllocationCancelled", topic("AllocationCancelled(bytes32)"), 1, listOf("id")),
    )

    // ── Proposal ──
    val proposalEvents = listOf(
        IndexedEvent("ProposalCreated", topic("ProposalCreated(uint256,address,bytes32[],uint256,uint256,string)"), 1,
            listOf("proposalId", "proposer", "allocationIds", "startTime", "endTime", "description"), walletParamIdx = 1),
        IndexedEvent("VoteCast", topic("VoteCast(uint256,address,uint8,uint256)"), 2, listOf("proposalId", "voter", "support", "weight"), walletParamIdx = 1),
        IndexedEvent("ProposalExecuted", topic("ProposalExecuted(uint256)"), 1, listOf("proposalId")),
        IndexedEvent("ProposalCancelled", topic("ProposalCancelled(uint256)"), 1, listOf("proposalId")),
    )

    // ── PaymentSplitter ──
    val paymentSplitterEvents = listOf(
        IndexedEvent("PayeeAdded", topic("PayeeAdded(address,uint256)"), 1, listOf("payee", "shares"), walletParamIdx = 0),
        IndexedEvent("PaymentReleased", topic("PaymentReleased(address,address,uint256)"), 2, listOf("token", "payee", "amount"), walletParamIdx = 1),
        IndexedEvent("PaymentReceived", topic("PaymentReceived(address,uint256)"), 1, listOf("from", "amount"), walletParamIdx = 0),
    )

    // ── SessionKeyModule ──
    val sessionKeyEvents = listOf(
        IndexedEvent("SessionKeyCreated", topic("SessionKeyCreated(bytes32,address,address,bytes32[],uint256,uint256,uint8)"), 3,
            listOf("keyId", "owner", "dApp", "permissions", "limit", "expiry", "riskTier"), walletParamIdx = 1),
        IndexedEvent("SessionKeyRevokedEv", topic("SessionKeyRevokedEv(bytes32,address)"), 2, listOf("keyId", "owner"), walletParamIdx = 1),
        IndexedEvent("SessionKeyUsed", topic("SessionKeyUsed(bytes32,bytes32,uint256)"), 3, listOf("keyId", "permission", "amount")),
    )

    // ── SocialRecoveryModule ──
    val socialRecoveryEvents = listOf(
        IndexedEvent("WalletRegistered", topic("WalletRegistered(address,bytes32)"), 1, listOf("wallet", "passkeyHash"), walletParamIdx = 0),
        IndexedEvent("GuardianAdded", topic("GuardianAdded(address,bytes32,uint256)"), 2, listOf("wallet", "identityHash", "index"), walletParamIdx = 0),
        IndexedEvent("GuardianConfirmed", topic("GuardianConfirmed(address,bytes32)"), 2, listOf("wallet", "identityHash"), walletParamIdx = 0),
        IndexedEvent("GuardianRemoved", topic("GuardianRemoved(address,bytes32,uint256)"), 2, listOf("wallet", "identityHash", "index"), walletParamIdx = 0),
        IndexedEvent("RecoveryInitiated", topic("RecoveryInitiated(address,bytes32,bytes32,address,uint256,uint256)"), 3,
            listOf("wallet", "oldPasskeyHash", "newPasskeyHash", "initiator", "deadline", "nonce"), walletParamIdx = 0),
        IndexedEvent("RecoveryExecutedEv", topic("RecoveryExecutedEv(address,bytes32)"), 2, listOf("wallet", "newPasskeyHash"), walletParamIdx = 0),
        IndexedEvent("ApprovalSubmitted", topic("ApprovalSubmitted(address,address,uint256,uint256)"), 2, listOf("wallet", "guardian", "nonce", "approvals"), walletParamIdx = 0),
        IndexedEvent("VetoSubmitted", topic("VetoSubmitted(address,address,uint256,uint256)"), 2, listOf("wallet", "guardian", "nonce", "vetoes"), walletParamIdx = 0),
    )

    // ── DeadManSwitch ──
    val deadManSwitchEvents = listOf(
        IndexedEvent("SwitchSet", topic("SwitchSet(address,address,uint256)"), 1, listOf("wallet", "beneficiary", "inactivityPeriod"), walletParamIdx = 0),
        IndexedEvent("ActivityPinged", topic("ActivityPinged(address,uint256)"), 1, listOf("wallet", "timestamp"), walletParamIdx = 0),
        IndexedEvent("SwitchTriggered", topic("SwitchTriggered(address,address)"), 2, listOf("wallet", "beneficiary"), walletParamIdx = 0),
    )

    // ── MDAOPaymaster (key events) ──
    val paymasterEvents = listOf(
        IndexedEvent("GasPaid", topic("GasPaid(address,address,uint256,uint256)"), 2, listOf("user", "token", "amount", "gasCost"), walletParamIdx = 0),
        IndexedEvent("PaymentFailed", topic("PaymentFailed(address,address,uint256,uint8)"), 2, listOf("user", "token", "amount", "reason"), walletParamIdx = 0),
        IndexedEvent("PriceUpdated", topic("PriceUpdated(address,uint256,uint256)"), 1, listOf("token", "oldPrice", "newPrice")),
    )
}
