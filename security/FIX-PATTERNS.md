# MDAOPay Fix Patterns — APPROVED SOLUTIONS

> Each pattern has: when to apply, code template, why, regression tests required.
> Implementer MUST use these patterns. Never invent own solutions.

---

## FP-AUTH-001: EIP-712 quote verification for paymaster

**Applies to:** F-001, F-006, F-012
**Status:** APPROVED

```solidity
import {ECDSA} from "@openzeppelin/contracts/utils/cryptography/ECDSA.sol";
import {EIP712} from "@openzeppelin/contracts/utils/cryptography/EIP712.sol";

contract MDAOPaymaster is EIP712("MDAOPay", "1") {
    using ECDSA for bytes32;
    
    address public immutable trustedSigner;
    
    bytes32 private constant QUOTE_TYPEHASH = keccak256(
        "Quote(address sender,address token,uint256 maxTokenAmount,uint256 maxGasPrice,uint256 quoteDeadline,uint256 nonce)"
    );
    
    mapping(address => uint256) public nextQuoteNonce;
    
    constructor(address _trustedSigner) EIP712("MDAOPay", "1") {
        require(_trustedSigner != address(0), "Invalid signer");
        trustedSigner = _trustedSigner;
    }
    
    function _verifyBackendSignature(
        UserOperation calldata userOp,
        address token, uint256 maxTokenAmount, uint256 maxGasPrice,
        uint256 quoteDeadline, bytes calldata signature
    ) internal returns (uint256) {
        if (block.timestamp > quoteDeadline) {
            return _packValidationData(false, _AGGREGATION_OFFSET - 1);
        }
        
        uint256 nonce = nextQuoteNonce[userOp.sender]++;
        bytes32 structHash = keccak256(abi.encode(
            QUOTE_TYPEHASH, userOp.sender, token, maxTokenAmount, maxGasPrice, quoteDeadline, nonce
        ));
        bytes32 digest = _hashTypedDataV4(structHash);
        
        address signer = digest.recover(signature);  // OZ rejects malleable
        if (signer != trustedSigner) return _packValidationData(false, 0);
        return 0;
    }
}
```

**Why this pattern:**
- All quote fields in hash → can't tamper
- EIP-712 domain → cross-chain/cross-contract replay protection
- `immutable trustedSigner` → can't change post-deploy
- Nonce consumption → replay protection
- OZ ECDSA.recover → s-malleability protection (resolves F-006)

**Regression tests required:**
- `testRejectsQuoteWithoutSignature()`
- `testRejectsTamperedMaxTokenAmount()`
- `testRejectsReplayedQuote()`
- `testFuzzRejectsMalleableSignature(uint256 s)`

---

## FP-AUTH-002: TimelockController for owner actions

**Applies to:** F-008 (RefundVault), F-018 (withdrawTokens)
**Status:** APPROVED

```solidity
import {TimelockController} from "@openzeppelin/contracts/governance/TimelockController.sol";

contract MDAOPaymaster is TimelockController {
    uint256 public constant MIN_DELAY = 48 hours;
    uint256 public constant MAX_DELAY = 14 days;
    
    // Per-day withdrawal cap (relative to non-user balance)
    uint256 public dailyWithdrawalCapBps = 500;  // 5% of sponsor balance
    uint256 public dailyWithdrawnToday;
    uint256 public dailyWithdrawalResetAt;
    
    function withdrawTokens(address token, uint256 amount, address to) 
        external onlyRole(PROPOSER_ROLE) 
    {
        // Only sponsor balance, not user refunds
        uint256 sponsorBalance = IERC20(token).balanceOf(address(this)) - refundVault.totalRefundsPending();
        require(amount <= sponsorBalance, "ExceedsSponsorBalance");
        
        // Daily cap
        if (block.timestamp > dailyWithdrawalResetAt) {
            dailyWithdrawnToday = 0;
            dailyWithdrawalResetAt = block.timestamp + 1 days;
        }
        require(dailyWithdrawnToday + amount <= sponsorBalance * dailyWithdrawalCapBps / 10000, "DailyCapExceeded");
        
        dailyWithdrawnToday += amount;
        
        // Schedule through timelock
        bytes32 id = hashOperation(token, amount, to);
        _schedule(id, MIN_DELAY);
    }
    
    function executeScheduledWithdrawal(address token, uint256 amount, address to) 
        external onlyRole(EXECUTOR_ROLE) 
    {
        bytes32 id = hashOperation(token, amount, to);
        require(isOperationReady(id), "TimelockNotElapsed");
        
        IERC20(token).safeTransfer(to, amount);
        emit WithdrawalExecuted(token, amount, to);
    }
}
```

**For RefundVault — non-withdrawable by design:**
```solidity
contract RefundVault is Ownable2Step {
    mapping(address => uint256) public pendingRefunds;
    uint256 public totalRefundsPending;
    
    function deposit(address user, uint256 amount) external onlyPaymaster {
        pendingRefunds[user] += amount;
        totalRefundsPending += amount;
        emit RefundDeposited(user, amount);
    }
    
    function claimRefund() external nonReentrant {
        uint256 amount = pendingRefunds[msg.sender];
        require(amount > 0, "NoRefund");
        
        pendingRefunds[msg.sender] = 0;
        totalRefundsPending -= amount;
        
        (bool ok,) = msg.sender.call{value: amount}("");
        require(ok, "TransferFailed");
        
        emit RefundClaimed(msg.sender, amount);
    }
    
    // NO withdrawToken function — owner cannot withdraw user refunds
}
```

---

## FP-LOG-001: Context-aware LogSanitizer

**Applies to:** F-013 (and revert of SEC-26-05, SEC-27-XX)
**Status:** APPROVED

```kotlin
// backend/src/main/kotlin/com/mdaopay/paymaster/util/LogSanitizer.kt
object LogSanitizer {
    fun sanitizeError(e: Throwable): String = when (e) {
        is SQLException -> "DB error code=${e.errorCode} state=${e.SQLState}"
        is ConnectException -> "Connection refused"
        is SocketTimeoutException -> "Timeout"
        is JsonParseException -> "Invalid JSON"
        is IllegalArgumentException -> "Invalid input"
        is ClientRequestException -> {
            val status = e.response.status.value()
            "HTTP $status"
        }
        else -> "Internal error type=${e::class.simpleName ?: "Unknown"}"
    }
    
    fun sanitizeAddress(addr: String): String = 
        if (addr.length < 10) "***" else "${addr.take(6)}...${addr.takeLast(4)}"
    
    fun sanitizeHash(hash: String): String = hash.take(8)
}

// Usage:
// log.error("Internal error id={} reason={}", errorId, LogSanitizer.sanitizeError(e))
// if (log.isDebugEnabled) log.debug("Stack id={}", errorId, e)
```

**Error codes for user-facing errors:**
```kotlin
enum class SignatureError(val code: String) {
    MALFORMED("SIG_MALFORMED"),
    WRONG_LENGTH("SIG_WRONG_LENGTH"),
    ADDRESS_MISMATCH("SIG_ADDRESS_MISMATCH"),
    EXPIRED("SIG_EXPIRED")
}

// Usage:
return Result.failure(IllegalArgumentException(
    when (e) {
        is HexDecodeException -> SignatureError.MALFORMED.code
        is IndexOutOfBoundsException -> SignatureError.WRONG_LENGTH.code
        else -> SignatureError.ADDRESS_MISMATCH.code
    }
))
```

**Regression tests required:**
- `testSQLExceptionDoesNotLeakSchema()`
- `testAddressSanitizationPreservesCorrelation()`
- `testErrorIdReturnedToUser()`

---

## FP-RECOVERY-001: Async recovery flow (per PRD §7)

**Applies to:** F-002, F-003
**Status:** APPROVED

```solidity
enum RecoveryState { None, Initiated, Ready, Executed, Vetoed }

struct RecoveryRequest {
    address initiator;
    address wallet;
    bytes newPasskeyPubKey;
    uint256 startedAt;
    uint256 approvalCount;
    uint256 readyAt;
    RecoveryState state;
}

uint256 public constant GUARDIAN_THRESHOLD = 2;
uint256 public constant TIMELOCK = 48 hours;
uint256 public constant EXECUTION_WINDOW = 48 hours;
uint256 public constant DEPOSIT_AMOUNT = 0.01 ether;

// initiate — anyone can call with deposit
function initiateRecovery(
    address wallet,
    bytes calldata newPasskeyPubKey
) external payable {
    require(msg.value >= DEPOSIT_AMOUNT, "InsufficientDeposit");
    require(recoveries[wallet].state == RecoveryState.None, "AlreadyInitiated");
    require(_hasGuardians(wallet), "NoGuardians");
    
    recoveries[wallet] = RecoveryRequest({
        initiator: msg.sender,
        wallet: wallet,
        newPasskeyPubKey: newPasskeyPubKey,
        startedAt: block.timestamp,
        approvalCount: 0,
        readyAt: 0,
        state: RecoveryState.Initiated
    });
    
    emit RecoveryInitiated(wallet, msg.sender, newPasskeyPubKey, block.timestamp);
}

// approve — guardian confirms (async)
function approveRecovery(
    address wallet,
    bytes calldata p256Signature
) external {
    RecoveryRequest storage req = recoveries[wallet];
    require(req.state == RecoveryState.Initiated, "NotInitiated");
    
    Guardian memory g = _findGuardian(wallet, msg.sender);
    require(g.addedAt > 0 && g.confirmed, "NotGuardian");
    require(!g.hasApproved, "AlreadyApproved");
    
    bytes32 messageHash = _hashApproval(wallet, req.newPasskeyPubKey, req.startedAt);
    require(_verifyP256(messageHash, p256Signature, g.pubKeyX, g.pubKeyY), "InvalidSig");
    
    g.hasApproved = true;
    req.approvalCount++;
    
    if (req.approvalCount >= GUARDIAN_THRESHOLD) {
        req.state = RecoveryState.Ready;
        req.readyAt = block.timestamp;
        emit RecoveryReady(wallet, block.timestamp + TIMELOCK);
    }
    
    emit RecoveryApproved(wallet, msg.sender, req.approvalCount);
}

// execute — after timelock, within execution window
function executeRecovery(address wallet) external {
    RecoveryRequest storage req = recoveries[wallet];
    require(req.state == RecoveryState.Ready, "NotReady");
    require(block.timestamp >= req.readyAt + TIMELOCK, "TimelockActive");
    require(block.timestamp <= req.readyAt + TIMELOCK + EXECUTION_WINDOW, "Expired");
    
    req.state = RecoveryState.Executed;
    _updateWalletOwner(wallet, req.newPasskeyPubKey);
    payable(req.initiator).transfer(DEPOSIT_AMOUNT);
    
    emit RecoveryExecuted(wallet, block.timestamp);
}

// cleanup expired
function cleanupExpiredRecovery(address wallet) external {
    RecoveryRequest storage req = recoveries[wallet];
    require(req.state == RecoveryState.Ready, "NotReady");
    require(block.timestamp > req.readyAt + TIMELOCK + EXECUTION_WINDOW, "NotExpired");
    
    req.state = RecoveryState.None;
    payable(BURN_ADDRESS).transfer(DEPOSIT_AMOUNT);
    emit RecoveryExpired(wallet);
}
```

**Why this pattern:**
- Anyone can initiate (полная потеря доступа ок)
- Async approvals (guardians не обязаны быть онлайн)
- Deposit как anti-spam
- Timelock + execution window
- Veto возможно (отдельная функция)

---

## FP-ANTIGRIEF-001: Differentiated failure handling

**Applies to:** F-004
**Status:** APPROVED

```solidity
enum FailureReason { None, InsufficientAllowance, InsufficientBalance, TokenRevert, MalformedCall }

struct PaymentStats {
    uint256 consecutiveFailures;
    uint256 lastFailureAt;
    uint256 blockedUntil;
    FailureReason lastReason;
}

mapping(address => PaymentStats) public paymentStats;

uint256 public constant BLOCK_THRESHOLD = 5;        // 5 failures (not 3)
uint256 public constant BLOCK_DURATION = 30 minutes; // 30 min (not 1h)
uint256 public constant BLOCK_COOLDOWN = 1 hours;    // reset counter after 1h clean

function postOp(...) external override {
    // ... transfer attempt ...
    
    if (!success) {
        PaymentStats storage stats = paymentStats[sender];
        
        FailureReason reason = _diagnoseFailure(sender, token, amountToCharge);
        stats.lastReason = reason;
        stats.lastFailureAt = block.timestamp;
        
        // Only block for repeated MalformedCall/TokenRevert
        if (reason == FailureReason.MalformedCall || reason == FailureReason.TokenRevert) {
            stats.consecutiveFailures++;
            if (stats.consecutiveFailures >= BLOCK_THRESHOLD) {
                stats.blockedUntil = block.timestamp + BLOCK_DURATION;
                emit PaymentBlocked(sender, reason, stats.consecutiveFailures);
            }
        }
        // InsufficientAllowance/Balance — не блокируем (user can fix)
        
        emit PaymentFailed(sender, token, amountToCharge, reason);
    } else {
        // Reset counter on success
        if (block.timestamp > paymentStats[sender].lastFailureAt + BLOCK_COOLDOWN) {
            paymentStats[sender].consecutiveFailures = 0;
        }
    }
}

function _diagnoseFailure(address sender, address token, uint256 amount) 
    internal view returns (FailureReason) 
{
    if (IERC20(token).allowance(sender, address(this)) < amount) {
        return FailureReason.InsufficientAllowance;
    }
    if (IERC20(token).balanceOf(sender) < amount) {
        return FailureReason.InsufficientBalance;
    }
    return FailureReason.TokenRevert;
}
```

---

## FP-RPC-001: Multi-provider with failover (per PRD §18)

**Applies to:** F-025
**Status:** APPROVED

```kotlin
data class AppConfig(
    val rpcProviders: List<RpcProvider>,  // ≥3
    val isTestnet: Boolean,
    val expectedChainId: Long
) {
    init {
        require(rpcProviders.size >= 3) { "Need at least 3 RPC providers" }
        require(rpcProviders.distinctBy { it.url }.size == rpcProviders.size) { "Duplicate RPC URLs" }
    }
}

data class RpcProvider(
    val name: String,
    val url: String,
    val apiKey: String?,
    val priority: Int
)

class RpcProviderManager(config: AppConfig) {
    private val providers = config.rpcProviders.map { RpcProviderState(it) }
    
    data class RpcProviderState(
        val config: RpcProvider,
        var healthScore: Int = 100,
        var consecutiveFailures: Int = 0,
        var cooldownUntil: Instant = Instant.EPOCH
    )
    
    suspend fun getBestProvider(): Web3j {
        val now = Instant.now()
        val available = providers
            .filter { it.cooldownUntil < now && it.healthScore > 0 }
            .sortedBy { it.config.priority }
        
        if (available.isEmpty()) {
            throw AllProvidersDownException("All RPC providers unavailable")
        }
        
        return buildWeb3j(available.first())
    }
    
    fun reportSuccess(provider: RpcProviderState) {
        provider.consecutiveFailures = 0
        provider.healthScore = minOf(100, provider.healthScore + 10)
    }
    
    fun reportFailure(provider: RpcProviderState) {
        provider.consecutiveFailures++
        provider.healthScore = maxOf(0, provider.healthScore - 25)
        
        if (provider.consecutiveFailures >= 3 || provider.healthScore == 0) {
            provider.cooldownUntil = Instant.now() + Duration.ofSeconds(30)
        }
    }
}
```

---

## FP-CRYPTO-001: WebAuthn verification for P-256

**Applies to:** F-020
**Status:** APPROVED

Use existing library: `webauthn-sol` from daimo-eth or `solady` `WebAuthnLib`.

```solidity
library WebAuthn {
    struct Assertion {
        bytes authenticatorData;  // 37 bytes minimum
        bytes clientDataJSON;
        uint256 r;
        uint256 s;
    }
    
    function verify(
        Assertion memory assertion,
        bytes32 challenge,
        uint256 pubKeyX,
        uint256 pubKeyY,
        bytes32 expectedRpIdHash,
        string memory expectedOrigin
    ) internal view returns (bool) {
        // 1. Parse clientDataJSON
        // 2. Verify type == "webauthn.get"
        // 3. Verify challenge matches
        // 4. Verify origin whitelisted
        // 5. Parse authenticatorData, verify rpIdHash
        // 6. Compute message = authenticatorData || SHA256(clientDataJSON)
        // 7. P-256 verify via precompile 0x100
    }
}
```

**Client-side (PasskeyManager.kt):**
```kotlin
val publicKeyCredential = PublicKeyCredential.getRequestOptions()
    .challenge(challenge.toByteArray())
    .rpId("mdaopay.xyz")
    .build()
val result = navigator.credentials.get(publicKeyCredential)
// result.authenticatorData, result.clientDataJSON, result.signature
```

**Regression tests required:**
- `testWebAuthnValidSignature()`
- `testWebAuthnWrongChallenge()`
- `testWebAuthnWrongOrigin()`
- `testWebAuthnWrongRpId()`
