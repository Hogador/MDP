package com.mdaopay.app.core.guardian

import kotlinx.serialization.Serializable

@Serializable
data class GuardianInfo(
    val identityHash: String,
    val label: String,
    val pubKeyX: String,
    val pubKeyY: String,
    val addedAt: Long,
    val shareIndex: Int,
    val isOnline: Boolean = false,
    val lastActiveDaysAgo: Int = 0
)

@Serializable
data class GuardianInvite(
    val inviteId: String,
    val walletAddress: String,
    val guardianLabel: String,
    val encryptedShare: String,
    val shareIndex: Int,
    val createdAt: Long,
    val status: InviteStatus = InviteStatus.PENDING
)

enum class InviteStatus { PENDING, ACCEPTED, DECLINED, EXPIRED }

@Serializable
data class PendingRecovery(
    val walletAddress: String,
    val newPasskeyPubKey: String,
    val startedAt: Long,
    val deadline: Long,
    val approvals: Int,
    val threshold: Int,
    val vetoed: Boolean,
    val executed: Boolean,
    val nonce: Int
)

@Serializable
data class RecoveryApproval(
    val walletAddress: String,
    val guardianIdentityHash: String,
    val signatureR: String,
    val signatureS: String,
    val nonce: Int
)

@Serializable
data class GuardianInviteResponse(
    val inviteId: String,
    val guardianLabel: String,
    val walletAddress: String,
    val encryptedShare: String,
    val shareIndex: Int
)

@Serializable
data class CreateInviteRequest(
    val walletAddress: String,
    val guardianLabel: String,
    val encryptedShare: String,
    val shareIndex: Int,
    val fcmToken: String
)
