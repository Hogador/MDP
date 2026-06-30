#!/usr/bin/env bash
# Показывает изменившиеся файлы с последнего аудита
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

LAST_AUDIT=$(cat docs/orchestration/.last-audit-hash 2>/dev/null || echo "HEAD~1")

echo "Files changed since last audit ($LAST_AUDIT):"
git diff --name-only $LAST_AUDIT HEAD 2>/dev/null | grep -E '\.(kt|ts|sol)$' || echo "(none)"

# Обновить hash после аудита (раскомментировать для auto-update)
# git rev-parse HEAD > docs/orchestration/.last-audit-hash
