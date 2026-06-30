#!/usr/bin/env bash
# Переносит VERIFIED findings старше 30 дней в ARCHIVE
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

echo "📦 Compacting findings..."

ARCHIVE_FILE="security/FINDINGS-ARCHIVE.md"
[ ! -f "$ARCHIVE_FILE" ] && echo "# Findings Archive (verified, >30 days old)" > "$ARCHIVE_FILE"

# Найти VERIFIED findings старше 30 дней
CUTOFF_DATE=$(date -d '30 days ago' +%Y-%m-%d 2>/dev/null || date -v-30d +%Y-%m-%d)

# TODO: full implementation needs parsing of lifecycle dates
# Пока просто отчёт
VERIFIED_COUNT=$(grep -c "| VERIFIED |" security/FINDINGS.md 2>/dev/null || echo 0)
echo "VERIFIED findings: $VERIFIED_COUNT"
echo "Cutoff date: $CUTOFF_DATE"
echo "Manual review needed for now. Auto-compaction in v3.1."
