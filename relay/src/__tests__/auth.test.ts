import { describe, it, expect } from 'vitest'
import { verifySignature, hmacSha256, verifyP256Signature } from '../auth'

const RELAY_SECRET = 'test-secret-key-for-testing'

function bytesToHex(bytes: Uint8Array): string {
  return Array.from(bytes).map(b => b.toString(16).padStart(2, '0')).join('')
}

async function generateP256KeyPair(): Promise<{ pubKeyX: string; pubKeyY: string; sign(msg: string): Promise<{ r: string; s: string }> }> {
  const keyPair = await crypto.subtle.generateKey(
    { name: 'ECDSA', namedCurve: 'P-256' },
    true,
    ['sign', 'verify'],
  )
  const pubRaw = new Uint8Array(await crypto.subtle.exportKey('raw', keyPair.publicKey))
  const pubKeyX = bytesToHex(pubRaw.subarray(1, 33))
  const pubKeyY = bytesToHex(pubRaw.subarray(33, 65))
  return {
    pubKeyX,
    pubKeyY,
    async sign(msg: string) {
      const sig = new Uint8Array(await crypto.subtle.sign(
        { name: 'ECDSA', hash: 'SHA-256' },
        keyPair.privateKey,
        new TextEncoder().encode(msg),
      ))
      return { r: bytesToHex(sig.subarray(0, 32)), s: bytesToHex(sig.subarray(32, 64)) }
    },
  }
}

describe('verifyP256Signature', () => {
  it('returns true for valid signature', async () => {
    const key = await generateP256KeyPair()
    const message = 'accept:test-invite-123:0x123'
    const { r, s } = await key.sign(message)
    expect(await verifyP256Signature(message, r, s, key.pubKeyX, key.pubKeyY)).toBe(true)
  })

  it('returns false for wrong message', async () => {
    const key = await generateP256KeyPair()
    const { r, s } = await key.sign('accept:invite-A:0x123')
    expect(await verifyP256Signature('accept:invite-B:0x456', r, s, key.pubKeyX, key.pubKeyY)).toBe(false)
  })

  it('returns false for wrong public key', async () => {
    const key = await generateP256KeyPair()
    const other = await generateP256KeyPair()
    const message = 'accept:test-invite:0x123'
    const { r, s } = await key.sign(message)
    expect(await verifyP256Signature(message, r, s, other.pubKeyX, other.pubKeyY)).toBe(false)
  })

  it('returns false for tampered signature', async () => {
    const key = await generateP256KeyPair()
    const message = 'accept:test-invite:0x123'
    const { r, s } = await key.sign(message)
    const rBytes = Uint8Array.from(r.match(/.{2}/g)!.map(b => parseInt(b, 16)))
    rBytes[0] ^= 0x01
    const tamperedR = bytesToHex(rBytes)
    expect(await verifyP256Signature(message, tamperedR, s, key.pubKeyX, key.pubKeyY)).toBe(false)
  })

  it('returns false for invalid hex', async () => {
    expect(await verifyP256Signature('test', 'xyz', 'abc', 'dead', 'beef')).toBe(false)
  })
})

describe('verifySignature', () => {
  it('returns true for valid signature', async () => {
    const body = JSON.stringify({ walletAddress: '0x123', action: 'approve' })
    const ts = Date.now()
    const sig = await hmacSha256(RELAY_SECRET, `${ts}.${body}`)
    expect(await verifySignature(body, ts.toString(), sig, RELAY_SECRET)).toBe(true)
  })

  it('returns false for wrong signature', async () => {
    const body = JSON.stringify({ walletAddress: '0x123' })
    const ts = Date.now()
    expect(await verifySignature(body, ts.toString(), 'deadbeef', RELAY_SECRET)).toBe(false)
  })

  it('returns false for wrong secret', async () => {
    const body = JSON.stringify({ walletAddress: '0x123' })
    const ts = Date.now()
    const sig = await hmacSha256('wrong-secret', `${ts}.${body}`)
    expect(await verifySignature(body, ts.toString(), sig, RELAY_SECRET)).toBe(false)
  })

  it('returns false for empty signature', async () => {
    const body = JSON.stringify({ walletAddress: '0x123' })
    const ts = Date.now()
    expect(await verifySignature(body, ts.toString(), '', RELAY_SECRET)).toBe(false)
  })

  it('returns false for tampered body', async () => {
    const body = JSON.stringify({ walletAddress: '0x123' })
    const ts = Date.now()
    const sig = await hmacSha256(RELAY_SECRET, `${ts}.${body}`)
    expect(await verifySignature(
      JSON.stringify({ walletAddress: '0x456' }),
      ts.toString(),
      sig,
      RELAY_SECRET,
    )).toBe(false)
  })

  it('returns false for expired timestamp (older than 5 min)', async () => {
    const body = JSON.stringify({ walletAddress: '0x123' })
    const ts = Date.now() - 6 * 60 * 1000
    expect(await verifySignature(body, ts.toString(), 'any', RELAY_SECRET)).toBe(false)
  })

  it('returns false for future timestamp (>30s ahead)', async () => {
    const body = JSON.stringify({ walletAddress: '0x123' })
    const ts = Date.now() + 60 * 1000
    expect(await verifySignature(body, ts.toString(), 'any', RELAY_SECRET)).toBe(false)
  })
})
