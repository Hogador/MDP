package com.mdaopay.app.feature.proposal.domain

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProposalRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `events url uses v1 path and contract address param`() {
        val url = proposalEventsUrl("http://backend:8080", "0xabc123")
        assertEquals("http://backend:8080/v1/events?contract=0xabc123&limit=500", url)
    }

    @Test
    fun `onchain event parses params field from backend response`() {
        val raw = """{"id":1,"chain_id":97,"contract_address":"0xabc","event_name":"ProposalCreated","block_number":10,"tx_hash":"0x1","log_index":0,"params":"{\"proposalId\":\"1\"}"}"""
        val event = json.decodeFromString<OnChainEvent>(raw)
        assertEquals("""{"proposalId":"1"}""", event.params)
        assertTrue(event.params.contains("proposalId"))
    }
}