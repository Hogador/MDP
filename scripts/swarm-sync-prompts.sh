#!/usr/bin/env bash
# swarm-sync-prompts.sh — синхронизация .hive/prompts/ → .opencode/agent/
# Запускать после правки промтов в .hive/prompts/
#
# Важно: поле tools НЕ используется — mode: primary уже даёт coordinator
# доступ к task tool, mode: subagent его ограничивает.

set -euo pipefail

make_agent() {
  local agent_name="$1"
  local description="$2"
  local mode="$3"
  local prompt_file=".hive/prompts/$agent_name/system.md"
  local output_file=".opencode/agent/$agent_name.md"

  if [ ! -f "$prompt_file" ]; then
    echo "  [SKIP] $prompt_file не найден"
    return 1
  fi

  {
    echo "---"
    echo "description: \"$description\""
    echo "mode: $mode"
    echo "---"
    echo ""
    cat "$prompt_file"
  } > "$output_file"

  echo "  [OK] synced $agent_name"
}

echo "Синхронизация промтов .hive/prompts/ → .opencode/agent/..."

make_agent "coordinator" "Главный роутер сворма MDAOPay v9.0 — вызывает других агентов через task" "primary"
make_agent "context-resolver" "Подгрузка контекста + анализ радиуса (--impact)" "subagent"
make_agent "product-gate" "Сверка с VISION.md и PRD" "subagent"
make_agent "researcher" "5 режимов: security/architecture/performance/ux/devops" "subagent"
make_agent "architect" "Архитектурные решения по radius" "subagent"
make_agent "implementer" "TDD + forge build / gradlew" "subagent"
make_agent "code-reviewer" "Red Team критик (Ponytail)" "subagent"
make_agent "verifier" "3 режима: build/logic/requirements" "subagent"
make_agent "adr-writer" "Фиксация свершившегося факта" "subagent"
make_agent "lessons-learned" "Tier 2 куратор памяти" "subagent"
make_agent "evolution-manager" "Чистка по триггерам — только архивация" "subagent"

echo ""
echo "Готово. Все 11 агентов синхронизированы (без поля tools)."
