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

Проведи security review только криптографической части проекта.

ОБЯЗАТЕЛЬНО ПЕРЕД НАЧАЛОМ:
- Прочитай security/FINDINGS.md, security/ERRORS-MEMORY.md (особенно EM-001..EM-005)
- Для каждого finding вычисли fingerprint и проверь на дубликат

Проверяй:
- nonce / IV / salt (уникальность, длина, CSPRNG)
- генерацию случайных чисел (SecureRandom vs java.util.Random vs Math.random)
- constant-time compare (MessageDigest.isEqual, manual implementations)
- использование проверенных библиотек (OZ ECDSA, BouncyCastle, java.crypto)
- отсутствие самописной криптографии
- корректность хеширования (SHA-256 vs keccak256, PBKDF2 vs Argon2)
- подписи:
  - ECDSA s-value (EM-002)
  - P-256 WebAuthn vs raw (EM-004)
  - EIP-191 vs EIP-712 (EM-003)
- шифрование (AES-256-GCM, IV reuse, key derivation)
- валидация ключей (length, format, rotation)
- HMAC (per-call Mac.getInstance vs shared, thread safety — C-6 fix)
- JWT (secret strength, expiry, refresh token rotation)
- NicknameService double hashing (EM-001)
- CF Workers crypto.subtle.timingSafeEqual (EM-005)
- Paymaster signing hash consistency (C-9)

Категории ошибок (из ERRORS-MEMORY.md):
- EM-001: Double hashing with EIP-191
- EM-002: Raw ecrecover without s-value check
- EM-003: EIP-191 instead of EIP-712 for structured data
- EM-004: P-256 with Ethereum prefix instead of WebAuthn
- EM-005: crypto.subtle.timingSafeEqual doesn't exist in CF Workers

Не оптимизируй код и не сокращай его.
Твоя задача — найти ошибки, слабые места и опасные допущения.

Для каждого найденного риска дай:
1. ID (F-XXX)
2. Что не так (с file:line)
3. Почему это опасно
4. Как исправить (ссылка на FIX-PATTERNS.md если есть)
5. Как проверить, что исправление работает (recipe)
6. Fingerprint
7. Связь с test-scenarios.md (SEC-CRYPTO-XX)
8. Status: NEW / DUPLICATE / REGRESSION

Вывод на русском в файл docs/orchestration/inbox-crypto.md:
# Incoming analysis
Source: - external model / local analysis / manual inspection
Findings:
- [F-XXX] <description>
  Location: <file:line>
  Risk: <why dangerous>
  Fix: <minimal safe fix>
  Verification: <recipe>
  Fingerprint: <sha256>
  Test scenario: <SEC-CRYPTO-XX>
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
