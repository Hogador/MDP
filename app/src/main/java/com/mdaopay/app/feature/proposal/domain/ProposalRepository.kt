package com.mdaopay.app.feature.proposal.domain

import com.mdaopay.app.BuildConfig
import com.mdaopay.app.core.common.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class OnChainEvent(
    val id: Long = 0,
    val chain_id: Long = 0,
    val contract_address: String = "",
    val event_name: String = "",
    val block_number: Long = 0,
    val tx_hash: String = "",
    val log_index: Int = 0,
    val wallet: String? = null,
    val param_json: String = "{}"
)

data class ProposalSummary(
    val id: Long,
    val description: String,
    val deadline: Long,
    val proposer: String,
    val allocId: String,
    val forVotes: Long = 0,
    val againstVotes: Long = 0,
    val abstainVotes: Long = 0,
    val executed: Boolean = false,
    val cancelled: Boolean = false,
    val blockNumber: Long = 0
) {
    val status: String get() = when {
        executed -> "executed"
        cancelled -> "cancelled"
        deadline > 0 && deadline * 1000 < System.currentTimeMillis() -> "ended"
        else -> "active"
    }
    val totalVotes: Long get() = forVotes + againstVotes + abstainVotes
}

@Singleton
class ProposalRepository @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun getProposals(): Result<List<ProposalSummary>> = withContext(Dispatchers.IO) {
        try {
            val url = "${BuildConfig.BACKEND_URL}/events?contract=Proposal&limit=500"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.Error(Exception("HTTP ${response.code}"))
            }
            val body = response.body?.string() ?: return@withContext Result.Error(Exception("Empty response"))

            val raw = json.parseToJsonElement(body).jsonObject
            val rawEvents = raw["events"]?.jsonArray ?: return@withContext Result.Error(Exception("No events"))
            val events = rawEvents.map { json.decodeFromString<OnChainEvent>(it.toString()) }

            val created = events.filter { it.event_name == "ProposalCreated" }
            val votes = events.filter { it.event_name == "VoteCast" }
            val executedIds = events.filter { it.event_name == "ProposalExecuted" }.mapNotNull { parseParam(it.param_json, "proposalId")?.toLongOrNull() }.toSet()
            val cancelledIds = events.filter { it.event_name == "ProposalCancelled" }.mapNotNull { parseParam(it.param_json, "proposalId")?.toLongOrNull() }.toSet()

            val proposals = created.mapNotNull { ev ->
                val params = parseParams(ev.param_json)
                val pid = params["proposalId"]?.toLongOrNull() ?: return@mapNotNull null

                val forVotes = votes.filter { v -> parseParam(v.param_json, "proposalId")?.toLongOrNull() == pid && parseParam(v.param_json, "support") == "1" }
                    .sumOf { parseParam(it.param_json, "weight")?.toLongOrNull() ?: 0L }
                val againstVotes = votes.filter { v -> parseParam(v.param_json, "proposalId")?.toLongOrNull() == pid && parseParam(v.param_json, "support") == "0" }
                    .sumOf { parseParam(it.param_json, "weight")?.toLongOrNull() ?: 0L }
                val abstainVotes = votes.filter { v -> parseParam(v.param_json, "proposalId")?.toLongOrNull() == pid && parseParam(v.param_json, "support") == "2" }
                    .sumOf { parseParam(it.param_json, "weight")?.toLongOrNull() ?: 0L }

                ProposalSummary(
                    id = pid,
                    description = params["description"]?.trim('"') ?: "—",
                    deadline = params["deadline"]?.toLongOrNull() ?: 0L,
                    proposer = params["proposer"]?.removePrefix("0x")?.take(8) ?: "?",
                    allocId = params["allocId"]?.removePrefix("0x")?.take(8) ?: "?",
                    forVotes = forVotes,
                    againstVotes = againstVotes,
                    abstainVotes = abstainVotes,
                    executed = pid in executedIds,
                    cancelled = pid in cancelledIds,
                    blockNumber = ev.block_number
                )
            }.sortedByDescending { it.id }

            Result.Success(proposals)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getProposalDetail(proposalId: Long): Result<ProposalSummary> {
        val result = getProposals()
        return when (result) {
            is Result.Success -> {
                val found = result.data.find { it.id == proposalId }
                if (found != null) Result.Success(found) else Result.Error(Exception("Not found"))
            }
            is Result.Error -> result
        }
    }

    private fun parseParams(paramJson: String): Map<String, String> {
        return try {
            val obj = json.parseToJsonElement(paramJson).jsonObject
            obj.entries.associate { (k, v) -> k to (v.jsonPrimitive?.content ?: v.toString()) }
        } catch (_: Exception) { emptyMap() }
    }

    private fun parseParam(paramJson: String, key: String): String? = parseParams(paramJson)[key]
}
