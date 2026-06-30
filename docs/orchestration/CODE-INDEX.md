# Code Index
> Read this first. For full code: read the actual file.
> Last updated: 2026-06-26 (Wave 11)

## Contracts (12 files)

### MDAOPaymaster.sol (470 lines)
**Purpose:** ERC-4337 paymaster, post-paid gas deduction
**Key functions:**
- validatePaymasterUserOp() L92 — trustedSigner ⚠️ F-001, F-006
- postOp() L379 — gas charge + refund ⚠️ F-004, F-007
- setPriceBufferBps() L246 ⚠️ F-009
- setMaxTokenAmountLimit() L219 ⚠️ F-010
- setMaxGasPrice() L224 ⚠️ F-011
- setTokenPrice() L233 ⚠️ F-005
- withdrawTokens() L470 ⚠️ F-018
- setCooldownPeriod() L562 ⚠️ F-051 (no bounds)
**Security:** ecrecover L310 (RAW), ReentrancyGuard L382, 2-step ownership
**Tests:** MDAOPaymaster.t.sol (20 tests)

### SocialRecoveryModule.sol (350 lines)
**Purpose:** 2-of-3 social recovery with P-256
**Key functions:**
- initiateRecovery() L85 ⚠️ F-002
- approveRecovery() L150 ⚠️ F-012, F-020
- executeRecovery() L220 ⚠️ F-003
- cleanupExpiredRecovery() L282 ⚠️ F-049 (burned deposit)
**Security:** EIP-191 prefix L206 (NOT EIP-712)
**Tests:** SocialRecoveryModule.t.sol (29 tests)

### MDAOToken.sol (120 lines)
**Purpose:** ERC-20 with burn fee
- transfer() L40 ⚠️ F-029
- setBurnFeeBps() L60 (rate limited)

### NicknameRegistry.sol (180 lines)
**Purpose:** Nickname → address mapping
- EIP-712 domain separator L84 (✅ has chainId)

### SessionKeyModule.sol (200 lines)
**Purpose:** Session keys for dApps
- createSession() L60, executeWithSession() L100, revokeSession() L150

### RefundVault.sol (80 lines)
**Purpose:** User refunds
- withdrawToken() L52 ⚠️ F-008

### InsuranceFund.sol (70 lines)
- collectFee() L27 ⚠️ F-027
- submitClaim() L35 ⚠️ F-052

### DeadManSwitch.sol (100 lines)
- claimFunds() L83 ⚠️ F-028, F-048

### AttestationLedger.sol (60 lines)
- attest() L11 ⚠️ F-050 (no ACL)

### TrustProviderRegistry.sol (72 lines)
**Purpose:** Trusted provider registry with 2-step ownership
- registerProvider() L32, setProviderStatus() L39, verify() L46

### ITrustProvider.sol (6 lines)
**Purpose:** Interface for trust provider verification
- verify() L5

### EcdsaVerifier.sol (22 lines)
**Purpose:** ECDSA-based trust provider
- verify() L19

## Backend (19 .kt files)

### PaymasterService.kt (250 lines)
- sign() L40 ⚠️ F-022
- MAX_NONCE_GAP L25 ⚠️ F-016
- fallbackPrices L114 ⚠️ F-017

### AuthService.kt (180 lines)
- hashPassword() L135 — PBKDF2 600k
- per-call Mac.getInstance (C-6 fix)
- register() L35 ⚠️ F-055 (weak password policy)

### NicknameService.kt (200 lines)
- register() L60 ⚠️ F-019
- verify() L140 ⚠️ F-021

### WatchtowerService.kt (220 lines)
- Over-sanitized logs ⚠️ F-013
- notifyWebhook() L238 ⚠️ F-053 (coroutine leak)

### PriceOracle.kt (150 lines)
- deviation check L33 ⚠️ F-013

### Application.kt (250 lines)
- /sign route L160, /etherscan proxy L200 ⚠️ F-056
- /auth/login, /auth/register L276-337 ⚠️ F-054 (no rate limit)

### RedisClient.kt, Database.kt
- Over-sanitized error logs

### AppConfig.kt (100 lines)
- Single RPC URL ⚠️ F-025

## Relay (5 .ts files)

### index.ts (320 lines)
- requireAuth() L78 ⚠️ F-014, F-071
- POST /guardian/invite L108
- GET /guardian/invite/:inviteId L136 ⚠️ F-066 (no auth)
- POST /guardian/invite/:inviteId/accept L155 ⚠️ F-015 → CLAIMED_FIXED (P-256 verify), F-067 → CLAIMED_FIXED (guardian check)
- GET /recovery/pending/:walletAddress L185 ⚠️ F-068 (no auth)
- POST /recovery/approve L194 ⚠️ F-015 → CLAIMED_FIXED (P-256 verify)
- POST /recovery/veto L243 ⚠️ F-015 → CLAIMED_FIXED (P-256 verify)
- POST /push/register L275
- POST /recovery/notify L285
- No request size limit ⚠️ F-069
- No rate limiting ⚠️ F-070

### fcm.ts (33 lines)
- sendPushNotification() ⚠️ F-065 (FCM broken)

### wrangler.toml
- No rate limiting config ⚠️ F-070

### auth.ts (80 lines)
- constantTimeEqual (C-8 fix)

## App (Android)
### RpcProviderManager.kt (60 lines)
- Public RPC ⚠️ F-023

### PasskeyManager.kt
- WebAuthn vs raw P-256 ⚠️ F-020
- rpId hardcoded ⚠️ F-063

### EthereumProviderInjector.kt (60 lines)
- @JavascriptInterface wallet bridge ⚠️ F-059

### DeviceIntegrityManager.kt (120 lines)
- Play Integrity no JWT verify ⚠️ F-060
- isRooted() software-only ⚠️ F-064

### RecoveryShareManager.kt (80 lines)
- evalInput plaintext storage ⚠️ F-061

### BiometricManager.kt (40 lines)
- BIOMETRIC_WEAK allowed ⚠️ F-062

### relay/Dockerfile
- CMD wrangler dev ⚠️ F-042 (dev server in production)

### .github/workflows/ci.yml
- No security scanning ⚠️ F-043

### backend/docker-compose.yml
- Missing depends_on ⚠️ F-047

## How to use
1. Read this file (150 lines) instead of all code (8000+)
2. Find relevant files: ⚠️ F-XXX markers
3. Read only relevant files/lines
4. After changes: librarian updates this index
