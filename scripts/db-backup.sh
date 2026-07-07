#!/usr/bin/env bash
# ponytail: pg_dump to compressed SQL. Run from cron or manually before deploys.
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-./backups}"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
mkdir -p "$BACKUP_DIR"

if [ -z "${DATABASE_URL:-}" ]; then
  echo "ERROR: DATABASE_URL not set"
  exit 1
fi

BACKUP_FILE="$BACKUP_DIR/mdaopay_$TIMESTAMP.sql.gz"
echo "Backing up to $BACKUP_FILE ..."
pg_dump "$DATABASE_URL" --no-owner --clean | gzip > "$BACKUP_FILE"
echo "Done: $(du -h "$BACKUP_FILE" | cut -f1)"

# Keep last 7 backups
ls -t "$BACKUP_DIR"/mdaopay_*.sql.gz | tail -n +8 | xargs -r rm
echo "Cleaned up old backups (kept 7)"
