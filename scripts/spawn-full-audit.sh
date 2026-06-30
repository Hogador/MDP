#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════
# MDAOPay — Full Security Audit (all 18 phases)
# Estimated time: ~50 minutes
# Token cost: ~150k (all free tier models)
# ═══════════════════════════════════════════════════════════
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}╔══════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  MDAOPay Full Security Audit — 18 phases         ║${NC}"
echo -e "${BLUE}║  Estimated: ~50 min, ~150k tokens (free tier)    ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════╝${NC}"
echo ""

# Prerequisites check
echo -e "${YELLOW}[0/6] Prerequisites...${NC}"
[ -f security/FINDINGS.md ] || { echo -e "${RED}❌ security/FINDINGS.md not found${NC}"; exit 1; }
[ -f security/ERRORS-MEMORY.md ] || { echo -e "${RED}❌ security/ERRORS-MEMORY.md not found${NC}"; exit 1; }
[ -f security/ANTI-PATTERNS.md ] || { echo -e "${RED}❌ security/ANTI-PATTERNS.md not found${NC}"; exit 1; }
[ -f security/FIX-PATTERNS.md ] || { echo -e "${RED}❌ security/FIX-PATTERNS.md not found${NC}"; exit 1; }
command -v opencode >/dev/null || { echo -e "${RED}❌ opencode not installed${NC}"; exit 1; }
echo -e "${GREEN}✅ Prerequisites OK${NC}"

# Pre-audit dashboard
echo ""
echo -e "${YELLOW}Pre-audit FINDINGS.md dashboard:${NC}"
echo "  OPEN:          $(grep -c '| OPEN |' security/FINDINGS.md 2>/dev/null || echo 0)"
echo "  CLAIMED_FIXED: $(grep -c '| CLAIMED_FIXED |' security/FINDINGS.md 2>/dev/null || echo 0)"
echo "  VERIFIED:      $(grep -c '| VERIFIED |' security/FINDINGS.md 2>/dev/null || echo 0)"
echo "  REGRESSED:     $(grep -c '| REGRESSED |' security/FINDINGS.md 2>/dev/null || echo 0)"
echo "  CONFLICT:      $(grep -c '| CONFLICT |' security/FINDINGS.md 2>/dev/null || echo 0)"

# Hivemind sync
echo ""
echo -e "${YELLOW}Syncing hivemind...${NC}"
opencode run "hivemind_sync" 2>/dev/null || true
opencode run "hivemind_find({ query: 'security audit MDAOPay patterns', limit: 10 })" 2>/dev/null || true
echo -e "${GREEN}✅ Hivemind synced${NC}"

# ─── PHASE 1: FOUNDATION (parallel) ──────────────────────
echo ""
echo -e "${BLUE}═══ PHASE 1: FOUNDATION (parallel) ═══${NC}"

echo -e "${YELLOW}[1A] Secrets scan (researcher-infra)...${NC}"
opencode run --agent researcher-infra " $(cat scripts/prompts/01-secrets.md)" &
PID1A=$!

echo -e "${YELLOW}[1B] Dependencies scan (researcher-infra)...${NC}"
opencode run --agent researcher-infra " $(cat scripts/prompts/02-deps.md)" &
PID1B=$!

echo -e "${YELLOW}[1C] Configuration audit (researcher-infra)...${NC}"
opencode run --agent researcher-infra " $(cat scripts/prompts/03-config.md)" &
PID1C=$!

wait $PID1A $PID1B $PID1C
echo -e "${GREEN}✅ Phase 1 complete${NC}"

# ─── PHASE 2: CODE-LEVEL (parallel) ──────────────────────
echo ""
echo -e "${BLUE}═══ PHASE 2: CODE-LEVEL (parallel) ═══${NC}"

echo -e "${YELLOW}[2A] Cryptography (researcher-contracts)...${NC}"
opencode run --agent researcher-contracts " $(cat scripts/prompts/04-crypto.md)" &
PID2A=$!

echo -e "${YELLOW}[2B] Blockchain logic (researcher-backend)...${NC}"
opencode run --agent researcher-backend " $(cat scripts/prompts/05-blockchain.md)" &
PID2B=$!

echo -e "${YELLOW}[2C] Smart contracts (researcher-contracts)...${NC}"
opencode run --agent researcher-contracts " $(cat scripts/prompts/06-contracts.md)" &
PID2C=$!

echo -e "${YELLOW}[2D] Input validation (researcher-backend)...${NC}"
opencode run --agent researcher-backend " $(cat scripts/prompts/07-input.md)" &
PID2D=$!

echo -e "${YELLOW}[2E] Auth & authorization (researcher-backend)...${NC}"
opencode run --agent researcher-backend " $(cat scripts/prompts/08-auth.md)" &
PID2E=$!

echo -e "${YELLOW}[2F] Mobile-specific (researcher-mobile)...${NC}"
opencode run --agent researcher-mobile " $(cat scripts/prompts/09-mobile.md)" &
PID2F=$!

wait $PID2A $PID2B $PID2C $PID2D $PID2E $PID2F
echo -e "${GREEN}✅ Phase 2 complete${NC}"

# ─── PHASE 3: SYSTEM-LEVEL (parallel) ────────────────────
echo ""
echo -e "${BLUE}═══ PHASE 3: SYSTEM-LEVEL (parallel) ═══${NC}"

echo -e "${YELLOW}[3A] Network/TLS (researcher-infra)...${NC}"
opencode run --agent researcher-infra " $(cat scripts/prompts/10-network.md)" &
PID3A=$!

echo -e "${YELLOW}[3B] Rate limiting & DoS (researcher-backend)...${NC}"
opencode run --agent researcher-backend " $(cat scripts/prompts/11-dos.md)" &
PID3B=$!

echo -e "${YELLOW}[3C] Gas/MEV (researcher-contracts)...${NC}"
opencode run --agent researcher-contracts " $(cat scripts/prompts/12-mev.md)" &
PID3C=$!

echo -e "${YELLOW}[3D] Upgradeability (researcher-contracts)...${NC}"
opencode run --agent researcher-contracts " $(cat scripts/prompts/13-upgrade.md)" &
PID3D=$!

echo -e "${YELLOW}[3E] Logging/PII (researcher-backend)...${NC}"
opencode run --agent researcher-backend " $(cat scripts/prompts/14-logging.md)" &
PID3E=$!

wait $PID3A $PID3B $PID3C $PID3D $PID3E
echo -e "${GREEN}✅ Phase 3 complete${NC}"

# ─── PHASE 4: SPECIALIZED (parallel) ─────────────────────
echo ""
echo -e "${BLUE}═══ PHASE 4: SPECIALIZED (parallel) ═══${NC}"

echo -e "${YELLOW}[4A] Chaos engineering (researcher-infra)...${NC}"
opencode run --agent researcher-infra " $(cat scripts/prompts/15-chaos.md)" &
PID4A=$!

echo -e "${YELLOW}[4B] Performance & load (researcher-backend)...${NC}"
opencode run --agent researcher-backend " $(cat scripts/prompts/16-perf.md)" &
PID4B=$!

echo -e "${YELLOW}[4C] Formal verification (researcher-contracts)...${NC}"
opencode run --agent researcher-contracts " $(cat scripts/prompts/17-formal.md)" &
PID4C=$!

echo -e "${YELLOW}[4D] Compliance (researcher-infra)...${NC}"
opencode run --agent researcher-infra " $(cat scripts/prompts/18-compliance.md)" &
PID4D=$!

echo -e "${YELLOW}[4E] Test coverage gaps (tester)...${NC}"
opencode run --agent tester " $(cat scripts/prompts/19-coverage.md)" &
PID4E=$!

wait $PID4A $PID4B $PID4C $PID4D $PID4E
echo -e "${GREEN}✅ Phase 4 complete${NC}"

# ─── PHASE 5: VERIFICATION (sequential) ──────────────────
echo ""
echo -e "${BLUE}═══ PHASE 5: VERIFICATION ═══${NC}"
echo -e "${YELLOW}[5] Verifier running all recipes (nemotron-3-ultra-550b)...${NC}"
opencode run --agent verifier " $(cat scripts/prompts/20-verify.md)"
echo -e "${GREEN}✅ Phase 5 complete${NC}"

# ─── PHASE 6: CONSOLIDATION ──────────────────────────────
echo ""
echo -e "${BLUE}═══ PHASE 6: CONSOLIDATION ═══${NC}"
echo -e "${YELLOW}[6] Librarian updating memory files...${NC}"
opencode run --agent librarian " $(cat scripts/prompts/21-consolidate.md)"
echo -e "${GREEN}✅ Phase 6 complete${NC}"

# ─── FINAL DASHBOARD ─────────────────────────────────────
echo ""
echo -e "${BLUE}╔══════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  AUDIT COMPLETE                                  ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${YELLOW}Post-audit FINDINGS.md dashboard:${NC}"
echo "  OPEN:          $(grep -c '| OPEN |' security/FINDINGS.md 2>/dev/null || echo 0)"
echo "  CLAIMED_FIXED: $(grep -c '| CLAIMED_FIXED |' security/FINDINGS.md 2>/dev/null || echo 0)"
echo "  VERIFIED:      $(grep -c '| VERIFIED |' security/FINDINGS.md 2>/dev/null || echo 0)"
echo "  REGRESSED:     $(grep -c '| REGRESSED |' security/FINDINGS.md 2>/dev/null || echo 0)"
echo "  CONFLICT:      $(grep -c '| CONFLICT |' security/FINDINGS.md 2>/dev/null || echo 0)"
echo ""
echo -e "${YELLOW}Reports generated:${NC}"
ls -la docs/orchestration/inbox-*.md 2>/dev/null
echo ""
echo -e "${GREEN}✅ Review security/FINDINGS.md for action items${NC}"
