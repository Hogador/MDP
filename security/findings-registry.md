# Security Findings Registry — MDAOPay

Status: ✅ fixed | 🔒 closed | ⏳ pending | ❌ rejected | 🔄 in audit

## Phase 1 — CRITICAL

| ID | Finding | Status | Fix | Commit |
|----|---------|--------|-----|--------|
| C-1 | Backend signature: no EIP-712, no nonce, mutable signer | ✅ fixed | EIP-712 domain separator, QUOTE_TYPEHASH, immutable trustedSigner, quoteNonces, suffix in paymasterData | current |
| C-6 | ECDSA malleability (low-S) | 🔒 closed | ECDSA.recover already used — OZ handles | `b36e7b8` |

## Phase 2 — HIGH

| ID | Finding | Status | Fix | Commit |
|----|---------|--------|-----|--------|
| C-4 | Anti-griefing: blanket block on all failures | ✅ fixed | Threshold 3→5, cooldown 1h→30min, failure type differentiation, counter reset on success | current |
| C-5 | Centralization: instant withdraw, no price cooldown | ✅ fixed | 48h timelock (schedule/execute/cancel), 15min PRICE_COOLDOWN | current |

## Phase 3 — MEDIUM

| ID | Finding | Status | Fix | Commit |
|----|---------|--------|-----|--------|
| C-2 | Social recovery: upfront guardian signatures → async flow | ⏳ pending | PRD §7 async initiate/approve/execute, 5d effort | — |
| C-3 | Recovery expiry: no execution window | ⏳ pending | C-2 dependency, 1d effort | — |
| C-7 | WebAuthn P-256 verification (NOT raw ECDSA) | ✅ verified | PasskeyManager uses full WebAuthn (Android CredentialManager API). Contract _verifyP256 needs WebAuthn verify | — |
| C-8/C-9 | Backend signing alignment with EIP-712 | ✅ fixed | EIP-712 sig in paymasterData suffix (magic 0x22e325a297439656), backend raw ECDSA | current |
| C-10 | Relay race condition on addApproval | ⏳ pending | KV TOCTOU — needs Durable Objects for strong consistency | current |
| C-11 | Relay auth model | ✅ verified | HMAC transport auth (auth.ts) + P-256 on-chain verify for recovery | — |
| C-12 | AppConfig env var validation + missing vars | ✅ fixed | URL_REGEX, TRUSTED_SIGNER, IS_TESTNET, RELAY_SECRET added | current |
| C-13 | Over-sanitization (SEC-26) | ✅ fixed | Restored txHash, wallet, price, error in logs | current |
| C-14 | MockP256 deploy guard | 🔒 closed | Already has `require(block.chainid != 1 && block.chainid != 56)` | — |

## Previous fixes (pre-audit)

| ID | Finding | Status |
|----|---------|--------|
| F-01 | ECDSA.sign usage | ✅ → ECDSA.recover |
| F-04 | Capped failure threshold | ✅ MAX_BLOCK_FAILURE_THRESHOLD = 10 |
| F-06 | Minimum deadline buffer | ✅ min 60s |
| F-07 | Low-level refund call | ✅ prevents postOp revert |
| F-08 | 2-step ownership | ✅ transferOwnership + acceptOwnership |
| F-10 | ReentrancyGuard on postOp | ✅ nonReentrant |
| F-11 | Price==0 bypass | ✅ maxTokenAmount must be 0 |
| F-12 | OldPrice in event | ✅ PriceUpdated includes oldPrice |
| SEC-25-01 | Price buffer cap | ✅ max 20% |
| SEC-25-02 | Max token amount | ✅ 1M cap |
| SEC-25-03 | Max gas price | ✅ 1000 gwei cap |
| SEC-26-01..05 | Unsafe logging | ✅ tx.hash, address, price, error logged |
