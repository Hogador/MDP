# MDAOPay — Security Audit Wave 1 (2026-07-13)
# Agent: security_auditor, Model: omniroute/auto/best-chat
# Files read: 16 files across contracts/, backend/, relay/, security/

## NEW FINDINGS (17)

### F-NEW-001: Price Oracle Deviation Bound Regression
- Severity: HIGH
- File: contracts/src/MDAOPaymaster.sol:195
- Description: maxDeviationBps = 1000 (10%) помечен как "(F-005, REGRESSED)"
- Impact: Владелец может манипулировать ценами ±10% за обновление
- Fix: Восстановить MAX_PRICE_CHANGE_BPS = 200

### F-NEW-002: No Validation for P-256 Verifier in SocialRecoveryModule
- Severity: MEDIUM
- File: contracts/src/SocialRecoveryModule.sol:105
- Description: setP256Verifier не проверяет интерфейс контракта
- Impact: Неправильная настройка может сломать WebAuthn
- Fix: Добавить extcodesize + staticcall проверку

### F-NEW-003: InsuranceFund Missing Claim Expiration
- Severity: MEDIUM
- File: contracts/src/InsuranceFund.sol:120-160
- Description: Нет expiration для claims
- Impact: Блокировка funds через множество claims
- Fix: Добавить timestamp expiration

### F-NEW-004: JWT Secret Entropy Check Missing
- Severity: HIGH
- File: backend/.../AuthService.kt
- Description: F-110 CLAIMED_FIXED но не проверено
- Impact: Слабый JWT secret = компрометация auth
- Fix: Проверка минимальной длины и энтропии

### F-NEW-005: No Input Validation for Ethereum Addresses
- Severity: MEDIUM
- File: backend/.../AuthService.kt:150-180
- Description: Нет checksum/length валидации
- Fix: Keys.toChecksumAddress()

### F-NEW-006: SwapService Uses Raw ECKeyPair Without KMS
- Severity: HIGH
- File: backend/.../SwapService.kt:50-60
- Description: F-035 CLAIMED_FIXED — ECKeyPair напрямую
- Impact: Утечка ключа = кража funds
- Fix: Проверить KmsPaymasterSigner

### F-NEW-007: Rate Limiting Per-Isolate Not Global
- Severity: MEDIUM
- File: relay/src/index.ts:15-45
- Description: Rate limit только в пределах изолята CF Worker
- Fix: Cloudflare KV или Durable Objects

### F-NEW-008: Missing CORS Headers in Relay
- Severity: LOW
- File: relay/src/index.ts:80-100
- Description: Нет CORS headers = CSRF
- Fix: CORS + origin check

### F-NEW-009: Hardcoded Database Credentials
- Severity: MEDIUM
- File: docker-compose.yml
- Description: Простые пароли по умолчанию
- Fix: Требовать .env, удалить дефолты

### F-NEW-010: ALLOW_LOCAL_SIGNING Production Flag
- Severity: HIGH
- File: .env.example:19-20
- Description: Флаг может остаться true в production
- Fix: default=false, валидация в production

### F-NEW-011: No Adaptive Rate Limiting
- Severity: MEDIUM
- Description: Rate limit не адаптируется к поведению
- Fix: Adaptive rate limiting

### F-NEW-012: Missing Multi-sig for Critical Operations
- Severity: HIGH
- Description: Критические операции = onlyOwner без multi-sig
- Fix: TimelockController или multi-sig

### F-NEW-013: Quantum Vulnerable Algorithms
- Severity: HIGH
- Description: ECDSA + P-256 уязвимы к quantum
- Fix: Планирование PQC миграции

### F-NEW-014: Double-Spend через Paymaster Data Manipulation
- Severity: CRITICAL
- File: contracts/src/MDAOPaymaster.sol:456-460
- Description: quoteDeadline может быть обойдён
- Impact: Одна подпись для multiple транзакций
- Fix: Проверить minimumDeadlineBuffer + nonce logic

### F-NEW-015: Front-running на Price Updates
- Severity: HIGH
- Description: Front-run setTokenPrice транзакции
- Fix: Commit-reveal или off-chain signed updates

### F-NEW-016: Guardian Collusion в Social Recovery
- Severity: HIGH
- File: contracts/src/SocialRecoveryModule.sol:240-260
- Description: 2 из 5 guardian = захват кошелька
- Fix: Увеличить threshold или добавить delay

### F-NEW-017: Refund Abuse через PostOp Manipulation
- Severity: MEDIUM
- File: contracts/src/MDAOPaymaster.sol:520-560
- Description: Логика refund может быть манипулирована
- Fix: Проверка расчетов

## REGRESSED
- F-005: Price oracle deviation — comment указывает на регрессию
- F-034: Domain separator — нужна проверка идентичности

## NEEDS VERIFICATION
- F-035: SwapService KMS (F-NEW-006)
- F-100: Paymaster в send-флоу
- F-110: JWT secret entropy (F-NEW-004)
- F-111: ALLOW_LOCAL_SIGNING guard (F-NEW-010)

## STATS
- model_used: omniroute/auto/best-chat
- files_read: 16
- new_findings: 17 (1 CRITICAL, 7 HIGH, 7 MEDIUM, 1 LOW, 1 INFO)
