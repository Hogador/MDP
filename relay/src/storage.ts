import { GuardianInvite, InviteStatus, PendingRecovery } from './types'

const INVITE_KEY = (id: string) => `invite:${id}`
const GUARDIAN_PUSH_KEY = (addr: string) => `push:${addr}`
const RECOVERY_KEY = (addr: string) => `recovery:${addr}`
const NONCE_KEY = (addr: string) => `nonce:${addr}`
const GUARDIAN_HASH_KEY = (hash: string) => `guardian:${hash}`

export async function createInvite(
  kv: KVNamespace,
  invite: GuardianInvite,
): Promise<void> {
  await kv.put(INVITE_KEY(invite.inviteId), JSON.stringify(invite), {
    expirationTtl: 7 * 86400,
  })
}

export async function getInvite(
  kv: KVNamespace,
  inviteId: string,
): Promise<GuardianInvite | null> {
  const raw = await kv.get(INVITE_KEY(inviteId))
  return raw ? (JSON.parse(raw) as GuardianInvite) : null
}

export async function updateInviteStatus(
  kv: KVNamespace,
  inviteId: string,
  status: InviteStatus,
): Promise<void> {
  const invite = await getInvite(kv, inviteId)
  if (!invite) throw new Error('Invite not found')
  invite.status = status
  await kv.put(INVITE_KEY(inviteId), JSON.stringify(invite), {
    expirationTtl: 7 * 86400,
  })
}

export async function registerPushToken(
  kv: KVNamespace,
  walletAddress: string,
  fcmToken: string,
): Promise<void> {
  const raw = await kv.get(GUARDIAN_PUSH_KEY(walletAddress))
  const tokens: string[] = raw ? (JSON.parse(raw) as string[]) : []
  if (!tokens.includes(fcmToken)) {
    tokens.push(fcmToken)
    await kv.put(GUARDIAN_PUSH_KEY(walletAddress), JSON.stringify(tokens))
  }
}

export async function getPushTokens(
  kv: KVNamespace,
  walletAddress: string,
): Promise<string[]> {
  const raw = await kv.get(GUARDIAN_PUSH_KEY(walletAddress))
  return raw ? (JSON.parse(raw) as string[]) : []
}

export async function getPendingRecovery(
  kv: KVNamespace,
  walletAddress: string,
): Promise<PendingRecovery | null> {
  const raw = await kv.get(RECOVERY_KEY(walletAddress))
  return raw ? (JSON.parse(raw) as PendingRecovery) : null
}

export async function setPendingRecovery(
  kv: KVNamespace,
  recovery: PendingRecovery,
): Promise<void> {
  await kv.put(RECOVERY_KEY(recovery.walletAddress), JSON.stringify(recovery), {
    expirationTtl: 72 * 86400,
  })
}

export async function addApproval(
  kv: KVNamespace,
  walletAddress: string,
  guardianHash: string,
): Promise<number> {
  const recovery = await getPendingRecovery(kv, walletAddress)
  if (!recovery) throw new Error('No pending recovery')
  if (recovery.vetoed) throw new Error('Recovery was vetoed')
  if (recovery.executed) throw new Error('Recovery already executed')

  // C-10: TOCTOU race fix — KV is eventually consistent; use idempotent guard
  const approvedKey = `approve:${walletAddress}:${recovery.nonce}`
  const approversRaw = await kv.get(approvedKey)
  const approvers: string[] = approversRaw
    ? (JSON.parse(approversRaw) as string[])
    : []
  if (approvers.includes(guardianHash)) {
    return recovery.approvals
  }
  approvers.push(guardianHash)

  // ponytail: KV put is last-write-wins; for strong consistency migrate to Durable Objects
  await kv.put(approvedKey, JSON.stringify(approvers), {
    expirationTtl: 72 * 86400,
  })

  recovery.approvals = approvers.length
  await setPendingRecovery(kv, recovery)
  return recovery.approvals
}

export async function vetoRecovery(
  kv: KVNamespace,
  walletAddress: string,
): Promise<void> {
  const recovery = await getPendingRecovery(kv, walletAddress)
  if (!recovery) throw new Error('No pending recovery')
  if (recovery.executed) throw new Error('Recovery already executed')
  recovery.vetoed = true
  await setPendingRecovery(kv, recovery)
}

// ponytail: guardian pub key lookup by identity hash — used for P-256 sig verification in recovery endpoints
export async function storeGuardian(
  kv: KVNamespace,
  identityHash: string,
  walletAddress: string,
  pubKeyX: string,
  pubKeyY: string,
): Promise<void> {
  await kv.put(GUARDIAN_HASH_KEY(identityHash), JSON.stringify({ walletAddress, pubKeyX, pubKeyY }), {
    expirationTtl: 90 * 86400,
  })
}

export async function getGuardian(
  kv: KVNamespace,
  identityHash: string,
): Promise<{ walletAddress: string; pubKeyX: string; pubKeyY: string } | null> {
  const raw = await kv.get(GUARDIAN_HASH_KEY(identityHash))
  return raw ? (JSON.parse(raw) as { walletAddress: string; pubKeyX: string; pubKeyY: string }) : null
}
