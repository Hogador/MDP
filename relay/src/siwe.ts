import { verifyMessage } from 'ethers'
import { hmacSha256, hexToBytes } from './auth'

// ponytail: base64url — avoids Buffer dependency, works in CF Workers
function base64url(data: string): string {
  return btoa(data).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function bytesToBase64url(bytes: Uint8Array): string {
  let binary = ''
  for (let i = 0; i < bytes.length; i++) binary += String.fromCharCode(bytes[i])
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

// ponytail: JWT (HS256) — minimal, no external lib, reuses hmacSha256 from auth.ts
export async function issueJwt(
  payload: Record<string, unknown>,
  secret: string,
): Promise<string> {
  const header = base64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const body = base64url(JSON.stringify(payload))
  const sigHex = await hmacSha256(secret, `${header}.${body}`)
  const signature = bytesToBase64url(hexToBytes(sigHex))
  return `${header}.${body}.${signature}`
}

// EIP-4361 SIWE message format:
// domain wants you to sign in with your Ethereum account:
// ADDRESS
// (optional statement)
//
// URI: https://...
// Version: 1
// Chain ID: 56
// Nonce: ...
// Issued At: ...
// Expiration Time: ... (optional)
// Not Before: ... (optional)
export function verifySiweSignature(
  message: string,
  signature: string,
  expectedDomain?: string,   // e.g. 'app.mdaopay.com'
  expectedChainId?: number,  // e.g. 56
): string | null {
  try {
    const recoveredAddress = verifyMessage(message, signature)

    // Parse address from first line
    const addressMatch = message.match(/^(0x[a-fA-F0-9]{40})/m)
    if (!addressMatch) return null
    if (recoveredAddress.toLowerCase() !== addressMatch[1].toLowerCase()) return null

    // Parse EIP-4361 fields
    const domainMatch = message.match(/^([^\n]+) wants you to sign in with your Ethereum account:/m)
    const uriMatch = message.match(/^URI:\s*(.+)$/m)
    const versionMatch = message.match(/^Version:\s*(.+)$/m)
    const chainIdMatch = message.match(/^Chain ID:\s*(\d+)$/m)
    const nonceMatch = message.match(/^Nonce:\s*(.+)$/m)
    const expMatch = message.match(/^Expiration Time:\s*(.+)$/m)
    const nbfMatch = message.match(/^Not Before:\s*(.+)$/m)

    // Required fields
    if (!domainMatch || !uriMatch || !versionMatch || !chainIdMatch || !nonceMatch) return null

    // Domain validation
    if (expectedDomain && domainMatch[1].trim() !== expectedDomain) return null

    // Chain ID validation
    if (expectedChainId && parseInt(chainIdMatch[1]) !== expectedChainId) return null

    // Version must be 1
    if (versionMatch[1].trim() !== '1') return null

    // Expiration check
    if (expMatch) {
      const expTime = new Date(expMatch[1].trim()).getTime()
      if (isNaN(expTime) || Date.now() > expTime) return null
    }

    // Not Before check
    if (nbfMatch) {
      const nbfTime = new Date(nbfMatch[1].trim()).getTime()
      if (isNaN(nbfTime) || Date.now() < nbfTime) return null
    }

    return recoveredAddress
  } catch {
    return null
  }
}
