#!/bin/bash
set -euo pipefail

# Bootstrap MDAOPay GCP infrastructure (run once).
# Usage: PROJECT_ID=mdaopay PAYMASTER_PRIVATE_KEY=0x... ./create-infra.sh

PROJECT_ID="${PROJECT_ID:-mdaopay}"
REGION="${REGION:-europe-west1}"

echo "--- Enabling required GCP APIs ---"
gcloud services enable \
  run.googleapis.com \
  sqladmin.googleapis.com \
  secretmanager.googleapis.com \
  cloudbuild.googleapis.com \
  --project "$PROJECT_ID"

echo "--- Creating Cloud SQL instance (PostgreSQL 14, db-f1-micro) ---"
# F-124: testnet bootstrap — single zone, db-f1-micro
gcloud sql instances create mdaopay-db \
  --database-version POSTGRES_14 \
  --tier db-f1-micro \
  --region "$REGION" \
  --project "$PROJECT_ID" \
  || echo "Instance may already exist, continuing..."

echo "--- Creating production Cloud SQL instance with HA (if PRODUCTION=1) ---"
if [ "${PRODUCTION:-0}" = "1" ]; then
  gcloud sql instances create mdaopay-db-prod \
    --database-version POSTGRES_14 \
    --tier db-custom-2-7680 \
    --region "$REGION" \
    --availability-type REGIONAL \
    --backup-start-time 03:00 \
    --enable-point-in-time-recovery \
    --retained-backups-count 30 \
    --retained-transaction-log-days 7 \
    --project "$PROJECT_ID" \
    || echo "Production instance may already exist, continuing..."

  # Cross-region read replica for DR (F-124)
  DR_REGION="${DR_REGION:-europe-west4}"
  gcloud sql instances create mdaopay-db-dr \
    --database-version POSTGRES_14 \
    --region "$DR_REGION" \
    --master-instance-name mdaopay-db-prod \
    --tier db-custom-2-7680 \
    --project "$PROJECT_ID" \
    || echo "DR replica may already exist, continuing..."

  gcloud sql databases create mdaopay \
    --instance mdaopay-db-prod \
    --project "$PROJECT_ID" \
    || echo "Production database may already exist, continuing..."
fi

echo "--- Creating database ---"
gcloud sql databases create mdaopay \
  --instance mdaopay-db \
  --project "$PROJECT_ID" \
  || echo "Database may already exist, continuing..."

echo "--- Storing paymaster-key in Secret Manager ---"
# ponytail: idempotent — if secret exists, creates a new version
echo -n "${PAYMASTER_PRIVATE_KEY:?PAYMASTER_PRIVATE_KEY is required}" | \
  gcloud secrets create paymaster-key \
    --replication-policy="automatic" \
    --data-file=- \
    --project "$PROJECT_ID" \
  || gcloud secrets versions add paymaster-key \
    --data-file=- \
    --project "$PROJECT_ID" <<< "${PAYMASTER_PRIVATE_KEY}"

echo "--- Done ---"
echo "Next: run PROJECT_ID=$PROJECT_ID DATABASE_URL=... CHAIN_ID=... ./deploy-backend.sh"
