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

Проведи security review конфигурации проекта.

ОБЯЗАТЕЛЬНО ПЕРЕД НАЧАЛОМ:
- Прочитай security/FINDINGS.md, security/ERRORS-MEMORY.md, security/ANTI-PATTERNS.md
- Для каждого finding вычисли fingerprint и проверь на дубликат

Проверяй:
- backend/AppConfig.kt — validation env vars (length, format, required, no fallback)
- relay/wrangler.toml — secrets hardcoded vs wrangler secret put
- docker-compose.yml — exposed ports, default passwords, volume mounts
- .github/workflows/ci.yml — secrets handling, OIDC, permissions
- AndroidManifest.xml — cleartext traffic, exported components, permissions
- network_security_config.xml — certificate pinning, trust anchors (если есть)
- proguard-rules.pro — obfuscation of sensitive classes
- foundry.toml — optimizer, via_ir, solc version
- gradle.properties — kotlin options, gradle config
- .env.example — placeholder formats (EM-031)
- .gitignore — what's excluded
- .githooks/pre-commit — patterns, enforcement

Категории ошибок (из ERRORS-MEMORY.md):
- EM-030: Hardcoded PostgreSQL password
- EM-060: Public RPC for mobile app
- EM-061: No certificate pinning
- EM-062: Single RPC URL without failover

Для каждого риска дай:
1. ID (F-XXX)
2. Файл конфигурации
3. Что не так
4. Почему опасно
5. Как исправить (minimal safe)
6. Verification recipe
7. Fingerprint
8. Status: NEW / DUPLICATE / REGRESSION

Вывод на русском в файл docs/orchestration/inbox-config.md:
# Incoming analysis
Source: - external model / local analysis / manual inspection
Findings:
- [F-XXX] <description>
  Location: <file:line>
  Risk: <why dangerous>
  Fix: <minimal safe fix>
  Verification: <recipe>
  Fingerprint: <sha256>
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
