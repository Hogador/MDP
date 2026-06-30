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

Проведи security review логирования и PII protection.

ОБЯЗАТЕЛЬНО ПЕРЕД НАЧАЛОМ:
- Прочитай security/FINDINGS.md, security/ERRORS-MEMORY.md (особенно EM-040..EM-043)
- Для каждого finding вычисли fingerprint и проверь на дубликат

Проверяй:
- Exception messages in logs (EM-040 — blanket removal vs context-aware)
- Wallet addresses in logs (EM-042)
- Transaction hashes (EM-041 — NOT sensitive, keep!)
- Price values (Wave 6 L-03)
- User counts (Wave 6 L-04)
- IP addresses in access logs
- User-Agent strings
- JWT tokens in logs (NEVER)
- Private keys in error messages (NEVER)
- Stack traces in production (debug level only)
- Error responses to clients (EM-043 — error codes not raw messages)
- WatchtowerService webhook payloads
- RedisClient connection logs
- Database migration logs
- NicknameService error propagation

Категории ошибок (из ERRORS-MEMORY.md):
- EM-040: Blanket ${e.message} removal
- EM-041: Removing txHash from logs
- EM-042: Removing wallet addresses from watchtower
- EM-043: Error propagation sanitized

ВАЖНО: различай PII leak (нужно sanitize) и over-sanitization (нужно revert).
- PII leak: wallet address, JWT, private key, email → sanitize
- Over-sanitization: txHash, generic error type, count → revert (keep info)

Не оптимизируй код. Найди PII leaks И over-sanitization.

Для каждого риска дай:
1. ID (F-XXX)
2. File:line
3. Что логируется (или что удалено)
4. Тип проблемы: PII_LEAK или OVER_SANITIZATION
5. Почему это проблема (privacy vs observability)
6. Как исправить:
   - PII_LEAK → LogSanitizer (FIX-PATTERNS.md FP-LOG-001)
   - OVER_SANITIZATION → revert, add context
7. Verification recipe
8. Fingerprint
9. Связь с test-scenarios.md (SEC-WEB-XX, SEC-CRYPTO-XX)
10. Status: NEW / DUPLICATE / REGRESSION

Вывод на русском в файл docs/orchestration/inbox-logging.md:
# Incoming analysis
Source: - external model / local analysis / manual inspection
Findings:
- [F-XXX] <description>
  Location: <file:line>
  Type: PII_LEAK / OVER_SANITIZATION
  Current: <what's logged or removed>
  Risk: <privacy leak or observability loss>
  Fix: <sanitize or revert>
  Verification: <recipe>
  Fingerprint: <sha256>
  Test scenario: <SEC-WEB-XX>
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
