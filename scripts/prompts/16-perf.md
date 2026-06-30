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

Проведи security review производительности и нагрузки.

ОБЯЗАТЕЛЬНО ПЕРЕД НАЧАЛОМ:
- Прочитай security/FINDINGS.md, security/ERRORS-MEMORY.md
- Прочитай TDD/test-scenarios-v5-final.md §5.5 (Load Tests)
- Для каждого finding вычисли fingerprint и проверь на дубликат

Проверяй:
- /sign под 1000 RPS — bottleneck? (DB? Redis? RPC? CPU?)
- /nickname/resolve под 500 RPS — cache hit ratio?
- /swap/quote под 100 RPS — DEX API rate limit?
- Bundler mempool под 10000 UserOps — backlog?
- Paymaster deposit tracking — race conditions?
- PostgreSQL connection pool — exhaustion?
- Redis memory — cache stampede?
- Cloud Run cold starts — abuse vector?
- Mobile app — ANR under load?
- Gas estimation — RPC calls per request?
- PaymasterService — per-call Mac.getInstance() overhead (C-6 fix)
- NicknameService — synchronized block contention (EM-063)

Запусти (если не запускалось):
- k6 run backend/load-tests/sign.js
- k6 run backend/load-tests/nickname.js

Не оптимизируй код. Найди performance bottlenecks.

Для каждого риска дай:
1. ID (F-XXX)
2. Endpoint / resource
3. Bottleneck description
4. Impact (latency, errors, downtime)
5. Как исправить (cache, pool, queue, scale)
6. Verification recipe (k6 command + threshold)
7. Fingerprint
8. Связь с test-scenarios.md (SCALE-XX)
9. Status: NEW / DUPLICATE / REGRESSION

Вывод на русском в файл docs/orchestration/inbox-perf.md:
# Incoming analysis
Source: - external model / local analysis / manual inspection
Findings:
- [F-XXX] <description>
  Resource: <endpoint/component>
  Bottleneck: <description>
  Impact: <latency/errors/downtime>
  Fix: <cache/pool/queue/scale>
  Verification: <k6 command>
  Fingerprint: <sha256>
  Test scenario: <SCALE-XX>
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
