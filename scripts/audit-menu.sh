#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

declare -a AUDIT_PHASES=(
    "researcher-infra|01-secrets.md|Audit 1A: Secrets scan"
    "researcher-infra|02-deps.md|Audit 1B: Dependencies scan"
    "researcher-infra|03-config.md|Audit 1C: Configuration audit"
    "researcher-contracts|04-crypto.md|Audit 2A: Cryptography"
    "researcher-backend|05-blockchain.md|Audit 2B: Blockchain logic"
    "researcher-contracts|06-contracts.md|Audit 2C: Smart contracts"
    "researcher-backend|07-input.md|Audit 2D: Input validation"
    "researcher-backend|08-auth.md|Audit 2E: Auth & authorization"
    "researcher-mobile|09-mobile.md|Audit 2F: Mobile-specific"
    "researcher-infra|10-network.md|Audit 3A: Network/TLS"
    "researcher-backend|11-dos.md|Audit 3B: Rate limiting & DoS"
    "researcher-contracts|12-mev.md|Audit 3C: Gas/MEV"
    "researcher-contracts|13-upgrade.md|Audit 3D: Upgradeability"
    "researcher-backend|14-logging.md|Audit 3E: Logging/PII"
    "researcher-infra|15-chaos.md|Audit 4A: Chaos engineering"
    "researcher-backend|16-perf.md|Audit 4B: Performance & load"
    "researcher-contracts|17-formal.md|Audit 4C: Formal verification"
    "researcher-infra|18-compliance.md|Audit 4D: Compliance"
    "tester|19-coverage.md|Audit 4E: Test coverage gaps"
    "verifier|20-verify.md|Verify: Verify audit claims"
    "librarian|21-consolidate.md|Consolidate: Update memory"
)

declare -a FIX_STAGES=(
    "architect|22-architect.md|FIX 1: Architect — propose solutions"
    "implementer|none|FIX 2: Implementer — apply fixes"
    "tdd-updater|none|FIX 3: TDD-updater — sync TDD"
    "tester|none|FIX 4: Tester — run all tests"
    "verifier|20-verify.md|FIX 5: Verify fixes"
    "librarian|21-consolidate.md|FIX 6: Consolidate"
)

while true; do
    echo -e "${BLUE}╔══════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║  MDAOPay Swarm Menu                               ║${NC}"
    echo -e "${BLUE}╚══════════════════════════════════════════════════╝${NC}"
    echo ""
    
    echo -e "${GREEN}─── AUDIT PHASES (find problems) ───${NC}"
    for i in "${!AUDIT_PHASES[@]}"; do
        IFS='|' read -r agent prompt_file description <<< "${AUDIT_PHASES[$i]}"
        printf "  %2d) %s\n" "$((i+1))" "$description"
    done
    
    echo ""
    echo -e "${GREEN}─── FIX WORKFLOW (solve + implement) ───${NC}"
    OFFSET=${#AUDIT_PHASES[@]}
    for i in "${!FIX_STAGES[@]}"; do
        IFS='|' read -r agent prompt_file description <<< "${FIX_STAGES[$i]}"
        printf "  %2d) %s\n" "$((OFFSET+i+1))" "$description"
    done
    
    echo ""
    echo -e "${YELLOW}─── SHORTCUTS ───${NC}"
    echo "   a) Run ALL audit phases"
    echo "   f) Run FULL fix workflow (audit → architect → implement → test)"
    echo "   w) Run fix workflow only (skip audit)"
    echo "   s) Show findings dashboard"
    echo "   r) Show all reports"
    echo "   c) Git commit changes"
    echo "   q) Quit"
    echo ""
    read -p "Select [1-$((OFFSET+${#FIX_STAGES[@]}))/a/f/w/s/r/c/q]: " choice
    
    case "$choice" in
        q|Q) echo "Bye!"; exit 0 ;;
        s|S) make findings-stats; echo ""; continue ;;
        r|R)
            ls -la docs/orchestration/*-report.md docs/orchestration/inbox-*.md 2>/dev/null
            echo ""; continue ;;
        c|C)
            git status --short
            echo ""
            read -p "Commit message: " msg
            git add -A
            git commit -m "$msg"
            echo -e "${GREEN}✅ Committed${NC}"
            continue ;;
        a|A)
            for i in "${!AUDIT_PHASES[@]}"; do
                IFS='|' read -r agent prompt_file description <<< "${AUDIT_PHASES[$i]}"
                echo ""
                echo -e "${GREEN}▶ [$((i+1))/${#AUDIT_PHASES[@]}] ${description}${NC}"
                opencode run --agent "$agent" "$(cat "scripts/prompts/${prompt_file}")"
            done ;;
        f|F) ./scripts/fix-workflow.sh ;;
        w|W)
            for i in "${!FIX_STAGES[@]}"; do
                IFS='|' read -r agent prompt_file description <<< "${FIX_STAGES[$i]}"
                echo ""
                echo -e "${GREEN}▶ [$((i+1))/${#FIX_STAGES[@]}] ${description}${NC}"
                if [ "$prompt_file" = "none" ]; then
                    case "$agent" in
                        implementer)
                            opencode run --agent implementer "Read docs/orchestration/architect-report.md and apply fixes. Update TDD in same commit. Run tests. Output: docs/orchestration/implementer-report.md" ;;
                        tdd-updater)
                            opencode run --agent tdd-updater "Read docs/orchestration/implementer-report.md, update TDD/TDD.md. Output: docs/orchestration/tdd-update-report.md" ;;
                        tester)
                            opencode run --agent tester "Run all test suites. Map findings to test scenarios. Output: docs/orchestration/test-report.md" ;;
                    esac
                else
                    opencode run --agent "$agent" "$(cat "scripts/prompts/${prompt_file}")"
                fi
            done ;;
        ''|*[!0-9]*) echo "Invalid choice"; continue ;;
        *)
            TOTAL=$((OFFSET+${#FIX_STAGES[@]}))
            if [ "$choice" -lt 1 ] || [ "$choice" -gt "$TOTAL" ]; then
                echo "Out of range"; continue
            fi
            if [ "$choice" -le "$OFFSET" ]; then
                IFS='|' read -r agent prompt_file description <<< "${AUDIT_PHASES[$((choice-1))]}"
            else
                IDX=$((choice-OFFSET-1))
                IFS='|' read -r agent prompt_file description <<< "${FIX_STAGES[$IDX]}"
            fi
            echo ""
            echo -e "${GREEN}▶ ${description}${NC}"
            echo -e "${YELLOW}  Agent: ${agent}${NC}"
            [ "$prompt_file" != "none" ] && echo -e "${YELLOW}  Prompt: scripts/prompts/${prompt_file}${NC}"
            echo ""
            if [ "$prompt_file" = "none" ]; then
                case "$agent" in
                    implementer)
                        opencode run --agent implementer "Read docs/orchestration/architect-report.md and apply fixes. Update TDD in same commit. Run tests. Output: docs/orchestration/implementer-report.md" ;;
                    tdd-updater)
                        opencode run --agent tdd-updater "Read docs/orchestration/implementer-report.md, update TDD/TDD.md. Output: docs/orchestration/tdd-update-report.md" ;;
                    tester)
                        opencode run --agent tester "Run all test suites. Map findings to test scenarios. Output: docs/orchestration/test-report.md" ;;
                esac
            else
                opencode run --agent "$agent" "$(cat "scripts/prompts/${prompt_file}")"
            fi ;;
    esac
    
    echo ""
    echo -e "${GREEN}✅ Done. Press Enter to return to menu...${NC}"
    read -r
done
