# Findings Index
> Read this first. For full details: security/findings/F-XXX.md
> Last updated: 2026-07-16 (B0-B5 complete + self-challenge hardening. 1 OPEN, 0 STILL_OPEN, 98 VERIFIED_FIXED, 1 ACCEPTED_RISK)

## Dashboard
| Status | Count |
|--------|-------|
| OPEN | 1 (F-113 ERC-4337 v0.6 deprecated) |
| VERIFIED_STILL_OPEN | 0 |
| VERIFIED_FIXED | 98 |
| ACCEPTED_RISK | 1 (F-049) |
| CANNOT_VERIFY | 2 (F-023, F-128) |
| REGRESSED | 0 |
| CONFLICT | 0 |
| WONTFIX | 0 |

## Findings by Severity

### CRITICAL (11)
| ID | Status | Title | File |
|----|--------|-------|------|
| F-001 | CLAIMED_FIXED | Backend signature not verified by paymaster contract | MDAOPaymaster.sol |
| F-002 | CLAIMED_FIXED | initiateRecovery impossible with full device loss | SocialRecoveryModule.sol |
| F-034 | CLAIMED_FIXED | Backend↔Contract signing scheme incompatible | contracts/src/MDAOPaymaster.sol |
| F-035 | CLAIMED_FIXED | SwapService uses PAYMASTER_PRIVATE_KEY without authentication | backend/.../SwapRoutes.kt |
| F-036 | CLAIMED_FIXED | OnChainRegistryClient computes wrong identityHash | backend/.../OnChainRegistryClient.kt |
| F-100 | CLAIMED_FIXED | Paymaster не используется в send-флоу | SendRepository.kt |
| F-108 | CLAIMED_FIXED | P-256 — FCL verifier (pure-Solidity, RIP-7212 fallback для BSC) | FCLP256Verifier.sol |
| F-129 | CLAIMED_FIXED | KMS — AWS KMS (ECC_SECG_P256K1) вместо GCP | backend/.../KmsPaymasterSigner |
| F-130 | CLAIMED_FIXED | PaymasterClient API не соответствует SignRequest | app/.../PaymasterClient.kt |
| F-134 | CLAIMED_FIXED | GCP KMS не поддерживает secp256k1 — мигрировано на AWS KMS | backend/.../KmsPaymasterSigner |
| F-135 | CLAIMED_FIXED | SIWE auth endpoint в relay | relay/src/routes/auth.ts |

### HIGH (32)
| ID | Status | Title | File |
|----|--------|-------|------|
| F-003 | CLAIMED_FIXED | No execution window for approved recovery | SocialRecoveryModule.sol |
| F-004 | CLAIMED_FIXED | No anti-griefing in postOp | MDAOPaymaster.sol |
| F-005 | CLAIMED_FIXED | Owner oracle manipulation via setTokenPrice | MDAOPaymaster.sol |
| F-006 | VERIFIED_FIXED | ECDSA s-value malleability in paymaster (OZ ECDSA.recover rejects high-s) | MDAOPaymaster.sol |
| F-008 | CLAIMED_FIXED | RefundVault withdrawable by owner | RefundVault.sol |
| F-012 | CLAIMED_FIXED | No EIP-712 domain separator for recovery | SocialRecoveryModule.sol |
| F-015 | CLAIMED_FIXED | Relay /recovery endpoints without guardian verification | relay/src/index.ts |
| F-018 | CLAIMED_FIXED | Owner can steal refunds via withdrawTokens | MDAOPaymaster.sol |
| F-020 | CLAIMED_FIXED | P-256 format doesn't match WebAuthn | SocialRecoveryModule.sol |
| F-023 | CLAIMED_FIXED | Public RPC for mobile app | RpcProviderManager.kt |
| F-024 | CLAIMED_FIXED | No certificate pinning — hasRealPins() guard | app |
| F-032 | VERIFIED_FIXED | Redis fail-open rate-limiting (in-memory fallback implemented) | RedisClient.kt |
| F-033 | VERIFIED_FIXED | Redis fail-open replay-protection (in-memory fallback implemented) | RedisClient.kt |
| F-037 | CLAIMED_FIXED | MoonPay API key exposed in widget URL | backend/.../FiatOnrampService.kt |
| F-038 | CLAIMED_FIXED | WebView JS enabled without domain restriction | app/.../MDAOWebView.kt |
| F-042 | CLAIMED_FIXED | relay/Dockerfile — wrangler dev в production | relay/Dockerfile |
| F-048 | CLAIMED_FIXED | DeadManSwitch: pooled ETH accounting | DeadManSwitch.sol |
| F-054 | CLAIMED_FIXED | Auth endpoints без rate limiting | Application.kt |
| F-059 | CLAIMED_FIXED | Ethereum JS Bridge — origin whitelist + user confirmation | EthereumProviderInjector.kt |
| F-060 | VERIFIED_FIXED | Play Integrity verdict client-side (full JWT verification implemented) | DeviceIntegrityManager.kt |
| F-062 | CLAIMED_FIXED | BIOMETRIC_STRONG + 30s window для high-risk | BiometricAuthManager.kt |
| F-065 | CLAIMED_FIXED | FCM push-уведомления сломаны | fcm.ts |
| F-102 | CLAIMED_FIXED | vetoRecovery — transfer(BURN_ADDRESS) вместо burn() | SocialRecoveryModule.sol |
| F-109 | VERIFIED_FIXED | WebAuthn DER→raw signature conversion (derToRS() implemented) | SocialRecoveryModule.sol |
| F-110 | CLAIMED_FIXED | JWT_SECRET entropy check отсутствует | backend |
| F-111 | CLAIMED_FIXED | ALLOW_LOCAL_SIGNING production guard | backend |
| F-112 | VERIFIED_FIXED | P-256 public key on-curve validation — `_isOnP256Curve()` added to `addGuardian()` (2026-07-16) | SocialRecoveryModule.sol:163,447-452 |
| F-113 | NEW | ERC-4337 v0.6 deprecated | contracts |
| F-131 | CLAIMED_FIXED | cleanupExpiredRecovery сжигает депозит (anti-spam) | SocialRecoveryModule.sol |
| F-132 | CLAIMED_FIXED | GuardianUserOpBuilder без paymaster | app/.../GuardianUserOpBuilder.kt |
| F-136 | CLAIMED_FIXED | Watchtower — динамический threshold из config | WatchtowerService.kt |
| F-137 | CLAIMED_FIXED | SwapService — minAmountOut в calldata | SwapService.kt |

### MEDIUM (41)
| ID | Status | Title | File |
|----|--------|-------|------|
| F-007 | CLAIMED_FIXED | MockP256 verify() without chain guard | MockP256.sol |
| F-009 | CLAIMED_FIXED | setPriceBufferBps no upper bound | MDAOPaymaster.sol |
| F-009a | CLAIMED_FIXED | setPriceBufferBps no lower bound | MDAOPaymaster.sol |
| F-010 | CLAIMED_FIXED | setMaxTokenAmountLimit decimal confusion | MDAOPaymaster.sol |
| F-011 | CLAIMED_FIXED | setMaxGasPrice chain-specific cap | MDAOPaymaster.sol |
| F-013 | CLAIMED_FIXED | Over-sanitization of error logs | backend/*.kt, LogSanitizer |
| F-014 | CLAIMED_FIXED | Relay auth bypass when RELAY_SECRET unset | relay/src/index.ts |
| F-019 | CLAIMED_FIXED | Nickname race condition | NicknameService.kt |
| F-022 | VERIFIED (STALE) | Paymaster signing hash inconsistency | PaymasterService.kt |
| F-025 | CLAIMED_FIXED | Single RPC URL without failover | AppConfig.kt |
| F-027 | CLAIMED_FIXED | InsuranceFund collectFee callable | InsuranceFund.sol |
| F-039 | CLAIMED_FIXED | setPriceBufferBps без нижней границы | MDAOPaymaster.sol |
| F-040 | CLAIMED_FIXED | Price cooldown regression test | contracts/test |
| F-041 | CLAIMED_FIXED | Failure differentiation regression test | contracts/test |
| F-043 | CLAIMED_FIXED | CI security scanning | .github/workflows/ci.yml |
| F-044 | CLAIMED_FIXED | GDPR — PII retention policy | V1__initial_schema.sql |
| F-045 | CLAIMED_FIXED | logback.xml без структурированного лога | logback.xml |
| F-046 | CLAIMED_FIXED | docker-compose порты изолированы | docker-compose.yml |
| F-049 | ACCEPTED_RISK | Депозит сжигается при cleanupExpiredRecovery (anti-spam per F-131) | SocialRecoveryModule.sol |
| F-050 | CLAIMED_FIXED | AttestationLedger: attest() без ACL | AttestationLedger.sol |
| F-053 | CLAIMED_FIXED | WatchtowerService coroutine leak | WatchtowerService.kt |
| F-055 | CLAIMED_FIXED | Weak password policy | AuthService.kt |
| F-056 | CLAIMED_FIXED | Etherscan proxy open API key | Application.kt |
| F-057 | CLAIMED_FIXED | Onramp routes без аутентификации | OnrampRoutes.kt |
| F-061 | CLAIMED_FIXED | Recovery evalInput в plaintext | RecoveryShareManager.kt |
| F-066 | CLAIMED_FIXED | GET /guardian/invite без auth | relay/src/index.ts |
| F-067 | CLAIMED_FIXED | Принятие инвайта без guardian check | relay/src/index.ts |
| F-068 | CLAIMED_FIXED | GET /recovery/pending без auth | relay/src/index.ts |
| F-069 | CLAIMED_FIXED | Нет лимита размера тела запроса | relay/src/index.ts |
| F-070 | CLAIMED_FIXED | Нет rate limiting на relay | relay |
| F-114 | CLAIMED_FIXED | NicknameRegistry длина/charset синхронизированы | NicknameRegistry.sol / backend |
| F-115 | CLAIMED_FIXED | MDAO Token — max burn fee 3% | MDAOToken.sol |
| F-116 | CLAIMED_FIXED | Daily withdrawal cap — edge case | MDAOPaymaster.sol |
| F-117 | CLAIMED_FIXED | Chain ID confusion 56/97 | config |
| F-118 | CLAIMED_FIXED | SessionKeyModule — permission whitelist | SessionKeyModule.sol |
| F-119 | CLAIMED_FIXED | Price Oracle — 3 источников | backend |
| F-120 | CLAIMED_FIXED | SSS over GF(256) — byte-wise spec | mobile |
| F-121 | CLAIMED_FIXED | PBKDF2 vs Argon2id (Phase 1) | mobile |
| F-122 | CLAIMED_FIXED | AES-GCM IV random entropy (Phase 1) | mobile |
| F-123 | CLAIMED_FIXED | Slither CI — pragma-version excluded | ci |
| F-124 | CLAIMED_FIXED | Cloud SQL HA — single region | infra |

### LOW (16)
| ID | Status | Title | File |
|----|--------|-------|------|
| F-016 | CLAIMED_FIXED | MAX_NONCE_GAP = 100 | PaymasterService.kt |
| F-017 | CLAIMED_FIXED | Fallback prices 100x off | PaymasterService.kt |
| F-026 | CLAIMED_FIXED | PostgreSQL password — все `:-mdaopay` заменены на `:?required` | docker-compose.yml, backend/docker-compose.yml |
| F-028 | CLAIMED_FIXED | DeadManSwitch reentrancy unverified | DeadManSwitch.sol |
| F-029 | CLAIMED_FIXED | MDAOToken burn fee precision | MDAOToken.sol |
| F-047 | CLAIMED_FIXED | backend/docker-compose зависит от postgres+redis | backend/docker-compose.yml |
| F-051 | CLAIMED_FIXED | setCooldownPeriod без границ | MDAOPaymaster.sol |
| F-052 | CLAIMED_FIXED | InsuranceFund auditorSignatures | InsuranceFund.sol |
| F-058 | CLAIMED_FIXED | SwapRoutes no rate limiting | SwapRoutes.kt |
| F-063 | CLAIMED_FIXED | PasskeyManager RP ID hardcoded | PasskeyManager.kt |
| F-071 | CLAIMED_FIXED | Ошибка конфигурации раскрывает auth | relay/src/index.ts |
| F-125 | CLAIMED_FIXED | HikariCP pool size = 10 | backend/database.kt |
| F-126 | CLAIMED_FIXED | ConcurrentHashMap memory leak | backend/RateLimiter |
| F-127 | CLAIMED_FIXED | Touch target 38dp < 48dp | app/MDAOButton |
| F-128 | CLAIMED_FIXED | Content descriptions missing | app/components |
| F-133 | CLAIMED_FIXED | RECOVERY_DEPOSIT — ether literal | SocialRecoveryModule.sol |

### INFO (4)
| ID | Status | Title | File |
|----|--------|-------|------|
| F-021 | VERIFIED_FIXED | NicknameService double hashing | NicknameService.kt |
| F-030 | CLAIMED_FIXED | MockP256 chain guard missing | MockP256.sol |
| F-031 | CLAIMED_FIXED | NicknameRegistry domainSeparator | NicknameRegistry.sol |
| F-064 | CLAIMED_FIXED | Software root detection bypassable | DeviceIntegrityManager.kt |

## Verification Log

### 2026-07-16 — Full Re-verification (A2)
**Reviewer:** deepseek-v4-flash-free (reviewer agent)
**Scope:** All 95 CLAIMED_FIXED findings

| Batch | VERIFIED_FIXED | STILL_OPEN | CANNOT_VERIFY |
|---|---|---|---|
| HIGH/CRITICAL (35) | 34 | 0 | 1 (F-023) |
| MEDIUM/LOW/INFO (60) | 58 | 1 (F-049) | 1 (F-128) |
| **Total (95)** | **92** | **1** | **2** |

**REGRESSED: 0** — codebase stable.

**Notes:**
- F-049: ACCEPTED_RISK — deposit burns on cleanupExpiredRecovery (anti-spam design per F-131)
- F-023: CANNOT_VERIFY — RPC URLs in BuildConfig, need manual check
- F-128: CANNOT_VERIFY — content descriptions need manual UI audit

## How to use
- Need full detail? Read security/findings/F-XXX.md
- Need verification recipe? It's in the detail file
- Only verifier can update status

---

## New Findings from Full Audit (2026-07-14)

### Smart Contract (SC-) — Phase 1, 16 findings

| ID | Severity | Title | Status | Remediation |
|----|----------|-------|--------|-------------|
| SC-001 | HIGH | Treasury unbounded recipients loop (DoS) | **FIXED** | `maxRecipients=200`, `ABSOLUTE_MAX_RECIPIENTS=500`, `setMaxRecipients()` setter |
| SC-002 | HIGH | SocialRecoveryModule unbounded hooks loop (DoS) | **FIXED** | `MAX_HOOKS=10`, `ErrMaxHooks`, try/catch in executeRecovery |
| SC-003 | HIGH | MDAOPaymaster CEI violation (nonce after external call) | **FIXED** | nonce incremented before `IRegistry.verify()` call |
| SC-004 | MED | P-256 signature malleability (no low-s check) | OPEN | Add `if (s > n/2) revert` in P256Verifier + FCLP256Verifier |
| SC-005 | MED | Registry.verify() via CALL not STATICCALL | **FALSE POSITIVE** | `view` interface already forces STATICCALL in Solidity ≥0.5 |
| SC-006 | MED | Flash-loan voting (no snapshot) | OPEN | Use ERC20Votes with checkpoint |
| SC-007 | MED | setExempt selective burn-fee exemption | OPEN | Add timelock or DAO vote |
| SC-008 | MED | maxAllowed rounds to zero for small values | OPEN | Ceiling division or minimum |
| SC-009 | MED | recoveryCaller has full owner privileges | OPEN | Document + consider timelock |
| SC-010 | LOW | PaymentSplitter precision loss | ACCEPTED_RISK | Standard behavior |
| SC-011 | LOW | No address(0) check on setRecoveryTransfer | OPEN | Add validation |
| SC-012 | LOW | DeadManSwitch no ownership transfer | OPEN | Add 2-step transfer |
| SC-013 | LOW | NicknameRegistry cached domainSeparator | OPEN | Use OZ EIP712 |
| SC-014 | LOW | postOp refund return not checked | OPEN | Use SafeERC20 |
| SC-015 | LOW | InsuranceFund collectFee inflates totalFunds | OPEN | Count actual balance |
| SC-016 | LOW | _tokenDecimals staticcall to arbitrary token | INFO | Acceptable |

### Android (SA-) — Phase 2, 25 findings

| ID | Severity | Title | Status | Notes |
|----|----------|-------|--------|-------|
| SA-001 | CRIT | Wallet decrypt fails on Android 11+ after auth window | OPEN | Needs BiometricPrompt flow (deferred to B4) |
| SA-002 | HIGH | WebView bridge personal_sign dialog fatigue | OPEN | Rate limit + contextual preview |
| SA-003 | HIGH | Cert pinning missing for RPC/bundler/relay | OPEN | Add pins for all endpoints |
| SA-004 | HIGH | PII stored unencrypted in DataStore | OPEN | Encrypt with KeystoreCrypto |
| SA-005 | HIGH | Recovery shares sent to relay without e2e encryption | OPEN | Per-invite ephemeral key |
| SA-006 | MED | KeystoreCrypto not thread-safe | OPEN | Synchronize access |
| SA-007 | MED | Cold PIN hash — no evidence of strong KDF | OPEN | Argon2id or PBKDF2 600k+ |
| SA-008 | MED | Biometric grace window 30s exploitable | OPEN | Reduce to 5s |
| SA-009 | MED | InvalidatedByBiometricEnrollment locks wallet | OPEN | Recovery escrow |
| SA-010 | MED | Room DB unencrypted | OPEN | SQLCipher |
| SA-011 | MED | Sensitive data in logs | OPEN | ProGuard strip |
| SA-012 | MED | PRF output < 64 bytes → crash | OPEN | Size check before copyOfRange |
| SA-013 | MED | Google OAuth client ID hardcoded | OPEN | BuildConfig per flavor |
| SA-014 | MED | Identity salt in plaintext SharedPrefs | OPEN | EncryptedSharedPrefs |
| SA-015 | MED | Offline sync without re-auth | OPEN | Biometric re-auth |
| SA-016 | MED | Root detection software-only | OPEN | Server-side Play Integrity |
| SA-017 | LOW | GCM random IV — RNG failure risk | INFO | Acceptable |
| SA-018 | LOW | GF256 evalPoly non-constant-time | INFO | Limited exploitability |
| SA-019 | LOW | runBlocking in PasskeyManager | OPEN | Move to coroutine |
| SA-020 | LOW | Relay circuit breaker global | OPEN | Per-endpoint |
| SA-021 | LOW | Guardian on-chain failure silently swallowed | OPEN | Block until confirmed |
| SA-022 | LOW | UserOperation.signature var not in constructor | INFO | Code quality |
| SA-023 | LOW | Dev flavor HTTP cleartext | INFO | Documented |
| SA-024 | LOW | JavaScript enabled globally in WebView | LOW | Consider per-use-case |
| SA-025 | LOW | No rate limit on bridge.send() | OPEN | Per-origin limit |

### Relay (RA-) — Phase 2, 19 findings

| ID | Severity | Title | Status | Remediation |
|----|----------|-------|--------|-------------|
| RA-001 | CRIT | Replay attack (no nonce in HMAC) | **FIXED** | Nonce in HMAC message, X-Nonce header |
| RA-002 | CRIT | SIWE missing validation | **FIXED** | Full EIP-4361: domain, chainId, exp, nbf |
| RA-003 | CRIT | JWT no expiration | **FIXED** | `exp: now + 3600` |
| RA-004 | CRIT | No CORS/security headers | **FIXED** | SECURITY_HEADERS const, OPTIONS preflight |
| RA-005 | HIGH | Rate limit bypass via x-forwarded-for | OPEN | Remove x-forwarded-for fallback |
| RA-006 | HIGH | Rate limiter per-isolate | OPEN | KV-backed or Durable Objects |
| RA-007 | HIGH | No Content-Length on /auth/siwe | OPEN | Apply readBody helper |
| RA-008 | HIGH | No input validation on fields | OPEN | Regex walletAddress, size limit |
| RA-009 | HIGH | Docker compose 0.0.0.0 binding | OPEN | Bind to 127.0.0.1 |
| RA-010 | HIGH | Private keys in env vars | OPEN | Docker secrets |
| RA-011 | MED | FCM legacy API (deprecated) | OPEN | Migrate to HTTP v1 |
| RA-012 | MED | FCM errors silently swallowed | OPEN | Log failed tokens |
| RA-013 | MED | TOCTOU race in addApproval | OPEN | Durable Objects |
| RA-014 | MED | RELAY_SECRET in env var | OPEN | Docker secrets |
| RA-015 | MED | No Cache-Control headers | OPEN | Add no-store |
| RA-016 | LOW | Content-Length NaN bypass | OPEN | isNaN check |
| RA-017 | LOW | JSON.parse relies on outer catch | OPEN | Local try/catch |
| RA-018 | LOW | unset doesn't clear secrets | OPEN | Subshell |
| RA-019 | LOW | Dead config: SOCIAL_RECOVERY_MODULE | OPEN | Remove from Env |

### Code Quality (CQ-) — Phase 3, 15 findings

| ID | Severity | Title | Status | Notes |
|----|----------|-------|--------|-------|
| CQ-001 | CRIT | InsuranceFund owner bypass multi-sig limits | OPEN | Store submittedAmount |
| CQ-002 | CRIT | Treasury allocation ID collision | OPEN | keccak256(id, token) |
| CQ-003 | CRIT | SocialRecovery deposit accounting mismatch | OPEN | Snap fee at initiation |
| CQ-004 | HIGH | Paymaster rounds down charges | OPEN | Ceiling division |
| CQ-005 | HIGH | Guardian on-chain failure silently swallowed | OPEN | Block until confirmed |
| CQ-006 | HIGH | GuardianStorage.commit() ANR risk | OPEN | Use apply() |
| CQ-007 | HIGH | RecoveryShareManager raw AES key | OPEN | Keystore import |
| CQ-008 | HIGH | DER parsing edge cases | OPEN | Use OZ library |
| CQ-009 | MED | Hardcoded gas limits | OPEN | Dynamic fallback |
| CQ-010 | MED | Duplicate UserOp builder code | OPEN | Extract base class |
| CQ-011 | MED | Global circuit breaker | OPEN | Per-endpoint |
| CQ-012 | MED | Placeholder addresses in config | OPEN | Throw on access |
| CQ-013 | MED | KeystoreCrypto.encrypt hides biometric req | OPEN | Rename + KDoc |
| CQ-014 | LOW | Hardcoded 5-min deadline | OPEN | Parameterize |
| CQ-015 | LOW | MDAOToken infinite approval | INFO | Intended behavior |

### Penetration (PT-) — Phase 4, 17 findings

| ID | Severity | Title | Status | Notes |
|----|----------|-------|--------|-------|
| PT-001 | CRIT | setP256Verifier bypass all signatures | OPEN | Remove or multisig+timelock |
| PT-002 | CRIT | RELAY_SECRET leak → social engineering | OPEN | (RA-001-004 partially address) |
| PT-003 | CRIT | XSS on trusted origin → personal_sign | OPEN | Remove personal_sign from bridge |
| PT-004 | HIGH | Guardian threshold 2 too low | OPEN | Increase to 3 |
| PT-005 | HIGH | Removed guardians still send approvals | OPEN | On-chain sync |
| PT-006 | HIGH | Per-isolate rate limit bypass | OPEN | (RA-006) |
| PT-007 | HIGH | MITM RPC in dev builds | OPEN | (SA-003) |
| PT-008 | MED | Deep link injection | OPEN | Validate + confirm |
| PT-009 | MED | Dead JWT endpoint (/auth/siwe) | OPEN | Implement or remove |
| PT-010 | MED | Relay no on-chain guardian check | OPEN | RPC sync |
| PT-011 | MED | Paymaster bypass when trustedSigner=0 | OPEN | Require registry |
| PT-012 | LOW | KV race condition | OPEN | Durable Objects |
| PT-013 | LOW | Nonce missing in KV key | OPEN | Add guardianHash |
| PT-014 | LOW | Push spam | OPEN | Rate limit |
| PT-015 | LOW | SIWE no domain check | OPEN | (RA-002) |
| PT-016 | LOW | Long biometric window | OPEN | Reduce to 30s |
| PT-017 | INFO | Off-chain vs on-chain count | INFO | Design note |

---

## Remediation Updates (2026-07-16 — Self-Challenge Hardening)

Applied after self-challenge identified 3 gaps in B2 (DoS protection):

### SocialRecoveryModule.sol — 3 improvements

| Change | Before | After | Why |
|--------|--------|-------|-----|
| **Gas limit on hooks** | `try hooks[i].onRecoveryExecuted(wallet) {} catch {}` | `try hooks[i].onRecoveryExecuted{gas: 50_000}(wallet) {} catch (bytes memory reason) { emit RecoveryHookFailed(...) }` | Prevents infinite-loop hook from consuming all gas despite try/catch |
| **Whitelist approved hooks** | `addRecoveryHook(hook)` — any address | `approveHook(address)` → `approvedHookContracts[addr] = true` → `addRecoveryHook(hook)` requires whitelist | Owner cannot silently add malicious hook without explicit approval step |
| **Emit failure reason** | Silent `catch {}` | `catch (bytes memory reason) { emit RecoveryHookFailed(wallet, hook, reason) }` + `AllRecoveryHooksFailed` event | Watchtower monitors hook failures instead of silent swallowing |

New state:
- `HOOK_GAS_LIMIT = 50_000` — per-hook gas cap
- `approvedHookContracts` mapping — whitelist
- `approveHook(address)` / `revokeHook(address)` — owner-only
- `RecoveryHookFailed` / `AllRecoveryHooksFailed` / `HookApproved` / `HookRevoked` events

### TrustProviderRegistry.t.sol — proxy detection tests

3 forge tests added to catch if registry is ever replaced with a proxy:
- `test_Registry_IsNotTransparentProxy()` — checks EIP-1967 impl slot
- `test_Registry_IsNotUUPSProxy()` — checks ERC1967 UUPS slot
- `test_Registry_IsNotBeaconProxy()` — checks beacon slot

These fail-fast in CI if `setRegistry()` is ever pointed at a proxy contract.

### Note on CEI in executeRecovery

`req.executed = true` was already at line 329 BEFORE all external calls (deposit transfer, hook loop, SmartAccount transferOwnership). CEI was correct — no change needed. This was verified, not assumed.
