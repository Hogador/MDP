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

Проведи security review только зависимостей проекта.

ОБЯЗАТЕЛЬНО ПЕРЕД НАЧАЛОМ:
- Прочитай security/FINDINGS.md — registry известных findings
- Прочитай security/ERRORS-MEMORY.md — паттерны ошибок
- Прочитай security/ANTI-PATTERNS.md — запрещённые подходы
- Для каждого finding вычисли fingerprint и проверь на дубликат

Проверяй:
- package.json, package-lock.json (relay/)
- build.gradle.kts, gradle.lockfile, settings-gradle.lockfile (root, backend, app)
- contracts/foundry.toml, .gitmodules
- vendored или встроенные зависимости
- небезопасные версии пакетов (запусти: npm audit)
- подозрительные или давно не обновлявшиеся библиотеки
- supply-chain риски (typosquatting, malicious maintainer)
- конфликты версий
- лишние зависимости, которые можно удалить (ponytail audit)
- submodules integrity (contracts/lib/openzeppelin-contracts — commit hash vs release tag)

Запусти сканеры (если доступны):
- cd relay && npm audit --audit-level=moderate
- ./gradlew dependencyCheckAnalyze (если OWASP plugin установлен)
- trivy image postgres:16-alpine (если trivy установлен)
- trivy image redis:7-alpine

Не переписывай код и не делай оптимизацию ради красоты.
Твоя задача — найти рискованные зависимости и объяснить, что с ними делать.

Для каждого пункта дай:
1. ID (F-XXX, продолжая нумерацию из FINDINGS.md)
2. Название зависимости
3. Версию (current и fixed если есть)
4. В чём риск (CVE ID если есть)
5. Насколько это критично (CVSS score если есть)
6. Как исправить
7. Как проверить исправление
8. Fingerprint (sha256)
9. Связь с test-scenarios.md (SEC-DEPS-XX)
10. Status: NEW / DUPLICATE / REGRESSION

Вывод на русском в файл docs/orchestration/inbox-deps.md:
# Incoming analysis
Source: - external model / local analysis / manual inspection
Findings:
- [F-XXX] <description>
  Dependency: <name@version>
  Risk: <why dangerous>
  Fix: <minimal safe fix>
  Verification: <recipe>
  Fingerprint: <sha256>
  Test scenario: <SEC-DEPS-XX>
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
