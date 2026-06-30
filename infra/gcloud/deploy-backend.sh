#!/bin/bash
set -euo pipefail

# Deploy MDAOPay backend to Cloud Run
# Usage: PROJECT_ID=mdaopay DATABASE_URL=postgres://... CHAIN_ID=137 ./deploy-backend.sh
#
# Prerequisites: ./create-infra.sh has been run once.

PROJECT_ID="${PROJECT_ID:-mdaopay}"
REGION="${REGION:-europe-west1}"
IMAGE_TAG="${IMAGE_TAG:-latest}"

echo "--- Building and pushing image (gcr.io/$PROJECT_ID/mdaopay-backend:$IMAGE_TAG) ---"
gcloud builds submit \
  --tag "gcr.io/$PROJECT_ID/mdaopay-backend:$IMAGE_TAG" \
  backend/ \
  --project "$PROJECT_ID"

echo "--- Deploying Cloud Run service ---"
gcloud run deploy mdaopay-backend \
  --image "gcr.io/$PROJECT_ID/mdaopay-backend:$IMAGE_TAG" \
  --platform managed \
  --region "$REGION" \
  --allow-unauthenticated \
  --memory 1Gi \
  --cpu 1 \
  --min-instances 0 \
  --max-instances 10 \
  --concurrency 80 \
  --set-env-vars "DATABASE_URL=${DATABASE_URL:?DATABASE_URL is required},CHAIN_ID=${CHAIN_ID:?CHAIN_ID is required}" \
  --set-secrets "PAYMASTER_PRIVATE_KEY=paymaster-key:latest" \
  --project "$PROJECT_ID"

echo "--- Done ---"
echo "Service URL: $(gcloud run services describe mdaopay-backend --region $REGION --project $PROJECT_ID --format='value(status.url)')"
