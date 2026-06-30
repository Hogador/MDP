#!/bin/bash
set -euo pipefail

# Usage: ./backup-restore.sh <backup_file.sql.gz>
# Restores a PostgreSQL backup into the running db container.

if [ $# -ne 1 ]; then
  echo "Usage: $0 <backup_file.sql.gz>" >&2
  exit 1
fi

BACKUP="$1"
CONTAINER=$(docker ps --filter "name=postgres" --format "{{.Names}}" | head -1)

if [ -z "$CONTAINER" ]; then
  echo "Error: no running postgres container found" >&2
  exit 1
fi

echo "Restoring $BACKUP into $CONTAINER..."
gunzip -c "$BACKUP" | docker exec -i "$CONTAINER" psql -U mdaopay
echo "Restore complete."
