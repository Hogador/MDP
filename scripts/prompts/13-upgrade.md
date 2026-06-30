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

Проведи security review upgradeability и proxy patterns.

ОБЯЗАТЕЛЬНО ПЕРЕД НАЧАЛОМ:
- Прочитай security/FINDINGS.md, security/ERRORS-MEMORY.md
- Для каждого finding вычисли fingerprint и проверь на дубликат

Проверяй:
- Proxy pattern (UUPS, Transparent, Beacon)
- Storage layout collisions (upgrades changing slots)
- Initializer re-initialization (_disableInitializers)
- Upgrade authorization (multisig, timelock — EM-021)
- Exit window (PRD ADR — 7 days)
- Migration tooling (data migration between versions)
- Contract versioning (semver, version() function)
- Delegatecall safety (target contracts vetted)
- SocialRecoveryModule upgrade path
- MDAOPaymaster upgrade path
- NicknameRegistry upgrade path

Категории ошибок (из ERRORS-MEMORY.md):
- EM-021: "Mitigated by multisig" without on-chain enforcement

Не оптимизируй код. Найди upgrade risks.

Для каждого риска дай:
1. ID (F-XXX)
2. Contract
3. Что не так
4. Почему опасно
5. Как исправить
6. Verification recipe
7. Fingerprint
8. Связь с test-scenarios.md (PROD-GOVERNANCE-XX)
9. Status: NEW / DUPLICATE / REGRESSION

Вывод на русском в файл docs/orchestration/inbox-upgrade.md:
# Incoming analysis
Source: - external model / local analysis / manual inspection
Findings:
- [F-XXX] <description>
  Contract: <name>
  Issue: <description>
  Risk: <why dangerous>
  Fix: <minimal safe fix>
  Verification: <recipe>
  Fingerprint: <sha256>
  Test scenario: <PROD-GOVERNANCE-XX>
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
