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

Проведи security review только валидации входных данных.

ОБЯЗАТЕЛЬНО ПЕРЕД НАЧАЛОМ:
- Прочитай security/FINDINGS.md, security/ERRORS-MEMORY.md
- Для каждого finding вычисли fingerprint и проверь на дубликат

Проверяй ВСЕ точки входа:
- /v1/sign — sender, nonce, gas limits, paymasterData
- /v1/nickname/register — name format, address, signature
- /v1/nickname/{name} — name validation, SQL injection
- /v1/recovery/approve — wallet, signature, guardianIdentityHash
- /v1/recovery/veto — wallet, signature
- /v1/swap/quote — token addresses, amount, slippage
- /v1/swap/execute — path, deadlines
- /v1/onramp/* — order data, KYC info
- WebView bridge (mobile) — message format, origin validation
- Relay endpoints — HMAC auth, timestamp drift, body validation
- Etherscan proxy endpoint — query params, API key exposure

Типы проверок:
- Format (regex for addresses, hex, base64)
- Length (max nickname, max calldata, max request body)
- Range (amount > 0, gas < max, nonce gap)
- Type (Long vs String, BigInteger)
- Sanitization (SQL, XSS, command injection)
- Content-Type validation
- JSON schema validation
- Rate limit per input (not just per IP)

Для каждого риска дай:
1. ID (F-XXX)
2. Endpoint / function
3. Что не валидируется
4. Почему опасно (PoC если возможно)
5. Как исправить (minimal validation)
6. Verification recipe
7. Fingerprint
8. Связь с test-scenarios.md (ILL-INPUT-XX, SEC-WEB-XX)
9. Status: NEW / DUPLICATE / REGRESSION

Вывод на русском в файл docs/orchestration/inbox-input-validation.md:
# Incoming analysis
Source: - external model / local analysis / manual inspection
Findings:
- [F-XXX] <description>
  Endpoint: <path>
  Missing validation: <what>
  PoC: <attack scenario>
  Fix: <minimal safe fix>
  Verification: <recipe>
  Fingerprint: <sha256>
  Test scenario: <ILL-INPUT-XX>
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
