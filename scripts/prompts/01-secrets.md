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

Проведи security review только на наличие секретов, токенов, ключей, паролей и чувствительных данных.

ОБЯЗАТЕЛЬНО ПЕРЕД НАЧАЛОМ:
- Прочитай security/FINDINGS.md — registry известных findings
- Прочитай security/ERRORS-MEMORY.md — паттерны ошибок (особенно EM-030..EM-032)
- Прочитай security/ANTI-PATTERNS.md — запрещённые подходы
- Для каждого найденного риска вычисли fingerprint:
  fp = sha256(category + ":" + file + ":" + function + ":" + bug_pattern)
- Проверь, есть ли уже этот fingerprint в FINDINGS.md
- Если есть и status=VERIFIED → НЕ переоткрывай, это дубликат
- Если есть и status=CLAIMED_FIXED → проверь recipe, если FAIL → REGRESSION
- Если нет → новый finding, добавь в отчёт

Проверяй:
- .env, .env.local, .env.production, .env.testnet, .env.bsc, .env.sepolia
- конфиги JSON, YAML, TOML, ini
- строки подключения (PostgreSQL, Redis, RPC)
- API keys, private keys, tokens, cookies, session secrets
- логирование секретов в коде (console.log, log.info с секретами)
- случайные утечки в комментариях, тестах, README, примерах и CI/CD
- git history: git log --all -S "PRIVATE_KEY" -- '*.env*'
- GitHub Actions secrets (названия, не значения)
- Cloudflare Workers wrangler.toml — hardcoded vs wrangler secret put
- Android keystore files, signing configs
- backend/.env.example — placeholders with valid hex format (EM-031)
- docker-compose.yml — hardcoded passwords (EM-030)

Категории ошибок (из ERRORS-MEMORY.md):
- EM-030: Hardcoded PostgreSQL password
- EM-031: Placeholder private keys with valid hex format
- EM-032: No git history scan

Не делай рефакторинг и не оптимизируй код.
Твоя задача — найти возможные утечки и опасные места.

Для каждого найденного пункта дай:
1. ID (F-XXX, продолжая нумерацию из FINDINGS.md)
2. Где найдено (file:line)
3. Что это за секрет или чувствительные данные
4. Почему это опасно
5. Как исправить минимально безопасно (ссылка на FIX-PATTERNS.md если есть)
6. Как проверить, что утечка устранена (verification recipe)
7. Fingerprint (sha256)
8. Связь с test-scenarios.md (SEC-SECRETS-XX)
9. Status: NEW / DUPLICATE / REGRESSION

Если увидишь риск, но не уверен на 100%, пометь как "подозрение" и объясни почему.

Вывод на русском в файл docs/orchestration/inbox-secrets.md:
# Incoming analysis
Source: - external model / local analysis / manual inspection
Findings:
- [F-XXX] <description>
  Location: <file:line>
  Risk: <why dangerous>
  Fix: <minimal safe fix>
  Verification: <recipe>
  Fingerprint: <sha256>
  Test scenario: <SEC-SECRETS-XX or new>
  Status: <NEW/DUPLICATE/REGRESSION>
Possible impact:
- ...
Suspected files:
- ...
EOF

ВНИМАНИЕ: Если opencode блокирует чтение .env файлов, используй .env.audit копии:
- backend/.env.audit (вместо backend/.env)
- backend/.env.bsc.audit (вместо backend/.env.bsc)
- backend/.env.sepolia.audit (вместо backend/.env.sepolia)

Эти копии созданы специально для аудита. Читай их.

---
⚠️ КРИТИЧЕСКОЕ ПРАВИЛО:
ОБЯЗАТЕЛЬНО СОХРАНИ ОТЧЁТ В ФАЙЛ перед завершением работы.
Имя файла: docs/orchestration/inbox-<area>.md (например, inbox-contracts.md, inbox-backend.md, inbox-mobile.md).
Используй Write tool. Если отчёт не сохранён в файл — работа считается проваленной.
