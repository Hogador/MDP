#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

mkdir -p docs/orchestration

declare -a STAGES=(
    "researcher-infra|01-secrets.md|Stage 1: Audit — Secrets"
    "researcher-infra|02-deps.md|Stage 1: Audit — Dependencies"
    "researcher-infra|03-config.md|Stage 1: Audit — Config"
    "researcher-contracts|04-crypto.md|Stage 1: Audit — Crypto"
    "researcher-backend|05-blockchain.md|Stage 1: Audit — Blockchain"
    "researcher-contracts|06-contracts.md|Stage 1: Audit — Contracts"
    "researcher-backend|07-input.md|Stage 1: Audit — Input validation"
    "researcher-backend|08-auth.md|Stage 1: Audit — Auth"
    "researcher-mobile|09-mobile.md|Stage 1: Audit — Mobile"
    "researcher-infra|10-network.md|Stage 1: Audit — Network"
    "researcher-backend|11-dos.md|Stage 1: Audit — DoS"
    "researcher-contracts|12-mev.md|Stage 1: Audit — MEV"
    "researcher-contracts|13-upgrade.md|Stage 1: Audit — Upgrade"
    "researcher-backend|14-logging.md|Stage 1: Audit — Logging"
    "researcher-infra|15-chaos.md|Stage 1: Audit — Chaos"
    "researcher-backend|16-perf.md|Stage 1: Audit — Performance"
    "researcher-contracts|17-formal.md|Stage 1: Audit — Formal"
    "researcher-infra|18-compliance.md|Stage 1: Audit — Compliance"
    "tester|19-coverage.md|Stage 1: Audit — Coverage"
    "verifier|20-verify.md|Stage 2: Verify audit claims"
    "architect|22-architect.md|Stage 3: Architect — propose solutions"
    "implementer|none|Stage 4: Implementer — apply fixes"
    "tdd-updater|none|Stage 5: TDD-updater — sync TDD"
    "tester|none|Stage 6: Tester — run all tests"
    "verifier|20-verify.md|Stage 7: Verify fixes — update FINDINGS.md"
    "librarian|21-consolidate.md|Stage 8: Consolidate — update memory"
)

if [ -n "${1:-}" ]; then
    STAGE_NUM="$1"
    if [ "$STAGE_NUM" -lt 1 ] || [ "$STAGE_NUM" -gt ${#STAGES[@]} ]; then
        echo "Usage: $0 [stage_number 1-${#STAGES[@]}]"
        exit 1
    fi
    STAGES=("${STAGES[$((STAGE_NUM-1))]}")
    SINGLE_MODE=true
else
    SINGLE_MODE=false
fi

echo -e "${BLUE}╔══════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  MDAOPay Full Fix Workflow — ${#STAGES[@]} stages    ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════╝${NC}"
echo ""

for i in "${!STAGES[@]}"; do
    IFS='|' read -r agent prompt_file description <<< "${STAGES[$i]}"
    
    echo -e "${GREEN}▶ [$((i+1))/${#STAGES[@]}] ${description}${NC}"
    echo -e "${YELLOW}  Agent: ${agent}${NC}"
    [ "$prompt_file" != "none" ] && echo -e "${YELLOW}  Prompt: scripts/prompts/${prompt_file}${NC}"
    
    if [ "$SINGLE_MODE" = false ]; then
        echo "Press Enter to start (Ctrl+C to abort)..."
        read -r
    fi
    
    if [ "$prompt_file" = "none" ]; then
        case "$agent" in
            implementer)
                opencode run --agent implementer "Read docs/orchestration/architect-report.md and apply fixes per architect recommendations. Update TDD in same commit. Run tests after each fix. Output: docs/orchestration/implementer-report.md"
                ;;
            tdd-updater)
                opencode run --agent tdd-updater "Read docs/orchestration/implementer-report.md and update TDD/TDD.md to reflect all code changes. Output: docs/orchestration/tdd-update-report.md"
                ;;
            tester)
                opencode run --agent tester "Run all test suites (forge, gradlew, npm). Map findings to test scenarios from TDD/test-scenarios-v5-final.md. Output: docs/orchestration/test-report.md"
                ;;
        esac
    else
        opencode run --agent "$agent" "$(cat "scripts/prompts/${prompt_file}")"
    fi
    
    echo -e "${GREEN}✅ Stage $((i+1)) complete${NC}"
    echo ""
done

echo -e "${BLUE}╔══════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  WORKFLOW COMPLETE                               ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════╝${NC}"
echo ""
make findings-stats 2>/dev/null || true
