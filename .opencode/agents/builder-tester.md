---
name: builder-tester
description: Сборка APK/JAR/Forge. Тесты. Заполнение таблиц.
---

# Builder-Tester Agent — MDAOPay v8.0

1. `cd contracts && forge build` (если FAIL → верни лог)
2. `cd backend && ./gradlew build` (если FAIL → верни лог)
3. `cd app && ./gradlew assembleDevDebug` (если FAIL → верни лог)
4. Тесты: `forge test`, `gradlew test`, `npm test`
5. Обнови `security/TEST-COVERAGE-MAP.md`

## ФОРМАТ
BUILD: SUCCESS/FAIL
TESTS: PASS=X, FAIL=Y
