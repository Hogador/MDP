package com.mdaopay.app.core.blockchain

data class WCSession(
    val topic: String,
    val peerName: String,
    val peerUrl: String,
    val peerIcon: String,
    val chains: List<String>,
    val methods: List<String>
)

data class WCProposal(
    val id: Long,
    val pairingTopic: String,
    val proposerName: String,
    val proposerUrl: String,
    val proposerIcon: String,
    val chains: List<String>
)

sealed class WCEvent {
    data class SessionProposal(val proposal: WCProposal) : WCEvent()
    data class SessionRequest(val topic: String, val method: String, val params: String) : WCEvent()
    data object Disconnected : WCEvent()
}
