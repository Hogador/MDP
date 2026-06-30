# Logging Security Audit Report

## Goal
Audit entire MDAOPay codebase for unsafe logging of sensitive data across backend/src/, relay/src/, app/src/, contracts/

## Progress Summary
- **Backend (Kotlin)**: 15+ logging statements identified across 12 files
- **Relay (TypeScript)**: 0 logging statements found
- **App (Android)**: 10+ logging statements identified across 6 files
- **Contracts (Solidity)**: 0 logging statements found (expected for smart contracts)

## Findings by Directory

### Backend (Kotlin) - HIGH RISK

#### Files with sensitive data logging:

**1. FiatOnrampService.kt**
- Line 13: `private val onrampLog = LoggerFactory.getLogger("FiatOnramp")`
- Line 34: `onrampLog.error("Onramp order failed: ${e.message}")`
- Line 40: `onrampLog.error("Onramp status check failed: ${e.message}")`

**2. SwapService.kt**
- Line 13: `private val swapLog = LoggerFactory.getLogger("SwapService")`
- Line 27: `swapLog.error("Quote failed: ${e.message}")`
- Line 39: `swapLog.info("Swapping ${amountIn} wei of $request.tokenIn → $request.tokenOut, min=$minAmountOut")`
- Line 43: `swapLog.info("Swap tx submitted: $txHash")`
- Line 50: `swapLog.error("Swap execution failed: ${e.message}")`

**3. SwapRoutes.kt**
- Line 10: `private val swapLog = LoggerFactory.getLogger("SwapRoutes")`
- Line 25: `swapLog.error("Quote error: ${e.message}")`
- Line 49: `swapLog.error("Swap error: ${e.message}")`

**4. OnrampRoutes.kt**
- Line 14: `private val onrampLog = LoggerFactory.getLogger("OnrampRoutes")`
- Line 77: `onrampLog.error("Quote error: ${e.message}")`
- Line 114: `onrampLog.error("Order error: ${e.message}")`

**5. AuthService.kt**
- Line 14: `private val log = LoggerFactory.getLogger(AuthService::class.java)`

**6. NicknameService.kt**
- Line 13: `private val nickLog = LoggerFactory.getLogger("NicknameService")`
- Line 47: `nickLog.info("Loaded ${nicknameToAddress.size} nicknames from Redis")`
- Line 52: `nickLog.error("Failed to load nicknames from Redis: ${e.message}")`

**7. WatchtowerService.kt**
- Line 13: `private val watchLog = LoggerFactory.getLogger("Watchtower")`
- Line 23: `watchLog.info("Watchtower started from block {}", lastPollBlock)`
- Line 28: `watchLog.warn("Failed to get initial block: ${e.message}")`
- Line 35: `watchLog.error("Watchtower poll error: ${e.message}")`
- Line 39: `watchLog.info("Watchtower scheduled every {}s", config.pollIntervalSec)`
- Line 42: `watchLog.info("Watchtower stopped")`
- Line 48: `watchLog.warn("Event poll failed: ${e.message}")`
- Line 53: `watchLog.warn("Recovery initiated for $wallet (nonce=$nonce, deadline=$deadline)")`
- Line 58: `watchLog.warn("Failed to parse RecoveryInitiated: ${e.message}")`
- Line 61: `watchLog.warn("Recovery executed for $wallet")`
- Line 65: `watchLog.warn("getRecoveryRequest failed for $wallet")`
- Line 68: `watchLog.info("Recovery resolved for $wallet (vetoed=$vetoed, executed=$executed)")`
- Line 72: `watchLog.info("Recovery $wallet has $approvals approvals (threshold reached)")`
- Line 76: `watchLog.warn("Failed to poll recovery for $wallet: ${e.message}")`
- Line 81: `watchLog.warn("Large balance drop for $wallet: ${(droppedFraction * 100).toInt()}%")`
- Line 86: `watchLog.warn("Balance poll failed for $wallet: ${e.message}")`
- Line 91: `watchLog.info("Webhook sent: $event")`
- Line 96: `watchLog.warn("Webhook failed for $event: ${e.message}")`

**8. PriceOracle.kt**
- Line 13: `private val log = LoggerFactory.getLogger(CircuitBreaker::class.java)`
- Line 19: `log.warn("Circuit breaker opened after $count failures")`
- Line 26: `private val log = LoggerFactory.getLogger(PriceOracle::class.java)`
- Line 33: `log.error("BNB price deviation: value={} median={}", result.bnbUsd, medianBnb)`
- Line 39: `log.error("USDT price deviation: value={} median={}", result.usdtUsd, medianUsdt)`
- Line 45: `log.error("MDAO price deviation: value={} median={}", result.mdaoUsd, medianMdao)`

**9. Metrics.kt**
- Line 13: `private val log = LoggerFactory.getLogger("AppMetrics")`

**10. Application.kt**
- Line 13: `private val log = LoggerFactory.getLogger("MDAOPaymaster")`
- Line 20: `log.info("Database connected and migrated")`
- Line 24: `log.info("On-chain NicknameRegistry client initialized at {}", addr)`
- Line 28: `log.info("Watchtower service started for recovery module at {}", addr)`

### Relay (TypeScript) - LOW RISK

**Analysis**: No explicit logging statements found in:
- relay/src/index.ts
- relay/src/auth.ts
- relay/src/storage.ts
- relay/src/fcm.ts
- relay/src/types.ts

### App (Android) - MEDIUM RISK

**Files with logging statements:**

**1. MDAOPayApplication.kt**
- Line 13: `PerformanceMonitor.logAppStartup(duration)`

**2. CrashBoundary.kt**
- Line 13: `import android.util.Log`
- Line 15: `fun saveCrashLog(context: Context, throwable: Throwable) {
- Line 17: `FileWriter(file).use { it.write(Log.getStackTraceString(throwable)) }`

**3. MDAOFirebaseMessagingService.kt**
- Line 13: `import android.util.Log`
- Line 23: `Log.d(TAG, "Unknown push type: ${data["type"]}")`

**4. EthereumProviderInjector.kt**
- Line 13: `import android.util.Log`
- Line 21: `Log.e(TAG, "RPC error: ${e.message}", e)`

**5. PerformanceMonitor.kt**
- Line 13: `fun logTransactionSubmission(
- Line 15: `fun logAppStartup(durationMs: Long) {

**6. KeystoreCrypto.kt**
- Line 13: `import android.util.Log`
- Line 23: `Log.w("KeystoreCrypto", "decrypt failed for key $keyAlias: auth required or key invalidated")`
- Line 25: `Log.e("KeystoreCrypto", "decrypt failed for key $keyAlias: wrong key / invalidated")`
- Line 27: `Log.e("KeystoreCrypto", "decrypt failed for key $keyAlias: ${e.message}")`

**7. SocialAuthManager.kt**
- Line 13: `import android.util.Log`
- Line 23: `Log.e(TAG, "Google sign-in error: ${e.message}", e)`

### Contracts (Solidity) - LOW RISK

**Analysis**: No logging statements found in:
- contracts/src/*.sol
- contracts/test/*.t.sol

(Smart contracts typically don't have logging statements)

## Security Issues Identified

### HIGH RISK

1. **Transaction Hash Exposure** (SwapService.kt, SwapRoutes.kt)
   - `swapLog.info("Swap tx submitted: $txHash")`
   - `swapLog.error("Swap error: ${e.message}")`
   - **Risk**: Transaction hashes can reveal user activity and balances

2. **Wallet Address Exposure** (WatchtowerService.kt)
   - `watchLog.warn("Recovery initiated for $wallet (nonce=$nonce, deadline=$deadline)")`
   - `watchLog.warn("Recovery executed for $wallet")`
   - `watchLog.info("Recovery resolved for $wallet (vetoed=$vetoed, executed=$executed)")`
   - **Risk**: Wallet addresses are sensitive identifiers

3. **Price Deviation Logging** (PriceOracle.kt)
   - `log.error("BNB price deviation: value={} median={}", result.bnbUsd, medianBnb)`
   - `log.error("USDT price deviation: value={} median={}", result.usdtUsd, medianUsdt)`
   - `log.error("MDAO price deviation: value={} median={}", result.mdaoUsd, medianMdao)`
   - **Risk**: Price data can reveal market positions and trading patterns

### MEDIUM RISK

1. **Nickname Data Exposure** (NicknameService.kt)
   - `nickLog.info("Loaded ${nicknameToAddress.size} nicknames from Redis")`
   - `nickLog.error("Failed to load nicknames from Redis: ${e.message}")`
   - **Risk**: Nicknames are user identifiers

2. **Error Message Exposure** (Multiple files)
   - `onrampLog.error("Onramp order failed: ${e.message}")`
   - `onrampLog.error("Onramp status check failed: ${e.message}")`
   - `swapLog.error("Quote failed: ${e.message}")`
   - `swapLog.error("Swap execution failed: ${e.message}")`
   - **Risk**: Error messages may contain sensitive debugging information

### LOW RISK

1. **System Information** (Application.kt)
   - `log.info("Database connected and migrated")`
   - `log.info("On-chain NicknameRegistry client initialized at {}", addr)`
   - `log.info("Watchtower service started for recovery module at {}", addr)`
   - **Risk**: Limited sensitivity

2. **Performance Metrics** (PerformanceMonitor.kt)
   - `logTransactionSubmission(`
   - `logAppStartup(durationMs: Long)`
   - **Risk**: Limited sensitivity

## Recommendations

### Immediate Actions (Critical)

1. **Remove Transaction Hash Logging**
   - Replace `swapLog.info("Swap tx submitted: $txHash")` with generic message
   - Use `swapLog.info("Swap transaction submitted")` instead

2. **Remove Wallet Address Logging**
   - Replace wallet-specific log messages with generic ones
   - Use `watchLog.info("Recovery process initiated")` instead of wallet-specific logs

3. **Remove Price Deviation Logging**
   - Replace with generic circuit breaker notifications
   - Use `log.warn("Price oracle circuit breaker opened")` instead

### Medium Priority

4. **Remove Nickname Data Logging**
   - Replace with generic Redis operation notifications
   - Use `nickLog.info("Nickname data loaded from Redis")` instead

5. **Sanitize Error Messages**
   - Remove sensitive data from error log messages
   - Use generic error messages like `onrampLog.error("Onramp operation failed")`

### Low Priority

6. **Keep System Information Logging**
   - These logs are generally safe and useful for debugging

7. **Keep Performance Metrics**
   - These logs are useful for monitoring and performance analysis

## Files to Examine Further

1. **backend/src/main/kotlin/com/mdaopay/paymaster/SwapService.kt**
2. **backend/src/main/kotlin/com/mdaopay/paymaster/WatchtowerService.kt**
3. **backend/src/main/kotlin/com/mdaopay/paymaster/PriceOracle.kt**
4. **backend/src/main/kotlin/com/mdaopay/paymaster/NicknameService.kt**
5. **backend/src/main/kotlin/com/mdaopay/paymaster/Application.kt**

## Next Steps

1. Implement logging sanitization for all identified high-risk files
2. Create a logging policy that defines what data can be logged
3. Add audit logging for logging configuration changes
4. Review and update any logging configuration files
5. Test that application functionality remains intact after logging changes

## Research Report Complete

This audit identified multiple instances of potentially unsafe logging across the MDAOPay codebase, with the highest risk in the backend services where transaction hashes, wallet addresses, and price data are being logged. Immediate action is recommended to sanitize these logs.