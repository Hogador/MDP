import { describe, it, expect, vi, beforeEach } from 'vitest'

// vi.hoisted is required so the factory can reference the mock variable
const { mockVerifySignature, mockVerifyP256 } = vi.hoisted(() => ({
  mockVerifySignature: vi.fn(),
  mockVerifyP256: vi.fn(),
}))

vi.mock('../auth', () => ({
  verifySignature: mockVerifySignature,
  verifyP256Signature: mockVerifyP256,
  hmacSha256: vi.fn(),
}))

import handler, { __resetRateLimit } from '../index'

function mockEnv() {
  return {
    RELAY_SECRET: 'test-secret',
    FCM_SERVER_KEY: 'test-key',
    SOCIAL_RECOVERY_MODULE: '0x0000000000000000000000000000000000000000',
    RPC_URL: 'https://rpc.test',
    KV: {
      get: vi.fn(),
      put: vi.fn(),
      list: vi.fn(),
      delete: vi.fn(),
    } as any,
  }
}

describe('auth on invite endpoints', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    __resetRateLimit()
  })

  it('requires auth on POST /guardian/invite', async () => {
    mockVerifySignature.mockResolvedValue(false)
    const req = new Request('http://localhost/guardian/invite', {
      method: 'POST',
      body: JSON.stringify({
        walletAddress: '0x123',
        guardianLabel: 'test-guardian',
        encryptedShare: '0xabc123',
        shareIndex: 0,
      }),
      headers: { 'Content-Type': 'application/json' },
    })
    const res = await handler.fetch(req, mockEnv())
    expect(res.status).toBe(401)
    const body = await res.json()
    expect(body.success).toBe(false)
    expect(body.error).toMatch(/unauthorized/i)
  })

  it('requires auth on POST /recovery/approve', async () => {
    mockVerifySignature.mockResolvedValue(false)
    const req = new Request('http://localhost/recovery/approve', {
      method: 'POST',
      body: JSON.stringify({
        walletAddress: '0x123',
        guardianIdentityHash: '0xabc',
        nonce: 1,
      }),
      headers: { 'Content-Type': 'application/json' },
    })
    const res = await handler.fetch(req, mockEnv())
    expect(res.status).toBe(401)
    const body = await res.json()
    expect(body.success).toBe(false)
    expect(body.error).toMatch(/unauthorized/i)
  })

  it('requires auth on GET /guardian/invite/:inviteId', async () => {
    mockVerifySignature.mockResolvedValue(false)
    const req = new Request('http://localhost/guardian/invite/test-invite-123')
    const res = await handler.fetch(req, mockEnv())
    expect(res.status).toBe(401)
    const body = await res.json()
    expect(body.success).toBe(false)
    expect(body.error).toMatch(/unauthorized/i)
  })

  it('requires auth on GET /recovery/pending/:walletAddress', async () => {
    mockVerifySignature.mockResolvedValue(false)
    const req = new Request('http://localhost/recovery/pending/0x1234567890abcdef1234567890abcdef12345678')
    const res = await handler.fetch(req, mockEnv())
    expect(res.status).toBe(401)
    const body = await res.json()
    expect(body.success).toBe(false)
    expect(body.error).toMatch(/unauthorized/i)
  })

  it('rejects POST /guardian/invite/:inviteId/accept with invalid P-256 signature', async () => {
    mockVerifySignature.mockResolvedValue(true)
    mockVerifyP256.mockResolvedValue(false)
    const env = mockEnv()
    env.KV.get.mockResolvedValue(JSON.stringify({
      inviteId: 'inv-001',
      walletAddress: '0x123',
      guardianLabel: 'test',
      guardianPubKeyX: 'abc',
      guardianPubKeyY: 'def',
      encryptedShare: '0xenc',
      shareIndex: 0,
      createdAt: Date.now(),
      status: 'PENDING',
    }))
    const req = new Request('http://localhost/guardian/invite/inv-001/accept', {
      method: 'POST',
      body: JSON.stringify({ signatureR: 'bad', signatureS: 'sig', guardianIdentityHash: 'hash1' }),
      headers: { 'Content-Type': 'application/json' },
    })
    const res = await handler.fetch(req, env)
    expect(res.status).toBe(401)
    const body = await res.json()
    expect(body.error).toMatch(/invalid guardian signature/i)
  })

  it('accepts POST /guardian/invite/:inviteId/accept with valid P-256 signature', async () => {
    mockVerifySignature.mockResolvedValue(true)
    mockVerifyP256.mockResolvedValue(true)
    const env = mockEnv()
    env.KV.get.mockResolvedValue(JSON.stringify({
      inviteId: 'inv-002',
      walletAddress: '0x456',
      guardianLabel: 'test-guardian',
      guardianPubKeyX: 'abc123',
      guardianPubKeyY: 'def456',
      encryptedShare: '0xencrypted',
      shareIndex: 1,
      createdAt: Date.now(),
      status: 'PENDING',
    }))
    const req = new Request('http://localhost/guardian/invite/inv-002/accept', {
      method: 'POST',
      body: JSON.stringify({ signatureR: 'good_r', signatureS: 'good_s', guardianIdentityHash: 'hash2' }),
      headers: { 'Content-Type': 'application/json' },
    })
    const res = await handler.fetch(req, env)
    expect(res.status).toBe(200)
    const body = await res.json()
    expect(body.data.accepted).toBe(true)
    expect(env.KV.put).toHaveBeenCalled()
  })

  it('returns 404 for POST /guardian/invite/:inviteId/accept when invite not found', async () => {
    mockVerifySignature.mockResolvedValue(true)
    const env = mockEnv()
    env.KV.get.mockResolvedValue(null)
    const req = new Request('http://localhost/guardian/invite/nonexistent/accept', {
      method: 'POST',
      body: JSON.stringify({ signatureR: 'r', signatureS: 's', guardianIdentityHash: 'hash3' }),
      headers: { 'Content-Type': 'application/json' },
    })
    const res = await handler.fetch(req, env)
    expect(res.status).toBe(404)
  })

  it('returns 400 for POST /guardian/invite/:inviteId/accept when already accepted', async () => {
    mockVerifySignature.mockResolvedValue(true)
    const env = mockEnv()
    env.KV.get.mockResolvedValue(JSON.stringify({
      inviteId: 'inv-003',
      walletAddress: '0x789',
      guardianLabel: 'test',
      guardianPubKeyX: 'abc',
      guardianPubKeyY: 'def',
      encryptedShare: '0xenc',
      shareIndex: 0,
      createdAt: Date.now(),
      status: 'ACCEPTED',
    }))
    const req = new Request('http://localhost/guardian/invite/inv-003/accept', {
      method: 'POST',
      body: JSON.stringify({ signatureR: 'r', signatureS: 's', guardianIdentityHash: 'hash4' }),
      headers: { 'Content-Type': 'application/json' },
    })
    const res = await handler.fetch(req, env)
    expect(res.status).toBe(400)
    expect(res.status).toBe(400)
    const body = await res.json()
    expect(body.error).toMatch(/already processed/i)
  })

  // F-015: guardian verification for recovery endpoints
  it('rejects POST /recovery/approve with invalid guardian P-256 signature', async () => {
    mockVerifySignature.mockResolvedValue(true)
    mockVerifyP256.mockResolvedValue(false)
    const env = mockEnv()
    env.KV.get
      .mockResolvedValueOnce(JSON.stringify({ walletAddress: '0x123', pubKeyX: 'abc', pubKeyY: 'def' })) // getGuardian
      .mockResolvedValueOnce(JSON.stringify({ walletAddress: '0x123', newPasskeyPubKey: '0xnew', startedAt: Date.now(), deadline: Date.now() + 172800000, approvals: 0, threshold: 2, vetoed: false, executed: false, nonce: 1 })) // getPendingRecovery
    const req = new Request('http://localhost/recovery/approve', {
      method: 'POST',
      body: JSON.stringify({ walletAddress: '0x123', guardianIdentityHash: 'ghash', signatureR: 'bad_r', signatureS: 'bad_s', nonce: 1 }),
      headers: { 'Content-Type': 'application/json' },
    })
    const res = await handler.fetch(req, env)
    expect(res.status).toBe(401)
    const body = await res.json()
    expect(body.error).toMatch(/invalid guardian signature/i)
  })

  it('accepts POST /recovery/approve with valid guardian P-256 signature', async () => {
    mockVerifySignature.mockResolvedValue(true)
    mockVerifyP256.mockResolvedValue(true)
    const env = mockEnv()
    env.KV.get
      .mockResolvedValueOnce(JSON.stringify({ walletAddress: '0x123', pubKeyX: 'abc', pubKeyY: 'def' })) // getGuardian
      .mockResolvedValueOnce(JSON.stringify({ walletAddress: '0x123', newPasskeyPubKey: '0xnew', startedAt: Date.now(), deadline: Date.now() + 172800000, approvals: 0, threshold: 3, vetoed: false, executed: false, nonce: 1 })) // getPendingRecovery
      .mockResolvedValueOnce(JSON.stringify({ walletAddress: '0x123', newPasskeyPubKey: '0xnew', startedAt: Date.now(), deadline: Date.now() + 172800000, approvals: 1, threshold: 3, vetoed: false, executed: false, nonce: 1 })) // getPendingRecovery inside addApproval
    env.KV.get.mockResolvedValue(null) // approve: key (first approval)
    const req = new Request('http://localhost/recovery/approve', {
      method: 'POST',
      body: JSON.stringify({ walletAddress: '0x123', guardianIdentityHash: 'ghash', signatureR: 'good_r', signatureS: 'good_s', nonce: 1 }),
      headers: { 'Content-Type': 'application/json' },
    })
    const res = await handler.fetch(req, env)
    expect(res.status).toBe(200)
    const body = await res.json()
    expect(body.data.approvals).toBeGreaterThanOrEqual(1)
  })

  it('rejects POST /recovery/approve when guardian not found', async () => {
    mockVerifySignature.mockResolvedValue(true)
    const env = mockEnv()
    env.KV.get.mockResolvedValue(null) // getGuardian returns null
    const req = new Request('http://localhost/recovery/approve', {
      method: 'POST',
      body: JSON.stringify({ walletAddress: '0x123', guardianIdentityHash: 'unknown', signatureR: 'r', signatureS: 's', nonce: 1 }),
      headers: { 'Content-Type': 'application/json' },
    })
    const res = await handler.fetch(req, env)
    expect(res.status).toBe(401)
    const body = await res.json()
    expect(body.error).toMatch(/guardian not found/i)
  })

  it('rejects POST /recovery/veto with invalid guardian P-256 signature', async () => {
    mockVerifySignature.mockResolvedValue(true)
    mockVerifyP256.mockResolvedValue(false)
    const env = mockEnv()
    env.KV.get.mockResolvedValue(JSON.stringify({ walletAddress: '0x123', pubKeyX: 'abc', pubKeyY: 'def' })) // getGuardian
    const req = new Request('http://localhost/recovery/veto', {
      method: 'POST',
      body: JSON.stringify({ walletAddress: '0x123', guardianIdentityHash: 'ghash', signatureR: 'bad_r', signatureS: 'bad_s', nonce: 1 }),
      headers: { 'Content-Type': 'application/json' },
    })
    const res = await handler.fetch(req, env)
    expect(res.status).toBe(401)
    const body = await res.json()
    expect(body.error).toMatch(/invalid guardian signature/i)
  })

  // F-069: body size limit
  it('returns 413 for POST with Content-Length > 100KB', async () => {
    const env = mockEnv()
    // Build a body just over 100KB
    const largeBody = 'x'.repeat(102401)
    const req = new Request('http://localhost/guardian/invite', {
      method: 'POST',
      body: largeBody,
      headers: { 'Content-Type': 'application/json', 'Content-Length': String(102401) },
    })
    const res = await handler.fetch(req, env)
    expect(res.status).toBe(413)
    const body = await res.json()
    expect(body.error).toMatch(/payload too large/i)
  })

  // F-070: rate limiting
  it('returns 429 when rate limit exceeded for write endpoint', async () => {
    mockVerifySignature.mockResolvedValue(true)
    const env = mockEnv()
    // The rate limiter is per-IP; use the same request pattern
    const body = JSON.stringify({ walletAddress: '0x123', guardianLabel: 'test', encryptedShare: '0xenc', shareIndex: 0, guardianPubKeyX: 'x', guardianPubKeyY: 'y' })
    const makeReq = () => new Request('http://localhost/guardian/invite', {
      method: 'POST',
      body,
      headers: { 'Content-Type': 'application/json' },
    })
    // Fire 12 POST requests — 10 should pass, 11th should be 429
    for (let i = 0; i < 10; i++) {
      const r = await handler.fetch(makeReq(), env)
      // Some may be 400 (missing fcmToken) but not 429
      expect(r.status).not.toBe(429)
    }
    // 11th should be rate limited
    const res = await handler.fetch(makeReq(), env)
    expect(res.status).toBe(429)
    const body2 = await res.json()
    expect(body2.error).toMatch(/too many requests/i)
  })

  // F-071: error message does not leak auth details
  it('returns 500 without revealing RELAY_SECRET details when secret is missing', async () => {
    const env = mockEnv()
    env.RELAY_SECRET = ''
    const req = new Request('http://localhost/guardian/invite', {
      method: 'POST',
      body: JSON.stringify({ walletAddress: '0x123' }),
      headers: { 'Content-Type': 'application/json' },
    })
    const res = await handler.fetch(req, env)
    expect(res.status).toBe(500)
    const body = await res.json()
    expect(body.error).not.toMatch(/RELAY_SECRET/i)
    expect(body.error).toMatch(/internal server error/i)
  })
})
