#!/usr/bin/env bash
# ============================================================
# swarm-model.sh — Local model orchestrator
# ============================================================
# Load/unload/status for local LLM models via llama-server.
# Only ONE model in memory at a time (no exceptions).
#
# Usage:
#   ./swarm-model.sh load qwen       # Qwen2.5-Coder-7B on :8080
#   ./swarm-model.sh load qwythos    # Qwythos-9B on :8081
#   ./swarm-model.sh unload          # Kill all local llama-server
#   ./swarm-model.sh status          # What's running
# ============================================================
set -euo pipefail

MODELS_DIR="${MODELS_DIR:-/home/ekzent/models}"
QWEN_GGUF="${QWEN_GGUF:-qwen2.5-coder-7b-instruct-q5_k_m.gguf}"
QWYTHOS_GGUF="${QWYTHOS_GGUF:-Qwythos-9B-Claude-Mythos-5-1M-Q4_K_M.gguf}"
QWEN_PORT="${QWEN_PORT:-8080}"
QWYTHOS_PORT="${QWYTHOS_PORT:-8081}"
QWEN_CTX="${QWEN_CTX:-4096}"
QWYTHOS_CTX="${QWYTHOS_CTX:-4096}"
QWEN_GPU_L="${QWEN_GPU_L:-0}"
QWYTHOS_GPU_L="${QWYTHOS_GPU_L:-0}"

# ── helpers ────────────────────────────────────────────
_llama_pids() { pgrep -x llama-server 2>/dev/null || true; }
_llama_count() { _llama_pids | wc -l; }

_stop_all() {
    local pids
    pids=$(_llama_pids)
    if [[ -z "$pids" ]]; then
        return 0
    fi
    echo "→ Останавливаю llama-server (PIDs: $(echo $pids | tr '\n' ' '))..."
    kill $pids 2>/dev/null || true
    # wait up to 5s for graceful shutdown
    for _ in $(seq 1 5); do
        if _llama_count -eq 0 2>/dev/null; then break; fi
        sleep 1
    done
    # force kill remaining
    pids=$(_llama_pids)
    if [[ -n "$pids" ]]; then
        kill -9 $pids 2>/dev/null || true
    fi
    echo "✓ Остановлено"
}

_assert_model() {
    local file="$1"
    if [[ ! -f "$MODELS_DIR/$file" ]]; then
        echo "✗ Ошибка: модель не найдена: $MODELS_DIR/$file"
        exit 1
    fi
}

_start_qwen() {
    _assert_model "$QWEN_GGUF"
    _stop_all
    echo "→ Загружаю Qwen2.5-Coder-7B на порт $QWEN_PORT..."
    # ponytail: CPU-only by default. Add --n-gpu-layers N for GPU offload.
    nohup llama-server \
        -m "$MODELS_DIR/$QWEN_GGUF" \
        --port "$QWEN_PORT" \
        --host 127.0.0.1 \
        -c "$QWEN_CTX" \
        --no-mmap \
        --mlock \
        > /tmp/llama-qwen.log 2>&1 &
    echo "✓ Qwen2.5-Coder-7B запущен на :$QWEN_PORT"
    echo "  Лог: /tmp/llama-qwen.log"
    echo "  Модель: qwen2.5-coder-7b-instruct-q5_k_m"
    echo ""
    echo "  Статус:"
    echo "    curl -s http://127.0.0.1:$QWEN_PORT/v1/models | python3 -m json.tool"
    echo ""
    echo "  Тест:"
    echo "    curl -s http://127.0.0.1:$QWEN_PORT/v1/chat/completions \\"
    echo "      -H 'Content-Type: application/json' \\"
    echo "      -d '{\"model\":\"qwen2.5-coder-7b-instruct-q5_k_m\",\"messages\":[{\"role\":\"user\",\"content\":\"1+1\"}],\"max_tokens\":10}'"
}

_start_qwythos() {
    _assert_model "$QWYTHOS_GGUF"
    _stop_all
    echo "→ Загружаю Qwythos-9B на порт $QWYTHOS_PORT..."
    nohup llama-server \
        -m "$MODELS_DIR/$QWYTHOS_GGUF" \
        --port "$QWYTHOS_PORT" \
        --host 127.0.0.1 \
        -c "$QWYTHOS_CTX" \
        --no-mmap \
        --mlock \
        > /tmp/llama-qwythos.log 2>&1 &
    echo "✓ Qwythos-9B запущен на :$QWYTHOS_PORT"
    echo "  Лог: /tmp/llama-qwythos.log"
    echo "  Модель: Qwythos-9B-Claude-Mythos-5-1M-Q4_K_M"
    echo ""
    echo "  Статус:"
    echo "    curl -s http://127.0.0.1:$QWYTHOS_PORT/v1/models | python3 -m json.tool"
}

_status() {
    local pids
    pids=$(_llama_pids)
    if [[ -z "$pids" ]]; then
        echo "● Модели не загружены (llama-server не запущен)"
        echo ""
        echo "  Доступные модели:"
        echo "    ./swarm-model.sh load qwen     → Qwen2.5-Coder-7B на :$QWEN_PORT"
        echo "    ./swarm-model.sh load qwythos  → Qwythos-9B на :$QWYTHOS_PORT"
        return 0
    fi
    echo "● llama-server запущен:"
    for pid in $pids; do
        local port
        port=$(ps -p "$pid" -o args= 2>/dev/null | grep -oP '--port \K\d+' || echo "?")
        local model
        model=$(ps -p "$pid" -o args= 2>/dev/null | grep -oP '\-m [^ ]+' | head -1 || echo "?")
        local uptime_sec
        uptime_sec=$(ps -p "$pid" -o etimes= 2>/dev/null | tr -d ' ' || echo "?")
        echo "  PID $pid — порт $port — модель $model — uptime ${uptime_sec}с"
    done
    echo ""
    echo "  Память: всего 1 модель (ограничение не более одной)"
}

# ── commands ──────────────────────────────────────────
case "${1:-}" in
    load)
        case "${2:-}" in
            qwen)
                _start_qwen
                _status
                ;;
            qwythos)
                _start_qwythos
                _status
                ;;
            *)
                echo "Использование: $0 load {qwen|qwythos}"
                echo "  qwen     — Qwen2.5-Coder-7B (кодинг)"
                echo "  qwythos  — Qwythos-9B (аудит)"
                exit 1
                ;;
        esac
        ;;
    unload)
        _stop_all
        _status
        ;;
    status)
        _status
        ;;
    *)
        echo "Использование: $0 {load|unload|status}"
        echo ""
        echo "  load qwen     — Загрузить Qwen2.5-Coder-7B на :$QWEN_PORT"
        echo "  load qwythos  — Загрузить Qwythos-9B на :$QWYTHOS_PORT"
        echo "  unload        — Выгрузить все модели"
        echo "  status        — Статус загруженных моделей"
        echo ""
        echo "Переменные окружения:"
        echo "  MODELS_DIR   (по умолчанию: $MODELS_DIR)"
        echo "  QWEN_PORT    (по умолчанию: $QWEN_PORT)"
        echo "  QWYTHOS_PORT (по умолчанию: $QWYTHOS_PORT)"
        exit 0
        ;;
esac
