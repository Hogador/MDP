#!/usr/bin/env bash
# swarm-sync-prompts.sh — синхронизация .hive/prompts/ → .opencode/agent/
# СОХРАНЯЕТ model: (читает из текущего .opencode/agent/*.md если есть)

set -euo pipefail

# Модели по умолчанию (используются если в .opencode/agent/*.md нет model:)
declare -A DEFAULT_MODELS=(
    ["coordinator"]="sambanova/DeepSeek-V3.1"
    ["context-resolver"]="groq/llama-3.3-70b-versatile"
    ["product-gate"]="groq/llama-3.3-70b-versatile"
    ["researcher"]="sambanova/DeepSeek-V3.1"
    ["architect"]="sambanova/DeepSeek-V3.1"
    ["implementer"]="mistral/codestral-latest"
    ["code-reviewer"]="sambanova/DeepSeek-V3.1"
    ["verifier"]="sambanova/DeepSeek-V3.1"
    ["adr-writer"]="mistral/codestral-latest"
    ["lessons-learned"]="groq/llama-3.3-70b-versatile"
    ["evolution-manager"]="groq/llama-3.3-70b-versatile"
)

declare -A DESCRIPTIONS=(
    ["coordinator"]="Coordinator v11 + ZCode + Strict + Steer + Logging"
    ["context-resolver"]="Подгрузка контекста + --impact"
    ["product-gate"]="Сверка с VISION"
    ["researcher"]="5 режимов + edge cases"
    ["architect"]="3 альтернативы + interview + edge cases"
    ["implementer"]="TDD + ACI + refactor mode"
    ["code-reviewer"]="Red Team"
    ["verifier"]="3 режима"
    ["adr-writer"]="Фиксация факта"
    ["lessons-learned"]="Tier 2 + session summary"
    ["evolution-manager"]="Чистка по триггерам"
)

declare -A MODES=(
    ["coordinator"]="primary"
    ["context-resolver"]="subagent"
    ["product-gate"]="subagent"
    ["researcher"]="subagent"
    ["architect"]="subagent"
    ["implementer"]="subagent"
    ["code-reviewer"]="subagent"
    ["verifier"]="subagent"
    ["adr-writer"]="subagent"
    ["lessons-learned"]="subagent"
    ["evolution-manager"]="subagent"
)

echo "Синхронизация .hive/prompts/ → .opencode/agent/..."

for agent in "${!DEFAULT_MODELS[@]}"; do
    src=".hive/prompts/$agent/system.md"
    dst=".opencode/agent/$agent.md"
    
    if [ ! -f "$src" ]; then
        echo "  [SKIP] $agent: нет исходника"
        continue
    fi
    
    # Пытаемся сохранить текущую model: из существующего файла
    CURRENT_MODEL=""
    if [ -f "$dst" ]; then
        CURRENT_MODEL=$(grep -E '^model:' "$dst" 2>/dev/null | head -1 | sed 's/^model:[[:space:]]*//' | tr -d '"' || echo "")
    fi
    
    # Если не удалось — используем default
    if [ -z "$CURRENT_MODEL" ]; then
        CURRENT_MODEL="${DEFAULT_MODELS[$agent]}"
    fi
    
    # Собираем файл
    {
        echo "---"
        echo "description: \"${DESCRIPTIONS[$agent]}\""
        echo "mode: ${MODES[$agent]}"
        echo "model: \"$CURRENT_MODEL\""
        echo "---"
        echo ""
        cat "$src"
    } > "$dst"
    
    echo "  [OK] $agent → $CURRENT_MODEL"
done

echo ""
echo "Готово. Все 11 агентов синхронизированы с model: в frontmatter."
