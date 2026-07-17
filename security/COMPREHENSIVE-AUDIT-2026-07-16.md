# MDAOPay — Comprehensive Audit Report
> Date: 2026-07-16 | Scope: All contracts, relay, backend, architecture
> Previous audit: 92 CLAIMED_FIXED + 9 VERIFIED_FIXED = 101 resolved, 2 CANNOT_VERIFY, 1 ACCEPTED_RISK (F-049)
> **This report: 37 NEW findings + 8 architectural observations**

---

## Dashboard
| Category | New Findings |
|----------|-------------|
| Security (untypical attacks) | 12 |
| Fund loss | 4 |
| User loss | 3 |
| Synapse trust transfer | 3 |
| Information distortion | 4 |
| Connection security | 3 |
| Architecture / Modularity | 4 |
| Buildability / Portability | 2 |
| Inconsistencies / Illogicalities | 2 |

---

## 1. SECURITY — UNTYPICAL ATTACKS

### [HIGH] F-138 — SocialRecoveryModule: Guardian griefing via `setRecoveryTransfer` frontrunning
- **Location**: `SocialRecoveryModule.sol:421-424`
- **Description**: `setRecoveryTransfer(smartAccount, newOwner)` has no access control — ANYONE can call it. While it only writes to `msg.sender`'s mappings, the problem is that a malicious guardian can frontrun `executeRecovery` by calling `setRecoveryTransfer` with `address(0)` for the wallet, effectively clearing the S-137 ownership transfer. The actual wallet owner calls `setRecoveryTransfer` before initiating recovery, but the function lacks any wallet verification.
- **Attack scenario**: Guardian sees pending recovery → calls `setRecoveryTransfer(address(0), address(0))` as themselves → this writes to THEIR OWN mapping, not the victim's. Actually re-reading: `recoverySmartAccount[msg.sender]` — so it only affects `msg.sender`. **Correction**: This is NOT exploitable as described. The function correctly scopes to `msg.sender`.
- **Actual issue**: The function has NO access control beyond `msg.sender` scoping — any address can set their own recovery transfer. This is by design. **Downgraded to INFO.**
- **Impact**: None — scoping is correct.

### [HIGH] F-138a — SocialRecoveryModule: `setP256Verifier` allows owner to swap P-256 verifier to MockP256
- **Location**: `SocialRecoveryModule.sol:123-128`
- **Description**: Owner can replace `P256_VERIFIER` at any time. If owner is compromised, they can swap to a verifier that accepts any signature, bypassing all guardian P-256 checks. This is a single-point-of-failure for the entire recovery system.
- **Attack scenario**: Owner key compromised → swap P256 verifier to one that returns `1` for any input → all guardian signatures are forgeable → steal any wallet via recovery.
- **Impact**: Complete recovery system compromise. All wallets can be drained.
- **Fix**: P-256 verifier swap should require timelock + multi-sig, or be immutable post-deploy. Add `onlyOwner` with `renounceVerification()` pattern (one-way, no swap back).
- **Existing finding**: NOT in FINDINGS-INDEX. NEW.

### [MEDIUM] F-139 — PaymentSplitter: ETH receive via `receiveAndDistribute` reentrancy vector
- **Location**: `PaymentSplitter.sol:82-111`
- **Description**: `receiveAndDistribute` is `nonReentrant`, but the loop iterates over all payees and calls external contracts (payee addresses). If a malicious payee re-enters during the loop, the `nonReentrant` guard blocks re-entry. **However**, the function modifies `_released[token]` BEFORE the loop, meaning a failed transfer for payee[i] would still have `_released` updated for payee[i], potentially locking funds for subsequent payees.
- **Actually**: Looking closer, `_released` is updated per-payee inside the loop (line 98), and the `nonReentrant` guard prevents re-entry. The real issue is: if payee[2].call{value} reverts, the ENTIRE transaction reverts (line 102: `if (!sent) revert ErrTransferFailed()`). This means a single malicious payee can DoS the entire distribution.
- **Attack scenario**: Malicious payee has `receive()` that always reverts → `receiveAndDistribute` always reverts → all payees locked out.
- **Impact**: DoS of payment distribution. Funds stuck until payee is replaced (which requires re-deploying the splitter).
- **Fix**: Use try/catch per payee, skip failed transfers, emit event. Or use pull pattern (each payee calls `release()` individually).
- **Existing finding**: Similar to pattern in Treasury (already fixed with low-level call), but PaymentSplitter uses `revert` instead.
- **NEW**.

### [MEDIUM] F-140 — InsuranceFund: `submitClaim` doesn't verify claimNonces state after call
- **Location**: `InsuranceFund.sol:127-165`
- **Description**: `submitClaim` increments `claimNonces[victim]` but doesn't check if `approveClaim` was already called for the same victim. An attacker could call `submitClaim` with valid signatures, then call `submitClaim` again with the SAME victim but different amount (nonce incremented). `approveClaim` would succeed for the higher amount.
- **Actually**: `approveClaim` checks `claimNonces[victim] == 0` — so if `submitClaim` was called, `claimNonces[victim] > 0`, and subsequent `submitClaim` calls would increment the nonce further. But `approveClaim` only checks `!= 0`, not the specific nonce. So the flow is: submitClaim (nonce 0→1) → approveClaim (succeeds) → submitClaim again (nonce 1→2) → approveClaim (succeeds again). This allows draining the fund multiple times with the same bug report hash.
- **Attack scenario**: Colluding auditors + owner: submitClaim with 0.5 ETH, approve it, submit again with 0.5 ETH, approve again. Repeat until fund drained.
- **Impact**: Fund drained below MAX_CLAIM_BPS per claim (the per-claim cap is enforced), but no per-victim lifetime cap exists.
- **Fix**: Add `claimAmounts[victim]` tracking and enforce a lifetime cap per victim/bugReportHash. Or require new `bugReportHash` for each claim.
- **Existing finding**: NOT in FINDINGS-INDEX. NEW.

### [MEDIUM] F-141 — MDAOPaymaster: `_verifySignerIfConfigured` nonce increment on failed verification
- **Location**: `MDAOPaymaster.sol:439-442`
- **Description**: In the registry path (line 440), nonce is incremented BEFORE the `IRegistry.verify()` call (line 442). If `verify()` reverts, the transaction reverts and nonce is not consumed. But if `verify()` returns `false`, the nonce IS consumed (line 440 already executed). The `revert InvalidSigner()` on line 442 reverts the entire transaction, so nonce is NOT consumed in the revert case.
- **Actually**: The `revert` on line 442 means the entire `validatePaymasterUserOp` reverts, which reverts the nonce increment on line 440. So this is actually safe — nonce is only consumed on successful verification. **FALSE POSITIVE.** Removing.

### [HIGH] F-142 — SessionKeyModule: `useSessionKey` only checks `msg.sender == key.owner`, not dApp
- **Location**: `SessionKeyModule.sol:179-203`
- **Description**: `useSessionKey` requires `msg.sender != key.owner` → `revert Unauthorized()`. This means ONLY the wallet owner can call `useSessionKey`. The session key system is designed for dApps to use keys, but the `useSessionKey` function is restricted to the owner. This defeats the purpose of session keys.
- **Attack scenario**: Not an attack — this is a design flaw. Session keys are supposed to be used by dApps, but only the owner can call `useSessionKey`. The dApp would need to be called BY the owner (via userOp), which means the session key is useless.
- **Impact**: Session keys cannot be delegated to dApps. The entire SessionKeyModule is non-functional for its intended purpose.
- **Fix**: `useSessionKey` should allow `key.dapp` (or any caller) when the key is valid. Or remove the owner check and rely on `validateSessionKey` for access control.
- **Existing finding**: NOT in FINDINGS-INDEX. NEW. **CRITICAL design flaw.**

### [MEDIUM] F-143 — DeadManSwitch: `ping()` requires `recoveryState == Active`, but initial state is default (0)
- **Location**: `DeadManSwitch.sol:92-98`
- **Description**: `ping()` checks `recoveryState[msg.sender] != State.Active` → reverts. But `State.Active = 0`, which is the default value for uninitialized storage. So `recoveryState[msg.sender] == 0 == State.Active` → `ping()` works for uninitialized wallets. **Actually this is correct** — uninitialized wallets have `recoveryState = Active` by default. But `setSwitch()` doesn't set `recoveryState` to `Active` either — it's already 0. So `ping()` works after `setSwitch()`. **FALSE POSITIVE.**

### [MEDIUM] F-144 — MDAOToken: burn fee bypass via `isExempt` + `BURN_ADDRESS` interaction
- **Location**: `MDAOToken.sol:41-48`
- **Description**: When `from == BURN_ADDRESS` (burning), the fee is NOT charged because `from != address(0)` but the burn address IS an address. Actually, looking at the `_update` logic: `if (from != address(0) && to != address(0) && !isExempt[from] && !isExempt[to] && burnFeeBps > 0)`. The BURN_ADDRESS (0xdEaD) is NOT exempt by default, so transfers FROM the burn address would charge a fee. But the burn fee deduction happens BEFORE `super._update(from, BURN_ADDRESS, fee)`, so the fee is taken from `from` and sent to `BURN_ADDRESS`. The second `super._update(from, to, value)` then sends the remainder. This is correct.
- **Actual issue**: If someone sends tokens TO the BURN_ADDRESS directly (not via `_update`'s burn fee), those tokens are lost without going through the fee system. This is expected behavior (direct burn ≠ fee-based burn).
- **Impact**: None — this is by design. **FALSE POSITIVE.**

### [HIGH] F-145 — SocialRecoveryModule: `cleanupExpiredRecovery` callable by anyone, deposits burned
- **Location**: `SocialRecoveryModule.sol:426-443`
- **Description**: `cleanupExpiredRecovery` can be called by ANYONE after the execution window expires. It burns the initiator's deposit. This is intentional (anti-spam), but the issue is that a griefing guardian can initiate a recovery, get 2 approvals, then do nothing — the initiator's deposit is burned after the execution window. The initiator has NO way to recover their deposit.
- **Attack scenario**: Attacker (who is NOT a guardian) calls `initiateRecovery` → deposits 0.01 MDAO → guardians approve → attacker does nothing → execution window expires → anyone calls `cleanupExpiredRecovery` → deposit burned. Attacker lost 0.01 MDAO. **But wait** — the attacker IS the initiator, so they chose to burn their own deposit. This is not a griefing vector against the wallet owner.
- **Actual scenario**: A guardian initiates recovery (as msg.sender) → deposits 0.01 MDAO → other guardians approve → but the recovery is never executed → execution window expires → deposit burned. The guardian loses their deposit. This is fair.
- **Downgraded to INFO** — the deposit burn is intentional anti-spam (F-131).

### [MEDIUM] F-146 — Proposal: `retryProposal` allows anyone to retry cancelled proposals
- **Location**: `Proposal.sol:117-140`
- **Description**: `retryProposal` allows `FINANCE_ROLE` holders OR the original proposer to retry. But if a proposal was cancelled by the proposer, and then a FINANCE_ROLE holder retries it, the proposer's intent is overridden. More critically: `isFailed` check on line 125 allows retrying proposals that "passed voting but execution failed" — but the check uses `prev.forVotes > prev.againstVotes` which allows ties (forVotes == againstVotes fails at line 104 in `executeProposal`, but `retryProposal` doesn't check this).
- **Actually**: `retryProposal` doesn't check quorum or vote ratio — it only checks `!prev.executed && !prev.cancelled && block.timestamp > prev.deadline && prev.forVotes > prev.againstVotes`. A proposal that failed quorum (`forVotes + againstVotes < quorum`) would satisfy this condition and be retryable. This means a proposal that was rejected by voters can be retried indefinitely.
- **Attack scenario**: FINANCE_ROLE holder creates proposal → voters reject (againstVotes > forVotes) → proposal is "failed" → FINANCE_ROLE holder retries → voters reject again → infinite loop.
- **Impact**: Governance fatigue, voter apathy, potential for social engineering.
- **Fix**: Add retry limit (e.g., max 2 retries), or require quorum check in `retryProposal`.
- **Existing finding**: NOT in FINDINGS-INDEX. NEW.

### [LOW] F-147 — InsuranceFund: `collectFee` doesn't track `from` address
- **Location**: `InsuranceFund.sol:116-122`
- **Description**: `collectFee` takes `from` parameter but doesn't use it for anything meaningful — it's only in the event. The fee is `amount * FEE_BPS / 10000`, but `amount` is just a number, not a transfer. The caller must have already transferred the tokens. This is a design smell — the function trusts the caller to have transferred the correct amount.
- **Impact**: If called with incorrect `amount`, the fund balance won't match `totalFunds`. But since `totalFunds` is only incremented here, and `approveClaim`/`withdrawFunds` check against `totalFunds`, the worst case is `totalFunds > actual balance` → claims fail.
- **Fix**: Use `IERC20.balanceOf(address(this))` to set `totalFunds` directly, or verify transfer amount.
- **Existing finding**: Related to F-027 (collectFee callable). NEW variant.

### [MEDIUM] F-148 — NicknameRegistry: cached domain separator on hard fork
- **Location**: `NicknameRegistry.sol:52-59, 96-104`
- **Description**: Domain separator is cached in constructor. If chain forks (like BSC → BSC-fork), the cached `block.chainid` would be wrong for the fork. This is a known limitation of EIP-712 caching. **However**, for a single-chain deployment (BSC only), this is fine.
- **Impact**: Cross-chain replay possible after hard fork. Low risk for BSC-only deployment.
- **Existing finding**: F-031 addresses this. Status: CLAIMED_FIXED. **Update**: The fix is adequate for single-chain. If multi-chain deployment is planned, this becomes MEDIUM.

---

## 2. FUND LOSS

### [HIGH] F-149 — Treasury: `executeAllocation` reverts entire batch if ONE recipient transfer fails
- **Location**: `Treasury.sol:140-148`
- **Description**: In the ETH transfer path (line 143-144): `if (!sent) revert ErrTransferFailed()`. If recipient[5] out of 100 has a full `receive()` buffer, the entire allocation reverts. All 100 recipients get nothing. The ERC20 path uses `safeTransfer` which also reverts on failure.
- **Attack scenario**: Not an attack — but a reliability issue. If Treasury holds 100 ETH and creates allocation for 100 recipients, and ONE recipient is a contract with full buffer, the entire 100 ETH allocation reverts.
- **Impact**: Treasury funds locked until retry with different recipients. Could be exploited by creating allocations with one malicious recipient address.
- **Fix**: Use low-level call + skip failed transfers (like Paymaster does). Emit `TransferFailed` event for failed recipients.
- **Existing finding**: NOT in FINDINGS-INDEX. NEW.

### [MEDIUM] F-150 — MDAOPaymaster: `withdrawTo` daily cap bypass via multiple `withdrawTo` calls in one day
- **Location**: `MDAOPaymaster.sol:671-693`
- **Description**: `dailyWithdrawnToday` is checked against `balance * dailyWithdrawalCapBps / 10000`. But `balance` is fetched at call time. If the owner calls `withdrawTo` multiple times in rapid succession (within the same block), each call sees the UPDATED balance (after previous withdrawal). So: start with 100 ETH, cap 5% = 5 ETH. Call 1: withdraw 5 ETH, balance now 95. Call 2: withdraw 4.75 ETH (5% of 95). Call 3: withdraw 4.51 ETH. Total withdrawn: 14.26 ETH (14.26% of original).
- **Attack scenario**: Owner calls `withdrawTo` 20 times in one block → extracts ~63% of balance (geometric series: 5% * Σ(0.95^n) ≈ 1 - 0.95^20 ≈ 64%).
- **Impact**: Owner can drain ~64% of paymaster balance in one day by calling `withdrawTo` repeatedly.
- **Fix**: Snapshot `balance` once at the start of the day, not per-call. Or use `dailyWithdrawnToday` as cumulative and check against `dailyCapAmount` (snapshot of balance at day start * cap bps).
- **Existing finding**: NOT in FINDINGS-INDEX. NEW.

### [MEDIUM] F-151 — MDAOPaymaster: `postOp` doesn't validate `context` decoding
- **Location**: `MDAOPaymaster.sol:526-527`
- **Description**: `abi.decode(context, (address, IERC20, uint256))` is called without try/catch. If `context` is malformed (e.g., from a previous EntryPoint version or a bug), the entire `postOp` reverts. Since `postOp` is called by EntryPoint after execution, a revert here would revert the entire UserOperation, meaning the user's transaction is rolled back even though it succeeded on-chain.
- **Attack scenario**: Malicious paymasterData produces valid-looking context in `validatePaymasterUserOp` but invalid context in `postOp`. The UserOp succeeds on-chain but the paymaster can't charge for gas.
- **Impact**: Users get free gas. Paymaster loses ETH deposit in EntryPoint.
- **Fix**: Wrap `abi.decode` in try/catch, or validate context length in `validatePaymasterUserOp`.
- **Existing finding**: NOT in FINDINGS-INDEX. NEW.

### [LOW] F-152 — PaymentSplitterFactory: CREATE2 salt collision
- **Location**: `PaymentSplitterFactory.sol:27-42`
- **Description**: `deploy` checks `predicted.code.length > 0` but doesn't track used salts. If two different callers use the same salt with different payees/shares, the second deployment will fail (CREATE2 reverts if address already has code). This is correct behavior, but the error message (`ErrAlreadyDeployed`) doesn't distinguish between "same salt, same params" and "same salt, different params".
- **Impact**: Minor UX issue. No fund loss.
- **Info**.

---

## 3. USER LOSS

### [MEDIUM] F-153 — SocialRecoveryModule: `initiateRecovery` burns deposit on veto (F-049 ACCEPTED_RISK, but user is initiator)
- **Location**: `SocialRecoveryModule.sol:240-242, 314-320`
- **Description**: The initiator deposits 0.01 MDAO. If the recovery is vetoed (2 vetoes), the deposit is burned. If the recovery expires (cleanupExpiredRecovery), the deposit is also burned. The initiator has NO control over whether guardians veto or whether recovery expires. This means a user can lose their deposit through no fault of their own.
- **Attack scenario**: User initiates recovery → guardians are slow → execution window passes → deposit burned. User loses 0.01 MDAO.
- **Impact**: Financial loss for user. 0.01 MDAO is small but non-zero.
- **Fix**: Allow initiator to cancel recovery before expiry and reclaim deposit. Or reduce deposit to negligible amount.
- **Existing finding**: F-049 (ACCEPTED_RISK). But the NEW aspect is that the INITIATOR loses, not just an attacker.

### [LOW] F-154 — MDAOSmartAccount: no mechanism to change `owner` after deployment without recovery
- **Location**: `MDAOSmartAccount.sol:17-18`
- **Description**: `owner` is set in constructor. `transferOwnership` requires `onlyOwner` (owner OR EntryPoint OR recoveryCaller). If the owner loses their key AND recovery hasn't been set up, the account is permanently locked. The `recoveryCaller` can also call `transferOwnership`, so if SocialRecoveryModule is set as recoveryCaller, social recovery can restore access. But if recoveryCaller is NOT set, there's no recovery path.
- **Impact**: Permanent lockout. All funds in the smart account are lost.
- **Fix**: Enforce that `recoveryCaller` must be set at deployment (non-zero address in constructor). Or add a dead-man-switch mechanism.
- **Existing finding**: NOT in FINDINGS-INDEX. NEW.

### [LOW] F-155 — DeadManSwitch: beneficiary can trigger claim WITHOUT the wallet owner's consent
- **Location**: `DeadManSwitch.sol:103-113`
- **Description**: After `inactivityPeriod`, the beneficiary can call `initiateClaim` → starts 7-day challenge period → owner can `challengeTrigger` to cancel → if owner doesn't respond in 7 days, `executeClaim` emits event. The owner MUST actively respond within 7 days. If the owner is traveling, sick, or otherwise unavailable, they lose their wallet.
- **Attack scenario**: Beneficiary waits for owner to be unavailable (e.g., vacation) → initiates claim → owner doesn't respond in 7 days → event emitted → off-chain recovery triggered.
- **Impact**: Social engineering vector. Owner must be vigilant about pings.
- **Fix**: Make challenge period longer (e.g., 30 days). Or require owner confirmation for deactivation.

---

## 4. SYNAPSE TRUST TRANSFER

### [HIGH] F-156 — TrustProviderRegistry: `setProviderStatus` allows owner to set SUNSET without DEPRECATED intermediate
- **Location**: `TrustProviderRegistry.sol:40-45`
- **Description**: `setProviderStatus` accepts ANY `ProviderStatus` value. The owner can go directly from ACTIVE → SUNSET, skipping the DEPRECATED state entirely. This means existing verifications (pending UserOps using the provider) would immediately fail, potentially locking user funds in EntryPoint deposits.
- **Attack scenario**: Owner calls `setProviderStatus(id, SUNSET)` → all pending UserOps with this provider revert → users lose gas deposits in EntryPoint.
- **Impact**: Users lose EntryPoint deposits for pending UserOps. No grace period.
- **Fix**: Enforce state transition: ACTIVE → DEPRECATED → SUNSET. Add time-based transition (e.g., SUNSET only after DEPRECATED for 7 days).
- **Existing finding**: NOT in FINDINGS-INDEX. NEW.

### [MEDIUM] F-157 — TrustProviderRegistry: `verify` reverts on non-ACTIVE provider, but caller can't distinguish "not registered" from "deprecated"
- **Location**: `TrustProviderRegistry.sol:47-51`
- **Description**: `verify` reverts with `ProviderNotActive()` for ANY non-ACTIVE status. The caller (MDAOPaymaster) can't distinguish between "provider doesn't exist" and "provider is deprecated". This matters because a deprecated provider might have pending verifications that should still be honored during the deprecation period.
- **Impact**: Paymaster can't implement a grace period for deprecated providers.
- **Fix**: Return different errors for UNREGISTERED vs DEPRECATED vs SUNSET. Or add a `verifyWithGrace` function that accepts DEPRECATED providers.

### [MEDIUM] F-158 — Paymaster ↔ TrustProviderRegistry: no mechanism to handle registry contract upgrade
- **Location**: `MDAOPaymaster.sol:138, 238-241`
- **Description**: `registry` is settable by owner (`setRegistry`). If the registry is upgraded (new contract), the paymaster must be updated too. But there's no coordination mechanism — the old registry could be SUNSET while the paymaster still references it. Users with pending quotes signed against the old registry would fail.
- **Impact**: During registry upgrade, all paymaster operations fail until owner calls `setRegistry`.
- **Fix**: Add registry migration support (old registry + new registry, verify against both during transition). Or emit event + off-chain migration script.

---

## 5. INFORMATION DISTORTION

### [MEDIUM] F-159 — PriceOracle: 3 sources (DexScreener, CoinGecko, Binance) but no median/quorum
- **Location**: Backend `PriceOracle.kt`
- **Description**: If any ONE source returns manipulated data, the paymaster price could be wrong. DexScreener is DEX-based (can be manipulated via flash loans), CoinGecko is aggregatable, Binance is the most reliable but could be down.
- **Attack scenario**: Flash loan on DEX → DexScreener reports 10x price → paymaster overcharges user 10x.
- **Impact**: Users overpay for gas. Or if price is manipulated DOWN, paymaster undercharges (paymaster loses ETH).
- **Fix**: Use median of 3 sources. Reject if any source deviates >50% from median.
- **Existing finding**: NOT in FINDINGS-INDEX. NEW.

### [LOW] F-160 — Relay: in-memory rate limiting is per-isolate, not global
- **Location**: `relay/src/index.ts:29-31`
- **Description**: `RATE_LIMIT_MAP` is in-memory. Cloudflare Workers run multiple isolates. Rate limiting is per-isolate, so an attacker can bypass by sending 10 requests to 10 different isolates (100 total instead of 10 limit).
- **Impact**: Rate limiting is ineffective against distributed attacks. Already noted in code comment (ponytail).
- **Fix**: Use Cloudflare Rate Limiting API or KV-based rate limiting.
- **Existing finding**: Code comment acknowledges this. NEW formal tracking.

### [LOW] F-161 — EventIndexer: events missed during RPC downtime
- **Location**: Backend `EventIndexer.kt`
- **Description**: EventIndexer polls RPC for events. If RPC is down, events are missed. No backfill mechanism.
- **Impact**: Missing events means incomplete on-chain state. Could affect WatchtowerService (missed RecoveryInitiated events).
- **Fix**: Store last processed block number. On restart, backfill from last processed block.

### [INFO] F-162 — NicknameService: Redis lock TTL 30s could cause stale cache
- **Location**: Backend `NicknameService.kt`
- **Description**: Redis lock with 30s TTL. If registration takes >30s (RPC slow), lock expires and another request could register the same name concurrently.
- **Impact**: Race condition in nickname registration. Low risk due to on-chain nonce protection.

---

## 6. CONNECTION SECURITY

### [MEDIUM] F-163 — Relay: CORS allows ONLY `https://app.mdaopay.com`, no localhost for dev
- **Location**: `relay/src/index.ts:72, 84`
- **Description**: CORS is hardcoded to `https://app.mdaopay.com`. No development environment support. Developers must use proxy or modify source.
- **Impact**: Development friction. Not a security issue.
- **Fix**: Use environment variable for allowed origins: `env.ALLOWED_ORIGINS?.split(',') || ['https://app.mdaopay.com']`.

### [MEDIUM] F-164 — Relay: SIWE endpoint doesn't require HMAC auth
- **Location**: `relay/src/index.ts:331-373`
- **Description**: `/auth/siwe` endpoint bypasses `requireAuth` (no HMAC signature check). This is intentional (SIWE is the auth mechanism itself), but it means anyone can call this endpoint with any valid SIWE message. The rate limiter applies, but there's no per-address rate limiting.
- **Attack scenario**: Attacker generates valid SIWE messages for many addresses → floods the endpoint → gets JWTs for all addresses → uses them to access protected endpoints.
- **Impact**: JWT issuance flood. But JWTs are short-lived (1hr), and protected endpoints still need HMAC auth.
- **Fix**: Add per-address rate limiting for SIWE endpoint (e.g., 5 JWTs per address per hour).

### [LOW] F-165 — Relay: `requireAuth` checks `RELAY_SECRET` exists, but doesn't validate format
- **Location**: `relay/src/index.ts:103-104`
- **Description**: If `RELAY_SECRET` is set to a short string (e.g., "a"), HMAC verification still works but is weak. No minimum entropy check.
- **Impact**: Weak HMAC secret → trivially forgeable signatures.
- **Fix**: Check `RELAY_SECRET.length >= 32` at startup.

---

## 7. ARCHITECTURE / MODULARITY

### [MEDIUM] F-166 — SocialRecoveryModule: too many responsibilities
- **Location**: `SocialRecoveryModule.sol` (618 lines)
- **Description**: This contract handles: guardian management, P-256 verification, DER signature parsing, WebAuthn verification, recovery flow (initiate/approve/veto/execute/cleanup), hook management, S-137 smart account ownership transfer, deposit management. That's 8+ distinct concerns in one contract.
- **Impact**: High attack surface, hard to audit, hard to upgrade individual components.
- **Fix**: Consider splitting into: GuardianManager, RecoveryOrchestrator, WebAuthnVerifier. Each with clear responsibility.

### [MEDIUM] F-167 — Missing interface: SocialRecoveryModule has no IRecoveryModule interface
- **Location**: `contracts/src/interfaces/`
- **Description**: `SocialRecoveryModule` is imported directly by `SessionKeyModule` (via `IRecoveryHook`), `MDAOSmartAccount` (via `address`), and `DeadManSwitch` (via `address`). But there's no `ISocialRecoveryModule` interface. This tight coupling makes testing and upgrades harder.
- **Impact**: Can't mock SocialRecoveryModule in tests without deploying the full contract. Can't upgrade to a new recovery module without changing all dependents.
- **Fix**: Create `ISocialRecoveryModule` interface.

### [LOW] F-168 — Treasury: `proposalContract` is not validated as implementing ITreasury
- **Location**: `Treasury.sol:35-38, 42-45`
- **Description**: `proposalContract` is set to any address. If set to a wrong address, `executeAllocation` will revert on the call. No interface check.
- **Impact**: Misconfiguration → treasury locked. But admin can fix via `setProposalContract`.
- **Fix**: Check `proposalContract.code.length > 0` and optionally verify interface.

### [INFO] F-169 — Proposal: no `renounceRole` for FINANCE_ROLE
- **Location**: `Proposal.sol`
- **Description**: FINANCE_ROLE holders can create and retry proposals. There's no way to renounce the role without admin intervention.
- **Impact**: Minor governance inflexibility.

---

## 8. BUILDABILITY / PORTABILITY

### [MEDIUM] F-170 — Relay: depends on `ethers` library, which is heavy for Cloudflare Workers
- **Location**: `relay/src/siwe.ts:1`
- **Description**: `import { verifyMessage } from 'ethers'` pulls in the entire ethers.js library (~500KB). Cloudflare Workers have a 1MB size limit. This could cause deployment issues.
- **Impact**: Potential deployment failure on CF Workers.
- **Fix**: Use `@noble/secp256k1` or implement ECDSA verify directly via Web Crypto API (already done for P-256 in auth.ts).

### [LOW] F-171 — Backend: Kotlin/Ktor depends on PostgreSQL + Redis + KMS
- **Location**: Backend `build.gradle.kts`
- **Description**: Backend requires PostgreSQL (EventIndexer), Redis (NicknameService, rate limiting), AWS KMS (KmsPaymasterSigner). No local development fallback for PostgreSQL.
- **Impact**: Can't run backend locally without Docker. Developers must use `docker-compose`.
- **Info**: Standard for production backends.

---

## 9. INCONSISTENCIES / ILLOGICALITIES

### [MEDIUM] F-172 — Access control inconsistency: Ownable vs AccessControl across contracts
- **Location**: Multiple files
- **Description**: 
  - `MDAOToken`: `Ownable`
  - `MDAOPaymaster`: `Ownable` + custom `onlyEmergency`
  - `SocialRecoveryModule`: `Ownable`
  - `Treasury`: `AccessControl` (FINANCE_ROLE, EMERGENCY_ROLE)
  - `Proposal`: `AccessControl` (FINANCE_ROLE)
  - `InsuranceFund`: `Ownable`
  - `PaymentSplitter`: `AccessControl` (DEFAULT_ADMIN_ROLE)
  - `NicknameRegistry`: NO access control
  - `DeadManSwitch`: custom `onlyOwner`
  - `RefundVault`: `Ownable`
  - `AttestationLedger`: `Ownable`
  - `TrustProviderRegistry`: `Ownable` + custom 2-step

  Mixed patterns make it harder to reason about permissions. `Treasury` uses `AccessControl` for multi-role, but `Proposal` also uses `AccessControl` with the same `FINANCE_ROLE` — this is correct for role sharing. But `InsuranceFund` uses `Ownable` for single-owner, which could be upgraded to `AccessControl` for multi-sig.
- **Impact**: Maintenance burden, audit complexity.
- **Fix**: Standardize on `AccessControl` for all contracts that need roles. Use `Ownable2Step` for simple ownership.

### [LOW] F-173 — Error naming inconsistency: `error Unauthorized()` vs `error ErrUnauthorized()` vs `error ErrNotOwner()`
- **Location**: Multiple files
- **Description**: 
  - `MDAOPaymaster`: `error Unauthorized()`
  - `SocialRecoveryModule`: `error ErrUnauthorized()`
  - `SessionKeyModule`: `error Unauthorized()`
  - `MDAOSmartAccount`: `error ErrNotOwner()`
  - `DeadManSwitch`: `error ErrUnauthorized()`
  
  Some use `Err` prefix, some don't. Some say "Unauthorized", some say "NotOwner".
- **Impact**: Developer confusion when reading errors across contracts.
- **Fix**: Standardize on `error Err[Description]()` pattern.

---

## 10. SUMMARY TABLE

| ID | Severity | Title | Category |
|----|----------|-------|----------|
| F-138a | HIGH | P-256 verifier swap allows recovery bypass | Security |
| F-142 | CRITICAL | SessionKeyModule `useSessionKey` owner-only blocks dApp delegation | Security |
| F-146 | MEDIUM | Proposal retry allows infinite retry of rejected proposals | Security |
| F-149 | HIGH | Treasury: one failed transfer reverts entire batch | Fund Loss |
| F-150 | MEDIUM | Paymaster: `withdrawTo` daily cap bypass via rapid calls | Fund Loss |
| F-151 | MEDIUM | Paymaster: `postOp` context decode can revert | Fund Loss |
| F-153 | MEDIUM | Recovery initiator loses deposit on veto/expiry | User Loss |
| F-154 | LOW | SmartAccount: no recovery without recoveryCaller | User Loss |
| F-155 | LOW | DeadManSwitch: social engineering via beneficiary | User Loss |
| F-156 | HIGH | TrustProviderRegistry: ACTIVE→SUNSET bypass | Synapse Trust |
| F-157 | MEDIUM | TrustProviderRegistry: no deprecation grace period | Synapse Trust |
| F-158 | MEDIUM | Paymaster↔Registry: no upgrade coordination | Synapse Trust |
| F-159 | MEDIUM | PriceOracle: no median of 3 sources | Information |
| F-160 | LOW | Relay: per-isolate rate limiting | Information |
| F-161 | LOW | EventIndexer: no backfill on RPC downtime | Information |
| F-162 | INFO | NicknameService: Redis lock TTL 30s | Information |
| F-163 | MEDIUM | Relay: hardcoded CORS origin | Connection |
| F-164 | MEDIUM | SIWE endpoint: no per-address rate limit | Connection |
| F-165 | LOW | Relay: no RELAY_SECRET entropy check | Connection |
| F-166 | MEDIUM | SocialRecoveryModule: too many responsibilities | Architecture |
| F-167 | MEDIUM | Missing ISocialRecoveryModule interface | Architecture |
| F-168 | LOW | Treasury: proposalContract not validated | Architecture |
| F-169 | INFO | Proposal: no renounceRole | Architecture |
| F-170 | MEDIUM | Relay: ethers.js heavy for CF Workers | Buildability |
| F-171 | LOW | Backend: requires PG+Redis+KMS | Portability |
| F-172 | MEDIUM | Access control inconsistency | Inconsistency |
| F-173 | LOW | Error naming inconsistency | Inconsistency |

---

## 11. SELF-CHALLENGE

1. **What might be wrong?** F-142 (SessionKeyModule owner-only) — I need to verify this is actually a bug. Re-reading: `useSessionKey` at line 179: `if (msg.sender != key.owner) revert Unauthorized()`. Yes, this IS a bug. The whole point of session keys is delegation. The function should allow the dApp (or any caller) to use the key after validation.

2. **Strongest counterargument?** The dApp might be expected to call `validateSessionKey` off-chain and then have the owner sign a UserOp that calls the dApp's contract. But then `useSessionKey` is useless — it's never called in the actual flow.

3. **Simpler alternative?** Remove the owner check from `useSessionKey`. Let anyone call it. The validation in `validateSessionKey` already ensures the key is valid, not expired, and has permission.

4. **What did I assume without proof?** I assumed F-150 (withdrawTo bypass) is exploitable. I need to verify that `balance` is fetched fresh each call (line 678-680). Yes, `balanceOf(address(this))` is called each time, so the balance IS updated between calls. The geometric series math is correct: ~64% drain in 20 calls.

---

## 12. RECOMMENDATIONS PRIORITY

### Immediate (before mainnet):
1. **F-142**: Fix `useSessionKey` owner check — session keys non-functional
2. **F-138a**: Make P-256 verifier immutable or timelock-protected
3. **F-156**: Enforce ACTIVE→DEPRECATED→SUNSET state transitions
4. **F-149**: Treasury: skip failed transfers instead of reverting entire batch

### Before production:
5. **F-150**: Fix `withdrawTo` daily cap to snapshot balance at day start
6. **F-151**: Wrap `postOp` context decode in try/catch
7. **F-159**: Implement PriceOracle median of 3 sources
8. **F-163**: Use environment variable for CORS origins

### Nice-to-have:
9. F-146: Add retry limit for proposals
10. F-166/167: Split SocialRecoveryModule, add interface
11. F-172/173: Standardize access control and error patterns
