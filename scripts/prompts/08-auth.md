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

Проведи security review аутентификации и авторизации.

ОБЯЗАТЕЛЬНО ПЕРЕД НАЧАЛОМ:
- Прочитай security/FINDINGS.md, security/ERRORS-MEMORY.md
- Для каждого finding вычисли fingerprint и проверь на дубликат

Проверяй все точки входа:
- /sign — кто может вызвать? JWT? API key? Anyone?
- /recovery/approve — guardian verification (EM-010, relay auth — Wave 4 F-1)
- /recovery/veto — same as approve
- WebView bridge — origin validation, message auth
- Relay endpoints — HMAC auth (Wave 4 F-1: RELAY_SECRET bypass)
- Admin endpoints — multisig, timelock (EM-021)
- Mobile biometric — Face ID bypass, fallback to PIN
- Session keys — capability mapping (PRD §8.3)
- JWT lifecycle:
  - Issue (login)
  - Refresh (token rotation)
  - Revoke (logout, compromise)
  - Expiry (access token vs refresh token)

Категории ошибок (из ERRORS-MEMORY.md):
- EM-010: Backend signature not verified by contract
- EM-021: "Mitigated by multisig" without on-chain enforcement

Не оптимизируй код. Найди auth bypass vectors.

Для каждого риска дай:
1. ID (F-XXX)
2. Точка входа
3. Кто может вызвать (auth required?)
4. Что не так
5. Как исправить
6. Verification recipe (PoC: curl without auth)
7. Fingerprint
8. Связь с test-scenarios.md (SEC-AUTH-XX, new family)
9. Status: NEW / DUPLICATE / REGRESSION

Вывод на русском в файл docs/orchestration/inbox-auth.md:
# Incoming analysis
Source: - external model / local analysis / manual inspection
Findings:
- [F-XXX] <description>
  Endpoint: <path>
  Auth required: <yes/no/what>
  Vulnerability: <what's wrong>
  PoC: <curl command or attack scenario>
  Fix: <minimal safe fix>
  Verification: <recipe>
  Fingerprint: <sha256>
  Test scenario: <SEC-AUTH-XX>
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
