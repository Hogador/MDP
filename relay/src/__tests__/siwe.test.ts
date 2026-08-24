import { describe, it, expect, vi, beforeEach } from 'vitest'

// vi.hoisted is required so the factory can reference the mock variable
const { mockVerifySiwe } = vi.hoisted(() => ({
  mockVerifySiwe: vi.fn(),
}))

vi.mock('../siwe', async (importOriginal) => {
  const actual = await importOriginal() as typeof import('../siwe')
  return { ...actual, verifySiweSignature: mockVerifySiwe }
})

import handler, { __resetRateLimit } from '../index'

function mockEnv() {
  return {
    RELAY_SECRET: 'test-secret-for-jwt',
    FCM_SERVER_KEY: 'test-key',
    SOCIAL_RECOVERY_MODULE: '0x0000000000000000000000000000000000000000',
    RPC_URL: 'https://rpc.test',
    KV: { get: vi.fn(), put: vi.fn(), list: vi.fn(), delete: vi.fn() } as any,
  }
}

describe('POST /auth/siwe', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    __resetRateLimit()
  })

  it('returns 200 with JWT token for valid SIWE signature', async () => {
    mockVerifySiwe.mockReturnValue('0x1234567890abcdef1234567890abcdef12345678')
    const req = new Request('http://localhost/auth/siwe', {
      method: 'POST',
      body: JSON.stringify({
        message: 'example.com wants you to sign in with your Ethereum account:\n\n0x1234567890abcdef1234567890abcdef12345678\n\nSome statement\nURI: https://example.com\nVersion: 1\nChain ID: 1\nNonce: abcdefgh12345678\nIssued At: 2025-01-01T00:00:00.000Z',
        signature: '0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abc1b',
      }),
      headers: { 'Content-Type': 'application/json' },
    })
    const res = await handler.fetch(req, mockEnv())
    expect(res.status).toBe(200)
    const body = await res.json()
    expect(body.success).toBe(true)
    expect(body.data.token).toBeTruthy()
    const parts = body.data.token.split('.')
    expect(parts).toHaveLength(3)
    // Verify JWT structure
    const decodedHeader = JSON.parse(atob(parts[0]))
    expect(decodedHeader.alg).toBe('HS256')
    expect(decodedHeader.typ).toBe('JWT')
    const decodedPayload = JSON.parse(atob(parts[1]))
    expect(decodedPayload.sub).toBe('0x1234567890abcdef1234567890abcdef12345678')
    expect(decodedPayload.method).toBe('siwe')
    expect(decodedPayload.iat).toBeTypeOf('number')
  })

  it('returns 400 for missing message', async () => {
    const req = new Request('http://localhost/auth/siwe', {
      method: 'POST',
      body: JSON.stringify({ signature: '0x1234' }),
      headers: { 'Content-Type': 'application/json' },
    })
    const res = await handler.fetch(req, mockEnv())
    expect(res.status).toBe(400)
    const body = await res.json()
    expect(body.error).toMatch(/missing/i)
  })

  it('returns 400 for missing signature', async () => {
    const req = new Request('http://localhost/auth/siwe', {
      method: 'POST',
      body: JSON.stringify({ message: 'Sign in with Ethereum...' }),
      headers: { 'Content-Type': 'application/json' },
    })
    const res = await handler.fetch(req, mockEnv())
    expect(res.status).toBe(400)
    const body = await res.json()
    expect(body.error).toMatch(/missing/i)
  })

  it('returns 400 for invalid JSON body', async () => {
    const req = new Request('http://localhost/auth/siwe', {
      method: 'POST',
      body: 'not-json',
      headers: { 'Content-Type': 'application/json' },
    })
    const res = await handler.fetch(req, mockEnv())
    expect(res.status).toBe(400)
    const body = await res.json()
    expect(body.error).toMatch(/invalid json/i)
  })

  it('returns 401 when signature verification fails', async () => {
    mockVerifySiwe.mockReturnValue(null)
    const req = new Request('http://localhost/auth/siwe', {
      method: 'POST',
      body: JSON.stringify({
        message: 'example.com wants you to sign in...',
        signature: '0xbad',
      }),
      headers: { 'Content-Type': 'application/json' },
    })
    const res = await handler.fetch(req, mockEnv())
    expect(res.status).toBe(401)
    const body = await res.json()
    expect(body.error).toMatch(/verification failed/i)
  })

  it('returns 413 for oversized message (>10KB)', async () => {
    const req = new Request('http://localhost/auth/siwe', {
      method: 'POST',
      body: JSON.stringify({ message: 'x'.repeat(10001), signature: '0xsig' }),
      headers: { 'Content-Type': 'application/json' },
    })
    const res = await handler.fetch(req, mockEnv())
    expect(res.status).toBe(413)
  })

  it('returns 413 for oversized signature (>2KB)', async () => {
    const req = new Request('http://localhost/auth/siwe', {
      method: 'POST',
      body: JSON.stringify({ message: 'valid message', signature: '0x' + 'a'.repeat(2000) }),
      headers: { 'Content-Type': 'application/json' },
    })
    const res = await handler.fetch(req, mockEnv())
    expect(res.status).toBe(413)
  })

  it('does not require X-Signature auth (public endpoint)', async () => {
    mockVerifySiwe.mockReturnValue('0x1234567890abcdef1234567890abcdef12345678')
    const req = new Request('http://localhost/auth/siwe', {
      method: 'POST',
      body: JSON.stringify({
        message: 'Sign in with Ethereum:\n\n0x1234567890abcdef1234567890abcdef12345678\n\nURI: https://app.com\nVersion: 1\nChain ID: 1\nNonce: abcdefgh\nIssued At: 2025-01-01T00:00:00.000Z',
        signature: '0xdeadbeef',
      }),
      headers: { 'Content-Type': 'application/json' },
    })
    const res = await handler.fetch(req, mockEnv())
    expect(res.status).toBe(200)
  })

  it('returns 400 for empty body', async () => {
    const req = new Request('http://localhost/auth/siwe', {
      method: 'POST',
      body: '',
      headers: { 'Content-Type': 'application/json' },
    })
    const res = await handler.fetch(req, mockEnv())
    expect(res.status).toBe(400)
  })
})
