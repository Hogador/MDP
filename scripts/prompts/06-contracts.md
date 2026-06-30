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

Проведи аудит смарт-контрактов только на безопасность.

ОБЯЗАТЕЛЬНО ПЕРЕД НАЧАЛОМ:
- Прочитай security/FINDINGS.md, security/ERRORS-MEMORY.md (особенно EM-020..EM-024)
- Для каждого finding вычисли fingerprint и проверь на дубликат

Проверяй:
- reentrancy (CEI pattern, ReentrancyGuard, EM-024)
- access control (onlyOwner, onlyRole, TimelockController, EM-021)
- integer math (Solidity 0.8+ checked, но custom unchecked blocks)
- unsafe external calls (low-level call, delegatecall)
- upgradeability (UUPS, transparent proxy, storage layout)
- initializer issues (re-initialization, _disableInitializers)
- oracle manipulation (price feeds, median, deviation, EM-020)
- frontrunning / sandwich risk (commit-reveal, slippage)
- event consistency (events for all state changes, EM-021)
- dangerous defaults (max uint256 approval, address(0))
- centralization (owner can withdraw — EM-021, EM-022)
- MockP256 in production (EM-023)
- Paymaster economics (deposit drain, TOCTOU — EM-014)
- ERC-4337 compliance (validationData, postOp revert handling)

Контракты для аудита:
- contracts/src/MDAOPaymaster.sol
- contracts/src/SocialRecoveryModule.sol
- contracts/src/MDAOToken.sol
- contracts/src/NicknameRegistry.sol
- contracts/src/SessionKeyModule.sol
- contracts/src/RefundVault.sol
- contracts/src/InsuranceFund.sol
- contracts/src/DeadManSwitch.sol
- contracts/src/AttestationLedger.sol
- contracts/test/mocks/MockP256.sol

Категории ошибок (из ERRORS-MEMORY.md):
- EM-002: Raw ecrecover without s-value check
- EM-003: EIP-191 instead of EIP-712
- EM-011: No chainId in message hash
- EM-020: setPriceBufferBps no upper bound
- EM-021: "Mitigated by multisig" without on-chain enforcement
- EM-022: RefundVault withdrawable by owner
- EM-023: MockP256 in production
- EM-024: safeTransfer ≠ reentrancy protection

Не предлагай оптимизацию газа как приоритет.
Сначала найди критические и high-risk баги.

На каждый найденный пункт дай:
1. ID (F-XXX)
2. Место (contract:function:line)
3. Риск
4. Причина
5. Исправление (FIX-PATTERNS.md ref)
6. Способ проверки (recipe)
7. Fingerprint
8. Связь с test-scenarios.md (SEC-CONTRACT-XX)
9. Status: NEW / DUPLICATE / REGRESSION

Вывод на русском в файл docs/orchestration/inbox-contracts.md:
# Incoming analysis
Source: - external model / local analysis / manual inspection
Findings:
- [F-XXX] <description>
  Location: <contract:line>
  Risk: <why dangerous>
  Fix: <minimal safe fix>
  Verification: <recipe>
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
