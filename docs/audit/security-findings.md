# MDAOPay — Security Findings Memory

## Summary
110+ findings (F-001 to F-137). Last updated 2026-07-07 (Wave 16).
Status: 1 OPEN, 0 REGRESSED after Wave 16.

## CRITICAL (11)
| ID | Status | Title | File |
|----|--------|-------|------|
| F-001 | CLAIMED_FIXED | Backend signature not verified by paymaster | MDAOPaymaster.sol |
| F-002 | CLAIMED_FIXED | initiateRecovery impossible with full device loss | SocialRecoveryModule.sol |
| F-034 | CLAIMED_FIXED | Backend↔Contract signing scheme incompatible | MDAOPaymaster.sol |
| F-035 | CLAIMED_FIXED | SwapService uses PAYMASTER_PRIVATE_KEY without auth | SwapRoutes.kt |
| F-036 | CLAIMED_FIXED | OnChainRegistryClient computes wrong identityHash | OnChainRegistryClient.kt |
| F-100 | CLAIMED_FIXED | Paymaster не используется в send-флоу | SendRepository.kt |
| F-108 | CLAIMED_FIXED | P-256 — FCL verifier (pure-Solidity, RIP-7212 fallback) | FCLP256Verifier.sol |
| F-129 | CLAIMED_FIXED | KMS — AWS KMS (ECC_SECG_P256K1) | KmsPaymasterSigner |
| F-130 | CLAIMED_FIXED | PaymasterClient API не соответствует SignRequest | PaymasterClient.kt |
| F-134 | CLAIMED_FIXED | GCP KMS не поддерживает secp256k1 — миграция на AWS | KmsPaymasterSigner |
| F-135 | CLAIMED_FIXED | SIWE auth endpoint в relay | auth.ts |

## VERIFIED (still open, not fixed)
| ID | Title | File |
|----|-------|------|
| F-006 | ECDSA s-value malleability in paymaster | MDAOPaymaster.sol |
| F-032 | Redis fail-open rate-limiting | RedisClient.kt |
| F-033 | Redis fail-open replay-protection | RedisClient.kt |
| F-060 | Play Integrity verdict client-side без JWT | DeviceIntegrityManager.kt |
| F-109 | WebAuthn DER→raw signature conversion | SocialRecoveryModule.sol |

## Notable HIGH findings
- F-003: No execution window for approved recovery
- F-004: No anti-griefing in postOp
- F-005: Owner oracle manipulation via setTokenPrice
- F-008: RefundVault withdrawable by owner
- F-012: No EIP-712 domain separator for recovery
- F-015: Relay /recovery endpoints without guardian verification
- F-018: Owner can steal refunds via withdrawTokens
- F-020: P-256 format doesn't match WebAuthn
- F-023: Public RPC for mobile app
- F-024: No certificate pinning
- F-037: MoonPay API key exposed in widget URL
- F-038: WebView JS enabled without domain restriction
- F-042: relay/Dockerfile — wrangler dev в production
- F-048: DeadManSwitch: pooled ETH accounting
- F-054: Auth endpoints без rate limiting
- F-059: Ethereum JS Bridge — origin whitelist + user confirmation
- F-062: BIOMETRIC_STRONG + 30s window для high-risk
- F-065: FCM push-уведомления сломаны
- F-102: vetoRecovery — transfer(BURN_ADDRESS) вместо burn()

## Priority for re-audit
ALL CLAIMED_FIXED must be VERIFIED. 90+ findings claim fix but 0% verified in this audit wave.
