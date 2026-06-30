#!/usr/bin/env bash
# Генерирует FINDINGS-INDEX.md, ERRORS-INDEX.md, detail files
# Запускать после каждого аудита или изменения findings
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

echo "📄 Generating indexes..."
mkdir -p security/findings security/errors

# Разбить FINDINGS.md на отдельные файлы по F-XXX
awk '
/^### F-[0-9]/ {
    if (current_file != "") close(current_file)
    id = $2
    current_file = "security/findings/" id ".md"
    print > current_file
    next
}
current_file != "" { print > current_file }
' security/FINDINGS.md

# Сгенерировать FINDINGS-INDEX.md
{
    echo "# Findings Index"
    echo "> Read this first. For full details: security/findings/F-XXX.md"
    echo "> Last updated: $(date -u +%Y-%m-%d)"
    echo ""
    echo "## Dashboard"
    echo "| Status | Count |"
    echo "|--------|-------|"
    for status in OPEN CLAIMED_FIXED VERIFIED REGRESSED CONFLICT ACCEPTED_RISK WONTFIX; do
        count=$(grep -c "| $status |" security/FINDINGS.md 2>/dev/null || echo 0)
        [ "$count" -gt 0 ] && echo "| $status | $count |"
    done
    echo ""
    echo "## Findings by Severity"
    echo ""
    for sev in CRITICAL HIGH MEDIUM LOW INFO; do
        count=$(grep -c "\[$sev\]" security/FINDINGS.md 2>/dev/null || echo 0)
        if [ "$count" -gt 0 ]; then
            echo "### $sev ($count)"
            echo "| ID | Status | Title | File |"
            echo "|----|--------|-------|------|"
            grep -B 1 "\[$sev\]" security/FINDINGS.md | grep "^### F-" | while read -r line; do
                id=$(echo "$line" | awk '{print $2}')
                title=$(echo "$line" | sed "s/### $id \[$sev\] //")
                status=$(grep -A 30 "^### $id " security/FINDINGS.md | grep "Status" | head -1 | sed 's/.*| //' | sed 's/ |.*//')
                file=$(grep -A 30 "^### $id " security/FINDINGS.md | grep "Fingerprint" | head -1 | sed 's/.*sha256("//' | sed 's/".*//' | cut -d: -f2)
                echo "| $id | $status | $title | $file |"
            done
            echo ""
        fi
    done
    echo "## How to use"
    echo "- Need full detail? Read security/findings/F-XXX.md"
    echo "- Need verification recipe? It's in the detail file"
    echo "- Only verifier can update status"
} > security/FINDINGS-INDEX.md

# Сгенерировать ERRORS-INDEX.md
{
    echo "# Errors Index"
    echo "> Read this first. For full details: see ERRORS-MEMORY.md sections"
    echo "> Last updated: $(date -u +%Y-%m-%d)"
    echo ""
    echo "## Patterns by Category"
    echo ""
    for cat in "CRYPTOGRAPHY" "BLOCKCHAIN" "SMART CONTRACT" "SECRETS" "LOGGING" "PROCESS" "ARCHITECTURE"; do
        echo "### $cat"
        grep -A 2 "^### EM-" security/ERRORS-MEMORY.md | grep -E "^(### EM-|\*\*Pattern)" | \
        awk '/^### EM-/{id=$2} /^\*\*Pattern/{p=$0; sub(/^\*\*Pattern:\*\* /,"",p); print "| "id" | "p" |"}'
        echo ""
    done
} > security/ERRORS-INDEX.md

echo "✅ FINDINGS-INDEX.md: $(wc -l < security/FINDINGS-INDEX.md) lines"
echo "✅ ERRORS-INDEX.md: $(wc -l < security/ERRORS-INDEX.md) lines"
echo "✅ Detail files: $(ls security/findings/F-*.md 2>/dev/null | wc -l)"
