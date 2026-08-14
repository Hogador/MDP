#!/bin/bash
# scripts/deploy-testnet.sh
# MDAOPay — BSC Testnet deployment script (v4 — hardened, all review issues fixed)
#
# Usage:
#   BSC_TESTNET_DEPLOYER_KEY=0x... \
#   TESTNET_PAYMASTER_KEY=0x... \
#   TESTNET_SWAP_KEY=0x... \
#   BSCSCAN_API_KEY=... \
#   GNOSIS_SAFE=0x... \
#   TRUSTED_SIGNER=0x... \
#   AWS_REGION=us-east-1 \
#   CF_ACCOUNT_SUBDOMAIN=my-subdomain \
#   RELAY_URL_OVERRIDE=https://... \
#   ./scripts/deploy-testnet.sh
#
# Required tools: forge, cast, jq, docker, aws CLI, wrangler, curl, openssl
#
# CHANGELOG v4:
#   - Fixed #1: `if ! "$POSTGRES_READY"` → `[ "$POSTGRES_READY" != "true" ]`
#   - Fixed #2: Redis readiness check (ping-loop with PONG verification)
#   - Fixed #3: cast abi-encode arrays — pre-flight check + fallback
#   - Fixed #4: --health-start-period 45s + timeout 180 sync
#   - Fixed #5: SWAP_ROUTER_ADDRESS added to .env.testnet.public

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }
log_step()  { echo -e "\n${GREEN}=== $* ===${NC}"; }
log_info "Running from: $REPO_ROOT"

# ============================================================================
# 0. CONSTANTS
# ============================================================================
export EXPECTED_CHAIN_ID=97
RPC_URL="https://data-seed-prebsc-1-s1.binance.org:8545"
ENTRY_POINT="0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789"
USDT_TESTNET="0x337610d27c682E347C9cD60BD4b3b107C9d34dDD"
WBNB_TESTNET="0xae13d989daC2f0dEbFf460aC112a837C89BAa7cd"
SWAP_ROUTER_TESTNET="0x9Ac64Cc6e4415144C455BD8E4837Fea55603e5c3"  # PancakeSwap testnet
BSC_EXPLORER="https://testnet.bscscan.com"

# ============================================================================
# 1. PRE-FLIGHT CHECKS
# ============================================================================
log_step "Pre-flight checks"

for tool in forge cast jq docker aws wrangler curl openssl; do
    command -v "$tool" >/dev/null 2>&1 || { log_error "$tool is required but not installed"; exit 1; }
done
log_info "All required tools available"

[ -n "${BSC_TESTNET_DEPLOYER_KEY:-}" ] || { log_error "BSC_TESTNET_DEPLOYER_KEY not set"; exit 1; }
[ -n "${BSCSCAN_API_KEY:-}" ]          || { log_error "BSCSCAN_API_KEY not set"; exit 1; }
[ -n "${TRUSTED_SIGNER:-}" ]           || { log_error "TRUSTED_SIGNER not set"; exit 1; }
[ -n "${TESTNET_PAYMASTER_KEY:-}" ]    || { log_error "TESTNET_PAYMASTER_KEY not set"; exit 1; }
[ -n "${TESTNET_SWAP_KEY:-}" ]         || { log_error "TESTNET_SWAP_KEY not set"; exit 1; }

[[ "$BSC_TESTNET_DEPLOYER_KEY" =~ ^0x[0-9a-fA-F]{64}$ || "$BSC_TESTNET_DEPLOYER_KEY" =~ ^[0-9a-fA-F]{64}$ ]] \
    || { log_error "BSC_TESTNET_DEPLOYER_KEY must be 64-char hex (with or without 0x prefix)"; exit 1; }

[[ "$TESTNET_PAYMASTER_KEY" =~ ^0x[0-9a-fA-F]{64}$ || "$TESTNET_PAYMASTER_KEY" =~ ^[0-9a-fA-F]{64}$ ]] \
    || { log_error "TESTNET_PAYMASTER_KEY must be 64-char hex"; exit 1; }

[[ "$TESTNET_SWAP_KEY" =~ ^0x[0-9a-fA-F]{64}$ || "$TESTNET_SWAP_KEY" =~ ^[0-9a-fA-F]{64}$ ]] \
    || { log_error "TESTNET_SWAP_KEY must be 64-char hex"; exit 1; }

[[ "$TRUSTED_SIGNER" =~ ^0x[a-fA-F0-9]{40}$ ]] \
    || { log_error "TRUSTED_SIGNER must be 0x-prefixed 40-char hex address"; exit 1; }

# F-035: PAYMASTER and SWAP keys must differ
PAYMASTER_ADDR=$(cast wallet address --private-key "$TESTNET_PAYMASTER_KEY")
SWAP_ADDR=$(cast wallet address --private-key "$TESTNET_SWAP_KEY")
[ "$PAYMASTER_ADDR" != "$SWAP_ADDR" ] \
    || { log_error "TESTNET_PAYMASTER_KEY and TESTNET_SWAP_KEY derive same address (F-035 violation)"; exit 1; }
log_info "Paymaster ($PAYMASTER_ADDR) and Swap ($SWAP_ADDR) keys differ"

GNOSIS_SAFE="${GNOSIS_SAFE:-$(cast wallet address --private-key "$BSC_TESTNET_DEPLOYER_KEY")}"
log_info "Using GNOSIS_SAFE=$GNOSIS_SAFE"

log_info "Pre-flight checks passed"

# ============================================================================
# 2. CHAIN VERIFICATION
# ============================================================================
log_step "Chain verification"

ACTUAL_CHAIN=$(cast chain-id --rpc-url "$RPC_URL" 2>/dev/null || echo "0")
[ "$ACTUAL_CHAIN" = "$EXPECTED_CHAIN_ID" ] \
    || { log_error "RPC returned chain $ACTUAL_CHAIN, expected $EXPECTED_CHAIN_ID (BSC Testnet). ABORTING."; exit 1; }
log_info "Chain ID verified: $ACTUAL_CHAIN (BSC Testnet)"

EP_CODE=$(cast code "$ENTRY_POINT" --rpc-url "$RPC_URL")
[ "$EP_CODE" != "0x" ] && [ -n "$EP_CODE" ] \
    || { log_error "EntryPoint not deployed at $ENTRY_POINT on chain $ACTUAL_CHAIN"; exit 1; }
log_info "EntryPoint verified at $ENTRY_POINT"

DEPLOYER_ADDR=$(cast wallet address --private-key "$BSC_TESTNET_DEPLOYER_KEY")
if ! DEPLOYER_BALANCE=$(cast balance "$DEPLOYER_ADDR" --rpc-url "$RPC_URL" --ether 2>/dev/null); then
    log_error "Cannot fetch deployer balance — RPC unreachable?"
    exit 1
fi
if ! awk "BEGIN{exit !($DEPLOYER_BALANCE > 0.5)}"; then
    log_error "Deployer balance $DEPLOYER_BALANCE BNB < 0.5 BNB minimum (need ~0.5 BNB for 12 contracts)"
    exit 1
fi
log_info "Deployer $DEPLOYER_ADDR balance: $DEPLOYER_BALANCE BNB"

# ============================================================================
# 3. LOAD PERSISTENT SECRETS FROM AWS SECRETS MANAGER
# ============================================================================
log_step "Loading persistent secrets"

JWT_SECRET=$(aws secretsmanager get-secret-value \
    --secret-id mdaopay/testnet/jwt-secret \
    --query SecretString --output text 2>/dev/null || echo "")

if [ -z "$JWT_SECRET" ]; then
    log_warn "First-time setup: generating JWT_SECRET (48 bytes = 64 chars Base64)"
    JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
    aws secretsmanager create-secret \
        --name mdaopay/testnet/jwt-secret \
        --secret-string "$JWT_SECRET" \
        >/dev/null
    log_info "JWT_SECRET stored in AWS Secrets Manager"
else
    log_info "JWT_SECRET loaded from AWS Secrets Manager (existing)"
fi

# C-3: split — one secret per role (JWT signing vs HMAC request signing)
RELAY_JWT_SECRET=$(aws secretsmanager get-secret-value \
    --secret-id mdaopay/testnet/relay-jwt-secret \
    --query SecretString --output text 2>/dev/null || echo "")

if [ -z "$RELAY_JWT_SECRET" ]; then
    RELAY_JWT_SECRET=$(openssl rand -hex 32)
    aws secretsmanager create-secret \
        --name mdaopay/testnet/relay-jwt-secret \
        --secret-string "$RELAY_JWT_SECRET" \
        >/dev/null
    log_info "RELAY_JWT_SECRET stored in AWS Secrets Manager"
else
    log_info "RELAY_JWT_SECRET loaded from AWS Secrets Manager (existing)"
fi

RELAY_HMAC_SECRET=$(aws secretsmanager get-secret-value \
    --secret-id mdaopay/testnet/relay-hmac-secret \
    --query SecretString --output text 2>/dev/null || echo "")

if [ -z "$RELAY_HMAC_SECRET" ]; then
    RELAY_HMAC_SECRET=$(openssl rand -hex 32)
    aws secretsmanager create-secret \
        --name mdaopay/testnet/relay-hmac-secret \
        --secret-string "$RELAY_HMAC_SECRET" \
        >/dev/null
    log_info "RELAY_HMAC_SECRET stored in AWS Secrets Manager"
else
    log_info "RELAY_HMAC_SECRET loaded from AWS Secrets Manager (existing)"
fi

INSURANCE_AUDITOR=$(aws secretsmanager get-secret-value \
    --secret-id mdaopay/testnet/auditor-address \
    --query SecretString --output text 2>/dev/null || echo "")

if [ -z "$INSURANCE_AUDITOR" ]; then
    log_warn "INSURANCE_AUDITOR not set in Secrets Manager — using deployer as placeholder (TESTNET ONLY)"
    INSURANCE_AUDITOR="$DEPLOYER_ADDR"
    log_warn "For mainnet: set real auditor address via 'aws secretsmanager create-secret ...'"
fi
export INSURANCE_AUDITOR_ADDRESS="$INSURANCE_AUDITOR"
log_info "InsuranceFund auditor: $INSURANCE_AUDITOR"

FCM_SERVER_KEY=$(aws secretsmanager get-secret-value \
    --secret-id mdaopay/testnet/fcm-server-key \
    --query SecretString --output text 2>/dev/null || echo "")
if [ -z "$FCM_SERVER_KEY" ]; then
    log_warn "FCM_SERVER_KEY not set — push notifications will not work"
fi

METRICS_TOKEN=$(aws secretsmanager get-secret-value \
    --secret-id mdaopay/testnet/metrics-token \
    --query SecretString --output text 2>/dev/null || echo "")
if [ -z "$METRICS_TOKEN" ]; then
    METRICS_TOKEN=$(openssl rand -hex 16)
    aws secretsmanager create-secret \
        --name mdaopay/testnet/metrics-token \
        --secret-string "$METRICS_TOKEN" \
        >/dev/null
    log_info "METRICS_TOKEN stored in AWS Secrets Manager"
else
    log_info "METRICS_TOKEN loaded from AWS Secrets Manager (existing)"
fi

# ============================================================================
# 4. PREPARE ENVIRONMENT FOR FORGE
# ============================================================================
log_step "Preparing forge environment"

export ENTRY_POINT
export USDT_ADDRESS="$USDT_TESTNET"
export WBNB_ADDRESS="$WBNB_TESTNET"
export TRUSTED_SIGNER
export GNOSIS_SAFE
export INSURANCE_AUDITOR_ADDRESS

# ============================================================================
# 5. DEPLOY CONTRACTS
# ============================================================================
log_step "Deploying contracts via forge script"

cd "$REPO_ROOT/contracts"

# ── Step 1/2: dry-run — show deployment plan, do NOT broadcast ──
log_step "Step 1/2: Dry-run (plan only, no broadcast)"

forge script script/Deploy.s.sol \
    --rpc-url "$RPC_URL" \
    --private-key "$BSC_TESTNET_DEPLOYER_KEY" \
    --dry-run \
    --slow

# ── HITL pause: review dry-run plan before broadcast ──
log_info "DRY-RUN OK — подтвердите план перед broadcast"

if [ "${CONFIRM_DEPLOY:-}" = "no" ]; then
    log_info "CONFIRM_DEPLOY=no — dry-run only, exiting"
    exit 0
fi

if [ -z "${CONFIRM_DEPLOY:-}" ] && [ -t 0 ]; then
    read -r -p "Proceed with broadcast? [Y/n] " answer
    if [[ "$answer" =~ ^[nN](o)?$ ]]; then
        log_error "Aborted by user"
        exit 1
    fi
fi

# ── Step 2/2: real deployment (broadcast + verify) ──
log_step "Step 2/2: Broadcast (real deployment)"

forge script script/Deploy.s.sol \
    --rpc-url "$RPC_URL" \
    --private-key "$BSC_TESTNET_DEPLOYER_KEY" \
    --broadcast \
    --verify \
    --etherscan-api-key "$BSCSCAN_API_KEY" \
    --slow

log_info "Deploy script completed"

# ============================================================================
# 6. PARSE DEPLOYED ADDRESSES
# ============================================================================
log_step "Parsing deployed addresses"

BROADCAST_DIR="broadcast/Deploy.s.sol/$EXPECTED_CHAIN_ID/run-latest.json"

[ -f "$BROADCAST_DIR" ] || { log_error "Broadcast file not found: $BROADCAST_DIR"; exit 1; }

list_available_contracts() {
    log_error "Available CREATE transactions in broadcast:"
    jq -r '.transactions[] | select(.transactionType=="CREATE") | "  - \(.contractName // "unnamed"): \(.contractAddress)"' "$BROADCAST_DIR" >&2
}

parse_address() {
    local name=$1
    local addr

    addr=$(jq -r --arg n "$name" \
        '.transactions[] | select(.contractName==$n and .transactionType=="CREATE") | .contractAddress' \
        "$BROADCAST_DIR" | head -1)

    if [ -z "$addr" ] || [ "$addr" = "null" ]; then
        log_error "Cannot find deployment address for '$name'"
        list_available_contracts
        exit 1
    fi

    echo "$addr"
}

MDAO_TOKEN=$(parse_address "MDAOToken")
PAYMASTER=$(parse_address "MDAOPaymaster")
INSURANCE_FUND=$(parse_address "InsuranceFund")
SOCIAL_RECOVERY=$(parse_address "SocialRecoveryModule")
NICKNAME_REGISTRY=$(parse_address "NicknameRegistry")
DEAD_MAN_SWITCH=$(parse_address "DeadManSwitch")
ATTESTATION_LEDGER=$(parse_address "AttestationLedger")
REFUND_VAULT=$(parse_address "RefundVault")
SESSION_KEY=$(parse_address "SessionKeyModule")
TIMELOCK=$(parse_address "TimelockController")
ECDSA_VERIFIER=$(parse_address "EcdsaVerifier")
TRUST_PROVIDER_REGISTRY=$(parse_address "TrustProviderRegistry")
P256_VERIFIER=$(parse_address "P256Verifier")
TREASURY=$(parse_address "Treasury")
PROPOSAL=$(parse_address "Proposal")
SPLITTER_FACTORY=$(parse_address "PaymentSplitterFactory")
log_info "P256Verifier deployed at: $P256_VERIFIER"

ALL_ADDRS=("$MDAO_TOKEN" "$PAYMASTER" "$INSURANCE_FUND" "$SOCIAL_RECOVERY" \
           "$NICKNAME_REGISTRY" "$DEAD_MAN_SWITCH" "$ATTESTATION_LEDGER" "$REFUND_VAULT" \
           "$SESSION_KEY" "$TIMELOCK" "$ECDSA_VERIFIER" "$TRUST_PROVIDER_REGISTRY" "$P256_VERIFIER" \
           "$TREASURY" "$PROPOSAL" "$SPLITTER_FACTORY")
ALL_LOWER=($(printf '%s\n' "${ALL_ADDRS[@]}" | tr '[:upper:]' '[:lower:]'))
UNIQUE_ADDRS=($(printf '%s\n' "${ALL_LOWER[@]}" | sort -u))
[ ${#UNIQUE_ADDRS[@]} -eq ${#ALL_LOWER[@]} ] \
    || { log_error "Duplicate contract addresses detected — deployment corrupted"; exit 1; }

log_info "All contract addresses parsed and unique:"
log_info "  MDAOToken:              $MDAO_TOKEN"
log_info "  MDAOPaymaster:          $PAYMASTER"
log_info "  InsuranceFund:          $INSURANCE_FUND"
log_info "  SocialRecoveryModule:   $SOCIAL_RECOVERY"
log_info "  NicknameRegistry:       $NICKNAME_REGISTRY"
log_info "  DeadManSwitch:          $DEAD_MAN_SWITCH"
log_info "  AttestationLedger:      $ATTESTATION_LEDGER"
log_info "  RefundVault:            $REFUND_VAULT"
log_info "  SessionKeyModule:       $SESSION_KEY"
log_info "  TimelockController:     $TIMELOCK"
log_info "  EcdsaVerifier:          $ECDSA_VERIFIER"
log_info "  TrustProviderRegistry:  $TRUST_PROVIDER_REGISTRY"
log_info "  P256Verifier:           $P256_VERIFIER"
log_info "  Treasury:               $TREASURY"
log_info "  Proposal:               $PROPOSAL"
log_info "  PaymentSplitterFactory: $SPLITTER_FACTORY"

# ============================================================================
# 7. VERIFY CONTRACTS ON BSCSCAN
# ============================================================================
log_step "Verifying contracts on BscScan"

TIMELOCK_PATH=$(find "$REPO_ROOT/lib" -name "TimelockController.sol" 2>/dev/null | head -1 || true)
if [ -z "$TIMELOCK_PATH" ]; then
    log_warn "TimelockController.sol not found — skipping verification of contract source"
    TIMELOCK_PATH="lib/openzeppelin-contracts/contracts/governance/TimelockController.sol"
fi

# ✅ FIXED #3: Pre-flight check for cast abi-encode array syntax support
TIMELOCK_ABI_ENCODE_OK=false
if cast abi-encode "f(uint256,address[],address[],address)" \
    172800 "[$GNOSIS_SAFE]" "[$GNOSIS_SAFE]" \
    "0x0000000000000000000000000000000000000000" >/dev/null 2>&1; then
    TIMELOCK_ABI_ENCODE_OK=true
    log_info "cast abi-encode supports array syntax — TimelockController verification enabled"
else
    log_warn "cast abi-encode does not support array syntax on this version"
    log_warn "TimelockController verification will be skipped — verify manually on BscScan"
fi

verify_contract() {
    local addr=$1 path=$2 name=$3 ctor_args=${4:-}

    log_info "Verifying $name at $addr..."

    local output exit_ok
    if [ -n "$ctor_args" ] && [ "$ctor_args" != "0x" ]; then
        output=$(forge verify-contract "$addr" "$path:$name" \
            --chain "$EXPECTED_CHAIN_ID" \
            --etherscan-api-key "$BSCSCAN_API_KEY" \
            --constructor-args "$ctor_args" \
            --retries 5 \
            2>&1) && exit_ok=0 || exit_ok=1
    else
        output=$(forge verify-contract "$addr" "$path:$name" \
            --chain "$EXPECTED_CHAIN_ID" \
            --etherscan-api-key "$BSCSCAN_API_KEY" \
            --retries 5 \
            2>&1) && exit_ok=0 || exit_ok=1
    fi

    if echo "$output" | grep -qE "Contract successfully verified|Already verified"; then
        log_info "$name verified"
    else
        log_warn "Verification failed for $name (exit=$exit_ok)"
        log_warn "Output: $(echo "$output" | tail -3)"
        log_warn "Retry: forge verify-contract $addr $path:$name --chain $EXPECTED_CHAIN_ID ..."
    fi
}

verify_contract "$MDAO_TOKEN" "src/MDAOToken.sol" "MDAOToken" \
    "$(cast abi-encode "constructor(address)" "$DEPLOYER_ADDR")"

verify_contract "$PAYMASTER" "src/MDAOPaymaster.sol" "MDAOPaymaster" \
    "$(cast abi-encode "constructor(address,address,address,address)" "$ENTRY_POINT" "$MDAO_TOKEN" "$USDT_TESTNET" "$TRUSTED_SIGNER")"

verify_contract "$SOCIAL_RECOVERY" "src/SocialRecoveryModule.sol" "SocialRecoveryModule" \
    "$(cast abi-encode "constructor(address,address)" "$MDAO_TOKEN" "$P256_VERIFIER")"

verify_contract "$INSURANCE_FUND" "src/InsuranceFund.sol" "InsuranceFund" \
    "$(cast abi-encode "constructor(address[],uint256)" "[$INSURANCE_AUDITOR]" "1")"

verify_contract "$NICKNAME_REGISTRY" "src/NicknameRegistry.sol" "NicknameRegistry" ""
verify_contract "$DEAD_MAN_SWITCH" "src/DeadManSwitch.sol" "DeadManSwitch" ""
verify_contract "$ATTESTATION_LEDGER" "src/AttestationLedger.sol" "AttestationLedger" ""
verify_contract "$REFUND_VAULT" "src/RefundVault.sol" "RefundVault" ""
verify_contract "$SESSION_KEY" "src/SessionKeyModule.sol" "SessionKeyModule" ""

# ✅ FIXED #3: Conditional TimelockController verification
if [ "$TIMELOCK_ABI_ENCODE_OK" = "true" ]; then
    verify_contract "$TIMELOCK" "$TIMELOCK_PATH" "TimelockController" \
        "$(cast abi-encode "constructor(uint256,address[],address[],address)" \
            172800 "[$GNOSIS_SAFE]" "[$GNOSIS_SAFE]" \
            "0x0000000000000000000000000000000000000000")"
else
    log_warn "Skipping TimelockController verification (cast abi-encode array syntax not supported)"
fi

verify_contract "$ECDSA_VERIFIER" "src/EcdsaVerifier.sol" "EcdsaVerifier" \
    "$(cast abi-encode "constructor(address)" "$TRUSTED_SIGNER")"

verify_contract "$TRUST_PROVIDER_REGISTRY" "src/TrustProviderRegistry.sol" "TrustProviderRegistry" ""
verify_contract "$P256_VERIFIER" "src/helpers/P256Verifier.sol" "P256Verifier" ""

# ── Governance contracts (R-2) ──
verify_contract "$TREASURY" "src/Treasury.sol" "Treasury" \
    "$(cast abi-encode "constructor(address,address)" "$DEPLOYER_ADDR" "0x0000000000000000000000000000000000000000")"

verify_contract "$PROPOSAL" "src/Proposal.sol" "Proposal" \
    "$(cast abi-encode "constructor(address,address,address)" "$TREASURY" "$MDAO_TOKEN" "$DEPLOYER_ADDR")"

verify_contract "$SPLITTER_FACTORY" "src/PaymentSplitterFactory.sol" "PaymentSplitterFactory" ""

# ============================================================================
# 8. POST-DEPLOY ON-CHAIN CONFIGURATION
# ============================================================================
log_step "Post-deploy on-chain configuration"

log_info "Setting MDAOToken.setExempt(SocialRecoveryModule, true)..."
cast send "$MDAO_TOKEN" "setExempt(address,bool)" "$SOCIAL_RECOVERY" true \
    --rpc-url "$RPC_URL" \
    --private-key "$BSC_TESTNET_DEPLOYER_KEY" \
    >/dev/null

IS_EXEMPT=$(cast call "$MDAO_TOKEN" "isExempt(address)(bool)" "$SOCIAL_RECOVERY" --rpc-url "$RPC_URL")
[ "$IS_EXEMPT" = "true" ] \
    || { log_error "setExempt for SocialRecoveryModule failed"; exit 1; }
log_info "SocialRecoveryModule is exempt from MDAO fee-on-transfer"

# C-08: InsuranceFund uses auditors[] array — check index 0 (matching deploy: [addr], 1)
AUDITOR_ONCHAIN=$(cast call "$INSURANCE_FUND" "auditors(uint256)(address)" 0 --rpc-url "$RPC_URL")
AUDITOR_ONCHAIN=$(echo "$AUDITOR_ONCHAIN" | tr '[:upper:]' '[:lower:]')
AUDITOR_ENV=$(echo "$INSURANCE_AUDITOR" | tr '[:upper:]' '[:lower:]')
[ "$AUDITOR_ONCHAIN" = "$AUDITOR_ENV" ] \
    || { log_error "InsuranceFund auditor mismatch: on-chain=$AUDITOR_ONCHAIN env=$AUDITOR_ENV"; exit 1; }
log_info "InsuranceFund auditor verified: $AUDITOR_ONCHAIN"

PAYMASTER_OWNER=$(cast call "$PAYMASTER" "owner()(address)" --rpc-url "$RPC_URL")
if [ "$PAYMASTER_OWNER" = "$TIMELOCK" ]; then
    log_info "Paymaster owned by TimelockController"
elif [ "$PAYMASTER_OWNER" = "$DEPLOYER_ADDR" ]; then
    log_warn "Paymaster still owned by deployer — Timelock.execute pending (2-day delay)"
else
    log_warn "Paymaster owner unexpected: $PAYMASTER_OWNER"
fi

# ============================================================================
# 9. PREPARE BACKEND CONFIG
# ============================================================================
log_step "Preparing backend configuration"

cd "$REPO_ROOT/backend"

# ✅ FIXED #5: SWAP_ROUTER_ADDRESS added to .env.testnet.public
cat > .env.testnet.public <<EOF
PORT=8080
EXPECTED_CHAIN_ID=$EXPECTED_CHAIN_ID
IS_TESTNET=true
RPC_URLS=$RPC_URL
PAYMASTER_ADDRESS=$PAYMASTER
MDAO_ADDRESS=$MDAO_TOKEN
USDT_ADDRESS=$USDT_TESTNET
WBNB_ADDRESS=$WBNB_TESTNET
ENTRY_POINT=$ENTRY_POINT
RECOVERY_MODULE_ADDRESS=$SOCIAL_RECOVERY
NICKNAME_REGISTRY_ADDRESS=$NICKNAME_REGISTRY
INSURANCE_FUND_ADDRESS=$INSURANCE_FUND
DEAD_MAN_SWITCH_ADDRESS=$DEAD_MAN_SWITCH
REFUND_VAULT_ADDRESS=$REFUND_VAULT
SESSION_KEY_MODULE_ADDRESS=$SESSION_KEY
ATTESTATION_LEDGER_ADDRESS=$ATTESTATION_LEDGER
TRUST_PROVIDER_REGISTRY_ADDRESS=$TRUST_PROVIDER_REGISTRY
ECDSA_VERIFIER_ADDRESS=$ECDSA_VERIFIER
TIMELOCK_ADDRESS=$TIMELOCK
P256_VERIFIER_ADDRESS=$P256_VERIFIER
SWAP_ROUTER_ADDRESS=$SWAP_ROUTER_TESTNET
TREASURY_ADDRESS=$TREASURY
PROPOSAL_ADDRESS=$PROPOSAL
PAYMENT_SPLITTER_FACTORY_ADDRESS=$SPLITTER_FACTORY
ALLOW_LOCAL_SIGNING=true
KMS_REGION=us-east-1
EOF

cd "$REPO_ROOT"
[ -f .gitignore ] || touch .gitignore
grep -q '\.env\.testnet' .gitignore || { echo ".env.testnet" >> .gitignore; log_warn "Added .env.testnet to .gitignore"; }
grep -q '\.env\.' .gitignore || { echo ".env.*" >> .gitignore; log_warn "Added .env.* to .gitignore"; }
grep -q 'deployments/' .gitignore || { echo "deployments/" >> .gitignore; log_warn "Added deployments/ to .gitignore"; }

log_info "Backend public config written to backend/.env.testnet.public"
log_info ".gitignore updated"

# ============================================================================
# 10. BUILD AND DEPLOY BACKEND
# ============================================================================
log_step "Building and deploying backend"

docker network create mdaopay-testnet 2>/dev/null || true

REDIS_PASSWORD="${REDIS_PASSWORD:-testnet_redis_pass}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-testnet_pg_pass}"

ensure_container_running() {
    local name=$1
    local status
    status=$(docker inspect "$name" --format '{{.State.Status}}' 2>/dev/null || echo "absent")
    if [ "$status" = "running" ]; then
        log_info "$name already running"
        return 0
    fi
    if [ "$status" != "absent" ]; then
        log_warn "$name exists but status=$status — removing"
        docker rm -f "$name" 2>/dev/null || true
    fi
    return 1
}

# ✅ FIXED #2: Redis readiness check (ping-loop with PONG verification)
if ! ensure_container_running mdaopay-redis; then
    docker run -d \
        --name mdaopay-redis \
        --network mdaopay-testnet \
        --restart unless-stopped \
        redis:7-alpine \
        redis-server --requirepass "$REDIS_PASSWORD"

    log_info "Waiting for Redis to become ready..."
    REDIS_READY=false
    for i in $(seq 1 15); do
        if docker exec mdaopay-redis redis-cli -a "$REDIS_PASSWORD" ping 2>/dev/null | grep -q PONG; then
            log_info "Redis ready after $((i))s"
            REDIS_READY=true
            break
        fi
        [ "$i" -lt 15 ] && sleep 1
    done
    # ✅ FIXED #1: string comparison instead of executing variable as command
    if [ "$REDIS_READY" != "true" ]; then
        log_error "Redis not ready after 15s — check container logs:"
        docker logs mdaopay-redis 2>&1 | tail -20
        exit 1
    fi
    log_info "Redis started"
fi

if ! ensure_container_running mdaopay-postgres; then
    docker run -d \
        --name mdaopay-postgres \
        --network mdaopay-testnet \
        --restart unless-stopped \
        -e POSTGRES_DB=mdaopay \
        -e POSTGRES_USER=mdaopay \
        -e "POSTGRES_PASSWORD=$POSTGRES_PASSWORD" \
        postgres:16-alpine
    log_info "Postgres started"
fi

log_info "Waiting for Postgres to become ready..."
POSTGRES_READY=false
for i in $(seq 1 30); do
    if docker exec mdaopay-postgres pg_isready -U mdaopay >/dev/null 2>&1; then
        log_info "Postgres ready after $((i * 2))s"
        POSTGRES_READY=true
        break
    fi
    [ "$i" -lt 30 ] && sleep 2
done
# ✅ FIXED #1: string comparison instead of executing "$POSTGRES_READY" as command
if [ "$POSTGRES_READY" != "true" ]; then
    log_error "Postgres not ready after 60s — check container logs:"
    docker logs mdaopay-postgres 2>&1 | tail -20
    exit 1
fi

cd "$REPO_ROOT/backend"
docker build -t mdaopay-backend:testnet .

log_info "Database migrations run automatically at backend startup (Flyway auto-migrate)"

docker stop mdaopay-backend 2>/dev/null || true
docker rm mdaopay-backend 2>/dev/null || true

# ✅ FIXED #4: --health-start-period 45s (was 15s) + interval 15s + retries 8
# Total grace: start-period + interval*retries = 45 + 15*8 = 165s
# timeout 180 below gives extra 15s margin
docker run -d \
    --name mdaopay-backend \
    --network mdaopay-testnet \
    --env-file .env.testnet.public \
    -e "JWT_SECRET=$JWT_SECRET" \
    -e "RELAY_JWT_SECRET=$RELAY_JWT_SECRET" \
    -e "RELAY_HMAC_SECRET=$RELAY_HMAC_SECRET" \
    -e "TRUSTED_SIGNER=$TRUSTED_SIGNER" \
    -e "PAYMASTER_PRIVATE_KEY=$TESTNET_PAYMASTER_KEY" \
    -e "SWAP_PRIVATE_KEY=$TESTNET_SWAP_KEY" \
    -e "METRICS_TOKEN=$METRICS_TOKEN" \
    -e "REDIS_URL=redis://:${REDIS_PASSWORD}@mdaopay-redis:6379" \
    -e "DATABASE_URL=postgresql://mdaopay:${POSTGRES_PASSWORD}@mdaopay-postgres:5432/mdaopay" \
    --restart unless-stopped \
    -p 127.0.0.1:8080:8080 \
    --memory 1g \
    --health-cmd "curl -sf http://localhost:8080/health || exit 1" \
    --health-interval 15s \
    --health-start-period 45s \
    --health-retries 8 \
    mdaopay-backend:testnet

# ✅ FIXED #4: timeout 180 synced with health-start-period + interval*retries
# Grace: 45s start + 15s*8 retries = 165s, timeout 180s gives 15s margin
log_info "Waiting for backend to become healthy (timeout 180s)..."
timeout 180 bash -c '
    while true; do
        status=$(docker inspect mdaopay-backend --format "{{.State.Health.Status}}" 2>/dev/null || echo "starting")
        if [ "$status" = "healthy" ]; then
            echo "Backend healthy"
            exit 0
        elif [ "$status" = "unhealthy" ]; then
            echo "Backend unhealthy"
            docker logs mdaopay-backend 2>&1 | tail -50
            exit 1
        fi
        sleep 3
    done
' || { log_error "Backend failed to become healthy within 180s"; docker logs mdaopay-backend 2>&1 | tail -100; exit 1; }

# ============================================================================
# 11. DEPLOY RELAY TO CLOUDFLARE WORKERS
# ============================================================================
log_step "Deploying relay to Cloudflare Workers"

cd "$REPO_ROOT/relay"

wrangler deploy --env testnet

log_info "Setting Cloudflare Workers secrets..."

echo "$RELAY_HMAC_SECRET" | wrangler secret put RELAY_HMAC_SECRET --env testnet
if [ -n "$FCM_SERVER_KEY" ]; then
    echo "$FCM_SERVER_KEY" | wrangler secret put FCM_SERVER_KEY --env testnet
fi

log_info "Verifying relay secrets..."
wrangler secret list --env testnet | grep -q "RELAY_HMAC_SECRET" \
    || { log_error "RELAY_HMAC_SECRET not set in Cloudflare Workers"; exit 1; }
log_info "RELAY_HMAC_SECRET set in Cloudflare Workers"

if [ -n "$FCM_SERVER_KEY" ]; then
    wrangler secret list --env testnet | grep -q "FCM_SERVER_KEY" \
        || { log_error "FCM_SERVER_KEY not set in Cloudflare Workers"; exit 1; }
    log_info "FCM_SERVER_KEY set in Cloudflare Workers"
fi

# ============================================================================
# 12. FUNCTIONAL SMOKE TESTS
# ============================================================================
log_step "Functional smoke tests"

BACKEND_URL="http://localhost:8080"

curl -sf "$BACKEND_URL/health" | jq -e '.status == "ok"' >/dev/null \
    || { log_error "Basic health check failed"; exit 1; }
log_info "/health OK"

if curl -sf "$BACKEND_URL/health/deep" >/dev/null 2>&1; then
    DEEP_HEALTH=$(curl -sf "$BACKEND_URL/health/deep")
    echo "$DEEP_HEALTH" | jq -e '.status == "ok"' >/dev/null \
        && log_info "/health/deep OK" \
        || log_warn "/health/deep degraded (non-blocking for testnet)"
    echo "$DEEP_HEALTH" | jq '.checks'
else
    log_warn "/health/deep not implemented yet (Phase 2.3 pending — non-blocking)"
fi

NONCE_RESP=$(curl -sf "$BACKEND_URL/v1/auth/siwe/nonce/0x0000000000000000000000000000000000000001" || echo "")
echo "$NONCE_RESP" | jq -e '.nonce' >/dev/null \
    || { log_error "SIWE nonce endpoint broken (Phase 0.2 required before Phase 3 deploy)"; \
         log_error "Response: $NONCE_RESP"; exit 1; }
log_info "SIWE nonce endpoint works"

SIGN_STATUS=$(curl -o /dev/null -s -w "%{http_code}" \
    -X POST "$BACKEND_URL/v1/sign" \
    -H "Content-Type: application/json" \
    -d '{"sender":"0x0000000000000000000000000000000000000001","nonce":"0x0","callData":"0x","verificationGasLimit":"0x0","callGasLimit":"0x0","preVerificationGas":"0x0","maxPriorityFeePerGas":"0x0","maxFeePerGas":"0x0"}')
[ "$SIGN_STATUS" = "401" ] \
    || { log_error "/v1/sign returns $SIGN_STATUS instead of 401 (auth bypassed?)"; exit 1; }
log_info "/v1/sign requires auth (returns 401)"

METRICS=$(curl -sf "$BACKEND_URL/metrics" || echo "")
if echo "$METRICS" | grep -qE "mdaopay_watchtower|watchtower"; then
    log_info "Watchtower metrics present"
else
    log_warn "Watchtower metrics not found (Phase 2.4 pending — non-blocking)"
fi

WORKER_NAME=$(grep -E '^name\s*=' "$REPO_ROOT/relay/wrangler.toml" | head -1 \
    | sed 's/^name\s*=\s*//' | tr -d '"' | tr -d "'" | xargs)
RELAY_URL="${RELAY_URL_OVERRIDE:-https://${WORKER_NAME}-testnet.${CF_ACCOUNT_SUBDOMAIN:-YOUR_SUBDOMAIN}.workers.dev}"
if [ -n "$RELAY_URL" ] && [[ "$RELAY_URL" != *"YOUR_SUBDOMAIN"* ]]; then
    curl -sf "$RELAY_URL/health" >/dev/null 2>&1 \
        && log_info "Relay health check OK ($RELAY_URL)" \
        || log_warn "Relay /health not responding at $RELAY_URL (non-blocking)"
else
    log_warn "RELAY_URL not resolvable — set CF_ACCOUNT_SUBDOMAIN env var or RELAY_URL_OVERRIDE"
fi

# ============================================================================
# 13. SAVE DEPLOYMENT SUMMARY
# ============================================================================
log_step "Saving deployment summary"

cd "$REPO_ROOT"
mkdir -p deployments

DEPLOY_TIMESTAMP=$(date -u +%Y%m%d-%H%M%S)
DEPLOY_SUMMARY="deployments/testnet-$DEPLOY_TIMESTAMP.json"

cat > "$DEPLOY_SUMMARY" <<EOF
{
  "network": "bsc-testnet",
  "chainId": $EXPECTED_CHAIN_ID,
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "deployer": "$DEPLOYER_ADDR",
  "gnosisSafe": "$GNOSIS_SAFE",
  "trustedSigner": "$TRUSTED_SIGNER",
  "entryPoint": "$ENTRY_POINT",
  "usdtAddress": "$USDT_TESTNET",
  "wbnbAddress": "$WBNB_TESTNET",
  "swapRouterAddress": "$SWAP_ROUTER_TESTNET",
  "p256Verifier": "$P256_VERIFIER",
  "contracts": {
    "MDAOToken": "$MDAO_TOKEN",
    "MDAOPaymaster": "$PAYMASTER",
    "InsuranceFund": "$INSURANCE_FUND",
    "SocialRecoveryModule": "$SOCIAL_RECOVERY",
    "NicknameRegistry": "$NICKNAME_REGISTRY",
    "DeadManSwitch": "$DEAD_MAN_SWITCH",
    "AttestationLedger": "$ATTESTATION_LEDGER",
    "RefundVault": "$REFUND_VAULT",
    "SessionKeyModule": "$SESSION_KEY",
    "TimelockController": "$TIMELOCK",
    "EcdsaVerifier": "$ECDSA_VERIFIER",
    "TrustProviderRegistry": "$TRUST_PROVIDER_REGISTRY",
    "Treasury": "$TREASURY",
    "Proposal": "$PROPOSAL",
    "PaymentSplitterFactory": "$SPLITTER_FACTORY"
  },
  "explorer": "$BSC_EXPLORER",
  "nextSteps": [
    "Wait 2 days for TimelockController delay",
    "Execute: cast send $TIMELOCK 'execute(...)' for paymaster.acceptOwnership",
    "Execute: cast send $TIMELOCK 'execute(...)' for registry.acceptOwnership",
    "Verify: cast call $PAYMASTER 'owner()' == $TIMELOCK"
  ]
}
EOF

log_info "Deployment summary saved to $DEPLOY_SUMMARY"

aws ssm put-parameter \
    --name "/mdaopay/testnet/deployment" \
    --value "$(cat "$DEPLOY_SUMMARY")" \
    --type String \
    --overwrite \
    --region "${AWS_REGION:-us-east-1}" \
    2>/dev/null && log_info "Deployment saved to AWS SSM (/mdaopay/testnet/deployment)" \
    || log_warn "AWS SSM put-parameter failed (non-blocking)"

# ============================================================================
# 14. FINAL OUTPUT
# ============================================================================
log_step "Deployment complete!"

echo ""
echo "MDAOPay testnet deployment successful!"
echo ""
echo "--- Contract addresses:"
echo "   MDAOToken:              $BSC_EXPLORER/address/$MDAO_TOKEN"
echo "   MDAOPaymaster:          $BSC_EXPLORER/address/$PAYMASTER"
echo "   InsuranceFund:          $BSC_EXPLORER/address/$INSURANCE_FUND"
echo "   SocialRecoveryModule:   $BSC_EXPLORER/address/$SOCIAL_RECOVERY"
echo "   NicknameRegistry:       $BSC_EXPLORER/address/$NICKNAME_REGISTRY"
echo "   TimelockController:     $BSC_EXPLORER/address/$TIMELOCK"
echo "   TrustProviderRegistry:  $BSC_EXPLORER/address/$TRUST_PROVIDER_REGISTRY"
echo "   Treasury:               $BSC_EXPLORER/address/$TREASURY"
echo "   Proposal:               $BSC_EXPLORER/address/$PROPOSAL"
echo "   PaymentSplitterFactory: $BSC_EXPLORER/address/$SPLITTER_FACTORY"
echo "   P256Verifier:           $BSC_EXPLORER/address/$P256_VERIFIER"
echo ""
echo "--- Services:"
echo "   Backend:  http://localhost:8080"
echo "   Relay:    $RELAY_URL"
echo ""
echo "--- Next steps:"
echo "   1. Wait 2 days for Timelock delay"
echo "   2. Execute ownership transfers via TimelockController"
echo "   3. Run e2e tests: cd e2e && maestro test send.yaml"
echo "   4. Onboard beta testers"
echo ""
echo "Full deployment summary: $DEPLOY_SUMMARY"
echo ""

unset BSC_TESTNET_DEPLOYER_KEY TESTNET_PAYMASTER_KEY TESTNET_SWAP_KEY \
      JWT_SECRET RELAY_JWT_SECRET RELAY_HMAC_SECRET FCM_SERVER_KEY METRICS_TOKEN \
      REDIS_PASSWORD POSTGRES_PASSWORD
