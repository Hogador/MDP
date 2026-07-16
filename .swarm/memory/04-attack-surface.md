# MDAOPay — Attack Surface Memory

## Financial Attack Vectors (CRITICAL for payment system)
1. **Double-spend via paymaster**: Can a user submit the same UserOp twice with different paymasters?
2. **Price oracle manipulation**: Can swap rates be manipulated to drain funds?
3. **Flash loan + governance**: Borrow tokens → vote → repay → profit
4. **Front-running**: MEV bots can sandwich swap transactions
5. **Refund abuse**: Can users claim refunds they're not entitled to?
6. **Guardian collusion**: 3-of-5 guardians can steal all keys
7. **Recovery griefing**: Attackers can grief recovery process
8. **DeadManSwitch abuse**: Trigger premature key handover

## Smart Contract Attack Vectors
1. Reentrancy through ERC-20 callbacks (approve/transferFrom)
2. Signature replay across chains (EIP-712 domain separator)
3. ECDSA malleability (s-value) — F-006 still open
4. P-256 verification bypass
5. Delegatecall injection in proxy patterns
6. Storage collision in upgradeable contracts
7. Access control bypass via role escalation

## Infrastructure Attack Vectors
1. API exposed to internet (R-06, risk score 16)
2. Keys in env files (R-07, risk score 15)
3. Redis fail-open for rate limiting (F-032, F-033)
4. Docker wrangler dev in production (F-042)
5. No certificate pinning (F-024)
6. WebView JS injection (F-038)
7. MoonPay API key in URL (F-037)

## Social Engineering Vectors
1. Guardian impersonation for key theft
2. Phishing for social recovery approval
3. SIWE message manipulation
4. Push notification spoofing (FCM)

## Key Files to Audit
### contracts/src/ (ALL files)
- MDAOPaymaster.sol — paymaster logic, signature verification
- SocialRecoveryModule.sol — guardian management, recovery flow
- InsuranceFund.sol — fund management, deposits/withdrawals
- DeadManSwitch.sol — timer logic, ETH pooling
- Proposal.sol — voting, flash loan resistance
- MDAOToken.sol — mint, transfer, voting power
- EcdsaVerifier.sol — signature verification
- MDAOSmartAccount.sol — account abstraction
- RefundVault.sol — refund logic

### backend/src/
- PaymasterService.kt — core paymaster API
- AuthService.kt — auth, JWT, session management
- SwapService.kt — token swap logic
- FiatOnrampService.kt — MoonPay integration
- PriceOracle.kt — price feeds
- signing/ — KMS key management

### relay/src/
- auth.ts — authentication
- siwe.ts — SIWE implementation
- index.ts — all endpoints
- fcm.ts — push notifications
