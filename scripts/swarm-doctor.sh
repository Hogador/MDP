#!/usr/bin/env bash
# swarm-doctor.sh — проверка здоровья Swarm v9.0
# Запуск: ./scripts/swarm-doctor.sh
# Опции: --verbose (подробный вывод), --fix (попытаться починить .gitkeep)

set -uo pipefail

VERBOSE=0
FIX_MODE=0
for arg in "$@"; do
  case "$arg" in
    --verbose|-v) VERBOSE=1 ;;
    --fix)        FIX_MODE=1 ;;
    --help|-h)
      echo "Usage: $0 [--verbose] [--fix]"
      echo "  --verbose  показывать детали по каждому чек-пойнту"
      echo "  --fix      пересоздать .gitkeep если отсутствует"
      exit 0
      ;;
  esac
done

# Цвета
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

check() {
  # $1 = описание, $2 = условие (0 = success)
  local desc="$1"
  local cond="$2"
  if [ "$cond" = "0" ]; then
    printf "  ${GREEN}[OK]${NC}  %s\n" "$desc"
    PASS=$((PASS + 1))
    [ "$VERBOSE" = "1" ] && [ -n "${3:-}" ] && printf "       %s\n" "$3"
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
  fi
}

check_contains() {
  # $1 = файл, $2 = что искать, $3 = описание
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
  # $1 = путь, $2 = описание
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
echo -e "${BLUE}${BOLD}  Swarm Doctor v9.0 — проверка здоровья MDAOPay${NC}"
echo -e "${BLUE}${BOLD}═══════════════════════════════════════════════════════════════${NC}"
echo ""

# ════════════════════════════════════════════════════════════════════
echo -e "${BOLD}1. СТРУКТУРА КАТАЛОГОВ${NC}"
# ════════════════════════════════════════════════════════════════════

for d in \
  .hive \
  .hive/daily \
  .hive/audit \
  .hive/verifications \
  .hive/escalations \
  .hive/reports \
  .hive/impact \
  .hive/prompts \
  docs \
  docs/adr \
  security \
  scripts \
  bin; do
  check "Каталог $d существует" $([ -d "$d" ]; echo $?)
done

echo ""

# ════════════════════════════════════════════════════════════════════
echo -e "${BOLD}2. ПРОМТЫ 11 АГЕНТОВ${NC}"
# ════════════════════════════════════════════════════════════════════

AGENTS="coordinator context-resolver product-gate researcher architect implementer code-reviewer verifier adr-writer lessons-learned evolution-manager"

for agent in $AGENTS; do
  FILE=".hive/prompts/$agent/system.md"
  if [ ! -f "$FILE" ]; then
    check "Промт $agent" 1
    continue
  fi
  SIZE=$(wc -c < "$FILE")
  LINES=$(wc -l < "$FILE")
  if [ "$SIZE" -lt 500 ]; then
    check "Промт $agent" 1 "файл слишком маленький ($SIZE bytes)"
  else
    check "Промт $agent существует ($LINES строк, $SIZE bytes)" 0
  fi
  # Проверка что промт содержит "СИСТЕМНЫЙ ПРОМТ"
  check_contains "$FILE" "СИСТЕМНЫЙ ПРОМТ" "Промт $agent содержит заголовок системы"
done

echo ""

# ════════════════════════════════════════════════════════════════════
echo -e "${BOLD}3. БАЗОВЫЕ ДОКУМЕНТЫ${NC}"
# ════════════════════════════════════════════════════════════════════

# VISION.md
check "docs/VISION.md существует" $([ -f docs/VISION.md ]; echo $?)
check_contains "docs/VISION.md" "ЗАПРЕЩЁННЫЕ КОМПРОМИССЫ" "VISION.md содержит раздел запрещённых компромиссов"
check_contains "docs/VISION.md" "MDAOPay" "VISION.md содержит имя проекта"
check_contains "docs/VISION.md" "Что МЫ ДЕЛАЕМ" "VISION.md содержит in-scope"
check_contains "docs/VISION.md" "Что МЫ НЕ ДЕЛАЕМ" "VISION.md содержит out-of-scope"

# ROADMAP.md
check "docs/ROADMAP.md существует" $([ -f docs/ROADMAP.md ]; echo $?)
check_contains "docs/ROADMAP.md" "LOCALNET" "ROADMAP.md содержит стадию LOCALNET"
check_contains "docs/ROADMAP.md" "TESTNET" "ROADMAP.md содержит стадию TESTNET"
check_contains "docs/ROADMAP.md" "MAINNET" "ROADMAP.md содержит стадию MAINNET"
check_contains "docs/ROADMAP.md" "F-001" "ROADMAP.md содержит ID задач"

# KNOWLEDGE-BASE.md
check "security/KNOWLEDGE-BASE.md существует" $([ -f security/KNOWLEDGE-BASE.md ]; echo $?)
check_contains "security/KNOWLEDGE-BASE.md" "KB-" "KB содержит формат ID правил"
check_contains "security/KNOWLEDGE-BASE.md" "SOL" "KB содержит домен SOL"
check_contains "security/KNOWLEDGE-BASE.md" "MOB" "KB содержит домен MOB"
check_contains "security/KNOWLEDGE-BASE.md" "verifier --logic" "KB упоминает verifier --logic"

# RISK-REGISTRY.md
check "security/RISK-REGISTRY.md существует" $([ -f security/RISK-REGISTRY.md ]; echo $?)
check_contains "security/RISK-REGISTRY.md" "Severity" "RISK-REGISTRY содержит Severity"
check_contains "security/RISK-REGISTRY.md" "Mitigation" "RISK-REGISTRY содержит Mitigation"
check_contains "security/RISK-REGISTRY.md" "Critical" "RISK-REGISTRY содержит Critical severity"

echo ""

# ════════════════════════════════════════════════════════════════════
echo -e "${BOLD}4. RUNTIME-ФАЙЛЫ${NC}"
# ════════════════════════════════════════════════════════════════════

# config.json
check ".hive/config.json существует" $([ -f .hive/config.json ]; echo $?)
if [ -f .hive/config.json ]; then
  if command -v python3 >/dev/null 2>&1; then
    python3 -c "import json; json.load(open('.hive/config.json'))" 2>/dev/null
    check "config.json валидный JSON" $? 
  elif command -v jq >/dev/null 2>&1; then
    jq empty .hive/config.json 2>/dev/null
    check "config.json валидный JSON" $?
  else
    check_warn "config.json валидность JSON" 1 "ни python3, ни jq не найдены"
  fi
  check_contains ".hive/config.json" '"version": "9.0"' "config.json version = 9.0"
  check_contains ".hive/config.json" "routing" "config.json содержит routing"
  check_contains ".hive/config.json" "loop_detection" "config.json содержит loop_detection"
  check_contains ".hive/config.json" "context_priorities" "config.json содержит context_priorities"
  check_contains ".hive/config.json" "evolution_manager" "config.json содержит evolution_manager"
  check_contains ".hive/config.json" "audit" "config.json содержит routing.audit"
  check_contains ".hive/config.json" "feature" "config.json содержит routing.feature"
  check_contains ".hive/config.json" "devops" "config.json содержит researcher.devops (5-й режим)"
fi

# state.json
check ".hive/state.json существует" $([ -f .hive/state.json ]; echo $?)
if [ -f .hive/state.json ]; then
  if command -v python3 >/dev/null 2>&1; then
    python3 -c "import json; json.load(open('.hive/state.json'))" 2>/dev/null
    check "state.json валидный JSON" $?
  elif command -v jq >/dev/null 2>&1; then
    jq empty .hive/state.json 2>/dev/null
    check "state.json валидный JSON" $?
  fi
  check_contains ".hive/state.json" "loop_detection" "state.json содержит loop_detection"
  check_contains ".hive/state.json" "repeat_count" "state.json содержит repeat_count"
fi

# memories.jsonl
check ".hive/memories.jsonl существует" $([ -f .hive/memories.jsonl ]; echo $?)
if [ -f .hive/memories.jsonl ]; then
  MLINES=$(wc -l < .hive/memories.jsonl)
  check "memories.jsonl пустой (Tier 2, ещё не наполнен)" $([ "$MLINES" = "0" ]; echo $?) "$MLINES строк"
fi

echo ""

# ════════════════════════════════════════════════════════════════════
echo -e "${BOLD}5. ADR СТРУКТУРА${NC}"
# ════════════════════════════════════════════════════════════════════

check "docs/adr/README.md существует" $([ -f docs/adr/README.md ]; echo $?)
check "docs/adr/ADR-000-template.md существует" $([ -f docs/adr/ADR-000-template.md ]; echo $?)
check_contains "docs/adr/README.md" "Когда создавать ADR" "ADR README содержит гайд"
check_contains "docs/adr/ADR-000-template.md" "Контекст" "ADR-шаблон содержит секцию Контекст"
check_contains "docs/adr/ADR-000-template.md" "Решение" "ADR-шаблон содержит секцию Решение"
check_contains "docs/adr/ADR-000-template.md" "Альтернативы" "ADR-шаблон содержит секцию Альтернативы"
check_contains "docs/adr/ADR-000-template.md" "Верификация" "ADR-шаблон содержит секцию Верификация"

echo ""

# ════════════════════════════════════════════════════════════════════
echo -e "${BOLD}6. CLI-РОУТЕР${NC}"
# ════════════════════════════════════════════════════════════════════

for cli in swarm-router swarm-audit swarm-feature swarm-bug swarm-roadmap; do
  FILE="bin/$cli"
  check "bin/$cli существует" $([ -f "$FILE" ]; echo $?)
  if [ -f "$FILE" ]; then
    check "bin/$cli исполняемый" $([ -x "$FILE" ]; echo $?)
    check_contains "$FILE" "swarm-router" "bin/$cli ссылается на swarm-router"
  fi
done

# Проверка функциональности swarm-router
if [ -f bin/swarm-router ] && [ -x bin/swarm-router ]; then
  check_contains "bin/swarm-router" "mode=audit" "swarm-router поддерживает mode=audit"
  check_contains "bin/swarm-router" "mode=feature" "swarm-router поддерживает mode=feature"
  check_contains "bin/swarm-router" "mode=bug" "swarm-router поддерживает mode=bug"
  check_contains "bin/swarm-router" "mode=explain" "swarm-router поддерживает mode=explain"
  check_contains "bin/swarm-router" "mode=roadmap" "swarm-router поддерживает mode=roadmap"
  check_contains "bin/swarm-router" "dry-run" "swarm-router поддерживает --dry-run"

  # Реальный запуск --help
  bin/swarm-router --help >/dev/null 2>&1
  check "swarm-router --help запускается без ошибки" $?

  # Реальный запуск dry-run audit
  bin/swarm-router --mode=audit --dry-run >/dev/null 2>&1
  check "swarm-router --mode=audit --dry-run запускается без ошибки" $?
fi

echo ""

# ════════════════════════════════════════════════════════════════════
echo -e "${BOLD}7. .gitignore${NC}"
# ════════════════════════════════════════════════════════════════════

check ".gitignore существует" $([ -f .gitignore ]; echo $?)
if [ -f .gitignore ]; then
  check_contains ".gitignore" "Swarm v9.0" ".gitignore содержит swarm-секцию"
  check_contains ".gitignore" ".hive/state.json" ".gitignore исключает state.json"
  check_contains ".gitignore" ".hive/escalations/" ".gitignore исключает escalations"
  check_contains ".gitignore" ".hive/audit/" ".gitignore исключает audit"
  check_contains ".gitignore" ".hive/daily/" ".gitignore исключает daily"
fi

echo ""

# ════════════════════════════════════════════════════════════════════
echo -e "${BOLD}8. ОТСУТСТВИЕ ХВОСТОВ СТАРОГО СВАРМА${NC}"
# ════════════════════════════════════════════════════════════════════

check_not_exists "AGENTS.md" "Старый AGENTS.md удалён"
check_not_exists "security/ERRORS-MEMORY.md" "Старый ERRORS-MEMORY.md удалён"
check_not_exists "security/FINDINGS.md" "Старый FINDINGS.md удалён"
check_not_exists "security/FIX-PATTERNS.md" "Старый FIX-PATTERNS.md удалён"
check_not_exists "security/ANTI-PATTERNS.md" "Старый ANTI-PATTERNS.md удалён"
check_not_exists "security/TEST-COVERAGE-MAP.md" "Старый TEST-COVERAGE-MAP.md удалён"
check_not_exists "Makefile.audit" "Старый Makefile.audit удалён"
check_not_exists "ci.yml.additions" "Старый ci.yml.additions удалён"
check_not_exists "scripts/prompts" "Старый scripts/prompts/ удалён"
check_not_exists "scripts/audit-menu.sh" "Старый audit-menu.sh удалён"
check_not_exists "scripts/spawn-full-audit.sh" "Старый spawn-full-audit.sh удалён"

echo ""

# ════════════════════════════════════════════════════════════════════
echo -e "${BOLD}9. СПЕЦИФИКАЦИЯ АГЕНТОВ (ключевые принципы)${NC}"
# ════════════════════════════════════════════════════════════════════

# Coordinator: русский язык + 5 режимов
check_contains ".hive/prompts/coordinator/system.md" "только на русском" "Coordinator: русификация"
check_contains ".hive/prompts/coordinator/system.md" "mode=audit" "Coordinator: режим audit"
check_contains ".hive/prompts/coordinator/system.md" "mode=feature" "Coordinator: режим feature"
check_contains ".hive/prompts/coordinator/system.md" "mode=bug" "Coordinator: режим bug"
check_contains ".hive/prompts/coordinator/system.md" "mode=explain" "Coordinator: режим explain"
check_contains ".hive/prompts/coordinator/system.md" "mode=roadmap" "Coordinator: режим roadmap"
check_contains ".hive/prompts/coordinator/system.md" "ПРИВЕТСТВИЕ" "Coordinator: секция приветствия"
check_contains ".hive/prompts/coordinator/system.md" "РАСПОЗНАВАНИЕ ЕСТЕСТВЕННЫХ КОМАНД" "Coordinator: понимает естественный язык"
check_contains ".hive/prompts/coordinator/system.md" "ПРОГРЕСС И ПРОЗРАЧНОСТЬ" "Coordinator: показывает прогресс"
check_contains ".hive/prompts/coordinator/system.md" "/swarm" "Coordinator: поддерживает команду /swarm"
check_contains ".hive/prompts/coordinator/system.md" "внедри" "Coordinator: триггер внедри"
check_contains ".hive/prompts/coordinator/system.md" "аудит" "Coordinator: триггер аудит"
check_contains ".hive/prompts/coordinator/system.md" "баг" "Coordinator: триггер баг"
check_contains ".hive/prompts/coordinator/system.md" "объясни" "Coordinator: триггер объясни"
check_contains ".hive/prompts/coordinator/system.md" "LOOP DETECTION" "Coordinator: loop detection"
check_contains ".hive/prompts/coordinator/system.md" "CONTEXT RESET" "Coordinator: context reset"
check_contains ".hive/prompts/coordinator/system.md" "ИТОГОВЫЙ ОТЧЁТ" "Coordinator: итоговый отчёт"

# Context-resolver: --impact режим
check_contains ".hive/prompts/context-resolver/system.md" "impact" "Context-resolver: режим --impact"
check_contains ".hive/prompts/context-resolver/system.md" "radius" "Context-resolver: классификация radius"
check_contains ".hive/prompts/context-resolver/system.md" "local" "Context-resolver: radius=local"
check_contains ".hive/prompts/context-resolver/system.md" "cross-module" "Context-resolver: radius=cross-module"

# Product-gate: VISION
check_contains ".hive/prompts/product-gate/system.md" "VISION.md" "Product-gate: сверка с VISION"
check_contains ".hive/prompts/product-gate/system.md" "APPROVED" "Product-gate: APPROVED decision"
check_contains ".hive/prompts/product-gate/system.md" "REJECTED" "Product-gate: REJECTED decision"

# Researcher: 5 режимов
check_contains ".hive/prompts/researcher/system.md" "security" "Researcher: режим security"
check_contains ".hive/prompts/researcher/system.md" "architecture" "Researcher: режим architecture"
check_contains ".hive/prompts/researcher/system.md" "performance" "Researcher: режим performance"
check_contains ".hive/prompts/researcher/system.md" "ux" "Researcher: режим ux"
check_contains ".hive/prompts/researcher/system.md" "devops" "Researcher: режим devops (5-й, новый)"

# Architect: radius-логика + gap-analysis
check_contains ".hive/prompts/architect/system.md" "radius" "Architect: логика по radius"
check_contains ".hive/prompts/architect/system.md" "gap-analysis" "Architect: режим --gap-analysis"
check_contains ".hive/prompts/architect/system.md" "Red Team" "Architect: Red Team для medium+"

# Implementer: TDD + сам запускает build
check_contains ".hive/prompts/implementer/system.md" "TDD" "Implementer: TDD обязателен"
check_contains ".hive/prompts/implementer/system.md" "forge build" "Implementer: сам вызывает forge build"
check_contains ".hive/prompts/implementer/system.md" "loop" "Implementer: осведомлён о loop detection"
check_contains ".hive/prompts/implementer/system.md" "не верифицируешь свой код" "Implementer: запрет self-verification"

# Code-reviewer: Red Team + severity
check_contains ".hive/prompts/code-reviewer/system.md" "Red Team" "Code-reviewer: Red Team"
check_contains ".hive/prompts/code-reviewer/system.md" "blocker" "Code-reviewer: severity blocker"
check_contains ".hive/prompts/code-reviewer/system.md" "major" "Code-reviewer: severity major"
check_contains ".hive/prompts/code-reviewer/system.md" "Ponytail" "Code-reviewer: стиль Ponytail"

# Verifier: 3 режима + KB
check_contains ".hive/prompts/verifier/system.md" "build" "Verifier: режим --build"
check_contains ".hive/prompts/verifier/system.md" "logic" "Verifier: режим --logic"
check_contains ".hive/prompts/verifier/system.md" "requirements" "Verifier: режим --requirements"
check_contains ".hive/prompts/verifier/system.md" "KNOWLEDGE-BASE" "Verifier: пишет в KB"
check_contains ".hive/prompts/verifier/system.md" "НЕ пишешь правила" "Verifier --build НЕ пишет KB (факт, не знание)"

# ADR-writer: факт, не план
check_contains ".hive/prompts/adr-writer/system.md" "свершившийся факт" "ADR-writer: фиксация факта"
check_contains ".hive/prompts/adr-writer/system.md" "Supersedes" "ADR-writer: поддержка supersede"

# Lessons-learned: Tier 2 + куратор
check_contains ".hive/prompts/lessons-learned/system.md" "Tier 2" "Lessons-learned: Tier 2 куратор"
check_contains ".hive/prompts/lessons-learned/system.md" "memories.jsonl" "Lessons-learned: пишет в memories.jsonl"

# Evolution-manager: триггеры + архивация
check_contains ".hive/prompts/evolution-manager/system.md" "trigger_adr_count_gt" "Evolution: триггер по ADR count"
check_contains ".hive/prompts/evolution-manager/system.md" "trigger_rules_count_gt" "Evolution: триггер по KB count"
check_contains ".hive/prompts/evolution-manager/system.md" "архив" "Evolution: только архивация"

echo ""

# ════════════════════════════════════════════════════════════════════
echo -e "${BOLD}10. .gitkeep В ПУСТЫХ КАТАЛОГАХ${NC}"
# ════════════════════════════════════════════════════════════════════

GITKEEP_DIRS=".hive/daily .hive/audit .hive/verifications .hive/escalations .hive/reports .hive/impact"
for d in $GITKEEP_DIRS; do
  if [ -d "$d" ]; then
    # Каталог должен быть пустым (только .gitkeep) или содержать только runtime-файлы
    FILECOUNT=$(ls -A "$d" 2>/dev/null | grep -v '^\.gitkeep$' | wc -l)
    if [ "$FILECOUNT" = "0" ]; then
      if [ -f "$d/.gitkeep" ]; then
        check "$d/.gitkeep присутствует (пустой каталог)" 0
      else
        check_warn "$d/.gitkeep отсутствует" 1
        if [ "$FIX_MODE" = "1" ]; then
          touch "$d/.gitkeep"
          echo "       → [fix] создан $d/.gitkeep"
        fi
      fi
    else
      check "$d содержит файлы (runtime active)" 0
    fi
  fi
done

echo ""

# ════════════════════════════════════════════════════════════════════
echo -e "${BOLD}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${BOLD}  ИТОГОВЫЙ ОТЧЁТ${NC}"
echo -e "${BOLD}═══════════════════════════════════════════════════════════════${NC}"
echo ""
printf "  ${GREEN}OK${NC}:   %d\n" "$PASS"
printf "  ${YELLOW}WARN${NC}: %d\n" "$WARN"
printf "  ${RED}FAIL${NC}: %d\n" "$FAIL"
echo ""

if [ "$FAIL" = "0" ] && [ "$WARN" = "0" ]; then
  echo -e "  ${GREEN}${BOLD}✅ Swarm v9.0 — ВСЁ КОРРЕКТНО${NC}"
  echo ""
  echo "  Сворм готов к работе. Можно запускать:"
  echo "    ./bin/swarm-router --help"
  echo "    ./bin/swarm-roadmap --target=testnet --action=gap-analysis"
  echo "    ./bin/swarm-audit --scope=full"
elif [ "$FAIL" = "0" ]; then
  echo -e "  ${YELLOW}${BOLD}⚠️  Swarm v9.0 — РАБОТАЕТ, но есть предупреждения${NC}"
  echo ""
  echo "  Предупреждения можно игнорировать, но рекомендуется исправить."
else
  echo -e "  ${RED}${BOLD}❌ Swarm v9.0 — ЕСТЬ ПРОБЛЕМЫ${NC}"
  echo ""
  echo "  Необходимо исправить следующие FAIL:"
  for f in "${FAILURES[@]}"; do
    printf "    - %s\n" "$f"
  done
  echo ""
  echo "  После исправления запустите снова: ./scripts/swarm-doctor.sh"
fi

echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════════${NC}"

# Exit code: 0 = OK/warn, 1 = FAIL
if [ "$FAIL" -gt "0" ]; then
  exit 1
else
  exit 0
fi
