export interface CreateInviteRequest {
  walletAddress: string
  guardianLabel: string
  encryptedShare: string
  shareIndex: number
  fcmToken: string
  guardianPubKeyX: string
  guardianPubKeyY: string
}

export interface GuardianInvite {
  inviteId: string
  walletAddress: string
  guardianLabel: string
  encryptedShare: string
  shareIndex: number
  createdAt: number
  status: InviteStatus
  guardianPubKeyX: string
  guardianPubKeyY: string
}

export type InviteStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'EXPIRED'

export interface GuardianInviteResponse {
  inviteId: string
  guardianLabel: string
  walletAddress: string
  encryptedShare: string
  shareIndex: number
}

export interface RecoveryApproval {
  walletAddress: string
  guardianIdentityHash: string
  signatureR: string
  signatureS: string
  nonce: number
}

export interface PendingRecovery {
  walletAddress: string
  newPasskeyPubKey: string
  startedAt: number
  deadline: number
  approvals: number
  threshold: number
  vetoed: boolean
  executed: boolean
  nonce: number
}

export interface AcceptInviteRequest {
  signatureR: string
  signatureS: string
  guardianIdentityHash: string
}

export interface PushRegisterRequest {
  walletAddress: string
  fcmToken: string
}

export interface VetoRequest {
  walletAddress: string
  guardianIdentityHash: string
  signatureR: string
  signatureS: string
  nonce: number
}

export interface ApiResponse<T = unknown> {
  success: boolean
  data?: T
  error?: string
}
