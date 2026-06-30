#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

mkdir -p docs/orchestration

opencode run --print-logs "Coordinate full security audit of MDAOPay project.

BEFORE STARTING:
- Read security/FINDINGS.md, security/ERRORS-MEMORY.md, security/ANTI-PATTERNS.md
- Read security/FIX-PATTERNS.md for approved solutions

EXECUTE 18 AUDITS in 6 phases using: opencode run --agent <role> \"\$(cat scripts/prompts/NN-name.md)\"
Phase 1 (parallel): 01-secrets, 02-deps, 03-config (researcher-infra)
Phase 2 (parallel): 04-crypto, 05-blockchain, 06-contracts, 07-input, 08-auth, 09-mobile
Phase 3 (parallel): 10-network, 11-dos, 12-mev, 13-upgrade, 14-logging
Phase 4 (parallel): 15-chaos, 16-perf, 17-formal, 18-compliance, 19-coverage
Phase 5 (sequential): 20-verify (verifier)
Phase 6: 21-consolidate (librarian)

FOR EACH FINDING:
- Compute fingerprint (sha256 of category:file:function:pattern)
- Check duplicates in security/FINDINGS.md
- If new: assign F-XXX, severity per §7 policy
- If duplicate: reference existing ID, don't re-report

OUTPUT:
- docs/orchestration/inbox-<area>.md for each phase
- Update security/FINDINGS.md (lifecycle, dashboard, changelog)
- Update security/ERRORS-MEMORY.md if new patterns

DO NOT OPTIMIZE CODE. Only audit, propose fixes, verify claims.

Start with Phase 1 — run 'opencode run --agent researcher-infra \"\$(cat scripts/prompts/01-secrets.md)\"' first.
" 2>&1 | tee docs/orchestration/audit-simple.log
