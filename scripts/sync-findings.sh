#!/usr/bin/env bash
# scripts/sync-findings.sh
# Этап 0.1 ROADMAP_SECURITY_FIXES: кросс-валидация статусов security/findings/F-*.md
# против security/FINDINGS-INDEX.md (единый дашборд).
#
# Реальность проекта: индекс — дашборд (обновляется вручную), файлы — детальная
# история с lifecycle. Строки в индексе: "| F-XXX | STATUS | Title | File |".
# Статус в файле: "| **Status** | STATUS |".
#
# Логика:
#   - индекс VERIFIED_FIXED + файл NEW/OPEN/REGRESSED  → файл устарел (dry-run:
#     показать; --apply: авто-фикс sed 'Status: NEW|OPEN' → 'Status: FIXED')
#   - файл REGRESSED + индекс CLAIMED_FIXED            → КОНФЛИКТ (регрессия
#     задокументирована в файле, индекс не знает) — НЕ трогаем, требуем решения
#   - прочие расхождения                              → показать, не трогать
#
# Exit: 0 = синхронизировано, 1 = есть конфликты/расхождения.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

INDEX="security/FINDINGS-INDEX.md"
FINDINGS_DIR="security/findings"

[ -f "$INDEX" ] || { log_error "$INDEX not found"; exit 1; }
[ -d "$FINDINGS_DIR" ] || { log_error "$FINDINGS_DIR not found"; exit 1; }

APPLY=0
[ "${1:-}" = "--apply" ] && APPLY=1

# ── Парсим индекс: ID → Status ──
declare -A INDEX_STATUS
while IFS='|' read -r _ id status _rest; do
    id="$(echo "$id" | xargs)"
    status="$(echo "$status" | xargs)"
    if [[ "$id" =~ ^F-[0-9]+$ ]] && [[ "$status" =~ ^[A-Z_]+$ ]]; then
        INDEX_STATUS["$id"]="$status"
    fi
done < <(grep -E '^\|\s*F-[0-9]+\s*\|' "$INDEX")

log_info "Индекс: ${#INDEX_STATUS[@]} findings"

# ── Сверка ──
fixed=0; conflicts=0; others=0
declare -a CONFLICT_IDS=()
declare -a OTHER_IDS=()

for f in "$FINDINGS_DIR"/F-*.md; do
    [ -f "$f" ] || continue
    id="$(basename "$f" .md)"
    # Статус из файла (только поле Status, не Severity)
    fstat="$(sed -n 's/^| \*\*Status\*\* | \([A-Z_ ()-]*\) |.*/\1/p' "$f" | head -1 | xargs)"
    istat="${INDEX_STATUS[$id]:-}"

    if [ -z "$istat" ]; then
        log_warn "$id: нет в индексе (file=$fstat)"
        others=$((others+1)); OTHER_IDS+=("$id")
        continue
    fi
    # Нормализация эквивалентных статусов: FIXED/VERIFIED ≡ VERIFIED_FIXED
    norm() { case "$1" in FIXED|VERIFIED) echo "VERIFIED_FIXED";; *) echo "$1";; esac; }
    [ "$(norm "$fstat")" = "$(norm "$istat")" ] && { fixed=$((fixed+1)); continue; }

    # КОНФЛИКТ: файл говорит REGRESSED, индекс говорит FIXED — регрессия задокументирована
    if [ "$fstat" = "REGRESSED" ] && { [ "$istat" = "CLAIMED_FIXED" ] || [ "$istat" = "VERIFIED_FIXED" ]; }; then
        conflicts=$((conflicts+1)); CONFLICT_IDS+=("$id")
        log_error "$id: КОНФЛИКТ file=REGRESSED vs index=$istat — требует ручного решения"
        continue
    fi

    # Безопасный авто-фикс: файл не обновлён (NEW/OPEN), индекс VERIFIED_FIXED
    if [ "$APPLY" = "1" ] && [ "$istat" = "VERIFIED_FIXED" ] && { [ "$fstat" = "NEW" ] || [ "$fstat" = "OPEN" ]; }; then
        sed -i "s/^| \*\*Status\*\* | $fstat |/| **Status** | FIXED |/" "$f"
        log_info "$id: file=$fstat → FIXED (индекс VERIFIED_FIXED)"
        fixed=$((fixed+1)); continue
    fi

    # Прочее расхождение
    others=$((others+1)); OTHER_IDS+=("$id")
    log_warn "$id: file=$fstat vs index=$istat (dry-run; --apply трогает только NEW/OPEN при VERIFIED_FIXED)"
done

echo ""
echo "═══ Итог ═══"
echo "  Синхронизировано:      $fixed"
echo "  Конфликтов (REGRESSED): $conflicts"
echo "  Прочих расхождений:    $others"
if [ "$conflicts" -gt 0 ]; then
    echo "  Конфликты: ${CONFLICT_IDS[*]}"
fi
if [ "$others" -gt 0 ] && [ "$APPLY" = "0" ]; then
    echo "  (dry-run — для применения авто-фиксов: $0 --apply)"
fi

[ "$conflicts" -eq 0 ] && [ "$others" -eq 0 ] && exit 0
exit 1
