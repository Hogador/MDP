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

Проведи security review сетевой безопасности.

ОБЯЗАТЕЛЬНО ПЕРЕД НАЧАЛОМ:
- Прочитай security/FINDINGS.md, security/ERRORS-MEMORY.md (особенно EM-061, EM-062)
- Для каждого finding вычисли fingerprint и проверь на дубликат

Проверяй:
- Certificate pinning (Android CertificatePinner, iOS) — EM-061
- TLS version (1.2 minimum, 1.3 preferred)
- Cipher suites (weak ciphers disabled)
- Cleartext traffic (android:usesCleartextTraffic)
- HTTP Strict Transport Security (HSTS) on backend
- CORS configuration (wildcard vs whitelist)
- WebSocket security (wss, origin validation)
- DNS (DNSSEC, DoH/DoT)
- BGP hijack mitigation (multi-RPC — EM-062)
- Man-in-the-Middle protection
- Certificate transparency monitoring
- API key in URL vs header (Etherscan proxy)
- RPC endpoint authentication

Категории ошибок (из ERRORS-MEMORY.md):
- EM-061: No certificate pinning
- EM-062: Single RPC URL without failover

Не оптимизируй код. Найди network vulnerabilities.

Для каждого риска дай:
1. ID (F-XXX)
2. Где (file/config)
3. Что не так
4. Почему опасно
5. Как исправить
6. Verification recipe
7. Fingerprint
8. Связь с test-scenarios.md (SEC-NETWORK-XX)
9. Status: NEW / DUPLICATE / REGRESSION

Вывод на русском в файл docs/orchestration/inbox-network.md:
# Incoming analysis
Source: - external model / local analysis / manual inspection
Findings:
- [F-XXX] <description>
  Location: <file:line or config>
  Risk: <why dangerous>
  Fix: <minimal safe fix>
  Verification: <recipe>
  Fingerprint: <sha256>
  Test scenario: <SEC-NETWORK-XX>
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
