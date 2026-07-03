#!/usr/bin/env bash
# scripts/verify-findings.sh
# Pre-deploy gate: verifies no OPEN/REGRESSED/NEW findings before testnet deploy.
# Exit 0 = GO (all findings resolved). Exit 1 = NO-GO (open findings remain).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
log_info()    { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; }
log_finding() { echo -e "  ${RED}$1${NC} — $2"; }

STRICT="${1:-}"

# ── Source: single authority file (marked "Read this first") ──
FINDINGS_INDEX="security/FINDINGS-INDEX.md"
[ -f "$FINDINGS_INDEX" ] || { log_error "$FINDINGS_INDEX not found — run audit first"; exit 1; }

# ── Statuses considered "open" (block deployment) ──
# OPEN = confirmed bug, not fixed
# REGRESSED = was "fixed" but broke again
# NEW = fresh finding, not yet assessed
# CONFLICT = contradictory status, requires manual review
OPEN_STATUSES="OPEN|REGRESSED|NEW|CONFLICT"

log_info "Scanning findings in $FINDINGS_INDEX ..."

open_findings=0
total_findings=0

while IFS= read -r line; do
    # Match lines like: | F-XXX | STATUS | ... |
    if echo "$line" | grep -qE '^\|\s*F-[0-9]+\s*\|'; then
        total_findings=$((total_findings + 1))

        # $2 = ID, $3 = Status (FIXED: was $4 — read title instead)
        finding_id=$(echo "$line" | awk -F'|' '{print $2}' | xargs)
        status=$(echo "$line" | awk -F'|' '{print $3}' | xargs)

        # Check for open statuses
        if echo "$status" | grep -qE "^($OPEN_STATUSES)$"; then
            log_finding "$finding_id" "STATUS=$status"
            open_findings=$((open_findings + 1))
        fi

        # Strict: CLAIMED_FIXED without implementation = warning
        if [ "$STRICT" = "--strict" ] && [ "$status" = "CLAIMED_FIXED" ]; then
            finding_file="security/findings/${finding_id}.md"
            if [ ! -f "$finding_file" ]; then
                log_warn "$finding_id — CLAIMED_FIXED but no detail file at $finding_file"
            else
                # Check for Fix Applied section in detail file
                if ! grep -qiE "fix applied|fix committed|commit [0-9a-f]{7}" "$finding_file" 2>/dev/null; then
                    log_warn "$finding_id — CLAIMED_FIXED but no 'Fix Applied' section in detail file"
                fi
            fi
        fi
    fi
done < "$FINDINGS_INDEX"

echo ""

if [ $total_findings -eq 0 ]; then
    log_error "No findings found in $FINDINGS_INDEX — file may be empty or malformed"
    exit 1
fi

if [ $open_findings -gt 0 ]; then
    log_error "$open_findings of $total_findings findings are OPEN/REGRESSED/NEW/CONFLICT"
    echo -e "${YELLOW}Fix or accept-risk before proceeding to testnet deploy.${NC}"
    exit 1
else
    log_info "All $total_findings findings resolved — no open items"
    log_info "Testnet deploy gate: ${GREEN}GO${NC}"
fi
