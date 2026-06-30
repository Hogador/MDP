# Roadmap Status — MDAOPay

> Этот файл отслеживает прогресс внедрения архитектурных решений и фиксов.
> Coordinator обновляет его после каждой завершенной задачи.
> Last updated: 2026-06-30 — F-102/F-062/F-130/F-131/F-133 fixed (Wave 15)

## Фаза 1: The Blockers (P0)
- [x] F-034: EIP-712 Backend↔Contract signing mismatch (CLAIMED_FIXED Wave 12)
- [x] F-001: Backend sig not verified — EIP-712 quote verification (CLAIMED_FIXED Wave 12)
- [x] F-008/F-018: Owner steals refunds — TimelockController (CLAIMED_FIXED Wave 12)
- [ ] Recovery threshold 2-of-3 (PRD vs Code)
- [ ] Deposit token: MDAO (not ETH)

## Фаза 2: Security & Safety
- [x] F-035: SwapService without auth — JWT auth added (CLAIMED_FIXED Wave 12)
- [x] F-004: Anti-griefing in postOp — differentiated failure handling (CLAIMED_FIXED)
- [ ] NG-8: No DB backup
- [ ] F-013: Логи без ошибок (LogSanitizer) — REGRESSED

## Фаза 3: Infra & Observability
- [x] NG-4 / F-065: FCM push broken — fixed (CLAIMED_FIXED Wave 12)
- [x] F-042: relay/Dockerfile dev server → wrangler deploy (CLAIMED_FIXED Wave 12)
- [ ] F-013: Логи без ошибок (LogSanitizer) — REGRESSED

## Регрессии (incomplete fixes)
- [x] F-100: Paymaster не используется в send-флоу — CLAIMED_FIXED (GaslessTransactionOrchestrator подключён)
- [x] F-102: vetoRecovery — transfer(BURN_ADDRESS) вместо burn() — CLAIMED_FIXED (commit 95214cb)
- [x] F-062: BIOMETRIC_WEAK в recovery — CLAIMED_FIXED (commit ed39136)

## BSC Testnet Audit (F-108..F-128)

### CRITICAL
- [x] F-108: P-256 Precompile (RIP-7212) — Mock fallback deployed (VERIFIED)

### HIGH
- [x] F-109: WebAuthn DER→raw signature conversion — derToRS() added (VERIFIED)
- [ ] F-110: JWT_SECRET entropy check отсутствует
- [ ] F-111: ALLOW_LOCAL_SIGNING production guard отсутствует
- [x] F-112: P-256 public key on-curve validation — added (VERIFIED)
- [ ] F-113: ERC-4337 v0.6 deprecated — миграция на v0.7

### MEDIUM
- [ ] F-114: NicknameRegistry — расхождение длины/charset
- [ ] F-115: MDAO Token — max burn fee 10% → 3%
- [ ] F-116: Daily withdrawal cap — edge case reset
- [ ] F-117: Chain ID confusion 56/97 — блокер для testnet
- [ ] F-118: SessionKeyModule — нет on-chain selector whitelist
- [ ] F-119: Price Oracle — только 2/3 источников
- [ ] F-120: SSS over GF(256) — byte-wise spec missing
- [ ] F-121: PBKDF2-HMAC-SHA256 vs Argon2id
- [ ] F-122: AES-256-GCM — random IV entropy
- [ ] F-123: Slither CI — исключение pragma-version
- [ ] F-124: Cloud SQL HA — single region

### LOW
- [ ] F-125: HikariCP pool size = 10 → 25
- [ ] F-126: ConcurrentHashMap — memory leak (rate limit)
- [ ] F-127: Touch target — MDAOButton Sm=38dp < 48dp
- [ ] F-128: Content descriptions — accessibility

## Architecture Audit (F-129..F-133)

### CRITICAL (блокируют gasless UX)
- [ ] F-129: KMS для paymaster ключа — PaymasterSigner interface + GCP KMS (deferred)
- [x] F-130: PaymasterClient API не соответствует SignRequest — CLAIMED_FIXED (commit d9af2c4)

### HIGH
- [x] F-131: cleanupExpiredRecovery возвращает депозит — CLAIMED_FIXED (commit 95214cb)
- [x] F-132: GuardianUserOpBuilder без paymaster — CLAIMED_FIXED (USES PaymasterClient.signUserOp())

### LOW
- [x] F-133: RECOVERY_DEPOSIT ether literal → wei constant (commit 95214cb)

## Dependencies
- F-100 ← F-130 ✅ (можно фиксить — PaymasterClient готов)
- F-132 ← F-130 ✅ (можно фиксить — PaymasterClient готов)
- F-100: SendRepository нужно перевести на GaslessTransactionOrchestrator
- F-132: GuardianUserOpBuilder нужно перевести на PaymasterClient.signUserOp()
- F-129 independent (KMS infra, deferred)

## Внедрение Энграммы (Crypto Agility)
- [ ] Step 1a: P0 fixes (direct)
- [ ] Step 1b: TrustProviderRegistry (Ownable, not TimelockController inheritance)
- [ ] Step 1b: Enum Status { UNREGISTERED, ACTIVE, DEPRECATED, SUNSET }
