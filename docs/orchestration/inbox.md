# Security Review — Consolidated Index (6-Direction Audit)

## Source
- Coordinator: opencode (6 parallel researchers + manual verification)
- Date: 2026-06-25
- Scope: Full project (backend, relay, app, contracts, infra)

---

## 1. AUTHENTICATION & AUTHORIZATION

### Findings

| ID | Location | What | Risk | Status |
|----|----------|------|------|--------|
| A-01 | backend/Application.kt:126-136 | /sign API key auth — constant-time via MessageDigest.isEqual | INFO | ✅ Secure |
| A-02 | backend/Application.kt:179-185 | /metrics metricsToken auth — constant-time | INFO | ✅ Secure |
| A-03 | relay/src/auth.ts:2-7 | HMAC constant-time compare | INFO | ✅ Secure |
| A-04 | backend/Application.kt:140-144 | /sign IP rate limiting | INFO | ✅ Works |
| A-05 | backend/Application.kt:148-151 | /sign sender rate limiting | INFO | ✅ Works |
| A-06 | backend/NicknameService.kt:89-188 | /nickname/register signature verification | INFO | ✅ EIP-191 + s-malleability check |
| A-07 | Multiple endpoints | No rate limiting on /auth/*, /nickname/*, /onramp/*, /swap/* | LOW | Design choice — public read endpoints |
| A-08 | relay/src/index.ts | Guardian endpoints — HMAC auth only, no RBAC | LOW | Design choice — signature = authorization |
| A-09 | app/ WebView | injectEthereumProvider — user-initiated | INFO | ✅ User controls WebView |

### Assessment
Auth architecture is **by design** for a crypto wallet backend:
- `/sign` — API key + rate limiting (optional API_KEY)
- `/health` — public (monitoring)
- `/nickname/*` — public read, signed write
- `/auth/*` — self-contained JWT auth
- `/onramp/*`, `/swap/*` — optional API_KEY
- relay — HMAC signature auth per request
- No RBAC needed — signature proves ownership

---

## 2. CONFIGURATION & SECRETS

### Findings

| ID | Location | What | Risk | Status |
|----|----------|------|------|--------|
| S-01 | backend/.env* | Private key placeholders (`0x...`) | INFO | Gitignored, never committed ✅ |
| S-02 | backend/AppConfig.kt:43-64 | All secrets from env vars | INFO | ✅ No hardcoded secrets |
| S-03 | scripts/scan-secrets.sh | Secret scanning in pre-commit + CI | INFO | ✅ Active |
| S-04 | .gitignore:17-20 | .env exclusion | INFO | ✅ Configured |
| S-05 | relay/src/__tests__/auth.test.ts:4 | Test secret `'test-secret-key-for-testing'` | INFO | Test-only ✅ |
| S-06 | ponytail/.env.example:2 | `ANTHROPIC_API_KEY=sk-ant-...` | INFO | Placeholder ✅ |
| S-07 | docker-compose.yml:7 | `env_file: .env` | INFO | ✅ Uses env file |
| S-08 | backend/AuthService.kt:135-145 | PBKDF2 600k, constant-time compare | INFO | ✅ Secure |

### Assessment
**0 real secret findings.** All .env files are placeholders, gitignored, never committed. AppConfig reads all secrets from environment variables. scan-secrets.sh is active.

---

## 3. CHAOS / RESILIENCE

### Findings

| ID | Location | What | Risk | Mitigation |
|----|----------|------|------|------------|
| C-01 | RedisClient.kt:14-23 | Redis = single point of failure | MEDIUM | Rate limiting, replay cache, nickname cache all fail |
| C-02 | Application.kt:54-88 | No graceful shutdown hook | MEDIUM | Watchtower scope not cancelled, Redis not closed |
| C-03 | NicknameService.kt:35-36 | ConcurrentHashMap unbounded growth | LOW | No eviction, but bounded by DB |
| C-04 | WatchtowerService.kt:41-42 | CoroutineScope not cancelled on error | LOW | SupervisorJob handles child failures |
| C-05 | PriceOracle.kt | CircuitBreaker only for PriceOracle | LOW | PaymasterService, SwapService have no breaker |
| C-06 | RpcProviderManager.kt | Failover with cooldown | INFO | ✅ 30s cooldown per provider |
| C-07 | PaymasterService.kt | RPC failure → exception | INFO | ✅ Proper error propagation |

### Assessment
**C-01 and C-02 are the real risks.** Redis failure breaks rate limiting (but /sign still works — just without rate limiting). No graceful shutdown means Watchtower may miss events during deployment.

---

## 4. FORMAL VERIFICATION (CONTRACTS)

### Findings

| ID | Location | What | Risk | Status |
|----|----------|------|------|--------|
| F-01 | contracts/test/invariant/MDAOPaymaster.invariant.sol | 3 invariants (maxTokenAmountLimit, maxGasPrice, minimumDeadlineBuffer) | INFO | ✅ Exists |
| F-02 | contracts/test/fuzz/MDAOPaymaster.fuzz.sol | 3 fuzz tests (computeAmountToCharge, quoteDeadline, maxTokenAmount) | INFO | ✅ Exists |
| F-03 | No invariant tests for SocialRecoveryModule | Missing invariants | MEDIUM | Recovery threshold, share distribution |
| F-04 | No invariant tests for MDAOToken | Missing invariants | MEDIUM | totalSupply <= MAX_SUPPLY |
| F-05 | No invariant tests for InsuranceFund, DeadManSwitch, RefundVault | Missing | LOW | Simpler contracts |
| F-06 | Slither in CI | Limited detector set | INFO | ✅ Active |
| F-07 | No Certora/Halmos/KEVM | No formal verification tools | LOW | Optional for MVP |

### Assessment
MDAOPaymaster has basic invariant and fuzz coverage. SocialRecoveryModule and MDAOToken lack invariants. Formal verification tools (Certora, Halmos) are not used — acceptable for MVP but should be added before mainnet.

---

## 5. PERFORMANCE & LOAD

### Findings

| ID | Location | What | Risk | Status |
|----|----------|------|------|--------|
| P-01 | backend/load-tests/sign.js | k6 load test — max 50 VUs | INFO | ✅ Exists |
| P-02 | backend/load-tests/nickname.js | k6 load test — nickname | INFO | ✅ Exists |
| P-03 | Database.kt:14 | HikariCP pool size = 10 | LOW | May be insufficient at 1000 RPS |
| P-04 | PriceOracle.kt | No timeout on HTTP clients | LOW | Slow source blocks all |
| P-05 | NicknameService.kt:35-36 | ConcurrentHashMap no eviction | LOW | Memory growth under load |
| P-06 | No RPC batching | Each RPC call = separate HTTP | LOW | Increases latency under load |
| P-07 | Metrics.kt | Metrics endpoint exists | INFO | ✅ Prometheus format |

### Assessment
k6 tests exist but max at 50 VUs. No 1000-concurrent test. HikariCP pool of 10 may bottleneck. No RPC batching means more HTTP connections under load.

---

## 6. UX / ACCESSIBILITY

### Findings

| ID | Location | What | Risk | Status |
|----|----------|------|------|--------|
| U-01 | MainScreen.kt:248 | ProductCard Icon `contentDescription = null` | MEDIUM | Screen reader can't identify |
| U-02 | MDAOButton.kt:66-70 | Sm/Md buttons < 48dp (38dp, 44dp) | MEDIUM | Touch target too small |
| U-03 | MDAOTopBar.kt:43 | Back button 42dp | LOW | Below 48dp minimum |
| U-04 | ErrorStatesScreen.kt:96 | ErrorIcon no contentDescription | MEDIUM | Screen reader can't identify |
| U-05 | ReceiveScreen.kt:138 | QR code no contentDescription | MEDIUM | Screen reader can't read |
| U-06 | Multiple screens | No offline state indicator | LOW | User gets no feedback |
| U-07 | Multiple screens | Hardcoded font sizes | LOW | No dynamic type support |
| U-08 | HomeScreen.kt:235,252 | Icon tint onSurfaceVariant | LOW | May have contrast issues |

### Assessment
Real accessibility gaps in touch targets (38dp < 48dp minimum) and contentDescription. Offline behavior missing. Font sizes hardcoded.

---

## Summary

| Category | CRITICAL | HIGH | MEDIUM | LOW | INFO |
|----------|----------|------|--------|-----|------|
| Auth | 0 | 0 | 0 | 2 | 7 |
| Secrets | 0 | 0 | 0 | 0 | 8 |
| Resilience | 0 | 0 | 2 | 3 | 2 |
| Contracts | 0 | 0 | 2 | 1 | 3 |
| Performance | 0 | 0 | 0 | 4 | 3 |
| UX/A11y | 0 | 0 | 4 | 4 | 0 |
| **Total** | **0** | **0** | **8** | **14** | **23** |

## Action Items (Priority Order)

### P0 — Fix Before Mainnet
1. **C-01**: Redis SPOF — add Redis Cluster or failopen mode
2. **C-02**: Add graceful shutdown hook (Runtime.addShutdownHook)
3. **F-03**: Add SocialRecoveryModule invariant tests
4. **F-04**: Add MDAOToken invariant tests (totalSupply <= MAX_SUPPLY)

### P1 — Fix Soon
5. **U-02**: Fix MDAOButton touch targets to 48dp minimum
6. **U-01**: Add contentDescription to ProductCard Icon
7. **U-04**: Add contentDescription to ErrorIcon
8. **U-05**: Add contentDescription to QR code
9. **P-03**: Increase HikariCP pool size to 25
10. **P-04**: Add timeout to PriceOracle HTTP clients

### P2 — Track
11. **C-03**: Add ConcurrentHashMap eviction or use Caffeine cache
12. **P-06**: Implement RPC batching for PaymasterService
13. **P-01**: Add 1000-VU k6 test scenario
14. **U-06**: Add offline state indicator
15. **U-07**: Use Material typography for font scaling
16. **F-07**: Evaluate Certora/Halmos for formal verification

## Suspected Files
- backend/src/main/kotlin/com/mdaopay/paymaster/RedisClient.kt
- backend/src/main/kotlin/com/mdaopay/paymaster/Application.kt
- backend/src/main/kotlin/com/mdaopay/paymaster/Database.kt
- backend/src/main/kotlin/com/mdaopay/paymaster/PriceOracle.kt
- backend/src/main/kotlin/com/mdaopay/paymaster/NicknameService.kt
- contracts/test/invariant/MDAOPaymaster.invariant.sol
- contracts/test/fuzz/MDAOPaymaster.fuzz.sol
- app/src/main/java/com/mdaopay/app/core/ui/components/MDAOButton.kt
- app/src/main/java/com/mdaopay/app/feature/main/presentation/MainScreen.kt
- app/src/main/java/com/mdaopay/app/feature/states/presentation/ErrorStatesScreen.kt
