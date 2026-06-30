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

Проведи chaos engineering аудит — что будет при отказах.

ОБЯЗАТЕЛЬНО ПЕРЕД НАЧАЛОМ:
- Прочитай security/FINDINGS.md, security/ERRORS-MEMORY.md
- Прочитай TDD/test-scenarios-v5-final.md §6 (Chaos Testing Protocol)
- Для каждого finding вычисли fingerprint и проверь на дубликат

Проверяй (для каждого сценария — что происходит?):
- Redis down → rate limit disabled, replay cache lost
- PostgreSQL primary down → failover < 30s?
- PostgreSQL replica down → reads fallback?
- RPC primary down → failover < 5s? (EM-062)
- All RPC down → UI degrades gracefully?
- WebSocket relay down → polling fallback?
- FCM key revoked → in-app badge works?
- Cloud Run restart mid-/sign → idempotency?
- Bundler down → fallback < 60s?
- Paymaster down → /sign returns 503?
- Nickname service down → cached names work 1h?
- DexScreener API down → price fallback?
- DNS outage → hardcoded IP fallback?
- TLS cert expired → cert pinning triggers?
- Disk full → writes fail gracefully?
- Bundle broadcast fail → retry policy?
- Gas price spike → circuit breaker?

Категории ошибок (из ERRORS-MEMORY.md):
- EM-062: Single RPC URL without failover

Не оптимизируй код. Найди single points of failure.

Для каждого риска дай:
1. ID (F-XXX)
2. Сервис / компонент
3. Что падает
4. Что происходит (graceful degrade? crash? data loss?)
5. Как исправить (fallback, circuit breaker, queue)
6. Verification recipe (chaos command)
7. Fingerprint
8. Связь с test-scenarios.md (EDGE-INFRA-XX, §6 Chaos)
9. Status: NEW / DUPLICATE / REGRESSION

Вывод на русском в файл docs/orchestration/inbox-chaos.md:
# Incoming analysis
Source: - external model / local analysis / manual inspection
Findings:
- [F-XXX] <description>
  Service: <name>
  Failure: <what fails>
  Current behavior: <graceful/crash/data loss>
  Fix: <fallback/circuit breaker/queue>
  Verification: <chaos command>
  Fingerprint: <sha256>
  Test scenario: <EDGE-INFRA-XX>
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
