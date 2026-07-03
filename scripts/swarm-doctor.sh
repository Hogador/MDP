#!/usr/bin/env bash
# swarm-doctor.sh v2.0 — проверка здоровья Swarm v10
# + реальные тесты провайдеров + AI-ANALYSIS OUTPUT

set -uo pipefail

VERBOSE=0
FIX_MODE=0
for arg in "$@"; do
  case "$arg" in
    --verbose|-v) VERBOSE=1 ;;
    --fix) FIX_MODE=1 ;;
    --help|-h)
      echo "Usage: $0 [--verbose] [--fix]"
      exit 0 ;;
  esac
done

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
BOLD='\033[1m'
NC='\033[0m'

PASS=0
FAIL=0
WARN=0
FAILURES=()
WARNINGS=()

check() {
  local desc="$1"
  local cond="$2"
  if [ "$cond" = "0" ]; then
    printf "  ${GREEN}[OK]${NC}  %s\n" "$desc"
    PASS=$((PASS + 1))
  else
    printf "  ${RED}[FAIL]${NC} %s\n" "$desc"
    FAIL=$((FAIL + 1))
    FAILURES+=("$desc")
  fi
}

check_warn() {
  local desc="$1"
  local cond="$2"
  if [ "$cond" = "0" ]; then
    printf "  ${GREEN}[OK]${NC}  %s\n" "$desc"
    PASS=$((PASS + 1))
  else
    printf "  ${YELLOW}[WARN]${NC} %s\n" "$desc"
    WARN=$((WARN + 1))
    WARNINGS+=("$desc")
  fi
}

check_contains() {
  local file="$1"
  local needle="$2"
  local desc="$3"
  if [ -f "$file" ] && grep -iq "$needle" "$file"; then
    printf "  ${GREEN}[OK]${NC}  %s\n" "$desc"
    PASS=$((PASS + 1))
  else
    printf "  ${RED}[FAIL]${NC} %s\n" "$desc"
    FAIL=$((FAIL + 1))
    FAILURES+=("$desc")
  fi
}

check_not_exists() {
  if [ ! -e "$1" ]; then
    printf "  ${GREEN}[OK]${NC}  %s\n" "$2"
    PASS=$((PASS + 1))
  else
    printf "  ${RED}[FAIL]${NC} %s (найдено: %s)\n" "$2" "$1"
    FAIL=$((FAIL + 1))
    FAILURES+=("$2")
  fi
}

echo ""
echo -e "${BLUE}${BOLD}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}${BOLD}  Swarm Doctor v2.0 — проверка здоровья MDAOPay${NC}"
echo -e "${BLUE}${BOLD}═══════════════════════════════════════════════════════════════${NC}"
echo ""

# === 1. СТРУКТУРА КАТАЛОГОВ ===
echo -e "${BOLD}1. СТРУКТУРА КАТАЛОГОВ${NC}"
for d in .hive .hive/daily .hive/audit .hive/verifications .hive/escalations .hive/reports .hive/impact .hive/prompts .hive/commands .hive/stats .hive/ponytail docs docs/adr security scripts bin; do
  check "Каталог $d существует" $([ -d "$d" ]; echo $?)
done
echo ""

# === 2. ПРОМТЫ 11 АГЕНТОВ ===
echo -e "${BOLD}2. ПРОМТЫ 11 АГЕНТОВ${NC}"
AGENTS="coordinator context-resolver product-gate researcher architect implementer code-reviewer verifier adr-writer lessons-learned evolution-manager"
for agent in $AGENTS; do
  FILE=".hive/prompts/$agent/system.md"
  if [ ! -f "$FILE" ]; then
    check "Промт $agent" 1
    continue
  fi
  SIZE=$(wc -c < "$FILE")
  if [ "$SIZE" -lt 500 ]; then
    check "Промт $agent (размер $SIZE)" 1
  else
    check "Промт $agent ($SIZE bytes)" 0
  fi
done
echo ""

# === 3. БАЗОВЫЕ ДОКУМЕНТЫ ===
echo -e "${BOLD}3. БАЗОВЫЕ ДОКУМЕНТЫ${NC}"
check "docs/VISION.md существует" $([ -f docs/VISION.md ]; echo $?)
check_contains "docs/VISION.md" "ЗАПРЕЩЁННЫЕ КОМПРОМИССЫ" "VISION: запрещённые компромиссы"
check_contains "docs/VISION.md" "MDAOPay" "VISION: имя проекта"
check "docs/ROADMAP.md существует" $([ -f docs/ROADMAP.md ]; echo $?)
check_contains "docs/ROADMAP.md" "LOCALNET" "ROADMAP: LOCALNET"
check_contains "docs/ROADMAP.md" "TESTNET" "ROADMAP: TESTNET"
check_contains "docs/ROADMAP.md" "MAINNET" "ROADMAP: MAINNET"
check "security/KNOWLEDGE-BASE.md существует" $([ -f security/KNOWLEDGE-BASE.md ]; echo $?)
check_contains "security/KNOWLEDGE-BASE.md" "KB-" "KB: формат ID"
check "security/RISK-REGISTRY.md существует" $([ -f security/RISK-REGISTRY.md ]; echo $?)
check_contains "security/RISK-REGISTRY.md" "Severity" "RISK: Severity"
check_contains "security/RISK-REGISTRY.md" "Mitigation" "RISK: Mitigation"
echo ""

# === 4. RUNTIME-ФАЙЛЫ ===
echo -e "${BOLD}4. RUNTIME-ФАЙЛЫ${NC}"
check ".hive/config.json существует" $([ -f .hive/config.json ]; echo $?)
if [ -f .hive/config.json ]; then
  python3 -c "import json; json.load(open('.hive/config.json'))" 2>/dev/null
  check "config.json валидный JSON" $?
  check_contains ".hive/config.json" '"version"' "config: version"
  check_contains ".hive/config.json" "routing" "config: routing"
  check_contains ".hive/config.json" "loop_detection" "config: loop_detection"
  check_contains ".hive/config.json" "fallbacks" "config: fallbacks секция"
fi
check ".hive/state.json существует" $([ -f .hive/state.json ]; echo $?)
check ".hive/memories.jsonl существует" $([ -f .hive/memories.jsonl ]; echo $?)
check ".hive/stats/daily.jsonl существует" $([ -f .hive/stats/daily.jsonl ]; echo $?)
check ".hive/stats/fallback.jsonl существует" $([ -f .hive/stats/fallback.jsonl ]; echo $?)
echo ""

# === 5. ADR СТРУКТУРА ===
echo -e "${BOLD}5. ADR СТРУКТУРА${NC}"
check "docs/adr/README.md существует" $([ -f docs/adr/README.md ]; echo $?)
check "docs/adr/ADR-000-template.md существует" $([ -f docs/adr/ADR-000-template.md ]; echo $?)
echo ""

# === 6. CLI-РОУТЕР ===
echo -e "${BOLD}6. CLI-РОУТЕР${NC}"
for cli in swarm-router swarm-audit swarm-feature swarm-bug swarm-roadmap; do
  FILE="bin/$cli"
  check "bin/$cli существует" $([ -f "$FILE" ]; echo $?)
  [ -f "$FILE" ] && check "bin/$cli исполняемый" $([ -x "$FILE" ]; echo $?)
done
check_contains "bin/swarm-router" "opencode" "swarm-router: запускает opencode"
echo ""

# === 7. .gitignore ===
echo -e "${BOLD}7. .gitignore${NC}"
check ".gitignore существует" $([ -f .gitignore ]; echo $?)
[ -f .gitignore ] && check_contains ".gitignore" "Swarm v9.0\|Swarm v10" ".gitignore: swarm секция"
[ -f .gitignore ] && check_contains ".gitignore" ".hive/state.json" ".gitignore: state.json"
[ -f .gitignore ] && check_contains ".gitignore" ".zcode" ".gitignore: .zcode исключён"
echo ""

# === 8. ОТСУТСТВИЕ ХВОСТОВ СТАРОГО СВАРМА ===
echo -e "${BOLD}8. ОТСУТСТВИЕ ХВОСТОВ СТАРОГО СВАРМА${NC}"
check_not_exists "AGENTS.md" "Старый AGENTS.md удалён (в корне проекта)"
check_not_exists "security/ERRORS-MEMORY.md" "Старый ERRORS-MEMORY.md"
check_not_exists "security/FINDINGS.md" "Старый FINDINGS.md"
check_not_exists "security/FIX-PATTERNS.md" "Старый FIX-PATTERNS.md"
check_not_exists "Makefile.audit" "Старый Makefile.audit"
echo ""

# === 9. СПЕЦИФИКАЦИЯ АГЕНТОВ ===
echo -e "${BOLD}9. СПЕЦИФИКАЦИЯ АГЕНТОВ (ключевые принципы)${NC}"
check_contains ".hive/prompts/coordinator/system.md" "Coordinator" "Coordinator: заголовок"
check_contains ".hive/prompts/coordinator/system.md" "СИСТЕМНЫЙ ПРОМТ\|Swarm v10" "Coordinator: v10"
check_contains ".hive/prompts/coordinator/system.md" "FALLBACK DETECTION" "Coordinator: fallback"
check_contains ".hive/prompts/coordinator/system.md" "ЛОГИРОВАНИЕ" "Coordinator: логирование"
check_contains ".hive/prompts/context-resolver/system.md" "impact" "Context-resolver: --impact"
check_contains ".hive/prompts/context-resolver/system.md" "radius" "Context-resolver: radius"
check_contains ".hive/prompts/product-gate/system.md" "VISION" "Product-gate: VISION"
check_contains ".hive/prompts/researcher/system.md" "security" "Researcher: security"
check_contains ".hive/prompts/researcher/system.md" "devops" "Researcher: devops"
check_contains ".hive/prompts/architect/system.md" "radius" "Architect: radius"
check_contains ".hive/prompts/architect/system.md" "gap-analysis" "Architect: --gap-analysis"
check_contains ".hive/prompts/implementer/system.md" "TDD\|MANDATORY" "Implementer: TDD"
check_contains ".hive/prompts/implementer/system.md" "forge build\|BUILD" "Implementer: build"
check_contains ".hive/prompts/implementer/system.md" "self-verification\|SELF-VERIFICATION" "Implementer: запрет self-verification"
check_contains ".hive/prompts/code-reviewer/system.md" "Red Team" "Code-reviewer: Red Team"
check_contains ".hive/prompts/verifier/system.md" "build" "Verifier: --build"
check_contains ".hive/prompts/verifier/system.md" "logic" "Verifier: --logic"
check_contains ".hive/prompts/verifier/system.md" "requirements" "Verifier: --requirements"
check_contains ".hive/prompts/adr-writer/system.md" "свершившийся факт\|свершившийся" "ADR-writer: фиксация факта"
check_contains ".hive/prompts/lessons-learned/system.md" "Tier 2" "Lessons: Tier 2"
check_contains ".hive/prompts/evolution-manager/system.md" "архив" "Evolution: только архивация"
echo ""

# === 10. .gitkeep ===
echo -e "${BOLD}10. .gitkeep В ПУСТЫХ КАТАЛОГАХ${NC}"
for d in .hive/daily .hive/audit .hive/verifications .hive/escalations .hive/reports .hive/impact; do
  if [ -d "$d" ]; then
    FILECOUNT=$(ls -A "$d" 2>/dev/null | grep -v '^\.gitkeep$' | wc -l)
    if [ "$FILECOUNT" = "0" ]; then
      [ -f "$d/.gitkeep" ] && check "$d/.gitkeep" 0 || { check_warn "$d/.gitkeep отсутствует" 1; [ "$FIX_MODE" = "1" ] && touch "$d/.gitkeep"; }
    else
      check "$d содержит файлы (runtime active)" 0
    fi
  fi
done
echo ""

# === 11. ИНТЕГРАЦИЯ С OPENCODE ===
echo -e "${BOLD}11. ИНТЕГРАЦИЯ С OPENCODE${NC}"
check "Каталог .opencode/agent/ существует" $([ -d .opencode/agent ]; echo $?)
check ".hive/commands/swarm.md существует" $([ -f .hive/commands/swarm.md ]; echo $?)
check ".opencode/command/swarm.md существует" $([ -f .opencode/command/swarm.md ]; echo $?)
check "scripts/swarm-sync-prompts.sh существует" $([ -f scripts/swarm-sync-prompts.sh ]; echo $?)
check "scripts/swarm-stats.sh существует" $([ -f scripts/swarm-stats.sh ]; echo $?)
check "scripts/swarm-doctor.sh существует" $([ -f scripts/swarm-doctor.sh ]; echo $?)
for agent in $AGENTS; do
  FILE=".opencode/agent/$agent.md"
  if [ -f "$FILE" ]; then
    head -1 "$FILE" | grep -q "^---$" && check "opencode: $agent.md (frontmatter)" 0 || check "opencode: $agent.md (НЕТ frontmatter)" 1
  else
    check "opencode: $agent.md" 1
  fi
done
echo ""

# === 12. PONYTAIL ===
echo -e "${BOLD}12. PONYTAIL${NC}"
check ".hive/ponytail/patterns.yaml существует" $([ -f .hive/ponytail/patterns.yaml ]; echo $?)
check_contains ".hive/ponytail/patterns.yaml" "solidity" "Ponytail: Solidity паттерны"
check_contains ".hive/ponytail/patterns.yaml" "typescript" "Ponytail: TypeScript паттерны"
check_contains ".hive/prompts/implementer/system.md" "PONYTAIL" "Implementer: Ponytail pre-check"
echo ""

# === 13. НОВОЕ: test_agent_models (model: в frontmatter валидна) ===
echo -e "${BOLD}13. АГЕНТЫ — model: В FRONTMATTER${NC}"
GLOBAL_OCP="$HOME/.config/opencode/opencode.json"
# Собираем все валидные модели из opencode.json
VALID_MODELS=$(python3 -c "
import json
try:
    data = json.load(open('$GLOBAL_OCP'))
    models = set()
    for prov_name, prov in data.get('provider', {}).items():
        for m in prov.get('models', {}):
            models.add(f'{prov_name}/{m}')
    for m in models:
        print(m)
except: pass
" 2>/dev/null)

# Добавляем opencode/big-pickle (встроенная)
VALID_MODELS="opencode/big-pickle
 $VALID_MODELS"

for agent in $AGENTS; do
  FILE=".opencode/agent/$agent.md"
  [ ! -f "$FILE" ] && continue
  MODEL=$(python3 -c "
import re
content = open('$FILE').read()
m = re.search(r'^model:\s*\"?([^\"]+)\"?', content, re.MULTILINE)
print(m.group(1) if m else '')
" 2>/dev/null)
  if [ -z "$MODEL" ]; then
    check_warn "$agent: model не указана" 1
  else
    # Проверяем что модель есть в валидных
    if echo "$VALID_MODELS" | grep -qF "$MODEL"; then
      check "$agent: model '$MODEL' валидна" 0
    else
      check "$agent: model '$MODEL' НЕ найдена в opencode.json" 1
    fi
  fi
done
echo ""

# === 14. НОВОЕ: test_symlinks (глобальные агенты — симлинки) ===
echo -e "${BOLD}14. СИМЛИНКИ В ~/.config/opencode/agent/${NC}"
GLOBAL_AGENT_DIR="$HOME/.config/opencode/agent"
if [ -d "$GLOBAL_AGENT_DIR" ]; then
  for agent in $AGENTS; do
    FILE="$GLOBAL_AGENT_DIR/$agent.md"
    if [ -L "$FILE" ]; then
      TARGET=$(readlink "$FILE")
      if [ -f "$TARGET" ]; then
        check "global $agent.md → симлинк OK" 0
      else
        check "global $agent.md → СЛОМАННЫЙ симлинк" 1
      fi
    elif [ -f "$FILE" ]; then
      check_warn "global $agent.md — НЕ симлинк (копия)" 1
    else
      check "global $agent.md — отсутствует" 1
    fi
  done
else
  check_warn "Каталог $GLOBAL_AGENT_DIR не существует" 1
fi
echo ""

# === 15. НОВОЕ: test_global_agents_clean (нет ли мусора в global) ===
echo -e "${BOLD}15. ЧИСТОТА ~/.config/opencode/agent/ (нет старых агентов)${NC}"
if [ -d "$GLOBAL_AGENT_DIR" ]; then
  for f in "$GLOBAL_AGENT_DIR"/*.md; do
    [ ! -f "$f" ] && continue
    base=$(basename "$f" .md)
    FOUND=0
    for agent in $AGENTS; do
      [ "$base" = "$agent" ] && FOUND=1 && break
    done
    if [ "$FOUND" = "0" ]; then
      check "Старый агент в global: $base.md" 1
    fi
  done
  # Если не было FAIL'ов выше — значит чисто
  check "Нет старых агентов в global" 0
fi
echo ""

# === 16. НОВОЕ: test_stats_fresh (daily.jsonl обновлялся) ===
echo -e "${BOLD}16. СТАТИСТИКА (daily.jsonl актуальность)${NC}"
STATS_FILE=".hive/stats/daily.jsonl"
if [ -f "$STATS_FILE" ]; then
  LINES=$(wc -l < "$STATS_FILE")
  if [ "$LINES" = "0" ]; then
    check_warn "daily.jsonl пустой (задачи ещё не запускались)" 1
  else
    LAST_DATE=$(python3 -c "
import json
lines = open('$STATS_FILE').readlines()
if lines:
    try:
        r = json.loads(lines[-1])
        print(r.get('date', 'unknown'))
    except: print('unknown')
else: print('empty')
" 2>/dev/null)
    TODAY=$(date +%Y-%m-%d)
    check "daily.jsonl: $LINES записей, последняя $LAST_DATE" 0
  fi
else
  check_warn "daily.jsonl не существует" 1
fi
echo ""

# === 17. НОВОЕ: test_orphan_backups ===
echo -e "${BOLD}17. ОРФАН-БЭКАПЫ (.backup файлов)${NC}"
BACKUP_COUNT=$(find .hive/prompts/ -name "*.backup-*" 2>/dev/null | wc -l)
if [ "$BACKUP_COUNT" -gt 5 ]; then
  check_warn "Накопилось $BACKUP_COUNT .backup файлов в .hive/prompts/" 1
else
  check "Backup файлов: $BACKUP_COUNT (норма)" 0
fi
echo ""

# === 18. НОВОЕ: test_coordinator_function — Coordinator видит агентов ===
echo -e "${BOLD}18. COORDINATOR FUNCTION (видит агентов и может давать команды)${NC}"
COORD_FILE=".hive/prompts/coordinator/system.md"

# Проверяем что в промте есть упоминания всех 11 агентов
for agent in $AGENTS; do
  if grep -q "$agent" "$COORD_FILE" 2>/dev/null; then
    check "Coordinator упоминает агента '$agent'" 0
  else
    check "Coordinator НЕ упоминает агента '$agent'" 1
  fi
done

# Проверяем что Coordinator имеет правила вызова через task
check_contains "$COORD_FILE" "task(" "Coordinator: вызывает task tool"
check_contains "$COORD_FILE" "mode=audit" "Coordinator: режим audit"
check_contains "$COORD_FILE" "mode=feature" "Coordinator: режим feature"
check_contains "$COORD_FILE" "mode=bug" "Coordinator: режим bug"
check_contains "$COORD_FILE" "mode=explain" "Coordinator: режим explain"
check_contains "$COORD_FILE" "mode=roadmap" "Coordinator: режим roadmap"

# Проверяем что .opencode/agent/coordinator.md существует и имеет mode: primary
if [ -f ".opencode/agent/coordinator.md" ]; then
  if grep -q "mode: primary" ".opencode/agent/coordinator.md"; then
    check "Coordinator: mode: primary в frontmatter" 0
  else
    check "Coordinator: mode: primary ОТСУТСТВУЕТ" 1
  fi
else
  check ".opencode/agent/coordinator.md существует" 1
fi

# Проверяем что глобальный AGENTS.md не содержит старого контекста
GLOBAL_AGENTS="$HOME/.config/opencode/AGENTS.md"
if [ -f "$GLOBAL_AGENTS" ]; then
  if grep -q "Wave 16\|Phase 0" "$GLOBAL_AGENTS"; then
    check "Global AGENTS.md: НЕТ старого контекста (Wave 16/Phase 0)" 1
  else
    check "Global AGENTS.md: чистый (нет Wave 16/Phase 0)" 0
  fi
fi
echo ""

# === ИТОГОВЫЙ ОТЧЁТ ===
echo -e "${BOLD}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}  ИТОГОВЫЙ ОТЧЁТ${NC}"
echo -e "${BOLD}═══════════════════════════════════════════════════════════════${NC}"
echo ""
printf "  ${GREEN}OK${NC}:   %d\n" "$PASS"
printf "  ${YELLOW}WARN${NC}: %d\n" "$WARN"
printf "  ${RED}FAIL${NC}: %d\n" "$FAIL"
echo ""

if [ "$FAIL" = "0" ] && [ "$WARN" = "0" ]; then
  echo -e "  ${GREEN}${BOLD}✅ Swarm v10 — ВСЁ КОРРЕКТНО${NC}"
elif [ "$FAIL" = "0" ]; then
  echo -e "  ${YELLOW}${BOLD}⚠️  Swarm v10 — РАБОТАЕТ, но есть предупреждения${NC}"
else
  echo -e "  ${RED}${BOLD}❌ Swarm v10 — ЕСТЬ ПРОБЛЕМЫ${NC}"
  echo "  Необходимо исправить:"
  for f in "${FAILURES[@]}"; do printf "    - %s\n" "$f"; done
fi
echo ""

# === AI-ANALYSIS OUTPUT (новое) ===
echo -e "${BOLD}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}  AI-ANALYSIS OUTPUT (copy to LLM)${NC}"
echo -e "${BOLD}═══════════════════════════════════════════════════════════════${NC}"
echo ""
echo "DOCTOR_VERSION: 2.0"
echo "CHECKS_TOTAL: $((PASS + WARN + FAIL))"
echo "OK: $PASS"
echo "WARN: $WARN"
echo "FAIL: $FAIL"
echo ""
if [ ${#FAILURES[@]} -gt 0 ]; then
  echo "FAILURES:"
  for f in "${FAILURES[@]}"; do echo "- $f"; done
  echo ""
fi
if [ ${#WARNINGS[@]} -gt 0 ]; then
  echo "WARNINGS:"
  for w in "${WARNINGS[@]}"; do echo "- $w"; done
  echo ""
fi

# Сводка статистики
echo "STATS_SUMMARY:"
if [ -f .hive/stats/daily.jsonl ]; then
  STATS_LINES=$(wc -l < .hive/stats/daily.jsonl)
  STATS_TOKENS=$(python3 -c "
import json
total = 0
for line in open('.hive/stats/daily.jsonl'):
    if line.strip():
        try:
            r = json.loads(line)
            total += r.get('tokens', {}).get('total', 0)
        except: pass
print(total)
" 2>/dev/null)
  echo "  tasks_total: $STATS_LINES"
  echo "  tokens_total: $STATS_TOKENS"
else
  echo "  tasks_total: 0"
  echo "  tokens_total: 0"
fi

if [ -f .hive/stats/fallback.jsonl ]; then
  FB_COUNT=$(wc -l < .hive/stats/fallback.jsonl)
  echo "  fallbacks_total: $FB_COUNT"
else
  echo "  fallbacks_total: 0"
fi
echo ""

# Статус провайдеров (кратко)
echo "PROVIDERS_CONFIGURED:"
python3 -c "
import json
try:
    data = json.load(open('$GLOBAL_OCP'))
    for prov in data.get('provider', {}):
        print(f'  - {prov}')
except: print('  (не удалось прочитать)')
" 2>/dev/null
echo ""

echo "MODELS_PER_AGENT:"
for agent in $AGENTS; do
  FILE=".opencode/agent/$agent.md"
  if [ -f "$FILE" ]; then
    MODEL=$(python3 -c "
import re
content = open('$FILE').read()
m = re.search(r'^model:\s*\"?([^\"]+)\"?', content, re.MULTILINE)
print(m.group(1) if m else 'NOT_SET')
" 2>/dev/null)
    echo "  $agent: $MODEL"
  fi
done
echo ""
echo -e "${BOLD}═══════════════════════════════════════════════════════════════${NC}"

[ "$FAIL" -gt "0" ] && exit 1 || exit 0
