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

Проведи анализ покрытия тестов — какие сценарии из test-scenarios.md не покрыты.

ОБЯЗАТЕЛЬНО ПЕРЕД НАЧАЛОМ:
- Прочитай security/FINDINGS.md
- Прочитай TDD/test-scenarios-v5-final.md ПОЛНОСТЬЮ
- Прочитай security/TEST-COVERAGE-MAP.md

Проверяй:
- Для каждого scenario family (SEC-*, AA-*, PAY-*, EDGE-*, etc.):
  - Сколько variants заявлено
  - Сколько variants имеют actual test
  - Какие variants missing
- Smoke tests (20) — все ли реализованы?
- Chaos tests (15) — какие не автоматизированы?
- Load tests (k6) — какие пороги не проверены?

Формат отчёта:
| Family | Variants claimed | Variants tested | Coverage % | Missing |
|--------|------------------|-----------------|------------|---------|
| SEC-REPLAY | 6 | 4 | 67% | C, E |
| SEC-PHISHING | 5 | 2 | 40% | C, D, E |
| ...

Для каждого missing variant:
1. ID (F-XXX)
2. Scenario (e.g., SEC-REPLAY-C)
3. Что должно тестироваться
4. Почему важно
5. Какой тест нужен (file, test name)
6. Verification recipe (forge test --match-test ...)
7. Status: NEW / DUPLICATE

Запусти для baseline:
- forge coverage --report lcov (contracts)
- ./gradlew :backend:test (backend, если JaCoCo настроен)
- npm test -- --coverage (relay)

Вывод на русском в файл docs/orchestration/inbox-coverage.md:
# Incoming analysis
Source: - local analysis / manual inspection
Findings:
- [F-XXX] Missing test: SEC-REPLAY-C (Cross-chain paymaster sig replay)
  Family: SEC-REPLAY
  What: Test that signature on chain 1 fails on chain 2
  Why: EM-011 (no chainId in message hash)
  Test needed: contracts/test/MDAOPaymaster.t.sol::testCrossChainReplayRevert
  Verification: forge test --match-test "testCrossChainReplayRevert"
  Status: NEW
...
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
