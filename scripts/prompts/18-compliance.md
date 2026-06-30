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

Проведи compliance аудит — OFAC, GDPR, MiCA.

ОБЯЗАТЕЛЬНО ПЕРЕД НАЧАЛОМ:
- Прочитай security/FINDINGS.md, security/ERRORS-MEMORY.md
- Прочитай TDD/test-scenarios-v5-final.md LEG-COMPLIANCE
- Для каждого finding вычисли fingerprint и проверь на дубликат

Проверяй:
- OFAC sanctions screening (addresses, geo-fencing)
- GDPR right to erasure (nickname data, transaction history)
- GDPR data portability (export user data)
- MiCA compliance (crypto-asset regulation)
- KYC/AML for on-ramp (Transak/Onramper integration)
- Privacy policy (links in app, website)
- Terms of service
- Cookie consent (if web)
- Data retention policy (logs, DB, backups)
- Audit log (TDD §1.6 audit_log table)
- Tax reporting (1099-K if US users)
- Smart contract audit requirements (MiCA)
- White paper requirements (MiCA)
- Notification of competent authority

Категории ошибок:
- (новые — compliance patterns)

Не оптимизируй код. Найди compliance gaps.

Для каждого риска дай:
1. ID (F-XXX)
2. Area (OFAC/GDPR/MiCA/etc.)
3. Что не соответствует
4. Почему это regulatory risk
5. Как исправить (technical + legal)
6. Verification recipe
7. Fingerprint
8. Связь с test-scenarios.md (LEG-COMPLIANCE-XX)
9. Status: NEW / DUPLICATE / REGRESSION

Вывод на русском в файл docs/orchestration/inbox-compliance.md:
# Incoming analysis
Source: - external model / local analysis / manual inspection
Findings:
- [F-XXX] <description>
  Area: <OFAC/GDPR/MiCA/etc.>
  Gap: <what's missing>
  Risk: <regulatory consequence>
  Fix: <technical + legal>
  Verification: <recipe>
  Fingerprint: <sha256>
  Test scenario: <LEG-COMPLIANCE-XX>
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
