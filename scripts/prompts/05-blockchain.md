# SMART LOADING (ОБЯЗАТЕЛЬНО)
# Перед началом прочитай ТОЛЬКО индексы (не полные файлы):
# 1. security/FINDINGS-INDEX.md (~80 строк) — НЕ FINDINGS.md
# 2. security/ERRORS-INDEX.md (~50 строк) — НЕ ERRORS-MEMORY.md  
# 3. docs/orchestration/CODE-INDEX.md (~150 строк) — карту кода
# Для релевантных findings — security/findings/F-XXX.md (один файл)
# Для релевантного кода — ТОЛЬКО указанные в индексе файлы
# ЗАПРЕЩЕНО читать FINDINGS.md целиком (трата токенов)
# ЗАПРЕЩЕНО сканировать все файлы (используй CODE-INDEX)
# Исключение: verifier при full verification

Проведи security review только блокчейн-логики проекта.

ОБЯЗАТЕЛЬНО ПЕРЕД НАЧАЛОМ:
- Прочитай security/FINDINGS.md, security/ERRORS-MEMORY.md (особенно EM-010..EM-014)
- Для каждого finding вычисли fingerprint и проверь на дубликат

Проверяй:
- подписи (backend → contract verification, EM-010)
- nonce (UserOp nonce, quote nonce, permit nonce)
- replay protection (cross-chain, cross-contract, EM-011)
- chain id (in message hash, in AppConfig)
- approve / allowance (front-running, infinite approval)
- deadlines (quote expiry, permit deadline, recovery execution window — EM-013)
- slippage (DEX swaps, price buffer)
- access control (onlyOwner, onlyRole, onlyEntryPoint)
- admin / owner функции (2-step ownership, timelock, multisig enforcement — EM-021)
- обработку ошибок RPC (retry, fallback, timeout — EM-062)
- риск front-running (recovery execute, sandwich attacks)
- race conditions (nickname registration — EM-063, nonce gap)
- recovery flow:
  - initiate by anyone (EM-012)
  - async approvals
  - timelock
  - execution window (EM-013)
- postOp TOCTOU (anti-griefing — EM-014)
- trustedSigner verification (EM-010 — backend sig not verified)
- ECDSA s-malleability (EM-002)
- chainId binding (EM-011)

Категории ошибок (из ERRORS-MEMORY.md):
- EM-010: Backend signature not verified by contract
- EM-011: No chainId in message hash
- EM-012: initiateRecovery requires wallet owner
- EM-013: No execution window for recovery
- EM-014: No anti-griefing in postOp

Не оптимизируй код и не переписывай архитектуру.
Ищи только уязвимости, опасные допущения и логические ошибки.

Для каждого риска дай:
1. ID (F-XXX)
2. Что найдено (file:line)
3. Почему это риск
4. Как исправить минимально безопасно (FIX-PATTERNS.md ref)
5. Как проверить исправление (recipe)
6. Fingerprint
7. Связь с test-scenarios.md (SEC-REPLAY-XX, SEC-KEYS-XX)
8. Status: NEW / DUPLICATE / REGRESSION

Вывод на русском в файл docs/orchestration/inbox-blockchain.md:
# Incoming analysis
Source: - external model / local analysis / manual inspection
Findings:
- [F-XXX] <description>
  Location: <file:line>
  Risk: <why dangerous>
  Fix: <minimal safe fix>
  Verification: <recipe>
  Fingerprint: <sha256>
  Test scenario: <SEC-REPLAY-XX or SEC-KEYS-XX>
  Status: <NEW/DUPLICATE/REGRESSION>
Possible impact:
- ...
Suspected files:
- ...
EOF

---
⚠️ КРИТИЧЕСКОЕ ПРАВИЛО:
ОБЯЗАТЕЛЬНО СОХРАНИ ОТЧЁТ В ФАЙЛ перед завершением работы.
Имя файла: docs/orchestration/inbox-<area>.md (например, inbox-contracts.md, inbox-backend.md, inbox-mobile.md).
Используй Write tool. Если отчёт не сохранён в файл — работа считается проваленной.
