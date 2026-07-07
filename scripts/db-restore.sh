#!/usr/bin/env bash
# ponytail: restore from a compressed SQL backup.
set -euo pipefail

BACKUP_FILE="${1:-}"
if [ -z "$BACKUP_FILE" ]; then
  echo "Usage: $0 <backup-file.sql.gz>"
  echo ""
  echo "Available backups:"
  ls -1 ./backups/mdaopay_*.sql.gz 2>/dev/null || echo "  (none in ./backups/)"
  exit 1
fi

if [ ! -f "$BACKUP_FILE" ]; then
  echo "ERROR: File not found: $BACKUP_FILE"
  exit 1
fi

if [ -z "${DATABASE_URL:-}" ]; then
  echo "ERROR: DATABASE_URL not set"
  exit 1
fi

echo "WARNING: This will OVERWRITE the database at \$DATABASE_URL"
echo "  Target: $(echo "$DATABASE_URL" | sed 's/\/\/.*@/\/\/user@/')"
echo "  Backup: $BACKUP_FILE ($(du -h "$BACKUP_FILE" | cut -f1))"
read -rp "Continue? [y/N] " confirm
if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
  echo "Aborted."
  exit 0
fi

echo "Restoring ..."
gunzip -c "$BACKUP_FILE" | psql "$DATABASE_URL"
echo "Restore complete."
