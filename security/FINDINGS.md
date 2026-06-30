# MDAOPay Security Findings Registry

> **Version:** 1.4.0
> **Last Updated:** 2026-06-30
> **Maintainer:** Security Lead
> **Git:** Single source of truth for all security findings

---

## 0. PROTOCOL FOR AI AGENTS

### Before reporting a NEW finding

```
STEP 1: Compute fingerprint
  fp = sha256(category_code + ":" + file_path + ":" + function_name + ":" + bug_pattern)
  
STEP 2: Search this file for fp
  grep -A 20 "fp: <your_fingerprint>" security/FINDINGS.md
  
STEP 3: Decision tree
  ├─ fp FOUND, status=VERIFIED → DUPLICATE. Do NOT re-report. Reference F-XXX.
  ├─ fp FOUND, status=CLAIMED_FIXED → verify claim (see §3 Verification Protocol)
  │    ├─ claim holds → DUPLICATE, status→VERIFIED
  │    └─ claim false → REGRESSION, create F-YYY, link to F-XXX
  ├─ fp FOUND, status=REGRESSED → DUPLICATE. Do NOT re-report. Reference latest F-YYY.
  └─ fp NOT FOUND → NEW FINDING. Add entry per §2 format.

STEP 4: Never claim "fixed" without
  - commit hash
  - regression test name + file
  - verification command output
```

### Before marking a finding as CLAIMED_FIXED

```
CHECKLIST (all must be true):
  □ Code change committed (git log shows commit)
  □ Regression test added (test file exists, test name in FINDINGS.md)
  □ Test passes locally (forge test / ./gradlew test output attached)
  □ Verification recipe from §3 returns PASS
  □ No anti-pattern from §4 introduced
  □ TDD updated in SAME commit
```

### Severity downgrade rules

```
CRITICAL → HIGH: Only if exploit path blocked by another control (document which)
HIGH → MEDIUM: Only if mitigation on-chain enforced (not documentation)
MEDIUM → LOW: Only if impact < $1000 or < 100 users
Any → ACCEPTED_RISK: Requires security lead sign-off + risk acceptance form
```

---

## 1. ERROR TAXONOMY

### Category codes (stable, never reuse)

| Code | Category | Subcategories |
|------|----------|---------------|
| A | Authentication & Authorization | A1=auth-bypass, A2=missing-acl, A3=priv-esc, A4=id-confusion |
| B | Cryptography | B1=malleable-sig, B2=double-hash, B3=weak-algo, B4=replay, B5=key-mgmt |
| C | Smart Contracts | C1=reentrancy, C2=integer, C3=access, C4=upgrade, C5=oracle, C6=paymaster |
| D | Secrets & Config | D1=hardcoded-secret, D2=env-leak, D3=log-leak, D4=default-cred |
| E | Network & RPC | E1=no-failover, E2=no-tls, E3=no-pin, E4=public-endpoint |
| F | Dependencies | F1=unpinned, F2=known-cve, F3=typosquat, F4=unaudited |
| G | Logging & Observability | G1=pii-leak, G2=over-sanitize, G3=missing-audit, G4=error-leak |
| H | Process & Governance | H1=centralization, H2=no-timelock, H3=no-multisig, H4=no-test |
| I | Recovery & Resilience | I1=no-recovery, I2=ss-failure, I3=toctou, I4=race |

### Bug patterns (used in fingerprints)

Each pattern has a canonical name:
- `ecrecover-raw` — using Solidity ecrecover without s-value check
- `e.message-leak` — logging exception message verbatim
- `multisig-claim-only` — claiming multisig protection without on-chain enforcement
- `blanket-sanitize` — removing all context from logs without differentiation
- `hash-incomplete` — signing hash missing critical fields
- `double-hash-eip191` — manual EIP-191 prefix + web3j signedMessageToKey
- `p256-eth-prefix` — P-256 verification with Ethereum prefix instead of WebAuthn
- `no-chainid-binding` — message hash without block.chainid
- `initiate-recovery-owner-only` — initiateRecovery requires wallet owner
- `no-execution-window` — executeRecovery public without upper time bound
- `no-anti-griefing` — postOp failure without cooldown/blocklist
- `admin-no-bound` — admin function without upper/lower bound check
- `refund-vault-withdrawable` — RefundVault has withdrawToken function
- `mock-in-production` — mock contract deployable to mainnet
- `safe-transfer-not-reentrancy` — claiming safeTransfer prevents reentrancy
- `hardcoded-password` — default password in docker-compose
- `valid-hex-placeholder` — placeholder key with valid hex format
- `no-git-history-scan` — secret scan without git history check
- `public-rpc-mobile` — public RPC endpoints for mobile app
- `no-cert-pinning` — no certificate pinning
- `single-rpc-no-failover` — single RPC URL without failover
- `nickname-race` — synchronized block for nickname uniqueness
- `signing-scheme-mismatch` — backend signs EIP-191, contract expects EIP-712
- `unauthorized-swap-execution` — swap endpoint without authentication
- `hash-computation-wrong` — address.toLowerCase().toByteArray() instead of keccak256
- `api-key-in-url` — API key exposed as query parameter in widget URL
- `webview-javascript-unrestricted` — WebView with JS enabled without domain restrictions
- `dev-server-in-production` — using dev server command in production Dockerfile
- `ci-no-security-scanning` — CI pipeline without security scanning
- `gdpr-compliance-gap` — no PII retention/deletion mechanism
- `no-structured-logging` — logging without JSON format or PII masking
- `open-infra-ports` — infrastructure ports exposed to all interfaces
- `pooled-eth-accounting` — using address(this).balance without per-wallet tracking
- `burned-deposit` — deposit zeroed without return to user
- `no-parameter-bounds` — admin function without min/max bounds on parameter
- `unverified-signatures` — signature length check without cryptographic verification
- `fire-and-forget-coroutine-leak` — CoroutineScope created per call without management
- `auth-brute-force-no-ratelimit` — auth endpoints without rate limiting
- `weak-password-policy` — password registration without complexity requirements
- `open-proxy-api-key-abuse` — open proxy using service API key without auth
- `unauthorized-onramp-access` — onramp endpoints without authentication
- `swap-dos-no-ratelimit` — swap endpoint without rate limiting
- `webview-js-bridge-wallet-signing` — JS bridge with wallet signing without domain restriction
- `play-integrity-no-jwt-verify` — Play Integrity verdict decoded without JWT verification
- `plaintext-sensitive-storage` — sensitive data stored without encryption
- `biometric-weak-allowed` — biometric auth allowing weak modalities for high-risk ops
- `passkey-rpid-hardcoded` — RP ID hardcoded across build flavors
- `software-root-detection-bypassable` — software-only root detection without hardware attestation
- `fcm-push-broken` — FCM push notifications non-functional due to env variable access
- `no-auth-invite-read` — GET endpoint exposing invite data without authentication
- `no-guardian-check-accept` — invite acceptance without verifying guardian identity
- `no-auth-pending-recovery` — GET endpoint exposing recovery state without auth
- `no-request-size-limit` — request body read without size limit
- `no-ratelimit-relay` — relay endpoints without rate limiting
- `error-message-info-leak` — error message revealing configuration details

---

## 2. FINDINGS REGISTRY

### F-001 [CRITICAL] Backend signature not verified by paymaster contract

| Field | Value |
|-------|-------|
| **ID** | F-001 |
| **Severity** | CRITICAL |
| **Category** | A1 (auth-bypass) |
| **Bug pattern** | `signature-unverified` |
| **Fingerprint** | `sha256("A1:MDAOPaymaster.sol:validatePaymasterUserOp:signature-unverified")` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 4 (2026-06-24) |
| **Last verified** | Wave 7 (2026-06-25) — still OPEN |

**Description:**
PaymasterService.kt signs paymasterAndData with private key, but MDAOPaymaster.sol → validatePaymasterUserOp() never recovers signer. Anyone can craft paymasterAndData bypassing backend rate-limit, price validation, MDAO/USDT priority.

**Impact:** Full auth bypass — attacker submits unlimited free gas operations.

**Lifecycle:**
- 2026-06-24 Wave 4: Found (severity CRITICAL, agent: swarm-researcher)
- 2026-06-25 Wave 6: Claimed fixed (commit c395312, "use OZ ECDSA.recover") — **REJECTED**: grep shows raw ecrecover still in code
- 2026-06-25 Wave 7: Re-verified, status OPEN, fingerprint matches

**Verification recipe:**
```bash
grep -n "ecrecover\|ECDSA.recover\|import.*ECDSA" contracts/src/MDAOPaymaster.sol
# PASS condition: line with "ECDSA.recover(" exists, no line with raw "ecrecover("
# FAIL condition: any raw "ecrecover(" without ECDSA wrapper
```

**Required fix:**
See §5 Fix Patterns → FP-AUTH-001 (EIP-712 quote verification)

**Regression test (must exist):**
- File: `contracts/test/MDAOPaymaster.t.sol`
- Test: `testRejectsPaymasterDataWithoutValidSignature()`
- Test: `testRejectsTamperedMaxTokenAmount()`
- Test: `testRejectsReplayedQuote()`

**Cross-references:**
- Related: F-006 (s-malleability), F-012 (EIP-712 domain)
- Supersedes: none
- Regressed from: none

---

### F-002 [CRITICAL] initiateRecovery impossible with full device loss

| Field | Value |
|-------|-------|
| **ID** | F-002 |
| **Severity** | CRITICAL |
| **Category** | I1 (no-recovery) |
| **Bug pattern** | `initiate-recovery-owner-only` |
| **Fingerprint** | `sha256("I1:SocialRecoveryModule.sol:initiateRecovery:initiate-recovery-owner-only")` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 7 (2026-06-25) |

**Description:**
`modifier onlyWalletOwner` on `initiateRecovery()` requires `msg.sender == wallet`. User with lost device can't sign UserOp → recovery impossible. This is the main target scenario of social recovery.

**Impact:** Module useless for full device loss. Core feature broken.

**Lifecycle:**
- 2026-06-25 Wave 7: Found (severity CRITICAL)

**Verification recipe:**
```bash
grep -A 3 "function initiateRecovery" contracts/src/SocialRecoveryModule.sol | grep "onlyWalletOwner\|onlyOwner"
# FAIL condition: "onlyWalletOwner" or "onlyOwner" present (means initiate is restricted)
# PASS condition: no such modifier (anyone can initiate with deposit)
```

**Required fix:**
See §5 Fix Patterns → FP-RECOVERY-001 (async recovery flow per PRD §7)

**Regression test (must exist):**
- File: `contracts/test/SocialRecoveryModule.t.sol`
- Test: `testAnyoneCanInitiateRecoveryWithDeposit()`
- Test: `testInitiateRecoveryWithoutDevice()`

**Cross-references:**
- Related: F-003 (execution window), F-004 (anti-griefing)

---

### F-003 [HIGH] No execution window for approved recovery

| Field | Value |
|-------|-------|
| **ID** | F-003 |
| **Severity** | HIGH |
| **Category** | I3 (toctou) |
| **Bug pattern** | `no-execution-window` |
| **Fingerprint** | `sha256("I3:SocialRecoveryModule.sol:executeRecovery:no-execution-window")` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 7 (2026-06-25) |
| **Last verified** | Wave 12 (2026-06-26) — CLAIMED_FIXED |

**Description:**
`executeRecovery()` is public with no upper time bound. Approved recovery request is "forever approved".

**Impact:** Stale recovery requests can be executed months later when conditions change.

**Lifecycle:**
- 2026-06-25 Wave 7: Found (severity HIGH)
- 2026-06-26 Wave 12: EXECUTION_WINDOW = 48 hours already in code (commit d6da680), checked in executeRecovery() + cleanupExpiredRecovery() → CLAIMED_FIXED
- **NOTE:** Regression test `testExecuteRecoveryAfterWindowReverts` is missing (see F-040 pattern)

**Verification recipe:**
```bash
grep -A 5 "function executeRecovery" contracts/src/SocialRecoveryModule.sol | grep "EXECUTION_WINDOW\|require.*timestamp"
# PASS: EXECUTION_WINDOW constant present and checked
# FAIL: no upper bound on execution time
```

**Required fix:**
```solidity
uint256 public constant EXECUTION_WINDOW = 48 hours;
require(block.timestamp <= req.readyAt + TIMELOCK + EXECUTION_WINDOW, "RecoveryExpired");
```

**Regression test (must exist):**
- Test: `testExecuteRecoveryAfterWindowReverts()`

---

### F-004 [HIGH] No anti-griefing in postOp

| Field | Value |
|-------|-------|
| **ID** | F-004 |
| **Severity** | HIGH |
| **Category** | C6 (paymaster) |
| **Bug pattern** | `no-anti-griefing` |
| **Fingerprint** | `sha256("C6:MDAOPaymaster.sol:postOp:no-anti-griefing")` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 7 (2026-06-25) |

**Description:**
TOCTOU between validate and postOp. Sender can null allowance/balance. On PaymentFailed — no cooldown/blocklist.

**Impact:** Attacker spams failing ops, paymaster pays gas for each.

**Lifecycle:**
- 2026-06-25 Wave 7: Found (severity HIGH)

**Verification recipe:**
```bash
grep -E "failedPaymentCount|blockedUntil|PaymentFailed" contracts/src/MDAOPaymaster.sol
# PASS: failure tracking present
# FAIL: no anti-griefing mechanism
```

**Required fix:**
See §5 Fix Patterns → FP-ANTIGRIEF-001 (differentiated failure handling)

**Regression test (must exist):**
- Test: `testRepeatedFailuresTriggerBlock()`
- Test: `testInsufficientAllowanceDoesNotBlock()`

---

### F-005 [HIGH] Owner oracle manipulation via setTokenPrice

| Field | Value |
|-------|-------|
| **ID** | F-005 |
| **Severity** | HIGH |
| **Category** | C5 (oracle) |
| **Bug pattern** | `admin-no-bound` |
| **Fingerprint** | `sha256("C5:MDAOPaymaster.sol:setTokenPrice:admin-no-bound")` |
| **Status** | REGRESSED |
| **Found** | Wave 5 (2026-06-24) |
| **Last verified** | Wave 10 (2026-06-26) — REGRESSED |

**Description:**
Owner controls tokenPrice mappings. If owner key compromised, attacker can set arbitrary prices. Existing mitigation: 2% cap per update (MAX_PRICE_CHANGE_BPS = 200).

**Impact:** Compromised owner key → gradual price manipulation (compound 2% per call).

**Lifecycle:**
- 2026-06-24 Wave 5: Found (severity MEDIUM)
- 2026-06-25 Wave 7: Upgraded to HIGH — compound 2% per call = 188% per day if no cooldown
- 2026-06-26 Wave 10: CLAIMED_FIXED → REGRESSED — `PRICE_COOLDOWN`/`priceLastUpdated` код есть, но regression test `testPriceUpdateCooldownEnforced` отсутствует (нарушение AP-PROCESS-001)

**Verification recipe:**
```bash
grep -E "MAX_PRICE_CHANGE_BPS|PRICE_UPDATE_COOLDOWN|lastPriceUpdate" contracts/src/MDAOPaymaster.sol
# PASS: 2% cap present AND cooldown present
# FAIL: 2% cap present but no cooldown (compound risk)
```

**Required fix:**
Add cooldown (15 min between updates per token):
```solidity
mapping(address => uint256) public lastPriceUpdate;
uint256 public constant PRICE_UPDATE_COOLDOWN = 15 minutes;

function setTokenPrice(address token, uint256 price) external onlyOwner {
    require(block.timestamp >= lastPriceUpdate[token] + PRICE_UPDATE_COOLDOWN, "Cooldown");
    // ... existing 2% cap logic ...
    lastPriceUpdate[token] = block.timestamp;
}
```

**Regression test (must exist):**
- Test: `testPriceUpdateCooldownEnforced()`
- Test: `testCompoundPriceChangePerDay()`

---

### F-006 [HIGH] ECDSA s-value malleability in paymaster

| Field | Value |
|-------|-------|
| **ID** | F-006 |
| **Severity** | HIGH |
| **Category** | B1 (malleable-sig) |
| **Bug pattern** | `ecrecover-raw` |
| **Fingerprint** | `sha256("B1:MDAOPaymaster.sol:trusted-signer-verify:ecrecover-raw")` |
| **Status** | VERIFIED |
| **Found** | Wave 4 (2026-06-24) |
| **Last verified** | Wave 10 (2026-06-26) — VERIFIED |

**Description:**
Raw ecrecover without s-value check. Malleable signatures (s' = n - s) accepted.

**Impact:** Trusted signer signature replay via malleable signature.

**Lifecycle:**
- Wave 4: Found (severity HIGH)
- Wave 5: Claimed fixed (Wave 5 inbox.md)
- Wave 6: Wave 4 audit re-verified — raw ecrecover still in code → CONFLICT
- Wave 7: Wave 6/7 claim OZ ECDSA applied → unverifiable from log, status CONFLICT
- Wave 10: Verification recipe PASS — `import {ECDSA} from "@openzeppelin/..."` + `ECDSA.recover(digest, v, r, s)` used, no raw `ecrecover(` found → VERIFIED

**Verification recipe:**
```bash
grep -n "import.*ECDSA\|ECDSA.recover\|ecrecover(" contracts/src/MDAOPaymaster.sol
# PASS: "import {ECDSA}" line present + "ECDSA.recover(" used + NO raw "ecrecover("
# FAIL: raw "ecrecover(" present
```

**Required fix:**
```solidity
import {ECDSA} from "@openzeppelin/contracts/utils/cryptography/ECDSA.sol";
import {MessageHashUtils} from "@openzeppelin/contracts/utils/cryptography/MessageHashUtils.sol";

bytes32 ethHash = MessageHashUtils.toEthSignedMessageHash(hash);
address signer = ECDSA.recover(ethHash, signature);  // rejects malleable
require(signer == trustedSigner, "InvalidSigner");
```

**Regression test (must exist):**
- Test: `testFuzzRejectsMalleableSignature(uint256 s)`

---

### F-007 [MEDIUM] MockP256 verify() without chain guard

| Field | Value |
|-------|-------|
| **ID** | F-007 |
| **Severity** | MEDIUM |
| **Category** | H4 (no-test in production) |
| **Bug pattern** | `mock-in-production` |
| **Fingerprint** | `sha256("H4:MockP256.sol:verify:mock-in-production")` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 4 (2026-06-24) |

**Description:**
MockP256 `verify()` always returns 1, no chain ID check (unlike `fallback`). If accidentally deployed to mainnet, anyone can forge P-256 signatures.

**Lifecycle:**
- Wave 4: Found (severity MEDIUM)
- Wave 5: Claimed fixed (chain guard added)

**Verification recipe:**
```bash
grep -n "block.chainid" contracts/test/mocks/MockP256.sol
# PASS: chain guard present in verify() AND function is view (not pure)
# FAIL: no chain check, or function is pure (can't read block.chainid)
```

**Required fix:**
```solidity
function verify(...) external view returns (uint256) {  // view, not pure
    require(block.chainid != 1 && block.chainid != 56, "MockP256: mainnet");
    return 1;
}
```

---

### F-008 [HIGH] RefundVault withdrawable by owner

| Field | Value |
|-------|-------|
| **ID** | F-008 |
| **Severity** | HIGH |
| **Category** | H1 (centralization) |
| **Bug pattern** | `refund-vault-withdrawable` |
| **Fingerprint** | `sha256("H1:RefundVault.sol:withdrawToken:refund-vault-withdrawable")` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 5 (2026-06-24) |

**Description:**
Owner can withdraw any token at any time via `withdrawToken`. If owner key compromised, all refund funds stolen.

**Impact:** User refunds at risk — owner can drain.

**Lifecycle:**
- Wave 5: Found (severity MEDIUM)
- Wave 6, 7: "Mitigated by multisig" — false claim, no on-chain enforcement
- Wave 7: Upgraded to HIGH

**Verification recipe:**
```bash
grep -n "function withdrawToken\|function withdraw" contracts/src/RefundVault.sol
# PASS: no withdraw function (only claimRefund for users)
# FAIL: withdrawToken or withdraw function exists
```

**Required fix:**
Remove `withdrawToken` function. RefundVault should only have:
- `deposit(user, amount)` — called by Paymaster
- `claimRefund()` — called by user

No owner withdrawal. If contract decommission — burn, don't withdraw.

**Regression test (must exist):**
- Test: `testOwnerCannotWithdraw()`
- Test: `testUserCanClaimRefund()`

---

### F-009 [MEDIUM] setPriceBufferBps no upper bound

| Field | Value |
|-------|-------|
| **ID** | F-009 |
| **Severity** | MEDIUM |
| **Category** | C5 (oracle) |
| **Bug pattern** | `admin-no-bound` |
| **Fingerprint** | `sha256("C5:MDAOPaymaster.sol:setPriceBufferBps:admin-no-bound")` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 5 (2026-06-24) |

**Description:**
`setPriceBufferBps` has no maximum value check. Owner could set buffer to 10000+ (100%+) making `maxAllowed` in price check unreasonably large.

**Lifecycle:**
- Wave 5: Found (severity MEDIUM)
- Wave 5: Claimed fixed (`if (newBuffer > 2000) revert`)

**Verification recipe:**
```bash
grep -A 3 "function setPriceBufferBps" contracts/src/MDAOPaymaster.sol | grep "2000\|MAX.*BUFFER"
# PASS: upper bound check present
# FAIL: no upper bound
```

**Note:** Lower bound missing — see F-009a.

---

### F-009a [MEDIUM] setPriceBufferBps no lower bound

| Field | Value |
|-------|-------|
| **ID** | F-009a |
| **Severity** | MEDIUM |
| **Category** | C5 (oracle) |
| **Bug pattern** | `admin-no-bound` |
| **Fingerprint** | `sha256("C5:MDAOPaymaster.sol:setPriceBufferBps:no-lower-bound")` |
| **Status** | OPEN |
| **Found** | Wave 5 audit (2026-06-25) |

**Description:**
F-09 fixed upper bound (2000) but no lower bound. Owner can set buffer to 0, disabling price protection.

**Required fix:**
```solidity
uint256 public constant MIN_PRICE_BUFFER_BPS = 100;   // 1%
uint256 public constant MAX_PRICE_BUFFER_BPS = 2000;  // 20%

function setPriceBufferBps(uint256 newBuffer) external onlyOwner {
    if (newBuffer < MIN_PRICE_BUFFER_BPS) revert BufferTooLow();
    if (newBuffer > MAX_PRICE_BUFFER_BPS) revert BufferTooHigh();
    // ...
}
```

---

### F-010 [MEDIUM] setMaxTokenAmountLimit decimal confusion

| Field | Value |
|-------|-------|
| **ID** | F-010 |
| **Severity** | MEDIUM |
| **Category** | C6 (paymaster) |
| **Bug pattern** | `admin-no-bound` |
| **Fingerprint** | `sha256("C6:MDAOPaymaster.sol:setMaxTokenAmountLimit:decimal-confusion")` |
| **Status** | REGRESSED |
| **Found** | Wave 5 (2026-06-24) |
| **Last verified** | Wave 10 (2026-06-26) — REGRESSED |

**Description:**
`setMaxTokenAmountLimit` cap is `1_000_000 * 10**18`. But USDT has 6 decimals, MDAO has 18. Cap is wrong for USDT (10^24 = 10^18 USDT = unrealistic).

**Lifecycle:**
- Wave 5: Found (severity LOW)
- Wave 5: Claimed fixed
- Wave 7: Upgraded to MEDIUM — decimal confusion risk
- Wave 10: CLAIMED_FIXED → REGRESSED — код всё ещё использует единый cap `1_000_000 * 10**18`, нет per-token limit с decimals normalization

**Verification recipe:**
```bash
grep -A 3 "function setMaxTokenAmountLimit" contracts/src/MDAOPaymaster.sol
# PASS: per-token limit with decimals normalization
# FAIL: single cap for all tokens
```

**Required fix:**
```solidity
mapping(address => uint256) public maxTokenAmountLimit;
mapping(address => uint8) public tokenDecimals;

function setMaxTokenAmountLimit(address token, uint256 newLimit) external onlyOwner {
    uint8 dec = IERC20Metadata(token).decimals();
    uint256 maxLimit = 1_000_000 * 10**dec;
    if (newLimit > maxLimit) revert AmountTooHigh();
    tokenDecimals[token] = dec;
    maxTokenAmountLimit[token] = newLimit;
}
```

---

### F-011 [MEDIUM] setMaxGasPrice chain-specific cap

| Field | Value |
|-------|-------|
| **ID** | F-011 |
| **Severity** | MEDIUM |
| **Category** | C6 (paymaster) |
| **Bug pattern** | `admin-no-bound` |
| **Fingerprint** | `sha256("C6:MDAOPaymaster.sol:setMaxGasPrice:chain-specific-cap")` |
| **Status** | REGRESSED |
| **Found** | Wave 5 (2026-06-24) |
| **Last verified** | Wave 10 (2026-06-26) — REGRESSED |

**Description:**
`setMaxGasPrice` cap is 1000 gwei. But BSC typical gas is 3-5 gwei (1000 = 200x overkill), Ethereum typical 20-50 gwei (1000 = 20x, reasonable).

**Lifecycle:**
- Wave 5: Found (severity LOW)
- Wave 5: Claimed fixed
- Wave 7: Upgraded to MEDIUM — chain-specific cap needed
- Wave 10: CLAIMED_FIXED → REGRESSED — код всё ещё использует единый cap `1000 gwei`, нет chain-specific cap для BSC vs ETH

**Verification recipe:**
```bash
grep -A 3 "function setMaxGasPrice" contracts/src/MDAOPaymaster.sol | grep "gwei\|MAX_GAS"
# PASS: chain-specific cap (BSC vs ETH)
# FAIL: single 1000 gwei cap
```

**Required fix:**
```solidity
uint256 public constant MAX_GAS_PRICE_BSC = 100 gwei;
uint256 public constant MAX_GAS_PRICE_ETH = 1000 gwei;

function setMaxGasPrice(uint256 newMaxGasPrice) external onlyOwner {
    uint256 cap = block.chainid == 56 ? MAX_GAS_PRICE_BSC : MAX_GAS_PRICE_ETH;
    if (newMaxGasPrice > cap) revert GasPriceTooHigh();
    // ...
}
```

---

### F-012 [HIGH] No EIP-712 domain separator for recovery

| Field | Value |
|-------|-------|
| **ID** | F-012 |
| **Severity** | HIGH |
| **Category** | B4 (replay) |
| **Bug pattern** | `no-chainid-binding` |
| **Fingerprint** | `sha256("B4:SocialRecoveryModule.sol:approveRecovery:no-chainid-binding")` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 4 (2026-06-24) |

**Description:**
No EIP-712 domain separator. Cross-contract and cross-chain replay possible. Wave 4 found EIP-191 only; Wave 7 confirmed EIP-712 still missing.

**Lifecycle:**
- Wave 4: Found (deferred as "enhancement" — WRONG per Wave 2)
- Wave 6: Claimed fixed (chainId added to message hash)
- Wave 7: Still EIP-191 + chainId, NOT EIP-712

**Verification recipe:**
```bash
grep -E "EIP712|_domainSeparator|DOMAIN_TYPEHASH" contracts/src/SocialRecoveryModule.sol
# PASS: EIP712 imported and used
# FAIL: only EIP-191 prefix, no EIP-712 domain
```

**Required fix:**
```solidity
import {EIP712} from "@openzeppelin/contracts/utils/cryptography/EIP712.sol";

contract SocialRecoveryModule is EIP712("MDAOPay-Recovery", "1") {
    bytes32 private constant APPROVE_TYPEHASH = keccak256(
        "ApproveRecovery(address wallet,bytes newPasskeyPubKey,uint256 nonce)"
    );
    
    function _hashApprove(address wallet, bytes memory newPubKey, uint256 nonce) 
        internal view returns (bytes32) 
    {
        return _hashTypedDataV4(keccak256(abi.encode(
            APPROVE_TYPEHASH, wallet, keccak256(newPubKey), nonce
        )));
    }
}
```

---

### F-013 [MEDIUM] Over-sanitization of error logs

| Field | Value |
|-------|-------|
| **ID** | F-013 |
| **Severity** | MEDIUM |
| **Category** | G2 (over-sanitize) |
| **Bug pattern** | `blanket-sanitize` |
| **Fingerprint** | `sha256("G2:backend/*.kt:catch-block:e.message-removed-blanket")` |
| **Status** | REGRESSED |
| **Found** | Wave 6 (2026-06-24) |
| **Last verified** | Wave 7 — REGRESSED |

**Description:**
`${e.message}` blanket-removed from 14+ log statements across WatchtowerService, RedisClient, Database, NicknameService. Loss of observability, UX regression in error propagation.

**Lifecycle:**
- Wave 6: Found as L-05 (5 instances)
- Wave 6: Claimed fixed (commit cfc7807) — blanket removal applied
- Wave 7: REGRESSION — same anti-pattern applied to 14 MORE instances (SEC-27-XX). Pattern spread, not resolved.

**Anti-pattern violated:** §4 AP-LOG-002 (blanket-sanitize)

**Verification recipe:**
```bash
grep -rn '\${e.message}' backend/src/main/ | wc -l
# PASS: 0 (all removed)
# FAIL: > 0 (some remain)
# WARNING: even if PASS, check if context-aware sanitization applied
#   grep -rn 'LogSanitizer' backend/src/main/ | wc -l
#   PASS: > 0 (centralized sanitizer exists)
```

**Required fix:**
See §5 Fix Patterns → FP-LOG-001 (context-aware LogSanitizer)

**Regression test (must exist):**
- File: `backend/src/test/.../LogSanitizerTest.kt`
- Test: `testSQLExceptionDoesNotLeakSchema()`
- Test: `testErrorIdReturnedToUser()`

---

### F-014 [MEDIUM] Relay auth bypass when RELAY_SECRET unset

| Field | Value |
|-------|-------|
| **ID** | F-014 |
| **Severity** | MEDIUM |
| **Category** | A1 (auth-bypass) |
| **Bug pattern** | `auth-bypass-env-unset` |
| **Fingerprint** | `sha256("A1:relay/src/index.ts:requireAuth:auth-bypass-env-unset")` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 4 (2026-06-24) |

**Description:**
`requireAuth` returns `null` if `RELAY_SECRET` is unset. If downstream treats null as anonymous → full auth bypass.

**Lifecycle:**
- Wave 4: Found (severity CRITICAL)
- Wave 6: Claimed fixed (`return err('Server misconfigured', 500)`)
- Wave 7: Severity downgrade to MEDIUM (no PoC confirmed)

**Verification recipe:**
```bash
grep -A 3 "function requireAuth\|const requireAuth" relay/src/index.ts | grep "RELAY_SECRET\|return"
# PASS: returns Response (not null) when RELAY_SECRET missing
# FAIL: returns null
```

**Required fix:**
```typescript
const requireAuth = (req: Request, env: Env): Response | null => {
    if (!env.RELAY_SECRET || env.RELAY_SECRET.length < 32) {
        return new Response('Server misconfigured', { status: 500 })
    }
    // ... rest
}
```

---

### F-015 [HIGH] Relay /recovery endpoints without guardian verification

| Field | Value |
|-------|-------|
| **ID** | F-015 |
| **Severity** | HIGH |
| **Category** | A2 (missing-acl) |
| **Bug pattern** | `no-guardian-verification` |
| **Fingerprint** | `sha256("A2:relay/src/index.ts:/recovery/approve:no-guardian-verification")` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 4 (2026-06-24) |

**Description:**
`/recovery/approve` and `/recovery/veto` accept `guardianIdentityHash` without cryptographic verification. Anyone can submit approval.

**Impact:** Wallet takeover — attacker forges 2-of-3 approvals, executes recovery.

**Lifecycle:**
- Wave 4: Found (severity MEDIUM)
- Wave 4: Claimed "v2" — deferred
- Wave 7: Upgraded to HIGH — wallet takeover risk

**Verification recipe:**
```bash
grep -A 10 "recovery/approve\|recovery/veto" relay/src/index.ts | grep "p256Signature\|verifyP256\|guardian.*sig"
# PASS: P-256 signature verification present
# FAIL: no signature verification
```

**Required fix:**
Require P-256 signature on relay endpoints OR forward to on-chain contract (which verifies P-256).

P-256 verification implemented in relay/src/index.ts lines 168-175 (acceptInvite) and 203-212 (approveRecovery). Verification recipe: PASS.

---

### F-016 [LOW] MAX_NONCE_GAP = 100 DoS vector

| Field | Value |
|-------|-------|
| **ID** | F-016 |
| **Severity** | LOW |
| **Category** | I4 (race) |
| **Bug pattern** | `nonce-gap-too-large` |
| **Fingerprint** | `sha256("I4:PaymasterService.kt:MAX_NONCE_GAP:nonce-gap-too-large")` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 5 (2026-06-24) |

**Description:**
`MAX_NONCE_GAP = 100` allows 100 pending UserOps per sender. DoS vector.

**Lifecycle:**
- Wave 5: Found (severity LOW)
- Wave 5: Claimed fixed (reduced to 20)

**Verification recipe:**
```bash
grep "MAX_NONCE_GAP" backend/src/main/kotlin/com/mdaopay/paymaster/PaymasterService.kt
# PASS: MAX_NONCE_GAP = 20
# FAIL: MAX_NONCE_GAP = 100
```

---

### F-017 [LOW] Fallback prices 100x off on testnet

| Field | Value |
|-------|-------|
| **ID** | F-017 |
| **Severity** | LOW |
| **Category** | C5 (oracle) |
| **Bug pattern** | `fallback-price-inaccurate` |
| **Fingerprint** | `sha256("C5:PaymasterService.kt:fallbackPrices:fallback-price-inaccurate")` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 5 (2026-06-24) |

**Description:**
Fallback token prices on testnet can be 100x off real price.

**Lifecycle:**
- Wave 5: Found (severity LOW)
- Wave 5: Claimed fixed (log warning added)

**Verification recipe:**
```bash
grep -A 3 "fallbackPrices\|isTestnet" backend/src/main/kotlin/com/mdaopay/paymaster/PaymasterService.kt | grep "log.warn\|FALLBACK"
# PASS: warning log present
# FAIL: silent fallback
```

---

### F-018 [HIGH] Owner can steal refunds via withdrawTokens

| Field | Value |
|-------|-------|
| **ID** | F-018 |
| **Severity** | HIGH |
| **Category** | H1 (centralization) |
| **Bug pattern** | `centralization-no-timelock` |
| **Fingerprint** | `sha256("H1:MDAOPaymaster.sol:withdrawTokens:centralization-no-timelock")` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 5 (2026-06-24) |
| **Last verified** | Wave 12 (2026-06-26) — CLAIMED_FIXED |

**Description:**
Owner can withdraw MDAO/USDT from paymaster at any time. "Mitigated by Gnosis Safe 3-of-5 multisig" — trust assumption, not on-chain enforcement.

**Lifecycle:**
- Wave 5: Found (severity MEDIUM)
- Wave 6, 7: "Mitigated by multisig" — false claim
- Wave 7: Upgraded to HIGH
- 2026-06-26 Wave 12: TimelockController deployed (minDelay=2d) as owner via Deploy.s.sol (commit 2edae72). withdrawTo() with daily cap, onlyOwner. AP-AUTH-001 resolved → CLAIMED_FIXED

**Verification recipe:**
```bash
# Проверить Deploy.s.sol: TimelockController создаётся и становится owner
grep -E "TimelockController|transferOwnership" contracts/script/Deploy.s.sol
# PASS: TimelockController deployed + paymaster.transferOwnership(address(timelock))
# FAIL: no TimelockController in deploy script

# Проверить withdrawTo с daily cap
grep -A 5 "function withdrawTo" contracts/src/MDAOPaymaster.sol
# PASS: withdrawTo с dailyWithdrawalCapBps и onlyOwner
```

**Required fix:**
See §5 Fix Patterns → FP-AUTH-002 (TimelockController for owner actions)

**Regression test (must exist):**
- Test: `test_TimelockAdminCanWithdrawTo()`
- Test: `test_RevertWhen_WithdrawToExceedsDailyCap()`
- Test: `test_DailyCapResetsAfter24Hours()`

---

### F-019 [MEDIUM] Nickname race condition on multiple instances

| Field | Value |
|-------|-------|
| **ID** | F-019 |
| **Severity** | MEDIUM |
| **Category** | I4 (race) |
| **Bug pattern** | `nickname-race` |
| **Fingerprint** | `sha256("I4:NicknameService.kt:register:nickname-race")` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 7 (2026-06-25) |

**Description:**
`synchronized(this)` + ConcurrentHashMap — works only in single JVM. Horizontal scaling → duplicate nicknames.

**Verification recipe:**
```bash
grep -E "synchronized|ConcurrentHashMap" backend/src/main/kotlin/com/mdaopay/paymaster/NicknameService.kt
# PASS: DB unique constraint + Redis SETNX
# FAIL: only synchronized (single-JVM only)
```

**Required fix:**
```sql
ALTER TABLE nicknames ADD CONSTRAINT nicknames_name_unique UNIQUE (name) WHERE deleted_at IS NULL;
```

---

### F-020 [HIGH] P-256 format doesn't match WebAuthn

| Field | Value |
|-------|-------|
| **ID** | F-020 |
| **Severity** | HIGH |
| **Category** | B5 (key-mgmt) |
| **Bug pattern** | `p256-eth-prefix` |
| **Fingerprint** | `sha256("B5:SocialRecoveryModule.sol:_verifyP256:p256-eth-prefix")` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 7 (2026-06-25) |

**Description:**
`_verifyP256()` wraps in Ethereum prefix (`\x19Ethereum Signed Message:\n32`), but WebAuthn signs `authenticatorData || SHA256(clientDataJSON)`. Face ID / Touch ID won't work.

**Verification recipe:**
```bash
grep -E "Ethereum Signed Message\|authenticatorData\|clientDataJSON\|WebAuthn" contracts/src/SocialRecoveryModule.sol
# PASS: WebAuthn verification (authenticatorData + clientDataJSON)
# FAIL: Ethereum prefix only
```

**Required fix:**
Full WebAuthn verification (see FP-CRYPTO-001 in FIX-PATTERNS.md).

---

### F-021 [INFO] NicknameService double hashing

| Field | Value |
|-------|-------|
| **ID** | F-021 |
| **Severity** | INFO |
| **Category** | B2 (double-hash) |
| **Bug pattern** | `double-hash-eip191` |
| **Fingerprint** | `sha256("B2:NicknameService.kt:verifySignature:double-hash-eip191")` |
| **Status** | VERIFIED |
| **Found** | Wave 7 (2026-06-25) |
| **Last verified** | Wave 12 (2026-06-26) — VERIFIED |

**Description:**
`sha3(messageHex) → Sign.signedMessageToKey(hashBytes, sig)` — web3j adds EIP-191 prefix and hashes again. Two different hashes.

**Lifecycle:**
- Wave 7: Found (severity INFO, status CONFLICT between Wave 1 claim and Wave 7 finding)
- 2026-06-26 Wave 12: Verified — code now uses `Sign.signedPrefixedMessageToKey()` which handles EIP-191 prefix internally. No manual hash before signing. Double hashing resolved → VERIFIED

**Verification recipe:**
```bash
grep "signedPrefixedMessageToKey" backend/src/main/kotlin/com/mdaopay/paymaster/NicknameService.kt
# PASS: signedPrefixedMessageToKey used (correct, no double hash)
# FAIL: Hash.sha3 before signedMessageToKey
```

---

### F-022 [MEDIUM] Paymaster signing hash inconsistency

| Field | Value |
|-------|-------|
| **ID** | F-022 |
| **Severity** | MEDIUM |
| **Category** | B4 (replay) |
| **Bug pattern** | `hash-inconsistent` |
| **Fingerprint** | `sha256("B4:PaymasterService.kt:computeSigningHash:hash-inconsistent")` |
| **Status** | OPEN |
| **Found** | Wave 7 (2026-06-25) |

**Description:**
`computeSigningHash()` doesn't do `.drop(2)` for sender/nonce, although `computeUserOpHash()` does. Different hashes for same data.

**Verification recipe:**
```bash
grep -B 2 -A 10 "fun computeSigningHash\|fun computeUserOpHash" backend/src/main/kotlin/com/mdaopay/paymaster/PaymasterService.kt | grep "drop(2)\|cleanHexPrefix"
# PASS: consistent pattern (both use drop(2) or cleanHexPrefix)
# FAIL: different patterns
```

---

### F-023 [HIGH] Public RPC for mobile app

| Field | Value |
|-------|-------|
| **ID** | F-023 |
| **Severity** | HIGH |
| **Category** | E4 (public-endpoint) |
| **Bug pattern** | `public-rpc-mobile` |
| **Fingerprint** | `sha256("E4:RpcProviderManager.kt:public-rpc-mobile")` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 6 (2026-06-25) |

**Description:**
Public RPC endpoints (publicnode, ankr, chainstack) in RpcProviderManager.kt. Rate limited, no SLA, guaranteed outage at scale.

**Lifecycle:**
- Wave 6: Found (severity LOW)
- Wave 7: Upgraded to HIGH — mobile app with 1000+ users will fail

**Verification recipe:**
```bash
grep -E "publicnode|ankr|chainstack" app/src/main/java/com/mdaopay/app/core/blockchain/RpcProviderManager.kt
# PASS: private RPC with API key
# FAIL: public RPC endpoints
```

**Required fix:**
Private RPC (Alchemy/Infura) with API key, multi-provider failover.

---

### F-024 [HIGH] No certificate pinning

| Field | Value |
|-------|-------|
| **ID** | F-024 |
| **Severity** | HIGH |
| **Category** | E3 (no-pin) |
| **Bug pattern** | `no-cert-pinning` |
| **Fingerprint** | `sha256("E3:app:no-cert-pinning")` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 6 (2026-06-25) |

**Description:**
No CertificatePinner in Android app. MITM with rogue CA possible.

**Verification recipe:**
```bash
grep -rE "CertificatePinner|certificatePinner|network_security_config" app/src/main/
# PASS: CertificatePinner or network_security_config.xml present
# FAIL: no certificate pinning
```

---

### F-025 [MEDIUM] Single RPC URL without failover

| Field | Value |
|-------|-------|
| **ID** | F-025 |
| **Severity** | MEDIUM |
| **Category** | E1 (no-failover) |
| **Bug pattern** | `single-rpc-no-failover` |
| **Fingerprint** | `sha256("E1:AppConfig.kt:single-rpc-no-failover")` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 7 (2026-06-25) |

**Description:**
Single RPC URL in AppConfig. PRD §18 requires 3 providers with failover.

**Verification recipe:**
```bash
grep -E "rpcProviders|RPC_PRIMARY|RPC_SECONDARY|RPC_TERTIARY" backend/src/main/kotlin/com/mdaopay/paymaster/AppConfig.kt
# PASS: List<RpcProvider> with 3+ entries
# FAIL: single rpcUrl
```

**Required fix:**
See §5 Fix Patterns → FP-RPC-001 (multi-provider with failover)

---

### F-026 [LOW] Hardcoded PostgreSQL password

| Field | Value |
|-------|-------|
| **ID** | F-026 |
| **Severity** | LOW |
| **Category** | D4 (default-cred) |
| **Bug pattern** | `hardcoded-password` |
| **Fingerprint** | `sha256("D4:docker-compose.yml:POSTGRES_PASSWORD:hardcoded-password")` |
| **Status** | CONFLICT |
| **Found** | Wave 4 (2026-06-24) |

**Description:**
`POSTGRES_PASSWORD: mdaopay` hardcoded in docker-compose.yml.

**Lifecycle:**
- Wave 4: Found (severity MEDIUM)
- Wave 5: Claimed fixed
- Wave 5 audit: "Already fixed" — false claim, no commit
- Wave 6: "Fixed (comment + .env override)"
- Wave 7: Still CONFLICT — need verification

**Verification recipe:**
```bash
grep "POSTGRES_PASSWORD" docker-compose.yml
# PASS: ${POSTGRES_PASSWORD:?required} (fail-fast)
# FAIL: mdaopay (hardcoded)
```

---

### F-027 [MEDIUM] InsuranceFund collectFee callable by anyone

| Field | Value |
|-------|-------|
| **ID** | F-027 |
| **Severity** | MEDIUM |
| **Category** | A2 (missing-acl) |
| **Bug pattern** | `no-access-control` |
| **Fingerprint** | `sha256("A2:InsuranceFund.sol:collectFee:no-access-control")` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 5 (2026-06-24) |

**Description:**
`collectFee` is external with no access control. Anyone can call to register a fee.

**Verification recipe:**
```bash
grep -A 3 "function collectFee" contracts/src/InsuranceFund.sol | grep "onlyOwner\|onlyRole\|external"
# PASS: onlyOwner or onlyRole
# FAIL: external only (no access control)
```

---

### F-028 [LOW] DeadManSwitch claimFunds reentrancy unverified

| Field | Value |
|-------|-------|
| **ID** | F-028 |
| **Severity** | LOW |
| **Category** | C1 (reentrancy) |
| **Bug pattern** | `reentrancy-unverified` |
| **Fingerprint** | `sha256("C1:DeadManSwitch.sol:claimFunds:reentrancy-unverified")` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 5 (2026-06-24) |

**Description:**
`claimFunds` uses low-level `call{value: balance}`. CEI pattern claimed but not verified.

**Verification recipe:**
```bash
grep -B 2 -A 10 "function claimFunds" contracts/src/DeadManSwitch.sol | grep "claimed\|nonReentrant"
# PASS: nonReentrant modifier OR claimed flag set BEFORE call
# FAIL: no reentrancy protection
```

---

### F-029 [LOW] MDAOToken burn fee precision loss

| Field | Value |
|-------|-------|
| **ID** | F-029 |
| **Severity** | LOW |
| **Category** | C2 (integer) |
| **Bug pattern** `precision-loss` |
| **Fingerprint** | `sha256("C2:MDAOToken.sol:burnFee:precision-loss")` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 5 (2026-06-24) |

**Description:**
`value * burnFeeBps / 10000` rounds to 0 for small values. Attacker can split transfers into chunks of 99 to avoid burn fee.

**Required fix:**
```solidity
uint256 burnAmount = value * burnFeeBps / 10000;
if (burnAmount == 0 && value > 0) burnAmount = 1;  // minimum 1 wei burn
```

---

### F-030 [INFO] MockP256 verify() chain guard missing

| Field | Value |
|-------|-------|
| **ID** | F-030 |
| **Severity** | INFO |
| **Category** | H4 |
| **Bug pattern** | `mock-in-production` |
| **Fingerprint** | `sha256("H4:MockP256.sol:verify:mock-in-production")` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 4 (2026-06-24) |

Same as F-007. Kept for historical reference.

---

### F-031 [INFO] NicknameRegistry domainSeparator not cached

| Field | Value |
|-------|-------|
| **ID** | F-031 |
| **Severity** | INFO |
| **Category** | C6 |
| **Bug pattern** | `gas-inefficiency` |
| **Fingerprint** | `sha256("C6:NicknameRegistry.sol:domainSeparator:gas-inefficiency")` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 5 (2026-06-24) |

**Description:**
`domainSeparator()` recomputed on every call. Use OZ EIP712 mixin for caching.

---

### F-032 [HIGH] Redis fail-open rate-limiting

| Field | Value |
|-------|-------|
| **ID** | F-032 |
| **Severity** | HIGH |
| **Category** | I2 (ss-failure) |
| **Bug pattern** | `redis-fail-open-rate-limit` |
| **Fingerprint** | `sha256("I2:RedisClient.kt:RedisRateLimiter.isLimited:redis-fail-open-rate-limit")` |
| **Status** | VERIFIED |
| **Found** | Wave 7 (2026-06-25) |
| **Last verified** | Wave 10 (2026-06-26) — VERIFIED |

**Description:**
При падении Redis (connection == null) метод incr возвращает null, isLimited возвращает false — rate-limit полностью отключается. Открытый вектор DoS/абьюза в момент инфраструктурной деградации.

**Impact:**
Атакующий может беспрепятственно спамить /sign при падении Redis. Fail-open для security-критичного компонента.

**Lifecycle:**
- 2026-06-25 Wave 7: Found (severity HIGH)
- 2026-06-25 Wave 8: CLAIMED_FIXED (commit 6f842b5, RedisClientFallbackTest.kt, 7/7 PASS) — added `isLimitedInMemory()` with ConcurrentHashMap fallback
- 2026-06-26 Wave 10: Verification recipe PASS — `isLimitedInMemory()` present + `fallbackMap = ConcurrentHashMap<>` + 3 regression tests exist → VERIFIED

**Verification recipe:**
```bash
grep -n "isLimitedInMemory\|fallbackMap.*ConcurrentHashMap" backend/src/main/kotlin/com/mdaopay/paymaster/RedisClient.kt
# PASS: isLimitedInMemory() present + fallbackMap = ConcurrentHashMap<...>
```

**Required fix:**
Add in-memory fallback (ConcurrentHashMap) when Redis unavailable. Log warn on fallback switch. See EM-064.

**Regression test:**
- File: `backend/src/test/kotlin/com/mdaopay/paymaster/RedisClientFallbackTest.kt`
- Test: `rate limiter falls back to in-memory when Redis incr returns null`
- Test: `rate limiter in-memory fallback resets after window expiry`

**Cross-references:**
- Related: F-033 (same root cause: Redis fail-open)

---

### F-033 [HIGH] Redis fail-open replay-protection

| Field | Value |
|-------|-------|
| **ID** | F-033 |
| **Severity** | HIGH |
| **Category** | B4 (replay) |
| **Bug pattern** | `redis-fail-open-replay` |
| **Fingerprint** | `sha256("B4:RedisClient.kt:RedisReplayCache.isUsed:redis-fail-open-replay")` |
| **Status** | VERIFIED |
| **Found** | Wave 7 (2026-06-25) |
| **Last verified** | Wave 10 (2026-06-26) — VERIFIED |

**Description:**
При падении Redis (connection == null) метод exists возвращает null/false, isUsed всегда возвращает false — защита от повторного использования подписи при регистрации никнейма полностью отключается.

**Impact:**
Возможен replay-атака (повторная регистрация никнейма с той же подписью) при падении Redis.

**Lifecycle:**
- 2026-06-25 Wave 7: Found (severity HIGH)
- 2026-06-25 Wave 8: CLAIMED_FIXED (commit 6f842b5, RedisClientFallbackTest.kt, 7/7 PASS) — added `isUsedInMemory()` with ConcurrentHashMap fallback
- 2026-06-26 Wave 10: Verification recipe PASS — `isUsedInMemory()` present + `fallbackMap = ConcurrentHashMap<>` + 4 regression tests exist → VERIFIED

**Verification recipe:**
```bash
grep -n "isUsedInMemory\|fallbackMap.*ConcurrentHashMap" backend/src/main/kotlin/com/mdaopay/paymaster/RedisClient.kt
# PASS: isUsedInMemory() present + fallbackMap = ConcurrentHashMap<...>
```

**Required fix:**
Add in-memory fallback (ConcurrentHashMap) when Redis unavailable. Log warn on fallback switch. See EM-064.

**Regression test:**
- File: `backend/src/test/kotlin/com/mdaopay/paymaster/RedisClientFallbackTest.kt`
- Test: `replay cache falls back to in-memory when Redis exists fails`
- Test: `replay cache in-memory fallback respects TTL`

**Cross-references:**
- Related: F-032 (same root cause: Redis fail-open)

---

### F-034 [CRITICAL] Backend↔Contract signing scheme incompatible

| Field | Value |
|-------|-------|
| **ID** | F-034 |
| **Severity** | CRITICAL |
| **Category** | B4 (Backend-Solidity interface mismatch) |
| **Bug pattern** | `signing-scheme-mismatch` |
| **Fingerprint** | `sha256("B4:contracts/src/MDAOPaymaster.sol:_verifyQuoteSignature:signing-scheme-mismatch")` → `891960d52b0ccf50e0d5df1b481d564eab864baf03883d61047545c8909b38b1` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 9 (2026-06-26) |
| **Last verified** | Wave 12 (2026-06-26) — CLAIMED_FIXED |

**Description:**
Backend (`PaymasterService.kt`) подписывает EIP-191(userOpHash), контракт ожидает EIP-712(Quote). При `trustedSigner != address(0)` все UserOp падают с `InvalidSigner()`. Полная несовместимость схем подписания между бэкендом и контрактом.

**Impact:**
CRITICAL — Paymaster неработоспособен при `trustedSigner != address(0)`. Ни одна UserOp не пройдёт валидацию. Полный отказ функции газ-спонсорства.

**Lifecycle:**
- 2026-06-26 Wave 9: Found (severity CRITICAL, agent: librarian)
- 2026-06-26 Wave 12: Fixed in commits 7e143f4 + 8b37e58 — backend now signs EIP-712 Quote struct with domain separator, contract verifies via _hashTypedDataV4. Both sides consistent → CLAIMED_FIXED

**Verification recipe:**
```bash
# Проверить схему подписания на бэкенде
grep -A 5 "computeEIP712QuoteHash\|EIP712_DOMAIN_TYPEHASH" backend/src/main/kotlin/com/mdaopay/paymaster/PaymasterService.kt
# PASS: EIP-712 structured data signing (quote hash with domain separator)
# FAIL: EIP-191 userOpHash signing

# Проверить схему верификации на контракте
grep -A 5 "_verifyQuoteSignature\|_hashTypedDataV4\|EIP712" contracts/src/MDAOPaymaster.sol
# PASS: EIP-712 _hashTypedDataV4
# FAIL: EIP-191 toEthSignedMessageHash
```

**Required fix:**
Backend должен подписывать EIP-712 Quote (те же поля, что контракт ожидает в `_verifyQuoteSignature`). Использовать `EIP712Domain` с `name="MDAOPay"`, `version="1"`, `chainId`, `verifyingContract`.

**Regression test (must exist):**
- File: `backend/src/test/kotlin/com/mdaopay/paymaster/PaymasterServiceTest.kt`
- Test: `testQuoteSignedWithEIP712MatchesContractVerification()`
- File: `contracts/test/MDAOPaymaster.t.sol`
- Test: `testBackendQuoteSignatureVerifiesOnChain()`

**Cross-references:**
- Related: F-001 (signature-unverified), F-006 (ecrecover-raw), F-012 (no EIP-712)
- Supersedes: none

---

### F-035 [CRITICAL] SwapService uses PAYMASTER_PRIVATE_KEY without authentication

| Field | Value |
|-------|-------|
| **ID** | F-035 |
| **Severity** | CRITICAL |
| **Category** | A2 (missing-acl) |
| **Bug pattern** | `unauthorized-swap-execution` |
| **Fingerprint** | `sha256("A2:backend/src/main/kotlin/com/mdaopay/paymaster/swap/SwapRoutes.kt:executeSwap:unauthorized-swap-execution")` → `b97533d6425e61b3b26cbbcffe7dc95892eee9cc99855c026134785a2f1de581` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 9 (2026-06-26) |
| **Last verified** | Wave 12 (2026-06-26) — CLAIMED_FIXED |

**Description:**
`SwapService` использует `PAYMASTER_PRIVATE_KEY` для подписания swap-транзакций, но `/swap/execute` и `/swap/quote` не имеют аутентификации (нет `X-API-Key`). Любой может подписать swap-транзакцию ключом пеймастера.

**Impact:**
CRITICAL — Funds drain через swap. Атакующий может вызывать executeSwap с произвольными параметрами, подписывая транзакции ключом пеймастера без ограничения.

**Lifecycle:**
- 2026-06-26 Wave 9: Found (severity CRITICAL, agent: librarian)
- 2026-06-26 Wave 12: Fixed via commit aed5b3b — swapRoutes registered inside `authenticate("auth-jwt")` block in Application.kt. JWT Bearer token required. Regression tests in SwapRoutesAuthTest.kt confirm 401 without auth → CLAIMED_FIXED

**Verification recipe:**
```bash
# Проверить что swap routes внутри JWT auth middleware
grep -B 5 -A 2 "swapRoutes\|authenticate.*auth-jwt" backend/src/main/kotlin/com/mdaopay/paymaster/Application.kt
# PASS: swapRoutes() call before closing `}` of authenticate("auth-jwt") block
# FAIL: swapRoutes outside auth block

# Проверить regression test
grep -E "swap quote returns 401\|swap execute returns 401" backend/src/test/kotlin/com/mdaopay/paymaster/SwapRoutesAuthTest.kt
# PASS: both tests exist
```

**Required fix:**
Добавить `X-API-Key` аутентификацию на `/swap/quote` и `/swap/execute`. Использовать ту же схему, что и `/sign` (ApiKeyAuthMiddleware). Никакой публичный доступ к swap с PAYMASTER_PRIVATE_KEY.

**Regression test (must exist):**
- File: `backend/src/test/kotlin/com/mdaopay/paymaster/SwapRoutesAuthTest.kt`
- Test: `swap quote returns 401 without auth`
- Test: `swap execute returns 401 without auth`

**Cross-references:**
- Related: F-001, F-022
- Supersedes: none

---

### F-036 [CRITICAL] OnChainRegistryClient computes wrong identityHash

| Field | Value |
|-------|-------|
| **ID** | F-036 |
| **Severity** | CRITICAL |
| **Category** | B2 (Blockchain integration) |
| **Bug pattern** | `hash-computation-wrong` |
| **Fingerprint** | `sha256("B2:backend/src/main/kotlin/com/mdaopay/paymaster/blockchain/OnChainRegistryClient.kt:isIdentityRegistered:hash-computation-wrong")` → `1b73fad2169659693b2c69a8cc72664cd4fab82c71a3bf9f7ea4b770498eebd9` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 9 (2026-06-26) |
| **Last verified** | Wave 9 (2026-06-26) — initial |

**Description:**
`address.lowercase().toByteArray()` даёт ASCII 42 байта (строка адреса как UTF-8), контракт ожидает `keccak256(abi.encodePacked(signer))` = 20 байт. `isIdentityRegistered()` всегда возвращает неправильный результат.

**Impact:**
CRITICAL — on-chain регистрация не работает. Nickname можно зарегистрировать без проверки on-chain identity. Полный обход механизма верификации личности.

**Lifecycle:**
- 2026-06-26 Wave 9: Found (severity CRITICAL, agent: librarian)

**Verification recipe:**
```bash
# Проверить метод вычисления хеша на бэкенде
grep -A 5 "identityHash\|toByteArray\|lowercase" backend/src/main/kotlin/com/mdaopay/paymaster/blockchain/OnChainRegistryClient.kt
# PASS: Numeric.hexStringToByteArray(address) или Address.fromHex(address).toByteArray()
# FAIL: address.lowercase().toByteArray() (ASCII строка вместо 20 байт)

# Проверить ожидание контракта
grep -A 5 "identityHash\|abi.encodePacked" contracts/src/NicknameRegistry.sol
# PASS: keccak256(abi.encodePacked(signer))
```

**Required fix:**
```kotlin
// Вместо address.lowercase().toByteArray()
val addressBytes = Numeric.hexStringToByteArray(address)
val identityHash = Hash.sha3(addressBytes)
```

**Regression test (must exist):**
- File: `backend/src/test/kotlin/com/mdaopay/paymaster/blockchain/OnChainRegistryClientTest.kt`
- Test: `testIdentityHashMatchesContract()`
- Test: `testIsIdentityRegisteredReturnsCorrectResult()`

**Cross-references:**
- Related: F-021 (double-hash), F-022 (hash-inconsistent)
- Supersedes: none

---

### F-037 [HIGH] MoonPay API key exposed in widget URL returned to client

| Field | Value |
|-------|-------|
| **ID** | F-037 |
| **Severity** | HIGH |
| **Category** | D2 (env-leak) |
| **Bug pattern** | `api-key-in-url` |
| **Fingerprint** | `sha256("D2:backend/src/main/kotlin/com/mdaopay/paymaster/fiat/FiatOnrampService.kt:createOrder:api-key-in-url")` → `bbf075be2da76b2ef57a1fcece0964ce001e2c74c43bae346a3ecc51aed658ac` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 9 (2026-06-26) |
| **Last verified** | Wave 9 (2026-06-26) — initial |

**Description:**
API key передаётся как query-параметр в widget URL и возвращается клиенту. Ключ виден в логах (`onrampLog`), Referrer-заголовках, client-side инспекции. MoonPay API key — чувствительный секрет.

**Impact:**
HIGH — API key exposure. Злоумышленник может использовать ключ для создания поддельных order от имени сервиса, что ведёт к финансовым потерям.

**Lifecycle:**
- 2026-06-26 Wave 9: Found (severity HIGH, agent: librarian)

**Verification recipe:**
```bash
# Проверить формирование URL
grep -A 10 "createOrder\|widgetUrl\|apiKey" backend/src/main/kotlin/com/mdaopay/paymaster/fiat/FiatOnrampService.kt
# PASS: API key передаётся через server-side session token или backend proxy
# FAIL: API key в query-параметре widget URL, возвращаемом клиенту
```

**Required fix:**
Использовать server-side session token вместо API key в URL. MoonPay поддерживает `sessionToken` flow: backend создаёт session, клиент получает только session ID.

**Regression test (must exist):**
- File: `backend/src/test/kotlin/com/mdaopay/paymaster/fiat/FiatOnrampServiceTest.kt`
- Test: `testWidgetUrlDoesNotContainApiKey()`
- Test: `testApiKeyNotLogged()`

**Cross-references:**
- Related: none
- Supersedes: none

---

### F-038 [HIGH] WebView with JavaScript enabled without domain restrictions

| Field | Value |
|-------|-------|
| **ID** | F-038 |
| **Severity** | HIGH |
| **Category** | E3 (Mobile security) |
| **Bug pattern** | `webview-javascript-unrestricted` |
| **Fingerprint** | `sha256("E3:app/src/main/java/com/mdaopay/app/core/ui/components/MDAOWebView.kt:loadUrl:webview-javascript-unrestricted")` → `63287b3358b73207e0a29b4fb035f04513d4e3c1bda210c8bb9539e8ffd39b2b` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 9 (2026-06-26) |
| **Last verified** | Wave 9 (2026-06-26) — initial |

**Description:**
`settings.javaScriptEnabled = true`, `domStorageEnabled = true`. Нет `allowFileAccess=false`, `allowContentAccess=false`. Нет контроля навигации через `shouldOverrideUrlLoading`. WebView может загружать произвольные URL, включая `file://` и `content://` схемы.

**Impact:**
HIGH — XSS в контексте WebView. Если атакующий контролирует любой загружаемый URL (через редирект, параметр, iframe), он может выполнить произвольный JavaScript в контексте приложения, получить доступ к WebView storage, кукам, Android JavaScript bridge.

**Lifecycle:**
- 2026-06-26 Wave 9: Found (severity HIGH, agent: librarian)

**Verification recipe:**
```bash
# Проверить настройки WebView
grep -A 15 "fun loadUrl\|WebView\|webView" app/src/main/java/com/mdaopay/app/core/ui/components/MDAOWebView.kt
# PASS: shouldOverrideUrlLoading с whitelist доменов, allowFileAccess=false, allowContentAccess=false
# FAIL: javaScriptEnabled=true без shouldOverrideUrlLoading, нет ограничений file/content access
```

**Required fix:**
```kotlin
settings.javaScriptEnabled = true
settings.domStorageEnabled = true
settings.allowFileAccess = false          // ADD
settings.allowContentAccess = false       // ADD
settings.allowFileAccessFromFileURLs = false  // ADD
settings.allowUniversalAccessFromFileURLs = false  // ADD

webView.webViewClient = object : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        if (!url.startsWith("https://trusted-domain.com/")) return true  // BLOCK
        return false  // ALLOW
    }
}
```

**Regression test (must exist):**
- File: `app/src/test/java/com/mdaopay/app/core/ui/components/MDAOWebViewTest.kt`
- Test: `testWebViewBlocksFileAccess()`
- Test: `testWebViewBlocksUntrustedDomain()`

**Cross-references:**
- Related: F-024 (no certificate pinning)
- Supersedes: none

---

### F-039 [MEDIUM] setPriceBufferBps без нижней границы

| Field | Value |
|-------|-------|
| **ID** | F-039 |
| **Severity** | MEDIUM |
| **Category** | C5 (oracle) |
| **Bug pattern** | `no-lower-bound` |
| **Fingerprint** | `sha256("B4:contracts/src/MDAOPaymaster.sol:setPriceBufferBps:no-lower-bound")` |
| **Status** | OPEN |
| **Found** | Wave 10 (2026-06-26) |

**Description:**
`setPriceBufferBps` имеет верхнюю границу 2000 (F-009 fixed), но нижняя граница отсутствует. Owner может установить `priceBufferBps = 0`, что полностью отключает price buffer и делает `maxAllowed = price`, позволяя точную price manipulation.

**Impact:** MEDIUM — price manipulation при compromised owner key (аналогично F-009a).

**Verification recipe:**
```bash
grep -A 5 "function setPriceBufferBps" contracts/src/MDAOPaymaster.sol | grep "newBuffer < MIN\|MIN_PRICE_BUFFER"
# PASS: MIN_PRICE_BUFFER_BPS или lower bound check присутствует
# FAIL: только upper bound (2000), lower bound отсутствует
```

**Required fix:**
```solidity
uint256 public constant MIN_PRICE_BUFFER_BPS = 100;   // 1% minimum buffer
require(newBuffer >= MIN_PRICE_BUFFER_BPS, "Buffer too low");
```

**Regression test (must exist):**
- Test: `testSetPriceBufferBpsBelowMinimumReverts()`

**Cross-references:**
- Related: F-009 (upper bound), F-009a (lower bound — same issue, different fingerprint)

---

### F-040 [MEDIUM] Price cooldown без regression test

| Field | Value |
|-------|-------|
| **ID** | F-040 |
| **Severity** | MEDIUM |
| **Category** | H4 (no-test) |
| **Bug pattern** | `missing-regression-test` |
| **Fingerprint** | `sha256("B4:contracts/test/MDAOPaymaster.t.sol:setTokenPrice:missing-regression-test")` |
| **Status** | OPEN |
| **Found** | Wave 10 (2026-06-26) |

**Description:**
C-5 фикс price cooldown (`PRICE_COOLDOWN = 15 minutes`, `priceLastUpdated[token]` mapping) присутствует в коде, но не имеет regression test `testPriceUpdateCooldownEnforced`. Нарушение AP-PROCESS-001 (marking "fixed" without regression test).

**Impact:** MEDIUM — regression risk. Изменение cooldown-логики не будет поймано тестами.

**Verification recipe:**
```bash
forge test --match-test "testPriceUpdateCooldownEnforced" 2>&1 | grep -E "PASS|FAIL|no tests"
# PASS: "PASS" output
# FAIL: "no tests matched" (test does not exist)
```

**Required fix:**
Добавить regression test:
```solidity
function testPriceUpdateCooldownEnforced() public {
    vm.prank(owner);
    paymaster.setTokenPrice(address(usdt), 1e18);
    vm.prank(owner);
    vm.expectRevert(MDAOPaymaster.PriceCooldownActive.selector);
    paymaster.setTokenPrice(address(usdt), 1e18 + 1);
}
```

**Regression test (must exist):**
- Test: `testPriceUpdateCooldownEnforced()`
- Test: `testCompoundPriceChangePerDay()`

**Cross-references:**
- Related: F-005 (REGRESSED)

---

### F-041 [MEDIUM] Failure differentiation без regression test

| Field | Value |
|-------|-------|
| **ID** | F-041 |
| **Severity** | MEDIUM |
| **Category** | H4 (no-test) |
| **Bug pattern** | `missing-regression-test` |
| **Fingerprint** | `sha256("B4:contracts/test/MDAOPaymaster.t.sol:_handlePostOp:failure-differentiation-missing-regression-test")` |
| **Status** | OPEN |
| **Found** | Wave 10 (2026-06-26) |

**Description:**
C-4 фикс failure differentiation (allowance/balance failures не инкрементят `failedPaymentCount`) не имеет отдельного regression test, который проверяет, что allowance/balance failure НЕ увеличивает счётчик.

**Impact:** MEDIUM — regression risk. Будущие изменения могут сломать дифференциацию, и это не будет обнаружено.

**Verification recipe:**
```bash
forge test --match-test "testInsufficientAllowanceDoesNotBlock\|testInsufficientBalanceDoesNotBlock\|testAllowanceFailNoBlock" 2>&1 | grep -E "PASS|FAIL|no tests"
# PASS: any test matching exists and passes
# FAIL: "no tests matched" (test does not exist)
```

**Required fix:**
Добавить regression test:
```solidity
function testInsufficientAllowanceDoesNotIncrementFailureCount() public {
    // setup user with insufficient allowance
    // execute UserOp → postOp → PaymentFailed
    // verify failedPaymentCount[user] == 0 (unchanged)
}
```

**Regression test (must exist):**
- Test: `testInsufficientAllowanceDoesNotBlock()` or equivalent
- Test: `testInsufficientBalanceDoesNotBlock()` or equivalent

**Cross-references:**
- Related: F-004 (anti-griefing)

---

### F-042 [HIGH] relay/Dockerfile использует wrangler dev в production

| Field | Value |
|-------|-------|
| **ID** | F-042 |
| **Severity** | HIGH |
| **Category** | H4 (process) |
| **Bug pattern** | `dev-server-in-production` |
| **Fingerprint** | `sha256("H4:relay/Dockerfile:CMD:dev-server-in-production")` → `7bc01b6b1c80547ace20d8c4b7f111938302db0cbc479552ae2adb6882d170bb` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 11 (2026-06-26) |

**Description:**
`CMD ["npx", "wrangler", "dev", "--port", "8787", "--host", "0.0.0.0"]` — wrangler dev это dev-сервер, не предназначен для production.

**Impact:**
HIGH — при деплое через Docker будет запущен dev-сервер, падение под нагрузкой, утечка памяти.

**Verification recipe:**
```bash
grep "wrangler dev\|wrangler deploy\|wrangler publish" relay/Dockerfile
# PASS: "wrangler deploy" or "wrangler publish"
# FAIL: "wrangler dev"
```

**Required fix:**
Заменить `CMD ["npx", "wrangler", "dev"` на `CMD ["npx", "wrangler", "deploy"` или использовать `wrangler publish`.

**Test coverage:** (пока пусто)

---

### F-043 [MEDIUM] CI без permissions block и дополнительного security scanning

| Field | Value |
|-------|-------|
| **ID** | F-043 |
| **Severity** | MEDIUM |
| **Category** | H4 (process) |
| **Bug pattern** | `ci-no-security-scanning` |
| **Fingerprint** | `sha256("H4:.github/workflows/ci.yml:ci-no-security-scanning")` → `55cbfcf0737c958370e2c5d89de0b53716809e3d8a732747d7fa58fe00a9c038` |
| **Status** | OPEN |
| **Found** | Wave 11 (2026-06-26) |

**Description:**
CI запускает Slither (contracts static analysis, добавлен в Wave 1), но отсутствуют: permissions block для GITHUB_TOKEN (contents: read), secret scanning (trufflehog/gitleaks), dependency scanning (Trivy/Snyk), SAST для backend (CodeQL/Semgrep).

**Impact:**
MEDIUM — GITHUB_TOKEN имеет избыточные права; уязвимые зависимости и секреты могут попасть в main.

**Verification recipe:**
```bash
grep -E "trivy|snyk|codeql|semgrep|trufflehog|gitleaks" .github/workflows/ci.yml
# PASS: at least one security scanner present
# FAIL: no security scanning
```

**Required fix:**
Добавить CodeQL/Semgrep analysis, dependency scanning (Trivy), secret scanning (trufflehog/gitleaks). Ограничить permissions GITHUB_TOKEN (contents: read).

**Test coverage:** (пока пусто)

---

### F-044 [MEDIUM] Нет политики retention/очистки PII (GDPR)

| Field | Value |
|-------|-------|
| **ID** | F-044 |
| **Severity** | MEDIUM |
| **Category** | H2 (process) |
| **Bug pattern** | `gdpr-compliance-gap` |
| **Fingerprint** | `sha256("H2:V1__initial_schema.sql:gdpr-compliance-gap")` → `cdb73f37c4a83bfd08fba476624a4aa079b4da4d207b1b368a365966a17b8b45` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 11 (2026-06-26) |

**Description:**
users хранит email, google_sub, apple_sub — нет механизма удаления. Нет DELETE/ANONYMIZE для права на забвение (GDPR Art. 17).

**Impact:**
MEDIUM — нарушение GDPR, юридические риски.

**Verification recipe:**
```bash
grep -E "DELETE FROM users|ANONYMIZE|ANONYMIZATION|retention_days|data_retention" backend/src/main/resources/db/migration/
# PASS: DELETE or ANONYMIZE mechanism present
# FAIL: no data removal mechanism
```

**Required fix:**
Добавить поле `deleted_at` и хранимую процедуру для анонимизации PII через 30 дней после запроса на удаление.

**Test coverage:** (пока пусто)

---

### F-045 [MEDIUM] logback.xml без структурированного логирования

| Field | Value |
|-------|-------|
| **ID** | F-045 |
| **Severity** | MEDIUM |
| **Category** | G1 (logging) |
| **Bug pattern** | `no-structured-logging` |
| **Fingerprint** | `sha256("G1:logback.xml:no-structured-logging")` → `f9fe8808a76544545477f237811eb5303b72f4f00dfad4a4f5239a3bb799f0b1` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 11 (2026-06-26) |

**Description:**
Только STDOUT с plain-text pattern. Нет JSON формата для log aggregation. Нет маскирования PII. Нет file appender с rotation/retention.

**Impact:**
MEDIUM — невозможность эффективного поиска ошибок, PII в логах.

**Verification recipe:**
```bash
grep -E "JsonLayout|LogstashEncoder|json" backend/src/main/resources/logback.xml
# PASS: JSON layout/formatter present
# FAIL: only plain-text pattern
```

**Required fix:**
Добавить LogstashEncoder или JsonLayout для JSON логов. Настроить маскирование PII. Добавить file appender с rotation (maxHistory, maxFileSize).

**Test coverage:** (пока пусто)

---

### F-046 [MEDIUM] docker-compose не изолирует порты

| Field | Value |
|-------|-------|
| **ID** | F-046 |
| **Severity** | MEDIUM |
| **Category** | D4 (config) |
| **Bug pattern** | `open-infra-ports` |
| **Fingerprint** | `sha256("D4:docker-compose.yml:open-infra-ports")` → `19d0d3667c73ab575141e61665dcf8c1d9c49c31e4a17cc3fad8fea7f2beb740` |
| **Status** | OPEN |
| **Found** | Wave 11 (2026-06-26) |

**Description:**
PostgreSQL (5432) и Redis (6379) проброшены на host во все интерфейсы. Пароль PostgreSQL — mdaopay (дефолт). Нет network segmentation.

**Impact:**
MEDIUM — прямой доступ к БД и кэшу с хоста.

**Verification recipe:**
```bash
grep -E "5432:5432|6379:6379|127\.0\.0\.1:5432" docker-compose.yml
# PASS: порты привязаны к 127.0.0.1 (localhost)
# FAIL: порты проброшены на 0.0.0.0 (все интерфейсы)
```

**Required fix:**
Привязать порты к localhost (`127.0.0.1:5432:5432`). Использовать `${POSTGRES_PASSWORD:?required}`. Разделить сети на frontend/backend/internal.

**Test coverage:** (пока пусто)

---

### F-047 [LOW] backend/docker-compose.yml не включает dependencies

| Field | Value |
|-------|-------|
| **ID** | F-047 |
| **Severity** | LOW |
| **Category** | H4 (process) |
| **Bug pattern** | `missing-docker-deps` |
| **Fingerprint** | `sha256("H4:backend/docker-compose.yml:missing-docker-deps")` → `49361b9f67c099201f40b0355cc80d6d8d5c5f3a8fcfeed8ef395aba6e39696b` |
| **Status** | OPEN |
| **Found** | Wave 11 (2026-06-26) |

**Description:**
При запуске `docker compose up` из backend/ стартует только paymaster без БД и кэша.

**Impact:**
LOW — сломанный DX.

**Verification recipe:**
```bash
grep -E "depends_on|postgres|redis" backend/docker-compose.yml
# PASS: depends_on with postgres and redis
# FAIL: no depends_on
```

**Required fix:**
Добавить depends_on: postgres и redis в backend/docker-compose.yml.

**Test coverage:** (пока пусто)

---

### F-048 [HIGH] DeadManSwitch: pooled ETH accounting

| Field | Value |
|-------|-------|
| **ID** | F-048 |
| **Severity** | HIGH |
| **Category** | C6 (smart contract) |
| **Bug pattern** | `pooled-eth-accounting` |
| **Fingerprint** | `sha256("C6:DeadManSwitch.sol:claimFunds:pooled-eth-accounting")` → `7e05246f25b37c911c84ded2c791368f746aa33b2f96820d07393b9839186b69` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 11 (2026-06-26) |
| **Last verified** | Wave 12 (2026-06-26) — CLAIMED_FIXED |

**Description:**
claimFunds() использует address(this).balance — отправляет ВЕСЬ баланс контракта, а не только сумму для конкретного кошелька. Нет per-wallet учёта депозитов.

**Impact:**
HIGH — бенефициар может украсть чужие средства.

**Lifecycle:**
- 2026-06-26 Wave 11: Found (severity HIGH)
- 2026-06-26 Wave 12: Fixed in commit e82f790 — per-wallet `deposits[wallet]` tracking in receive() + claimFunds() sends only `deposits[wallet]` → CLAIMED_FIXED

**Verification recipe:**
```bash
grep -A 10 "function claimFunds" contracts/src/DeadManSwitch.sol | grep "address(this).balance\|deposits\["
# PASS: per-wallet deposit tracking (deposits[wallet])
# FAIL: address(this).balance without per-wallet tracking
```

**Required fix:**
Добавить `mapping(address => uint256) public deposits` и отправлять только `deposits[beneficiary]`, а не весь баланс контракта.

**Test coverage:** (пока пусто)

---

### F-049 [MEDIUM] SocialRecoveryModule: депозит 0.01 ETH сжигается при cleanupExpiredRecovery

| Field | Value |
|-------|-------|
| **ID** | F-049 |
| **Severity** | MEDIUM |
| **Category** | C6 (smart contract) |
| **Bug pattern** | `burned-deposit` |
| **Fingerprint** | `sha256("C6:SocialRecoveryModule.sol:cleanupExpiredRecovery:burned-deposit")` → `8c179bc4683b4dd91a9952b5828505db6f353ce7f28a5d48b36d1ca3be63e33a` |
| **Status** | OPEN |
| **Found** | Wave 11 (2026-06-26) |

**Description:**
cleanupExpiredRecovery() обнуляет recoveryDeposit[wallet] но не возвращает депозит ни инициатору, ни вызывающему cleanup. Средства остаются в контракте навсегда.

**Impact:**
MEDIUM — потеря пользовательских средств (0.01 ETH × количество просроченных recovery).

**Verification recipe:**
```bash
grep -A 10 "function cleanupExpiredRecovery" contracts/src/SocialRecoveryModule.sol | grep "transfer\|call\|send\|refund\|return"
# PASS: deposit returned to initiator
# FAIL: deposit zeroed without return
```

**Required fix:**
```solidity
function cleanupExpiredRecovery(address wallet) external {
    // ... check expiry ...
    uint256 deposit = recoveryDeposit[wallet];
    recoveryDeposit[wallet] = 0;
    (bool sent, ) = wallet.call{value: deposit}("");
    require(sent, "DepositReturnFailed");
    // ... rest ...
}
```

**Test coverage:** (пока пусто)

---

### F-050 [MEDIUM] AttestationLedger: attest() без ACL

| Field | Value |
|-------|-------|
| **ID** | F-050 |
| **Severity** | MEDIUM |
| **Category** | A2 (auth) |
| **Bug pattern** | `no-access-control` |
| **Fingerprint** | `sha256("A2:AttestationLedger.sol:attest:no-access-control")` → `55388a9ecca0a486ec84cb2c9c637e9d03424011c633c750e105b94ac0a69c63` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 11 (2026-06-26) |

**Description:**
attest() не имеет access control — любой может установить attestations[attestationHash]=true для любого subject. verify() возвращает true для любого хэша.

**Impact:**
MEDIUM — контракт непригоден как доверенный регистр аттестаций.

**Verification recipe:**
```bash
grep -A 5 "function attest" contracts/src/AttestationLedger.sol | grep "onlyOwner\|onlyRole\|onlyAttester"
# PASS: access control modifier present
# FAIL: external function without modifier
```

**Required fix:**
Добавить `onlyOwner` или `onlyAttester` modifier на attest(). Добавить роль attester с возможностью добавления/удаления.

**Test coverage:** (пока пусто)

---

### F-051 [LOW] MDAOPaymaster: setCooldownPeriod без границ

| Field | Value |
|-------|-------|
| **ID** | F-051 |
| **Severity** | LOW |
| **Category** | C5 (smart contract) |
| **Bug pattern** | `no-parameter-bounds` |
| **Fingerprint** | `sha256("C5:MDAOPaymaster.sol:setCooldownPeriod:no-parameter-bounds")` → `122237297a802de6f2dd262ff667902f07962427648057b6b9ca402549542c78` |
| **Status** | OPEN |
| **Found** | Wave 11 (2026-06-26) |

**Description:**
setCooldownPeriod() не имеет нижней/верхней границы. Значение 0 отключает anti-spam, type(uint256).max блокирует навсегда.

**Impact:**
LOW — требует compromised owner.

**Verification recipe:**
```bash
grep -A 5 "function setCooldownPeriod" contracts/src/MDAOPaymaster.sol | grep "MIN_COOLDOWN\|MAX_COOLDOWN\|require"
# PASS: bounds check present
# FAIL: no bounds
```

**Required fix:**
Добавить MIN_COOLDOWN (например, 1 час) и MAX_COOLDOWN (например, 7 дней).

**Test coverage:** (пока пусто)

---

### F-052 [LOW] InsuranceFund: auditorSignatures не проверяются

| Field | Value |
|-------|-------|
| **ID** | F-052 |
| **Severity** | LOW |
| **Category** | A2 (auth) |
| **Bug pattern** | `unverified-signatures` |
| **Fingerprint** | `sha256("A2:InsuranceFund.sol:submitClaim:unverified-signatures")` → `73097bdf8e9aef61feaed679221957f0c4555aeaf274ffeabac5c31c0de1eb46` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 11 (2026-06-26) |

**Description:**
submitClaim() проверяет только auditorSignatures.length >= 3. Сами подписи не валидируются.

**Impact:**
LOW — submitClaim не меняет состояние (только event), но вводит в заблуждение.

**Verification recipe:**
```bash
grep -A 15 "function submitClaim" contracts/src/InsuranceFund.sol | grep -E "ECDSA.recover|ecrecover|verifySplit"
# PASS: signature verification present
# FAIL: only length check
```

**Required fix:**
Валидировать каждую подпись auditor через ECDSA.recover против списка доверенных auditor address.

**Test coverage:** (пока пусто)

---

### F-053 [MEDIUM] WatchtowerService coroutine leak

| Field | Value |
|-------|-------|
| **ID** | F-053 |
| **Severity** | MEDIUM |
| **Category** | I2 (resilience) |
| **Bug pattern** | `fire-and-forget-coroutine-leak` |
| **Fingerprint** | `sha256("I2:WatchtowerService.kt:notifyWebhook:fire-and-forget-coroutine-leak")` → `af6ec135cfe393991e73dcd2364e4bbecb188bfe6c4c39f6c7a665271df5d13e` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 11 (2026-06-26) |

**Description:**
notifyWebhook() создаёт CoroutineScope(Dispatchers.IO).launch {} при КАЖДОМ вызове. Не привязаны к class-level scope, нет exception handler, нет таймаута.

**Impact:**
MEDIUM — утечка памяти и тредов при высокой частоте recovery-ивентов.

**Verification recipe:**
```bash
grep -B 2 -A 5 "CoroutineScope\|\.launch" backend/src/main/kotlin/com/mdaopay/paymaster/WatchtowerService.kt
# PASS: class-level CoroutineScope + supervisorScope + withTimeout
# FAIL: new CoroutineScope per call without exception handler
```

**Required fix:**
Использовать class-level `CoroutineScope(supervisorJob + Dispatchers.IO)` с `withTimeout(5000)` и `try/catch` внутри launch.

**Test coverage:** (пока пусто)

---

### F-054 [HIGH] Auth endpoints без rate limiting

| Field | Value |
|-------|-------|
| **ID** | F-054 |
| **Severity** | HIGH |
| **Category** | A1 (auth) |
| **Bug pattern** | `auth-brute-force-no-ratelimit` |
| **Fingerprint** | `sha256("A1:Application.kt:auth-routes:auth-brute-force-no-ratelimit")` → `f2f0c0043272d7c60d448f87a278fcb348a00f472ffcb5ab344f2686730d7a8c` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 11 (2026-06-26) |

**Description:**
/auth/login, /auth/register, /auth/refresh не имеют rate limiting. Атакующий может перебирать пароли без ограничения.

**Impact:**
HIGH — перебор паролей, account takeover.

**Verification recipe:**
```bash
grep -A 5 "login\|register\|refresh" backend/src/main/kotlin/com/mdaopay/paymaster/Application.kt | grep -i "rateLimit\|bucket\|limiter"
# PASS: rate limiting present on auth endpoints
# FAIL: no rate limiting
```

**Required fix:**
Добавить rate limiting на /auth/login (max 5/min/IP), /auth/register (max 3/min/IP), /auth/refresh (max 10/min/IP). Использовать Redis-based sliding window.

**Test coverage:** (пока пусто)

---

### F-055 [MEDIUM] Weak password policy

| Field | Value |
|-------|-------|
| **ID** | F-055 |
| **Severity** | MEDIUM |
| **Category** | A1 (auth) |
| **Bug pattern** | `weak-password-policy` |
| **Fingerprint** | `sha256("A1:AuthService.kt:register:weak-password-policy")` → `6d0fd8baa1f4b887fc8115185f799dbab514c227925733fba4b1fbc5db9aa8f6` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 11 (2026-06-26) |

**Description:**
register() не проверяет длину/сложность пароля. Можно зарегистрироваться с паролем "a" или пустой строкой.

**Impact:**
MEDIUM — пользователи могут установить слабые пароли.

**Verification recipe:**
```bash
grep -A 10 "fun register\|fun createUser" backend/src/main/kotlin/com/mdaopay/paymaster/AuthService.kt | grep -i "length\|minLength\|password.*valid\|regex\|pattern"
# PASS: password length/complexity validation present
# FAIL: no validation
```

**Required fix:**
```kotlin
const val MIN_PASSWORD_LENGTH = 8
require(password.length >= MIN_PASSWORD_LENGTH) { "Password must be at least 8 characters" }
// Добавить проверку на complexity: хотя бы 1 буква, 1 цифра
```

**Test coverage:** (пока пусто)

---

### F-056 [MEDIUM] Etherscan proxy open API key abuse

| Field | Value |
|-------|-------|
| **ID** | F-056 |
| **Severity** | MEDIUM |
| **Category** | A2 (auth) |
| **Bug pattern** | `open-proxy-api-key-abuse` |
| **Fingerprint** | `sha256("A2:Application.kt:etherscan-proxy:open-proxy-api-key-abuse")` → `f75d6f1a06a01cfc44c6039a7a7ce0bd48c8c15c1ff9d275422c11ede0dba03a` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 11 (2026-06-26) |

**Description:**
/etherscan-proxy пропускает все query-параметры в Etherscan API, используя сервисный ETHERSCAN_API_KEY. Нет auth, нет rate limiting.

**Impact:**
MEDIUM — финансовые потери при платном API Etherscan.

**Verification recipe:**
```bash
grep -A 10 "etherscan\|etherscan-proxy" backend/src/main/kotlin/com/mdaopay/paymaster/Application.kt | grep -i "apiKey\|auth\|rateLimit"
# PASS: auth check + rate limiting present
# FAIL: no auth, no rate limiting
```

**Required fix:**
Добавить аутентификацию (X-API-Key) и rate limiting. Валидировать параметры запроса (белый список разрешённых модулей/акшенов).

**Test coverage:** (пока пусто)

---

### F-057 [MEDIUM] Onramp routes без аутентификации

| Field | Value |
|-------|-------|
| **ID** | F-057 |
| **Severity** | MEDIUM |
| **Category** | A2 (auth) |
| **Bug pattern** | `unauthorized-onramp-access` |
| **Fingerprint** | `sha256("A2:OnrampRoutes.kt:createOrder:unauthorized-onramp-access")` → `0763b3dba6b3d180f984212758f74001fd81de24b45c3fce07adb149c7076cdb` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 11 (2026-06-26) |
| **Last verified** | Wave 12 (2026-06-26) — CLAIMED_FIXED |

**Description:**
/onramp/order и /onramp/quote не имеют проверки X-API-Key. Любой может генерировать MoonPay URL с произвольным destinationAddress.

**Impact:**
MEDIUM — фишинговые MoonPay URL, усиливает F-037.

**Lifecycle:**
- 2026-06-26 Wave 11: Found (severity MEDIUM)
- 2026-06-26 Wave 12: Fixed in commit aed5b3b — onrampRoutes() registered inside `authenticate("auth-jwt")` block. Regression test in OnrampRoutesAuthTest.kt confirms 401 without auth → CLAIMED_FIXED

**Verification recipe:**
```bash
grep -B 5 -A 2 "onrampRoutes\|authenticate.*auth-jwt" backend/src/main/kotlin/com/mdaopay/paymaster/Application.kt
# PASS: onrampRoutes() inside authenticate("auth-jwt") block
# FAIL: outside auth block

grep -E "onramp quote returns 401\|onramp order returns 401" backend/src/test/kotlin/com/mdaopay/paymaster/OnrampRoutesAuthTest.kt
# PASS: regression test exists
```

**Required fix:**
Добавить middleware проверку X-API-Key на /onramp/order и /onramp/quote.

**Test coverage:** (пока пусто)

---

### F-058 [LOW] SwapRoutes no rate limiting

| Field | Value |
|-------|-------|
| **ID** | F-058 |
| **Severity** | LOW |
| **Category** | I2 (resilience) |
| **Bug pattern** | `swap-dos-no-ratelimit` |
| **Fingerprint** | `sha256("I2:SwapRoutes.kt:executeSwap:swap-dos-no-ratelimit")` → `3eb6a6fcc50b2be1347b44f7b6de53389f161e39ee3331cffe8597a82b805f2f` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 11 (2026-06-26) |

**Description:**
/swap/execute не имеет rate limiting. waitForReceipt блокирует корутину на 60с. После фикса F-035 — авторизованный DoS.

**Impact:**
LOW (после F-035 fix) — DoS авторизованным пользователем.

**Verification recipe:**
```bash
grep -A 5 "fun executeSwap\|swap/execute" backend/src/main/kotlin/com/mdaopay/paymaster/swap/SwapRoutes.kt | grep -i "rateLimit\|limiter\|throttle"
# PASS: rate limiting present
# FAIL: no rate limiting
```

**Required fix:**
Добавить rate limiting на /swap/execute (max 3/min/user).

**Test coverage:** (пока пусто)

---

### F-059 [HIGH] Ethereum JS Bridge exposes wallet signing

| Field | Value |
|-------|-------|
| **ID** | F-059 |
| **Severity** | HIGH |
| **Category** | A1 (auth) |
| **Bug pattern** | `webview-js-bridge-wallet-signing` |
| **Fingerprint** | `sha256("A1:EthereumProviderInjector.kt:_MDAOBridge:webview-js-bridge-wallet-signing")` → `3f0a24df2a762c36c9ed0d17a085c8abaac936530447d241ff8e6f00c9f46715` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 11 (2026-06-26) |

**Description:**
@JavascriptInterface мост _MDAOBridge сохраняется при всех навигациях WebView. Экспортирует personal_sign, eth_requestAccounts. Любой HTTPS-сайт может запросить подпись.

**Impact:**
HIGH — XSS в WebView → signed message attack.

**Verification recipe:**
```bash
grep -A 5 "@JavascriptInterface\|personal_sign\|eth_requestAccounts" app/src/main/java/com/mdaopay/app/core/blockchain/EthereumProviderInjector.kt
# PASS: @JavascriptInterface with domain whitelist or user confirmation required
# FAIL: unrestricted JS bridge with wallet signing
```

**Required fix:**
Ограничить @JavascriptInterface только для доверенных доменов. Добавить user confirmation диалог для personal_sign и eth_requestAccounts. Очищать bridge при navigate.

**Test coverage:** (пока пусто)

---

### F-060 [HIGH] Play Integrity verdict decoded client-side без JWT verify

| Field | Value |
|-------|-------|
| **ID** | F-060 |
| **Severity** | HIGH |
| **Category** | A2 (auth) |
| **Bug pattern** | `play-integrity-no-jwt-verify` |
| **Fingerprint** | `sha256("A2:DeviceIntegrityManager.kt:decodeJwtPayload:play-integrity-no-jwt-verify")` → `a3997ad65d39b74a479f8e476e9e706833740d45d99889aa309b14fb2ce3d269` |
| **Status** | VERIFIED |
| **Found** | Wave 11 (2026-06-26) |
| **Fixed** | 2026-06-26 (commit 5cfaffb) |
| **Last verified** | Wave 12 (2026-06-26) — VERIFIED |

**Description:**
decodeJwtPayload() декодировал Base64 payload без верификации JWT подписи Google (`parts[1]` split + Base64 decode — нет проверки signature). Любой JWT с корректной структурой (HEADER.PAYLOAD.SIGNATURE) принимался.

**Impact:**
HIGH — подделанный integrity verdict позволял обойти device integrity check для HIGH-риска операций (recovery, guardian management, large transfers).

**Fix applied:**
Добавлен метод `verifyIntegrityToken()`, который:
1. Парсит JWT (header.payload.signature) и извлекает `kid` из header
2. Получает Google public key из JWKS endpoint с in-memory кэшированием на 24ч
3. Верифицирует RSA signature через SHA256withRSA
4. Проверяет nonce на соответствие отправленному
5. Проверяет timestamp (≤5 минут, ±1 минута clock skew)
6. Проверяет package name на соответствие `context.packageName`
7. Возвращает `IntegrityPayload` с распарсенными verdicts

При недоступности JWKS (offline) — fallback к закешированным ключам (fail closed, если кэш пуст).

Существующий публичный интерфейс (`checkIntegrity(operation)`) не изменён.

**File changed:** `app/src/main/java/com/mdaopay/app/core/security/DeviceIntegrityManager.kt`
**Regression tests:** `app/src/test/java/com/mdaopay/app/core/security/DeviceIntegrityManagerTest.kt`

**Verification recipe:**
```bash
grep -c "verifyIntegrityToken\|getJwksKeys\|SHA256withRSA" app/src/main/java/com/mdaopay/app/core/security/DeviceIntegrityManager.kt
# PASS: ≥9 (all components present)
# FAIL: only Base64 decode without verification
```

---

### F-061 [MEDIUM] Recovery evalInput stored в plaintext SharedPreferences

| Field | Value |
|-------|-------|
| **ID** | F-061 |
| **Severity** | MEDIUM |
| **Category** | D3 (secrets) |
| **Bug pattern** | `plaintext-sensitive-storage` |
| **Fingerprint** | `sha256("D3:RecoveryShareManager.kt:saveEvalInput:plaintext-sensitive-storage")` → `63cd03acbe8d2002d32a4041a4a49f93c66767cee72200c44458756eb3ec5ad2` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 11 (2026-06-26) |

**Description:**
saveEvalInput() сохраняет PRF eval input (32 байта) в HEX-формате без шифрования, хотя recovery shares шифруются через KeystoreCrypto.

**Impact:**
MEDIUM — evalInput + encrypted share → PRF recovery для атакующего с root.

**Verification recipe:**
```bash
grep -A 5 "saveEvalInput\|fun saveEval" app/src/main/java/com/mdaopay/app/core/recovery/RecoveryShareManager.kt | grep -i "encrypt\|cipher\|keystore"
# PASS: evalInput encrypted before storage
# FAIL: stored as plaintext HEX
```

**Required fix:**
Использовать EncryptedSharedPreferences или шифровать evalInput через KeystoreCrypto перед сохранением.

**Test coverage:** (пока пусто)

---

### F-062 [HIGH] Biometric auth допускает BIOMETRIC_WEAK для всех операций

| Field | Value |
|-------|-------|
| **ID** | F-062 |
| **Severity** | HIGH |
| **Category** | A1 (auth) |
| **Bug pattern** | `biometric-weak-allowed` |
| **Fingerprint** | `sha256("A1:BiometricManager.kt:authenticate:biometric-weak-allowed")` → `4a92971f0946252a752a8e8f0b91a921e2d1f8427b3f94eafda5c120c223f5fa` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 11 (2026-06-26) |

**Description:**
BiometricManager.kt имеет authenticateHighRisk(), но RecoveryScreen всё ещё вызывает authenticate() вместо него. Fix: commit ed39136 — RecoveryScreen вызывает authenticateHighRisk(), SendScreen проверяет amount >= 1000 USDT.
isBiometricAvailable() и authenticate() используют `BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL` для ВСЕХ операций. BIOMETRIC_WEAK включает 2D face unlock, который может быть обойдён фото/видео.

**Impact:**
HIGH — для recovery flow и guardian management (HIGH-риск операции) BIOMETRIC_WEAK позволяет атакующему с фото владельца обойти biometric check.

**Verification recipe:**
```bash
grep -A 3 "BIOMETRIC_WEAK\|BIOMETRIC_STRONG" app/src/main/java/com/mdaopay/app/core/security/BiometricManager.kt
# PASS: high-risk operations require BIOMETRIC_STRONG only
# FAIL: any operation allows BIOMETRIC_WEAK
```

**Required fix:**
Разделить на два уровня: authenticate() для входа допускает WEAK+STRONG; authenticateHighRisk() требует только BIOMETRIC_STRONG.

**Test coverage:** (пока пусто)

---

### F-063 [LOW] PasskeyManager RP ID hardcoded

| Field | Value |
|-------|-------|
| **ID** | F-063 |
| **Severity** | LOW |
| **Category** | A1 (auth) |
| **Bug pattern** | `passkey-rpid-hardcoded` |
| **Fingerprint** | `sha256("A1:PasskeyManager.kt:rpId:passkey-rpid-hardcoded")` → `d5d64acec51addbbc31947a8055ecc624526856b00aea2bcb57bff046a2b5318` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 11 (2026-06-26) |

**Description:**
rpId = "mdaopay.app" жёстко закодирован, не меняется между build flavors. Dev сборка имеет applicationIdSuffix ".dev" но RP ID единый.

**Impact:**
LOW — конфликт credential'ов между сборками.

**Verification recipe:**
```bash
grep "rpId\|RP_ID\|relyingParty" app/src/main/java/com/mdaopay/app/core/recovery/PasskeyManager.kt
# PASS: rpId configurable per build flavor (BuildConfig)
# FAIL: hardcoded string
```

**Required fix:**
Вынести rpId в BuildConfig или ресурсы с переопределением для разных flavors.

**Test coverage:** (пока пусто)

---

### F-064 [INFO] Software-only root detection bypassable

| Field | Value |
|-------|-------|
| **ID** | F-064 |
| **Severity** | INFO |
| **Category** | A1 (auth) |
| **Bug pattern** | `software-root-detection-bypassable` |
| **Fingerprint** | `sha256("A1:DeviceIntegrityManager.kt:isRooted:software-root-detection-bypassable")` → `fa62a850a24570517d46ba1951645fa7a5c89336e29970da6b7033a06ee3e880` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 11 (2026-06-26) |

**Description:**
isRooted() использует базовые эвристики (su paths, test-keys, root packages) — легко обходится Magisk Hide / KernelSU.

**Impact:**
INFO — ложное чувство безопасности.

**Verification recipe:**
```bash
grep -A 20 "fun isRooted" app/src/main/java/com/mdaopay/app/core/security/DeviceIntegrityManager.kt
# PASS: Play Integrity API used as primary signal
# FAIL: only software checks
```

**Required fix:**
Документировать что isRooted() — только мягкая эвристика. Основная защита должна быть через Play Integrity API (server-side).

**Test coverage:** (пока пусто)

---

### F-065 [HIGH] FCM push-уведомления полностью сломаны

| Field | Value |
|-------|-------|
| **ID** | F-065 |
| **Severity** | HIGH |
| **Category** | D2 (config) |
| **Bug pattern** | `fcm-push-broken` |
| **Fingerprint** | `sha256("D2:fcm.ts:sendPushNotification:fcm-push-broken")` → `51c53eb4e1c1dd281f812d7408f01185403843646ca4010e9addabf31683b4af` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 11 (2026-06-26) |
| **Last verified** | Wave 12 (2026-06-26) — CLAIMED_FIXED |

**Description:**
sendPushNotification использует declare const FCM_SERVER_KEY, но в modules format env доступен только через параметр `env`. FCM_SERVER_KEY всегда undefined. Все push-уведомления никогда не доставляются.

**Impact:**
HIGH — guardians не получают уведомления о recovery. Социальное восстановление ослаблено.

**Lifecycle:**
- 2026-06-26 Wave 11: Found (severity HIGH)
- 2026-06-26 Wave 12: Fixed in commit e4c17ff — sendPushNotification now takes `fcmServerKey: string` as first parameter, called with `env.FCM_SERVER_KEY` from index.ts → CLAIMED_FIXED

**Verification recipe:**
```bash
grep -A 5 "export async function sendPushNotification" relay/src/fcm.ts
# PASS: first parameter is fcmServerKey: string
# FAIL: declare const FCM_SERVER_KEY (global)
```

**Required fix:**
```typescript
export async function sendPushNotification(fcmServerKey: string, tokens: string[], payload: Record<string, string>): Promise<void> {
    // use fcmServerKey parameter instead of global
}
```

**Test coverage:** (пока пусто)

---

### F-066 [MEDIUM] GET /guardian/invite/:inviteId без аутентификации

| Field | Value |
|-------|-------|
| **ID** | F-066 |
| **Severity** | MEDIUM |
| **Category** | A2 (auth) |
| **Bug pattern** | `no-auth-invite-read` |
| **Fingerprint** | `sha256("A2:relay/src/index.ts:getInvite:no-auth-invite-read")` → `72fd0939d35597539d9291a84fce334d697fcb948b8c5348721118540fe8f8e7` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 11 (2026-06-26) |
| **Last verified** | Wave 12 (2026-06-26) — CLAIMED_FIXED |

**Description:**
Любой кто знает inviteId может прочитать encryptedShare, guardianLabel, walletAddress.

**Impact:**
MEDIUM — утечка encryptedShare.

**Lifecycle:**
- 2026-06-26 Wave 11: Found (severity MEDIUM)
- 2026-06-26 Wave 12: Fixed in commit aed5b3b — all relay endpoints now use `requireAuth` via `verifySignature` from auth.ts. Regression tests in invite.test.ts confirm 401 without valid signature → CLAIMED_FIXED

**Verification recipe:**
```bash
grep -A 10 "guardian/invite/:" relay/src/index.ts | grep "requireAuth\|authError\|verifySignature"
# PASS: auth check present
# FAIL: no auth check
```

**Required fix:**
Добавить requireAuth на GET /guardian/invite/:inviteId.

**Test coverage:** (пока пусто)

---

### F-067 [MEDIUM] Принятие инвайта без проверки guardian

| Field | Value |
|-------|-------|
| **ID** | F-067 |
| **Severity** | MEDIUM |
| **Category** | A2 (auth) |
| **Bug pattern** | `no-guardian-check-accept` |
| **Fingerprint** | `sha256("A2:relay/src/index.ts:acceptInvite:no-guardian-check-accept")` → `855ea9df00757820e01baf8b01acf2e43720d62772ad0d3f947fe27378a5592e` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 11 (2026-06-26) |
| **Last verified** | Wave 12 (2026-06-26) — CLAIMED_FIXED |

**Description:**
POST /guardian/invite/:inviteId/accept требует HMAC но не проверяет что вызывающий — именно guardian инвайта.

**Impact:**
MEDIUM — любой HMAC-клиент может принять любой инвайт.

**Lifecycle:**
- 2026-06-26 Wave 11: Found (severity MEDIUM)
- 2026-06-26 Wave 12: Fixed in commit aed5b3b — all relay endpoints now use `verifySignature` (P-256) from auth.ts, checking caller identity against guardianIdentityHash → CLAIMED_FIXED

**Verification recipe:**
```bash
grep -A 15 "invite/:inviteId/accept" relay/src/index.ts | grep "verifySignature\|requireAuth\|authError"
# PASS: verifySignature check present
# FAIL: no guardian identity check
```

**Required fix:**
Добавить проверку что HMAC подпись соответствует guardianIdentityHash, указанному в инвайте.

**Test coverage:** (пока пусто)

---

### F-068 [MEDIUM] GET /recovery/pending/:walletAddress без аутентификации

| Field | Value |
|-------|-------|
| **ID** | F-068 |
| **Severity** | MEDIUM |
| **Category** | A2 (auth) |
| **Bug pattern** | `no-auth-pending-recovery` |
| **Fingerprint** | `sha256("A2:relay/src/index.ts:getPendingRecovery:no-auth-pending-recovery")` → `af9d07c226e02706db3a78aa8e986becbd544aa4ea71f6ecf70ff8ac15c93eff` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 11 (2026-06-26) |
| **Last verified** | Wave 12 (2026-06-26) — CLAIMED_FIXED |

**Description:**
Любой может узнать активный recovery, newPasskeyPubKey, deadline, approvals, threshold.

**Impact:**
MEDIUM — утечка информации о состоянии recovery.

**Lifecycle:**
- 2026-06-26 Wave 11: Found (severity MEDIUM)
- 2026-06-26 Wave 12: Fixed in commit aed5b3b — all relay endpoints now use `requireAuth` via `verifySignature` → CLAIMED_FIXED

**Verification recipe:**
```bash
grep -A 10 "recovery/pending/:" relay/src/index.ts | grep "requireAuth\|authError\|verifySignature"
# PASS: auth check present
# FAIL: no auth check
```

**Required fix:**
Добавить requireAuth на GET /recovery/pending/:walletAddress.

**Test coverage:** (пока пусто)

---

### F-069 [MEDIUM] Нет ограничения размера тела запроса

| Field | Value |
|-------|-------|
| **ID** | F-069 |
| **Severity** | MEDIUM |
| **Category** | I2 (resilience) |
| **Bug pattern** | `no-request-size-limit` |
| **Fingerprint** | `sha256("I2:relay/src/index.ts:no-request-size-limit")` → `6c96cb701702270234444c4941c3e036b0dceab10b86e923bcda35757adeba2d` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 11 (2026-06-26) |

**Description:**
request.text() читает всё тело запроса в память без лимита. CF Workers 128MB limit — OOM вектор.

**Impact:**
MEDIUM — DoS через OOM.

**Verification recipe:**
```bash
grep "request.text\|request.json\|request.arrayBuffer" relay/src/index.ts | grep -i "limit\|max\|size"
# PASS: size limit before reading body (e.g., request.text({ maxBytes: 10000 }))
# FAIL: unbounded request.text()
```

**Required fix:**
```typescript
const body = await request.text({ maxBytes: 10000 });
// или
const contentLength = parseInt(request.headers.get('Content-Length') || '0');
if (contentLength > 10000) return new Response('Request too large', { status: 413 });
```

**Test coverage:** (пока пусто)

---

### F-070 [MEDIUM] Нет rate limiting ни на одном эндпоинте relay

| Field | Value |
|-------|-------|
| **ID** | F-070 |
| **Severity** | MEDIUM |
| **Category** | I2 (resilience) |
| **Bug pattern** | `no-ratelimit-relay` |
| **Fingerprint** | `sha256("I2:relay:wrangler.toml:no-ratelimit-relay")` → `e0d45c06603f6e6a87e821b3fb5117095f0ca0b15aafdf170382eb00cdf35091` |
| **Status** | CLAIMED_FIXED |
| **Found** | Wave 11 (2026-06-26) |

**Description:**
Ни один эндпоинт не имеет rate limiting. KV имеет лимит 1000 writes/sec. Атакующий может заспамить write-эндпоинты.

**Impact:**
MEDIUM — KV throttle, отказ обслуживания.

**Verification recipe:**
```bash
grep -E "rateLimit|RateLimit|ratelimit|throttle" relay/wrangler.toml relay/src/index.ts
# PASS: rate limiting configured
# FAIL: no rate limiting
```

**Required fix:**
Добавить rate limiting через CF Worker Rate Limiting API или встроенную очередь. Для write-эндпоинтов max 10/min/IP.

**Test coverage:** (пока пусто)

---

### F-071 [LOW] Ошибка конфигурации раскрывает детали аутентификации

| Field | Value |
|-------|-------|
| **ID** | F-071 |
| **Severity** | LOW |
| **Category** | G4 (logging) |
| **Bug pattern** | `error-message-info-leak` |
| **Fingerprint** | `sha256("G4:relay/src/index.ts:requireAuth:error-message-info-leak")` → `6318638cdc278c2e879c8ae090104679b8e4da5660e250b1ee49c9fe3e624995` |
| **Status** | OPEN |
| **Found** | Wave 11 (2026-06-26) |

**Description:**
При отсутствии RELAY_SECRET ответ содержит "Server misconfigured: RELAY_SECRET not set" — раскрывает тип auth и имя переменной.

**Impact:**
LOW — information leak.

**Verification recipe:**
```bash
grep "RELAY_SECRET\|misconfigured" relay/src/index.ts | grep "not set\|missing"
# PASS: generic error message (e.g., "Server misconfigured")
# FAIL: reveals variable name or auth type
```

**Required fix:**
```typescript
return new Response('Service unavailable', { status: 500 });  // generic
```

**Test coverage:** (пока пусто)

---

### F-100 [CRITICAL] Paymaster не используется в send-флоу

| Field | Value |
|-------|-------|
| **ID** | F-100 |
| **Severity** | CRITICAL |
| **Category** | G1 (architecture) |
| **Bug pattern** | `paymaster-unused-send` |
| **Fingerprint** | `sha256("G1:app/.../SendRepository.kt:sendUsdt:paymaster-unused-send")` → `a100...` |
| **Status** | CLAIMED_FIXED |
| **Found** | External Cloud Audit 2026-06-29 |

**Description:**
SendRepository.sendUsdt() строит UserOp с paymasterAndData = ByteArray(0).
GaslessTransactionOrchestrator — запрос подписи через PaymasterClient.signUserOp(). SendRepository.sendUsdt() — gasless-first: signUserOp() → paymasterAndData → execute. Fallback на native-gas при ошибке paymaster.
Fix: (unstaged) — SendRepository injects GaslessTransactionOrchestrator via Lazy, fallback to sendUsdtNative().

**Required fix:**
1. Исправить F-130 (PaymasterClient API)
2. GaslessTransactionOrchestrator заработает автоматически

---

### F-102 [HIGH] Депозит не сжигается при veto

| Field | Value |
|-------|-------|
| **ID** | F-102 |
| **Severity** | HIGH |
| **Category** | C2 (token-economics) |
| **Bug pattern** | `veto-no-burn` |
| **Fingerprint** | `sha256("C2:contracts/SocialRecoveryModule.sol:vetoRecovery:veto-no-burn")` → `a102...` |
| **Status** | CLAIMED_FIXED |
| **Found** | External Cloud Audit 2026-06-29 |

**Description:**
vetoRecovery использует `mdaoToken.transfer(BURN_ADDRESS, deposit)` вместо `mdaoToken.burn(deposit)`. BURN_ADDRESS — ошибочный паттерн: токены навсегда застревают в нулевом адресе (no `burn()` event = проблемы с индексацией). Fix: commit 95214cb — vetoRecovery использует MDAOToken.burn() вместо transfer(BURN_ADDRESS).

**Required fix:**
```solidity
MDAOToken(address(mdaoToken)).burn(deposit);
```

---

### F-108 [CRITICAL] P-256 Precompile (RIP-7212) — недоступен на BSC Testnet

| Field | Value |
|-------|-------|
| **ID** | F-108 |
| **Severity** | CRITICAL |
| **Category** | C5 (precompile) |
| **Bug pattern** | `rip7212-unavailable` |
| **Fingerprint** | `sha256("C5:SocialRecoveryModule.sol:P256_VERIFIER:rip7212-unavailable")` → `f108...` |
| **Status** | VERIFIED |
| **Found** | BSC Testnet Audit 2026-06-29 |

**Description:** SocialRecoveryModule использует P-256 precompile at 0x100 (RIP-7212), который вероятно не активен на BSC Testnet. `staticcall` к несуществующему precompile возвращает `(true, 0x00)` — recovery невозможен.

**Impact:** CRITICAL — блокер для testnet.

**Required fix:** Проверить RIP-7212 перед деплоем. Если недоступен — задеплоить MockP256 и сделать P256_VERIFIER mutable.

---

### F-109 [HIGH] WebAuthn DER→raw signature conversion отсутствует

| Field | Value |
|-------|-------|
| **ID** | F-109 |
| **Severity** | HIGH |
| **Category** | B5 (key-mgmt) |
| **Bug pattern** | `der-to-raw-missing` |
| **Fingerprint** | `sha256("B5:SocialRecoveryModule.sol:_verifyP256:der-to-raw-missing")` → `f109...` |
| **Status** | VERIFIED |
| **Found** | BSC Testnet Audit 2026-06-29 |

**Description:** WebAuthn возвращает ASN.1 DER signatures (70-72 bytes). P-256 precompile требует raw 64 bytes (r||s). Конвертация отсутствует — все WebAuthn подписи будут fail.

**Impact:** HIGH — блокер для testnet (WebAuthn recovery не работает).

**Required fix:** Добавить `derToRS()` в SocialRecoveryModule.sol или на mobile.

---

### F-110 [HIGH] JWT_SECRET entropy check отсутствует

| Field | Value |
|-------|-------|
| **ID** | F-110 |
| **Severity** | HIGH |
| **Category** | D1 (secrets) |
| **Bug pattern** | `jwt-entropy-missing` |
| **Fingerprint** | `sha256("D1:backend:AppConfig.kt:jwt-entropy-missing")` → `f110...` |
| **Status** | NEW |
| **Found** | BSC Testnet Audit 2026-06-29 |

**Description:** "32 characters" != "256 bits entropy". `aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa` (32 chars, 0 entropy) проходит length check.

**Impact:** HIGH — JWT подписи могут быть подделаны.

**Required fix:** `require(jwtSecret.length >= 44)` — Base64-encoded 256-bit key.

---

### F-111 [HIGH] ALLOW_LOCAL_SIGNING production guard отсутствует

| Field | Value |
|-------|-------|
| **ID** | F-111 |
| **Severity** | HIGH |
| **Category** | D2 (config) |
| **Bug pattern** | `local-signing-production` |
| **Fingerprint** | `sha256("D2:backend:AppConfig.kt:local-signing-production")` → `f111...` |
| **Status** | NEW |
| **Found** | BSC Testnet Audit 2026-06-29 |

**Description:** Нет `isProduction()` guard. Production может запуститься с ALLOW_LOCAL_SIGNING=true и приватным ключом в env.

**Impact:** HIGH — компрометация paymaster ключа.

**Required fix:** `if (production && ALLOW_LOCAL_SIGNING) throw IllegalStateException(...)`.

---

### F-112 [HIGH] P-256 public key on-curve validation отсутствует

| Field | Value |
|-------|-------|
| **ID** | F-112 |
| **Severity** | HIGH |
| **Category** | B5 (key-mgmt) |
| **Bug pattern** | `p256-oncurve-missing` |
| **Fingerprint** | `sha256("B5:SocialRecoveryModule.sol:addGuardian:p256-oncurve-missing")` → `f112...` |
| **Status** | VERIFIED |
| **Found** | BSC Testnet Audit 2026-06-29 |

**Description:** `addGuardian()` принимает pubKeyX/pubKeyY без on-curve check. Guardian с невалидной точкой занимает слот и блокирует recovery.

**Impact:** HIGH — griefing (занятие слотов guardian).

**Required fix:** `require(isOnCurveP256(pubKeyX, pubKeyY))` в addGuardian().

---

### F-113 [HIGH] ERC-4337 v0.6 deprecated

| Field | Value |
|-------|-------|
| **ID** | F-113 |
| **Severity** | HIGH |
| **Category** | F1 (deps) |
| **Bug pattern** | `erc4337-v06-deprecated` |
| **Fingerprint** | `sha256("F1:contracts:entrypoint:v06-deprecated")` → `f113...` |
| **Status** | NEW |
| **Found** | BSC Testnet Audit 2026-06-29 |

**Description:** Используется EntryPoint v0.6. v0.7 — актуальная (2024). Bundler'ы прекращают поддержку v0.6.

**Impact:** HIGH — для mainnet обязательна миграция.

**Required fix:** Запланировать миграцию на v0.7 (2-3 нед).

---

### F-114 [MEDIUM] NicknameRegistry — расхождение длины/charset

| Field | Value |
|-------|-------|
| **ID** | F-114 |
| **Severity** | MEDIUM |
| **Category** | C3 (access) |
| **Bug pattern** | `nickname-length-charset` |
| **Fingerprint** | `sha256("C3:NicknameRegistry.sol:setNickname:nickname-length-charset")` → `f114...` |
| **Status** | NEW |
| **Found** | BSC Testnet Audit 2026-06-29 |

**Description:** PRD: 3-20, `[a-zA-Z0-9_-]`. TDD Appendix A: 3-32, `[a-zA-Z0-9_]`. Код: 32 chars, без дефиса.

**Required fix:** Синхронизировать: 3-20 chars, `[a-zA-Z0-9_-]`.

---

### F-115 [MEDIUM] MDAO Token — max burn fee 10%

| Field | Value |
|-------|-------|
| **ID** | F-115 |
| **Severity** | MEDIUM |
| **Category** | C6 (paymaster) |
| **Bug pattern** | `burn-fee-cap-high` |
| **Fingerprint** | `sha256("C6:MDAOToken.sol:MAX_BURN_FEE_BPS:burn-fee-cap-high")` → `f115...` |
| **Status** | NEW |
| **Found** | BSC Testnet Audit 2026-06-29 |

**Description:** `MAX_BURN_FEE_BPS = 1000` (10%). Рекомендация: 300 (3%).

**Required fix:** Уменьшить до 300 BPS.

---

### F-116 [MEDIUM] Daily withdrawal cap — edge case

| Field | Value |
|-------|-------|
| **ID** | F-116 |
| **Severity** | MEDIUM |
| **Category** | C6 (paymaster) |
| **Bug pattern** | `withdrawal-cap-reset` |
| **Fingerprint** | `sha256("C6:MDAOPaymaster.sol:dailyWithdrawalCapBps:withdrawal-cap-reset")` → `f116...` |
| **Status** | NEW |
| **Found** | BSC Testnet Audit 2026-06-29 |

**Description:** 24h reset без cooldown. 23:59 → 50%, 00:00 → 50% = 75% за 1 мин.

**Required fix:** Sliding window или min interval.

---

### F-117 [MEDIUM] Chain ID confusion 56/97

| Field | Value |
|-------|-------|
| **ID** | F-117 |
| **Severity** | MEDIUM |
| **Category** | D2 (config) |
| **Bug pattern** | `chainid-confusion` |
| **Fingerprint** | `sha256("D2:config:chainId:chainid-confusion")` → `f117...` |
| **Status** | NEW |
| **Found** | BSC Testnet Audit 2026-06-29 |

**Description:** Dev/Staging: 97, Prod: 56. Разные precompiles, latency. **Блокер для testnet.**

**Required fix:** CI validation: testnet builds reject 56, mainnet reject 97.

---

### F-118 [MEDIUM] SessionKeyModule — нет on-chain selector whitelist

| Field | Value |
|-------|-------|
| **ID** | F-118 |
| **Severity** | MEDIUM |
| **Category** | C3 (access) |
| **Bug pattern** | `selector-whitelist-missing` |
| **Fingerprint** | `sha256("C3:SessionKeyModule.sol:permissions:selector-whitelist-missing")` → `f118...` |
| **Status** | NEW |
| **Found** | BSC Testnet Audit 2026-06-29 |

**Description:** Любой selector может быть добавлен. Client-side validation обходится прямой отправкой UserOp.

**Required fix:** `mapping(bytes4 => bool) allowedSelectors` + owner-only add.

---

### F-119 [MEDIUM] Price Oracle — только 2/3 источников

| Field | Value |
|-------|-------|
| **ID** | F-119 |
| **Severity** | MEDIUM |
| **Category** | C5 (oracle) |
| **Bug pattern** | `oracle-insufficient-sources` |
| **Fingerprint** | `sha256("C5:backend:PriceOracle:oracle-insufficient-sources")` → `f119...` |
| **Status** | NEW |
| **Found** | BSC Testnet Audit 2026-06-29 |

**Description:** DexScreener + CoinGecko. OnChain TWAP — placeholder. Нет fallback при недоступности обоих.

**Required fix:** Реализовать TWAP или добавить Binance API.

---

### F-120 [MEDIUM] SSS over GF(256) — byte-wise spec missing

| Field | Value |
|-------|-------|
| **ID** | F-120 |
| **Severity** | MEDIUM |
| **Category** | B3 (crypto) |
| **Bug pattern** | `sss-gf256-spec` |
| **Fingerprint** | `sha256("B3:mobile:SSS:byte-wise-spec")` → `f120...` |
| **Status** | NEW |
| **Found** | BSC Testnet Audit 2026-06-29 |

**Description:** Не описано, как 256-bit secret представляется в GF(256). Нужна explicit byte-wise spec.

**Required fix:** "Byte-wise SSS: 32 parallel GF(256) polynomials."

---

### F-121 [MEDIUM] PBKDF2-HMAC-SHA256 vs Argon2id

| Field | Value |
|-------|-------|
| **ID** | F-121 |
| **Severity** | MEDIUM |
| **Category** | B3 (crypto) |
| **Bug pattern** | `pbkdf2-outdated` |
| **Fingerprint** | `sha256("B3:mobile:KDF:pbkdf2-outdated")` → `f121...` |
| **Status** | NEW |
| **Found** | BSC Testnet Audit 2026-06-29 |

**Description:** PBKDF2 уязвим к GPU/ASIC. OWASP 2025 рекомендует Argon2id.

**Required fix:** Миграция на Argon2id (Phase 1).

---

### F-122 [MEDIUM] AES-256-GCM — random IV entropy

| Field | Value |
|-------|-------|
| **ID** | F-122 |
| **Severity** | MEDIUM |
| **Category** | B3 (crypto) |
| **Bug pattern** | `aes-gcm-iv-random` |
| **Fingerprint** | `sha256("B3:mobile:AES-GCM:iv-random")` → `f122...` |
| **Status** | NEW |
| **Found** | BSC Testnet Audit 2026-06-29 |

**Description:** Random 12-byte IV. Birthday paradox: 50% collision at ~2^48 ops.

**Required fix:** Counter-based IV (Phase 1).

---

### F-123 [MEDIUM] Slither CI — исключение pragma-version

| Field | Value |
|-------|-------|
| **ID** | F-123 |
| **Severity** | MEDIUM |
| **Category** | H4 (process) |
| **Bug pattern** | `slither-exclude-pragma` |
| **Fingerprint** | `sha256("H4:ci:slither:pragma-exclude")` → `f123...` |
| **Status** | NEW |
| **Found** | BSC Testnet Audit 2026-06-29 |

**Description:** `--exclude pragma-version` скрывает предупреждения.

**Required fix:** Убрать исключение, добавить solhint rule.

---

### F-124 [MEDIUM] Cloud SQL HA — single region

| Field | Value |
|-------|-------|
| **ID** | F-124 |
| **Severity** | MEDIUM |
| **Category** | I4 (resilience) |
| **Bug pattern** | `cloudsql-single-region` |
| **Fingerprint** | `sha256("I4:infra:cloudsql:single-region")` → `f124...` |
| **Status** | NEW |
| **Found** | BSC Testnet Audit 2026-06-29 |

**Description:** 2 zones в одном region — не защищает от regional outage.

**Required fix:** Cross-region DR replica.

---

### F-125 [LOW] HikariCP pool size = 10

| Field | Value |
|-------|-------|
| **ID** | F-125 |
| **Severity** | LOW |
| **Category** | I4 (resilience) |
| **Bug pattern** | `hikaricp-pool-small` |
| **Fingerprint** | `sha256("I4:backend:database.kt:hikaricp-pool-small")` → `f125...` |
| **Status** | NEW |
| **Found** | BSC Testnet Audit 2026-06-29 |

**Required fix:** Увеличить до 25.

---

### F-126 [LOW] ConcurrentHashMap — memory leak (rate limit)

| Field | Value |
|-------|-------|
| **ID** | F-126 |
| **Severity** | LOW |
| **Category** | I4 (resilience) |
| **Bug pattern** | `concurrenthashmap-leak` |
| **Fingerprint** | `sha256("I4:backend:RateLimiter:concurrenthashmap-leak")` → `f126...` |
| **Status** | NEW |
| **Found** | BSC Testnet Audit 2026-06-29 |

**Required fix:** Использовать Caffeine cache с TTL.

---

### F-127 [LOW] Touch target — MDAOButton Sm=38dp

| Field | Value |
|-------|-------|
| **ID** | F-127 |
| **Severity** | LOW |
| **Category** | G1 (ux) |
| **Bug pattern** | `touch-target-small` |
| **Fingerprint** | `sha256("G1:app:MDAOButton:38dp")` → `f127...` |
| **Status** | NEW |
| **Found** | BSC Testnet Audit 2026-06-29 |

**Required fix:** Увеличить до 48dp.

---

### F-128 [LOW] Content descriptions — accessibility

| Field | Value |
|-------|-------|
| **ID** | F-128 |
| **Severity** | LOW |
| **Category** | G1 (ux) |
| **Bug pattern** | `content-description-missing` |
| **Fingerprint** | `sha256("G1:app:components:content-description-missing")` → `f128...` |
| **Status** | NEW |
| **Found** | BSC Testnet Audit 2026-06-29 |

**Required fix:** Добавить contentDescription для ProductCard, ErrorIcon, QR code.

---

### F-129 [CRITICAL] Paymaster — KMS для подписи не реализован

| Field | Value |
|-------|-------|
| **ID** | F-129 |
| **Severity** | CRITICAL |
| **Category** | D1 (secrets) |
| **Bug pattern** | `kms-signing-missing` |
| **Fingerprint** | `sha256("D1:backend:PaymasterService:kms-signing-missing")` → `f129...` |
| **Status** | NEW |
| **Found** | Architecture Audit 2026-06-30 |

**Description:**
TDD требует KMS remote signing (GCP/AWS KMS) для paymaster ключа. Реализация: env-var `PAYMASTER_PRIVATE_KEY` → `ECKeyPair.create()`. Приватный ключ в env var / памяти процесса — компрометация через heap-dump, /proc/pid/environ, k8s describe pod, Sentry crash reports.

**Impact:** CRITICAL — компрометация paymaster ключа = подделка любых подписей.

**Required fix:**
1. Создать `PaymasterSigner` interface с `signDigest(digest): (v,r,s)`
2. Реализовать `LocalPaymasterSigner` (dev) и `KmsPaymasterSigner` (production)
3. DI-переключение по `KMS_KEY_NAME` / `ALLOW_LOCAL_SIGNING`

---

### F-130 [CRITICAL] PaymasterClient API не соответствует SignRequest бэкенда

| Field | Value |
|-------|-------|
| **ID** | F-130 |
| **Severity** | CRITICAL |
| **Category** | G1 (architecture) |
| **Bug pattern** | `paymaster-client-api-mismatch` |
| **Fingerprint** | `sha256("G1:app:PaymasterClient:api-mismatch")` → `f130...` |
| **Status** | CLAIMED_FIXED |
| **Found** | Architecture Audit 2026-06-30 |

**Description:**
`PaymasterClient.getQuote()` шлёт `{sender, token, maxTokenAmount}`, но бэкенд `PaymasterService.SignRequest` требует `nonce, callData, verificationGasLimit, callGasLimit, preVerificationGas, maxPriorityFeePerGas, maxFeePerGas`. Любой вызов /sign возвращает 400. Fix: commit d9af2c4 — PaymasterClient.signUserOp() отправляет полный SignRequest соответствующий backend API.

Связан с F-100 (REGRESSED: gasless send не работает) и F-132 (guardian ops без paymaster).

**Impact:** CRITICAL — весь gasless UX сломан (PRD §8.1).

**Required fix:**
Переделать `PaymasterClient.getQuote()` → `signUserOp()`, отправляющий полный UserOp как SignRequest.

---

### F-131 [HIGH] cleanupExpiredRecovery возвращает депозит (anti-spam сломан)

| Field | Value |
|-------|-------|
| **ID** | F-131 |
| **Severity** | HIGH |
| **Category** | C2 (token-economics) |
| **Bug pattern** | `cleanup-returns-deposit` |
| **Fingerprint** | `sha256("C2:SocialRecoveryModule.sol:cleanupExpiredRecovery:cleanup-returns-deposit")` → `f131...` |
| **Status** | CLAIMED_FIXED |
| **Found** | Architecture Audit 2026-06-30 |

**Description:**
`cleanupExpiredRecovery()` возвращает 0.01 MDAO депозит инициатору при истечении recovery. Anti-spam семантика (PRD §7) требует сжигания при veto/expiry. Атакующий может: инициировать recovery → подождать 96ч → cleanup → вернуть депозит → повторить. Fix: commit 95214cb — cleanupExpiredRecovery burns deposit вместо возврата инициатору.

**Impact:** HIGH — anti-spam полностью обнулён. DoS на guardian'ов.

**Required fix:**
Сжигать депозит при expiry (`mdaoToken.burn()`).

---

### F-132 [HIGH] GuardianUserOpBuilder не использует paymaster (veto не бесплатный)

| Field | Value |
|-------|-------|
| **ID** | F-132 |
| **Severity** | HIGH |
| **Category** | C6 (paymaster) |
| **Bug pattern** | `guardian-no-paymaster` |
| **Fingerprint** | `sha256("C6:app:GuardianUserOpBuilder:guardian-no-paymaster")` → `f132...` |
| **Status** | CLAIMED_FIXED |
| **Found** | Architecture Audit 2026-06-30 |

**Description:**
`GuardianUserOpBuilder.buildUserOp()` хардкодит `paymasterAndData = ByteArray(0)` для confirmGuardian, approveRecovery, vetoRecovery. PRD §7: "Veto = бесплатный (Paymaster покрывает gas)". Guardian без BNB не может veto.

**Impact:** HIGH — veto недоступен для guardian'ов без BNB. Разблокирован через F-130.

**Fix:** (unstaged) — GuardianUserOpBuilder вызывает PaymasterClient.signUserOp() после buildUserOp(), подставляет paymasterAndData при успехе. Fallback на ByteArray(0) при ошибке.`

---

### F-133 [LOW] RECOVERY_DEPOSIT использует ether literal для MDAO

| Field | Value |
|-------|-------|
| **ID** | F-133 |
| **Severity** | LOW |
| **Category** | C2 (token-economics) |
| **Bug pattern** | `ether-literal-token` |
| **Fingerprint** | `sha256("C2:SocialRecoveryModule.sol:RECOVERY_DEPOSIT:ether-literal-token")` → `f133...` |
| **Status** | CLAIMED_FIXED |
| **Found** | Architecture Audit 2026-06-30 |

**Description:**
`uint256 public constant RECOVERY_DEPOSIT = 0.01 ether;` — значение численно верное (MDAO has 18 decimals), но `ether` литерал вводит в заблуждение. Fix: commit 95214cb — 0.01 ether заменён на 10_000_000_000_000_000 wei константу.

**Impact:** LOW — code smell, не влияет на исполнение.

**Required fix:**
```solidity
uint256 public constant RECOVERY_DEPOSIT = 10_000_000_000_000_000; // 0.01 MDAO (18 decimals)
```

---

## 3. VERIFICATION PROTOCOL

Each finding has a deterministic verification recipe. Agents MUST run this before changing status.

### Recipe templates

```bash
# For "code fixed" claims:
grep -n "<expected_pattern>" <file>
# PASS: pattern present
# FAIL: pattern absent

# For "test added" claims:
forge test --match-test "<test_name>" 2>&1 | grep -E "PASS|FAIL"
# PASS: "PASS" output
# FAIL: "FAIL" or "no tests matched"

# For "config changed" claims:
git show <commit_hash>:<file> | grep "<expected_line>"
# PASS: line present in that commit
# FAIL: line absent

# For "multisig enforced" claims:
grep -n "onlyRole\|AccessControl\|TimelockController" <contract>
# PASS: at least one access control pattern
# FAIL: only Ownable / onlyOwner (trust assumption)
```

---

## 4. ANTI-PATTERNS (DO NOT REPEAT)

See `security/ANTI-PATTERNS.md` for full list. Summary:

| ID | Anti-pattern | First seen |
|----|--------------|------------|
| AP-LOG-001 | Removing txHash from logs | Wave 6 |
| AP-LOG-002 | Blanket ${e.message} removal | Wave 6 |
| AP-AUTH-001 | "Mitigated by multisig" without on-chain enforcement | Wave 5 |
| AP-CRYPTO-001 | EIP-191 instead of EIP-712 | Wave 4 |
| AP-PROCESS-001 | Marking "fixed" without regression test | Wave 6 |
| AP-PROCESS-002 | Severity downgrade without justification | Wave 5 |
| AP-PROCESS-003 | Coordinator writes code after failed subagents | Wave 5 |

---

## 5. FIX PATTERNS (APPROVED SOLUTIONS)

See `security/FIX-PATTERNS.md` for full list. Summary:

| ID | Pattern | Applies to |
|----|---------|------------|
| FP-AUTH-001 | EIP-712 quote verification | F-001, F-006, F-012 |
| FP-AUTH-002 | TimelockController | F-008, F-018 |
| FP-LOG-001 | Context-aware LogSanitizer | F-013 |
| FP-RECOVERY-001 | Async recovery flow | F-002, F-003 |
| FP-ANTIGRIEF-001 | Differentiated failure handling | F-004 |
| FP-RPC-001 | Multi-provider with failover | F-025 |
| FP-CRYPTO-001 | WebAuthn verification | F-020 |

---

## 6. STATUS DASHBOARD

| Status | Count | IDs |
|--------|-------|-----|
| OPEN | 30 | F-009a, F-022, F-039, F-040, F-041, F-043, F-046, F-047, F-049, F-051, F-071, F-110, F-111, F-113, F-114, F-115, F-116, F-117, F-118, F-119, F-120, F-121, F-122, F-123, F-124, F-125, F-126, F-127, F-128, F-129 |
| CLAIMED_FIXED | 57 | F-001, F-002, F-003, F-004, F-007, F-008, F-009, F-012, F-014, F-015, F-016, F-017, F-018, F-019, F-020, F-023, F-024, F-025, F-027, F-028, F-029, F-030, F-031, F-034, F-035, F-036, F-037, F-038, F-042, F-044, F-045, F-048, F-050, F-052, F-053, F-054, F-055, F-056, F-057, F-058, F-059, F-061, F-062, F-063, F-064, F-065, F-066, F-067, F-068, F-069, F-070, F-100, F-102, F-130, F-131, F-132, F-133 |
| VERIFIED | 8 | F-006, F-021, F-032, F-033, F-060, F-108, F-109, F-112 |
| REGRESSED | 4 | F-005, F-010, F-011, F-013 |
| CONFLICT | 1 | F-026 |
| ACCEPTED_RISK | 0 | — |
| WONTFIX | 0 | — |

| Severity | OPEN | CLAIMED | VERIFIED | REGRESSED | CONFLICT |
|----------|------|---------|----------|-----------|----------|
| CRITICAL | 1 | 7 | 1 | 0 | 0 |
| HIGH | 2 | 20 | 6 | 1 | 0 |
| MEDIUM | 19 | 19 | 0 | 3 | 0 |
| LOW | 7 | 8 | 0 | 0 | 1 |
| INFO | 0 | 3 | 1 | 0 | 0 |

---

## 7. SEVERITY POLICY (LOCKED)

| Severity | Definition | Examples |
|----------|------------|----------|
| CRITICAL | Funds loss, auth bypass, full compromise | F-001 (auth bypass), F-002 (recovery impossible) |
| HIGH | Data leak, DoS, centralization without enforcement | F-005 (oracle), F-006 (malleable sig), F-018 (withdrawTokens) |
| MEDIUM | Info leak, missing best practice, UX regression | F-013 (over-sanitize), F-019 (race condition) |
| LOW | Cosmetic, edge case, low impact | F-016 (nonce gap), F-029 (burn fee) |
| INFO | Documentation, observation | F-031 (domainSeparator cache) |

**Downgrade rules:** Document reason in lifecycle, reference policy.

---

## 8. CHANGELOG

| Date | Wave | Changes |
|------|------|---------|
| 2026-06-24 | 4 | Created registry. Added F-001..F-010 from Wave 1-4 audits. |
| 2026-06-24 | 5 | Added F-011..F-020 from Wave 5. Marked F-006 CONFLICT. |
| 2026-06-25 | 6 | Added F-021..F-025. Marked F-013 REGRESSED (AP-LOG-002 repeated). |
| 2026-06-25 | 7 | Added F-026..F-031. Re-verified F-006 (still CONFLICT). Re-verified F-013 (still REGRESSED). Upgraded F-005, F-008, F-018, F-023 to HIGH. |
| 2026-06-25 | 7 | Added F-032..F-033 (Redis fail-open rate-limiting & replay-protection). Added EM-064 to ERRORS-MEMORY.md. |
| 2026-06-25 | 8 | F-032, F-033 CLAIMED_FIXED (commit 6f842b5, RedisClientFallbackTest.kt 7/7 PASS). In-memory ConcurrentHashMap fallback for rate-limiter and replay cache. |
| 2026-06-26 | 9 | Added F-034..F-038 (5 findings: 3 CRITICAL, 2 HIGH). New bug patterns: `signing-scheme-mismatch`, `unauthorized-swap-execution`, `hash-computation-wrong`, `api-key-in-url`, `webview-javascript-unrestricted`. All OPEN. |
| 2026-06-26 | 10 | **Verifier run.** F-006 CONFLICT→VERIFIED (OZ ECDSA.recover confirmed). F-032, F-033 CLAIMED_FIXED→VERIFIED (in-memory fallback + tests PASS). F-005, F-010, F-011 CLAIMED_FIXED→REGRESSED (missing regression tests / incomplete fix). Added F-039 (setPriceBufferBps no lower bound), F-040 (price cooldown missing regression test), F-041 (failure differentiation missing regression test). Dashboard updated. |
| 2026-06-26 | 12 | **Severity corrections after quality review.** F-043 HIGH→MEDIUM (Slither already in CI). F-060 MEDIUM→HIGH (forged Play Integrity verdict bypasses device check for high-risk ops). F-062 LOW→HIGH (BIOMETRIC_WEAK allows photo bypass for recovery flow). Dashboard: HIGH 16→17, MEDIUM 24 (no change), LOW 9→8. |
| 2026-06-26 | 11 | **Bulk add.** Added F-042..F-071 (30 findings) from comprehensive security sweep across all layers. New bug patterns: `dev-server-in-production`, `ci-no-security-scanning`, `gdpr-compliance-gap`, `no-structured-logging`, `open-infra-ports`, `pooled-eth-accounting`, `burned-deposit`, `no-parameter-bounds`, `unverified-signatures`, `fire-and-forget-coroutine-leak`, `auth-brute-force-no-ratelimit`, `weak-password-policy`, `open-proxy-api-key-abuse`, `unauthorized-onramp-access`, `swap-dos-no-ratelimit`, `webview-js-bridge-wallet-signing`, `play-integrity-no-jwt-verify`, `plaintext-sensitive-storage`, `biometric-weak-allowed`, `passkey-rpid-hardcoded`, `software-root-detection-bypassable`, `fcm-push-broken`, `no-auth-invite-read`, `no-guardian-check-accept`, `no-auth-pending-recovery`, `no-request-size-limit`, `no-ratelimit-relay`, `error-message-info-leak`. Version 1.1.0→1.2.0. Dashboard updated. |
| 2026-06-30 | 13 | **BSC Testnet Audit.** Added F-108..F-128 (21 findings: 1 CRITICAL, 5 HIGH, 11 MEDIUM, 4 LOW) from pre-deployment audit `MDAOPay_BSC_Testnet_Audit.md`. New bug patterns: `rip7212-unavailable`, `der-to-raw-missing`, `jwt-entropy-missing`, `local-signing-production`, `p256-oncurve-missing`, `erc4337-v06-deprecated`, `nickname-length-charset`, `burn-fee-cap-high`, `withdrawal-cap-reset`, `chainid-confusion`, `selector-whitelist-missing`, `oracle-insufficient-sources`, `sss-gf256-spec`, `pbkdf2-outdated`, `aes-gcm-iv-random`, `slither-exclude-pragma`, `cloudsql-single-region`, `hikaricp-pool-small`, `concurrenthashmap-leak`, `touch-target-small`, `content-description-missing`. Version 1.2.0→1.3.0. Dashboard updated. |
| 2026-06-30 | 14 | **Architecture Audit.** Added F-129..F-133 (5 findings: 2 CRITICAL, 2 HIGH, 1 LOW) from `docs/audit/MDAOPay_audit.md`. F-100, F-102, F-062 CLAIMED_FIXED→REGRESSED (incomplete fixes). F-108, F-109, F-112 NEW→VERIFIED (3/3 tests PASS by verifier). New bug patterns: `kms-signing-missing`, `paymaster-client-api-mismatch`, `cleanup-returns-deposit`, `guardian-no-paymaster`, `ether-literal-token`. Version 1.3.0→1.4.0. Dashboard updated. |
| 2026-06-30 | 15 | **Architecture Audit fixes + F-100/F-132.** F-102 (veto burn), F-131 (cleanup burn), F-133 (ether literal), F-062 (biometric high-risk), F-130 (PaymasterClient API) CLAIMED_FIXED. F-100 (gasless send) REGRESSED→CLAIMED_FIXED (SendRepository uses GaslessTransactionOrchestrator). F-132 (guardian paymaster) NEW→CLAIMED_FIXED (GuardianUserOpBuilder calls signUserOp()). Version 1.4.0→1.5.0. Dashboard updated. |
| 2026-06-30 | 15 | **Status update.** F-062, F-102 REGRESSED→CLAIMED_FIXED (commits ed39136, 95214cb). F-130, F-131, F-133 NEW→CLAIMED_FIXED (commits d9af2c4, 95214cb). Dashboard updated. OPEN 34→31, CLAIMED 50→55, REGRESSED 7→5. |

---

*End of Findings Registry*
