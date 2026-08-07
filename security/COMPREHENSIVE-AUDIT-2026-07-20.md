# MDAOPay — Comprehensive Audit (3rd Pass)
> Date: 2026-07-20
> Agents: Smart Contract Reviewer, Backend+Relay Reviewer, PRD/TDD/CodeQuality Planner
> Status: DRAFT — pending user approval

---

## Executive Summary

| Agent | Findings | CRIT | HIGH | MED | LOW | INFO | Score |
|-------|----------|------|------|-----|-----|------|-------|
| Smart Contracts | 18 | 1 | 3 | 6 | 5 | 3 | 62/100 |
| Backend + Relay | 30 | 2 | 6 | 12 | 8 | 4 | REJECT |
| PRD/TDD/CQ/TN | 34 | 1 | 7 | 17 | 7 | 2 | — |
| **TOTAL** | **82** | **4** | **16** | **35** | **20** | **9** | **REJECT** |

**Verdict: REJECT** — 4 CRITICAL + 16 HIGH findings across all layers.

---

## 🔴 CRITICAL Findings (4)

### C-1: ERC-4337 Version Mismatch (SC-001 + CQ-001)
**Contracts:** MDAOSmartAccount (v0.7 PackedUserOperation) vs MDAOPaymaster (v0.6 UserOperation)
**Impact:** Deployment blocker — different ABI encodings produce garbage values.
**Also:** F-113 ERC-4337 v0.6 is deprecated. Bundlers may drop support.
**Fix:** Migrate both to v0.7. Align Paymaster to `IPaymaster` + `PackedUserOperation`.

### C-2: MoonPay API Key Leak (BR-001)
**Component:** `Application.kt /moonpay-proxy`
**Impact:** Server-side API key embedded in 302 redirect URL → visible in browser address bar, Referer header, proxy logs.
**Fix:** Remove 302 redirect. Proxy server-side. Never expose API key to client.

### C-3: Relay Shared Secret (BR-002)
**Component:** `relay/src/index.ts`, `siwe.ts`, `auth.ts`
**Impact:** Same `RELAY_SECRET` used for HMAC request auth AND JWT signing. Key separation violation → potential signature forgery.
**Fix:** Separate `RELAY_SECRET` (HMAC) and `JWT_SECRET` (JWT signing).

### C-4: Testnet Blockers (TN-001)
**Component:** GAP-REPORT.md
**Impact:** 9 blockers (B1-B9) unresolved: CI/CD secrets, AWS dependency, no testnet E2E.
**Fix:** Resolve all 9 blockers before testnet deploy.

---

## 🟠 HIGH Findings (16)

### Smart Contracts
| ID | Finding | Fix |
|----|---------|-----|
| SC-002 | `onlyOwner` includes `_entryPoint` — compromised EP transfers ownership of ALL accounts | Remove EP from onlyOwner |
| SC-003 | `postOp` reentrancy — nonReentrant removed, v0.6 EP has no guard | Re-add ReentrancyGuard |
| SC-004 | `dailyBalanceSnapshot` stale — large deposit after snapshot locks withdrawals | Use `max(snapshot, current)` or update snapshot on deposit |

### Backend + Relay
| ID | Finding | Fix |
|----|---------|-----|
| BR-003 | Relay SIWE no nonce → unlimited signature replay | Add nonce storage + verification |
| BR-004 | Price oracle MDAO bounds 0.0001..100.0 → free gas via manipulation | Tighten to 0.001..1.0, add TWAP |
| BR-005 | SIWE nonce TOCTOU — concurrent requests both authenticate | Atomic DELETE RETURNING |
| BR-006 | Relay rate limiter per-isolate → bypassable | Use Durable Objects or KV |
| BR-007 | Etherscan proxy per-IP rate limit → API key exhaustion | Add global per-key limit |
| BR-013 | PaymasterService logs full token balances | Truncate in logs |

### PRD/TDD/CQ/TN
| ID | Finding | Fix |
|----|---------|-----|
| PRD-001 | Session Keys no E2E biometric test | Add Maestro E2E |
| TDD-001 | MDAOSmartAccount missing from TDD | Add spec section |
| TDD-002 | TDD nickname spec wrong (3-32 vs actual 3-20) | Fix to 3-20 |
| TN-002 | Deploy script requires AWS CLI, no fallback | Add env var fallback |
| TN-003 | No E2E test against actual testnet | Add testnet fork CI job |
| CQ-002 | P-256 signature malleability — no low-s check | Add `if (s > n/2) revert` |
| CQ-003 | InsuranceFund auditor signatures not verified on-chain | Implement on-chain verify |

---

## 🟡 MEDIUM Findings (35)

### Smart Contracts (6)
| ID | Finding |
|----|---------|
| SC-005 | `withdrawStuckTokens` can drain all Treasury funds |
| SC-006 | `setDailyWithdrawalCapBps` allows 100% cap |
| SC-007 | `initiateRecovery` deposit calculation front-runnable |
| SC-008 | `vetoRecovery` blocks approved guardian from vetoing |
| SC-010 | `executeAllocation` sets executed=true before loop — failed recipients can't retry |
| SC-011 | `Proposal.vote` snapshot-free — double-voting possible |

### Backend + Relay (12)
| ID | Finding |
|----|---------|
| BR-008 | No CORS on backend |
| BR-009 | Debug `/metrics` leaks JVM internals |
| BR-010 | Hardcoded swap gas limit (500K) |
| BR-011 | SwapService no recipient address validation |
| BR-012 | Nickname signature not EIP-712 domain-separated |
| BR-013 | PaymasterService logs full token balances |
| BR-014 | CoinGecko API key in URL query parameter |
| BR-015 | Email/password auth has SIWE users with empty password hashes |
| BR-016 | Swap private key loaded into memory |
| BR-017 | Relay body not validated as JSON before HMAC |
| BR-018 | Watchtower webhook no dedup/rate limit |
| RR-001 | KV eventual consistency in approval counting |

### PRD/TDD/CQ (17)
| ID | Finding |
|----|---------|
| PRD-002 | On-Ramp PRD mismatch (Telegram bot vs MoonPay) |
| PRD-003 | Service Cards PRD mismatch (only DEX has code) |
| TDD-003 | TDD says recovery 3-of-5, code is 2-of-3 |
| TDD-004 | Deprecated constructor spec still in TDD |
| TDD-005 | Coverage baseline stale |
| CQ-005 | Duplicate UserOp builder code (~450 lines) |
| CQ-006 | Hardcoded gas limits |
| CQ-007 | Placeholder addresses in config |
| CQ-008 | InsuranceFund owner bypass multi-sig limits |
| CQ-009 | Treasury allocation ID collision risk |
| CQ-010 | SocialRecovery deposit accounting mismatch |
| CQ-011 | Paymaster rounds down charges |
| CQ-012 | 10+ OPEN findings no remediation plan |
| TN-004 | Deploy auditor defaults to deployer |
| TN-005 | Pre-launch chaos tests all PENDING |
| TN-006 | Smoke CI pipeline described but doesn't exist |
| SC-009 | `onlyWalletOrGuardian` modifier unused (dead code) |

---

## Cross-Layer Integration Matrix

| Integration | Status | Risk |
|-------------|--------|------|
| Paymaster ↔ EntryPoint | **BROKEN** (v0.6 vs v0.7) | 🔴 CRITICAL |
| SmartAccount ↔ EntryPoint | v0.7 only | 🟠 HIGH (SC-002) |
| Backend ↔ MoonPay | API key leaked | 🔴 CRITICAL |
| Relay ↔ KV (approvals) | Eventual consistency | 🟡 MEDIUM |
| Relay ↔ Backend (auth) | Shared secret | 🔴 CRITICAL |
| Backend ↔ PriceOracle | Wide bounds | 🟠 HIGH |
| Mobile ↔ WebView | Whitelist mismatch | 🟠 (from prev audit) |
| SocialRecovery ↔ SmartAccount | Works | ✅ OK |
| SessionKey ↔ SocialRecovery | Works | ✅ OK |
| Treasury ↔ PaymentSplitter | Works | ✅ OK |

---

## Attack Simulation (5-Year Perspective)

### Immediate (0-6 months)
1. **MoonPay key extraction** — trivial, anyone with network access to proxy
2. **SIWE replay on relay** — no nonce → stolen signatures reusable
3. **ERC-4337 v0.6 → bundler death** — if bundlers drop v0.6, system non-functional
4. **Price oracle manipulation** — MDAO low liquidity → free gas via inflated price

### Medium-term (6-18 months)
5. **ERC-4337 v0.7 migration** — breaking changes require contract redeploy
6. **Bundler landscape consolidation** — v0.6 support drops across ecosystem
7. **MDAO token liquidity growth** — price manipulation becomes harder but exploit still viable at current bounds

### Long-term (18-60 months)
8. **Post-quantum P-256** — P-256 (secp256r1) is NOT quantum-resistant. WebAuthn reliance creates long-term migration need
9. **Account abstraction standardization** — ERC-4337 v0.8+ likely, another migration
10. **Regulatory compliance** — KYC/AML requirements may conflict with self-custody design

---

## Strengths as Weaknesses (Reverse SWOT)

| Strength | Hidden Weakness |
|----------|-----------------|
| 97 security findings fixed | Creates false confidence — new audit found 82 MORE findings |
| Comprehensive TDD (2969 lines) | MDAOSmartAccount not documented, nickname spec wrong |
| Strong CI (7 jobs, fuzz, invariants) | Mobile not in CI, smoke pipeline doesn't exist |
| EIP-712 everywhere | Nickname service doesn't use EIP-712 (BR-012) |
| USDT false-return handling in Paymaster | Treasury has same pattern but missed the boolean check initially (now fixed) |
| 48h timelock on verifier swap | No timelock on emergencySunset (by design, but asymmetric) |
| 403/403 forge tests passing | 32 tests were failing before P-256 key fix (pre-existing) |

---

## Testnet Readiness Verdict

### Blockers (must resolve before testnet)
1. **SC-001**: ERC-4337 version mismatch — deployment will fail
2. **B1-B9**: GAP-REPORT blockers (CI/CD secrets, AWS dependency)
3. **BR-001**: MoonPay key leak — cannot deploy with proxy as-is
4. **BR-002**: Relay shared secret — cannot deploy with single secret

### Should resolve (risk of testnet failure)
5. **SC-003**: Reentrancy in postOp
6. **SC-002**: EP can transfer ownership
7. **BR-004**: Price manipulation in testnet (lower liquidity = easier)
8. **TN-003**: No E2E against testnet (can't verify deploy worked)

### Can defer to mainnet
9. PRD/TDD documentation gaps
10. Dead code removal
11. Code quality improvements (CQ-005 through CQ-012)

---

## Recommended Execution Order

```
Phase 1: BLOCKERS (deployment possible)
├── SC-001: ERC-4337 v0.7 migration (Paymaster + SmartAccount)
├── BR-001: MoonPay proxy fix (server-side, no 302)
└── BR-002: Relay secret separation (JWT_SECRET)

Phase 2: CRITICAL SECURITY
├── SC-002: Remove EP from onlyOwner
├── SC-003: Re-add ReentrancyGuard to postOp
├── BR-003: Relay SIWE nonce
└── BR-005: Backend SIWE atomic nonce

Phase 3: HIGH RISK
├── SC-004: dailyBalanceSnapshot fix
├── BR-004: Price oracle bounds tightening
├── BR-006: Relay rate limiter upgrade
├── CQ-002: P-256 low-s check
└── B1-B9: GAP-REPORT blockers

Phase 4: TESTNET DEPLOY
├── Deploy to testnet
├── Run E2E tests against testnet
└── Chaos testing

Phase 5: MAINNET PREP
├── All MEDIUM findings
├── TDD/PRD documentation sync
├── Mobile E2E tests
└── Final security review
```

---

## Previous Audit Cross-Reference

| Previous Finding | Status | This Audit |
|------------------|--------|------------|
| F-113 (ERC-4337 v0.6 deprecated) | OPEN | ✅ Confirmed CRITICAL (SC-001) |
| F-142 (SessionKey delegation) | OPEN | Still OPEN, needs integration test |
| F-149 (Treasury batch revert) | FIXED | ✅ Verified fixed in Phase 1 |
| F-150 (Paymaster daily cap drain) | FIXED | ✅ Verified fixed in Phase 2 |
| F-151 (postOp decode crash) | FIXED | ✅ Verified fixed in Phase 2 |
| F-153 (Recovery deposit refund) | FIXED | ✅ Verified fixed in Phase 2 |
| F-156 (Registry state machine) | FIXED | ✅ Verified fixed in Phase 1 |
| F-138a (P-256 verifier timelock) | FIXED | ✅ Verified fixed in Phase 1 |
| F-146 (Infinite proposal retry) | FIXED | ✅ Verified fixed in Phase 2 |
| F-157 (Registry grace period) | FIXED | ✅ Verified fixed in Phase 2 |
| 403/403 forge tests | ✅ PASSING | All tests green |

---

> **Self-challenge:**
> 1. What might be wrong? The 82 findings count may miss cross-layer interactions not tested (e.g., mobile ↔ relay ↔ smart contract end-to-end).
> 2. Strongest counter-argument? Some findings (SC-007, SC-008) are theoretical and economically unexploitable.
> 3. Simpler alternative? Focus only on the 4 CRITICAL + 5 HIGH blockers for testnet — defer everything else.
> 4. What was assumed without evidence? That ERC-4337 v0.6 bundler support timeline is short — no concrete date found.
