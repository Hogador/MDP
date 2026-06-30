# MDAOPay Test Coverage Map

> Маппинг audit findings → test-scenarios.md scenarios.
> Цель: каждый finding должен быть связан с существующим или новым тестом.

## Mapping

| Audit Type | test-scenarios.md Family | Coverage Status |
|------------|--------------------------|-----------------|
| Secrets | (none — manual) | ❌ Add SEC-SECRETS family |
| Dependencies | (none — manual) | ❌ Add SEC-DEPS family |
| Configuration | (none — manual) | ❌ Add SEC-CONFIG family |
| Cryptography | SEC-CRYPTO (4 variants) | ⚠️ Partial |
| Blockchain logic | SEC-REPLAY (6), SEC-KEYS (5) | ✅ Good |
| Smart contracts | SEC-CONTRACT (8) | ✅ Good |
| Input validation | ILL-INPUT (6) | ✅ Good |
| Auth & authorization | SEC-AUTH (new) | ❌ Add |
| Mobile-specific | FE-MOBILE (7), FE-DEVICE (6) | ⚠️ Partial |
| Network/TLS | SEC-NETWORK (8) | ✅ Good |
| Rate limiting & DoS | SEC-DOS (5) | ✅ Good |
| Gas/MEV | PAY-GAS (5), SEC-REPLAY-D | ⚠️ Partial |
| Upgradeability | PROD-GOVERNANCE (5) | ⚠️ Partial |
| Logging/PII | SEC-WEB (5), SEC-CRYPTO | ⚠️ Partial |
| Chaos engineering | EDGE-INFRA (7), §6 Chaos | ✅ Good |
| Performance & load | SCALE-TRAFFIC (5) | ✅ Good |
| Formal verification | SEC-CONTRACT (Foundry fuzz) | ⚠️ Needs invariant |
| Compliance | LEG-COMPLIANCE (5) | ✅ Good |
| Test coverage gaps | (this file) | 🔄 Self-referential |

## Missing test families (PROPOSED)

### SEC-SECRETS (new, 5 variants)
- A: .env committed to git
- B: Hardcoded API key in source
- C: Secret in CI log
- D: Secret in error message
- E: Secret in stack trace

### SEC-DEPS (new, 5 variants)
- A: Known CVE in dependency
- B: Outdated package
- C: Typosquatting detected
- D: Unpinned version
- E: Unnecessary dependency

### SEC-AUTH (new, 5 variants)
- A: /sign endpoint accessible without auth
- B: /recovery/approve accepts fake guardian
- C: WebView bridge exploited
- D: Relay endpoint without HMAC
- E: JWT token replay

### SEC-CONFIG (new, 5 variants)
- A: Hardcoded password in docker-compose
- B: Missing env var validation
- C: Default fallback for secret
- D: Exposed port in production
- E: No certificate pinning

## Findings → Test mapping

| Finding ID | Test scenario | Test file | Status |
|------------|---------------|-----------|--------|
| F-001 | SEC-REPLAY-C | contracts/test/MDAOPaymaster.t.sol | ❌ Missing |
| F-002 | REC-FLOW-A | contracts/test/SocialRecoveryModule.t.sol | ❌ Missing |
| F-003 | REC-FLOW-B | contracts/test/SocialRecoveryModule.t.sol | ❌ Missing |
| F-004 | PAY-ECONOMICS-D | contracts/test/MDAOPaymaster.t.sol | ❌ Missing |
| F-005 | PAY-ORACLE-A | contracts/test/MDAOPaymaster.t.sol | ⚠️ Partial |
| F-006 | SEC-REPLAY-D | contracts/test/MDAOPaymaster.t.sol | ❌ Missing (fuzz) |
| F-008 | SEC-CONTRACT-C | contracts/test/RefundVault.t.sol | ❌ Missing |
| F-013 | SEC-WEB-A | backend/src/test/.../LogSanitizerTest.kt | ❌ Missing |
| F-015 | SEC-AUTH-B (new) | relay/src/__tests__/recovery.test.ts | ❌ Missing |
| F-019 | ASYNC-RACE-A | backend/src/test/.../NicknameServiceTest.kt | ❌ Missing |
| F-020 | SEC-KEYS-A | contracts/test/SocialRecoveryModule.t.sol | ❌ Missing |
| F-023 | SEC-NETWORK-E | app/src/test/.../RpcProviderManagerTest.kt | ❌ Missing |
| F-024 | SEC-NETWORK-C | app/src/test/.../NetworkSecurityTest.kt | ❌ Missing |
| F-025 | SEC-NETWORK-A | backend/src/test/.../RpcProviderManagerTest.kt | ❌ Missing |

## Smoke tests (20 from test-scenarios.md §7)

| # | ID | Status |
|---|-----|--------|
| 1 | EX-03 Paymaster economic death | ✅ Foundry |
| 2 | EX-09 Regulatory kill | ❌ Missing (Maestro) |
| 3 | SEC-REPLAY-A UserOp replay | ✅ Foundry |
| 4 | SEC-PHISHING-A Recipient validation | ❌ Missing (Maestro) |
| 5 | SEC-DOS-A Paymaster /sign rate limit | ❌ Missing (k6) |
| 6 | SEC-RECOVERY-A Guardian collusion | ✅ Foundry |
| 7 | SEC-CONTRACT-F Reentrancy | ✅ Foundry |
| 8 | EDGE-INFRA-A Redis down | ❌ Missing (Chaos Mesh) |
| 9 | EDGE-CHAIN-D Token decimals | ✅ Foundry |
| 10 | AA-INIT-C Stuck account | ✅ Foundry |
| 11 | PAY-ECONOMICS-A Paymaster balance | ✅ Foundry |
| 12 | PAY-ECONOMICS-B Price TOCTOU | ✅ Foundry |
| 13 | ILL-INPUT-A Send 0 USDT | ❌ Missing (Maestro) |
| 14 | ILL-INPUT-D Address without 0x | ❌ Missing (Postman) |
| 15 | UX-ERROR-B Double-tap idempotency | ❌ Missing (k6) |
| 16 | ASYNC-RACE-A 2 tx same nonce | ✅ Foundry |
| 17 | COMP-01 OFAC blocked | ❌ Missing (Postman) |
| 18 | SCALE-TRAFFIC-A x10 traffic | ❌ Missing (k6) |
| 19 | FE-MOBILE-A Onboarding happy path | ❌ Missing (Maestro) |
| 20 | OPS-RUNBOOK-B Kill switch | ❌ Missing (Game day) |

**Coverage:** 8/20 smoke tests implemented (40%)
