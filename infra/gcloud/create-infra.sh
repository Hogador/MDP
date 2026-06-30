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
gcloud sql instances create mdaopay-db \
  --database-version POSTGRES_14 \
  --tier db-f1-micro \
  --region "$REGION" \
  --project "$PROJECT_ID" \
  || echo "Instance may already exist, continuing..."

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
