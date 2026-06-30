// ponytail: hex decode — avoids Buffer dependency, works in CF Workers
function hexToBytes(hex: string): Uint8Array {
  hex = hex.replace(/^0x/i, '')
  if (hex.length % 2 !== 0) hex = '0' + hex
  const bytes = new Uint8Array(hex.length / 2)
  for (let i = 0; i < hex.length; i += 2)
    bytes[i / 2] = parseInt(hex.substring(i, i + 2), 16)
  return bytes
}

// ponytail: ECDSA P-256 signature verification via Web Crypto API
// Falls back to DER encoding for older runtimes (Firefox, etc.)
export async function verifyP256Signature(
  message: string,
  signatureR: string,
  signatureS: string,
  pubKeyX: string,
  pubKeyY: string,
): Promise<boolean> {
  try {
    const keyBytes = new Uint8Array(65)
    keyBytes[0] = 0x04
    keyBytes.set(hexToBytes(pubKeyX), 1)
    keyBytes.set(hexToBytes(pubKeyY), 33)

    const publicKey = await crypto.subtle.importKey(
      'raw',
      keyBytes,
      { name: 'ECDSA', namedCurve: 'P-256' },
      false,
      ['verify'],
    )

    const rawSig = new Uint8Array(64)
    rawSig.set(hexToBytes(signatureR), 0)
    rawSig.set(hexToBytes(signatureS), 32)

    return await crypto.subtle.verify(
      { name: 'ECDSA', hash: 'SHA-256' },
      publicKey,
      rawSig,
      new TextEncoder().encode(message),
    )
  } catch {
    return false
  }
}

// ponytail: constant-time hex compare — no Node crypto dep, works in CF Workers
function constantTimeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false
  let diff = 0
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i)
  return diff === 0
}

const TIMESTAMP_DRIFT_MS = 5 * 60 * 1000
const FUTURE_DRIFT_MS = 30 * 1000

export async function hmacSha256(secret: string, data: string): Promise<string> {
  const encoder = new TextEncoder()
  const key = await crypto.subtle.importKey(
    'raw',
    encoder.encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign'],
  )
  const sig = await crypto.subtle.sign('HMAC', key, encoder.encode(data))
  return Array.from(new Uint8Array(sig))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

export async function verifySignature(
  body: string,
  timestamp: string,
  signature: string,
  relaySecret: string,
): Promise<boolean> {
  if (!signature || !timestamp || !relaySecret) return false

  const ts = parseInt(timestamp, 10)
  if (isNaN(ts)) return false

  const now = Date.now()
  if (now - ts > TIMESTAMP_DRIFT_MS) return false
  if (ts - now > FUTURE_DRIFT_MS) return false

  const expectedSig = await hmacSha256(relaySecret, `${timestamp}.${body}`)
  return constantTimeEqual(signature, expectedSig)
}
