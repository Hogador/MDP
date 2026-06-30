# MDAOPay Engineering Specification (TDD)

> **Document Owner:** Engineering Team
> **Status:** v2.8
> **Last Updated:** 2026-06-30
> **Corresponding PRD:** `PRD/` directory (27 documents)
> **ADR Reference:** `PRD/Appendix E. Architectural Decisions Record (ADR).md`

---

## Table of Contents

1. [Backend Architecture](#1-backend-architecture)
2. [Mobile Architecture](#2-mobile-architecture)
   - 2.4.5 [Identity Connect](#245-identity-connect)
3. [Smart Contracts](#3-smart-contracts)
4. [Infrastructure & DevOps](#4-infrastructure--devops)
   - 4.7 [Dev Tooling & Code Quality](#47-dev-tooling--code-quality)
   - 4.8 [Docker Compose Local Development](#48-docker-compose-local-development)
5. [Testing Strategy](#5-testing-strategy)
   - 5.3 [E2E Tests (Maestro)](#53-e2e-tests-maestro)
   - 5.5 [Load Tests](#55-load-tests)
   - 5.6 [Coverage Baseline](#56-coverage-baseline)
   - 5.7 [PermissionMapper](#57-permissionmapper-client-side-capability-mapping)
6. [Deployment & Environments](#6-deployment--environments)
   - 6.5 [Initial Contract Deployment](#65-initial-contract-deployment)

---

## 1. Backend Architecture

### 1.1 Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Kotlin (JVM) | 2.0.21 |
| Framework | Ktor | 3.1.2 |
| Server | Netty (embedded) | via Ktor |
| RPC Client | Web3j | 4.14.0 |
| Serialization | kotlinx.serialization | 1.7.3 |
| Async | kotlinx.coroutines | 1.9.0 |
| Logging | Logback | 1.5.18 |
| HTTP Client | Ktor CIO | via Ktor |
| Build | Gradle | 8.x |
| JVM Target | 21 | toolchain (javac —release 17 if runtime remains 17) |
| Runtime | eclipse-temurin:21-jre | Docker |

**Dependency Security Constraints:**
- **BouncyCastle:** `bcprov-jdk18on:1.80` forced via Gradle `constraints` (CVE-2024-30171, CVE-2023-33259 fixed in 1.77+). Web3j 4.14.0 transitively pulls earlier versions — constraint overrides.
- **Firebase:** All Firebase libraries managed via `firebase-bom:32.7.2` (BoM controls consistent versions).
- **ProGuard/R8:** Keep rules for `org.web3j.*`, `org.bouncycastle.*`, `com.google.zxing.*`, `kotlinx.serialization.*` in `app/proguard-rules.pro`.

### 1.2 API Contracts

#### 1.2.1 Paymaster Service

**Base URL:** `/v1/`

##### `POST /sign`

Signs a UserOperation for ERC-4337 gas sponsorship. Validates chain ID, nonce, DEX prices, token balances, and allowances before signing.

**Request:**
```json
{
    "sender": "0x...",
    "nonce": "0x...",
    "initCode": "0x",
    "callData": "0x...",
    "verificationGasLimit": "0x...",
    "callGasLimit": "0x...",
    "preVerificationGas": "0x...",
    "maxPriorityFeePerGas": "0x...",
    "maxFeePerGas": "0x...",
    "paymasterAndData": "0x",
    "signature": "0x",
    "mdaoMaxAmount": "0x...",
    "usdtMaxAmount": "0x...",
    "permitDeadline": "0x...",
    "permitV": "0x...",
    "permitR": "0x...",
    "permitS": "0x..."
}
```

**Response:**
```json
{
    "paymasterAndData": "0x...",
    "userOpHash": "0x...",
    "maxFee": "123456789",
    "token": "0x..."
}
```

**Validation pipeline (`PaymasterService.sign()`):**
1. **Chain ID check** — verify `eth_chainId` matches `EXPECTED_CHAIN_ID` (required, error if mismatch or unset)
2. **Nonce validation** — `eth_getTransactionCount(sender, PENDING)` > request nonce → reject stale; nonce too far ahead (>100) → reject with `GasEstimationException`
3. **Price fetch** — `PriceOracle` with 3 sources (DexScreener, CoinGecko, OnChain TWAP) in parallel, CircuitBreaker, median pricing, sanity checks. Redis cache: 30s fresh, 120s stale
4. **Gas cost calculation** — `totalGas = verificationGasLimit + callGasLimit + preVerificationGas`; `gasCostWei = maxFeePerGas * totalGas`; `gasCostUsd = gasCostWei * bnbUsd / 1e18`
5. **MDAO attempt** — parallel `eth_call` for balance + allowance (via `coroutineScope { async {} }`); if sufficient, sign with MDAO
6. **USDT fallback** — if MDAO insufficient, check balance/allowance for USDT (done in same parallel batch)
7. **Signing** — SHA3 hash of packed UserOp fields → **KMS remote signing** (key never materialized in app memory) → append to paymasterAndData

Steps 3-6 are parallelized via `coroutineScope { async {} }`, reducing RPC hops from ~5 sequential to ~1 (parallel batch of `eth_call` for balances + allowances). JSON-RPC batching further combines 4 `eth_call` requests into 1 HTTP round-trip.

> **Key security:** `PAYMASTER_PRIVATE_KEY` must use KMS remote signing (GCP Cloud KMS or AWS KMS). The private key never exists in application memory or environment variables. The signing service calls `kms.sign()` via gRPC/HTTP API. Fallback: env-var signing for non-production environments only, with explicit `ALLOW_LOCAL_SIGNING=true` flag.

**Error responses:**
```json
{"error": "Gas estimation failed"}
{"error": "Rate limited. Try again later."}
{"error": "Internal error"}
```

**Rate limits (/sign):**
- Per sender: 1 request per 30 seconds
- Per IP: 20 requests per 60 seconds
- Window: sliding (ConcurrentHashMap + cleanup thread every 300s)

**Rate limits (Auth endpoints — F-054):**
- `POST /auth/login`: 5 requests per minute per IP
- `POST /auth/register`: 3 requests per minute per IP
- `POST /auth/refresh`: 10 requests per minute per IP
- Implementation: `RedisRateLimiter` with prefix `ratelimit:auth:{ip}:{endpoint}`
- Fallback: in-memory ConcurrentHashMap when Redis unavailable (fail-closed)

**IP extraction (proxy-safe — F-054):**
- `extractClientIp()` reads `X-Forwarded-For` header first
- Falls back to `X-Real-IP`, then `request.origin.remoteHost`
- Trusted proxy list: Cloudflare IP ranges + RFC1918 private ranges
- Untrusted `X-Forwarded-For` values are ignored (spoofing prevention)

#### Registry Integration (Step 1b)

The Paymaster integrates with `TrustProviderRegistry` for signature verification. The registry provides on-chain provider registration, status management, and verification delegation.

```solidity
/// @notice Sets the TrustProviderRegistry address
/// @param _registry Address of the registry contract (0 = fallback to legacy trustedSigner)
function setRegistry(address _registry) external onlyOwner
```

**Verification flow in `validatePaymasterUserOp`:**

```
1. If trustProviderRegistry == address(0):
     └── Use legacy trustedSigner ECDSA verification (old flow)
2. If trustProviderRegistry != address(0):
     └── Derive providerId = keccak256(abi.encodePacked(signer))
     └── Call TrustProviderRegistry.verify(providerId, intentHash, proof)
     └── Registry checks:
         ├── Provider must be ACTIVE (reverts with ProviderNotActive if DEPRECATED/UNREGISTERED)
         └── Delegates to ITrustProvider(verifier).verify()
     └── If registry reverts → validation fails
```

#### `withdrawTo()` — Unified Withdrawal (Step 1a)

Replaces the old `scheduleWithdraw`/`executeWithdraw`/`cancelWithdraw` pattern. Uses `TimelockController` for access control and includes daily cap protection (F-018).

```solidity
/// @notice Direct withdrawal with daily cap — protected by TimelockController (onlyOwner)
/// @param token Token address (address(0) = ETH via EntryPoint.withdrawTo)
/// @param to Recipient address
/// @param amount Amount to withdraw
function withdrawTo(address token, address to, uint256 amount) external onlyOwner
```

**Flow:**
1. Revert if `to == address(0)`
2. Reset daily counter if 24h window has passed
3. Check `dailyWithdrawnToday + amount <= balance * dailyWithdrawalCapBps / 10000` — revert with `DailyCapExceeded`
4. Increment `dailyWithdrawnToday += amount`
5. If ETH: call `EntryPoint.withdrawTo(to, amount)` via low-level call
6. If ERC-20: `IERC20(token).safeTransfer(to, amount)`
7. Emit `WithdrawalExecuted`

##### `GET /health`

```json
{"status": "ok"}
```

##### `GET /metrics`

**Internal only.** Must be behind VPC or require `X-Metrics-Token` header (random 256-bit value, rotated monthly). Not exposed to public internet.

```json
{
    "uptime_seconds": 12345,
    "requests_total": 5000,
    "errors_total": 23,
    "memory_used_bytes": 268435456,
    "memory_max_bytes": 1073741824,
    "active_threads": 12
}
```

#### 1.2.2 Nickname Service

**Base URL:** `/v1/nickname`

##### `POST /nickname/register`

Registers a human-readable nickname for a wallet address. Requires ECDSA signature proving ownership.

**Request:**
```json
{
    "nickname": "crazy-cherry",
    "address": "0x...",
    "signature": "0x...",
    "nonce": 12345
}
```

**Validation (`NicknameService.register()`):**
1. **Format** — `^[a-zA-Z0-9_-]{3,20}$` via `NicknamePolicy.validate()` which returns a sealed `ValidationResult` (Valid | TooShort | TooLong | InvalidChars | Reserved)
2. **Reserved names** — 10 reserved words: `admin, support, mdao, paymaster, root, help, official, team, moderator, staff` (case-insensitive, checked in `NicknamePolicy`)
3. **On-chain verification** — `eth_call` to `NicknameRegistry` contract (hash of nickname → hash of address). Pending Txn:
   - If on-chain hash exists and matches → proceed
   - If on-chain hash exists and mismatches → reject
   - If on-chain hash doesn't exist → proceed (will be created)
4. **Nonce** — must be within 5-minute window of server time (`|now - nonce| < 300_000ms`)
5. **Replay** — usedSignatures cache prevents duplicate consumption (ConcurrentHashMap + cleanup)
6. **Message verification** — constructs `"Register nickname $nickname for $address (nonce: $nonce)"`, prepends `\x19Ethereum Signed Message:\n${msgLen}` (EIP-191), hashes with SHA3-256, recovers signer via `Keys.getAddress(publicKey)` (fixed from `Keys.toChecksumAddress()` which caused `IndexOutOfBoundsException`)
7. **Signature malleability** — rejects signatures with `s > secp256k1n/2` (EIP-2)
8. **Uniqueness** — PostgreSQL `nicknames` table (unique constraint on `nickname` column), fallback Redis cache; on-chain `NicknameRegistry` creates hash binding after confirmation

**Response (success):**
```json
{
    "nickname": "crazy-cherry",
    "address": "0x...",
    "registeredAt": "2026-06-22T10:00:00Z"
}
```

**Response (error):**
```json
{"error": "Nickname already taken"}
{"error": "Invalid signature"}
{"error": "Nonce expired"}
```

**OnChainRegistryClient — Identity hash calculation (+ F-036 regression test):**
- F-036: `isIdentityRegistered()` hashed the ASCII string representation of the address (e.g., `"0xf39F..."`) instead of the decoded 20-byte address, causing on-chain registry lookups to never match.
- Fix: `Hash.sha3(Numeric.hexStringToByteArray(address))` correctly hashes the 20-byte address like the Solidity `keccak256(abi.encodePacked(address))`.
- Regression test: `identity hash uses keccak256 of address bytes not string bytes` in `PaymasterUtilTest.kt`.

##### `GET /nickname/{name}`

```json
{
    "nickname": "crazy-cherry",
    "address": "0x...",
    "registeredAt": "2026-06-22T10:00:00Z"
}
```
Returns 404 if not found.

##### `GET /nickname/reverse/{address}`

Reverse resolve address → nickname.

```json
{
    "nickname": "crazy-cherry",
    "address": "0x...",
    "registeredAt": "2026-06-22T10:00:00Z"
}
```

##### `GET /nickname/check/{name}`

```json
{"available": true}
```

##### `GET /nickname/stats`

```json
{
    "totalNicknames": 1234,
    "uniqueAddresses": 1200
}
```

### 1.3 PaymasterAndData Encoding

The paymaster data is appended to the paymaster address in the UserOperation:

```
paymasterAndData = paymasterAddress(20) || customData

customData = 
    token(20) || 
    maxTokenAmount(32) || 
    quoteDeadline(32) || 
    [permitDeadline(32) || v(1) || r(32) || s(32)]  // optional
```

Server signature suffix (appended by backend):
```
stampedPaymasterAndData = paymasterAndData || sigHex || lenHex || magic

where:
  sigHex = ECDSA signature (v || r || s) over EIP-712 Quote digest
  lenHex = length of sigHex / 2 (4 hex chars)
  magic  = "22e325a297439656"

Note: F-034 — Backend now signs EIP-712 Quote instead of EIP-191 userOpHash + chainId.
The Quote digest includes domainSeparator (with chainId and verifyingContract) for
cross-chain replay protection, plus nonce for per-sender replay protection.
```

### 1.4 UserOp Hash Computation (`computeUserOpHash`)

Standard ERC-4337 UserOp hash (v0.6-compatible):
```
userOpHash = keccak256(
    sender(20) ||
    nonce(32) ||
    keccak256(initCode)(32) ||
    keccak256(callData)(32) ||
    accountGasLimits(32) ||
    preVerificationGas(32) ||
    gasFees(32) ||
    keccak256(paymasterAndData)(32) ||
    keccak256(signature)(32)
)
```

`accountGasLimits = verificationGasLimit << 128 | callGasLimit`
`gasFees = maxPriorityFeePerGas << 128 | maxFeePerGas`

### 1.5 Environment Configuration (`AppConfig`)

| Variable | Format | Required | Default | Description |
|----------|--------|----------|---------|-------------|
| `PORT` | int | no | 8080 | HTTP server port |
| `RPC_URL` | URL | yes | — | JSON-RPC endpoint |
| `PAYMASTER_PRIVATE_KEY` | hex(64) | yes | — | ECDSA key for signing |
| `PAYMASTER_ADDRESS` | 0x + hex(40) | yes | — | Paymaster contract address |
| `MDAO_ADDRESS` | 0x + hex(40) | yes | — | MDAO token contract |
| `USDT_ADDRESS` | 0x + hex(40) | yes | — | USDT token contract |
| `WBNB_ADDRESS` | 0x + hex(40) | yes | — | WBNB token (for DexScreener) |
| `ENTRY_POINT` | 0x + hex(40) | no | `0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789` | ERC-4337 EntryPoint |
| `EXPECTED_CHAIN_ID` | long | yes | — | Chain verification — required, error if missing |
| `REDIS_URL` | URL | yes | — | Redis connection (e.g. `redis://localhost:6379`). Required in production — no unauthenticated default. |

Validation:
- Addresses: `^0x[a-fA-F0-9]{40}$`
- Private key: `^(0x)?[a-fA-F0-9]{64}$` (0x prefix auto-stripped)

### 1.6 Database Schema (PostgreSQL)

#### `users`

| Column | Type | Constraints |
|--------|------|------------|
| id | UUID | PK |
| wallet_address | VARCHAR(42) | UNIQUE, NOT NULL |
| email | VARCHAR(255) | |
| google_sub | VARCHAR(255) | UNIQUE |
| apple_sub | VARCHAR(255) | UNIQUE |
| created_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() |
| updated_at | TIMESTAMPTZ | NOT NULL, DEFAULT NOW() |

#### `nicknames`

| Column | Type | Constraints |
|--------|------|------------|
| id | UUID | PK |
| nickname | VARCHAR(20) | UNIQUE, NOT NULL |
| address | VARCHAR(42) | NOT NULL, INDEX |
| resolved | BOOLEAN | DEFAULT false |
| created_at | TIMESTAMPTZ | NOT NULL |
| updated_at | TIMESTAMPTZ | NOT NULL |

Indexes:
- `idx_nicknames_address` on `address`
- `idx_nicknames_nickname_lower` on `LOWER(nickname)`

#### `transactions`

| Column | Type | Constraints |
|--------|------|------------|
| id | UUID | PK |
| tx_hash | VARCHAR(66) | UNIQUE |
| chain_id | INT | NOT NULL |
| from_address | VARCHAR(42) | NOT NULL, INDEX |
| to_address | VARCHAR(42) | |
| value | NUMERIC(78,0) | |
| token_address | VARCHAR(42) | |
| token_symbol | VARCHAR(10) | |
| token_decimals | INT | |
| status | VARCHAR(20) | NOT NULL (pending/confirmed/failed) |
| block_number | BIGINT | |
| timestamp | TIMESTAMPTZ | |
| gas_used | BIGINT | |
| gas_price | NUMERIC(78,0) | |
| metadata | JSONB | |
| created_at | TIMESTAMPTZ | NOT NULL |

Indexes:
- `idx_transactions_from` on `from_address`
- `idx_transactions_hash` on `tx_hash`
- `idx_transactions_status` on `status`
- `idx_transactions_from_time` on `(from_address, created_at DESC)` — for wallet history queries

#### `guardians`

| Column | Type | Constraints |
|--------|------|------------|
| id | UUID | PK |
| wallet_id | UUID | FK → users.id |
| guardian_wallet_id | UUID | FK → users.id |
| share_index | SMALLINT | CHECK (1-4) |
| status | VARCHAR(20) | NOT NULL (pending/active/revoked) |
| invite_id | UUID | |
| created_at | TIMESTAMPTZ | NOT NULL |
| confirmed_at | TIMESTAMPTZ | |

#### `recovery_requests`

| Column | Type | Constraints |
|--------|------|------------|
| id | UUID | PK |
| wallet_id | UUID | FK → users.id |
| status | VARCHAR(20) | NOT NULL |
| threshold | SMALLINT | NOT NULL |
| expires_at | TIMESTAMPTZ | NOT NULL |
| created_at | TIMESTAMPTZ | NOT NULL |

#### `recovery_shares`

| Column | Type | Constraints |
|--------|------|------------|
| id | UUID | PK |
| wallet_id | UUID | FK → users.id |
| share_index | SMALLINT | CHECK (1-4) |
| share_hash | VARCHAR(64) | NOT NULL (SHA-256 of share) |
| created_at | TIMESTAMPTZ | NOT NULL |

Note: Shares are NEVER stored in plaintext on the server. Only SHA-256 hashes are stored for integrity verification.

#### `recovery_events`

| Column | Type | Constraints |
|--------|------|------------|
| id | UUID | PK |
| request_id | UUID | FK → recovery_requests.id |
| event_type | VARCHAR(50) | NOT NULL |
| payload | JSONB | |
| created_at | TIMESTAMPTZ | NOT NULL |

#### `audit_log`

| Column | Type | Constraints |
|--------|------|------------|
| id | BIGSERIAL | PK |
| event_type | VARCHAR(50) | NOT NULL |
| actor_address | VARCHAR(42) | |
| payload | JSONB | |
| ip_address | INET | |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT NOW() |

Partitioning: BY RANGE (created_at) — monthly partitions (`audit_log_2026_01`, `audit_log_2026_02`, ...)
Index: `idx_audit_log_type_created` on `(event_type, created_at DESC)`

**Fiat Onramp — MoonPay HMAC (F-037):**
- Widget URL is generated server-side using HMAC-SHA256 signature over sorted query parameters
- `javax.crypto.Mac` with `HmacSHA256` algorithm
- `moonpaySecretKey` env var (never exposed to client) used as HMAC key
- URL format: `https://buy.moonpay.com?apiKey=...&currencyCode=...&signature=<hex>`
- API key is a public merchant identifier (required by MoonPay widget); the HMAC signature prevents parameter tampering

#### `onramp_orders`

| Column | Type | Constraints |
|--------|------|------------|
| id | UUID | PK |
| user_id | UUID | FK → users.id |
| provider_id | VARCHAR(50) | NOT NULL |
| quote_id | VARCHAR(100) | |
| fiat_currency | VARCHAR(3) | NOT NULL |
| crypto_currency | VARCHAR(10) | NOT NULL |
| chain_id | INT | |
| fiat_amount | NUMERIC(18,2) | |
| crypto_amount | NUMERIC(78,0) | |
| fee | NUMERIC(18,2) | |
| rate | NUMERIC(36,18) | |
| status | VARCHAR(20) | NOT NULL |
| provider_ref | VARCHAR(100) | |
| destination_address | VARCHAR(42) | |
| created_at | TIMESTAMPTZ | NOT NULL |
| updated_at | TIMESTAMPTZ | |
| completed_at | TIMESTAMPTZ | |

### 1.7 Caching & Performance

| Component | Tech | Configuration | Rationale |
|-----------|------|---------------|-----------|
| Session/Token | Redis Cluster | TTL: 15m (JWT), 24h (Refresh) | Fast session validation |
| Nickname Resolution | Redis + CDN | TTL: 1h, CDN edge: 5m | < 50ms resolution |
| Rate Limiting | Redis (sliding window) | 100 req/min per IP, 1000 req/min per user | Abuse protection |
| On-Ramp Quotes | Redis | TTL: 30s | Price freshness |
| Transaction Index | PostgreSQL + Materialized Views | Refresh every 10s (was 30s — improved for real-time UX) | Query performance |
| DEX Prices (PaymasterService) | Redis | 30s fresh, 120s stale | Gas estimation speed, shared across instances |

**Redis eviction policy:** `allkeys-lru` — evict least recently used keys when memory limit is reached. Rate limiter keys (TTL: 1 min), replay cache keys (TTL: 60 min), and stale DEX prices are evicted first under memory pressure.

**Performance targets:**
- API p95: < 200ms
- Nickname resolve: < 50ms
- Recovery API: < 300ms
- Backend availability: > 99.9%

### 1.8 Authentication & Authorization

#### OAuth Providers
- **Google:** AndroidX Credential Manager → Firebase Auth
- **Apple:** Sign In with Apple → Firebase Auth OAuthProvider

#### JWT Token Structure
```
Access Token: { sub, wallet, nickname, iat, exp, scope }
Refresh Token: { sub, jti, iat, exp }
```

| Token | TTL | Storage | Rotation |
|-------|-----|---------|----------|
| Access Token | 15 min | Memory | On expiry |
| Refresh Token | 30 days | Secure storage | On use |

#### JWT_SECRET Requirements
- **Algorithm:** HMAC-SHA256
- **Storage:** Environment variable `JWT_SECRET` (required, no fallback)
- **Fail-fast:** Application refuses to start if `JWT_SECRET` is missing or < 44 chars (Base64-encoded 256-bit key)
- **Rationale:** 32 arbitrary chars can have low entropy (e.g., 32 repeated chars). Requiring 44 chars ensures the value is a Base64-encoded 256-bit key with real entropy.

#### Password Hashing (Auth)
- **Algorithm:** PBKDF2WithHmacSHA256
- **Iterations:** 600,000 (OWASP 2023 recommendation)
- **Key length:** 256 bits
- **Salt:** Random 16 bytes, Base64 encoded
- **Comparison:** `MessageDigest.isEqual()` (constant-time, prevents timing attacks)
- **Note:** PIN hashing uses same parameters (see §2.3.4)

#### API Keys
- Stored in: GCP Secret Manager (Cloud Run + Secret Manager)
- Rotation: Every 90 days (automated)
- Scope: Service-to-service communication

#### RBAC Roles
- **User:** Standard wallet operations
- **Admin:** Configuration, monitoring, support operations
- **Service:** Internal service-to-service

### 1.9 Rate Limiting Implementation

**Mandatory: Redis-based sliding window from Day 1.** In-memory state (ConcurrentHashMap) does not survive horizontal scaling on Cloud Run.

#### Redis Keys

| Key Pattern | TTL | Purpose |
|-------------|-----|---------|
| `ratelimit:ip:{ip}:{window}` | 60s | Per-IP rate limit (20 req/min) |
| `ratelimit:sender:{sender}:{window}` | 30s | Per-sender rate limit (1 req/30s) |
| `replay:signature:{hash(sig)}` | 300s | Nickname signature replay protection |
| `dex:prices:` | 30s (fresh) / 120s (stale) | DEX price cache |
| `paymaster:blocklist:{sender}` | 3600s | Sender block-list after repeated `PaymentFailed` |

#### Data Flow

```
Request → Redis EXISTS ratelimit:ip:{ip}:{window} → blocked | →
         → Redis EXISTS replay:signature:{sig_hash} → blocked | →
         → Process request → Redis SETEX replay:signature... (if Nickname)
         → Redis SETEX dex:prices... (if price fetch)
```

#### Implementation

```kotlin
// Redis client: Lettuce or Redisson (cluster-compatible)
// Uses ConcurrentHashMap in-memory fallback when Redis unavailable (fail-closed)
class RedisRateLimiter(private val prefix: String = "ratelimit") {
    private val fallbackMap = ConcurrentHashMap<String, RateLimitEntry>()
    private data class RateLimitEntry(val count: Long, val expiresAt: Long)

    suspend fun isLimited(key: String, maxRequests: Int, windowSec: Long): Boolean {
        val redisKey = "$prefix:$key"
        val count = Redis.incr(redisKey)
        if (count != null) {
            if (count == 1L) Redis.expire(redisKey, windowSec)
            return count > maxRequests
        }
        return isLimitedInMemory(key, maxRequests, windowSec)
    }
    // ... in-memory fallback with ConcurrentHashMap + warn logging
}
```

### 1.10 Price Oracle Architecture

**Multi-source architecture with Circuit Breaker:**

| Source | Reliability | Method |
|--------|-------------|--------|
| DexScreener | 1.0 | `GET https://api.dexscreener.com/latest/dex/tokens/{address}` — 3 tokens in parallel `async {}` |
| CoinGecko | 0.9 | `GET https://api.coingecko.com/api/v3/simple/price?ids=binancecoin,tether` (optional Pro API key) |
| OnChain TWAP | 0.95 | Placeholder (`NotImplementedError`) — Phase 2 via PancakeSwap pair TWAP |

**Circuit Breaker:**
- States: CLOSED → OPEN (after 5 consecutive failures) → HALF_OPEN (after 60s reset timeout)
- Half-open allows 1 probe request; success → CLOSED, failure → OPEN
- Each source uses its own breaker instance

**Median Pricing:**
- Collects prices from all available sources (parallel `async {}`)
- Sanity ranges: BNB $100–$10,000, USDT $0.90–$1.10, MDAO $0.0001–$100
- Median per token (robust to 1 outlier from 3 sources)
- Deviation alert (>10% from median → `log.error` + increments `errorsTotal` metric)

**DexPrices data class:**
```kotlin
@Serializable
data class DexPrices(
    val bnbUsd: Double,
    val mdaoUsd: Double,
    val usdtUsd: Double,
) {
    fun isValid(): Boolean = bnbUsd in 100.0..10000.0 && usdtUsd in 0.9..1.1 && mdaoUsd in 0.0001..100.0
}
```

**Caching:**
- Redis: 30s fresh cache (return without fetching), 120s stale cache (return if fetch fails)
- Cache key: `dex:prices`, serialized as `CachedDexPrices(prices, updatedAt)`
- Fallback prices (testnet only): BNB=$600, MDAO=$0.001, USDT=$1.00

**Exceptions:**
- `PriceOracleException` — thrown when all sources fail and cache is expired
- `CircuitBreakerOpenException` — thrown when breaker is OPEN (source temporarily disabled)

---

## 2. Mobile Architecture

### 2.1 Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Language | Kotlin | 2.0.21 |
| UI | Jetpack Compose | BOM 2025.06.00 |
| Architecture | MVI (Model-View-Intent) | — |
| DI | Hilt | 2.55 |
| Database | Room | 2.6.1 |
| Preferences | DataStore | 1.1.1 |
| Navigation | Compose Navigation | 2.8.5 |
| Blockchain | Web3j | 4.14.0 (with BouncyCastle 1.80 constraint) |
| Biometric | AndroidX Biometric | 1.4.0 |
| Credential Manager | AndroidX Credentials | 1.5.0 |
| Async | Coroutines | 1.9.0 |
| Serialization | Kotlinx Serialization | 1.7.3 |
| Background Work | WorkManager | 2.10.0 |
| QR Scan | ZXing | 3.5.3 |
| minSdk / targetSdk | 26 / 35 | |
| CompileSdk | 35 | |
| AGP | 8.13.2 | |

### 2.2 Application Layers

```
┌─────────────────────────────────────┐
│         Presentation Layer          │
│  Compose UI + ViewModels + States   │
├─────────────────────────────────────┤
│          Domain Layer               │
│  Use Cases, WalletManager, SSS,     │
│  PasskeyManager, BlockchainRepo     │
├─────────────────────────────────────┤
│           Data Layer                │
│  Room DB, DataStore, Keystore,      │
│  Blockchain RPC, REST API           │
└─────────────────────────────────────┘
```

#### 2.2.1 Presentation Layer

**ViewModels (13 total):**

| ViewModel | Purpose | Key State |
|-----------|---------|-----------|
| HomeViewModel | Wallet balances, tx history, contacts | `WalletState`, `TransactionItem` |
| SendViewModel | Multi-step send flow | Recipient → Amount → Confirm → Result |
| ReceiveViewModel | Display receive address | `walletAddress` |
| HistoryViewModel | Transaction history | `List<TransactionItem>` |
| ContactsViewModel | Contact management | `List<Contact>` |
| SettingsViewModel | App settings | Lock, notifications, theme |
| AssetDetailsViewModel | Per-token detail | Balance, tx history |
| WalletConnectViewModel | WalletConnect sessions | Sessions list |
| RecoveryViewModel (550 lines) | Social recovery, PIN, shares | Recovery state machine |
| OnboardingTutorialViewModel | Tutorial flow | Slide index |
| OnboardingBiometricViewModel | Biometric enrollment | `isBiometricAvailable` |
| OnboardingNicknameViewModel | Username selection | `nickname`, `isAvailable` |
| OnboardingGuardianViewModel | Guardian setup | Guardian list |

**Screens (18 total):**

| Screen | Route | Key Components |
|--------|-------|----------------|
| HomeScreen (1059 lines) | `main/home` | Card swiper, balance, favorites, service grid |
| SendScreen | `main/send` | Recipient input, amount, confirmation |
| ReceiveScreen | `main/receive` | QR code, address copy |
| HistoryScreen | `main/history` | Filterable tx list |
| SettingsScreen | `main/settings` | Toggles, theme, about |
| ProfileScreen | `main/profile` | Avatar, nickname, address |
| RecoveryScreen (1431 lines) | `main/recovery` | Guardian invite, PIN, progress |
| MainScreen | `main` | Bottom nav host |
| AssetDetailsScreen | `main/assets/{token}` | Price chart, tx list |
| AssetsScreen | `main/assets` | Token list |
| ContactsScreen | `main/contacts` | Contact list + search |
| WalletConnectScreen | `main/wc` | Sessions, pairing |
| OnrampScreen | `main/onramp` | Fiat on-ramp |
| ExchangerScreen | `main/exchanger` | Token swap |
| OnboardingTutorialScreen | `onboarding/tutorial` | Intro slides |
| OnboardingBiometricScreen | `onboarding/biometric` | Face/fingerprint setup |
| OnboardingNicknameScreen | `onboarding/nickname` | Username picker |
| OnboardingGuardianScreen | `onboarding/guardian` | Guardian assignment |

#### 2.2.2 Domain Layer

**Security module (`core/security/`) — 10 files:**

| File | Class | Responsibility | Key Parameters |
|------|-------|---------------|----------------|
| `KeystoreCrypto.kt` | `KeystoreCrypto` | AES-256-GCM via Android Keystore | Key size: 256, GCM tag: 128, IV: 12 bytes. `encrypt()` returns `iv + ciphertext`. `encrypt`/`decrypt` use `getOrCreateBiometricKey` (auth required). `getOrCreateKey` for no-auth keys (shares 1,3). |
| `ShamirSecretSharing.kt` | `ShamirSecretSharing` | SSS over GF(256) | K=3, N=5 (MVP: K=2, N=3 hermit) |
| `GF256.kt` | `GF256` | Galois Field arithmetic | Irreducible poly: 0x11D |
| `RecoveryShareManager.kt` | `RecoveryShareManager` | Share I/O (file + prefs) | Shares 1,3 → file, no auth key; Shares 2,4 → SharedPreferences, biometric auth |
| `PasskeyManager.kt` | `PasskeyManager` | WebAuthn PRF key derivation | FIDO2, HMAC-secret extension |
| `BiometricManager.kt` | `BiometricAuthManager` | AndroidX BiometricPrompt. `authenticateHighRisk()` with `BIOMETRIC_STRONG` only (F-062) | Requires BIOMETRIC_STRONG |
| `BiometricAvailability.kt` | `BiometricAvailability` | Biometric state enum | Available/NoHardware/NoneEnrolled |
| `SocialAuthManager.kt` | `SocialAuthManager` | Google + Apple Sign-In | Firebase Auth, Credential Manager |
| `AppLockManager.kt` | `AppLockManager` | Background lock enforcement | ProcessLifecycleOwner |
| `CborDecoder.kt` | `CborDecoder` | WebAuthn CBOR parsing | COSE key, authData |

**Blockchain module (`core/blockchain/`) — 12 files + subdirs:**

| File | Class | Key Function |
|------|-------|-------------|
| `WalletManager.kt` | `WalletManager` | BIP-39 mnemonic, BIP-44 derivation (path: m/44'/60'/0'/0/0) |
| `NetworkConfig.kt` | `NetworkConfig` | Chain ID 97 (BSC Testnet), EntryPoint, factory addresses |
| `BlockchainRepository.kt` | `BlockchainRepository` | Balance queries (ETH, USDT, MDAO) |
| `SendRepository.kt` | `SendRepository` | USDT transfer via ERC-4337 |
| `EthereumClient.kt` | `EthereumClient` | Multi-provider RPC (publicnode, ankr, chainstack) |
| `EthereumProviderInjector.kt` | `EthereumProviderInjector` | WebView ethereum provider (InjectedProvider). Origin validation + domain whitelist (F-059). Bridge cleanup on navigation. User confirmation dialog before signing. |
| `RpcProviderManager.kt` | `RpcProviderManager` | 3 providers, health check, auto-failover, error tracking with 30s cooldown |
| `NicknameResolver.kt` | `NicknameResolver` | DataStore-backed nickname cache |
| `TxErrorMapper.kt` | `TxErrorMapper` | Error → Russian user message |
| `EtherscanRepository.kt` | `EtherscanRepository` | Tx history via Etherscan API |
| `erc4337/BundlerClient.kt` | `BundlerClient` | `eth_sendUserOperation`, `eth_estimateUserOperationGas` |
| `erc4337/UserOperation.kt` | `UserOperation` | Struct with ABI encoding |
| `erc4337/RecoveryUserOpBuilder.kt` | `RecoveryUserOpBuilder` | Recovery execution UserOperation |
| `paymaster/PaymasterClient.kt` | `PaymasterClient` | signUserOp (POST /v1/sign), PaymasterAndData encoding (F-130) |

**Guardian module (`core/guardian/`) — 4 files:**

| File | Class | Key Function |
|------|-------|-------------|
| `GuardianManager.kt` | `GuardianManager` | invite, accept, approve, veto recovery |
| `GuardianStorage.kt` | `GuardianStorage` | DataStore persistence for guardian state |
| `RelayClient.kt` | `RelayClient` | REST client for relay worker |
| `GuardianContracts.kt` | Data classes | GuardianInfo, GuardianInvite, PendingRecovery |

#### 2.2.3 Data Layer

**Local Storage:**

| Component | Technology | Tables/Keys | Purpose |
|-----------|-----------|-------------|---------|
| AppDatabase | Room | `pending_transactions`, `contacts` | Persistent local cache |
| UserPreferences | DataStore | `theme`, `lock_enabled`, `onboarded` | User settings |
| TxQueue | Room (encrypted) | `id, chainId, from, to, value, data, gasLimit, maxFeePerGas, maxPriorityFeePerGas, nonce, createdAt, status, signedRawTx_enc` | Offline transaction queue |

> **Security:** `signedRawTx` contains a fully signed, broadcast-ready transaction. It MUST be encrypted at rest. Use SQLCipher for Room or encrypt the `signedRawTx` field with a Keystore-bound AES-256-GCM key (`txqueue_key`) before writing to the database. The encryption key is derived from the wallet key and never stored separately.
| KeystoreCrypto | Android Keystore | Key aliases: `mdaopay_wallet_key`, `mdaopay_share{1-4}_key` | Encrypted mnemonic + shares |
| PasskeyManager | Credential Manager | Passkey (WebAuthn) | PRF key derivation |
| GuardianStorage | DataStore | Guardians, invites, pending recoveries | Guardian state |

**Network Layer:**

| Component | Tech | Endpoints |
|-----------|------|-----------|
| Blockchain RPC | Web3j + OkHttp | Multi-provider with failover |
| Paymaster | Ktor client | `POST /sign` |
| Relay | Ktor client | `POST /invite`, `POST /accept`, `POST /approve` |
| Nickname | Ktor client | `GET /nickname/{name}` |
| Etherscan | HTTP | Tx history API |
| DexScreener | HTTP | Token prices |
| Firebase | FCM + Auth | Push + auth |

#### 2.2.4 Relay API Endpoints

The relay service exposes REST endpoints for guardian management, social recovery, and push notifications. All endpoints require HMAC authentication via `RELAY_SECRET`.

Types referenced from `relay/src/types.ts`.

| Endpoint | Method | Description | Request | Response |
|----------|--------|-------------|---------|----------|
| `/guardian/invite` | POST | Create guardian invite | `CreateInviteRequest` | `GuardianInvite` |
| `/guardian/invite/:inviteId` | GET | Get invite details | — | `GuardianInviteResponse` |
| `/guardian/invite/:inviteId/accept` | POST | Accept invite with P-256 signature | `AcceptInviteRequest` | `{accepted: boolean}` |
| `/recovery/pending/:walletAddress` | GET | Get pending recoveries | — | `PendingRecovery[]` |
| `/recovery/approve` | POST | Approve recovery | `RecoveryApproval` | `{approvals: number}` |
| `/recovery/veto` | POST | Veto recovery | `VetoRequest` | `{vetoed: boolean}` |
| `/recovery/notify` | POST | Notify guardians of recovery | `{walletAddress}` | `{notified: number}` |
| `/push/register` | POST | Register FCM push token | `PushRegisterRequest` | `{registered: boolean}` |

### 2.3 Security Architecture

#### 2.3.1 Key Hierarchy

```
BIP-39 Mnemonic (128-256 bits)
  └── AES-256-GCM encrypted with KeyStore key
       └── Stored in SharedPreferences ("encrypted_mnemonic")
       └── Key: "mdaopay_wallet_key" (AES-256, PURPOSE_ENCRYPT|DECRYPT)
              └── Biometric binding (BIOMETRIC_STRONG or DEVICE_CREDENTIAL for PIN fallback, 300s session window)
                    └── setUserAuthenticationRequired(true)
                    └── setInvalidatedByBiometricEnrollment(true)
                    └── API 30+: setUserAuthenticationParameters(300, BIOMETRIC_STRONG | DEVICE_CREDENTIAL)
                    └── Pre-API 30: setUserAuthenticationValidityDurationSeconds(300) + BiometricPrompt with DEVICE_CREDENTIAL
                    └── BiometricPrompt uses CryptoObject wrapping Cipher in DECRYPT_MODE
                    └── On InvalidKeyException → prompt biometric/PIN via BiometricPrompt

SSS Shares (K=3, N=5 or K=2, N=3 for Hermit):
  ├── Share 1: Filesystem ("shares/share1.enc") — AES-GCM with KeyStore key "mdaopay_share1_key"
  ├── Share 2: SharedPreferences — AES-GCM with KeyStore key "mdaopay_share2_key"
  ├── Share 3: Filesystem ("shares/share3.enc") — AES-GCM with KeyStore key "mdaopay_share3_key"
  └── Share 4: SharedPreferences — Dual-key scheme:
        • Local storage: AES-GCM with KeyStore key "mdaopay_share4_key" (device-bound)
        • Export: user-entered PIN → PBKDF2-HMAC-SHA256 (600k iterations) → AES-256-GCM key
        • Export flow: decrypt from Keystore → prompt PIN → re-encrypt with PIN-key → display as hex
        • Import flow: enter PIN → derive PIN-key → decrypt hex → store under Keystore key

  All share decrypt wrappers: `AEADBadTagException` caught and logged as ERROR (tamper signal).
  Non-AEAD exceptions caught and logged as WARN (transient failure).
  Previously all exceptions were silently caught returning `null` — AEADBadTagException now
  distinguished to detect data corruption / storage tampering.
```

#### 2.3.2 KeystoreKey Configuration

```
Algorithm: AES (256-bit)
Block mode: GCM
Padding: NoPadding
Tag length: 128 bits
IV length: 12 bytes (random, prepended to ciphertext)
Key purpose: ENCRYPT | DECRYPT
User authentication: Required (setUserAuthenticationRequired(true), PIN fallback via DEVICE_CREDENTIAL)
Invalidation: On biometric enrollment change (setInvalidatedByBiometricEnrollment(true))
Auth duration: 300 seconds (session window; key locked after timeout, re-auth required)
Authenticators: BIOMETRIC_STRONG | DEVICE_CREDENTIAL (API 30+) / BiometricPrompt fallback (API 28-29)
```

#### 2.3.3 Shamir Secret Sharing Parameters

```
Field: GF(256)
Irreducible polynomial: 0x11D (x^8 + x^4 + x^3 + x + 1)
Standard mode: K=3, N=5
Hermit mode: K=2, N=3
Share indices: 1 (phone), 2 (Passkey PRF), 3 (Guardian A), 4 (Guardian B), 5 (cold device / paper)
Share export format: Hex-encoded byte array
```

#### 2.3.4 Passkey PRF Key Derivation

```
User authenticates with biometrics (WebAuthn)
  → Credential Manager returns PRF evaluation result
  → CBOR-decoded authData → extract HMAC-secret output
  → Derive AES-256 key for share decryption
  
Fallback: PIN code with PBKDF2-HMAC-SHA256, ≥ 600,000 iterations
  → PIN hash comparison via `MessageDigest.isEqual()` (constant-time, prevents timing side-channel)
```

#### 2.3.5 App Security Configuration

```xml
<!-- AndroidManifest.xml -->
android:allowBackup="false"  <!-- prevents cloud backup of keystore-bound data -->
android:networkSecurityConfig="@xml/network_security_config"  <!-- cert pinning -->
```

#### 2.3.6 Biometric Authentication

**Library:** `androidx.biometric:biometric:1.4.0` (stable).

```
BiometricPrompt.PromptInfo:
  - Title: "Подтвердите действие"
  - Subtitle: "Приложите палец или Face ID"
  - Allowed authenticators: BIOMETRIC_STRONG
  - Confirmation required: false (for quick auth)

Key protection:
  - setUserAuthenticationRequired(true)
  - setUserAuthenticationValidityDurationSeconds(0)
  - setInvalidatedByBiometricEnrollment(true)
```

**F-062 — `authenticateHighRisk()`:**
- Separate authentication path for high-risk operations (send, recovery, backup export, mnemonic import)
- Requires `BIOMETRIC_STRONG` only — no `DEVICE_CREDENTIAL` (PIN) fallback
- No session window (`setUserAuthenticationValidityDurationSeconds(0)`) — forces biometric prompt every time
- Used by: `SendViewModel.send()`, `RecoveryViewModel.startRecovery()`, `RecoveryViewModel.importMnemonic()`, `WalletManager.createBackup()`
- Falls back to error if only weak biometric (BIOMETRIC_WEAK) or device credential available

#### 2.3.7 Screen-Off Lock

`AppLockManager` must lock on **both** background (via `ProcessLifecycleOwner`) AND screen-off (via `ACTION_SCREEN_OFF`). Without the screen-off trigger, an attacker with brief physical access can see balances and perform actions.

```kotlin
// Registered in Application.onCreate()
val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
registerReceiver(object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        appLockManager.forceLock()
    }
}, filter)
```

#### 2.3.8 Device Integrity

`DeviceIntegrityManager.kt` performs multi-layer integrity checks and returns a `RiskLevel` (HIGH / MEDIUM / LOW) based on aggregated signals:

| Signal | Detection | Deps |
|--------|-----------|------|
| Root detection | RootBeer library (build props, su binary, mounted partitions, dangerous apps) | `rootbeer 0.1.1` |
| Emulator detection | Build fingerprint (`generic`), `BatteryManager.isCharging` quirks, QEMU props | Android SDK |
| Play Integrity API | Google Play Integrity attestation — JWT signature verified offline via Google JWKS (SHA256withRSA, 24h key cache), nonce, timestamp (≤5 min), and package name checked | `play-integrity 1.4.0` |
| WebView security | SecureWebViewClient wrapper enforces HTTPS-only + blocks file:// and content:// | Android SDK (F-038) |

**F-060 — JWT signature verification detail:**
- Algorithm: SHA256withRSA (RS256)
- JWKS endpoint: `https://www.googleapis.com/playintegrity/v1/publicKeys`
- JWKS cache TTL: 24 hours (re-fetched on cache miss; stale cache used if fetch fails)
- Validated fields: `nonce` (matches request), `timestampMs` (≤ 5 min from now), `packageName` (matches app ID)
- Verification failure → RiskLevel HIGH (all wallet ops blocked)

**Risk tier → Action mapping:**

| Risk Level | Operations blocked | UI behaviour |
|------------|-------------------|--------------|
| HIGH (rooted device detected or Play Integrity fails) | All wallet ops: recovery, send, backup, import, onboarding | "Device does not meet security requirements" dialog, wallet refuses to load |
| MEDIUM (emulator or suspicious props, but not rooted) | Recovery start, mnemonic import, backup creation | Warning banner on balance view; small sends (<$100) allowed with additional confirmation |
| LOW (clean device) | None | Normal operation |

**Integration points:**
- `RecoveryViewModel`: checks integrity before `startRecovery()`, `importMnemonic()`, `createBackup()` — blocks on HIGH, warns on MEDIUM
- `SendViewModel`: checks integrity before `send()` — blocks on HIGH, warns + additional confirm on MEDIUM for amounts > $100

### 2.4 Onboarding Flow

```
1. Tutorial Screen (4 slides: intro, send, recover, start)
2. Biometric Enrollment Screen
   └─ Check BiometricManager.isBiometricAvailable()
   └─ If none: show warning, allow skip (MVP)
3. Nickname Selection Screen
   └─ Check availability via /nickname/check/{name}
   └─ Auto-suggest if taken
   └─ Sign registration with wallet key
4. Guardian Setup Screen
   └─ Choose Standard (3-of-5) or Hermit (2-of-3)
   └─ Invite guardians via relay
   └─ Generate SSS shares, distribute to guardians
5. Home Screen (wallet created)

Constraints:
  - Wallet creation target: < 2 minutes
  - Onboarding completion rate target: > 90%
  - Seed phrase NEVER shown to user
  - OAuth for identity verification, NOT wallet ownership
```

#### 2.4.5 Identity Connect

**ConnectModalScreen.kt** — bottom sheet for dApp session management:
- Permissions list display (read address, sign message, send transaction)
- Face ID / biometric confirmation before granting access
- Revoke session button (calls `SessionKeyModule.revokeSessionKey`)
- Session key info: dApp name, expiry, spending limit

**ConnectViewModel.kt** — MVI state management:
- State: `Idle` → `Connecting` → `PermissionsReview` → `BiometricConfirm` → `Connected` | `Error`
- Biometric auth gate: requires `BiometricAuthManager` before session creation
- Session list: queries active sessions from `SessionKeyModule`

**SessionKeyModule.sol** — on-chain scoped permissions with risk-scored dynamic limits (§3.12):
- `createSessionKey(dapp, validAfter, validUntil, permissions, spendingLimit, riskTier)` — wallet owner only; riskTier 0=LOW, 1=MEDIUM, 2=HIGH
- `revokeSessionKey(sessionId)` — wallet owner only
- `validateSessionKey(sessionId, amount)` — view, checks time bounds + dynamic spending limit (risk-scored)
- `useSessionKey(sessionId, amount)` — dApp calls, deducts from limit, updates risk metrics
- Permissions bitmap: bit 0 = read address, bit 1 = sign message, bit 2 = send transaction

### 2.5 Send Flow

```
1. Recipient Selection
   ├── Search by nickname (/nickname/check/{name})
   ├── Select from contacts (DataStore)
   └── QR code scan (ZXing)
2. Amount Entry
   └── Fiat or token amount
3. Confirmation Screen
   └── Show recipient (avatar + name), amount, network (hidden: BSC)
   └── Gas fee: hidden from user (covered by Paymaster)
4. Payment Execution
   └── Build UserOperation (ERC-4337)
   └── Sign with wallet key (ECDSA)
   └── Send to BundlerClient.estimateUserOperationGas()
    └── Send to PaymasterClient.signUserOp() → POST /v1/sign (F-130)
   └── Send to BundlerClient.sendUserOperation()
   └── Wait for confirmation
5. Result (success or error with Russian message)

Offline resilience:
  └── If network unavailable: queue to Room TxQueue
  └── WorkManager retries when online
  └── Idempotency: unique UUID per operation
```

**Constraints:**
- First payment from install: < 5 minutes
- Individual payment action: < 60 seconds
- No addresses, no networks, no gas fee visible
- Double-tap protection via UUID idempotency

### 2.6 Dynamic Island & Push Notifications

#### Notification Types

| Type | Trigger | Priority |
|------|---------|----------|
| `TxConfirmed` | Transfer confirmed on-chain | High |
| `TxFailed` | Transfer failed | High |
| `RecoveryRequested` | Recovery initiated | Urgent |
| `RecoveryApproved` | Guardian approved | Urgent |
| `GuardianInvited` | Guardian invitation received | High |
| `OnRampCompleted` | On-ramp order completed | Normal |

#### Implementation

| Component | Technology | Details |
|-----------|------------|---------|
| Push delivery | FCM (Firebase Cloud Messaging) | Android |
| Token registration | WorkManager | Registers on app start |
| Notification channels | NotificationChannels.kt | 3 channels: transactions, recovery, general |
| Badge management | BadgeManager.kt | Adaptive icon badge count |
| Dynamic Island | NotificationHelper.kt | Extended layout for Live Activities |
| Retry | WorkManager | Exponential backoff on failure |
| History | Room | Notification history table |

### 2.7 WalletConnect Integration

```
Pairing:
  └── QR code scan → URI → WalletConnect v2 session proposal
  └── Display chain, methods, and requested permissions
  └── User approves/rejects

Session management:
  └── Session persistence (DataStore)
  └── Auto-reconnect on app restart
  └── Session disconnect on timeout

Request handling:
  └── eth_requestAccounts → return smart account address
  └── personal_sign → sign with wallet key
  └── eth_sendTransaction → build UserOperation → Paymaster → Bundler
```

### 2.8 Error Mapping (User-Facing)

All blockchain errors are mapped to Russian language messages via `TxErrorMapper.kt`:

| Error | User Message |
|-------|-------------|
| Insufficient funds | "Недостаточно средств на счете" |
| Gas estimation failed | "Не удалось оценить стоимость газа. Попробуйте позже" |
| Network error | "Проверьте подключение к интернету" |
| Transaction reverted | "Транзакция отклонена сетью" |
| Nonce too low | "Транзакция устарела, попробуйте снова" |
| User rejected | "Операция отменена" |

---

## 3. Smart Contracts

### 3.1 Overview

| Contract | Standard | Location | Lines | Key Dependencies |
|----------|----------|----------|-------|-----------------|
| MDAOToken | ERC-20 (Burnable, Permit, Pausable, Ownable) | `contracts/src/MDAOToken.sol` | 70 | OpenZeppelin |
| MDAOPaymaster | IPaymaster (ERC-4337 v0.6), Pausable | `contracts/src/MDAOPaymaster.sol` | 684 | OpenZeppelin |
| SocialRecoveryModule | Custom (P-256 recovery, Ownable) | `contracts/src/SocialRecoveryModule.sol` | 505 | OpenZeppelin (Ownable) |
| NicknameRegistry | Custom (EIP-712 registry) | `contracts/src/NicknameRegistry.sol` | 97 | OpenZeppelin (ECDSA) |
| InsuranceFund | Insurance pool (Ownable) | `contracts/src/InsuranceFund.sol` | 96 | OpenZeppelin (Ownable) |
| DeadManSwitch | Inactivity switch (Ownable) | `contracts/src/DeadManSwitch.sol` | 98 | OpenZeppelin (Ownable) |
| AttestationLedger | Attestation store (Ownable) | `contracts/src/AttestationLedger.sol` | 34 | OpenZeppelin (Ownable) |
| RefundVault | Refund escrow (Ownable) | `contracts/src/RefundVault.sol` | 66 | OpenZeppelin (Ownable + SafeERC20) |
| SessionKeyModule | Session key management | `contracts/src/SessionKeyModule.sol` | 185 | None |
| **ITrustProvider** | **Verifier interface** | `contracts/src/ITrustProvider.sol` | 6 | None |
| **EcdsaVerifier** | **ECDSA-based verifier (immutable signer)** | `contracts/src/EcdsaVerifier.sol` | 22 | OpenZeppelin (ECDSA) |
| **TrustProviderRegistry** | **Provider registry (Ownable2Step)** | `contracts/src/TrustProviderRegistry.sol` | 72 | OpenZeppelin (Ownable) |
| MockP256 | Mock precompile | `contracts/test/mocks/MockP256.sol` | 14 | None |

**Compiler Version:** Solidity ^0.8.28
**Framework:** Foundry (forge)
**Libraries:** OpenZeppelin (via submodule), forge-std, account-abstraction

### 3.2 MDAOToken

#### Interface
```solidity
contract MDAOToken is ERC20, ERC20Burnable, ERC20Permit, ERC20Pausable, Ownable
```

#### Constants
| Name | Value | Description |
|------|-------|-------------|
| `MAX_SUPPLY` | `1_000_000_000 * 10**18` | Hard cap — no further minting beyond this |
| `MAX_BURN_FEE_BPS` | 1000 | Maximum burn fee: 10% (1000 basis points) |
| `BURN_ADDRESS` | `0x000...dEaD` | Fee destination |

#### State Variables
| Variable | Type | Default | Description |
|----------|------|---------|-------------|
| `burnFeeBps` | `uint256` | 50 | 0.5% burn fee on each transfer |
| `isExempt` | `mapping(address => bool)` | — | Exempt addresses (DEX, Paymaster, Factory, Treasury) |

#### Key Functions

```solidity
constructor(address initialOwner)
```
- Mints 100M tokens to `initialOwner`
- Sets `initialOwner` as exempt
- Inherits Ownable (sets owner)

```solidity
function _update(address from, address to, uint256 value) internal override
```
Applies burn fee on every transfer:
1. If `from` or `to` is exempt OR `from == address(0)` (mint) OR `to == address(0)` (burn): skip fee
2. Calculate fee = `value * burnFeeBps / 10000`
3. Burn: `super._update(from, BURN_ADDRESS, fee)`
4. Transfer remainder: `super._update(from, to, value - fee)`
5. Originates from `_mint` (constructor), `transfer`, `transferFrom`, and `burn` calls

```solidity
function mint(address to, uint256 amount) external onlyOwner
```
- Capped: `totalSupply() + amount <= MAX_SUPPLY`
- Reverts with `MaxSupplyExceeded()` if exceeded

```solidity
function setBurnFeeBps(uint256 newFee) external onlyOwner
function setExempt(address account, bool exempt) external onlyOwner
function pause() external onlyOwner
function unpause() external onlyOwner
```

#### Events
```solidity
event BurnFeeUpdated(uint256 indexed oldFee, uint256 indexed newFee);
event ExemptUpdated(address indexed account, bool exempt);
```

#### Errors
```solidity
error MaxSupplyExceeded();
error FeeTooHigh();
```

#### Burn Fee Logic (Detailed)

```
Input: transfer(from, to, value)

if exempt(from) OR exempt(to) OR from == 0 OR to == 0:
    super._update(from, to, value)     // no fee
else:
    fee = value * burnFeeBps / 10000   // 50 bps default → 0.5%
    super._update(from, BURN_ADDRESS, fee)    // burn
    super._update(from, to, value - fee)      // transfer remainder
```

### 3.3 MDAOPaymaster

#### Interface

```solidity
contract MDAOPaymaster is IPaymasterV06, Ownable, Pausable
```

**Implements:** ERC-4337 v0.6 `IPaymaster`
**EntryPoint:** `0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789` (default)

#### State Variables

| Variable | Type | Default | Description |
|----------|------|---------|-------------|
| `entryPoint` | `address immutable` | — | ERC-4337 EntryPoint address |
| `minimumDeadlineBuffer` | `uint256` | 300 | Min seconds from now for quote deadline |
| `maxTokenAmountLimit` | `uint256` | `10_000 * 10**18` | Max token amount per operation |
| `maxGasPrice` | `uint256` | 200 gwei | Max gas price accepted |
| `tokenPrice` | `mapping(address => uint256)` | — | Token/ETH price (18 decimals), owner-settable |
| `priceBufferBps` | `uint256` | 500 | 5% buffer over oracle price |
| `mdaoConfig` | `TokenConfig` | — | MDAO token + permit support |
| `usdtConfig` | `TokenConfig` | — | USDT token + permit support |
| `deprecationTimestamp` | `uint256` | 0 | Block.timestamp when deprecation started (0=active, type(uint256).max=finalized) |
| `blockFailureThreshold` | `uint256` | 3 | Consecutive PaymentFailed before block |
| `cooldownPeriod` | `uint256` | 1 hours | Cooldown after block |
| `failedPaymentCount` | `mapping(address => uint256)` | — | Per-sender failure counter |
| `blockedUntil` | `mapping(address => uint256)` | — | Per-sender block expiry |
| `emergencyAdmin` | `address` | — | Separate admin for pause/unpause |
| `EXIT_WINDOW_DURATION` | `uint256 constant` | 7 days | Exit window after initiateDeprecation |
| `registry` | `address` | `address(0)` | TrustProviderRegistry address (Step 1b); 0 = fallback to legacy trustedSigner |
| `dailyWithdrawnToday` | `uint256` | 0 | Current day's withdrawn amount (F-018) |
| `dailyWithdrawalResetAt` | `uint256` | 0 | Timestamp when daily cap resets |
| `dailyWithdrawalCapBps` | `uint256` | 5000 | Daily withdrawal cap in basis points (50% of balance default) |

#### Validation Flow (`validatePaymasterUserOp`)

```
Input: userOp, userOpHash, maxCost, paymasterData

1. Revert if deprecationTimestamp == type(uint256).max (finalized)
2. Check sender not blocked (blockedUntil[sender] <= block.timestamp)
3. Decode paymasterData → PaymasterExtra { token, maxTokenAmount, quoteDeadline, permit }
4. Validate token config (MDAO or USDT only)
5. Check quoteDeadline not expired
6. Check quoteDeadline >= block.timestamp + minimumDeadlineBuffer
7. Check maxTokenAmount <= maxTokenAmountLimit
8. Check userOp.maxFeePerGas <= maxGasPrice
9. Check EntryPoint.balanceOf(this) >= maxCost (sufficient deposit)
10. If tokenPrice configured:
      maxAllowed = maxCost * tokenPrice * (10000 + priceBufferBps) / 10000 / 1e18
      require(maxTokenAmount <= maxAllowed)
    Else if maxTokenAmount > 0: revert AmountTooHigh
11. Check sender token balance >= maxTokenAmount
12. If permit: check config.supportsPermit, execute permit
    Else: check allowance >= maxTokenAmount
13. Encode context: (sender, token, maxTokenAmount)
14. Return (context, 0) → validation passed
```

During exit window (deprecationTimestamp != 0 && < type(uint256).max), validation continues to allow users to move funds.

#### Post-Operation Flow (`postOp`)

```
Input: mode, context, actualGasCost, actualUserOpFeePerGas

1. Decode context → (sender, token, maxTokenAmount)
2. If mode == opReverted: return (userOp failed, no charge)
3. Compute charge via computeAmountToCharge(maxTokenAmount, actualGasCost, token):
      price = tokenPrice[token]
      actualTokenAmount = actualGasCost * price / 1e18
      if actualTokenAmount >= maxTokenAmount: charge = maxTokenAmount, refund = 0
      else: charge = actualTokenAmount, refund = maxTokenAmount - charge
4. Try low-level token.transferFrom(sender, this, charge)
   └─ On failure:
        emit PaymentFailed
        failedPaymentCount[sender]++
        if failedPaymentCount >= blockFailureThreshold: block sender for cooldownPeriod
        return (no revert)
5. If refund > 0: safeTransfer(sender, refund)
6. Emit GasPaid(sender, token, charge, actualGasCost)
```

#### Admin Functions & Access Control

**Owner:** Gnosis Safe multisig (min 3-of-5) — never an EOA.

**EmergencyAdmin:** Separate address (set via `setEmergencyAdmin()`) that can call `pause()`/`unpause()` without timelock.

**Timelock:** Ownership is transferred to an OZ `TimelockController` (min 2 days delay). All critical admin functions are accessed exclusively through the timelock. Direct `onlyOwner` calls from non-timelock addresses are impossible after ownership transfer.

| Function | Timelock | Reason |
|----------|----------|--------|
| `setTokenPrice()` | 2 days (via TimelockController) | Economic parameter — users need time to react |
| `withdrawTo()` | 2 days (via TimelockController) | Fund movement — monitoring window |
| `setMaxTokenAmountLimit()` | 2 days (via TimelockController) | Affects all users |
| `setMaxGasPrice()` | 2 days (via TimelockController) | Affects all users |
| `initiateDeprecation()` | 2 days (via TimelockController) | Contract lifecycle change |
| `setRegistry()` | 2 days (via TimelockController) | Changes verification provider |
| `pause()` | 0h | Emergency — no timelock (via emergencyAdmin) |
| `setPermitSupport()` | 0h | Low risk — no timelock |
| `setBlockFailureThreshold()` | 0h | Low risk — no timelock |
| `setCooldownPeriod()` | 0h | Bounded 1h–7 days (F-051) |
| `unblockSender()` | 0h | Low risk — no timelock |
| `setDailyWithdrawalCapBps()` | 2 days (via TimelockController) | Affects withdrawal limits |

**Cooldown Period Bounds (F-051):** `setCooldownPeriod(newCooldown)` reverts if `newCooldown < 1 hours || newCooldown > 7 days`. Prevents owner from setting zero cooldown (bypassing blocklist protection) or extreme cooldown (permanent blocking).

**Price Oracle Deviation Cap:** `maxPriceChangeBps = 200` (2% max change per `setTokenPrice` call) — prevents a single compromised key from setting arbitrary prices:

```solidity
uint256 public constant MAX_PRICE_CHANGE_BPS = 200;  // 2% max per update

function setTokenPrice(address token, uint256 price) external onlyOwner {
    uint256 oldPrice = tokenPrice[token];
    if (oldPrice > 0) {
        uint256 change = price > oldPrice ? price - oldPrice : oldPrice - price;
        require(change * 10000 / oldPrice <= MAX_PRICE_CHANGE_BPS, "Price change too high");
    }
    tokenPrice[token] = price;
    emit PriceUpdated(token, price);
}
```

```solidity
function setPermitSupport(address token, bool enabled) external onlyOwner
function setMinimumDeadlineBuffer(uint256) external onlyOwner
function setMaxTokenAmountLimit(uint256) external onlyOwner
function setMaxGasPrice(uint256) external onlyOwner
function setPriceBufferBps(uint256) external onlyOwner
```

#### PaymasterAndData Format

```
paymasterAndData = address(paymaster) || customData

customData:
  [0:20]   token address
  [20:52]  maxTokenAmount (uint256, big-endian)
  [52:84]  quoteDeadline (uint256, big-endian)
  [84:116] permitDeadline (uint256, optional)
  [116:117] permitV (uint8, optional)
  [117:149] permitR (bytes32, optional)
  [149:181] permitS (bytes32, optional)
```

Server suffix (appended by backend):
```
paymasterAndData || sig(v||r||s) || len(sig/2, 4 hex chars) || magic("22e325a297439656")
```

#### Events (14)

```solidity
event GasPaid(address indexed user, IERC20 indexed token, uint256 amount, uint256 gasCost);
event PaymentFailed(address indexed user, IERC20 indexed token, uint256 amount);
event PriceUpdated(address indexed token, uint256 price);
event PriceBufferUpdated(uint256 oldValue, uint256 newValue);
event MinimumDeadlineBufferUpdated(uint256 oldValue, uint256 newValue);
event MaxTokenAmountLimitUpdated(uint256 oldValue, uint256 newValue);
event MaxGasPriceUpdated(uint256 oldValue, uint256 newValue);
event EmergencyAdminUpdated(address indexed oldAdmin, address indexed newAdmin);
event SenderBlocked(address indexed sender, uint256 blockedUntil);
event SenderUnblocked(address indexed sender);
event BlockFailureThresholdUpdated(uint256 oldValue, uint256 newValue);
event CooldownPeriodUpdated(uint256 oldValue, uint256 newValue);
event DeprecationInitiated(uint256 deprecationTimestamp, uint256 exitWindowEnd);
event DeprecationFinalized(uint256 timestamp);
event WithdrawalExecuted(bytes32 indexed withdrawalHash, IERC20 indexed token, address indexed to, uint256 amount);
event DailyWithdrawalCapUpdated(uint256 oldBps, uint256 newBps);
```

#### TOCTOU Risk & Mitigations

There is a time window between `validatePaymasterUserOp` and `postOp`. During this window (mempool delay, up to `quoteDeadline` = 300s), the sender can:
- Transfer tokens out, causing `balanceOf < maxTokenAmount`
- Reduce allowance, causing `transferFrom` to fail

Both cases are handled without revert (emit `PaymentFailed`), but this allows systematic griefing — Paymaster sponsors gas for free.

**Mitigations:**

| Measure | Implementation | Status |
|---------|---------------|--------|
| Block-list | Maintain `mapping(address => uint256) failedPaymentCount`; after N=3 failures, reject in `validatePaymasterUserOp` | ✅ Implemented |
| Adaptive deadline | `quoteDeadline = min(quoteDeadline, block.timestamp + (MAX_TOKEN_AMOUNT > 1_000e18 ? 60 : 300))` | ✅ Implemented |
| Monitoring | `PaymentFailed` → Prometheus counter with PagerDuty alert if > 1% of ops fail | ✅ Documented (see §4.4) |
| Sender cooldown | After a `PaymentFailed`, cooldown period (1 hour) before `validatePaymasterUserOp` for that sender succeeds again | ✅ Implemented |
| Oracle price = 0 guard | If `tokenPrice[token] == 0`, reject any non-zero `maxTokenAmount` — prevents overcharge when oracle unconfigured | ✅ Implemented |

#### Errors (17)

```solidity
error Unauthorized();           // caller ≠ entryPoint
error InvalidToken();           // not MDAO or USDT
error InsufficientBalance();    // sender balance < maxTokenAmount
error InsufficientDeposit();    // EntryPoint deposit < maxCost
error InsufficientAllowance();  // allowance < maxTokenAmount
error PermitFailed();           // permit call failed
error TransferFailed();         // ETH deposit/withdraw failed
error QuoteExpired();           // quoteDeadline passed
error DeadlineTooSoon();        // deadline too close
error AmountTooHigh();          // maxTokenAmount > limit or oracle check
error GasPriceTooHigh();        // maxFeePerGas > maxGasPrice
error PaymentBlocked();         // sender on block-list after repeated PaymentFailed
error NotEmergencyAdmin();      // caller ≠ emergencyAdmin
error PriceChangeTooHigh();     // price change > MAX_PRICE_CHANGE_BPS
error AlreadyDeprecated();      // initiateDeprecation when already deprecated
error NotDeprecated();          // query/finalize when not deprecated
error ExitWindowActive();       // finalizeDeprecation before window ends
error ErrCooldownOutOfBounds(); // cooldown < 1h or > 7 days (F-051)
error DailyCapExceeded();       // daily withdrawal cap exceeded (F-018)
error NoTransferToZeroAddress(); // withdrawTo to address(0)
```

### 3.4 SocialRecoveryModule

#### Interface

> [DEPRECATED 2026-06-30]: Standalone contract without inheritance. Заменено на `is Ownable`.
```solidity
contract SocialRecoveryModule  // standalone, no inheritance
```

**Актуально:**
```solidity
contract SocialRecoveryModule is Ownable
```
- Наследует `Ownable` (OpenZeppelin) — владелец может обновлять `P256_VERIFIER`
- Владелец устанавливается в конструкторе: `Ownable(msg.sender)`

#### Constants

> [DEPRECATED 2026-06-30]: `P256_VERIFIER` был `public constant` с хардкодом `0x100`. Теперь это mutable `public` переменная, устанавливаемая в конструкторе и через `setP256Verifier()`.

| Name | Type | Default | Description |
|------|------|---------|-------------|
| `P256_VERIFIER` | `address public` | устанавливается в конструкторе | P-256 verifier address (RIP-7212 precompile `0x100` по умолчанию через конструктор; может быть переопределён для сетей без RIP-7212, включая `MockP256` для тестов) |
| `SHA256_PRECOMPILE` | `address public constant` | `0x0000000000000000000000000000000000000002` | SHA-256 precompile address |
| `BURN_ADDRESS` | `address public constant` | `0x000000000000000000000000000000000000dEaD` | Burn address for deposit destruction |
| `P256_P` | `uint256 public constant` | `0xffffffff00000001000000000000000000000000ffffffffffffffffffffffff` | P-256 field prime — используется для on-curve validation в `addGuardian()` |
| `TIMELOCK` | `uint256 public constant` | 48 hours | Time delay before recovery executes |
| `EXECUTION_WINDOW` | `uint256 public constant` | 48 hours | Max time after TIMELOCK to execute recovery (request expires after) |
| `GUARDIAN_THRESHOLD` | `uint256 public constant` | 2 | Approvals needed for recovery |
| `MIN_GUARDIANS_FOR_RECOVERY` | `uint256 public constant` | 2 | Minimum guardians required to start recovery |
| `MAX_GUARDIANS` | `uint256 public constant` | 3 | Maximum guardians per wallet |
| `VETO_THRESHOLD` | `uint256 public constant` | 2 | Vetoes needed to block recovery |
| `RECOVERY_DEPOSIT` | `uint256 public constant` | 10_000_000_000_000_000 (0.01 MDAO) | Deposit paid via ERC-20 transferFrom (anti-spam) |

#### Structs

```solidity
struct Guardian {
    bytes32 identityHash;     // keccak256(google:email or apple:sub)
    bytes32 pubKeyX;          // P-256 public key X coordinate
    bytes32 pubKeyY;          // P-256 public key Y coordinate
    uint256 addedAt;          // block.timestamp when added
    bool confirmed;           // guardian self-confirmed via confirmGuardian()
}

struct RecoveryRequest {
    address initiator;        // who paid the deposit
    bytes newPasskeyPubKey;   // 64 bytes (X || Y)
    uint256 startedAt;        // block.timestamp when initiated
    bool vetoed;              // true if vetoCount >= VETO_THRESHOLD
    bool executed;            // true if recovery already executed
    uint256 nonce;            // monotonic nonce per wallet
}
```

#### State Variables

| Variable | Type | Description |
|----------|------|-------------|
| `mdaoToken` | `IERC20 public immutable` | MDAO token address for recovery deposit (ERC-20 transferFrom) |
| `guardians` | `mapping(address => Guardian[MAX_GUARDIANS])` | Per-wallet guardian array |
| `guardianCount` | `mapping(address => uint256)` | Number of guardians per wallet |
| `isGuardian` | `mapping(address => mapping(bytes32 => bool))` | Quick guardian lookup |
| `ownerPasskeyHash` | `mapping(address => bytes32)` | Current passkey hash per wallet |
| `pendingRecovery` | `mapping(address => RecoveryRequest)` | Active recovery request |
| `recoveryApprovals` | `mapping(address => mapping(uint256 => mapping(bytes32 => bool)))` | Approval tracking |
| `recoveryVetoes` | `mapping(address => mapping(uint256 => mapping(bytes32 => bool)))` | Veto tracking |
| `approvalCount` | `mapping(address => mapping(uint256 => uint256))` | Approval counter |
| `vetoCount` | `mapping(address => mapping(uint256 => uint256))` | Veto counter |
| `recoveryDeposit` | `mapping(address => uint256)` | Actual deposit received per wallet (after token burn fees) |

#### Key Functions

> [DEPRECATED 2026-06-30]: Old constructor only took `_mdaoToken`. Теперь принимает `_p256Verifier` и вызывает `Ownable(msg.sender)`.
```solidity
constructor(address _mdaoToken)
```
- Sets `mdaoToken = IERC20(_mdaoToken)` — MDAO token used for recovery deposit
- Reverts if `_mdaoToken == address(0)` (validated via IERC20 cast — will revert on zero-address calls)

**Актуально:**
```solidity
constructor(address _mdaoToken, address _p256Verifier) Ownable(msg.sender)
```
- `Ownable(msg.sender)` — владелец (deployer) получает права `onlyOwner`
- Устанавливает `mdaoToken = IERC20(_mdaoToken)` — MDAO token for recovery deposit
- Устанавливает `P256_VERIFIER = _p256Verifier` — P-256 verifier address
- `require(_p256Verifier != address(0))` — guard на zero address

```solidity
function setP256Verifier(address _p256Verifier) external onlyOwner
```
- `onlyOwner` — только владелец контракта может вызвать
- `require(_p256Verifier != address(0))` — защита от zero address
- Сохраняет старый адрес в `old`, обновляет `P256_VERIFIER`
- Emits `P256VerifierUpdated(old, _p256Verifier)`
- Позволяет переключать verifier для разных сетей (например, `MockP256` для тестов, реальный RIP-7212 прекомпил для прода)

```solidity
function registerWallet(bytes32 passkeyPubKeyX, bytes32 passkeyPubKeyY) external
```
- Sets `ownerPasskeyHash[msg.sender] = keccak256(passkeyPubKeyX || passkeyPubKeyY)`
- Reverts if already registered

```solidity
function addGuardian(address wallet, bytes32 identityHash, bytes32 pubKeyX, bytes32 pubKeyY) external onlyWalletOwner(wallet)
```
- Validates: not already guardian (`ErrAlreadyGuardian`), count < MAX_GUARDIANS (`ErrMaxGuardians`), identityHash != 0 (`ErrInvalidIdentity`)
- **On-curve P-256 validation:**
  - `pubKeyX >= bytes32(P256_P) || pubKeyY >= bytes32(P256_P)` → `ErrInvalidPublicKey` (field range check)
  - `pubKeyX == bytes32(0) && pubKeyY == bytes32(0)` → `ErrInvalidPublicKey` (point-at-infinity check)
- Adds guardian with `confirmed = false`
- Guardian must call `confirmGuardian()` separately

```solidity
function confirmGuardian(address wallet) external
```
- Called by the guardian themselves (`msg.sender`)
- Вычисляет `identityHash = keccak256(abi.encodePacked(msg.sender))`
- Reverts with `ErrNotGuardian()` если не найден
- Reverts with `ErrGuardianAlreadySet()` если уже подтверждён
- Sets `guardian.confirmed = true`
- Prevents wallet owner from unilaterally adding fake guardians

```solidity
function removeGuardian(address wallet, bytes32 identityHash) external onlyWalletOwner(wallet)
```
- Validates: `guardianCount - 1 >= MIN_GUARDIANS_FOR_RECOVERY` → `ErrCannotRemove` (can't remove below threshold)
- Validates: `isGuardian[wallet][identityHash]` → `ErrNotGuardian`
- Removes guardian, reorganizes array (swap-pop to fill gap)
- Decrements `guardianCount[wallet]`, clears `isGuardian[wallet][identityHash]`

```solidity
function initiateRecovery(address wallet, bytes calldata newPasskeyPubKey) external
```
- Permissionless: anyone can initiate recovery (lost-device scenario)
- Validates: no active recovery (`ErrRecoveryAlreadyActive`), pubkey = 64 bytes (`ErrInvalidPublicKey`), guardianCount >= MIN_GUARDIANS_FOR_RECOVERY (`ErrInsufficientApprovals`), newKeyHash != oldKeyHash (`ErrInvalidNewPasskey`)
- Transfers `RECOVERY_DEPOSIT` from caller via ERC-20 transferFrom (anti-spam)
- Учитывает фактический депозит после burn fee: `actualDeposit = balanceAfter - balanceBefore`
- Starts recovery timelock (48h)

```solidity
function approveRecovery(address wallet, bytes32 guardianIdentityHash, bytes calldata authenticatorData, bytes calldata clientDataJSON, bytes calldata p256Signature) external
```
- Validates: active recovery (`ErrNoActiveRecovery`), not vetoed (`ErrRecoveryVetoed`), not executed (`ErrRecoveryExecuted`), not already approved (`ErrAlreadyApproved`)
- Guardian must be confirmed (`ErrNotGuardian`)
- Verifies WebAuthn P-256 signature: `SHA-256(authenticatorData || SHA-256(clientDataJSON))`
  - Поддерживает как 64-byte raw так и DER-encoded (70-74 bytes) signatures
- Guardian обязан иметь MDAOPay кошелёк (registered via registerWallet)
- Increments approvalCount

```solidity
function vetoRecovery(address wallet, bytes32 guardianIdentityHash, bytes calldata authenticatorData, bytes calldata clientDataJSON, bytes calldata p256Signature) external
```
- Validates: active recovery (`ErrNoActiveRecovery`), not executed (`ErrRecoveryExecuted`), not already vetoed (`ErrRecoveryVetoed` → corrected: `ErrAlreadyVetoed`), not already approved (`ErrAlreadyApproved` — can't approve + veto)
- Guardian must be confirmed (`ErrNotGuardian`)
- Verifies WebAuthn P-256 signature: `SHA-256(authenticatorData || SHA-256(clientDataJSON))`
  - Поддерживает как 64-byte raw так и DER-encoded (70-74 bytes) signatures
- Guardian обязан иметь MDAOPay кошелёк (registered via registerWallet)
- Increments vetoCount; if >= VETO_THRESHOLD, sets `req.vetoed = true` and **сжигает депозит** (transfer to BURN_ADDRESS)

```solidity
function executeRecovery(address wallet) external
```
- Validates: active recovery (`ErrNoActiveRecovery`), not vetoed (`ErrRecoveryVetoed`), not executed (`ErrRecoveryExecuted`)
- Validates: `approvalCount[wallet][nonce] >= GUARDIAN_THRESHOLD` (`ErrInsufficientApprovals`)
- Validates: `block.timestamp >= req.startedAt + TIMELOCK` (`ErrTimelockNotPassed`)
- Validates: `block.timestamp <= req.startedAt + TIMELOCK + EXECUTION_WINDOW` (`ErrRecoveryExpired`)
- Updates `ownerPasskeyHash[wallet] = newKeyHash`
- Sets `executed = true`
- **Возвращает депозит initiator'у** (transfer, не burn)
- Emits `RecoveryExecutedEv`

```solidity
function cleanupExpiredRecovery(address wallet) external
```
- Permissionless: anyone can clean up expired recovery requests (past `startedAt + TIMELOCK + EXECUTION_WINDOW`)
- Reverts with `ErrNoActiveRecovery()` если `startedAt == 0`
- Reverts with `ErrRecoveryExecuted()` если уже выполнен
- Reverts with `ErrNoExpiredRecovery()` если `block.timestamp <= startedAt + TIMELOCK + EXECUTION_WINDOW` (ещё не истёк)
- Resets `pendingRecovery[wallet]` to allow new recovery initiation
- **Сжигает депозит** (burn, anti-spam, F-131)

```solidity
function getRecoveryRequest(address wallet) external view returns (
    bytes memory newPasskeyPubKey,
    uint256 startedAt,
    uint256 approvals,
    uint256 vetoes,
    bool vetoed,
    bool executed,
    uint256 deadline,
    uint256 nonce
)
```

#### Events (11)

> [DEPRECATED 2026-06-30]: Было 10 событий. Добавлено `P256VerifierUpdated`.

```solidity
event WalletRegistered(address indexed wallet, bytes32 passkeyHash);
event GuardianAdded(address indexed wallet, bytes32 indexed identityHash, uint256 index);
event GuardianConfirmed(address indexed wallet, bytes32 indexed identityHash);
event GuardianRemoved(address indexed wallet, bytes32 indexed identityHash, uint256 index);
event RecoveryInitiated(address indexed wallet, bytes32 indexed oldPasskeyHash, bytes32 indexed newPasskeyHash, address initiator, uint256 deadline, uint256 nonce);
event ApprovalSubmitted(address indexed wallet, address indexed guardian, uint256 nonce, uint256 approvals);
event VetoSubmitted(address indexed wallet, address indexed guardian, uint256 nonce, uint256 vetoes);
event RecoveryExecutedEv(address indexed wallet, bytes32 indexed newPasskeyHash);
event RecoveryCleanedUp(address indexed wallet, uint256 depositBurned);
event DepositBurned(address indexed wallet, uint256 amount);
event P256VerifierUpdated(address indexed oldVerifier, address indexed newVerifier);
```

#### Recovery Flow Diagram

```
User loses device
  │
  ├── [New device] Recover via guardians
  │     │
  │     ├── registerWallet() — set new passkey pubkey
  │     │
  │     ├── initiateRecovery(wallet, newPubKey)
  │     │     └── Emits RecoveryInitiated
  │     │
  │     ├── Guardians approve (via app notification)
  │     │     └── approveRecovery(wallet, identityHash, p256Sig)
  │     │     └── Emits ApprovalSubmitted
  │     │
    │     ├── After GUARDIAN_THRESHOLD (2) approvals + TIMELOCK (48h):
    │     │     └── executeRecovery(wallet)
    │     │     └── Emits RecoveryExecutedEv, deposit returned to initiator
    │     │
    │     └── Wallet restored with new passkey
    │           └── Funds at same address, ownership changed
    │
    └── [Optional] Guardians can veto:
          └── vetoRecovery(wallet, identityHash, authenticatorData, clientDataJSON, p256Signature)
          └── After VETO_THRESHOLD (2) vetoes: req.vetoed = true, deposit burned
          └── Recovery can be re-initiated with new params
```

#### P-256 / WebAuthn Signature Verification

> [DEPRECATED 2026-06-30]: Старая функция `_verifyP256` принимала только 64-byte raw signature и keccak256 хеш. Заменена на `_verifyWebAuthn`, которая следует WebAuthn standard (FIDO2) — SHA-256 instead of keccak256, и принимает как raw (64-byte) так и DER-encoded (70-74 bytes) signatures.
> ```solidity
> function _verifyP256(bytes32 messageHash, bytes memory signature, bytes32 pubKeyX, bytes32 pubKeyY) internal view returns (bool)
> ```

**Актуально — WebAuthn P-256 signature verification:**

```solidity
function _verifyWebAuthn(
    bytes calldata authenticatorData,
    bytes calldata clientDataJSON,
    bytes calldata signature,
    bytes32 pubKeyX,
    bytes32 pubKeyY
) internal view returns (bool)
```

**Flow:**

```
1. clientDataHash = SHA-256(clientDataJSON)          // via precompile 0x02
2. signedData = authenticatorData || clientDataHash    // 37+ bytes
3. messageHash = SHA-256(signedData)                   // via precompile 0x02
4. Decode signature:
   ├── 64 bytes → raw r||s (no parsing needed)
   └── 70-74 bytes → DER-encoded → derToRS() → r, s
5. Call P256_VERIFIER.staticcall(abi.encodePacked(messageHash, r, s, pubKeyX, pubKeyY))
6. Return (result == 1)
```

**Поддержка DER-encoded signatures (внутренняя функция):**

```solidity
function derToRS(bytes memory der) internal pure returns (bytes32 r, bytes32 s)
```

- `der`: ASN.1 DER-encoded ECDSA P-256 signature (70-74 bytes)
- Парсит: `0x30 [totalLen] 0x02 [rLen] [r] 0x02 [sLen] [s]`
- Обрабатывает leading `0x00` когда установлен high bit (rLen/sLen = 33)
- Reverts with `ErrDerParsing` при неверном формате

**Допустимые форматы signature:**

| Формат | Длина | Распознаётся по |
|--------|-------|----------------|
| Raw r\|\|s | 64 bytes | `signature.length == 64` |
| DER-encoded | 70-74 bytes | `signature.length >= 70 && signature.length <= 74` |

#### Guardian Confirmation Flow

```
1. Wallet owner: addGuardian(wallet, identity, pubKeyX, pubKeyY)
   └── Guardian added with confirmed=false

2. Guardian: confirmGuardian(wallet)
   └── msg.sender must match identityHash = keccak256(msg.sender)
   └── Guardian now confirmed=true → can approve/veto recoveries

Rationale:
  - Prevents wallet owner from adding fake guardians
  - Guardian must actively confirm their role
  - Identity hash prevents impersonation: identityHash = keccak256(guardian's address)
```

#### Mixed Trust Model

The guardian role uses **two different keys with different trust properties:**

| Key | Purpose | Recovery | Preservation |
|-----|---------|----------|--------------|
| **EOA (msg.sender)** | `confirmGuardian()`, `addGuardian()` | Standard ECDSA (Ethereum account) | Device-bound; recovery via SocialRecoveryModule itself |
| **P-256 key** | `approveRecovery()`, `vetoRecovery()` | WebAuthn-generated P-256 key pair | Bound to biometrics via Passkey; can be re-generated |

**Source of truth:** The EOA is the authoritative identity for "who is this guardian." The P-256 key is the authorization mechanism for "this guardian approves this recovery." If the P-256 key is lost:
- The guardian must be re-invited with a new P-256 key
- The wallet owner calls `removeGuardian()` + `addGuardian()` with new key
- Old P-256 key can no longer approve/veto

**Test coverage requirements:**
- Approve with valid P-256 + invalid EOA → reject
- Confirm with valid EOA → P-256 approve
- Lost P-256 → re-invite flow works
- Same EOA, different P-256 → cannot approve old request

#### Errors (22)

> [DEPRECATED 2026-06-30]: Было 20 ошибок. Добавлены `ErrNoExpiredRecovery`, `ErrDerParsing`.

```solidity
error ErrAlreadyRegistered();         // wallet already has passkey
error ErrAlreadyGuardian();           // identityHash already guardian
error ErrNotGuardian();               // not a guardian for this wallet
error ErrMaxGuardians();              // guardianCount >= MAX_GUARDIANS
error ErrInvalidIdentity();           // identityHash == 0
error ErrInvalidPublicKey();          // pubKeyX/Y == 0 or P-256 field range or point-at-infinity
error ErrNoActiveRecovery();          // startedAt == 0
error ErrRecoveryAlreadyActive();     // startedAt != 0 && !executed && !vetoed
error ErrAlreadyApproved();           // already approved
error ErrRecoveryVetoed();            // req.vetoed == true
error ErrRecoveryExecuted();          // req.executed == true
error ErrTimelockNotPassed();         // block.timestamp < startedAt + TIMELOCK
error ErrRecoveryExpired();           // block.timestamp > startedAt + TIMELOCK + EXECUTION_WINDOW
error ErrInvalidSignature();          // P-256 verification failed
error ErrAlreadyVetoed();             // already vetoed
error ErrInsufficientApprovals();     // guardianCount < THRESHOLD or approvalCount < GUARDIAN_THRESHOLD
error ErrUnauthorized();              // msg.sender != wallet owner
error ErrCannotRemove();              // guardianCount - 1 < MIN_GUARDIANS_FOR_RECOVERY
error ErrInvalidNewPasskey();         // newKey == oldKey (same passkey)
error ErrGuardianAlreadySet();        // guardian already confirmed
error ErrNoExpiredRecovery();         // cleanupExpiredRecovery called before expiry
error ErrDerParsing();                // DER-encoded signature parse failure
```

### 3.5 NicknameRegistry

#### Interface

```solidity
contract NicknameRegistry  // standalone
```

#### Constants & Structs

```solidity
bytes32 constant REGISTRATION_TYPEHASH = keccak256("Registration(address wallet,uint256 nonce)");

struct Registration {
    address wallet;
    uint256 nonce;
}
```

#### State Variables

| Variable | Type | Description |
|----------|------|-------------|
| `nicknameToAddress` | `mapping(bytes32 => address)` | Identity hash → wallet address |
| `addressToNickname` | `mapping(address => bytes32)` | Wallet address → identity hash |
| `nonces` | `mapping(address => uint256)` | Per-wallet nonce for replay protection |

#### Key Functions

```solidity
function register(bytes calldata signature) external
```
1. Read current nonce from `nonces[msg.sender]`
2. Compute EIP-712 digest: `keccak256(EIP712_DOMAIN || REGISTRATION_TYPEHASH || Registration(wallet, nonce))`
3. Recover signer via `ECDSA.recover(digest, signature)`
4. Require `signer == msg.sender` (anti-frontrunning) and `signer != address(0)`
5. Increment nonce
6. Compute identity hash: `keccak256(abi.encodePacked(signer))`
7. Store `nicknameToAddress[identityHash] = signer`
8. Store `addressToNickname[signer] = identityHash`
9. Emit `NicknameRegistered`

```solidity
function resolve(bytes32 nicknameHash) external view returns (address)
function domainSeparator() public view returns (bytes32)
```

#### EIP-712 Domain

```solidity
domainSeparator = keccak256(
    "EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)",
    "MDAOPay",
    "1",
    block.chainid,
    address(this)
)
```

#### Events & Errors

```solidity
event NicknameRegistered(bytes32 indexed nicknameHash, address indexed wallet);
error NicknameAlreadyTaken(bytes32 nicknameHash);
error NicknameNotRegistered(bytes32 nicknameHash);
error InvalidSigner();
error InvalidNonce();
```

#### Identity Model

```
On-chain:
  identityHash = keccak256(abi.encodePacked(signer))
  └── Immutable, deterministic, tied to the signing key
  └── Maps 1:1 to wallet address
  └── NOT a human-readable name

Off-chain (backend):
  Human-readable nickname: "crazy-cherry" (3-20 chars, [a-zA-Z0-9_-])
  └── Maps to wallet address via REST API
  └── Stored in PostgreSQL + Redis cache
  └── Duplicate resolution: auto-suffix "username #2215"
```

### 3.6 InsuranceFund

#### Interface

```solidity
contract InsuranceFund is Ownable
```

Insurance pool that collects 0.5% fees and handles user claims. Claims require 3+ auditor signatures for submission, plus owner approval.

> **F-052 (unverified signatures):** `auditorSignatures` in `submitClaim()` are accepted as-is without on-chain verification. The contract lacks an `auditors` mapping and signature verification logic. Signature verification is delegated to off-chain processes (backend validates before calling `approveClaim`). This is a known limitation — on-chain auditor signature verification is planned for a future upgrade.

#### Constants

| Name | Value | Description |
|------|-------|-------------|
| `FEE_BPS` | 50 | 0.5% fee on each operation |
| `MAX_CLAIM_BPS` | 1000 | Max 10% of pool per claim |

#### State Variables

| Variable | Type | Description |
|----------|------|-------------|
| `auditor` | `address public immutable` | Auditor address — ECDSA-signed approval required for claim submission |
| `totalFunds` | `uint256` | Accumulated fee pool |
| `claims` | `mapping(address => uint256)` | Per-victim total claimed |

#### Key Functions

```solidity
constructor(address _auditor) Ownable(msg.sender)
```
- Sets `auditor = _auditor` (immutable) — the auditor address that can authorize claims via ECDSA signature verification
- Reverts if `_auditor == address(0)`

```solidity
function collectFee(address from, uint256 amount) external
```
- Called by Paymaster (or any approved caller)
- Calculates `fee = amount * FEE_BPS / 10000`
- Adds to `totalFunds`

```solidity
function submitClaim(address victim, uint256 amount, bytes32 bugReportHash, bytes[] calldata auditorSignatures) external
```
- Requires 3+ auditor signatures
- Validates amount <= totalFunds and amount <= maxClaim (10% of pool)

```solidity
function approveClaim(address victim, uint256 amount) external onlyOwner
function rejectClaim(bytes32 bugReportHash) external onlyOwner
function withdrawFunds(address to, uint256 amount) external onlyOwner
```

#### Events

```solidity
event FeeCollected(address indexed from, uint256 amount, uint256 fee);
event ClaimSubmitted(address indexed victim, uint256 amount, bytes32 bugReportHash);
event ClaimApproved(address indexed victim, uint256 amount, uint256 totalClaimed);
event ClaimRejected(address indexed victim, bytes32 bugReportHash);
event FundWithdrawn(address indexed to, uint256 amount);
```

#### Errors

```solidity
error ErrInsufficientFunds();
error ErrClaimLimitExceeded();
error ErrInvalidAuditApproval();
error ErrNoFeeCollected();
error ErrTransferFailed();
```

### 3.7 DeadManSwitch

#### Interface

```solidity
contract DeadManSwitch is Ownable, ReentrancyGuard
```

Inactivity-based recovery: if wallet owner does not call `ping()` within `inactivityPeriod` (min 90 days), the beneficiary can trigger recovery. After a 7-day CHALLENGE_PERIOD, the beneficiary can claim ETH. The owner can challenge the trigger within the challenge period to reset the switch.

#### State Machine

```
Active ──(inactivity met, beneficiary triggers)──→ Triggered ──(challenge period elapsed, beneficiary claims)──→ Claimable
  ↑                                                     │
  └──────(owner challenges, resets)─────────────────────┘
```

#### Constants

| Name | Value | Description |
|------|-------|-------------|
| `MIN_INACTIVITY` | 90 days | Minimum inactivity period |
| `CHALLENGE_PERIOD` | 7 days | Window for owner to challenge trigger |

#### State

```solidity
enum State { Active, Triggered, Claimable }

struct SwitchConfig {
    address beneficiary;
    uint256 inactivityPeriod;
    uint256 lastActivity;
    bool active;
    bool claimed;
}

mapping(address => uint256) public deposits;
mapping(address => State) public recoveryState;
mapping(address => uint256) public triggerAt;
```

#### Key Functions

```solidity
constructor() Ownable(msg.sender)
```

```solidity
function setSwitch(address beneficiary, uint256 inactivityPeriod) external
```
- Sets beneficiary (must differ from owner)
- Floors `inactivityPeriod` at `MIN_INACTIVITY`
- Initializes `lastActivity = block.timestamp`

```solidity
function ping() external
function changeBeneficiary(address newBeneficiary) external
function deactivate() external
```

```solidity
function triggerRecovery(address wallet) external
```
- Only beneficiary can call
- Requires `recoveryState[wallet] == State.Active`
- Verifies inactivity period has passed
- Sets `recoveryState[wallet] = State.Triggered`, records `triggerAt[wallet] = block.timestamp`

```solidity
function challengeTrigger() external
```
- Only wallet owner (msg.sender) can call for their own wallet
- Requires `recoveryState[msg.sender] == State.Triggered`
- Resets to `State.Active`, clears `triggerAt[msg.sender]`
- Allows owner to cancel a fraudulent/accidental trigger

```solidity
function claimFunds(address wallet) external nonReentrant
```
- Transfers per-wallet `deposits[wallet]` to beneficiary (not entire contract balance)
- Requires `recoveryState[wallet] == State.Triggered`
- Requires `block.timestamp >= triggerAt[wallet] + CHALLENGE_PERIOD`
- Sets `recoveryState[wallet] = State.Claimable`
- Uses CEI pattern: zeros deposit before send

```solidity
receive() external payable
```
- Tracks deposits per-wallet: `deposits[msg.sender] += msg.value`

#### Events

```solidity
event SwitchSet(address indexed wallet, address indexed beneficiary, uint256 inactivityPeriod);
event ActivityPinged(address indexed wallet, uint256 timestamp);
event BeneficiaryChanged(address indexed wallet, address indexed newBeneficiary);
event SwitchTriggered(address indexed wallet, address indexed beneficiary);
event SwitchDeactivated(address indexed wallet);
event FundsClaimed(address indexed wallet, address indexed beneficiary, uint256 amount);
event TriggerChallenged(address indexed wallet);
```

#### Errors

```solidity
error ErrNotBeneficiary();
error ErrInactivityNotMet();
error ErrAlreadyClaimed();
error ErrBeneficiarySameAsOwner();
error ErrTransferFailed();
error ErrNotExpired();
error ErrSwitchNotActive();
error ErrNoDeposits();
error ErrNotTriggered();
```

### 3.8 AttestationLedger

On-chain attestation registry. Only the owner can record attestations (`attest()` is `onlyOwner`). Anyone can verify attestation hashes.

```solidity
contract AttestationLedger is Ownable
```

> **F-050:** `attest()` is protected by `onlyOwner` — prevents unauthorized attestation submissions. The owner is set to `msg.sender` in the constructor.

| Function | Access | Description |
|----------|--------|-------------|
| `attest(bytes32 subject, bytes32 attestationHash, string calldata metadata)` | `onlyOwner` | Record attestation hash on-chain (F-050) |
| `verify(bytes32 attestationHash) → bool` | anyone | Check if hash exists |
| `verifyBatch(bytes32[] calldata) → bool[]` | anyone | Batch check |

```solidity
event AttestationRecorded(address indexed submitter, bytes32 indexed subject, bytes32 attestationHash, uint256 timestamp, string metadata);
```

### 3.9 RefundVault

Escrow for user refunds. Owner deposits, user claims one-time.

```solidity
contract RefundVault is Ownable
```

| Function | Access | Description |
|----------|--------|-------------|
| `depositRefund(address user, IERC20 token, uint256 amount)` | `onlyOwner` | Deposit refund, accumulates if exists |
| `claimRefund(IERC20 token)` | User self-service | Claim full accumulated amount |
| ~~`withdrawToken(IERC20 token, uint256 amount)`~~ | ~~`onlyOwner`~~ | **Intentionally removed (F-008)** — owner cannot withdraw user refunds. Funds are only claimable by users. `totalRefundsPending[token]` tracks pending amounts per token. |

```solidity
event RefundDeposited(address indexed user, IERC20 indexed token, uint256 amount);
event RefundClaimed(address indexed user, IERC20 indexed token, uint256 amount);
```

```solidity
error ErrNoRefundDue();
error ErrAlreadyRefunded();
error ErrTransferFailed();
error ErrInvalidAddress();
```

### 3.10 Gas Optimization Notes

| Contract | Optimization | Gas Saved |
|----------|-------------|-----------|
| MDAOToken | Exempt check before fee calculation | ~5k per exempt transfer |
| MDAOPaymaster | Low-level call instead of safeTransferFrom in postOp | ~2k per op |
| MDAOPaymaster | Direct `token.transferFrom` selector | ~500 per op |
| SocialRecoveryModule | `staticcall` for P-256 verification | ~10k per verify |
| SocialRecoveryModule | Guardian array vs mapping for iteration | Storage dependent |
| NicknameRegistry | Single SLOAD for nonce | ~2.1k per register |

### 3.11 Upgradeability & Migration Playbook

**Contracts are NOT upgradeable** (no proxy, no UUPS). This is an intentional trade-off:

| Pro | Con |
|-----|-----|
| No governance attack surface | Every bugfix requires migration |
| Immutable guarantees for users | New contracts = new addresses in backend/mobile |
| Simpler audit scope | Users must trust new deployment |
| No storage collision risk | No storage migration complexity |

#### Migration trigger criteria

Migration is warranted when:
- Critical vulnerability discovered (re-entrancy, access control bypass, fund loss)
- Breaking change required by upstream dependency (EntryPoint upgrade)
- Tokenomic parameter that cannot be changed via setter functions (e.g., `MAX_SUPPLY`)

Minor parameter changes (`burnFeeBps`, `priceBufferBps`, `maxTokenAmountLimit`) do NOT require migration — use existing `onlyOwner` setters.

#### Migration playbook

1. **Pause** — `MDAOToken.pause()` (inherited from OpenZeppelin). For MDAOPaymaster: deploy a new version before draining old one.
2. **Deploy** — new contract version via Forge script (`script/Deploy*.s.sol`). Verify on Etherscan immediately.
3. **Drain old Paymaster** — `withdrawTokens()` and `withdraw()` from old MDAOPaymaster to treasury.
4. **Update backend** — `AppConfig.kt` → new contract addresses. Deploy via Cloud Run blue-green (zero-downtime).
5. **Update mobile** — `NetworkConfig.kt` → new addresses. Release via Play Store staged rollout (25% → 50% → 100%).
6. **Update ADR** — document the migration in `PRD/Appendix E. Architectural Decisions Record (ADR).md`.
7. **Communicate** — in-app banner for users on old contract version to update their app.
8. **Clean up** — after 90 days of < 1% traffic on old contract, revoke its role from backend service account.

**For MDAOToken specifically:** `pause()` → transfer remaining state (if any) → renounce ownership of old token.
**For MDAOPaymaster:** old deposits must be fully drained via `withdraw()` / `withdrawTokens()` before pointing backend to new address. Backend checks `entryPoint.balanceOf(paymaster)` at startup.
**For SocialRecoveryModule:** in-flight recoveries on the old module become invalid after migration — document in UI with clear error message. Guardians must re-initiate recovery on the new module.

#### Deprecation & Exit Window

Contracts also support an on-chain deprecation lifecycle:

| Function | Description |
|----------|-------------|
| `initiateDeprecation()` | Start 7-day exit window (onlyOwner, 48h timelock) |
| `isDeprecated()` → bool | Returns `deprecationTimestamp != 0` |
| `exitWindowEnd()` → uint256 | Returns `deprecationTimestamp + 7 days` |
| `finalizeDeprecation()` | After 7 days: fully disable contract (onlyOwner) |

During the exit window, all normal operations continue (users can move funds). After `finalizeDeprecation()`, `deprecationTimestamp = type(uint256).max` and all `validatePaymasterUserOp` calls revert with `Unauthorized`.

#### CI automation

Add CI step that fires after contract deployment:
1. Read new addresses from Forge script output (JSON)
2. Update Secret Manager entries (or backend config map)
3. Create PR in infra repo with updated addresses
4. Tag deploy commit for traceability

> **ADR required:** Add explicit ADR documenting non-upgradeable decision and migration playbook before mainnet.

### 3.12 SessionKeyModule

#### Interface

```solidity
contract SessionKeyModule  // standalone, no inheritance
```

Manages session keys for dApp-specific spending permissions with time bounds and spending limits.

#### Structs

```solidity
struct SessionKey {
    address owner;          // wallet that created the session
    address dapp;           // authorized dApp address
    uint256 validAfter;     // earliest activation timestamp (set to block.timestamp by contract)
    uint256 validUntil;     // expiry timestamp
    bytes32[] permissions;  // array of allowed selectors (e.g., 0xa9059cbb for transfer)
    uint256 spendingLimit;  // max spend per session (wei)
    uint256 spent;          // accumulated spend
    bool revoked;           // manual revocation flag
    uint8 riskTier;         // 0=LOW, 1=MEDIUM, 2=HIGH
    uint256 successCount;   // successful operations (for dynamic limit)
    uint256 lastAmount;     // last operation amount (spike detection)
    uint256 lastUsedAt;     // timestamp of last use (time-decay)
}
```

#### Key Functions

| Function | Access | Description |
|----------|--------|-------------|
| `createSessionKey(address dapp, uint256 validUntil, bytes32[] permissions, uint256 spendingLimit, uint8 riskTier)` | wallet owner | Create a new session key with risk tier (0=LOW, 1=MEDIUM, 2=HIGH). `validAfter` is set to `block.timestamp` by the contract. |
| `revokeSessionKey(bytes32 sessionId)` | wallet owner | Revoke session key |
| `validateSessionKey(bytes32 sessionId, bytes32 permission, uint256 amount)` | view | Check if session is valid and within dynamic spending limit (risk-scored) |
| `useSessionKey(bytes32 sessionId, bytes32 permission, uint256 amount)` | dApp | Deduct from dynamic limit, update successCount/lastAmount/lastUsedAt, spike detection |
| `getSessionKey(bytes32 sessionId)` | view | Return session key data (including riskTier, successCount, lastAmount, lastUsedAt) |

**Risk scoring:** LOW tier + successCount > 10 → +20% dynamic limit. Spike detection: amount > 2×lastAmount resets successCount. Time-decay: +1 successCount per idle day (cap 30).

**Lines:** 185 | **Tests:** 20

### 3.13 Deployment Parameters

| Contract | Constructor Args | Notes |
|----------|-----------------|-------|
| MDAOToken | `initialOwner` | Mints 100M tokens |
| MDAOPaymaster | `entryPoint`, `mdao`, `usdt`, `trustedSigner` | Validates extcodesize > 0 for entryPoint. Sets `trustedSigner` as immutable signer for EIP-712 quote verification. |
| SocialRecoveryModule | `mdaoToken` | Sets MDAO token address for recovery deposits (ERC-20 transferFrom). `P256_VERIFIER` hardcoded as `0x000...100` (RIP-7212). Use MockP256 for testnets that don't support RIP-7212. |
| NicknameRegistry | none | Stateless |
| InsuranceFund | `auditor` | Sets immutable auditor address for claim signature verification. Ownable(msg.sender). |
| DeadManSwitch | none | Ownable(msg.sender) |
| AttestationLedger | none | Ownable(msg.sender) |
| RefundVault | none | Ownable(msg.sender) |

**Network:** BSC (Chain ID: 56) for MVP
**EntryPoint:** `0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789` (v0.6, same on all chains)

---

## 4. Infrastructure & DevOps

### 4.1 Cloud Infrastructure

| Service | Provider | Configuration | Cost Tier |
|---------|----------|---------------|-----------|
| Container orchestration | Cloud Run | Regional (us-central1 / europe-west1) | Pay-per-request |
| Database | Cloud SQL (PostgreSQL) | Single-zone (HA planned) | Production |
| Cache | Memorystore HA (Redis) | Standard tier | Production |
| CDN / WAF | Cloudflare | DDoS protection, edge caching | Pro/Business |
| Secrets | Secret Manager | Cloud Run + Secret Manager | Pay-per-secret |
| Container registry | Artifact Registry | Docker images | Pay-per-storage |

### 4.2 RPC Multi-Provider Architecture

```
App/Mobile ──► RpcProviderManager
                  │
                  ├── Primary: publicnode.com (eth_getBalance, eth_call)
                  ├── Fallback 1: ankr.com
                  └── Fallback 2: chainstack.com
                  
Backend ──► RpcProviderManager (same pattern as mobile)
              ├── Provider 0: configured via RPC_URLS[0] env var
              ├── Provider 1: configured via RPC_URLS[1] env var
              └── Provider N: configured via RPC_URLS[N] env var
              └── RpcProviderManager.getBestProvider() iterates providers, returns first healthy
              └── Error tracking: 3 consecutive failures → provider cooldown 30s, then re-tried
              └── Health check: eth_blockNumber every 60s (lazy on each call)
              └── Env: RPC_URLS = "<url1>,<url2>,<url3>" (backward compat: RPC_URL = single URL)
```

**RPC Provider Configuration:**
- Timeout: 15s per provider
- Health check: every 60s via `eth_blockNumber`
- Failover: automatic on consecutive failures

### 4.2.1 Request Idempotency

| Endpoint | Mechanism | Key | TTL |
|----------|-----------|-----|-----|
| `POST /sign` | RedisReplayCache | `idempotency:sign:{sender}:{nonce}` | 60 min |
| `POST /nickname/register` | RedisReplayCache | `replay:nickname:{address}:{nonce}` | 60 min |

Both endpoints check for duplicate requests on the first processing line (before calling business logic). If the key exists, the request is rejected with "Duplicate request" or "Signature already used". This prevents double-spending from client retries.

### 4.3 CI/CD Pipeline

**File:** `.github/workflows/ci.yml`

**Triggers:** push to `main`, PR to `main`

**Jobs (parallel):**

| Job | Runner | Toolchain | Command | Cache |
|-----|--------|-----------|---------|-------|
| `contracts` | ubuntu-latest | foundry-toolchain | `forge test -vvv` | `~/.foundry/cache`, `~/.svm` (key: `foundry-<lockfile>`) |
| `backend` | ubuntu-latest | JDK 21 (temurin) | `./gradlew :backend:test` | `~/.gradle/caches`, `~/.gradle/wrapper` (key: `gradle-<os>-<gradleKts+wrapper>`) |
| `app` | ubuntu-latest | JDK 21 (temurin) | `./gradlew assembleDevDebug` | Same as backend (shared key) |
| `relay` | ubuntu-latest | Node 20 | `npm ci && npm test` | npm cache (key from `relay/package-lock.json`) |

**Constraints:** 30 min timeout per job, no external dependencies, all standard GitHub Actions.

#### Contract Address Auto-Update

After successful `forge script Deploy.s.sol`:
1. Parse deployed addresses from `broadcast/Deploy.s.sol/<chainId>/run-latest.json`
2. Update `AppConfig.kt` defaults (backend) and `NetworkConfig.kt` (mobile)
3. Create PR with `gh pr create --title "Update contract addresses"`
4. Approval required before merge to main

| Variable | Source | Updated In |
|----------|--------|------------|
| `PAYMASTER_ADDRESS` | Forge broadcast JSON | `AppConfig.kt`, `NetworkConfig.kt` |
| `MDAO_ADDRESS` | Forge broadcast JSON | `AppConfig.kt`, `NetworkConfig.kt` |
| `USDT_ADDRESS` | Forge broadcast JSON | `AppConfig.kt`, `NetworkConfig.kt` |

#### Security Scanning

| Scan Type | Frequency | Tool | Trigger |
|-----------|-----------|------|---------|
| SAST | Every PR | Detekt + Semgrep | `on: pull_request` |
| DAST | Weekly | OWASP ZAP (staging) | `on: schedule` |
| Dependency | Daily | Dependabot + Trivy | `on: schedule` |
| Container | Every build | Trivy + Cosign verify | `on: push` |
| Pen test | Quarterly | External firm | Manual |
| Smart contract audit | Pre-mainnet + major changes | Trail of Bits / OpenZeppelin | Manual |

### 4.4 Monitoring & Alerting

#### Metrics (Prometheus + Grafana)

| Metric | Threshold | Alert | Channel |
|--------|-----------|-------|---------|
| API latency p50 | < 200ms | — | Grafana dashboard |
| API latency p99 | < 2s | > 2s over 5m → PagerDuty | PagerDuty |
| Error rate | < 1% | > 1% over 5m → PagerDuty | PagerDuty |
| DB connections | < 80% pool | > 80% → Slack | Slack |
| RPC health | all healthy | > 3 failures → Slack | Slack |
| Tx confirmation | < 3 min | > 3 min → Slack | Slack |
| Redis memory | < 80% | > 80% → Slack | Slack |
| Paymaster balance | above 1 BNB (or $600 equivalent) | below threshold → PagerDuty | PagerDuty |
| Paymaster auto-refill | < 1.5 BNB → treasury deposit() | automated via cron job (every 6h) | Slack notification |

#### Logging (Loki)

```
Structured JSON logging via Logback
Fields: timestamp, level, logger, message, requestId, sender, error, duration_ms

Log levels:
  INFO: request start/end, price fetched, signature created
  WARN: stale cache used, rate limit hit, chain mismatch
  ERROR: failed signature, price fetch failure, RPC error
```

### 4.5 Security Posture

#### Mobile Security

| Measure | Implementation | Status |
|---------|---------------|--------|
| minSdk | 26 | ✅ |
| Backup prevention | `allowBackup="false"` | ✅ |
| Network security | `network_security_config.xml` (cert pinning) | ✅ |
| Obfuscation | ProGuard/R8 + DexGuard (release) | ✅ |
| Biometric auth | AndroidX Biometric (BIOMETRIC_STRONG) | ✅ |
| Key storage | Android Keystore (AES-256-GCM) | ✅ |
| Passkey PRF | WebAuthn + HMAC-secret extension | ✅ |
| Device integrity | Play Integrity API (warning level, MVP) | ⚠ Phase 2: blocking |
| Root detection | RootBeer (warning level, MVP) | ⚠ Phase 2: blocking |

#### Smart Contract Security

| Measure | Implementation |
|---------|---------------|
| Reentrancy | Checks-effects-interactions pattern, no external calls in critical paths |
| Access control | Ownable (DAO token), onlyEntryPoint (Paymaster), onlyWalletOwner (Recovery add/remove guardian, registerWallet; initiateRecovery is permissionless) |
| DoS prevention | Fixed loops (MAX_GUARDIANS=5), bounded operations |
| Signature replay | Nonce per wallet (NicknameRegistry), nonce per request (RecoveryModule) |
| Price manipulation | On-chain price bounds (Paymaster), maxTokenAmountLimit |
| Frontrunning | signer == msg.sender check (NicknameRegistry) |
| Extcodesize check | Constructor validation (Paymaster only; RecoveryModule uses hardcoded RIP-7212 0x100) |
| Emergency pause | Pausable (MDAO token) |

#### Key Rotation

| Key | Rotation | Mechanism |
|-----|----------|-----------|
| Paymaster private key | 90 days | Automated via secret rotation |
| API keys | 90 days | GCP Secret Manager rotation |
| Guardian identity salt | Per-wallet | Derived from user's key material |
| JWT signing key | 90 days | Automated via KMS key rotation |

### 4.6 Backup & Recovery

#### RTO/RPO Targets

| Component | RTO | RPO | Mechanism |
|-----------|-----|-----|-----------|
| Backend (stateless) | < 2 min | N/A | Cloud Run multi-instance, zero-downtime deploy |
| Database (Cloud SQL) | < 15 min | < 5 min | PITR (point-in-time recovery), daily snapshots |
| Redis | < 5 min | < 1 min | AOF + RDB persistence, daily backups to GCS |
| Smart Contracts | Immutable | N/A | Contracts are non-upgradeable; drain + redeploy on migration |
| Mobile | User-driven | N/A | Recovery via SSS shares + guardian approval |

#### Database Backup

- **Frequency:** Automated daily snapshot at 02:00 UTC
- **Retention:** 30 daily snapshots, 12 monthly snapshots
- **PITR:** Enabled with 7-day lookback window
- **Restore testing:** Quarterly automated restore to staging environment

#### Redis Backup

- **AOF:** Append-only file, fsync every second
- **RDB:** Snapshot every 5 minutes if ≥ 100 keys changed
- **GCS:** Daily backup of AOF + RDB to `gs://mdaopay-backups/redis/`
- **Restore:** Automated script: restore RDB → replay AOF → verify key count

#### Recovery Procedures

| Scenario | Procedure | Responsible |
|----------|-----------|-------------|
| Pod crash | Cloud Run auto-restarts (< 30s) | Automated |
| DB corruption | PITR restore to latest healthy point | SRE (on-call) |
| Redis data loss | Restore from RDB + AOF; replay recent idempotency keys | SRE (on-call) |
| Contract bug | Emergency pause (MDAO token) → deploy new → drain → update configs | Dev team |
| Full region outage | Cloud SQL cross-region replica → DNS switch | SRE (on-call) |
| Key compromise | Rotate key → update Secret Manager → redeploy | Security team |

### 4.7 Dev Tooling & Code Quality

#### Pre-commit Hooks

File: `.pre-commit-config.yaml`

| Hook | Source | Scope |
|------|--------|-------|
| `trailing-whitespace` | `pre-commit/pre-commit-hooks` | All files |
| `end-of-file-fixer` | `pre-commit/pre-commit-hooks` | All files |
| `check-yaml` | `pre-commit/pre-commit-hooks` | `*.yml`, `*.yaml` |
| `ktlint` | Local (system) | `*.kt` |
| `solhint` | Local (system) | `*.sol` |
| `eslint` | Local (system) | `*.ts`, `*.tsx` |

Local hooks run via system-installed binaries (no extra pre-commit language runtimes). Each developer installs tools globally: `ktlint`, `solhint`, `eslint`.

#### EditorConfig

File: `.editorconfig`

| Glob | Setting | Value |
|------|---------|-------|
| `*` | indent | 4 spaces |
| `*` | line ending | LF |
| `*` | charset | UTF-8 |
| `*` | trailing whitespace | trimmed |
| `*.md` | trailing whitespace | preserved |
| `*.{yml,yaml}` | indent | 2 spaces |
| `Makefile` | indent | tab |

#### Code Quality Tools

| Tool | Language | Config | Enforcement |
|------|----------|--------|-------------|
| ktlint | Kotlin | `.editorconfig` rules | CI gate (GitHub Actions) |
| solhint | Solidity | `solhint.json` | CI gate |
| eslint | TypeScript | `eslint.config.js` | CI gate |
| detekt | Kotlin | `detekt.yml` | CI gate (SAST) |
| forge fmt | Solidity | `foundry.toml` | CI gate |

### 4.8 Docker Compose Local Development

Full-stack local environment via `docker compose` at project root.

#### Services

| Service | Image / Build | Port | Depends On | Healthcheck |
|---------|--------------|------|------------|-------------|
| `postgres` | `postgres:16-alpine` | 5432 | — | `pg_isready -U mdaopay` |
| `redis` | `redis:7-alpine` | 6379 | — | `redis-cli ping` |
| `backend` | `./backend` (Gradle + JDK 21) | 8080 | postgres, redis | `curl -f http://localhost:8080/health` |
| `relay` | `./relay` (Node 20 + node dist/index.mjs) | 8787 | backend | `wget --spider http://localhost:8787/` |

#### Volumes

| Volume | Purpose |
|--------|---------|
| `postgres_data` | Persistent PostgreSQL data |
| `redis_data` | Persistent Redis data |

#### Environment Variables

Copy `.env.example` → `.env` and fill in:

| Variable | Source | Notes |
|----------|--------|-------|
| `DATABASE_URL` | `postgresql://mdaopay:mdaopay@postgres:5432/mdaopay` | Compose-internal hostname |
| `REDIS_URL` | `redis://redis:6379` | Compose-internal hostname |
| `PAYMASTER_PRIVATE_KEY` | User | ECDSA private key (hex) |
| `RELAY_SECRET` | User | HMAC shared secret for relay |
| `CHAIN_ID` | 56 | BSC mainnet |
| `EXPECTED_CHAIN_ID` | 56 | Must match on-chain |
| `ALLOW_LOCAL_SIGNING` | true | Dev-only: env-var signing instead of KMS |
| `RPC_URL` | User | BSC JSON-RPC endpoint |

#### Commands

```bash
# Start all services
docker compose up -d

# Rebuild after code changes
docker compose up -d --build

# View logs
docker compose logs -f backend
docker compose logs -f relay

# Stop and remove volumes
docker compose down -v
```

#### Service Startup Order

```
postgres ──┐
            ├──► backend ──► relay
redis ──────┘
```

Healthchecks enforce ordering: `backend` waits for both `postgres` and `redis` to be healthy; `relay` waits for `backend` to be healthy.

---

## 5. Testing Strategy

### 5.1 Unit Tests

#### Smart Contracts (Foundry)

| Test File | Tests | Coverage Area |
|-----------|-------|---------------|
| `SocialRecoveryModule.t.sol` | 37 | Guardian lifecycle, recovery flow, veto, timelock, cleanupExpiredRecovery (F-049), edge cases |
| `MDAOPaymaster.t.sol` | 58 | Validate with approve/permit, postOp, access control, admin, deprecation, blocklist, cooldown bounds (F-051), TimelockController integration, TrustProviderRegistry integration, withdrawTo daily cap |
| `TrustProviderRegistry.t.sol` | 11 | Register provider, set status, verify, ECDSA verifier, access control, edge cases |
| `NicknameRegistry.t.sol` | 9 | Registration, resolve, frontrunning, duplicate prevention |
| `InsuranceFund.t.sol` | 14 | Fee collection, claim submit/approve/reject, withdrawal, unverified signatures (F-052), edge cases |
| `DeadManSwitch.t.sol` | 24 | Set switch, beneficiary, ping, trigger, claim, deactivate, challenge trigger, challenge period, per-wallet deposits, CEI pattern, ReentrancyGuard, F-072 two-phase trigger tests |
| `AttestationLedger.t.sol` | 4 | Attest, verify, batch verify |
| `RefundVault.t.sol` | 9 | Deposit, claim, accumulate, owner withdrawal, edge cases |
| `MDAOPaymaster.fuzz.sol` | 3 | computeAmountToCharge, maxTokenAmount, deadline validation |
| `MDAOPaymaster.invariant.sol` | 3 | maxGasPrice, maxTokenAmountLimit, minimumDeadlineBuffer |

Key test scenarios:
- Guardian operations: add, confirm, remove, max guardians, duplicate
- Recovery: initiate, approve, veto, execute, timelock enforcement
- Paymaster: token validation, gas price checks, quote expiry, permit flow
- Paymaster Timelock: admin functions via TimelockController, direct call reverts, withdrawTo with daily cap
- Paymaster Registry: uses TrustProviderRegistry, inactive provider rejected, fallback to trustedSigner
- TrustProviderRegistry: register provider, verify with active provider, verify with unregistered/deprecated, duplicate registration, access control, set status, EcdsaVerifier happy path + wrong signer
- Token: burn fee calculation, exempt addresses, MAX_SUPPLY cap, pause
- DeadManSwitch: testTriggerAfterInactivity, testOwnerCanChallengeTrigger, testHeirCanClaimAfterChallengePeriod, testCannotTriggerIfOwnerActive, testCannotClaimDuringChallengePeriod (F-072)
- DeadManSwitch per-wallet deposits: deposits tracked per-address, CEI pattern in claimFunds, ReentrancyGuard protection (F-048)

#### Backend (JUnit) — 64 tests, 0 failures

| Test File | Tests | Coverage Area |
|-----------|-------|---------------|
| `NicknamePolicyTest.kt` | 22 | Valid, TooShort, TooLong, InvalidChars, Reserved (10 reserved words), edge cases, sealed class exhaustiveness |
| `PriceOracleTest.kt` | 15 | Median pricing, CircuitBreaker states (CLOSED→OPEN→HALF_OPEN→CLOSED), sanity checks, multi-source, fallback |
| `NicknameServiceTest.kt` | 7 | Valid ECDSA registration, wrong signer, replay, expired nonce, duplicate name, on-chain verification |
| `PaymasterUtilTest.kt` | 7 | hexToBigInt, addPaymasterSuffix, DexPrices validity, hex encoding |
| `PaymasterServiceTest.kt` | 5 | Happy path (MDAO), chain mismatch, nonce gap, oracle failure, insufficient balance |
| `AuthRateLimiterTest.kt` | 4 | login 5/min/IP, register 3/min/IP, refresh 10/min/IP, Redis fallback (F-054) |
| `IpExtractorTest.kt` | 4 | X-Forwarded-For trusted/untrusted, X-Real-IP fallback, Cloudflare ranges (F-054) |

#### Mobile (JUnit)

| Test File | Tests | Coverage Area |
|-----------|-------|---------------|
| `ShamirSecretSharingTest.kt` | 17 | Split/join N-of-M, order independence, edge cases |
| `GF256Test.kt` | 18 | Field arithmetic, algebraic properties, reference match |
| `RecoveryShareManagerTest.kt` | 10 | 4-shares split, 3-of-4 recovery, roundtrip |
| `PaymasterClientTest.kt` | 5 | Encode/decode, V normalization, length checks, signUserOp (F-130) |
| `EthereumProviderInjectorTest.kt` | 6 | Origin validation, domain whitelist, bridge cleanup, user confirmation (F-059) |
| `DeviceIntegrityManagerTest.kt` | 8 | JWT signature verify, JWKS cache, nonce/timestamp/package validation, risk levels (F-060) |
| `BiometricManagerTest.kt` | 5 | authenticateHighRisk BIOMETRIC_STRONG, weak biometric rejection, session window (F-062) |

### 5.2 Integration Tests

**Backend:**
- Full Paymaster flow: mock Web3j → real DexScreenerClient → verify signature
- Nickname registration: real ECDSA sign → verify recovery

**Mobile:**
- KeystoreCrypto: encrypt → decrypt roundtrip (requires emulator with KeyStore)
- KeystoreCrypto: biometric key requires auth (verify InvalidKeyException without BiometricPrompt)
- WalletManager: generate → save → load → verify key derivation
- PasskeyManager: create → authenticate → derive PRF key
- OfflineSyncWorker: empty queue → success; single queued tx → sent + removed; send failure → retry count (TestListenableWorkerBuilder)
- Room MigrationTestHelper: version 1 → latest (schema exported to `app/schemas/`)

**Backend:**
- RpcProviderManager: multiple providers, failover on timeout, health check, error tracking with cooldown
- Sign idempotency: duplicate sender+nonce → 400 response
- Nickname registration idempotency: duplicate signature → already used

### 5.3 E2E Tests (Maestro)

**Test files:**
| File | Flow | Assertions |
|------|------|------------|
| `e2e/onboarding.yaml` | Tutorial → biometric → nickname → guardian setup | Onboarding complete, home screen reached |
| `e2e/send.yaml` | Select recipient → amount → confirm → verify pending | Tx pending state displayed |
| `e2e/receive.yaml` | Display QR → copy address | Address copied to clipboard |

**Run:**
```bash
maestro test e2e/
```

**Smoke tests (every PR):**
1. Onboarding: tutorial → biometric → nickname → guardian setup
2. Send: select recipient → enter amount → confirm → verify pending
3. Receive: display QR → copy address

**Full suite (release only):**
1. Complete send flow: nickname search → payment → on-chain confirmation
2. Recovery flow: lose device → guardian approval → execute
3. Guardian operations: invite → accept → approve recovery → veto
4. Offline resilience: queue tx → restore connectivity → confirm
5. WalletConnect: pair → sign → send transaction

### 5.4 Security Tests

| Test Type | Scope | Frequency |
|-----------|-------|-----------|
| SAST | Backend (Detekt), Contracts (Slither) | Every PR |
| DAST | Backend endpoints (OWASP ZAP) | Weekly |
| Fuzz | Smart contracts (Foundry fuzz) | Every PR |
| Invariant | Smart contracts (Foundry invariant) | Release |
| Penetration | Full system | Quarterly |
| Smart contract audit | All contracts | Pre-mainnet + major changes |

### 5.5 Load Tests

**Test files:**
| File | Scenario | Target RPS | Expected p95 |
|------|----------|-----------|--------------|
| `backend/load-tests/sign.js` | `POST /sign` — paymaster signing | 50 RPS | < 500ms |
| `backend/load-tests/nickname.js` | `GET /nickname/resolve` | 500 RPS | < 50ms |

**Run:**
```bash
k6 run backend/load-tests/sign.js
k6 run backend/load-tests/nickname.js
```

**All scenarios:**
| Scenario | Target RPS | Expected p95 | Notes |
|----------|-----------|--------------|-------|
| /sign | 50 RPS | < 500ms | Gas estimation bottleneck (see notes below) |
| /nickname/resolve | 500 RPS | < 50ms | Cache hot (Redis) |
| /health | 100 RPS | < 10ms | No state |
| Concurrent recovery | 10 parallel | < 30s each | Guardian notification bottleneck |

**/sign latency optimization strategy:**

Steps 3-6 of the validation pipeline (price fetch, balance/allowance checks, MDAO/USDT checks) are **independent RPC calls** and are executed in parallel:

```
Sequential (original):             Parallel (current):
  3. Price fetch ──┐                 3. Price fetch ──┐
  4. Gas cost      │                 4. Gas cost      │
  5. MDAO balance ─┤         →      5. MDAO balance ──┼─── all in parallel
  6. MDAO allow. ──┤                 6. MDAO allow. ──┤ via coroutineScope
  5b. USDT bal. ───┤                 5b. USDT bal. ───┘   async {}
  6b. USDT allow. ─┘                
  Total: ~5 RPC hops                Total: ~1 RPC hop (longest of 4 parallel eth_call)

Additionally: 4 eth_call requests (MDAO balance + allowance, USDT balance + allowance)
can be combined into 1 HTTP round-trip via JSON-RPC batch (optional optimization, not yet implemented).
```
- Use `eth_call` batching (batch JSON-RPC) for parallel balance/allowance queries
- Cache DexScreener prices in Redis (already specified)
- Defer signing to background coroutine: validate synchronously, sign asynchronously

### 5.6 Coverage Baseline

| Contract | Line % | Branch % | Function % | Statement % |
|----------|--------|----------|------------|-------------|
| AttestationLedger | 100% | 100% | 100% | 100% |
| DeadManSwitch | 100% | 89% | 64% | 100% |
| InsuranceFund | 100% | 87% | 50% | 100% |
| MDAOPaymaster | 83% | 83% | 60% | 81% |
| MDAOToken | 77% | 89% | 89% | 57% |
| NicknameRegistry | 100% | 96% | 75% | 100% |
| RefundVault | 100% | 100% | 100% | 100% |
| SessionKeyModule | 95% | 92% | 67% | 100% |
| SocialRecoveryModule | 93% | 89% | 59% | 100% |
| TrustProviderRegistry | 100% | 92% | 100% | 100% |
| EcdsaVerifier | 100% | 100% | 100% | 100% |
| **TOTAL** | **84%** | **83%** | **64%** | **83%** |

> Note: Low Function % in some contracts is due to internal helper functions that are only exercised indirectly through public entry points (e.g., `_verifyP256`, `_computeAmountToCharge`). Branch % gaps typically represent edge-case reverts (e.g., zero amounts, overflow guards).
>
> **Wave 11-12 additions:** DeadManSwitch +5 tests (F-072 two-phase trigger), MDAOPaymaster +1 test (F-051 cooldown bounds), SocialRecoveryModule +1 test (F-049 cleanupExpiredRecovery), InsuranceFund +1 test (F-052 unverified signatures).
> **Wave 13 additions:** TrustProviderRegistry +11 tests, EcdsaVerifier +2 tests, MDAOPaymaster +3 integration tests (Timelock + Registry). 3 new contracts: ITrustProvider, EcdsaVerifier, TrustProviderRegistry. Coverage recalculation pending `forge test --coverage` run.

### 5.7 PermissionMapper (Client-side Capability Mapping)

**File:** `app/src/main/java/com/mdaopay/app/feature/connect/domain/PermissionMapper.kt`

Maps raw Ethereum selectors from `SessionKeyModule.permissions` to human-readable intents for ConnectModal UI.

| Selector | Capability |危险 |
|----------|-----------|------|
| `0xa9059cbb` | PAYMENTS_SEND | No |
| `0x095ea7b3` | APPROVE_TOKENS | **Yes** |
| `0x42842e0e` | NFT_TRANSFER | **Yes** |
| `0x70a08231` | BALANCE_READ | No |
| `0x06fdde03` | PROFILE_READ | No |
| Unknown | UNKNOWN | **Yes** |

**Design decision:** Capability Mapping implemented client-side (Kotlin) rather than on-chain (Solidity enum). Rationale:
- Contract uses `bytes32[] permissions` (flexible, gas-efficient)
- UI maps selectors to human-readable intents
- Dangerous permissions (APPROVE, NFT_TRANSFER) trigger red warning in ConnectModal
- Allows UI updates without contract migration

**Tests:** 7 unit tests in `PermissionMapperTest.kt`

---

## 6. Deployment & Environments

### 6.1 Environment Overview

| Environment | Purpose | Domain | Backend URL | RPC Network | Chain ID |
|-------------|---------|--------|-------------|-------------|----------|
| `dev` | Local development | localhost:8080 | localhost:8080 | BSC Testnet (test) | 97 |
| `staging` | Integration testing | staging.mdaopay.com | api.staging.mdaopay.com | BSC Testnet (test) | 97 |
| `prod` | Production | app.mdaopay.com | api.mdaopay.com | BSC (mainnet) | 56 |

### 6.2 Mobile Build Variants

| Flavor | Backend URL | App Name | Analytics |
|--------|-------------|----------|-----------|
| `dev` | `10.0.2.2:8080` (emulator) | "MDAOPay Dev" | Disabled |
| `staging` | `api.staging.mdaopay.com` | "MDAOPay Staging" | Debug |
| `prod` | `api.mdaopay.com` | "MDAOPay" | Production |

### 6.3 Deployment Pipeline

#### Backend
```
commit → build JAR → build Docker image → push to Artifact Registry
  → Cloud Run deploy (staging: automatic, prod: manual approval)
  → Blue-green deployment (zero downtime)
  → Health check (3 consecutive /health OK) → switch traffic
  → Rollback: trigger previous revision
```

#### Smart Contracts
```
forge test (all pass) → forge script (deploy to testnet)
  → Integration tests pass → forge script (deploy to mainnet)
  → Verify on block explorer (forge verify-contract)
  → Update deployment addresses in:
    - backend/.env
    - mobile/NetworkConfig.kt
    - ADR documentation
```

#### Mobile
```
assembleRelease → upload to Google Play Console
  → Internal testing (dev)
  → Closed alpha (staging)
  → Open beta (pre-prod)
  → Production rollout (gradual, 10% → 50% → 100%)
```

### 6.4 Rollback Strategy

| Component | Rollback Method | RTO | RPO |
|-----------|----------------|-----|-----|
| Backend | Cloud Run revision switch | < 2 min | N/A (stateless) |
| Database | Point-in-time recovery | < 15 min | < 5 min |
| Smart contracts | Admin functions (pause, set fee) | < 1 min | N/A (immutable) |
| Mobile | Google Play staged rollout revert | < 4 hours | N/A |

### 6.5 Initial Contract Deployment

**Script:** `script/Deploy.s.sol` — deploys all contracts in sequence (MDAOToken → MDAOPaymaster → SocialRecoveryModule → NicknameRegistry → InsuranceFund → DeadManSwitch → AttestationLedger → RefundVault → SessionKeyModule) with post-deploy configuration (exempts, token prices, initial deposit).

**Deploy:**
```bash
# Рекомендуется (keystore, ключ не в истории shell):
cast wallet import deployer --interactive
forge script script/Deploy.s.sol --account deployer --sender <addr> --rpc-url $RPC_URL --broadcast

# Альтернатива (env var — ключ остаётся в ~/.bash_history):
forge script script/Deploy.s.sol --rpc-url $RPC_URL --broadcast
```

> **Security:** KMS remote signing для PAYMASTER_PRIVATE_KEY (см. §1.2). Для deployer-ключа используйте `cast wallet import` вместо `export` — это предотвращает попадание ключа в shell history и переменные окружения. В CI/CD используйте repository secrets, не хардкодьте ключи в скриптах.

**Post-deploy steps:**
```
Step 1: Verify on block explorer (forge verify-contract)
Step 2: Update backend config (AppConfig.kt — contract addresses)
Step 3: Update mobile NetworkConfig.kt
Step 4: Deposit ETH into Paymaster via deposit()
```

---

## Appendix A: Key Numbers Reference

| Parameter | Value | Source |
|-----------|-------|--------|
| SSS GF(256) irreducible poly | 0x11D | PRD #16 |
| SSS standard threshold | K=3, N=5 | PRD #16, ADR-006 |
| SSS hermit threshold | K=2, N=3 | PRD #16 |
| PBKDF2 iterations | ≥ 600,000 | PRD #16 |
| JWT access token TTL | 15 min | PRD #18 |
| JWT refresh token TTL | 30 days | PRD #18 |
| API rate limit (per IP) | 100 req/min | PRD #18 |
| API rate limit (per user) | 1,000 req/min | PRD #18 |
| Paymaster max gas price | 200 gwei | ADR-009 |
| Paymaster max token limit | 10,000 * 10^18 | ADR-009 |
| Paymaster price buffer | 5% (500 bps) | ADR-009 |
| Paymaster quote deadline buffer | 300s | ADR-009 |
| MDAO max supply | 1,000,000,000 | PRD #20 |
| MDAO burn fee (default) | 0.5% (50 bps) | PRD #20 |
| MDAO max burn fee | 10% (1000 bps) | PRD #20 |
| Recovery TIMELOCK | 48 hours | PRD #16, ADR-006 |
| Recovery MAX_EXECUTION_WINDOW | 7 days | Code — §3.4 |
| Recovery threshold | 3-of-5 | PRD #16 |
| Veto threshold | 3-of-5 | ADR-006 |
| Max guardians | 5 | PRD #16 |
| Guardian confirmation | Required | ADR-006 |
| Insurance fund fee | 0.5% (50 bps) | Code — §3.6 |
| Insurance fund max claim | 10% of pool | Code — §3.6 |
| Dead man switch inactivity | 90 days minimum | Code — §3.7 |
| Dead man switch CHALLENGE_PERIOD | 7 days | Code — §3.7, F-072 |
| Dead man switch deposits | Per-wallet mapping | Code — §3.7, F-048 |
| Paymaster exit window | 7 days | Code — §3.11 |
| Paymaster block failure threshold | 3 failures | Code — §3.3 |
| Paymaster cooldown period | 1 hour | Code — §3.3 |
| Paymaster daily withdrawal cap | 50% (5000 bps) of balance | Code — §3.3, F-018 |
| Paymaster registry fallback | address(0) = legacy trustedSigner | Code — §3.3 |
| TimelockController min delay | 2 days | External audit Step 1a |
| EcdsaVerifier signer | Immutable in constructor | TrustProviderRegistry.sol |
| Nickname length | 3-32 chars | Code (PRD #6 says 3-20 — TBD align) |
| Nickname charset | `[a-zA-Z0-9_]` | Code (PRD #6 includes `-` — TBD align) |
| Nickname resolve latency target | < 50ms | PRD #6 |
| API p95 latency target | < 200ms | PRD #18 |
| Recovery completion target | < 10 min | PRD #16 |
| Android minSdk | 26 | PRD #15 |
| Android targetSdk | 35 | build.gradle.kts |
| AES key size | 256 bits | KeystoreCrypto.kt |
| GCM tag length | 128 bits | KeystoreCrypto.kt |
| GCM IV length | 12 bytes | KeystoreCrypto.kt |
| Animation timing (majority) | 120-320ms | PRD #13 |
| Haptic H1 (soft) | 20% intensity | PRD #13 |
| Haptic H2 (medium) | 35% intensity | PRD #13 |
| Haptic H3 (strong success) | 60% intensity | PRD #13 |
| Haptic H4 (critical error) | 40% intensity | PRD #13 |
 | Chain ID (BSC production) | 56 | ADR-005 |
| Chain ID (BSC Testnet test) | 97 | ADR-005 |
| EntryPoint (v0.6) | `0x5FF...2789` | ERC-4337 canonical |
| Gradle dependency locking | Lock all configurations | `build.gradle.kts` — run `./gradlew dependencies --write-locks` after each dep change |
| Slither SAST (contracts) | Added to CI (`ci.yml` contracts job) | Runs `slither . --exclude naming-convention,pragma-version` on every PR |
| web3j version | 4.14.0 | Latest is 5.0.3 (Jun 2026) — major version jump; verify API compat before bumping; revisit pre-mainnet |
| DexScreener cache (fresh) | 30s | PaymasterService.kt |
| DexScreener cache (stale) | 120s | PaymasterService.kt |
| Nickname nonce window | 300s | NicknameService.kt |
| EIP-191 prefix (NicknameService) | `\x19Ethereum Signed Message:\n${len}` | C-4 fix — prevents cross-chain replay |
| Signature s-malleability | Reject `s > secp256k1n/2` | EIP-2, C-4 fix |
 | PIN comparison | `MessageDigest.isEqual()` | C-5 fix — constant-time, no timing side-channel |
| Auth HMAC | Per-call `Mac.getInstance()` | C-6 fix — `Mac` NOT thread-safe, shared instance caused race conditions |
| Auth password hash | `Base64.decode()` before comparison | C-7 fix — compared Base64 string UTF-8 bytes vs raw decoded bytes (always mismatch) |
| Relay HMAC verify | `crypto.subtle.timingSafeEqual` | C-8 fix — `===` string compare leaks timing |
| Identity hash derivation | `HKDF-extract(salt, userId) → HKDF-expand(info="MDAOPay-guardian-identity")` | H-2 fix — domain separation via info tag |
| Identity salt storage | Separate `SharedPreferences("mdaopay_identity_salt")` | H-2 fix — privilege separation from guardian DataStore |
| BIP39 checksum validation | `MnemonicUtils.generateSeed(phrase, "")` in `importMnemonic()` | H-10 fix — reject invalid mnemonics upfront |
| AEAD tamper detection | `catch (e: AEADBadTagException)` logged as error | H-5 fix — distinguish tampering from other failures |
| Share data format | `encrypt()` returns `iv(12) + ciphertext`; `decrypt()` extracts iv(12) + ciphertext | H-6 fix — GCM requires IV for decryption |
| NicknameGenerator RNG | `SecureRandom.nextInt(list.size)` | M-4 fix — replaces `List.random()` (java.util.Random) |
| Metrics token comparison | `MessageDigest.isEqual()` | M-7 fix — constant-time auth |
| `EXPECTED_CHAIN_ID` | Required (non-nullable) | Paymaster `fromEnv()` — error if missing, chain-validated on every `/sign` |
| Paymaster signing hash | `keccak256("\x19\x01" \|\| domainSeparator \|\| structHash)` via `computeEIP712QuoteHash()` | F-034: EIP-712 Quote signing — cross-chain replay via domainSeparator, per-sender replay via nonce |
| Nonce max gap | 20 | Reject `reqNonce > onChainNonce + 20` — prevents DoS via far-future nonces |
| Idempotency TTL | 3600s (sign + nickname) | RedisReplayCache — matches max bundler inclusion window |
| PaymasterClient quoteDeadline | `now + 300s` (independent of permitDeadline) | Prevents stale quote reuse when permitDeadline is far in future |
| Paymaster price oracle price=0 | Reject if `maxTokenAmount > 0` | Prevents overcharge when oracle price not configured |
| Relay `executed` flag | Removed — relay tracks only approval count | On-chain status is the single source of truth |
| Backend RPC error tracking | 3 consecutive errors → 30s cooldown | Prevents stuck providers from degrading latency |
| Relay auth bypass | `return err('Server misconfigured', 500)` when `RELAY_SECRET` unset | SEC-24-01 — prevents full auth bypass if env var missing |
| Paymaster s-malleability | Use `ECDSA.recover` from OpenZeppelin | SEC-24-02 — rejects malleable signatures via `s > secp256k1n/2` |
| Recovery chain ID binding | `block.chainid` in approve/veto message hash | SEC-24-03 — prevents cross-chain recovery replay |
| MockP256 chain guard | `require(block.chainid != 1 && != 56)` in `verify()` | SEC-24-04 — prevents mock in production |
| Refund transfer safety | Low-level call for refund in `postOp` | SEC-24-05 — prevents `postOp` revert blocking EntryPoint |
| Minimum deadline buffer | `if (newBuffer < 60) revert` | SEC-24-06 — prevents owner disabling quote expiry |
| Nonce gap production | `MAX_NONCE_GAP = 20` (was 100) | SEC-24-07 — reduces DoS vector per sender |
| Fallback price logging | `log.warn("FALLBACK_PRICES used — oracle returned zero values on testnet (chainId={})", chainId)` | SEC-24-08 — monitors oracle degradation on testnet |
| Recovery chain ID binding | `block.chainid` in approve/veto message hash + test mocks | SEC-24-03 update — test helpers now include chainId |
| Price buffer cap | `if (newBuffer > 2000) revert` | SEC-25-01 — max 20% buffer prevents price protection bypass |
| Max token amount cap | `if (newLimit > 1M * 10**18) revert` | SEC-25-02 — prevents unbounded per-operation charges |
| Max gas price cap | `if (newMaxGasPrice > 1000 gwei) revert` | SEC-25-03 — prevents owner from disabling gas price protection |
| Context-aware error sanitization | `LogSanitizer.sanitizeError(e)` categorizes exceptions: DB (code, state), Network, Timeout, Invalid JSON, HTTP status, Internal class | FP-LOG-001 — replaces blanket sanitize (SEC-26-05, SEC-27-xx) with categorized safe messages |
| LogSanitizer.sanitizeAddress | Shows first 6 + last 4 hex chars, masks short strings | FP-LOG-001 — preserves correlation without leaking full address (replaces SEC-26-02) |
| LogSanitizer.sanitizeHash | Shows first 8 chars of tx hash | FP-LOG-001 — enables tx correlation without leaking full hash (replaces SEC-26-01) |
| WatchtowerService.kt logs | 7 catch blocks use `reason={}` with `LogSanitizer.sanitizeError(e)`, debug-level stack in `isDebugEnabled`. Recovery wallet via `sanitizeAddress()` in WARN, full in webhook payload | FP-LOG-001 — context-aware: initial block, poll, event poll, parse, recovery poll, balance, webhook |
| Application.kt error logs | 4 catch blocks: GasEstimationException, Exception, Etherscan proxy, Nickname registration | FP-LOG-001 — context-aware error details for ops team |
| RedisClient.kt connection log | `Failed to connect to Redis reason={}` with sanitized error | FP-LOG-001 — categorizes connection failures without leaking URI |
| Database.kt migration log | `Migration failed reason={}` with DB error code + state | FP-LOG-001 — preserves SQLState for diagnostics without leaking schema |
| NicknameService.kt Redis load | `Failed to load nicknames from Redis reason={}` | FP-LOG-001 — categorizes Redis failure without leaking key details |
| SwapService.kt error logs | 2 catch blocks: quote + execution use `LogSanitizer.sanitizeError(e)`. txHash via `sanitizeHash()` in INFO, full in DEBUG | FP-LOG-001 — categorized swap errors, restores txHash correlation in execution log |
| SwapRoutes.kt + OnrampRoutes.kt | 4 catch blocks use `LogSanitizer.sanitizeError(e)` | FP-LOG-001 — categorized API error logging for quote/order/swap |
| FiatOnrampService.kt errors | 2 catch blocks: order + status check | FP-LOG-001 — categorized MoonPay provider errors |
| Auth audit: /sign API key | `MessageDigest.isEqual` constant-time comparison | SEC-28-01 — API key auth is secure |
| Auth audit: /metrics token | `MessageDigest.isEqual` constant-time comparison | SEC-28-02 — metrics auth is secure |
| Auth audit: relay HMAC | `constantTimeEqual` in auth.ts | SEC-28-03 — relay auth is secure |
| Auth audit: rate limiting | IP + sender rate limits on /sign | SEC-28-04 — DoS protection active |
| Chaos: Redis SPOF | In-memory fallback for rate limiting and replay cache when Redis unavailable | SEC-28-05 — ✅ fail-closed with ConcurrentHashMap fallback (F-032, F-033) |
| Chaos: graceful shutdown | No Runtime.addShutdownHook for Watchtower | SEC-28-06 — add shutdown hook |
| Formal verification: invariants | 3 invariants for MDAOPaymaster | SEC-28-07 — basic coverage exists |
| Formal verification: fuzzing | 3 fuzz tests for MDAOPaymaster | SEC-28-08 — basic coverage exists |
| Performance: HikariCP | Pool size updated 10→25, may bottleneck at 1000 RPS | SEC-28-09 — ✅ F-125 fixed |
| UX: touch targets | MDAOButton Sm=38dp < 48dp minimum | SEC-28-10 — fix to 48dp |
| UX: contentDescription | ProductCard, ErrorIcon, QR code missing | SEC-28-11 — add semantic labels |
| DeadManSwitch two-phase trigger | `State { Active, Triggered, Claimable }`, `challengeTrigger()`, `CHALLENGE_PERIOD = 7 days` | F-072 — owner can challenge trigger within challenge period |
| DeadManSwitch per-wallet deposits | `mapping(address => uint256) deposits`, CEI pattern, `nonReentrant` on `claimFunds` | F-048 — prevents cross-wallet theft |
| SocialRecoveryModule cleanupExpiredRecovery | Permissionless cleanup, burns deposit (F-131 burn, F-049 acknowledged) | F-131 — burn via MDAOToken.burn() instead of transfer |
| MDAOPaymaster cooldown bounds | `setCooldownPeriod()` capped 1h–7 days, `ErrCooldownOutOfBounds` | F-051 — prevents zero/arbitrary cooldown |
| InsuranceFund unverified sigs | `auditorSignatures` accepted without on-chain verification | F-052 — off-chain verification only |
| Auth rate limits | login: 5/min/IP, register: 3/min/IP, refresh: 10/min/IP via RedisRateLimiter | F-054 — DoS protection for auth |
| IP proxy-safe extraction | `extractClientIp()` with X-Forwarded-For, X-Real-IP, Cloudflare + RFC1918 trust list | F-054 — rate limit bypass via IP spoofing fixed |
| EthereumProviderInjector security | Origin validation + domain whitelist, bridge cleanup on navigation, user confirmation dialog | F-059 — prevents dApp phishing via WebView bridge |
| DeviceIntegrityManager JWT verify | SHA256withRSA (RS256), Google JWKS with 24h cache, nonce/timestamp/packageName validation | F-060 — prevents forged Play Integrity tokens |
| BiometricManager authenticateHighRisk | `BIOMETRIC_STRONG` only, no DEVICE_CREDENTIAL fallback, zero session window | F-062 — high-risk ops require strong biometric |
| Relay FCM env parameter | `FCM_SERVER_KEY` read from env instead of hardcoded global | F-065 — prevents key exposure in source |

### CLAIMED_FIXED — Wave 11-12

| Finding | Status | Component | TDD Ref |
|---------|--------|-----------|---------|
| F-048 (per-wallet deposits) | CLAIMED_FIXED | DeadManSwitch.sol | §3.7 |
| F-054 (auth rate limiting) | CLAIMED_FIXED | Application.kt | §1.2.1 |
| F-059 (WebView origin validation) | CLAIMED_FIXED | EthereumProviderInjector.kt | §2.2.2 |
| F-060 (Play Integrity JWT verify) | CLAIMED_FIXED | DeviceIntegrityManager.kt | §2.3.8 |
| F-065 (FCM env parameter) | CLAIMED_FIXED | relay/fcm.ts | §4.8 |

---

## Appendix B: ADR Cross-Reference

| ADR | Title | Decision | TDD Section |
|-----|-------|----------|-------------|
| ADR-003 | Token as Utility, not Access | MDAO not required for product use | 3.2 |
| ADR-004 | Centralized vs ENS Nicknames | PostgreSQL + Redis, NOT ENS | 1.2.2, 1.6 |
| ADR-005 | Chain Selection | BSC for MVP | 6.1 |
| ADR-006 | Recovery Module Parameters | K=3, N=5, TIMELOCK=48h | 3.4 |
| ADR-009 | Paymaster Fee Model | Token deduction, max caps | 3.3 |
| ADR-010 | Mobile Platform Priority | Android first, iOS Phase 2 | 2 |
| ADR-011 | Name Display Convention | Without @ in UI, @ in search | 1.2.2 |
| ADR-012 | ERC-4337 Interface Version | Custom v0.6 IPaymasterV06, submodule at v0.9 | 3.3, foundry.lock |

**ADR-012 rationale:** Contract defines its own `IPaymasterV06` interface (v0.6-compatible, EntryPoint `0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789`) instead of importing from submodule `lib/account-abstraction` (tagged v0.9.0). This avoids forced migration to v0.7 EntryPoint — ABI-compatible, test coverage 83%, no regression. Revisit before mainnet: align submodule tag with actual interface version.
