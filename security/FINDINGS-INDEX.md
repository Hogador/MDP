# Findings Index
> Read this first. For full details: security/findings/F-XXX.md
> Last updated: 2026-07-01 (Wave 15 — all OPEN cleared. 0 OPEN, 0 REGRESSED, 90 CLAIMED_FIXED, 9 VERIFIED)

## Dashboard
| Status | Count |
|--------|-------|
| OPEN | 0 |
| CLAIMED_FIXED | 90 |
| VERIFIED | 9 |
| REGRESSED | 0 |
| CONFLICT | 1 |
| ACCEPTED_RISK | 0 |
| WONTFIX | 0 |

## Findings by Severity

### CRITICAL (9)
| ID | Status | Title | File |
|----|--------|-------|------|
| F-001 | CLAIMED_FIXED | Backend signature not verified by paymaster contract | MDAOPaymaster.sol |
| F-002 | CLAIMED_FIXED | initiateRecovery impossible with full device loss | SocialRecoveryModule.sol |
| F-034 | CLAIMED_FIXED | Backend↔Contract signing scheme incompatible | contracts/src/MDAOPaymaster.sol |
| F-035 | CLAIMED_FIXED | SwapService uses PAYMASTER_PRIVATE_KEY without authentication | backend/.../SwapRoutes.kt |
| F-036 | CLAIMED_FIXED | OnChainRegistryClient computes wrong identityHash | backend/.../OnChainRegistryClient.kt |
| F-100 | CLAIMED_FIXED | Paymaster не используется в send-флоу | SendRepository.kt |
| F-108 | VERIFIED | P-256 Precompile (RIP-7212) на BSC Testnet | SocialRecoveryModule.sol |
| F-129 | CLAIMED_FIXED | KMS для paymaster ключа не реализован | backend/.../PaymasterSigner |
| F-130 | CLAIMED_FIXED | PaymasterClient API не соответствует SignRequest | app/.../PaymasterClient.kt |

### HIGH (30)
| ID | Status | Title | File |
|----|--------|-------|------|
| F-003 | CLAIMED_FIXED | No execution window for approved recovery | SocialRecoveryModule.sol |
| F-004 | CLAIMED_FIXED | No anti-griefing in postOp | MDAOPaymaster.sol |
| F-005 | CLAIMED_FIXED | Owner oracle manipulation via setTokenPrice | MDAOPaymaster.sol |
| F-006 | VERIFIED | ECDSA s-value malleability in paymaster | MDAOPaymaster.sol |
| F-008 | CLAIMED_FIXED | RefundVault withdrawable by owner | RefundVault.sol |
| F-012 | CLAIMED_FIXED | No EIP-712 domain separator for recovery | SocialRecoveryModule.sol |
| F-015 | CLAIMED_FIXED | Relay /recovery endpoints without guardian verification | relay/src/index.ts |
| F-018 | CLAIMED_FIXED | Owner can steal refunds via withdrawTokens | MDAOPaymaster.sol |
| F-020 | CLAIMED_FIXED | P-256 format doesn't match WebAuthn | SocialRecoveryModule.sol |
| F-023 | CLAIMED_FIXED | Public RPC for mobile app | RpcProviderManager.kt |
| F-024 | CLAIMED_FIXED | No certificate pinning | app |
| F-032 | VERIFIED | Redis fail-open rate-limiting | RedisClient.kt |
| F-033 | VERIFIED | Redis fail-open replay-protection | RedisClient.kt |
| F-037 | CLAIMED_FIXED | MoonPay API key exposed in widget URL | backend/.../FiatOnrampService.kt |
| F-038 | CLAIMED_FIXED | WebView JS enabled without domain restriction | app/.../MDAOWebView.kt |
| F-042 | CLAIMED_FIXED | relay/Dockerfile — wrangler dev в production | relay/Dockerfile |
| F-048 | CLAIMED_FIXED | DeadManSwitch: pooled ETH accounting | DeadManSwitch.sol |
| F-054 | CLAIMED_FIXED | Auth endpoints без rate limiting | Application.kt |
| F-059 | CLAIMED_FIXED | Ethereum JS Bridge exposes wallet signing | EthereumProviderInjector.kt |
| F-060 | VERIFIED | Play Integrity verdict client-side без JWT | DeviceIntegrityManager.kt |
| F-062 | CLAIMED_FIXED | BIOMETRIC_WEAK в recovery (authenticateHighRisk не вызывается) | RecoveryScreen.kt |
| F-065 | CLAIMED_FIXED | FCM push-уведомления сломаны | fcm.ts |
| F-102 | CLAIMED_FIXED | vetoRecovery — transfer(BURN_ADDRESS) вместо burn() | SocialRecoveryModule.sol |
| F-109 | VERIFIED | WebAuthn DER→raw signature conversion | SocialRecoveryModule.sol |
| F-110 | CLAIMED_FIXED | JWT_SECRET entropy check отсутствует | backend |
| F-111 | CLAIMED_FIXED | ALLOW_LOCAL_SIGNING production guard | backend |
| F-112 | VERIFIED | P-256 public key on-curve validation | SocialRecoveryModule.sol |
| F-113 | NEW | ERC-4337 v0.6 deprecated | contracts |
| F-131 | CLAIMED_FIXED | cleanupExpiredRecovery сжигает депозит (anti-spam) | SocialRecoveryModule.sol |
| F-132 | CLAIMED_FIXED | GuardianUserOpBuilder без paymaster | app/.../GuardianUserOpBuilder.kt |

### MEDIUM (41)
| ID | Status | Title | File |
|----|--------|-------|------|
| F-007 | CLAIMED_FIXED | MockP256 verify() without chain guard | MockP256.sol |
| F-009 | CLAIMED_FIXED | setPriceBufferBps no upper bound | MDAOPaymaster.sol |
| F-009a | CLAIMED_FIXED | setPriceBufferBps no lower bound | MDAOPaymaster.sol |
| F-010 | CLAIMED_FIXED | setMaxTokenAmountLimit decimal confusion | MDAOPaymaster.sol |
| F-011 | CLAIMED_FIXED | setMaxGasPrice chain-specific cap | MDAOPaymaster.sol |
| F-013 | CLAIMED_FIXED | Over-sanitization of error logs | backend/*.kt, LogSanitizer |
| F-014 | CLAIMED_FIXED | Relay auth bypass when RELAY_SECRET unset | relay/src/index.ts |
| F-019 | CLAIMED_FIXED | Nickname race condition | NicknameService.kt |
| F-022 | VERIFIED (STALE) | Paymaster signing hash inconsistency | PaymasterService.kt |
| F-025 | CLAIMED_FIXED | Single RPC URL without failover | AppConfig.kt |
| F-027 | CLAIMED_FIXED | InsuranceFund collectFee callable | InsuranceFund.sol |
| F-039 | CLAIMED_FIXED | setPriceBufferBps без нижней границы | MDAOPaymaster.sol |
| F-040 | CLAIMED_FIXED | Price cooldown regression test | contracts/test |
| F-041 | CLAIMED_FIXED | Failure differentiation regression test | contracts/test |
| F-043 | CLAIMED_FIXED | CI security scanning | .github/workflows/ci.yml |
| F-044 | CLAIMED_FIXED | GDPR — PII retention policy | V1__initial_schema.sql |
| F-045 | CLAIMED_FIXED | logback.xml без структурированного лога | logback.xml |
| F-046 | CLAIMED_FIXED | docker-compose порты изолированы | docker-compose.yml |
| F-049 | CLAIMED_FIXED | Депозит сжигается при cleanupExpiredRecovery | SocialRecoveryModule.sol |
| F-050 | CLAIMED_FIXED | AttestationLedger: attest() без ACL | AttestationLedger.sol |
| F-053 | CLAIMED_FIXED | WatchtowerService coroutine leak | WatchtowerService.kt |
| F-055 | CLAIMED_FIXED | Weak password policy | AuthService.kt |
| F-056 | CLAIMED_FIXED | Etherscan proxy open API key | Application.kt |
| F-057 | CLAIMED_FIXED | Onramp routes без аутентификации | OnrampRoutes.kt |
| F-061 | CLAIMED_FIXED | Recovery evalInput в plaintext | RecoveryShareManager.kt |
| F-066 | CLAIMED_FIXED | GET /guardian/invite без auth | relay/src/index.ts |
| F-067 | CLAIMED_FIXED | Принятие инвайта без guardian check | relay/src/index.ts |
| F-068 | CLAIMED_FIXED | GET /recovery/pending без auth | relay/src/index.ts |
| F-069 | CLAIMED_FIXED | Нет лимита размера тела запроса | relay/src/index.ts |
| F-070 | CLAIMED_FIXED | Нет rate limiting на relay | relay |
| F-114 | CLAIMED_FIXED | NicknameRegistry длина/charset синхронизированы | NicknameRegistry.sol / backend |
| F-115 | CLAIMED_FIXED | MDAO Token — max burn fee 3% | MDAOToken.sol |
| F-116 | CLAIMED_FIXED | Daily withdrawal cap — edge case | MDAOPaymaster.sol |
| F-117 | CLAIMED_FIXED | Chain ID confusion 56/97 | config |
| F-118 | CLAIMED_FIXED | SessionKeyModule — permission whitelist | SessionKeyModule.sol |
| F-119 | CLAIMED_FIXED | Price Oracle — 3 источников | backend |
| F-120 | CLAIMED_FIXED | SSS over GF(256) — byte-wise spec | mobile |
| F-121 | CLAIMED_FIXED | PBKDF2 vs Argon2id (Phase 1) | mobile |
| F-122 | CLAIMED_FIXED | AES-GCM IV random entropy (Phase 1) | mobile |
| F-123 | CLAIMED_FIXED | Slither CI — pragma-version excluded | ci |
| F-124 | CLAIMED_FIXED | Cloud SQL HA — single region | infra |

### LOW (16)
| ID | Status | Title | File |
|----|--------|-------|------|
| F-016 | CLAIMED_FIXED | MAX_NONCE_GAP = 100 | PaymasterService.kt |
| F-017 | CLAIMED_FIXED | Fallback prices 100x off | PaymasterService.kt |
| F-026 | CONFLICT | Hardcoded PostgreSQL password | docker-compose.yml |
| F-028 | CLAIMED_FIXED | DeadManSwitch reentrancy unverified | DeadManSwitch.sol |
| F-029 | CLAIMED_FIXED | MDAOToken burn fee precision | MDAOToken.sol |
| F-047 | CLAIMED_FIXED | backend/docker-compose зависит от postgres+redis | backend/docker-compose.yml |
| F-051 | CLAIMED_FIXED | setCooldownPeriod без границ | MDAOPaymaster.sol |
| F-052 | CLAIMED_FIXED | InsuranceFund auditorSignatures | InsuranceFund.sol |
| F-058 | CLAIMED_FIXED | SwapRoutes no rate limiting | SwapRoutes.kt |
| F-063 | CLAIMED_FIXED | PasskeyManager RP ID hardcoded | PasskeyManager.kt |
| F-071 | CLAIMED_FIXED | Ошибка конфигурации раскрывает auth | relay/src/index.ts |
| F-125 | CLAIMED_FIXED | HikariCP pool size = 10 | backend/database.kt |
| F-126 | CLAIMED_FIXED | ConcurrentHashMap memory leak | backend/RateLimiter |
| F-127 | CLAIMED_FIXED | Touch target 38dp < 48dp | app/MDAOButton |
| F-128 | CLAIMED_FIXED | Content descriptions missing | app/components |
| F-133 | CLAIMED_FIXED | RECOVERY_DEPOSIT — ether literal | SocialRecoveryModule.sol |

### INFO (4)
| ID | Status | Title | File |
|----|--------|-------|------|
| F-021 | VERIFIED | NicknameService double hashing | NicknameService.kt |
| F-030 | CLAIMED_FIXED | MockP256 chain guard missing | MockP256.sol |
| F-031 | CLAIMED_FIXED | NicknameRegistry domainSeparator | NicknameRegistry.sol |
| F-064 | CLAIMED_FIXED | Software root detection bypassable | DeviceIntegrityManager.kt |

## How to use
- Need full detail? Read security/findings/F-XXX.md
- Need verification recipe? It's in the detail file
- Only verifier can update status
