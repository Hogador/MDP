# Coverage Analysis Report

**Date:** 2026-06-30
**Scope:** contracts, backend, app, relay — source vs test files
**Reference:** TDD/test-scenarios.md (457 scenarios + 24 smoke), security/TEST-COVERAGE-MAP.md

---

## 1. Coverage % per Module

| Module | Source Files | Test Files | Coverage (by file) | Critical Untested Files |
|--------|-------------|-----------|-------------------|----------------------|
| **contracts/** | 12 src | 17 (13 unit + 1 invariant + 1 integration + 1 smoke + 1 deploy helper) | 100% | 0 — all src files have test files |
| **backend/** | 21 src | 15 test files | 67% (14/21) | 7 untested src files |
| **app/** | ~100+ src | 18 test (14 unit + 4 androidTest) | ~18% | ~82 src files untested |
| **relay/** | 4 src | 3 test files | 75% (3/4) | 1 untested (storage.ts — indirect only) |

### Contracts — Coverage Quality Assessment

All 12 `.sol` files have corresponding test files. However:

| Criteria | Status |
|----------|--------|
| Invariant tests | 1 test in `PaymasterInvariant.t.sol` — only checks `trustedSigner != address(0)` |
| Fuzz tests | **0 found** — no fuzz tests anywhere |
| Boundary value tests | Present in MDAOToken, SocialRecoveryModule |
| Negative path tests | Present in most modules |
| Formal verification | **0** — not implemented |

### Backend — Coverage Detail

| Source File | Lines | Has Test? | Test Quality |
|-------------|-------|-----------|-------------|
| PaymasterService.kt | ~120 | ✅ PaymasterServiceTest.kt | 4 tests, basic happy path |
| AuthService.kt | 180 | ✅ AuthServiceTest.kt | 5 tests, **only password validation** — no login/refresh/token validation tests |
| AuthRepository.kt | 126 | ❌ **No test** | — |
| SwapService.kt | 179 | ❌ **No test** | CRITICAL money path |
| SwapRoutes.kt | ~80 | ✅ SwapRoutesAuthTest.kt | 4 tests, auth only |
| OnrampRoutes.kt | ~70 | ✅ OnrampRoutesAuthTest.kt | 3 tests, auth only |
| FiatOnrampService.kt | ~60 | ✅ FiatOnrampServiceTest.kt | 2 tests, API key leak only |
| WatchtowerService.kt | ~80 | ✅ WatchtowerServiceTest.kt | 1 assertion — checks CoroutineScope field exists only |
| PriceOracle.kt | ~90 | ✅ PriceOracleTest.kt | 3 tests |
| NicknameService.kt | ~120 | ✅ NicknameServiceTest.kt | 4 tests, basic |
| NicknamePolicy.kt | ~60 | ✅ NicknamePolicyTest.kt | 18 tests, thorough |
| NicknameRepository.kt | 103 | ❌ **No test** | — |
| OnChainRegistryClient.kt | ~100 | ✅ OnChainRegistryClientTest.kt | 8 tests, thorough |
| PaymasterUtil.kt | ~80 | ✅ PaymasterUtilTest.kt | 7 tests, basic |
| RpcProviderManager.kt | 85 | ❌ **No test** | CRITICAL: RPC failover |
| RedisClient.kt | ~100 | ✅ (indirect via RedisChaosTest, RedisClientFallbackTest) | 8 total, thorough |
| Metrics.kt | 190 | ❌ **No test** | — |
| AppConfig.kt | 143 | ❌ **No test** | Medium: config validation |
| Database.kt | 37 | ❌ **No test** | Low |
| LogSanitizer.kt | ~40 | ✅ LogSanitizerTest.kt | 2 tests |
| SimpleJsonLayout.kt | ~30 | ❌ **No test** | Low (logging) |

### App — Coverage Detail

| Package | Source Files | Test Files | Coverage |
|---------|-------------|-----------|----------|
| `core/security/` | 11 | 7 tests | 64% — PasskeyManager, ShamirSecretSharing, DeviceIntegrityManager, BiometricManager, GF256, RecoveryShareManager ✅ |
| | | | **Untested:** SocialAuthManager, AppLockManager, KeystoreCrypto, CborDecoder, BiometricAvailability |
| `core/blockchain/` | 13 | 4 tests | 31% — NetworkConfig, PaymasterClient, RpcProviderManager, EthereumProviderInjector ✅ |
| | | | **Untested:** **SendRepository, NicknameResolver, TxErrorMapper, BlockchainRepository, EthereumClient, EtherscanRepository, UserOperation, RecoveryUserOpBuilder, BundlerClient, WalletManager, WalletConnectManager** |
| `core/guardian/` | 5 | 2 tests | 40% — GuardianUserOpBuilder, RelayClient ✅ |
| | | | **Untested:** GuardianManager, GuardianContracts, GuardianStorage |
| `core/datastore/` | 7 | **0 tests** | **0%** — TxQueue, UserPreferences, TxQueueDao, QueuedTransaction, AppDatabase, ContactsStore, TransactionHistory |
| `core/notification/` | 5 | **0 tests** | **0%** |
| `core/network/` | 2 | **0 tests** | **0%** — OfflineSyncWorker, ConnectivityMonitor |
| `core/ui/components/` | 30+ | 1 test | **~3%** — only MDAOWebViewTest |
| `domain/usecase/` | 1 | 1 test | 100% — GaslessTransactionOrchestrator ✅ |
| `di/` | 2 | 1 test | 50% — NetworkModule ✅, DatabaseModule untested |
| Screens/ViewModels | ~15+ | **0 tests** | **0%** |
| Navigation | 1 | **0 tests** | **0%** |
| **Total** | **~100+** | **18** | **~18%** |

### Relay — Coverage Detail

| Source File | Lines | Has Test? | Quality |
|-------------|-------|-----------|---------|
| auth.ts | ~80 | ✅ auth.test.ts | 4 tests, covers auth logic |
| fcm.ts | ~60 | ✅ fcm.test.ts | 3 tests, covers FCM send |
| index.ts | ~200 | ✅ invite.test.ts (indirect) | 15 tests via route testing |
| storage.ts | ~100 | ❌ **No direct test** | Tested only indirectly via invite.test.ts |

---

## 2. Top 10 Critical Untested Functions

Ranked by risk (auth, recovery, payments, RPC = CRITICAL):

| # | Function | File | Module | Risk | Why |
|---|----------|------|--------|------|-----|
| **1** | `SwapService.executeSwap()` | `backend/.../SwapService.kt:93` | backend | **CRITICAL** | Signs & sends real swap txs. No test at all. |
| **2** | `SwapService.getQuote()` | `backend/.../SwapService.kt:68` | backend | **CRITICAL** | On-chain quote via PancakeSwap. Money path. |
| **3** | `RpcProviderManager.getBestProvider()` | `backend/.../RpcProviderManager.kt:29` | backend | **CRITICAL** | RPC failover logic. All RPC calls depend on this. |
| **4** | `RpcProviderManager.refreshHealth()` | `backend/.../RpcProviderManager.kt:57` | backend | **CRITICAL** | Health check — if broken, all RPC appears down. |
| **5** | `AuthService.login()` | `backend/.../AuthService.kt:57` | backend | **CRITICAL** | Login flow not tested. Password comparison, token issuance. |
| **6** | `AuthService.refresh()` | `backend/.../AuthService.kt:71` | backend | **CRITICAL** | Token refresh flow not tested. |
| **7** | `AuthService.validateAccessToken()` | `backend/.../AuthService.kt:88` | backend | **CRITICAL** | Token validation not tested. |
| **8** | `AuthRepository.create()` / `findByEmail()` | `backend/.../AuthRepository.kt:33,46` | backend | **CRITICAL** | DB layer for auth — no tests at all. |
| **9** | `GuardianManager` / `GuardianContracts` (app) | `app/.../core/guardian/` | app | **CRITICAL** | Core recovery flow — no tests. |
| **10** | `BlockchainRepository` / `EthereumClient` / `BundlerClient` (app) | `app/.../core/blockchain/` | app | **CRITICAL** | Core blockchain operations — no tests. |

### Honorable Mentions (Medium-High Risk)

| Function | File | Why |
|----------|------|-----|
| `WatchtowerService.monitorRecovery()` | `backend/.../WatchtowerService.kt` | Test has only 1 assertion on CoroutineScope field |
| `AppConfig.fromEnv()` | `backend/.../AppConfig.kt:43` | Startup config validation — no test |
| `Metrics.prometheusText()` | `backend/.../Metrics.kt:75` | 190-line file, 0 tests |
| `NicknameRepository.findByNickname()` | `backend/.../NicknameRepository.kt:12` | DB layer, no tests |
| `KeystoreCrypto` (app) | `app/.../core/security/` | Key management, no tests |
| `SocialAuthManager` (app) | `app/.../core/security/` | Social auth, no tests |
| `AppLockManager` (app) | `app/.../core/security/` | App lock, no tests |
| `SendRepository` (app) | `app/.../core/blockchain/` | Send operations, no tests |
| `WalletManager` (app) | `app/.../core/blockchain/` | Wallet ops, no tests |
| `GuardianStorage` (app) | `app/.../core/guardian/` | Recovery storage, no tests |

---

## 3. Mapping to test-scenarios.md

### Automation Breakdown (126 scenario families)

```
Auto: Contract  → 32  (25%)  ← Foundry tests
Auto: Full     → 40  (32%)  ← JUnit / integration
Auto: Smoke    →  7   (6%)  ← CI smoke tests
Manual         → 47  (37%)  ← Human-only
```

### Gaps: "Auto" scenarios with NO automated test

| Scenario Family | Automation Marked As | Actual Test Coverage | Gap |
|----------------|---------------------|---------------------|-----|
| **PAY-GAS** (gas estimation) | Auto: Full | Partial — PaymasterServiceTest has gas estimation | Partial |
| **PAY-FAIL** (failure modes) | Auto: Full | Partial — some tests in PaymasterServiceTest | Partial |
| **REC-OPERATOR** (recovery ops) | Auto: Full | WatchtowerServiceTest has 1 assertion only | **MAJOR** |
| **SEC-AUTH** (auth flows) | Auto: Full | AuthServiceTest only tests password format (5/20+ methods) | **MAJOR** |
| **SEC-CRYPTO** (crypto validation) | Auto: Contract | Some contract tests | Partial |
| **SEC-DEPS** (dependency check) | Auto: Full | None | **MISSING** |
| **SEC-SECRETS** (secret scan) | Auto: Full | None | **MISSING** |
| **SEC-CONFIG** (config validation) | Auto: Full | None | **MISSING** |
| **SWAP** (swap operations) | Auto: Full | SwapRoutesAuthTest covers auth only — NOT swap logic | **MAJOR** |
| **ONRAMP** (onramp operations) | Auto: Full | OnrampRoutesAuthTest covers auth only — NOT onramp logic | **MAJOR** |
| **APP-RECOVERY** (app recovery flow) | Auto: Full | None in app | **MISSING** |
| **APP-BLOCKCHAIN** (app blockchain) | Auto: Full | Minimal — RpcProviderManagerTest, EthereumProviderInjectorTest only | **MAJOR** |
| **APP-OFFLINE** (offline mode) | Auto: Full | None | **MISSING** |
| **RELAY-INVITE** (invite flow) | Auto: Full | invite.test.ts covers main flow | OK |
| **RELAY-FCM** (FCM) | Auto: Full | fcm.test.ts covers send | OK |
| **PROD-LOAD** (load testing) | Auto: Full (k6) | None found | **MISSING** |
| **PROD-GOVERNANCE** (governance) | Manual | N/A | N/A (manual) |
| **CHAOS** (chaos testing) | Auto: Full | RedisChaosTest exists — Redis failure only | Partial |

### TEST-COVERAGE-MAP.md Cross-Reference

From `security/TEST-COVERAGE-MAP.md`:

| Missing Family | Status in MAP | Actual Status |
|---------------|--------------|---------------|
| SEC-SECRETS | ❌ Add | ✅ Agreed — no secret scanning tests |
| SEC-DEPS | ❌ Add | ✅ Agreed — no dependency check tests |
| SEC-AUTH | ❌ Add | ✅ Agreed — AuthServiceTest only covers password format |
| SEC-CONFIG | ❌ Add | ✅ Agreed — no config validation tests |
| SEC-CRYPTO | Partial | ✅ Matches — partial contract tests |
| FE-MOBILE | Partial | ✅ Matches — app coverage ~18% |
| FE-DEVICE | Partial | ✅ Matches — DeviceIntegrityManagerTest exists |
| PAY-GAS | Partial | ✅ Matches — partial |
| PROD-GOVERNANCE | Partial | N/A — manual scenarios |
| SEC-WEB | Partial | 🔴 Relay webhook tests exist but incomplete |
| Formal verification | ❌ Add | ✅ Agreed — not started |

---

## 4. Proposed Test Names (Convention: testSECXX_Description)

Top priority tests to write, per module:

### Backend (Priority: CRITICAL)

```
testSEC01_swapQuoteReturnsQuote           — SwapService.getQuote()
testSEC02_swapQuoteFailsOnRpcError         — SwapService.getQuote() — RPC failure
testSEC03_swapExecuteSubmitsTx             — SwapService.executeSwap()
testSEC04_swapExecuteWaitsForReceipt       — SwapService.waitForReceipt()
testSEC05_rpcProviderFailoverOnError       — RpcProviderManager.getBestProvider()
testSEC06_rpcProviderCooldownAfterErrors   — RpcProviderManager error tracking
testSEC07_rpcProviderRefreshHealth          — RpcProviderManager.refreshHealth()
testSEC08_authServiceLoginSuccess           — AuthService.login()
testSEC09_authServiceLoginWrongPassword     — AuthService.login() — wrong password
testSEC10_authServiceRefreshToken           — AuthService.refresh()
testSEC11_authServiceValidateAccessToken    — AuthService.validateAccessToken()
testSEC12_authRepoCreateAndFind             — AuthRepository.create() / findByEmail()
testSEC13_authRepoRefreshTokenCrud          — AuthRepository token lifecycle
testSEC14_watchtowerMonitorsRecovery        — WatchtowerService.monitorRecovery()
testSEC15_appConfigFromEnvValidation         — AppConfig.fromEnv() — valid/invalid env
testSEC16_metricsPrometheusOutput            — Metrics.prometheusText() — format
testSEC17_nicknameRepositoryCrud             — NicknameRepository CRUD
```

### Contracts (Priority: HIGH)

```
testSEC18_paymasterInvariantUnderrunProtection  — PaymasterInvariant — more invariants
testSEC19_paymasterFuzzTrustedSigner             — Fuzz: trustedSigner variations
testSEC20_socialRecoveryFuzzGuardianCombinations  — Fuzz: guardian threshold combos
```

### App (Priority: CRITICAL)

```
testSEC21_socialAuthManagerLogin              — SocialAuthManager social login
testSEC22_appLockManagerLockUnlock             — AppLockManager
testSEC23_keystoreCryptoEncryptDecrypt          — KeystoreCrypto
testSEC24_blockchainRepositoryFetchBalance      — BlockchainRepository
testSEC25_ethereumClientCall                    — EthereumClient RPC call
testSEC26_bundlerClientSendUserOp               — BundlerClient
testSEC27_walletManagerCreateWallet             — WalletManager
testSEC28_guardianManagerAddGuardian            — GuardianManager
testSEC29_guardianManagerExecuteRecovery        — GuardianManager recovery flow
testSEC30_txQueueEnqueueAndProcess              — TxQueue
```

### Relay (Priority: MEDIUM)

```
testSEC31_storageCacheHitAndMiss                — storage.ts direct tests
testSEC32_indexRouteValidation                   — index.ts route handler unit tests
```

### Deploy (Priority: MEDIUM)

```
testSEC33_deployScriptValidatesEnv               — deploy-backend.sh validation
```

---

## 5. Risk-Ranked Priority Summary

| Priority | Count | Action |
|----------|-------|--------|
| **P0-EXISTENTIAL** | 0 | — |
| **P0 (pre-launch)** | 6 | `testSEC01`-`testSEC11` — SwapService, RpcProviderManager, AuthService/AuthRepository |
| **P1 (first quarter)** | 10 | `testSEC12`-`testSEC17`, `testSEC21`-`testSEC24` — Watchtower, AppConfig, Metrics, NicknameRepo, app security |
| **P2 (backlog)** | 10 | `testSEC25`-`testSEC32` — app blockchain, guardian, relay, deploy |
| **P3 (accept risk)** | 5 | UI components (cosmetic), SimpleJsonLayout |

### Immediate actions (this sprint):

1. **SwapService.kt** — write `testSEC01`-`testSEC04` (CRITICAL — money path, zero tests)
2. **RpcProviderManager.kt** — write `testSEC05`-`testSEC07` (CRITICAL — all RPC depends on this)
3. **AuthService.kt** — write `testSEC08`-`testSEC11` (CRITICAL — login/refresh/validate untested)
4. **AuthRepository.kt** — write `testSEC12`-`testSEC13` (CRITICAL — auth DB layer)
5. **WatchtowerServiceTest.kt** — expand from 1 assertion to meaningful recovery checks

---

## 6. Test Quality Issues Found

| Issue | Location | Detail |
|-------|----------|--------|
| **1 assertion test** | WatchtowerServiceTest.kt | Only checks `CoroutineScope` field exists — not actual recovery monitoring |
| **Auth-only tests** | SwapRoutesAuthTest, OnrampRoutesAuthTest | Test auth only, never test actual swap/onramp execution logic |
| **Thin invariant** | PaymasterInvariant.t.sol | Only 1 invariant test (`trustedSigner != 0`) — no underrun, no price, no balance invariants |
| **Zero fuzz tests** | All contracts | No fuzz tests found in any contract test file |
| **Circular mock** | AuthServiceTest | Uses `mockkStatic`/`mockk` but AuthService calls `AuthRepository` — tests only password validation |
| **Indirect coverage only** | relay/src/storage.ts | No dedicated test file — tested only via invite.test.ts |
| **Missing negative tests** | PaymasterServiceTest | Only 1 failure case tested (RPC error) — no test for invalid signature, expired, replayed |
| **Deploy untested** | infra/gcloud/deploy-backend.sh | No tests for deploy script — env validation, error handling |
| **Contract deploy missing** | — | No Foundry deploy script within project — only OZ helper exists |
| **App ~82% uncovered** | app/src/ | Only 18 test files for ~100+ source files |

---

## 7. Recommendations

1. **Write SwapService tests immediately** (P0) — this handles real money
2. **Write RpcProviderManager tests immediately** (P0) — RPC failover is critical for liveness
3. **Write AuthService full tests** (P0) — login/refresh/token validation untested
4. **Expand WatchtowerServiceTest** (P1) — recovery monitoring must be tested
5. **Add contract fuzz tests** (P1) — minimum 2-3 fuzz tests per critical contract
6. **Add PaymasterInvariant tests** (P1) — at minimum: underrun protection, balance consistency
7. **Write app GuardianManager tests** (P1) — core recovery flow
8. **Automate Manual scenarios** (P2) — 47 manual scenarios → prioritize SEC families
9. **Add deploy tests** (P2) — at minimum validate env vars and error handling
10. **Set coverage gate** in CI: contracts ≥90%, backend ≥70%, relay ≥70%, app ≥40%

---

*Report generated by researcher-coverage agent.*
*References: CODE-INDEX.md, TEST-COVERAGE-MAP.md, TDD/test-scenarios.md*
