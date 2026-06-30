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

Проведи security review защиты от DoS и rate limiting.

ОБЯЗАТЕЛЬНО ПЕРЕД НАЧАЛОМ:
- Прочитай security/FINDINGS.md, security/ERRORS-MEMORY.md
- Для каждого finding вычисли fingerprint и проверь на дубликат

Проверяй:
- /sign endpoint — rate limit per IP, per wallet, per API key
- /nickname/register — per IP, per wallet
- /recovery/* — per guardian, per wallet
- /swap/* — per user, per token pair
- Relay endpoints — HMAC auth + rate limit
- WebView bridge — message rate limit
- RPC calls — multi-provider failover (EM-062)
- Bundler — mempool spam protection
- Paymaster — deposit drain (EM-014)
- Redis — connection pool, memory limits
- PostgreSQL — connection pool, query timeout
- Cloud Run — instance limits, cold start abuse
- Nonce gap DoS (MAX_NONCE_GAP — Wave 5 F-9)
- Anti-griefing в postOp (EM-014)

Категории ошибок (из ERRORS-MEMORY.md):
- EM-014: No anti-griefing in postOp
- EM-062: Single RPC URL without failover

Не оптимизируй код. Найди DoS vectors.

Для каждого риска дай:
1. ID (F-XXX)
2. Endpoint / resource
3. Attack vector
4. Impact (RPS, cost, downtime)
5. Как исправить (rate limit, queue, circuit breaker)
6. Verification recipe (k6 load test command)
7. Fingerprint
8. Связь с test-scenarios.md (SEC-DOS-XX, SCALE-XX)
9. Status: NEW / DUPLICATE / REGRESSION

Вывод на русском в файл docs/orchestration/inbox-dos.md:
# Incoming analysis
Source: - external model / local analysis / manual inspection
Findings:
- [F-XXX] <description>
  Endpoint: <path>
  Attack vector: <description>
  Impact: <RPS/cost/downtime>
  Fix: <rate limit/queue/circuit breaker>
  Verification: <k6 command>
  Fingerprint: <sha256>
  Test scenario: <SEC-DOS-XX or SCALE-XX>
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
