#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

RED='\033[0;31m'; YELLOW='\033[1;33m'; GREEN='\033[0;32m'; NC='\033[0m'
echo "🔍 Ponytail Audit"; echo "═════════════════"
VIOLATIONS=0

STAGED=$(git diff --cached --diff-filter=ACM --name-only 2>/dev/null | grep -E '\.(kt|ts|sol)$' || true)
# PONYTAIL_STAGED_ONLY=1 (режим pre-commit): проверяем только staged файлы,
# не откатываясь на скан всего дерева — иначе коммит доков упадёт из-за
# нарушений в коде, который к коммиту вообще не относится.
if [ -z "$STAGED" ] && [ "${PONYTAIL_STAGED_ONLY:-0}" != "1" ]; then
    STAGED=$(find contracts/src backend/src relay/src app/src -type f \( -name "*.kt" -o -name "*.ts" -o -name "*.sol" \) 2>/dev/null)
fi

if [ -z "$STAGED" ]; then
    echo "✅ Ponytail audit: нет .kt/.ts/.sol файлов для проверки"
    exit 0
fi

for file in $STAGED; do
    [ -f "$file" ] || continue
    # Вендорские библиотеки не ревьюим — их код не наш
    case "$file" in
        contracts/lib/*|*/node_modules/*) continue ;;
    esac
    # Закомментированный код: необязательная метка ("C-1:", "dev:", "-2:"),
    # затем СРАЗУ statement: ключевое слово | присваивание x = |
    # вызов с ";" | вызов в конце строки.
    # Проза с "func()" внутри ("verify() function", "type hash: Quote(a,b)") не матчится,
    # т.к. всё выражение заякорено от начала строки после метки.
    COMMENTED=$(grep -nP '^\s*//+\s*(?:[A-Za-z0-9_./-]{1,24}:\s*)?(?:(?:const |let |var |val |final |return |await |async |throw |if \(|for \(|while \(|switch )|[a-zA-Z_$][a-zA-Z0-9_.$]*\s*=[^=]|[a-zA-Z_$][a-zA-Z0-9_.$]*\([^)]*\)\s*;|[a-zA-Z_$][a-zA-Z0-9_.$]*\([^)]*\)\s*$)' "$file" 2>/dev/null | grep -v 'ponytail:doc' | head -5 || true)
    if [ -n "$COMMENTED" ]; then
        echo -e "${RED}[$file] Commented-out code:${NC}"; echo "$COMMENTED" | head -3
        VIOLATIONS=$((VIOLATIONS+1))
    fi
    TODO_NO_OWNER=$(grep -nE 'TODO|FIXME' "$file" 2>/dev/null | grep -vE 'TODO\(|FIXME\(' | head -3 || true)
    if [ -n "$TODO_NO_OWNER" ]; then
        echo -e "${YELLOW}[$file] TODO без owner:${NC}"; echo "$TODO_NO_OWNER"
        VIOLATIONS=$((VIOLATIONS+1))
    fi
    EMPTY_CATCH=$(grep -nE 'catch\s*\([^)]*\)\s*\{\s*\}' "$file" 2>/dev/null | head -3 || true)
    if [ -n "$EMPTY_CATCH" ]; then
        echo -e "${RED}[$file] Empty catch:${NC}"; echo "$EMPTY_CATCH"
        VIOLATIONS=$((VIOLATIONS+1))
    fi
done

echo ""
if [ "$VIOLATIONS" -gt 0 ]; then
    echo -e "${RED}❌ $VIOLATIONS ponytail violations${NC}"
    exit 1
else
    echo -e "${GREEN}✅ Ponytail audit passed${NC}"
    exit 0
fi
