# Smart Contract Security Audit

## Source
- Researcher: swarm-researcher (smart-contracts) + manual verification by coordinator
- Date: 2026-06-24

## Findings

### [F-01] MDAOPaymaster: `setPriceBufferBps` — no upper bound
- **Location:** MDAOPaymaster.sol:246-248
- **Risk:** MEDIUM
- **Description:** `setPriceBufferBps` has no maximum value check. Owner could set buffer to 10000+ (100%+) making `maxAllowed` in price check unreasonably large.
- **Why dangerous:** A compromised owner key could set buffer to extreme values, allowing overcharge in price validation.
- **Fix:** Add `require(newBuffer <= 2000)` (max 20% buffer).
- **Verification:** Test that setting buffer > 2000 reverts.

### [F-02] MDAOPaymaster: `setMaxTokenAmountLimit` — no upper bound
- **Location:** MDAOPaymaster.sol:219-222
- **Risk:** LOW
- **Description:** `setMaxTokenAmountLimit` has no maximum. Owner could set to `type(uint256).max`.
- **Why dangerous:** Unbounded limit removes a safety check on per-operation token charges.
- **Fix:** Add `require(newLimit <= 1_000_000 * 10**18)` or document as intentional.
- **Verification:** Test that setting extreme limit is rejected or documented.

### [F-03] MDAOPaymaster: `setMaxGasPrice` — no upper bound
- **Location:** MDAOPaymaster.sol:224-227
- **Risk:** LOW
- **Description:** `setMaxGasPrice` has no maximum. Owner could set to `type(uint256).max`.
- **Why dangerous:** Removes the gas price protection mechanism entirely.
- **Fix:** Add reasonable max (e.g., 1000 gwei) or document as intentional.
- **Verification:** Test with extreme values.

### [F-04] InsuranceFund: `collectFee` — callable by anyone
- **Location:** InsuranceFund.sol:27-33
- **Risk:** INFO
- **Description:** `collectFee` is external with no access control. Anyone can call it to register a fee.
- **Why dangerous:** Not directly exploitable — fee is only recorded in `totalFunds`, no ETH is transferred. But could be used to inflate `totalFunds` metric.
- **Fix:** Consider making `onlyOwner` or documenting as intentional (任何人都可以报告 fees).
- **Verification:** Verify this is by design.

### [F-05] MDAOPaymaster: oracle manipulation via owner
- **Location:** MDAOPaymaster.sol:233-244
- **Risk:** MEDIUM (centralization)
- **Description:** Owner controls `tokenPrice` mappings. If owner key is compromised, attacker can set arbitrary prices.
- **Mitigation already present:** `MAX_PRICE_CHANGE_BPS = 200` (2% per update), timelock on price changes.
- **Fix:** Already mitigated. Document in TDD that owner key compromise is a known risk with 2% cap as defense.
- **Verification:** Verify 2% cap is enforced in tests.

### [F-06] MDAOPaymaster: `withdrawTokens` centralization risk
- **Location:** MDAOPaymaster.sol:477-482
- **Risk:** MEDIUM (centralization)
- **Description:** Owner can withdraw MDAO/USDT from paymaster at any time.
- **Mitigation already present:** Owner is Gnosis Safe multisig (min 3-of-5) per TDD.
- **Fix:** Already mitigated by multisig requirement. No code change needed.
- **Verification:** Verify multisig requirement in deployment docs.

### [F-07] DeadManSwitch: `claimFunds` — reentrancy (benign)
- **Location:** DeadManSwitch.sol:83-95
- **Risk:** LOW
- **Description:** `claimFunds` uses low-level `call{value: balance}`. If `msg.sender` is a contract, it could re-enter. However, `cfg.claimed` is checked (line 86) before the call, preventing re-entry.
- **Why dangerous:** Not exploitable due to `claimed` check, but pattern is unconventional.
- **Fix:** Consider using `payable(msg.sender).transfer(balance)` for safety, or add `nonReentrant`.
- **Verification:** Verify `claimed` check prevents re-entry.

### [F-08] InsuranceFund: `approveClaim` — reentrancy (benign)
- **Location:** InsuranceFund.sol:49-64
- **Risk:** LOW
- **Description:** `approveClaim` uses low-level `call{value: amount}`. `totalFunds` is decremented before the call (line 57), following CEI pattern.
- **Why dangerous:** Not exploitable due to CEI pattern + `totalFunds` check on re-entry.
- **Fix:** No change needed — CEI pattern is correct.
- **Verification:** Verify CEI pattern in test.

### [F-09] MDAOToken: burn fee precision loss
- **Location:** MDAOToken.sol:43
- **Risk:** INFO
- **Description:** `value * burnFeeBps / 10000` has integer division precision loss. For very small values (< 10000/burnFeeBps), fee rounds to 0.
- **Why dangerous:** Not exploitable — standard pattern for token fee calculation.
- **Fix:** No change needed — acceptable precision for 18-decimal token.
- **Verification:** Verify with edge case amounts.

### [F-10] NicknameRegistry: `domainSeparator` is public view
- **Location:** NicknameRegistry.sol:84-96
- **Risk:** INFO
- **Description:** `domainSeparator()` is public and recomputed on every call (not cached).
- **Why dangerous:** Gas inefficiency, but not a security issue. EIP-712 domain separator includes chainId and address(this), making it unforgeable.
- **Fix:** Consider caching in a variable (updated on chain ID change), but not critical.
- **Verification:** N/A

### [F-11] RefundVault: `withdrawToken` — owner can drain vault
- **Location:** RefundVault.sol:52-55
- **Risk:** MEDIUM (centralization)
- **Description:** Owner can withdraw any token at any time via `withdrawToken`.
- **Why dangerous:** If owner key is compromised, all refund funds can be stolen.
- **Mitigation:** Owner is Gnosis Safe multisig per TDD.
- **Fix:** Already mitigated by multisig. Consider adding event emission for off-chain monitoring.
- **Verification:** Verify multisig requirement.

### [F-12] SocialRecoveryModule: `_verifyP256` — staticcall to precompile
- **Location:** SocialRecoveryModule.sol:340-346
- **Risk:** INFO
- **Description:** `_verifyP256` calls P256 precompile via `staticcall`. If precompile is not available (non-EVM chain), returns false.
- **Why dangerous:** Not exploitable — false return causes revert.
- **Fix:** No change needed.
- **Verification:** Verify precompile availability on target chains.

## Summary
- CRITICAL: 0
- HIGH: 0
- MEDIUM: 4 (F-01, F-05, F-06, F-11 — all centralization-related, mitigated by multisig)
- LOW: 3 (F-02, F-03, F-07)
- INFO: 4 (F-04, F-08, F-09, F-10, F-12)

## False Positives from Initial Research
- **F-03 (postOp reentrancy):** Already has `nonReentrant` modifier on line 382. FALSE POSITIVE.
- **F-04 (_executePermit reentrancy):** Internal function called from `onlyEntryPoint` context. FALSE POSITIVE.
- **F-05 (emergencyAdmin access):** By design — owner sets emergencyAdmin, both behind timelock. FALSE POSITIVE.
- **F-08 (two-step ownership bypass):** `acceptOwnership` correctly checks `msg.sender != pendingOwner`. FALSE POSITIVE.
- **F-10 (SocialRecoveryModule staticcall):** Checks `!success || result.length < 32`. FALSE POSITIVE.
- **F-15 (DeadManSwitch claimFunds reentrancy):** `claimed` flag set before call. FALSE POSITIVE.
- **F-19 (RefundVault claimRefund reentrancy):** Uses `safeTransfer` from OZ. FALSE POSITIVE.
