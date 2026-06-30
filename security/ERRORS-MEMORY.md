# MDAOPay Errors Memory — спиральное обучение

> Этот файл хранит ПАТТЕРНЫ ошибок, которые повторялись.
> AI агенты ОБЯЗАНЫ читать его перед любым audit/fix.
> Цель: не повторять одни и те же ошибки.

---

## 0. PROTOCOL

Перед началом audit:
```bash
grep -A 5 "<pattern_keywords>" security/ERRORS-MEMORY.md
```

Перед fix:
```bash
grep -B 2 -A 10 "<bug_pattern>" security/ERRORS-MEMORY.md
```

После fix:
```bash
# Append new learning
echo "## EM-XXX: <pattern>" >> security/ERRORS-MEMORY.md
```

---

## 1. CRYPTOGRAPHY ERRORS

### EM-001: Double hashing with EIP-191
**Pattern:** `Hash.sha3(message) → Sign.signedMessageToKey(hash, sig)`
**Why wrong:** web3j's `signedMessageToKey` already applies EIP-191 prefix. Double hash.
**Correct:** `Sign.signedPrefixedMessageToKey(message.toByteArray(), sig)`
**First seen:** Wave 1 (F-14)
**Repeats:** 0 (fixed)

### EM-002: Raw ecrecover without s-value check
**Pattern:** `ecrecover(hash, v, r, s)` without checking `s <= secp256k1n/2`
**Why wrong:** Malleable signatures (s' = n - s) accepted → replay attacks
**Correct:** `ECDSA.recover(hash, signature)` from OpenZeppelin
**First seen:** Wave 4
**Repeats:** Wave 6, 7 (claimed fixed but wasn't)

### EM-003: EIP-191 instead of EIP-712 for structured data
**Pattern:** `"\x19Ethereum Signed Message:\n32"` for quotes/approvals
**Why wrong:** No cross-contract/cross-chain replay protection
**Correct:** EIP-712 with domain separator (name, version, chainId, verifyingContract)
**First seen:** Wave 4 (F-05)
**Repeats:** Wave 5 (deferred as "enhancement" — WRONG)

### EM-004: P-256 with Ethereum prefix instead of WebAuthn
**Pattern:** P-256 verification wrapping message in `\x19Ethereum Signed Message:\n32`
**Why wrong:** WebAuthn signs `authenticatorData || SHA256(clientDataJSON)`, not Ethereum-prefixed hash
**Correct:** Full WebAuthn verification (authenticatorData + clientDataJSON + P-256)
**First seen:** Wave 7 (C-7)

### EM-005: crypto.subtle.timingSafeEqual doesn't exist in CF Workers
**Pattern:** `crypto.subtle.timingSafeEqual(a, b)` in Cloudflare Workers
**Why wrong:** API doesn't exist
**Correct:** Manual constant-time comparison function
**First seen:** Wave 1 (C-8)
**Repeats:** 0 (fixed)

---

## 2. BLOCKCHAIN LOGIC ERRORS

### EM-010: Backend signature not verified by contract
**Pattern:** PaymasterService signs paymasterAndData, contract never verifies
**Why wrong:** Anyone can craft paymasterAndData, bypass rate limit and price checks
**Correct:** EIP-712 quote verification in validatePaymasterUserOp
**First seen:** Wave 4 (F-001)
**Repeats:** 0 (still open)

### EM-011: No chainId in message hash
**Pattern:** Recovery approve/veto hash without `block.chainid`
**Why wrong:** Cross-chain replay (sign on BSC Testnet, replay on BSC)
**Correct:** EIP-712 domain separator with chainId
**First seen:** Wave 4 (F-05)
**Repeats:** Wave 5 (claimed fixed, was only EIP-191)

### EM-012: initiateRecovery requires wallet owner
**Pattern:** `modifier onlyWalletOwner` on initiateRecovery
**Why wrong:** User with lost device can't sign UserOp → recovery impossible
**Correct:** Anyone can initiate with deposit, async guardian approvals
**First seen:** Wave 7 (C-2)

### EM-013: No execution window for recovery
**Pattern:** executeRecovery public, no upper time bound
**Why wrong:** Forever-approved recovery requests
**Correct:** `require(block.timestamp <= readyAt + TIMELOCK + EXECUTION_WINDOW)`
**First seen:** Wave 7 (C-3)

### EM-014: No anti-griefing in postOp
**Pattern:** Failed payment has no cooldown/blocklist
**Why wrong:** Attacker spams failing ops, paymaster pays gas
**Correct:** Differentiated failure handling (5 fails → 30 min block, not 3 → 1h)
**First seen:** Wave 7 (C-4)

---

## 3. SMART CONTRACT ERRORS

### EM-020: setPriceBufferBps no upper bound
**Pattern:** Admin function without max value check
**Why wrong:** Compromised owner can disable price protection
**Correct:** `require(newBuffer <= 2000)` (20% max)
**First seen:** Wave 5 (F-01)
**Repeats:** 0 (fixed)

### EM-021: "Mitigated by multisig" without on-chain enforcement
**Pattern:** Claiming multisig protection in TDD, no code enforcement
**Why wrong:** TDD ≠ code. If multisig compromised or EOA used — no protection
**Correct:** TimelockController + AccessControl roles in contract
**First seen:** Wave 5 (F-06, F-11)
**Repeats:** Wave 6, 7 (3 waves, same false claim)

### EM-022: RefundVault withdrawable by owner
**Pattern:** `withdrawToken` function in RefundVault
**Why wrong:** User refunds at risk — owner can drain
**Correct:** No withdraw function, only claimRefund(user)
**First seen:** Wave 5 (F-11)

### EM-023: MockP256 in production
**Pattern:** MockP256.sol with `verify() returns 1` deployable to mainnet
**Why wrong:** Anyone can forge P-256 signatures
**Correct:** Foundry production profile excludes test/, constructor chain guard
**First seen:** Wave 4 (F-06)

### EM-024: safeTransfer ≠ reentrancy protection
**Pattern:** Claiming "uses OZ safeTransfer, no reentrancy"
**Why wrong:** safeTransfer only handles non-standard ERC20 return values, not reentrancy
**Correct:** ReentrancyGuard + CEI pattern
**First seen:** Wave 5 (FP-7)

---

## 4. SECRETS & CONFIG ERRORS

### EM-030: Hardcoded PostgreSQL password
**Pattern:** `POSTGRES_PASSWORD: mdaopay` in docker-compose.yml
**Why wrong:** Default password if deployed without override
**Correct:** `${POSTGRES_PASSWORD:?required}` (fail-fast if missing)
**First seen:** Wave 4 (S-1)
**Repeats:** Wave 5, 6, 7 (claimed fixed, wasn't)

### EM-031: Placeholder private keys with valid hex format
**Pattern:** `PAYMASTER_PRIVATE_KEY=0x...` in .env.example
**Why wrong:** If developer copies to .env with real key, scanner may miss
**Correct:** `PAYMASTER_PRIVATE_KEY=0xYOUR_PRIVATE_KEY_HERE` (non-hex placeholder)
**First seen:** Wave 5

### EM-032: No git history scan
**Pattern:** Claiming "no secrets in git" based on `git ls-files` only
**Why wrong:** ls-files shows tracked files, not history
**Correct:** `git log --all -S "PRIVATE_KEY" -- '*.env*'` or trufflehog
**First seen:** Wave 4

---

## 5. LOGGING ERRORS

### EM-040: Blanket ${e.message} removal
**Pattern:** Removing all `${e.message}` from logs without differentiation
**Why wrong:** Different exceptions leak different info. Destroys observability.
**Correct:** Context-aware LogSanitizer utility
**First seen:** Wave 6 (L-05)
**Repeats:** Wave 7 (SEC-27-XX, 14 more) — REGRESSION

### EM-041: Removing txHash from logs
**Pattern:** Deleting txHash from swap logs for "privacy"
**Why wrong:** txHash is public blockchain data. Breaks debugging.
**Correct:** Keep txHash, structured logging with `pii=false`
**First seen:** Wave 6 (L-01)

### EM-042: Removing wallet addresses from watchtower
**Pattern:** Generic "Recovery initiated" without wallet
**Why wrong:** Cannot correlate incidents, debug failures
**Correct:** Short hash `0x1234...5678` for correlation
**First seen:** Wave 6 (L-02)

### EM-043: Error propagation sanitized
**Pattern:** `Result.failure(IllegalArgumentException("Invalid signature"))` without details
**Why wrong:** User can't debug what's wrong with their signature
**Correct:** Error codes (SIG_MALFORMED, SIG_WRONG_LENGTH, SIG_ADDRESS_MISMATCH)
**First seen:** Wave 7 (SEC-27-05)

---

## 6. PROCESS ERRORS

### EM-050: Marking "fixed" without regression test
**Pattern:** Commit message says "fixed" but no test added
**Why wrong:** Regression undetectable, future agent re-discovers
**Correct:** Mandatory regression test per fix, name in FINDINGS.md
**First seen:** Wave 6
**Repeats:** All subsequent waves

### EM-051: Severity downgrade without justification
**Pattern:** F-06 HIGH → MEDIUM without reason in lifecycle
**Why wrong:** Severity drift, stakeholders lose trust
**Correct:** Document reason, reference §0 downgrade rules
**First seen:** Wave 5

### EM-052: Coordinator writes code after failed subagents
**Pattern:** Subagent fails → coordinator does the work
**Why wrong:** Coordinator on free model = lower quality. Defeats specialization.
**Correct:** Retry with upgraded model, escalate to human if all fail
**First seen:** Wave 5
**Repeats:** Wave 6, 7

### EM-053: Re-discovering same finding
**Pattern:** 51% of findings are duplicates across waves
**Why wrong:** Wastes tokens, creates noise
**Correct:** Fingerprint dedup against FINDINGS.md before reporting
**First seen:** Wave 5

### EM-054: Claiming "fixed" in commit message but diff shows otherwise
**Pattern:** Commit says "use OZ ECDSA.recover" but code has raw ecrecover
**Why wrong:** Misleading, audit trail corrupted
**Correct:** Code review checklist: verify diff matches commit message
**First seen:** Wave 4 (F-2)

---

## 7. ARCHITECTURE ERRORS

### EM-060: Public RPC for mobile app
**Pattern:** Hardcoded publicnode/ankr URLs in RpcProviderManager
**Why wrong:** Rate limited, no SLA, guaranteed outage at scale
**Correct:** Private RPC (Alchemy/Infura) with API key, multi-provider failover
**First seen:** Wave 6 (R-01)

### EM-061: No certificate pinning
**Pattern:** No CertificatePinner in Android app
**Why wrong:** MITM with rogue CA possible
**Correct:** CertificatePinner for backend domains, Network Security Config
**First seen:** Wave 6

### EM-062: Single RPC URL without failover
**Pattern:** One RPC URL in AppConfig
**Why wrong:** PRD §18 requires 3 providers with failover
**Correct:** List<RpcProvider> with priority, health score, cooldown
**First seen:** Wave 7 (C-12)

### EM-063: Nickname race condition
**Pattern:** `synchronized(this)` + ConcurrentHashMap for nickname uniqueness
**Why wrong:** Works only in single JVM, fails on horizontal scaling
**Correct:** DB unique constraint + Redis SETNX for optimistic check
**First seen:** Wave 7 (C-10)

### EM-064: redis-fail-open-critical
**Pattern:** Redis.incr → null → return false при падении Redis
**Why wrong:** Fail-open для rate-limiting и replay-protection — security-защита полностью отключается без предупреждения в момент инфраструктурной деградации.
**Fix:** Добавить in-memory fallback (ConcurrentHashMap) когда Redis недоступен. Логировать warn при переключении на fallback.

### EM-065: Backend↔Contract signing scheme mismatch
**Pattern:** Backend подписывает EIP-191(userOpHash), контракт верифицирует EIP-712(Quote)
**Why wrong:** Разные схемы подписания — все UserOp падают с InvalidSigner(). Полная несовместимость.
**Correct:** Backend должен подписывать EIP-712 Quote с тем же domain separator, что контракт ожидает в _verifyQuoteSignature.
**First seen:** Wave 9 (F-034)

### EM-066: Swap endpoint without authentication
**Pattern:** `/swap/execute` и `/swap/quote` используют PAYMASTER_PRIVATE_KEY, но не проверяют X-API-Key
**Why wrong:** Любой может подписать swap-транзакцию ключом пеймастера — funds drain.
**Correct:** Добавить X-API-Key аутентификацию на swap endpoints (как на /sign).
**First seen:** Wave 9 (F-035)

### EM-067: Wrong identity hash computation
**Pattern:** `address.lowercase().toByteArray()` (ASCII 42 байта) вместо keccak256(abi.encodePacked(signer)) (20 байт)
**Why wrong:** Хеш не совпадает с контрактом — isIdentityRegistered() всегда возвращает неправильный результат.
**Correct:** `Numeric.hexStringToByteArray(address)` → `Hash.sha3(addressBytes)`
**First seen:** Wave 9 (F-036)

### EM-068: API key in widget URL
**Pattern:** API key передаётся как query-параметр в widget URL и возвращается клиенту
**Why wrong:** Ключ виден в логах, Referrer-заголовках, client-side инспекции — exposure.
**Correct:** Использовать server-side session token (MoonPay sessionToken flow).
**First seen:** Wave 9 (F-037)

### EM-069: WebView unrestricted JavaScript
**Pattern:** WebView с javaScriptEnabled=true, domStorageEnabled=true, без shouldOverrideUrlLoading, без allowFileAccess=false
**Why wrong:** XSS в контексте WebView — доступ к Android JavaScript bridge, storage, кукам.
**Correct:** shouldOverrideUrlLoading с whitelist доменов, allowFileAccess=false, allowContentAccess=false.
**First seen:** Wave 9 (F-038)

### EM-070: wrangler dev in production Dockerfile
**Pattern:** `CMD ["npx", "wrangler", "dev", ...]` в Dockerfile
**Why wrong:** Dev-сервер не предназначен для production — падение под нагрузкой, утечка памяти.
**Correct:** `CMD ["npx", "wrangler", "deploy"]` или `wrangler publish`.
**First seen:** Wave 11 (F-042)

### EM-071: CI without security scanning
**Pattern:** CI pipeline runs only tests, no trivy/codeql/secret-scanning
**Why wrong:** Vulnerabilities and secrets reach main without detection.
**Correct:** Add CodeQL, Trivy, gitleaks/trufflehog to CI pipeline. Restrict GITHUB_TOKEN permissions.
**First seen:** Wave 11 (F-043)

### EM-072: FCM push notifications non-functional
**Pattern:** `declare const FCM_SERVER_KEY` in Cloudflare Worker modules format
**Why wrong:** In modules format, env vars are accessed via function parameter `env`, not global `declare const`. FCM_SERVER_KEY always undefined.
**Correct:** `export async function sendPushNotification(env: Env, ...)` — use `env.FCM_SERVER_KEY`.
**First seen:** Wave 11 (F-065)

### EM-073: JS bridge wallet signing without domain restriction
**Pattern:** @JavascriptInterface exposing personal_sign, eth_requestAccounts in WebView without domain whitelist
**Why wrong:** Any HTTPS page in WebView can call wallet signing methods via bridge.
**Correct:** Domain whitelist in shouldOverrideUrlLoading, user confirmation dialogs for signing, clear bridge on navigation.
**First seen:** Wave 11 (F-059)

### EM-074: Play Integrity verdict without JWT verification
**Pattern:** Client-side Base64 decode of JWT payload without signature verification
**Why wrong:** MITM attacker can forge integrity verdict.
**Correct:** Verify JWT signature against Google public key (JWKS), or send verdict to backend for verification.
**First seen:** Wave 11 (F-060)

### EM-075: Sensitive data in plaintext SharedPreferences
**Pattern:** PRF evalInput (32 bytes) stored as HEX string without encryption alongside encrypted recovery shares
**Why wrong:** Attacker with root access can combine plaintext evalInput with encrypted share to recover PRF.
**Correct:** Use EncryptedSharedPreferences or encrypt evalInput via KeystoreCrypto before storage.
**First seen:** Wave 11 (F-061)

---

## CHANGELOG

| Date | Wave | New patterns added |
|------|------|-------------------|
| 2026-06-24 | 4 | EM-001..EM-032 (crypto, blockchain, secrets) |
| 2026-06-24 | 5 | EM-020..EM-024 (contracts), EM-030..EM-032 (secrets) |
| 2026-06-25 | 6 | EM-040..EM-043 (logging) |
| 2026-06-25 | 7 | EM-004, EM-012..EM-014, EM-043, EM-060..EM-063 |
| 2026-06-25 | 7 | EM-064 (redis-fail-open-critical) |
| 2026-06-26 | 9 | EM-065..EM-069 (signing-scheme-mismatch, swap-unauth, hash-computation-wrong, api-key-in-url, webview-unrestricted) |
| 2026-06-26 | 11 | EM-070..EM-075 (wrangler-dev, ci-no-scan, fcm-broken, js-bridge-signing, play-integrity-no-jwt, plaintext-sensitive-storage) |
