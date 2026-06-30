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

Проведи формальную верификацию контрактов — invariant и fuzzing.

ОБЯЗАТЕЛЬНО ПЕРЕД НАЧАЛОМ:
- Прочитай security/FINDINGS.md, security/ERRORS-MEMORY.md
- Прочитай contracts/test/invariant/ (существующие invariant tests)
- Для каждого finding вычисли fingerprint и проверь на дубликат

Проверяй invariants:
- "paymaster balance never goes negative"
- "sum of user deposits == paymaster balance"
- "recovery threshold always 2-of-3"
- "no double-spending of approvals"
- "nickname uniqueness maintained"
- "session key capabilities never escalate"
- "ECDSA s-value always <= secp256k1n/2" (EM-002)
- "no reentrancy in postOp" (EM-024)
- "MockP256 never deployed to mainnet" (EM-023)

Fuzzing:
- Fuzz signature verification (random v, r, s)
- Fuzz paymasterAndData decoding (random bytes)
- Fuzz recovery flow (random guardian combinations)
- Fuzz token amounts (0, max, max-1)
- Fuzz deadlines (past, future, now)
- Fuzz chainId (1, 56, 31337, random)

Запусти:
- forge test --match-contract "Invariant" -vvv
- forge test --match-contract "Fuzz" -vvv
- forge coverage --report lcov

Инструменты (если доступны):
- Halmos (symbolic execution)
- Certora (formal verification)
- medusa (fuzzing)

Не оптимизируй код. Найди invariant violations.

Для каждого риска дай:
1. ID (F-XXX)
2. Contract / invariant
3. What breaks
4. Why (input sequence, state)
5. Как исправить
6. Verification recipe (forge test command)
7. Fingerprint
8. Связь с test-scenarios.md (SEC-CONTRACT-XX)
9. Status: NEW / DUPLICATE / REGRESSION

Вывод на русском в файл docs/orchestration/inbox-formal.md:
# Incoming analysis
Source: - external model / local analysis / manual inspection
Findings:
- [F-XXX] <description>
  Contract: <name>
  Invariant: <what should hold>
  Violation: <how it breaks>
  Fix: <minimal safe fix>
  Verification: <forge test command>
  Fingerprint: <sha256>
  Test scenario: <SEC-CONTRACT-XX>
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
