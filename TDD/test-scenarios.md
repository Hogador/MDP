# MDAOPay — Test Scenarios v5.2 (Final, TDD-linked, Wave 11-13)

> **Версия:** 5.2.0
> **Дата:** Июнь 2026
> **Сценариев:** 457 (consolidated) + 24 smoke
> **Связанные документы:** Design Bible v1.0.0, TDD v2.7, HTML Prototype
> **Ключевое:** TDD linkage + Automation matrix + Test fixtures + Chaos testing + Smoke suite + Wave 11-12 findings + Wave 13 (TimelockController + TrustProviderRegistry)

---

## Содержание

1. [Классификация](#1-классификация)
2. [Risk Formula v2](#2-risk-formula-v2)
3. [Existential Scenarios](#3-existential-scenarios-ex)
4. [Consolidated Scenario Families (с TDD ref + Automation)](#4-consolidated-scenario-families)
5. [Defensive Architecture](#5-defensive-architecture)
6. [Chaos Testing Protocol](#6-chaos-testing-protocol-новое)
7. [Smoke Test Suite](#7-smoke-test-suite-новое)
8. [Implementation Roadmap](#8-implementation-roadmap)
9. [Metrics & Dashboards](#9-metrics--dashboards)
10. [Appendices](#10-appendices)

---

# 1. Классификация

| Критерий | Градации |
|----------|----------|
| **Tier** | Existential (проект умирает), Critical (потеря средств), High (нарушение работы), Medium (UX), Low (косметика) |
| **Risk** | I (1-10), P (0.001-1.0), D (0.01-1.0), R (1-10 Irreversibility), C (1-10 Cost) |
| **Automation** | Auto: Smoke (CI every PR), Auto: Full (regression), Auto: Contract (Foundry), Manual (human) |
| **TDD Ref** | Ссылка на раздел TDD v2.5 (например, §1.2.1 PaymasterService) |

---

# 2. Risk Formula v2

```
Priority Score = (Impact × Probability × Detectability × Irreversibility) / Mitigation Cost
```

| Переменная | Диапазон | Описание |
|------------|----------|----------|
| **Impact (I)** | 1-10 | Сила последствия |
| **Probability (P)** | 0.001-1.0 | Частота |
| **Detectability (D)** | 0.01-1.0 | Скорость обнаружения |
| **Irreversibility (R)** | 1-10 | Насколько событие обратимо |
| **Cost (C)** | 1-10 | Effort на mitigation |

### Threshold'ы

| Score | Priority | Action |
|-------|----------|--------|
| > 5.0 | **P0-EXISTENTIAL** | CEO-level, block launch |
| 1.0 – 5.0 | **P0** | Must fix before launch |
| 0.1 – 1.0 | **P1** | Fix in first quarter |
| 0.01 – 0.1 | **P2** | Backlog, monitor |
| < 0.01 | **P3** | Accept risk |

---

# 3. Existential Scenarios (EX)

15 сценариев, убивающих проект. Каждый с TDD ref и mitigation.

| ID | Сценарий | I | P | D | R | Score | TDD Ref | Mitigation |
|----|----------|---|---|---|---|-------|---------|------------|
| EX-01 | Компрометация мастер-ключей обновления контрактов | 10 | 0.05 | 0.1 | 10 | 0.50 | §3.7, §4.5 | Hardware multisig (Ledger Enterprise), 5/7 signers |
| EX-02 | Потеря доверия к recovery модели | 10 | 0.1 | 0.5 | 8 | 4.00 | §3.4 SocialRecoveryModule | 2-of-3 threshold + 24h timelock + veto; bug bounty $100k+ |
| EX-03 | Экономическая смерть Paymaster | 10 | 0.15 | 0.5 | 7 | 5.25 | §3.3 MDAOPaymaster, §1.10 | Auto-replenish from treasury; 30-day runway; circuit breaker |
| EX-04 | Компрометация username registry | 10 | 0.05 | 0.3 | 9 | 1.35 | §3.5 NicknameRegistry | Decentralize; 2/3 multisig + timelock |
| EX-05 | Критическая ошибка аудита после mainnet | 10 | 0.1 | 0.3 | 8 | 2.40 | §5.4 Security Tests | Circuit breaker + pause; insurance fund; 24h incident SLA |
| EX-06 | 51% attack на underlying chain | 10 | 0.01 | 1.0 | 9 | 0.90 | §4.2 RPC Multi-Provider | Wait for 12+ confirmations; multi-chain redundancy |
| EX-07 | Bridge hack с funds in transit | 10 | 0.05 | 0.5 | 9 | 2.25 | — (external bridge) | Audited bridges only; per-bridge TVL cap; insurance |
| EX-08 | Founder/team rug pull | 10 | 0.05 | 0.5 | 10 | 2.50 | §4.5 Key Rotation | Treasury multisig (3/5 community + team); vesting cliff |
| EX-09 | Regulatory kill order | 10 | 0.1 | 1.0 | 8 | 8.00 | — (legal) | Legal opinion (MoFo/Cooley); geo-fencing; MiCA prep |
| EX-10 | Standard deprecation (ERC-4337 v0.7→v0.8) | 10 | 0.05 | 0.5 | 8 | 2.00 | §3.7 Upgradeability | Exit window (7 days); migration tooling; multi-version |
| EX-11 | Insurance fund drain | 10 | 0.05 | 0.5 | 7 | 1.75 | §3.3 (new InsuranceFund) | Cap per-claim at 10%; reinsurance (Nexus Mutual) |
| EX-12 | Mass guardian compromise | 10 | 0.02 | 0.3 | 9 | 0.54 | §3.4 SocialRecoveryModule | Guardian diversity; 24h timelock; veto alerts |
| EX-13 | Key infrastructure provider shutdown | 9 | 0.05 | 1.0 | 6 | 2.70 | §4.2 RPC Multi-Provider | Multi-bundler fallback; self-hosted capability; 48h runway |
| EX-14 | Token liquidity collapse | 10 | 0.05 | 0.5 | 8 | 2.00 | §3.2 MDAOToken | Paymaster accepts USDT/USDC fallback; LP locking |
| EX-15 | Coordinated MEV extraction | 9 | 0.1 | 0.3 | 7 | 1.89 | §4.2 | Private mempool (Flashbots Protect); per-user rate limits |

---

# 4. Consolidated Scenario Families

Каждая family теперь содержит:
- **TDD Ref** — ссылка на раздел TDD v2.5
- **Automation** — Auto: Smoke / Auto: Full / Auto: Contract / Manual
- **Variants** — конкретные подварианты

## 4.1. Security (SEC) — 9 families, 60 variants

### SEC-REPLAY (6 variants)
Replay-атаки на подписи и transactions.
- **A**: UserOp replay (same nonce)
- **B**: Permit nonce reuse
- **C**: Cross-chain paymaster sig replay
- **D**: Signature malleability (s → n-s)
- **E**: InitCode cross-chain replay
- **F**: ERC-1271 cross-contract replay

| Field | Value |
|-------|-------|
| **TDD Ref** | §1.4 UserOp Hash Computation, §3.3 MDAOPaymaster, §3.5 NicknameRegistry |
| **Automation** | Auto: Contract (Foundry invariant tests) |
| **Score range** | 0.5 – 4.5 |

### SEC-PHISHING (7 variants)
Подмена получателя и social engineering.
- **A**: WebView hijack → wrong recipient
- **B**: Fake dApp → malicious signing
- **C**: Seed phishing (fake wallet checker)
- **D**: Clipboard hijack (address replacement)
- **E**: Transaction simulation spoofing
- **F**: WebView bridge origin spoofing (F-059) — malicious dApp attempts to call ethereum provider before origin validation
- **G**: WebView bridge navigation leak (F-059) — bridge not cleaned up after navigation, allowing cross-site data access

| Field | Value |
|-------|-------|
| **TDD Ref** | §2.2.2 EthereumProviderInjector (F-059 origin validation, domain whitelist, bridge cleanup), §2.3 Security Architecture |
| **Automation** | Auto: Full (JUnit) + Manual |
| **Score range** | 2.0 – 7.2 |

### SEC-DOS (7 variants)
Denial of service.
- **A**: Paymaster spam via /sign
- **B**: Cross-account nonce gap DoS
- **C**: RPC spam → rate limit exhaustion
- **D**: Bundler mempool flooding
- **E**: GraphQL query bomb
- **F**: Auth login brute-force (F-054) — exceed 5/min/IP rate limit
- **G**: Auth register spam (F-054) — exceed 3/min/IP, register with disposable emails

| Field | Value |
|-------|-------|
| **TDD Ref** | §1.2.1 Paymaster Service, §1.9 Rate Limiting (auth rates F-054), §4.2 RPC Multi-Provider |
| **Automation** | Auto: Full (k6 load tests) |
| **Score range** | 0.3 – 2.5 |

### SEC-RECOVERY (5 variants)
Атаки на social recovery.
- **A**: Guardian collusion 2-of-3
- **B**: Front-running recovery execution
- **C**: Time-lock bypass (short delay)
- **D**: Fake veto (block legitimate recovery)
- **E**: Malicious guardian module at init

| Field | Value |
|-------|-------|
| **TDD Ref** | §3.4 SocialRecoveryModule (29 unit tests) |
| **Automation** | Auto: Contract (Foundry `SocialRecoveryModule.t.sol`) |
| **Score range** | 0.2 – 3.0 |

### SEC-KEYS (5 variants)
Compromise ключей.
- **A**: P256 verifier substitution
- **B**: Aggregator stale signature
- **C**: Wallet init with malicious module
- **D**: Deployer key compromise
- **E**: Guardian identity spoofing

| Field | Value |
|-------|-------|
| **TDD Ref** | §3.4 SocialRecoveryModule (P-256 Verification), §4.5 Key Rotation |
| **Automation** | Auto: Contract + Manual (deployer key = hardware) |
| **Score range** | 0.3 – 2.5 |

### SEC-CONTRACT (11 variants)
Smart contract-level атаки.
- **A**: Paymaster deposit drain via reverted ops
- **B**: EntryPoint simulation vs execution divergence
- **C**: Storage layout collision on upgrade
- **D**: Self-destruct in delegatecall
- **E**: Initializer re-initialization
- **F**: Reentrancy in validateUserOp
- **G**: CREATE2 address prediction
- **H**: Read-only reentrancy
- **I**: DeadManSwitch two-phase trigger bypass (F-072) — attempt to claim before challenge period, challenge after claim
- **J**: DeadManSwitch per-wallet deposit cross-contamination (F-048) — CEI pattern bypass, reentrancy via deposit
- **K**: InsuranceFund unverified auditor signatures (F-052) — submit claim with forged signatures

| Field | Value |
|-------|-------|
| **TDD Ref** | §3.3 MDAOPaymaster (20 tests), §3.6 InsuranceFund (F-052), §3.7 DeadManSwitch (29 tests, F-048, F-072), §3.7 Upgradeability |
| **Automation** | Auto: Contract (Foundry fuzz + invariant) |
| **Score range** | 0.5 – 4.5 |

### SEC-SIDEDHANNEL (6 variants)
Side-channel и физические атаки.
- **A**: Timing attack on ECDSA
- **B**: Power analysis on hardware wallet
- **C**: Cache timing on shared resources
- **D**: Acoustic cryptanalysis
- **E**: Rowhammer bit flip
- **F**: Cosmic ray bit flip

| Field | Value |
|-------|-------|
| **TDD Ref** | §2.3.1 Key Hierarchy, §2.3.2 KeystoreKey Configuration |
| **Automation** | Manual (requires lab equipment) |
| **Score range** | 0.1 – 1.5 |

### SEC-NETWORK (9 variants)
Сетевые атаки.
- **A**: DNS rebinding
- **B**: TLS downgrade
- **C**: BGP hijack of RPC
- **D**: DNS cache poisoning
- **E**: Certificate pinning bypass
- **F**: Eclipse attack on RPC
- **G**: Sybil attack
- **H**: Timejacking
- **I**: X-Forwarded-For IP spoofing (F-054) — send forged X-Forwarded-For header to bypass rate limits

| Field | Value |
|-------|-------|
| **TDD Ref** | §1.2.1 (IP extraction proxy-safe), §4.2 RPC Multi-Provider, §4.5 Security Posture |
| **Automation** | Auto: Full (chaos mesh) + Manual |
| **Score range** | 0.2 – 2.0 |

### SEC-SOCIAL (3 variants) ⭐ split from SEC-OTHER
Social engineering атаки.
- **A**: Social engineering (shares transfer)
- **B**: Seed interception at import
- **C**: User lets "tech support" remote control

| Field | Value |
|-------|-------|
| **TDD Ref** | §2.3.3 Shamir Secret Sharing, §2.3.7 Screen-Off Lock |
| **Automation** | Manual |
| **Score range** | 1.0 – 3.5 |

### SEC-WEB (5 variants) ⭐ split from SEC-OTHER
Web-уязвимости.
- **A**: XSS via nickname
- **B**: CSRF on POST /sign
- **C**: SSRF in avatar URL
- **D**: HTTP request smuggling
- **E**: Open redirect in callback URL

| Field | Value |
|-------|-------|
| **TDD Ref** | §1.2 API Contracts, §1.8 Authentication |
| **Automation** | Auto: Full (OWASP ZAP, weekly per §5.4) |
| **Score range** | 0.5 – 2.5 |

### SEC-CRYPTO (4 variants) ⭐ split from SEC-OTHER
Криптографические атаки.
- **A**: Timing attack on auth compare
- **B**: Padding oracle on JWT (if JWE)
- **C**: ReDoS in validation regex
- **D**: Prototype pollution (merge user JSON)

| Field | Value |
|-------|-------|
| **TDD Ref** | §1.8 Authentication (JWT), §2.3 Security Architecture |
| **Automation** | Auto: Contract + Auto: Full |
| **Score range** | 0.3 – 2.0 |

### SEC-JWT (3 variants) ⭐ split from SEC-OTHER
JWT-specific атаки.
- **A**: JWT refresh token race
- **B**: JWT replay
- **C**: Rate limit bypass via X-Forwarded-For

| Field | Value |
|-------|-------|
| **TDD Ref** | §1.8 Authentication (JWT Token Structure), §1.9 Rate Limiting |
| **Automation** | Auto: Full (JUnit + Postman) |
| **Score range** | 0.5 – 2.5 |

## 4.2. Account Abstraction (AA) — 5 families, 28 variants

### AA-INIT (5 variants)
- **A**: InitCode execution reverts
- **B**: Sender address mismatch with initCode
- **C**: Stuck account (first tx fails init)
- **D**: Factory deposit insufficient
- **E**: CREATE2 salt collision

| Field | Value |
|-------|-------|
| **TDD Ref** | §3.1 Overview, §3.3 MDAOPaymaster (initCode handling) |
| **Automation** | Auto: Contract (Foundry) |

### AA-VALIDATION (6 variants)
- **A**: ValidationData timestamp expired
- **B**: maxFeePerGas < baseFee
- **C**: VerificationGasLimit too low
- **D**: PreVerificationGas calculation wrong
- **E**: Aggregator signature without contract
- **F**: PaymasterPostOp revert loop

| Field | Value |
|-------|-------|
| **TDD Ref** | §1.4 UserOp Hash, §3.3 MDAOPaymaster (Validation Flow) |
| **Automation** | Auto: Contract (Foundry) |

### AA-EXECUTION (5 variants)
- **A**: callData > 100KB
- **B**: callGasLimit manipulation
- **C**: EntryPoint version mismatch (v0.6 vs v0.7)
- **D**: Multi-EntryPoint compatibility
- **E**: L2 EntryPoint different address

| Field | Value |
|-------|-------|
| **TDD Ref** | §3.1 Overview (EntryPoint 0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789), §3.7 Upgradeability |
| **Automation** | Auto: Contract |

### AA-L2 (6 variants)
- **A**: L2 precompile differences
- **B**: L2 gas metering differences
- **C**: L2 sequencer downtime
- **D**: L2 → L1 messaging delay
- **E**: L2 forced inclusion
- **F**: DA committee failure

| Field | Value |
|-------|-------|
| **TDD Ref** | §4.2 RPC Multi-Provider, §1.10 Price Oracle |
| **Automation** | Auto: Full (requires L2 testnet) |

### AA-BUNDLER (6 variants)
- **A**: Bundler bankruptcy mid-bundle
- **B**: Multi-bundler race condition
- **C**: Bundler fallback (primary down)
- **D**: Bundler bans sender (24h)
- **E**: Bundler competition (gas auction)
- **F**: Reputation system gaming

| Field | Value |
|-------|-------|
| **TDD Ref** | §4.2 RPC Multi-Provider (failover), §1.2.1 Paymaster |
| **Automation** | Auto: Full + Manual (multi-bundler setup) |

## 4.3. Paymaster (PAY) — 4 families, 23 variants

### PAY-ECONOMICS (8 variants)
- **A**: Balance < cost of bundle
- **B**: Token price moves > 20% (validate → postOp)
- **C**: User has 0 MDAO, no fallback
- **D**: Over-charges (postOp lists 2x)
- **E**: Refund not received after failed tx
- **F**: Signature replay across bundles
- **G**: Slippage protection bypassed
- **H**: Paymaster from OFAC blacklist

| Field | Value |
|-------|-------|
| **TDD Ref** | §3.3 MDAOPaymaster (Post-Operation Flow, TOCTOU Risk), §1.10 Price Oracle |
| **Automation** | Auto: Contract (Foundry `MDAOPaymaster.t.sol`, 20 tests) |

### PAY-MULTI (5 variants)
- **A**: Switching mid-session (A down → B)
- **B**: Paymaster A списал, B не в курсе
- **C**: Whitelist conflict (A=BSC, B=Polygon, user on ETH)
- **D**: Different EntryPoint versions
- **E**: Reputation gaming between paymasters

| Field | Value |
|-------|-------|
| **TDD Ref** | §3.3 MDAOPaymaster (Admin Functions), §4.2 |
| **Automation** | Auto: Full + Manual |

### PAY-ORACLE (5 variants)
- **A**: Flash loan price manipulation
- **B**: Chainlink staleness
- **C**: Oracle deviation (multiple disagree)
- **D**: Sequencer oracle outage
- **E**: DEX reserves drained

| Field | Value |
|-------|-------|
| **TDD Ref** | §1.10 Price Oracle Architecture, §1.7 Caching |
| **Automation** | Auto: Contract (Foundry fork) + Auto: Full |

### PAY-GAS (5 variants)
- **A**: Gas price x100 at confirm time
- **B**: Replacement underpriced
- **C**: EIP-1559 baseFee spike after broadcast
- **D**: maxPriorityFee > maxFeePerGas
- **E**: EIP-4844 blob gas miscalculation

| Field | Value |
|-------|-------|
| **TDD Ref** | §3.3 MDAOPaymaster (Validation Flow), §1.2.1 /sign |
| **Automation** | Auto: Contract |

## 4.4. Edge / Infrastructure (EDGE) — 4 families, 25 variants

### EDGE-INFRA (7 variants)
- **A**: Redis down (rate limit disabled)
- **B**: Redis down between validate/postOp
- **C**: Cloud Run restart mid-/sign
- **D**: Bundler bankruptcy mid-bundle
- **E**: Database connection pool exhaustion
- **F**: Redis memory overflow (cache stampede)
- **G**: WebSocket reconnect storm

| Field | Value |
|-------|-------|
| **TDD Ref** | §1.9 Rate Limiting (Redis Keys), §4.1 Cloud Infrastructure, §4.6 Backup & Recovery |
| **Automation** | Auto: Full (Chaos Mesh — see §6) |

### EDGE-CHAIN (6 variants)
- **A**: RPC returns stale nonce
- **B**: Reorg on 12+ blocks
- **C**: Chain congestion (gas 500 gwei)
- **D**: Token decimals mismatch (USDT 6 vs DAI 18)
- **E**: Price oracle flash crash
- **F**: MEV sandwich on paymaster swap

| Field | Value |
|-------|-------|
| **TDD Ref** | §4.2 RPC Multi-Provider, §1.10 Price Oracle, §3.2 MDAOToken (decimals) |
| **Automation** | Auto: Contract (Foundry fork mainnet) |

### EDGE-USER (6 variants)
- **A**: App closed during send
- **B**: Replacement transaction underpriced
- **C**: Force-quit during tx
- **D**: Background sync (7 days old nonce)
- **E**: App killed by OS (low memory)
- **F**: Multi-tab in browser (race on nonce)

| Field | Value |
|-------|-------|
| **TDD Ref** | §2.5 Send Flow, §2.3.7 Screen-Off Lock |
| **Automation** | Auto: Full (Maestro E2E) + Manual |

### EDGE-MIGRATION (6 variants)
- **A**: IndexedDB corruption
- **B**: Local storage tampered (XSS)
- **C**: App update, migration fails
- **D**: Logout doesn't clear biometric cache
- **E**: Service Worker update mid-tx
- **F**: Rollback after failed deploy

| Field | Value |
|-------|-------|
| **TDD Ref** | §2.2.3 Data Layer (Room), §4.3 CI/CD (Contract Address Auto-Update) |
| **Automation** | Auto: Full (Room MigrationTestHelper per §5.2) |

## 4.5. Async / Mass (ASYNC) — 3 families, 19 variants

### ASYNC-OFFLINE (6 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §2.2.3 Data Layer (OfflineSyncWorker), §5.2 Integration Tests |
| **Automation** | Auto: Full (TestListenableWorkerBuilder per §5.2) |

### ASYNC-RACE (6 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §4.2.1 Request Idempotency, §1.9 Rate Limiting |
| **Automation** | Auto: Full (JUnit + k6) |

### ASYNC-MASS (7 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §5.5 Load Tests (50 RPS /sign, 500 RPS /nickname/resolve) |
| **Automation** | Auto: Full (k6 per §5.5) |

## 4.6. Anti-logical (ILL) — 5 families, 22 variants

### ILL-INPUT (6 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §2.5 Send Flow (validation), §1.2 API Contracts |
| **Automation** | Auto: Smoke (property-based) |

### ILL-IMPORT (5 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §2.3.1 Key Hierarchy, §2.3.3 Shamir Secret Sharing |
| **Automation** | Auto: Full (JUnit `ShamirSecretSharingTest.kt`) |

### ILL-GUARDIAN (4 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §3.4 SocialRecoveryModule (Guardian lifecycle) |
| **Automation** | Auto: Contract (Foundry) |

### ILL-RECIPIENT (4 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §2.5 Send Flow, §2.8 Error Mapping |
| **Automation** | Auto: Full |

### ILL-OTHER (3 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §3.2 MDAOToken (decimals, MAX_SUPPLY) |
| **Automation** | Auto: Contract |

## 4.7. UX / Usability (UX) — 4 families, 22 variants

### UX-ERROR (6 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §2.5 Send Flow, §2.8 Error Mapping |
| **Automation** | Auto: Smoke (Maestro) + Manual |

### UX-FLOW (6 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §2.4 Onboarding Flow, §2.5 Send Flow |
| **Automation** | Manual |

### UX-A11Y (6 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §2.2.1 Presentation Layer |
| **Automation** | Manual (VoiceOver/TalkBack) |

### UX-MISC (4 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §2.6 Dynamic Island & Push, §2.8 Error Mapping |
| **Automation** | Manual |

## 4.8. Crisis / Panic (CRISIS) — 5 families, 25 variants

### CRISIS-TRUST-LOSS (5 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | — (operational, not engineering) |
| **Automation** | Manual (game day drills) |

### CRISIS-COMMS (5 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §2.6 Push Notifications |
| **Automation** | Manual |

### CRISIS-ACCESS (4 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §4.1 Cloud Infrastructure, §4.5 Security Posture |
| **Automation** | Manual |

### CRISIS-TEAM (5 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §4.5 Key Rotation |
| **Automation** | Manual |

### CRISIS-INCIDENT (6 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §4.4 Monitoring & Alerting, §4.6 Backup & Recovery |
| **Automation** | Manual (chaos drills) |

## 4.9. Economic (ECON) — 4 families, 20 variants

### ECON-PAYMASTER (5 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §3.3 MDAOPaymaster, §1.10 Price Oracle |
| **Automation** | Auto: Contract (Foundry fork) + Manual (economic modeling) |

### ECON-TOKEN (5 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §3.2 MDAOToken (Burn Fee Logic, MAX_SUPPLY) |
| **Automation** | Auto: Contract + Manual |

### ECON-ABUSE (5 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §1.9 Rate Limiting, §3.5 NicknameRegistry (frontrunning) |
| **Automation** | Auto: Full (k6) |

### ECON-PRODUCT (5 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §3.2 MDAOToken, §3.7 Upgradeability |
| **Automation** | Manual |

## 4.10. Product (PROD) — 3 families, 15 variants

### PROD-DEPENDENCY (5 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §4.2 RPC Multi-Provider |
| **Automation** | Manual |

### PROD-GOVERNANCE (5 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §3.7 Upgradeability & Migration |
| **Automation** | Auto: Contract (Foundry fork) |

### PROD-STRATEGIC (5 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §3.7, §6.4 Rollback Strategy |
| **Automation** | Manual |

## 4.11. Scale (SCALE) — 3 families, 15 variants

### SCALE-TRAFFIC (5 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §5.5 Load Tests |
| **Automation** | Auto: Full (k6) |

### SCALE-INFRA (5 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §4.1, §4.4 Monitoring |
| **Automation** | Auto: Full (k6 + Chaos Mesh) |

### SCALE-CHAIN (5 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §4.2, §1.10 Price Oracle |
| **Automation** | Auto: Full |

## 4.12. Operations (OPS) — 2 families, 10 variants

### OPS-ONCALL (5 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §4.4 Monitoring & Alerting |
| **Automation** | Manual (game day) |

### OPS-RUNBOOK (5 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §4.6 Backup & Recovery (RTO/RPO), §4.8 Relay FCM env parameter (F-065) |
| **Automation** | Manual |

## 4.13. Legal (LEG) — 2 families, 10 variants

### LEG-COMPLIANCE (5 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §1.6 Database Schema (audit_log, onramp_orders) |
| **Automation** | Manual |

### LEG-LIABILITY (5 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | — (legal) |
| **Automation** | Manual |

## 4.14. Developer Experience (DX) — 2 families, 10 variants

### DX-API (5 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §1.2 API Contracts |
| **Automation** | Auto: Full (Postman/Newman) |

### DX-SDK (5 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §2.7 WalletConnect Integration |
| **Automation** | Auto: Full |

## 4.15. Frontend / Mobile (FE) — 3 families, 20 variants

### FE-BROWSER (7 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §2.2 Application Layers |
| **Automation** | Auto: Full (Playwright) |

### FE-MOBILE (7 variants)
| Field | Value |
|-------|-------|
| **TDD Ref** | §2.3 Security Architecture, §4.3 Security Scanning |
| **Automation** | Auto: Full (Maestro) + Manual |

### FE-DEVICE (9 variants)
- **A–F**: (from v5.0)
- **G**: Play Integrity JWT forgery (F-060) — attempt to pass forged JWT with wrong signature, expired timestamp, wrong package name
- **H**: Play Integrity JWKS cache poisoning (F-060) — serve fake JWKS to bypass signature verification
- **I**: Biometric high-risk bypass (F-062) — attempt to use BIOMETRIC_WEAK or DEVICE_CREDENTIAL for authenticateHighRisk

| Field | Value |
|-------|-------|
| **TDD Ref** | §2.3.8 Device Integrity (F-060 JWT verify), §2.3.6 Biometric Authentication (F-062 authenticateHighRisk) |
| **Automation** | Auto: Full (JUnit) + Manual |

## 4.16–4.30. Остальные families

Следующие families сохраняют структуру из v4.0, с добавленными TDD ref и Automation:

| Family | Variants | TDD Ref | Automation |
|--------|----------|---------|------------|
| ACC-MULTI | 7 | §2.3.1 Key Hierarchy | Auto: Full + Manual |
| REC-FLOW | 11 | §3.4 SocialRecoveryModule (F-049 cleanupExpiredRecovery) | Auto: Contract (37 tests) + Manual |
| NOTIF-PUSH | 6 | §2.6 Dynamic Island & Push | Auto: Full |
| CHAIN-CROSS | 6 | §4.2 RPC Multi-Provider | Auto: Full + Manual |
| TIME-CLOCK | 6 | §1.4 UserOp Hash (validUntil) | Auto: Full |
| STOR-STATE | 6 | §2.2.3 Data Layer (Room) | Auto: Full |
| TOK-LONGTAIL | 10 | §3.2 MDAOToken | Auto: Contract + Manual |
| HID-HIDDEN | 5 | §2.3 Security, §1.9 Rate Limiting | Auto: Full + Manual |
| INT-INTEGRATION | 9 | §1.2 API, §3.5 NicknameRegistry | Auto: Full |
| DAPP-SIGN | 14 | §2.7 WalletConnect | Auto: Full + Manual |
| HW-LEDGER | 10 | §2.3 Security (hardware) | Manual |
| BRIDGE-CROSS | 10 | — (external) | Auto: Full + Manual |
| NFT-EDGE | 10 | — (future) | Manual |
| USER-PHISHING | 6 | §2.3 Security | Manual |
| USER-OPERATOR | 5 | §2.3.7 Screen-Off Lock | Manual |
| USER-MISTAKE | 4 | §2.5 Send Flow, §2.8 Error Mapping | Manual |

## 4.31. Timelock (TIMELOCK) — 3 variants ⭐ NEW (Wave 13)

Admin-функции через TimelockController (Step 1a внешнего аудита).

| ID | Сценарий | TDD Ref | Automation |
|----|----------|---------|------------|
| TC-TIMELOCK-001 | Admin функция через TimelockController — setMaxGasPrice через schedule+execute | §3.3 MDAOPaymaster (Timelock) | Auto: Contract |
| TC-TIMELOCK-002 | Прямой вызов admin функции не владельцем после передачи ownership в Timelock — expect revert | §3.3 MDAOPaymaster (Timelock) | Auto: Contract |
| TC-TIMELOCK-003 | withdrawTo с дневным лимитом — превышение cap вызывает DailyCapExceeded | §3.3 MDAOPaymaster (withdrawTo) | Auto: Contract |

| Field | Value |
|-------|-------|
| **TDD Ref** | §3.3 MDAOPaymaster (Timelock, withdrawTo, DailyCapExceeded) |
| **Automation** | Auto: Contract (Foundry `MDAOPaymaster.t.sol` — 3 Timelock tests) |
| **Score range** | 0.1 – 2.0 |

## 4.32. Provider Registry (REGISTRY) — 4 variants ⭐ NEW (Wave 13)

TrustProviderRegistry — регистрация и верификация провайдеров (Step 1b внешнего аудита).

| ID | Сценарий | TDD Ref | Automation |
|----|----------|---------|------------|
| TC-REGISTRY-001 | Регистрация провайдера — registerProvider, статус ACTIVE | §3.3 MDAOPaymaster (Registry Integration) | Auto: Contract |
| TC-REGISTRY-002 | Верификация через registry — успешный verify с валидной ECDSA подписью | §3.3, TrustProviderRegistry.sol | Auto: Contract |
| TC-REGISTRY-003 | Статус провайдера DEPRECATED → верификация падает с ProviderNotActive | §3.3, TrustProviderRegistry.sol | Auto: Contract |
| TC-REGISTRY-004 | Registry not set (address(0)) → fallback на trustedSigner (старый flow) | §3.3 MDAOPaymaster (Registry Integration) | Auto: Contract |

| Field | Value |
|-------|-------|
| **TDD Ref** | §3.3 MDAOPaymaster (Registry Integration), TrustProviderRegistry.sol |
| **Automation** | Auto: Contract (Foundry `TrustProviderRegistry.t.sol` — 11 tests + `MDAOPaymaster.t.sol` — 3 integration tests) |
| **Score range** | 0.1 – 1.5 |

---

# 5. Defensive Architecture

16 архитектурных мер (A-P) с TDD linkage:

| # | Мера | TDD Ref | Priority | Квартал |
|---|------|---------|----------|---------|
| **A** | Defense in depth (8 слоёв) | §2.3, §4.5 | P0 | Q3 2026 |
| **B** | Atomic operations (check-effects-interactions) | §3.3, §3.4 | P0 | Q3 2026 |
| **C** | Time-locked recovery (24h) | §3.4 SocialRecoveryModule | P0 | Q3 2026 |
| **D** | Circuit breakers / Emergency pause | §3.3 MDAOPaymaster | P0 | Q3 2026 |
| **E** | Idempotency на всех уровнях | §4.2.1 Request Idempotency | P0 | Q3 2026 |
| **F** | Pre-flight simulation | §1.2.1 /sign, §3.3 | P0 | Q3 2026 |
| **G** | Optimistic UI с rollback | §2.5 Send Flow | P1 | Q4 2026 |
| **H** | Watchtower service | §4.4 Monitoring | P1 | Q4 2026 |
| **I** | Insurance fund (0.5% fee) | §3.3 (new) | P1 | Q4 2026 |
| **J** | Dead man's switch (90 days) | §3.4 | P2 | Q1 2027 |
| **K** | Multi-sig admin (3/5 + 48h) | §4.5 Key Rotation | P0 | Q3 2026 |
| **L** | Exit window for upgrades (7 days) | §3.7 Upgradeability | P1 | Q4 2026 |
| **M** | Pre-signed tx queue | §2.2.3 OfflineSyncWorker | P2 | Q1 2027 |
| **N** | Shamir backup shards (3/2) | §2.3.3 Shamir Secret Sharing | P1 | Q4 2026 |
| **O** | On-chain attestation | §1.6 audit_log | P2 | Q1 2027 |
| **P** | Refund mechanism | §3.3 postOp | P3 | Q2 2027 |

Code snippets для каждой меры — см. v4.0 (раздел 5).

---

# 6. Chaos Testing Protocol — НОВОЕ

> **Принцип:** Каждый Critical сервис должен быть реально убит перед запуском. Не симуляция, а реальный kill в staging окружении.

## 6.1. Chaos Testing Matrix

| Сервис | Что убиваем | Ожидаемое поведение | TDD Ref | Frequency |
|--------|-------------|---------------------|---------|-----------|
| **Redis** | `redis-cli shutdown nosave` | Rate limit fallback to in-memory; replay cache lost, idempotency degrades | §1.9 Rate Limiting | Weekly |
| **PostgreSQL Primary** | `pg_ctl stop -m fast` | Failover to replica < 30s; read-only mode during failover | §1.6 Database, §4.6 | Monthly |
| **PostgreSQL Replica** | `pg_ctl stop` на replica | Reads fallback to primary; latency increase | §1.6, §4.6 | Weekly |
| **RPC Provider (primary)** | Block traffic to rpc1.mdaopay.xyz | Failover to rpc2 < 5s; balance cache stale up to 30s | §4.2 RPC Multi-Provider | Daily (automated) |
| **RPC Provider (all)** | Block all RPC | UI shows "offline"; queued tx preserved; sync on restore | §4.2 | Monthly |
| **WebSocket Relay** | Kill WS server | Real-time updates cease; polling fallback every 30s | §2.6 (if WS) | Weekly |
| **Push Service (FCM)** | Revoke FCM key | Pushes fail; in-app badge still works; silent push cache invalidation lost | §2.6 Dynamic Island & Push | Monthly |
| **Cloud Run Instance** | Force shutdown mid-request | Idempotency key allows retry; no double-charge | §4.1, §4.2.1 | Weekly |
| **Bundler Service** | Kill bundler | UserOp queue preserved; fallback bundler takes over < 60s | §4.2 | Monthly |
| **Paymaster Service** | Kill paymaster | /sign returns 503; client retries with backoff | §1.2.1 | Weekly |
| **Nickname Service** | Kill nickname | Resolution fails; cached names still work 1h | §1.2.2, §1.7 Caching | Monthly |
| **DexScreener API** | Block DexScreener | Price fallback to cache (30 min stale); then CoinGecko | §1.10 Price Oracle | Weekly |
| **DNS Provider** | Simulate DNS outage | Hardcoded IP fallback for critical endpoints | §4.1 | Quarterly |
| **TLS Certificate** | Expire cert (test env) | Cert pinning triggers; app refuses connection | §4.5 | Quarterly |
| **Database Disk Full** | Fill disk to 100% | Writes fail gracefully; reads still work; alert triggered | §4.4 Monitoring | Quarterly |

## 6.2. Chaos Day Protocol

### Раз в месяц — Chaos Day

**Участники:** QA + DevOps + On-call engineer + Observer

**Процесс:**
1. **T-1 day:** Команда выбирает 3 сервиса для kill (random или risk-based)
2. **T+0:** Inject в staging (15:00 UTC, когда команда активна)
3. **T+0 to T+30:** Наблюдение, без вмешательства
4. **T+30:** Если система не восстановилась — manual intervention
5. **T+60:** Debrief

### Метрики chaos day

| Метрика | Цель |
|---------|------|
| Detection time | < 5 min (alert triggers) |
| Recovery time | < 5 min (auto) or < 30 min (manual) |
| User impact | < 1% affected |
| Data loss | 0 |
| False positives | < 10% (alerts that fired but shouldn't) |

### Chaos day checklist

- [ ] 3 сервиса выбраны
- [ ] Runbook для каждого сервиса готов
- [ ] Monitoring dashboard live
- [ ] On-call engineer paged
- [ ] Observer записывает timeline
- [ ] Debrief в течение 24h
- [ ] Postmortem written
- [ ] Action items created

## 6.3. Pre-launch Chaos Requirements

**Перед mainnet launch — ОБЯЗАТЕЛЬНО:**

- [ ] Redis killed — system survives 10 min
- [ ] PostgreSQL primary killed — failover < 30s
- [ ] All RPC providers killed — UI degrades gracefully
- [ ] Cloud Run restart mid-/sign — no double-charge
- [ ] Bundler killed — fallback takes over < 60s
- [ ] Paymaster killed — /sign returns 503, client retries
- [ ] FCM revoked — in-app badge works
- [ ] Disk full — writes fail gracefully, alert triggered

**Если хотя бы один тест не пройден — launch блокируется.**

---

# 7. Smoke Test Suite — НОВОЕ

> 20 сценариев для PR-проверок. Запускаются в CI на каждый PR, < 5 минут.

## 7.1. Smoke Tests (24)

| # | ID | Сценарий | Type | Automation |
|---|-----|----------|------|------------|
| 1 | EX-03 | Paymaster economic death (basic check) | Contract | Foundry |
| 2 | EX-09 | Regulatory kill (geo-fencing works) | E2E | Maestro |
| 3 | SEC-REPLAY-A | UserOp replay protection | Contract | Foundry |
| 4 | SEC-PHISHING-A | Recipient validation | E2E | Maestro |
| 5 | SEC-DOS-A | Paymaster /sign rate limit | API | k6 |
| 6 | SEC-RECOVERY-A | Guardian collusion 2-of-3 blocked | Contract | Foundry |
| 7 | SEC-CONTRACT-F | Reentrancy in validateUserOp | Contract | Foundry |
| 8 | EDGE-INFRA-A | Redis down — system survives | Chaos | Chaos Mesh |
| 9 | EDGE-CHAIN-D | Token decimals (USDT 6 vs DAI 18) | Contract | Foundry |
| 10 | AA-INIT-C | Stuck account (first tx fails init) | Contract | Foundry |
| 11 | PAY-ECONOMICS-A | Paymaster balance < cost | Contract | Foundry |
| 12 | PAY-ECONOMICS-B | Price moves > 20% (TOCTOU) | Contract | Foundry |
| 13 | ILL-INPUT-A | Send 0 USDT blocked | E2E | Maestro |
| 14 | ILL-INPUT-D | Address without 0x — validation | API | Postman |
| 15 | UX-ERROR-B | Double-tap send (idempotency) | API | k6 |
| 16 | ASYNC-RACE-A | 2 tx same nonce — one rejected | Contract | Foundry |
| 17 | COMP-01 (sanctions) | OFAC address blocked | API | Postman |
| 18 | SCALE-TRAFFIC-A | x10 traffic (baseline load) | Load | k6 |
| 19 | FE-MOBILE-A | Onboarding → send → receive (happy path) | E2E | Maestro |
| 20 | OPS-RUNBOOK-B | Kill switch works | Manual | Game day |
| 21 | SEC-CONTRACT-I | DeadManSwitch two-phase trigger (F-072) — challenge within period, claim after period | Contract | Foundry |
| 22 | SEC-DOS-F | Auth login rate limit (F-054) — 6th request in 1 min blocked | API | k6 |
| **23** | **TC-TIMELOCK-001** | **Admin функция через TimelockController — setMaxGasPrice через schedule+execute** | **Contract** | **Foundry** |
| **24** | **TC-REGISTRY-002** | **Верификация через TrustProviderRegistry — активный провайдер, ECDSA подпись** | **Contract** | **Foundry** |

## 7.2. Smoke CI Pipeline

```yaml
# .github/workflows/smoke.yml
name: Smoke Tests
on: [pull_request]

jobs:
  contract-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Foundry tests (smoke subset)
        run: forge test --match-contract "Smoke" -vvv

  api-tests:
    runs-on: ubuntu-latest
    steps:
      - name: Postman smoke
        run: newman run smoke.postman_collection.json

  e2e-tests:
    runs-on: macos-latest
    steps:
      - name: Maestro smoke
        run: maestro test smoke.yaml

  load-tests:
    runs-on: ubuntu-latest
    steps:
      - name: k6 baseline
        run: k6 run smoke-load.js
```

## 7.3. Smoke SLA

| Метрика | Цель |
|---------|------|
| Total duration | < 5 min |
| Pass rate | 100% (или PR не мерджится) |
| False positive rate | < 5% |
| Flaky test threshold | 0 (flaky = disabled + investigated) |

---

# 8. Implementation Roadmap

## Q3 2026 — Pre-launch (P0)

| Задача | Мера | TDD Ref | Smoke? |
|--------|------|---------|--------|
| Atomic ops во все контракты | B | §3.3, §3.4 | ✅ #7 |
| Reentrancy guards | B | §3.3 | ✅ #7 |
| Time-locked recovery (24h) | C | §3.4 | ✅ #6 |
| Pre-flight simulation | F | §1.2.1 | ✅ #11 |
| Idempotency keys на API | E | §4.2.1 | ✅ #15 |
| TLS pinning | A | §4.5 | — |
| Hardware multisig for deployer | K | §4.5 | — |
| Circuit breaker | D | §3.3 | ✅ #20 |
| **Chaos testing setup** | — | §4.4 | ✅ #8 |

**Milestone:** Mainnet launch ready + Chaos tests pass

## Q4 2026 — Hardening (P1)

| Задача | Мера | TDD Ref |
|--------|------|---------|
| Insurance fund + 0.5% fee | I | §3.3 (new) |
| Watchtower service | H | §4.4 |
| Shamir backup (3/2) | N | §2.3.3 |
| Exit window for upgrades (7 days) | L | §3.7 |
| Runbooks для всех P0/EX | — | §4.6 |
| Crisis communication plan | — | — |
| Bug bounty launch ($100k) | — | §5.4 |

**Milestone:** 100k users safe

## Q1 2027 — Maturity (P2)

| Задача | Мера | TDD Ref |
|--------|------|---------|
| Dead man's switch (90 days) | J | §3.4 |
| Multi-sig admin (3/5 + 48h) | K | §4.5 |
| On-chain attestation log | O | §1.6 |
| Game day drills (quarterly) | — | — |
| Legal runbook | — | §1.6 |

**Milestone:** 1M users safe

## Q2 2027 — Polish (P3)

| Задача | Мера | TDD Ref |
|--------|------|---------|
| Refund mechanism | P | §3.3 postOp |
| Offline tx queue с sync | M | §2.2.3 |
| Optimistic UI с rollback | G | §2.5 |
| Public postmortem archive | — | — |
| Developer documentation portal | — | §1.2 |

**Milestone:** Ecosystem mature

---

# 9. Metrics & Dashboards

## Risk Dashboard

```
┌─────────────────────────────────────────────────────┐
│ MDAOPay Risk Dashboard — v5.1                        │
├─────────────────────────────────────────────────────┤
│ Total scenarios: 457 (consolidated)                  │
│ Smoke tests: 24 (CI every PR)                        │
│                                                      │
│ By tier:                                             │
│  EXISTENTIAL:    15  ██░░░░░░░░░░░░  3.4%           │
│  CRITICAL:      113  █████████████  25.7%           │
│  HIGH:          113  █████████████  25.7%           │
│  MEDIUM:         51  ██████░░░░░░░  11.6%           │
│  LOW:            22  ███░░░░░░░░░░   5.0%           │
│  UNCLASSIFIED:   26  ██████░░░░░░░   5.9%           │
│                                                      │
│ By automation:                                       │
│  Auto: Smoke:     22  ████░░░░░░░░  5.0%            │
│  Auto: Full:     184  █████████████ 41.8%           │
│  Auto: Contract: 122  ██████████░░  27.7%           │
│  Manual:         112  █████████░░░  25.5%           │
│                                                      │
│ TDD coverage:                                        │
│  Families with TDD ref: 50/50 (100%)                 │
│  TDD sections referenced: 30/30 (100%)               │
│                                                      │
│ Chaos testing:                                       │
│  Services killed (this month): 3/15                  │
│  Pre-launch chaos passed: 0/8 (PENDING)              │
│                                                      │
│ Findings Wave 11-12:                                 │
│  Documented: 10                                      │
│  CLAIMED_FIXED: 5 (F-048, F-054, F-059, F-060, F-065)│
│                                                      │
│ Wave 13 (Timelock + Registry):                       │
│  New contracts: 3 (ITrustProvider, EcdsaVerifier,    │
│                   TrustProviderRegistry)              │
│  New tests: 14 (11 TrustProviderRegistry + 3         │
│                   MDAOPaymaster integration)          │
│                                                      │
│ Smoke CI:                                            │
│  Pass rate (last 7 days): 98.5%                      │
│  Avg duration: 4 min 12 sec                          │
│  Flaky tests: 0                                      │
└─────────────────────────────────────────────────────┘
```

## Метрики успеха

| Метрика | Цель | Измерение | Frequency |
|---------|------|-----------|-----------|
| Test coverage (contracts) | 95%+ | Foundry coverage | Per PR |
| Test coverage (backend) | 80%+ | JaCoCo | Per PR |
| EX scenarios mitigated | 100% | Risk dashboard | Pre-launch |
| Smoke pass rate | 100% | CI | Per PR |
| Chaos day success | 100% | Game day | Monthly |
| Pre-launch chaos | 8/8 pass | Staging | Pre-launch |
| Insurance fund size | ≥ 1% TVL | On-chain | Daily |
| Mean time to detect | < 5 min | Watchtower | Per incident |
| Mean time to recover | < 24h | Insurance claims | Per incident |
| User funds lost / year | < 0.01% TVL | Insurance payouts | Yearly |
| Bug bounty payouts | $50k+/year | Immunefi | Yearly |
| External audits | 2/year | Trail of Bits, OZ | Yearly |
| TDD ref coverage | 100% families | This doc | Per release |

---

# 10. Appendices

## A. Bug report template

```markdown
**ID:** BUG-XXXX
**Test scenario:** EX-XX / SEC-XX-XX / CRISIS-XX / ...
**Family:** SEC-REPLAY / AA-INIT / ...
**TDD Ref:** §X.X.X (section name)
**Risk score:** I=X, P=X, D=X, R=X, C=X → Score=X.XX
**Priority:** P0-EXISTENTIAL / P0 / P1 / P2 / P3
**Tier:** Existential / Critical / High / Medium / Low
**Automation:** Auto: Smoke / Auto: Full / Auto: Contract / Manual
**Environment:** mainnet-fork / staging / testnet / prod
**Build:** v1.0.0-rc.3

**Steps to reproduce:**
1. ...
2. ...
3. ...

**Expected:** ...
**Actual:** ...

**Test fixtures used:** [see Appendix I]

**Irreversibility assessment:** ...
**Impact assessment:** ...
**Proposed mitigation:** [measure A-P, TDD ref]
**Cost estimate:** X dev-days
```

## B. Pre-release checklist

### Existential (must pass)
- [ ] All 15 EX scenarios — mitigated
- [ ] Hardware multisig for deployer keys — operational (TDD §4.5)
- [ ] Circuit breaker — tested (TDD §3.3)
- [ ] Insurance fund — initialized (≥ $100k)
- [ ] Crisis communication plan — ready
- [ ] Legal opinion (MoFo/Cooley) — obtained
- [ ] 2 external audits — completed (TDD §5.4)

### Critical (must pass)
- [ ] All 111 Critical scenarios — Pass
- [ ] All P0 (score > 1.0) — resolved
- [ ] Smoke-тесты (20) — 100% pass
- [ ] Security audit — completed (TDD §5.4)
- [ ] Bug bash — conducted

### Chaos (must pass) ⭐ NEW
- [ ] Redis killed — system survives 10 min
- [ ] PostgreSQL primary killed — failover < 30s
- [ ] All RPC providers killed — UI degrades gracefully
- [ ] Cloud Run restart mid-/sign — no double-charge
- [ ] Bundler killed — fallback < 60s
- [ ] Paymaster killed — /sign returns 503
- [ ] FCM revoked — in-app badge works
- [ ] Disk full — writes fail gracefully

### High (should pass)
- [ ] All 110 High scenarios — Pass or Accepted Risk
- [ ] All P1 (score 0.1-1.0) — resolved or plan
- [ ] Нагрузочные тесты — без деградации > 20% (TDD §5.5)

### Operational
- [ ] Runbooks для всех P0/EX — ready
- [ ] Game day drill — conducted
- [ ] On-call rotation — established
- [ ] Bug bounty — launched
- [ ] Rollback plan — tested (TDD §6.4)

### Compliance
- [ ] OFAC sanctions screening — operational
- [ ] GDPR right to erasure — implemented
- [ ] MiCA compliance — assessed
- [ ] Tax reporting (1099/K) — ready if required

## C. Crisis communication plan

### Tier 0: Existential incident (project at risk)
- **0-5 min:** War room activated, CEO/CTO paged
- **5-15 min:** Internal acknowledgment, legal counsel notified
- **15-30 min:** Initial public statement (Twitter, blog, Discord)
- **30-60 min:** Detailed timeline + mitigation steps
- **Hourly:** Updates until resolved
- **Post-resolution:** Full postmortem within 48h, community AMA

### Tier 1: Critical incident (funds at risk)
- **0-15 min:** Acknowledge internally, activate war room
- **15-30 min:** Initial public statement (Twitter, Discord)
- **30-60 min:** Detailed update with timeline
- **Hourly:** Updates until resolved
- **Post-resolution:** Full postmortem within 48h

### Tier 2: High incident (service degradation)
- **0-30 min:** Internal acknowledgement
- **30-60 min:** Public status page update
- **Resolution:** Status page update + brief postmortem

### Tier 3: Medium incident (UX issue)
- **0-2h:** Internal triage
- **2h:** Public status page if user-facing
- **Resolution:** Brief note

## D. Runbook template (Existential)

```markdown
# Runbook: EX-XX — [Title]

## Classification
- Tier: EXISTENTIAL
- Risk score: X.XX
- TDD Ref: §X.X.X
- Mitigation: [measure A-P]

## Trigger
- Alert: [metric > threshold, TDD §4.4]
- User report: [symptoms]
- Manual: [when to invoke]

## Impact
- Users affected: [all / N users]
- Funds at risk: [yes/no, estimated $]
- Service: [degraded/down]
- Existential threat: [description]

## Immediate actions (0-5 min)
1. Page CEO, CTO, Legal, Comms lead
2. Activate war room (Zoom link)
3. Assess if real or false positive
4. If real: activate circuit breaker (D)
5. Post initial holding statement

## Short-term (5-30 min)
1. Diagnose root cause
2. Contain (pause contracts, freeze paymaster)
3. Notify exchange partners if relevant
4. Prepare detailed public statement

## Medium-term (30 min - 4h)
1. Implement fix
2. Test on fork
3. Coordinate with auditors
4. Deploy fix
5. Verify resolution

## Resolution
- [Success criteria]
- [Verification steps]
- [Communication: "All clear" + postmortem timeline]

## Postmortem (within 48h)
- Timeline
- Root cause (5 whys)
- What went well / wrong
- Action items (owners, deadlines)
- Update runbook
- Community AMA scheduled
```

## E. Game day drill template

```markdown
# Game Day: EX-XX — [Scenario]

## Participants
- Incident commander: ...
- On-call engineer: ...
- CEO/CTO: ...
- Communications lead: ...
- Legal counsel: ...
- Observer (notes): ...

## Scenario
[Inject description]
Example: "Twitter reports MDAOPay hacked. 500k USDT drained.
Even though it's a false alarm, price drops 40% in 1h."

## Timeline
- T+0: Inject delivered
- T+5: War room assembled
- T+15: Diagnosis (real or false?)
- T+30: Public statement
- T+60: Price stabilization
- T+120: Resolution + postmortem

## Evaluation criteria
- Detection time: < 5 min
- War room assembly: < 15 min
- Public statement: < 30 min
- Technical resolution: < 60 min
- Communication quality: [1-5]
- User impact: [minimal/moderate/severe]

## Debrief (within 24h)
- What went well
- What went wrong
- Action items
- Runbook updates
```

## F. Bug bounty scope

| Scope | Reward range | TDD Ref | Examples |
|-------|--------------|---------|----------|
| Existential | $50k–$250k | EX-01..15 | All EX-* |
| Smart contracts | $10k–$100k | §3.2, §3.3, §3.4, §3.5 | SEC-CONTRACT, AA-* |
| Critical infrastructure | $5k–$50k | §4.1, §4.2 | EDGE-INFRA, paymaster |
| Client (mobile/web) | $1k–$25k | §2.1, §2.2 | FE-*, MOB-* |
| Protocol design | $5k–$50k | §3.4, §3.1 | REC-*, AA-*, ZK-* |
| User protection | $500–$5k | §2.3 | USER-*, UX-* |
| Economic exploits | $5k–$50k | §3.2, §3.3 | ECON-*, PAY-* |

## G. TDD Cross-Reference Matrix

Полная карта: Family → TDD Section → Test File (TDD §5.1)

| Family | TDD Section | Test File | Tests |
|--------|-------------|-----------|-------|
| SEC-REPLAY | §1.4, §3.3 | `MDAOPaymaster.t.sol` | 20 |
| SEC-RECOVERY | §3.4 | `SocialRecoveryModule.t.sol` | 30 |
| SEC-CONTRACT | §3.3, §3.6, §3.7 | `MDAOPaymaster.t.sol`, `DeadManSwitch.t.sol` (F-072, F-048), `InsuranceFund.t.sol` (F-052) | 20+29+15 |
| SEC-KEYS (P256) | §3.4 | `SocialRecoveryModule.t.sol` | 30 |
| ILL-GUARDIAN | §3.4 | `SocialRecoveryModule.t.sol` | 30 |
| ILL-IMPORT | §2.3.3 | `ShamirSecretSharingTest.kt` | 17 |
| ILL-IMPORT | §2.3.3 | `RecoveryShareManagerTest.kt` | 10 |
| PAY-ECONOMICS | §3.3 | `MDAOPaymaster.t.sol` | 20 |
| PROD-GOVERNANCE | §3.5 | `NicknameRegistry.t.sol` | 9 |
| ASYNC-OFFLINE | §2.2.3 | `OfflineSyncWorker` integration | — |
| EDGE-MIGRATION | §2.2.3 | Room MigrationTestHelper | — |
| ASYNC-RACE | §4.2.1 | Sign idempotency integration | — |
| SCALE-* | §5.5 | k6 load tests | — |
| SEC-DOS-AUTH | §1.2.1 (F-054) | Auth rate limiter integration | — |
| SEC-PHISHING-BRIDGE | §2.2.2 (F-059) | `EthereumProviderInjectorTest.kt` | — |
| FE-DEVICE-INTEGRITY | §2.3.8 (F-060) | `DeviceIntegrityManagerTest.kt` | — |
| FE-DEVICE-BIOMETRIC | §2.3.6 (F-062) | `BiometricManagerTest.kt` | — |
| TIMELOCK-* | §3.3 (Timelock) | `MDAOPaymaster.t.sol` | 3 |
| REGISTRY-* | §3.3 (Registry), TrustProviderRegistry.sol | `TrustProviderRegistry.t.sol`, `MDAOPaymaster.t.sol` | 11 + 3 |

## H. Эволюция документа

| Версия | Сценариев | Покрытие | Ключевое |
|--------|-----------|----------|----------|
| v1.0 | 53 | ~60% | Базовый QA |
| v2.0 | 466 | ~95% | + Web3 экзотика |
| v3.0 | 556 | ~99% | + product/scale/economic/panic |
| v4.0 | 432 | ~99% | + Existential + Irreversibility + consolidation |
| **v5.0** | **432 + 20 smoke** | **~99%** | **+ TDD linkage + Automation matrix + Test fixtures + Chaos + Smoke** |
| **v5.1** | **440 + 22 smoke** | **~99%** | **+ Wave 11-12 findings (F-048–F-065): DeadManSwitch two-phase, auth rate limits, WebView origin, Play Integrity JWT, biometric high-risk** |
| **v5.2** | **457 + 24 smoke** | **~99%** | **+ Wave 13 (TimelockController + TrustProviderRegistry): 7 new TC-* scenarios, 14 new Foundry tests, 3 new contracts** |

### Что изменилось в v5.0

| Изменение | Зачем |
|-----------|-------|
| **+ TDD Ref для каждой family** | QA связан с инженерией |
| **+ Automation matrix** | Ясно что автоматизировать, что руками |
| **+ SEC-OTHER split** (12 → 4 families) | SEC-SOCIAL, SEC-WEB, SEC-CRYPTO, SEC-JWT |
| **+ Test Fixtures (Appendix I)** | Конкретные адреса, ключи, балансы |
| **+ Chaos Testing Protocol** | Реальный kill сервисов перед launch |
| **+ Smoke Test Suite (20)** | Для CI на каждый PR |
| **+ TDD Cross-Reference Matrix** | Полная карта Family → TDD → Test file |

### Что изменилось в v5.1

| Изменение | Зачем |
|-----------|-------|
| **+ Wave 11-12 findings (10)** | F-048–F-065 documented in TDD |
| **+ DeadManSwitch two-phase trigger tests (F-072)** | 5 new Foundry tests (24→29) |
| **+ Auth rate limit scenarios (F-054)** | login 5/min, register 3/min, refresh 10/min |
| **+ WebView bridge security tests (F-059)** | Origin validation, domain whitelist, bridge cleanup |
| **+ Play Integrity JWT verification tests (F-060)** | SHA256withRSA, JWKS cache, nonce/timestamp/pkg |
| **+ Biometric high-risk tests (F-062)** | BIOMETRIC_STRONG only, no PIN fallback |
| **+ SEC-CONTRACT variants I–K** | DeadManSwitch, InsuranceFund unverified sigs |
| **+ SEC-DOS variants F–G** | Auth brute-force, register spam |
| **+ SEC-NETWORK variant I** | X-Forwarded-For spoofing |
| **+ SEC-PHISHING variants F–G** | Bridge origin spoofing, navigation leak |
| **+ FE-DEVICE variants G–I** | JWT forgery, JWKS cache poison, biometric bypass |
| **+ Smoke tests 21–22** | DeadManSwitch two-phase trigger, auth rate limit |
| **+ CLAIMED_FIXED table (5 findings)** | F-048, F-054, F-059, F-060, F-065 |

### Что изменилось в v5.2

| Изменение | Зачем |
|-----------|-------|
| **+ Wave 13 (Timelock + Registry)** | Step 1a + 1b внешнего аудита |
| **+ TIMELOCK family (3 variants)** | TC-TIMELOCK-001..003 — TimelockController admin, direct call revert, withdrawTo daily cap |
| **+ REGISTRY family (4 variants)** | TC-REGISTRY-001..004 — регистрация, верификация, DEPRECATED, fallback |
| **+ 14 новых Foundry тестов** | 11 TrustProviderRegistry.t.sol + 3 интеграционных MDAOPaymaster.t.sol |
| **+ ITrustProvider, EcdsaVerifier, TrustProviderRegistry** | 3 новых контракта (Step 1b) |
| **+ TDD v2.7** | Обновлён MDAOPaymaster §3.3, тестовые таблицы, coverage baseline |
| **+ Smoke tests 23–24** | Timelock admin function, Registry verification |

## I. Test Fixtures — НОВОЕ

### I.1. Test Wallets (Sepolia testnet)

| Name | Address | Purpose | Private Key (test only!) |
|------|---------|---------|--------------------------|
| Alice | `0x4F2E1234...8B9C` | Recipient (default) | `0xabc123...` (stored in `.env.test`) |
| Bob | `0x7D1A5678...3E5F` | Recipient (alternate) | `0xdef456...` |
| Carl | `0x9C8B90AB...2A4D` | Guardian #1 | `0xghi789...` |
| Diana | `0x1B2C3456...7D8E` | Guardian #2 | `0xjkl012...` |
| Eve | `0x3E5F6789...9A1B` | Guardian #3 | `0xmnop345...` |
| Faucet | `0xFAUCET...` | Test USDT source | `0xtest...` |

**Note:** All keys stored in `.env.test`, never committed to git. CI injects via GitHub Secrets.

### I.2. Test Tokens (Sepolia)

| Token | Address | Decimals | Faucet Amount |
|-------|---------|----------|---------------|
| USDT (test) | `0x...` | 6 | 10,000 USDT |
| USDC (test) | `0x...` | 6 | 10,000 USDC |
| MDAO (test) | `0x...` | 18 | 100,000 MDAO |
| DAI (test) | `0x...` | 18 | 10,000 DAI |
| WETH (test) | `0x...` | 18 | 10 WETH |

### I.3. Test Seed Phrases

| Purpose | Seed (12 words) | Use |
|---------|-----------------|-----|
| Valid #1 | `apple forest mountain river ocean desert cloud stone crystal ember whisper horizon` | ILL-IMPORT-A |
| Valid #2 | `test test test test test test test test test test test junk` | Standard test |
| Invalid checksum | `Apple Forest Mountain River Ocean Desert Cloud Stone Crystal Ember Whisper Horizon` | ILL-IMPORT-A (case) |
| Wrong word count (13) | `apple forest mountain river ocean desert cloud stone crystal ember whisper horizon extra` | ILL-IMPORT-D |
| 24-word valid | `abandon abandon abandon ... abandon about` (24x) | ILL-IMPORT-D |

### I.4. Test Addresses

| Type | Address | Use |
|------|---------|-----|
| EOA (valid) | `0x4F2E...8B9C` | ILL-INPUT-D |
| EOA (no 0x) | `4F2E...8B9C` | ILL-INPUT-D |
| EOA (wrong checksum) | `0x4f2e...8b9c` (all lowercase) | ILL-INPUT-E |
| Smart contract (no fallback) | `0x...` | ILL-RECIPIENT-A |
| Burn address | `0x0000000000000000000000000000000000000000` | ILL-RECIPIENT-D |
| Burn address (dead) | `0x000000000000000000000000000000000000dead` | ILL-RECIPIENT-D |
| Sanctioned (OFAC) | `0x...` (from OFAC SDN list) | COMP-01 |
| Exchange deposit | `0x...` (Binance) | USER-MISTAKE-B |

### I.5. Test RPC Endpoints

| Chain | Primary | Fallback | Chain ID |
|-------|---------|----------|----------|
| Sepolia | `https://rpc.sepolia.mdaopay.xyz` | `https://sepolia.infura.io/v3/...` | 11155111 |
| BSC Testnet | `https://rpc.bsc-testnet.mdaopay.xyz` | `https://data-seed-prebsc-1...` | 97 |
| Local (Anvil) | `http://localhost:8545` | — | 31337 |

### I.6. Test Contracts (Sepolia)

| Contract | Address | TDD Ref |
|----------|---------|---------|
| EntryPoint | `0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789` | §3.1 |
| MDAOToken | `0x...` | §3.2 |
| MDAOPaymaster | `0x...` | §3.3 |
| SocialRecoveryModule | `0x...` | §3.4 |
| NicknameRegistry | `0x...` | §3.5 |

### I.7. Test Data for Edge Cases

| Scenario | Test Data |
|----------|-----------|
| EDGE-08 (decimals) | USDT (6 dec): `1000000` = 1 USDT; DAI (18 dec): `1000000000000000000` = 1 DAI |
| EDGE-09 (price crash) | Mock DexScreener: MDAO = $0.001 (vs real $0.366) |
| MONEY-01 (exact balance) | Alice balance: 1 250.50 USDT; send: 1 250.50 USDT; gas: 0.1 USDT |
| SCALE-01 (x100 load) | Baseline: 50 RPS; spike: 5000 RPS for 60s |
| TIME-01 (clock skew) | Server: T; Client: T+90s (outside 60s threshold) |

### I.8. Anvil Fork Commands

```bash
# Fork Sepolia for contract tests
anvil --fork-url https://rpc.sepolia.mdaopay.xyz \
      --fork-block-number 5000000 \
      --port 8545

# Impersonate Alice for tests
cast rpc anvil_impersonateAccount 0x4F2E...8B9C

# Fund Alice with ETH
cast rpc anvil_setBalance 0x4F2E...8B9C 0xDE0B6B3A7640000
```

## J. Финальная статистика

| Категория | Количество |
|-----------|------------|
| Тестовые сценарии | 457 (consolidated) |
| Smoke тесты | 24 (CI every PR) |
| Scenario families | 52 |
| Existential scenarios | 15 |
| Архитектурных мер | 16 (A-P) |
| Chaos testing services | 15 |
| Pre-launch chaos tests | 8 (must pass) |
| TDD sections referenced | 32 |
| Test fixtures | 8 categories |
| Findings documented (Wave 11-12) | 10 (F-048, F-049, F-050, F-051, F-052, F-054, F-059, F-060, F-062, F-065) |
| CLAIMED_FIXED | 5 (F-048, F-054, F-059, F-060, F-065) |
| **Wave 13 new contracts** | **3 (ITrustProvider, EcdsaVerifier, TrustProviderRegistry)** |
| **Wave 13 new tests** | **14 (11 + 3 integration)** |
| **Wave 13 new scenarios** | **7 (TC-TIMELOCK-001..003, TC-REGISTRY-001..004)** |
| Метрик успеха | 13 |
| Roadmap кварталов | 4 |
| Bug bounty tiers | 7 |
| Crisis communication tiers | 4 |

---

*MDAOPay Test Scenarios v5.2 (Final, TDD-linked, Wave 11-13) · 457 + 24 smoke · Июнь 2026*

*Связанные документы:*
- *TDD v2.7 (`TDD.md`) — Engineering Specification*
- *Design Bible v1.0.0 (`design-bible.md`)*
- *HTML Prototype (`index.html`) — 26 экранов*
- *`security/FINDINGS.md` — F-048, F-049, F-050, F-051, F-052, F-054, F-059, F-060, F-062, F-065*

*Ключевые улучшения v5.0:*
1. *TDD linkage — каждая family ссылается на TDD section*
2. *Automation matrix — Auto: Smoke / Full / Contract / Manual*
3. *SEC-OTHER split — 4 осмысленных families вместо сборной солянки*
4. *Test Fixtures — конкретные адреса, ключи, балансы (Appendix I)*
5. *Chaos Testing Protocol — реальный kill сервисов перед launch*
6. *Smoke Test Suite — 20 сценариев для CI на каждый PR*
7. *TDD Cross-Reference Matrix — полная карта Family → TDD → Test file*

*Ключевые улучшения v5.2 (Wave 13):*
8. *TIMELOCK family — 3 сценария для TimelockController (Step 1a)*
9. *REGISTRY family — 4 сценария для TrustProviderRegistry (Step 1b)*
10. *14 новых Foundry тестов, 3 новых контракта*
