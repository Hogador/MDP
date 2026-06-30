# Wave 11 — Internal Comprehensive Security Audit

**Date:** 2026-06-26
**Type:** Internal — full automated scan (researcher swarm × 5)
**Mode:** hybrid
**Coordinator model:** opencode/big-pickle
**Models used:** big-pickle (coordinator, code-reviewer), deepseek-v4-flash-free (researchers), glm-5.2 (architect, verifier), north-mini-code-free (librarian)

---

## Scope

| Layer | Files scanned | Researcher |
|-------|--------------|------------|
| **contracts/** | 11 .sol files + tests | researcher-contracts |
| **backend/** | 19 Kotlin files + configs | researcher-backend |
| **relay/** | 5 TS source files + config | tester |
| **app/** (Android) | 40+ files | researcher-mobile |
| **infra/** | Docker, CI, DB migrations | researcher-infra |

---

## Wave 11 Results (First pass — 30 new findings)

| Severity | Count | IDs |
|----------|-------|-----|
| HIGH | 6 | F-042 (wrangler dev), F-043 (CI), F-048 (DeadManSwitch), F-054 (auth rate limit), F-059 (JS Bridge), F-065 (FCM) |
| MEDIUM | 16 | F-044..F-046, F-049, F-050, F-053, F-055..F-057, F-060, F-061, F-066..F-070 |
| LOW | 7 | F-047, F-051, F-052, F-058, F-062, F-063, F-071 |
| INFO | 1 | F-064 |

**Total:** 30 new findings added to FINDINGS.md (v1.1.0→1.2.0)

---

## Severity Corrections (Wave 12)

| Finding | Old | New | Reason |
|---------|-----|-----|--------|
| F-043 | HIGH | MEDIUM | Slither already in CI (Wave 1) |
| F-060 | MEDIUM | HIGH | Forged Play Integrity verdict bypasses device check |
| F-062 | LOW | HIGH | BIOMETRIC_WEAK allows photo bypass for recovery flow |

---

## Top 3 Fixes Applied

| Finding | Fix | Files changed | Tests |
|---------|-----|---------------|-------|
| **F-065** | Pass `env` param to `sendPushNotification()` | `relay/src/fcm.ts`, `index.ts` | relay 7/7 PASS |
| **F-054** | Add RedisRateLimiter to auth endpoints | `backend/.../Application.kt` | gradle timeout (network) |
| **F-048** | Per-wallet deposits + CEI + ReentrancyGuard | `contracts/src/DeadManSwitch.sol`, `DeadManSwitch.t.sol` | 17/17 PASS (+3 new) |

---

## Rescan Results (Second pass — 14 new findings)

| Severity | Count | Key issues |
|----------|-------|------------|
| HIGH | 2 | F-072 (ping after trigger blocks claim), F-NEW (IP rate limit behind proxy) |
| MEDIUM | 4 | CI supply chain, resource limits, registerPushToken race, lock screen leak |
| LOW | 8 | Dependabot Docker, Docker root, RPC cache race, no fetch timeout, no CORS, stale FCM tokens, +2 |

---

## Fixes Verified

| Finding | Status | Details |
|---------|--------|---------|
| F-048 | ✅ VERIFIED | Per-wallet mapping, CEI, nonReentrant, 17/17 tests |
| F-054 | ⚠️ Not committed | Code correct, needs commit + regression tests |
| F-065 | ⚠️ Not committed | Code correct, needs commit + fcm.test.ts |

---

## Coverage Gaps (still open)

- **Relay:** fcm.ts, storage.ts, index.ts — 0% test coverage
- **Backend:** AuthService, WatchtowerService, SwapService, OnrampService — 0 tests
- **App:** PasskeyManager, MDAOWebView, UI — 0 tests
- **Contracts:** F-040 (price cooldown test), F-041 (failure diff test) — missing

---

## Files Changed

| File | Change |
|------|--------|
| `security/FINDINGS.md` | v1.1.0→1.2.0, +44 findings, severity corrections |
| `security/FINDINGS-INDEX.md` | Regenerated (113 lines) |
| `security/ERRORS-MEMORY.md` | +6 error patterns (EM-065..EM-070) |
| `security/ERRORS-INDEX.md` | Regenerated (322 lines) |
| `security/findings/F-042.md`..`F-085.md` | 44 new detail files |
| `docs/orchestration/CODE-INDEX.md` | Updated with 72+ references |
| `contracts/src/DeadManSwitch.sol` | F-048 fix: per-wallet deposits, CEI, nonReentrant |
| `contracts/test/DeadManSwitch.t.sol` | +3 tests for F-048 |
| `relay/src/fcm.ts` | F-065 fix: env param instead of global |
| `relay/src/index.ts` | F-065 fix: pass env to sendPushNotification |
| `backend/.../Application.kt` | F-054 fix: auth rate limiting |

---

## Findings Registry Status

| Status | Count |
|--------|-------|
| OPEN | 56 |
| CLAIMED_FIXED | 7 |
| VERIFIED | 3 |
| REGRESSED | 4 |
| CONFLICT | 2 |
| **Total** | **72** |

---

## Open HIGH Priorities (not yet fixed)

1. **F-059** — Ethereum JS Bridge (WebView signing exposure)
2. **F-060** — Play Integrity no JWT verification
3. **F-062** — BIOMETRIC_WEAK for recovery ops
4. **F-042** — wrangler dev in production Dockerfile
5. **F-072** — DeadManSwitch ping after trigger blocks claimFunds
6. **F-NEW** — IP rate limiting bypass behind reverse proxy
