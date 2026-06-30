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

Проведи security review защиты от MEV и gas manipulation.

ОБЯЗАТЕЛЬНО ПЕРЕД НАЧАЛОМ:
- Прочитай security/FINDINGS.md, security/ERRORS-MEMORY.md
- Для каждого finding вычисли fingerprint и проверь на дубликат

Проверяй:
- Sandwich attacks (DEX swaps — slippage protection)
- Front-running (recovery execute, nickname registration)
- Back-running (arbitrage opportunities)
- Gas price manipulation (maxGasPrice — Wave 5 F-3)
- Priority fee auction
- Private mempool (Flashbots Protect, MEV-Share)
- Permit front-running (approve before transfer)
- Multi-call bundles (atomic execution)
- Replay across bundles (Wave 3 F-2)
- Paymaster postOp front-running (EM-014 related)

Не оптимизируй код. Найди MEV vectors.

Для каждого риска дай:
1. ID (F-XXX)
2. Transaction / flow
3. MEV vector
4. Impact ($ loss)
5. Как исправить (commit-reveal, private mempool, slippage)
6. Verification recipe
7. Fingerprint
8. Связь с test-scenarios.md (PAY-GAS-XX, SEC-REPLAY-XX)
9. Status: NEW / DUPLICATE / REGRESSION

Вывод на русском в файл docs/orchestration/inbox-mev.md:
# Incoming analysis
Source: - external model / local analysis / manual inspection
Findings:
- [F-XXX] <description>
  Flow: <transaction description>
  MEV vector: <sandwich/front-run/back-run>
  Impact: <$ loss estimate>
  Fix: <commit-reveal/private mempool/slippage>
  Verification: <recipe>
  Fingerprint: <sha256>
  Test scenario: <PAY-GAS-XX or SEC-REPLAY-XX>
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
