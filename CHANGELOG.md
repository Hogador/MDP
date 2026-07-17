# Changelog — MDAOPay

> Автоматически сформирован по git-истории. Даты соответствуют коммитам.

---

## [2026-07-16] — Swarm Infrastructure
- chore: swarm config fixes + audit report + model routing
- chore: sync hive

## [2026-07-07] — Features & Security Fixes
- **P1d**: biometric 30s window + fix 2 flaky tests + ROADMAP v1.1
- **F-115**: vote repository + UI wiring (+ fix MainScreen.kt brackets)
- **I-103**: Backup/restore scripts (pg_dump)
- **S-120**: CI/CD pipeline — test.yml + deploy-testnet.yml
- **F-112**: HistoryViewModel wired to EtherscanRepository for remote tx sync
- **F-110**: CHAIN_ID per-flavor (97 testnet, 56 mainnet)
- **F-111**: BackupScreen wired to real mnemonic + navigation route
- **F-113**: ProposalScreen with EventIndexer-driven read-only MVP + DAO tab
- **S-01**: InsuranceFund multi-sig fix — n-of-n -> 2-of-3 with requiredAuditors
- **S-05**: MDAOSmartAccount — zero-address guard + event on setRecoveryCaller
- **A-06**: MDAOPaymaster — remove nonReentrant from postOp (blocks testnet)
- **U-01**: SendScreen — replace hardcoded balance with real wallet balance
- **S-09 + P-01/P-02/A-01/U-04**: fix all unit tests
- Wave 16: close F-026/F-059/F-062 + update FINDINGS-INDEX

## [2026-07-03] — Swarm v10–v11
- feat(swarm): v11 — ZCode planning + strict rules (transparency, no code edits, roadmap updates)
- feat(swarm): enforce logging (start+end) + 5 Claude Library patterns
- feat(swarm): doctor v2 — provider tests, symlink checks, coordinator function
- feat(swarm): v10 step 3a — Coordinator rewrite + stats infrastructure
- feat(swarm): v10 step 3b — 5 core agents get identity + structured output
- feat(swarm): v10 step 3c — 5 QA agents get identity + structured output
- feat(swarm): v10 step 4 — native opencode fallback chains + Cloudflare provider
- feat(swarm): v10 step 5 — Ponytail pre-check + MDAOPay patterns
- fix(swarm): multiple model routing and config fixes
- perf(swarm): budget mode — Cloudflare out of primary, SambaNova/Groq/Mistral in
- feat: add verify-findings.sh — pre-deploy gate script

## [2026-07-02] — Audit & Cleanup
- chore: wave 16 — all external audit findings fixed
- chore: wave 15 — audit cleanup, all findings CLAIMED_FIXED
- feat: wave 16 — Phase 0 complete (all 6 blockers)

## [2026-07-01] — Wave 1 Security Fixes
- fix: wave 1 — F-049, F-110, F-111, F-022, F-131

## [2026-06-30] — Initial Setup & External Audit
- Initial commit
- fix: external audit findings D-1/F-129 (PaymasterSigner), N-5 (guardian paymaster), N-7 (RecoveryUserOpBuilder)
- fix: integrate external mobile audit (Claude 30.06.2026) — 12 findings
