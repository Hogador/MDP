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

// ponytail: SIWE verification via ethers — handles EIP-191 prefix + ecrecover in one call
export function verifySiweSignature(message: string, signature: string): string | null {
  try {
    const recoveredAddress = verifyMessage(message, signature)
    // EIP-4361: address appears on second line after "\n\n"
    const addressMatch = message.match(/^(0x[a-fA-F0-9]{40})/m)
    if (!addressMatch) return null
    if (recoveredAddress.toLowerCase() !== addressMatch[1].toLowerCase()) return null
    return recoveredAddress
  } catch {
    return null
  }
}
