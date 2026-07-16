# MDAOPay — Project Memory

## What is this
DAO-governed payment protocol on EVM with mobile-first wallet and self-custody.

## Stack
- **Smart Contracts**: Solidity (Foundry) — 3739 LOC, 20 .sol files in contracts/src/
- **Backend**: Kotlin/Ktor — 7525 LOC in backend/src/main/kotlin/com/mdaopay/paymaster/
- **Relay**: TypeScript/Cloudflare Worker — 1380 LOC in relay/src/
- **Mobile**: Android app in app/
- **Infra**: Docker Compose, Prometheus/Grafana, Terraform/Cloud Run

## Contracts
| Contract | Description | Lines |
|----------|-------------|-------|
| MDAOPaymaster.sol | ERC-4337 paymaster (gas sponsorship in ERC-20) | critical |
| SocialRecoveryModule.sol | Social recovery (guardians, P-256/WebAuthn) | critical |
| InsuranceFund.sol | Insurance fund for user protection | high |
| DeadManSwitch.sol | Inactivity timer for key handover | high |
| Proposal.sol | DAO governance voting | high |
| MDAOToken.sol | ERC20 governance token | medium |
| PaymentSplitter.sol | Recurring payments | medium |
| NicknameRegistry.sol | On-chain nickname registry | low |
| AttestationLedger.sol | Attestation registry | low |
| TrustProviderRegistry.sol | Trust provider registry | medium |
| EcdsaVerifier.sol | ECDSA signature verification | critical |
| MDAOSmartAccount.sol | ERC-4337 smart account | critical |
| RefundVault.sol | Refund storage | high |
| FCLP256Verifier.sol | P-256 FCL verification | critical |
| P256Verifier.sol | P-256 verification helper | critical |

## Backend Services
- PaymasterService — core paymaster logic
- AuthService — authentication
- SwapService — token swaps
- FiatOnrampService — fiat on-ramp (MoonPay integration)
- PriceOracle — price feeds
- WatchtowerService — monitoring/alerting
- EventIndexer — blockchain event indexing
- NicknameService — nickname management

## Relay
- auth.ts — authentication endpoints
- siwe.ts — Sign-In with Ethereum
- fcm.ts — Firebase Cloud Messaging push notifications
- index.ts — main worker entry

## Tests
336 tests in 19 suites, all green:
- 25 Treasury, 28 Proposal, 22 PaymentSplitter, 45 SocialRecovery
- 76 MDAOPaymaster, 26 SessionKeyModule, 16 MDAOToken + others

## Status
Pre-testnet/pre-mainnet. Not deployed to production.
