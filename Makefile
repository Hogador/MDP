# MDAOPay Security & Swarm targets — add to existing Makefile

.PHONY: audit audit-full audit-simple audit-contracts audit-backend audit-relay \
        audit-infra audit-mobile findings-verify findings-stats hive-sync \
        swarm-config

## Full security audit (all 18 phases, ~50 min)
audit: audit-full

## Full security audit
audit-full:
	@./scripts/spawn-full-audit.sh

## Simple one-command audit
audit-simple:
	@./scripts/audit-simple.sh

## Security audit (contracts only)
audit-contracts:
	@opencode run "researcher-contracts: $(cat scripts/prompts/06-contracts.md)"

## Security audit (backend only)
audit-backend:
	@opencode run "researcher-backend: $(cat scripts/prompts/05-blockchain.md)"

## Security audit (relay only)
audit-relay:
	@opencode run "researcher-relay: scan relay/ for security issues per security/FINDINGS.md"

## Security audit (infra only)
audit-infra:
	@opencode run "researcher-infra: $(cat scripts/prompts/03-config.md)"

## Security audit (mobile only)
audit-mobile:
	@opencode run "researcher-mobile: $(cat scripts/prompts/09-mobile.md)"

## Verify all CLAIMED_FIXED findings
findings-verify:
	@python3 scripts/verify-findings.py

## Show FINDINGS.md dashboard
findings-stats:
	@echo "=== MDAOPay Security Findings Dashboard ==="
	@echo "OPEN:          $$(grep -c '| OPEN |' security/FINDINGS.md 2>/dev/null || echo 0)"
	@echo "CLAIMED_FIXED: $$(grep -c '| CLAIMED_FIXED |' security/FINDINGS.md 2>/dev/null || echo 0)"
	@echo "VERIFIED:      $$(grep -c '| VERIFIED |' security/FINDINGS.md 2>/dev/null || echo 0)"
	@echo "REGRESSED:     $$(grep -c '| REGRESSED |' security/FINDINGS.md 2>/dev/null || echo 0)"
	@echo "CONFLICT:      $$(grep -c '| CONFLICT |' security/FINDINGS.md 2>/dev/null || echo 0)"
	@echo ""
	@echo "Errors patterns in ERRORS-MEMORY.md: $$(grep -c '^### EM-' security/ERRORS-MEMORY.md 2>/dev/null || echo 0)"
	@echo "Anti-patterns: $$(grep -c '^## AP-' security/ANTI-PATTERNS.md 2>/dev/null || echo 0)"
	@echo "Fix patterns: $$(grep -c '^## FP-' security/FIX-PATTERNS.md 2>/dev/null || echo 0)"

## Sync hivemind
hive-sync:
	@opencode run "hivemind_sync" 2>/dev/null || true
	@opencode run "hivemind_find({ query: 'security audit MDAOPay', limit: 5 })" 2>/dev/null || true

## Show swarm configuration
swarm-config:
	@echo "=== Swarm Configuration ==="
	@cat .opencode/opencode-swarm.json | jq -r '.agents | to_entries[] | "\(.key): \(.value.model)"' 2>/dev/null || cat .opencode/opencode-swarm.json
	@echo ""
	@echo "Agent definitions:"
	@ls .opencode/agents/*.md 2>/dev/null | xargs -n1 basename 2>/dev/null || echo "  (none)"

## Help
help-audit:
	@echo "MDAOPay Security Audit Commands:"
	@echo "  make audit              - Full audit (18 phases, ~50 min)"
	@echo "  make audit-simple       - One-command audit"
	@echo "  make audit-contracts    - Contracts only"
	@echo "  make audit-backend      - Backend only"
	@echo "  make audit-relay        - Relay only"
	@echo "  make audit-infra        - Infra only"
	@echo "  make audit-mobile       - Mobile only"
	@echo "  make findings-verify    - Verify CLAIMED_FIXED findings"
	@echo "  make findings-stats     - Show dashboard"
	@echo "  make hive-sync          - Sync hivemind"
	@echo "  make swarm-config       - Show swarm configuration"

## Sync security docs to GitHub (swarm stays in .gitignore)
sync:
    @git add security/ docs/orchestration/ TDD/ PRD/ AGENTS.md
    @git status --short
    @echo ""
    @read -p "Commit message: " msg && git commit -m "$$msg"
    @git push origin master
    @echo "✅ Synced to GitHub"

## Quick sync (auto message)
sync-quick:
    @git add security/ docs/orchestration/ TDD/ PRD/ AGENTS.md
    @git commit -m "sync: security docs + indexes update $$(date +%Y-%m-%d_%H:%M)"
    @git push origin master
    @echo "✅ Quick synced"
