#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════
# MDAOPay — scan-secrets.sh
# Поиск возможных утечек секретов в репозитории.
# Используется в CI и вручную.
#
# Использование:
#   ./scripts/scan-secrets.sh           # scan all files
#   ./scripts/scan-secrets.sh --ci      # exit 1 on any leak
# ═══════════════════════════════════════════════════════════
set -euo pipefail

CI_MODE=false
[[ "${1:-}" == "--ci" ]] && CI_MODE=true

RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m'

# Паттерны — синхронизированы с .githooks/pre-commit
PATTERNS=(
  '(PRIVATE_KEY|private_key|PRIV_KEY)["\047]?\s*[:=]\s*["\047]?(0x)?[a-fA-F0-9]{64}'
  '(secret|SECRET)["\047]?\s*[:=]\s*["\047]?(0x)?[a-fA-F0-9]{64}'
  'sk_live_[a-zA-Z0-9]+'
  'pk_live_[a-zA-Z0-9]+'
  'AIza[0-9A-Za-z_-]{35}'
  'AKIA[0-9A-Z]{16}'
  'ghp_[a-zA-Z0-9]{36}'
  'gho_[a-zA-Z0-9]{36}'
  'xox[baprs]-[a-zA-Z0-9_-]+'
)

EXCLUDE_DIRS=".git|.gradle|build|app/build|backend/build|node_modules|.idea"
EXCLUDE_FILES="\.(png|jpg|jpeg|gif|ico|woff2|ttf|keystore|jks|apk|aab)$|\.secrets\.baseline|\.env\.example|.githooks/"

HAS_LEAK=false
SCANNED=0

while IFS= read -r -d '' file; do
  SCANNED=$((SCANNED + 1))

  for pattern in "${PATTERNS[@]}"; do
    MATCHES=$(grep -Pn "$pattern" "$file" 2>/dev/null | head -3 || true)
    [ -z "$MATCHES" ] && continue

    while IFS= read -r line; do
      LINE_NUM=$(echo "$line" | cut -d: -f1)
      MATCHED=$(echo "$line" | cut -d: -f2- | tr -d '[:space:]' | head -c 80)

      case "$MATCHED" in
        *0000000000000000000000000000000000000000000000000000000000000000*) continue ;;
        *0000000000000000000000000000000000000000*) continue ;;
        *YOUR_API_KEY*) continue ;;
        *sk_live_xxx*) continue ;;
        *pim_xxx*) continue ;;
      esac

      echo -e "${RED}⚠️  ${file}:${LINE_NUM}${NC}"
      echo "   Паттерн: $pattern"
      echo "   Найдено: ...${MATCHED}..."
      HAS_LEAK=true
    done <<< "$MATCHES"
  done
done < <(find . -type f \
  -not -path './.git/*' \
  -not -path './.agents/*' \
  -not -path './contracts/lib/*' \
  -not -path '*/build/*' \
  -not -path '*/.gradle/*' \
  -not -path '*/node_modules/*' \
  -not -path '*/.idea/*' \
  -not -name '*.png' -not -name '*.jpg' -not -name '*.jpeg' \
  -not -name '*.gif' -not -name '*.ico' \
  -not -name '*.keystore' -not -name '*.jks' \
  -not -name '*.apk' -not -name '*.aab' \
  -not -name '.secrets.baseline' \
  -not -name '.env.example' \
  -print0 2>/dev/null)

if [ "$HAS_LEAK" = true ]; then
  echo ""
  echo -e "${RED}╔══════════════════════════════════════════════════╗${NC}"
  echo -e "${RED}║  Найдены возможные утечки секретов!             ║${NC}"
  echo -e "${RED}╚══════════════════════════════════════════════════╝${NC}"
  echo "Проверено файлов: $SCANNED"
  $CI_MODE && exit 1
  exit 1
fi

echo -e "${GREEN}✅ Секретов не найдено (проверено $SCANNED файлов)${NC}"
exit 0
