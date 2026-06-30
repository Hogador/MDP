import {
  createInvite,
  getInvite,
  updateInviteStatus,
  registerPushToken,
  getPushTokens,
  getPendingRecovery,
  setPendingRecovery,
  addApproval,
  vetoRecovery,
  storeGuardian,
  getGuardian,
} from './storage'
import { sendPushNotification } from './fcm'
import { verifySignature, verifyP256Signature } from './auth'
import type {
  AcceptInviteRequest,
  ApiResponse,
  CreateInviteRequest,
  GuardianInvite,
  GuardianInviteResponse,
  PendingRecovery,
  PushRegisterRequest,
  RecoveryApproval,
  VetoRequest,
} from './types'

// ponytail: in-memory rate limiter — per-isolate, not global, but better than nothing
const RATE_LIMIT_MAP = new Map<string, { count: number; windowStart: number }>()
const RATE_LIMIT_WINDOW_MS = 1000
const MAX_BODY_SIZE = 102400 // 100KB

// ponytail: tested via __resetRateLimit — only used in vitest
export function __resetRateLimit(): void { RATE_LIMIT_MAP.clear() }

function rateLimitKey(request: Request): { key: string; maxReqs: number } {
  const ip = request.headers.get('CF-Connecting-IP') || request.headers.get('x-forwarded-for') || 'unknown'
  const isWrite = request.method === 'POST' || request.method === 'PUT' || request.method === 'DELETE'
  return { key: `${ip}:${isWrite ? 'write' : 'read'}`, maxReqs: isWrite ? 10 : 30 }
}

function checkRateLimit(request: Request): Response | null {
  const { key, maxReqs } = rateLimitKey(request)
  const now = Date.now()
  const entry = RATE_LIMIT_MAP.get(key)
  if (!entry || now - entry.windowStart > RATE_LIMIT_WINDOW_MS) {
    RATE_LIMIT_MAP.set(key, { count: 1, windowStart: now })
    return null
  }
  if (entry.count >= maxReqs) {
    return new Response(JSON.stringify({ success: false, error: 'Too Many Requests' } satisfies ApiResponse), {
      status: 429,
      headers: { 'Content-Type': 'application/json', 'Retry-After': '1' },
    })
  }
  entry.count++
  return null
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url)
    const path = url.pathname
    const method = request.method

    const json = <T>(data: T, status = 200): Response =>
      new Response(JSON.stringify({ success: true, data } satisfies ApiResponse<T>), {
        status,
        headers: { 'Content-Type': 'application/json' },
      })

    const err = (msg: string, status = 400): Response =>
      new Response(JSON.stringify({ success: false, error: msg } satisfies ApiResponse), {
        status,
        headers: { 'Content-Type': 'application/json' },
      })

    const requireAuth = async (bodyText: string): Promise<Response | null> => {
      // ponytail: fail-closed — missing RELAY_SECRET means auth is bypassed, reject all requests
      if (!env.RELAY_SECRET) {
        console.error('RELAY_SECRET not set — all requests rejected')
        return err('Internal server error', 500)
      }
      const ts = request.headers.get('X-Timestamp') || ''
      const sig = request.headers.get('X-Signature') || ''
      const valid = await verifySignature(bodyText, ts, sig, env.RELAY_SECRET)
      if (!valid) return err('Unauthorized: invalid or missing signature', 401)
      return null
    }

    const readBody = async (): Promise<{ text: string; authError: Response | null }> => {
      // F-069: check Content-Length before reading body
      const contentLength = request.headers.get('Content-Length')
      if (contentLength && parseInt(contentLength, 10) > MAX_BODY_SIZE) {
        return { text: '', authError: err('Payload Too Large', 413) }
      }
      const text = await request.text()
      const authError = await requireAuth(text)
      return { text, authError }
    }

    try {
      // F-070: rate limit before processing
      const rateLimitError = checkRateLimit(request)
      if (rateLimitError) return rateLimitError

      // POST /guardian/invite
      if (method === 'POST' && path === '/guardian/invite') {
        const { text, authError } = await readBody()
        if (authError) return authError
        const body: CreateInviteRequest = JSON.parse(text)
        if (!body.walletAddress || !body.guardianLabel || !body.encryptedShare || !body.guardianPubKeyX || !body.guardianPubKeyY) {
          return err('Missing required fields')
        }

        await registerPushToken(env.KV, body.walletAddress, body.fcmToken)

        const invite: GuardianInvite = {
          inviteId: crypto.randomUUID(),
          walletAddress: body.walletAddress,
          guardianLabel: body.guardianLabel,
          encryptedShare: body.encryptedShare,
          shareIndex: body.shareIndex,
          createdAt: Date.now(),
          status: 'PENDING',
          guardianPubKeyX: body.guardianPubKeyX,
          guardianPubKeyY: body.guardianPubKeyY,
        }
        await createInvite(env.KV, invite)

        return json(invite, 201)
      }

      // GET /guardian/invite/:inviteId
      const inviteMatch = path.match(/^\/guardian\/invite\/([^/]+)$/)
      if (method === 'GET' && inviteMatch) {
        const authError = await requireAuth('')
        if (authError) return authError
        const inviteId = inviteMatch[1]
        const invite = await getInvite(env.KV, inviteId)
        if (!invite) return err('Invite not found', 404)

        const response: GuardianInviteResponse = {
          inviteId: invite.inviteId,
          guardianLabel: invite.guardianLabel,
          walletAddress: invite.walletAddress,
          encryptedShare: invite.encryptedShare,
          shareIndex: invite.shareIndex,
        }
        return json(response)
      }

      // POST /guardian/invite/:inviteId/accept
      const acceptMatch = path.match(/^\/guardian\/invite\/([^/]+)\/accept$/)
      if (method === 'POST' && acceptMatch) {
        const { text, authError } = await readBody()
        if (authError) return authError
        const inviteId = acceptMatch[1]
        const body: AcceptInviteRequest = JSON.parse(text)
        if (!body.signatureR || !body.signatureS || !body.guardianIdentityHash) {
          return err('Missing signature fields')
        }

        const invite = await getInvite(env.KV, inviteId)
        if (!invite) return err('Invite not found', 404)
        if (invite.status !== 'PENDING') return err('Invite already processed', 400)

        const valid = await verifyP256Signature(
          `accept:${inviteId}:${invite.walletAddress}`,
          body.signatureR,
          body.signatureS,
          invite.guardianPubKeyX,
          invite.guardianPubKeyY,
        )
        if (!valid) return err('Invalid guardian signature', 401)

        await updateInviteStatus(env.KV, inviteId, 'ACCEPTED')
        // F-015: store guardian pub key for recovery verification
        await storeGuardian(env.KV, body.guardianIdentityHash, invite.walletAddress, invite.guardianPubKeyX, invite.guardianPubKeyY)
        return json({ accepted: true })
      }

      // GET /recovery/pending/:walletAddress
      const pendingMatch = path.match(/^\/recovery\/pending\/(0x[a-fA-F0-9]+)$/)
      if (method === 'GET' && pendingMatch) {
        const authError = await requireAuth('')
        if (authError) return authError
        const walletAddress = pendingMatch[1]
        const recovery = await getPendingRecovery(env.KV, walletAddress)
        return json(recovery ? [recovery] : [])
      }

      // POST /recovery/approve
      if (method === 'POST' && path === '/recovery/approve') {
        const { text, authError } = await readBody()
        if (authError) return authError
        const body: RecoveryApproval = JSON.parse(text)
        if (!body.walletAddress || !body.guardianIdentityHash || !body.signatureR || !body.signatureS) {
          return err('Missing required fields')
        }

        // F-015: verify guardian P-256 signature
        const guardian = await getGuardian(env.KV, body.guardianIdentityHash)
        if (!guardian) return err('Guardian not found', 401)
        const sigValid = await verifyP256Signature(
          `approve:${body.walletAddress}:${body.nonce}`,
          body.signatureR,
          body.signatureS,
          guardian.pubKeyX,
          guardian.pubKeyY,
        )
        if (!sigValid) return err('Invalid guardian signature', 401)

        const recovery = await getPendingRecovery(env.KV, body.walletAddress)
        if (!recovery) return err('No pending recovery')
        if (body.nonce !== recovery.nonce) return err('Nonce mismatch: approval is for a different recovery round')

        const approvalCount = await addApproval(
          env.KV,
          body.walletAddress,
          body.guardianIdentityHash,
        )

        const updated = await getPendingRecovery(env.KV, body.walletAddress)
        if (updated && approvalCount >= updated.threshold) {
          const tokens = await getPushTokens(env.KV, body.walletAddress)
          await sendPushNotification(
            env.FCM_SERVER_KEY,
            tokens,
            {
              type: 'recovery_approvals_threshold_met',
              walletAddress: body.walletAddress,
              title: 'Recovery Approved',
              body: `Recovery threshold reached (${approvalCount}/${updated.threshold}).`,
            },
          )
        }

        return json({ approvals: approvalCount })
      }

      // POST /recovery/veto
      if (method === 'POST' && path === '/recovery/veto') {
        const { text, authError } = await readBody()
        if (authError) return authError
        const body: VetoRequest = JSON.parse(text)
        if (!body.walletAddress || !body.guardianIdentityHash || !body.signatureR || !body.signatureS) return err('Missing required fields')

        // F-015: verify guardian P-256 signature
        const guardian = await getGuardian(env.KV, body.guardianIdentityHash)
        if (!guardian) return err('Guardian not found', 401)
        const sigValid = await verifyP256Signature(
          `veto:${body.walletAddress}:${body.nonce}`,
          body.signatureR,
          body.signatureS,
          guardian.pubKeyX,
          guardian.pubKeyY,
        )
        if (!sigValid) return err('Invalid guardian signature', 401)

        await vetoRecovery(env.KV, body.walletAddress)

        const tokens = await getPushTokens(env.KV, body.walletAddress)
        await sendPushNotification(env.FCM_SERVER_KEY, tokens, {
          type: 'recovery_vetoed',
          walletAddress: body.walletAddress,
          title: 'Recovery Vetoed',
          body: 'A guardian has vetoed the recovery.',
        })

        return json({ vetoed: true })
      }

      // POST /push/register
      if (method === 'POST' && path === '/push/register') {
        const { text, authError } = await readBody()
        if (authError) return authError
        const body: PushRegisterRequest = JSON.parse(text)
        if (!body.walletAddress || !body.fcmToken) return err('Missing fields')
        await registerPushToken(env.KV, body.walletAddress, body.fcmToken)
        return json({ registered: true })
      }

      // POST /recovery/notify — notify guardians of initiated recovery
      if (method === 'POST' && path === '/recovery/notify') {
        const { text, authError } = await readBody()
        if (authError) return authError
        const body: { walletAddress: string } = JSON.parse(text)
        if (!body.walletAddress) return err('Missing walletAddress')

        const tokens = await getPushTokens(env.KV, body.walletAddress)
        await sendPushNotification(
          env.FCM_SERVER_KEY,
          tokens,
          {
            type: 'recovery_initiated',
            walletAddress: body.walletAddress,
            title: 'Recovery Initiated',
            body: 'Someone initiated recovery of your wallet. Approve or veto within 48h.',
          },
        )

        return json({ notified: tokens.length })
      }

      return err('Not found', 404)
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'Internal error'
      return err(msg, 500)
    }
  },
} satisfies ExportedHandler<Env>

interface Env {
  KV: KVNamespace
  FCM_SERVER_KEY: string
  SOCIAL_RECOVERY_MODULE: string
  RPC_URL: string
  RELAY_SECRET: string
}
