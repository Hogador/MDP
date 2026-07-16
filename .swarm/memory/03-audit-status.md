# MDAOPay — Audit Status Memory

## Previous Audits
- Wave 16 completed 2026-07-07 — all findings resolved, 1 OPEN, 0 REGRESSED
- Previous audits by GLM and Claude (docs/audit/audit_20260603_GLMandCLAUDE.md)
- Full audit v3 exists (docs/audit/MDAOPay_full_audit_v3.md)

## Current Audit (2026-07-13)
Full comprehensive audit requested by user. Planned waves:
- Wave 1: Security audit (OWASP, auth, payments) → @security_auditor — MODEL FAILED, needs retry
- Wave 2: Smart contract audit → @smart_contract_auditor — COMPLETED but empty result
- Wave 3: Crypto review → @crypto_reviewer — COMPLETED but empty result
- Wave 4: Penetration testing → @penetration_tester — PENDING
- Wave 5: Code quality & deps → @reviewer — PENDING
- Wave 6: Performance & DoS → @performance_engineer — PENDING
- Wave 7: Financial fraud vectors → @security_auditor (second pass) — PENDING

## Issue: Model Config
- Changed omniroute/auto/* to auto/* (removed omniroute/ prefix)
- Added auto/best-chat, auto/best-coding, auto/best-reasoning to omniroute provider model registry
- User needs to reload opencode for changes to take effect
- security_auditor failed with "Model not found: auto/best-chat" before fix

## Infrastructure Notes
- OMNIROUTE_API_KEY set in ~/.config/opencode/.env (sk-53d5d...)
- Omniroute proxy on localhost:3000, currently routes to DeepSeek-V3.2
- Local models (qwen2.5-coder-7b:8080, qwythos-9b:8081) NOT running (by user request)
- opencode-swarm-plugin NOT installed (hive/hivemind unavailable)
