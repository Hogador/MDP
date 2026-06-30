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

Проведи security review Android-приложения (app/).

ОБЯЗАТЕЛЬНО ПЕРЕД НАЧАЛОМ:
- Прочитай security/FINDINGS.md, security/ERRORS-MEMORY.md (особенно EM-060, EM-061)
- Для каждого finding вычисли fingerprint и проверь на дубликат

Проверяй:
- Android Keystore (key generation, biometric prompt, auth-required keys)
- Biometrics (BiometricPrompt vs deprecated FingerprintManager, fallback)
- Screen-off lock (PRD §2.3.7 — sensitive ops only when screen on)
- Root detection (SafetyNet/Play Integrity API)
- App integrity (tampering, repackaging, debug builds)
- WebView security (JavaScript interface, file access, content access)
- Deep links (intent filters, parameter validation, phishing)
- Push notifications (FCM token handling, notification content)
- Local storage (EncryptedSharedPreferences, cleartext files)
- Room database (SQLCipher encryption?)
- Network security config (cert pinning — EM-061, cleartext traffic)
- ProGuard/R8 (sensitive class obfuscation)
- Backup (android:allowBackup, fullBackupContent)
- Exported components (activities, services, receivers, providers)
- Public RPC endpoints (EM-060)

Категории ошибок (из ERRORS-MEMORY.md):
- EM-060: Public RPC for mobile app
- EM-061: No certificate pinning

Не оптимизируй код. Найди mobile-specific vulnerabilities.

Для каждого риска дай:
1. ID (F-XXX)
2. Файл / component
3. Что не так
4. Почему опасно
5. Как исправить
6. Verification recipe
7. Fingerprint
8. Связь с test-scenarios.md (FE-MOBILE-XX, FE-DEVICE-XX)
9. Status: NEW / DUPLICATE / REGRESSION

Вывод на русском в файл docs/orchestration/inbox-mobile.md:
# Incoming analysis
Source: - external model / local analysis / manual inspection
Findings:
- [F-XXX] <description>
  Location: <file:line or component>
  Risk: <why dangerous>
  Fix: <minimal safe fix>
  Verification: <recipe>
  Fingerprint: <sha256>
  Test scenario: <FE-MOBILE-XX or FE-DEVICE-XX>
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
