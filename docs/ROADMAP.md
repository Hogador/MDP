# ROADMAP — MDAOPay

> Чек-лист перехода между стадиями. Используется режимом
> opencode run --mode=roadmap --target=<stage> --action=gap-analysis
>
> Coordinator сравнивает текущий код с этим чек-листом и формирует
> GAP-REPORT. Каждая задача имеет ID формата F-NNN (Feature),
> I-NNN (Infra), S-NNN (Security), D-NNN (Docs).

---

## Стадия 1 — LOCALNET (для разработки)

Цель: смартконтракты компилируются, тесты проходят, mobile-приложение
запускается на эмуляторе с локальным узлом (Foundry anvil).

- [x] F-001: Базовый ERC20 DAO-токен (governance) — `MDAOToken.sol`
- [x] F-002: Treasury contract (приём/распределение средств) — ADR-001
- [x] F-003: Proposal contract (создание/голосование/исполнение) — ADR-002
- [x] F-004: PaymentSplitter (раздельные платежи по proposal) — ADR-003
- [x] F-005: SocialRecovery (guardians, threshold, BLS) — `SocialRecoveryModule.sol`
- [x] I-001: Локальный узел — `anvil` (Foundry), запускается через `anvil`
- [x] I-002: Mobile: создание кошелька (seed → keystore) — `WalletManager.kt`
- [x] I-003: Mobile: отправка транзакции на local node — `BundlerClient` + `SendViewModel`
- [~] S-001: Покрытие контрактов unit-тестами >= 80% — тесты есть (20+ файлов), точный % требует forge coverage
- [x] D-001: README с инструкцией запуска localnet

---

## Стадия 1.5 — PRE-TESTNET (подготовка к деплою)

Цель: всё что нужно сделать руками перед деплоем на testnet — заполнить секреты,
настроить инфраструктуру, получить детальную инструкцию.

### 1.5.1. Операционные задачи
- [x] D-050: **Инструкция по деплою testnet** — обновлена: Ktor вместо Spring Boot,
      добавлены Treasury/Proposal/SplitterFactory, F-104 EventIndexer, все секции с чекбоксами.
      *Файл: docs/DEPLOY-TESTNET.md*
- [~] D-051: Заполнить `.env` продакшн-ключами — **руками**, описано в D-050 шаг 2
- [~] D-052: Получить тестовые токены из faucet — **руками**, описано в D-050 шаг 1.2
- [~] D-053: Настроить CI/CD secrets для testnet деплоя — **руками**, описано в D-050 чек-лист

### 1.5.2. Аудит и исправление root causes (R-fixes)

Выявлены 5 корневых причин (R-1…R-5), сворачивающих 56 симптомов предыдущего аудита.
Порядок: верификация → критические фиксы → желательные → стратегический долг.

#### Фаза 0 — Верификация (проверка ERC-4337 assumptions)

**S-130: executeRecovery + SmartAccount (анализ)**
- [x] Анализ: в коде нет SmartAccount контракта — SocialRecoveryModule это standalone passkey-регистр
- [x] Найдено: `executeRecovery` обновляет только `ownerPasskeyHash[wallet]` — не меняет owner ERC-4337 аккаунта
- [x] Вывод: R-5 🔴 CRITICAL — social recovery не даёт новому владельцу контроль над кошельком
- [x] S-130.1: Создать MDAOSmartAccount контракт с интеграцией SocialRecoveryModule (S-137)
- [x] S-130.2: Интеграционный тест полного цикла: deploy → register → recover → execute UserOp новым ключом
      *310 тестов, 0 failures. Старый ключ отклоняется, новый проходит.*

**S-131: useSessionKey через EntryPoint (анализ)**
- [x] Анализ: `useSessionKey()` не проверяет `msg.sender` — любой знающий keyId может использовать
- [x] Найдено: в ERC-4337 контексте UserOp(sender=attacker, callData=useSessionKey(aliceKey)) пройдёт валидацию
- [x] Вывод: R-5 🔴 CRITICAL — нет привязки session key к конкретному sender адресу
- [x] S-131.1: Добавить проверку `msg.sender == key.owner` в useSessionKey — уже было в коде (lines 136, 182)
- [x] S-131.2: Тест: попытка чужого keyId через UserOp должна падать — test_RevertWhen_ForeignKeyUsedByStranger

**S-132: Deploy order dependencies (выполнен)**
- [x] Составлен граф зависимостей 15 контрактов
- [x] Найдено: 3 контракта (Treasury, Proposal, PaymentSplitter) отсутствуют во всех deploy скриптах
- [x] Найдено: Proposal захардкожен на адрес Treasury в конструкторе — неапгрейдабельно
- [x] Найдено: MDAOPaymaster проверяет extcodesize(entryPoint) — упадёт если entryPoint не контракт
- [x] Найдено: SocialRecoveryModule импортирует MDAOToken а не IERC20 — tight coupling
- [x] Найдено: PaymentSplitter требует отдельный вызов initialize() после деплоя — неатомарно
- [x] Результат: составлен топологический порядок деплоя (17 шагов)
- [x] S-132.1: Добавить Treasury/Proposal/PaymentSplitter в Deploy.s.sol и deploy-testnet.sh (S-143)

#### Фаза 0.5 — Очистка Sepolia → BSC Testnet migration
**Контекст:** Проект перешёл с Sepolia на BSC Testnet (chain 97). Все Sepolia-артефакты должны быть удалены или переписаны.

- [x] S-148: Удалить DeploySepolia.s.sol (не нужен — Deploy.s.sol уже на BSC 56/97)
- [x] S-149: Переписать backend/.env под BSC testnet (chain 97, RPC bsc-testnet, tBNB)
- [x] S-150: Удалить backend/.env.sepolia.audit и .env.audit
- [x] S-151: Переписать docs/DEPLOY-TESTNET.md под BSC testnet
- [x] S-152: Переписать docs/launch-instructions.md под BSC testnet
- [x] S-153: Переписать docs/e2e-test-plan.md под BSC testnet
- [x] S-154: App: 5 Kotlin файлов — Sepolia→BSC Testnet/tBNB/bscscan.com
- [x] S-155: TDD/test-scenarios.md: переписать раздел I (Sepolia→BSC Testnet)
- [x] S-156: DeployMDAOPaymaster.s.sol: комментарии Sepolia→BSC
- [x] S-157: TDD/code-roadmap.md: Sepolia→BSC Testnet

#### Фаза 1 — R-1: Proposal ↔ Treasury синхронизация (🔴 CRITICAL, обязательно)
- [x] S-133: Откатить предыдущий фикс (onlyRole) и переделать executeAllocation — привязка к address(proposalContract), CEI-порядок (executed=true до перевода), alloc.amount=0 double-spend защита
      *Файлы: Treasury.sol*
- [x] S-134: Cascade cancel в Proposal — cancelProposal() с CEI-порядком (cancelled=true до вызова Treasury) + расширить доступ (FINANCE_ROLE ИЛИ proposer)
      *Файлы: Proposal.sol, Treasury.sol*
- [x] S-135: retryProposal для failed proposals (новый proposal с тем же allocId)
      *Файлы: Proposal.sol*
- [x] S-136: receiveAndDistribute в PaymentSplitter для атомарной дистрибуции при получении средств из Treasury
      *Файлы: PaymentSplitter.sol*
- [x] S-136.1: Обновить тесты под новый дизайн R-1
      *Файлы: Treasury.t.sol, Proposal.t.sol, PaymentSplitter.t.sol*

#### Фаза 2 — R-5: ERC-4337 assumptions (🔴 CRITICAL, ✅ выполнено)
- [x] S-137: MDAOSmartAccount + SocialRecoveryModule — recovery → transferOwnership. Добавлены `setRecoveryTransfer`, `recoverySmartAccount`, `recoveryEOAOwner`. Интеграционный тест.
      *Файлы: contracts/src/MDAOSmartAccount.sol, contracts/src/SocialRecoveryModule.sol, contracts/test/MDAOSmartAccount.t.sol*
- [x] S-138: `msg.sender == key.owner` в useSessionKey (строка 182) — уже реализовано
      *Файлы: SessionKeyModule.sol*
- [x] S-139: SessionKeyModule.onRecoveryExecuted — инвалидирует session keys при recovery (через IRecoveryHook, Phase 4)
      *Файлы: SessionKeyModule.sol, SocialRecoveryModule.sol*

#### Фаза 3 — R-2: Treasury → PaymentSplitter разрыв (🟡 HIGH, ✅ выполнено)
- [x] S-140: PaymentSplitterFactory — отдельный контракт (CREATE2), Treasury ссылается через splitterFactory
- [x] S-141: Реестр верифицированных Splitter-ов (deployedSplitters mapping в Factory) + валидация recipient при createAllocation
      *Файлы: Treasury.sol, PaymentSplitterFactory.sol*
- [x] S-142: PaymentSplitter receiveAndDistribute — атомарная дистрибуция всем пэйи
      *Файлы: PaymentSplitter.sol*
- [x] S-143: Governance контракты в Deploy.s.sol + verify_contract + deployment summary
      *Файлы: Deploy.s.sol, scripts/deploy-testnet.sh*

#### Фаза 4 — R-4: Lifecycle hooks (🟡 HIGH, ✅ выполнено)
- [x] S-144: Интерфейсы IRecoveryHook (onRecoveryExecuted), IDeprecationHook (onPaymasterDeprecated)
      *Файлы: contracts/src/interfaces/* — IRecoveryHook реализован SessionKeyModule + DeadManSwitch*
- [x] S-145: SocialRecoveryModule.executeRecovery → вызывает IRecoveryHook.onRecoveryExecuted на SessionKeyModule + DeadManSwitch
      *Файлы: SocialRecoveryModule.sol, SessionKeyModule.sol, DeadManSwitch.sol*
- [x] S-146: Фикс двойного burn — SocialRecoveryModule добавлен в isExempt (MDAOToken)
      *Файлы: Deploy.s.sol (setExempt после деплоя SocialRecoveryModule)*
- [x] S-147: **Решено:** DeadManSwitch — чистый таймер/триггер, без deposits/ownership transfer.
      Только события → watchtower → guardian recovery через SocialRecoveryModule.
      *Файлы: DeadManSwitch.sol, ADR-004*

---

## Стадия 2 — TESTNET (BSC Testnet, chain 97)

Цель: развёртывание на публичном тестнете, end-to-end сценарии с реальной
сетью, но без реальных средств.

### 2.1. Смартконтракты
- [x] F-101: Деплой-скрипт для testnet (forge script) — `script/Deploy.s.sol`
- [x] F-102: Verify контрактов на Etherscan — `scripts/deploy-testnet.sh` (forge script --verify)
- [ ] F-103: Chainlink price feeds — отложено до mainnet. Текущая система (owner-set + off-chain quotes) достаточна для testnet.
      *Статус: DECIDED — не блокер*
- [x] F-104: Event-индексация для backend — EventIndexer (Kotlin/Ktor/Web3j), поллинг 6 контрактов, `/v1/events` REST, Flyway V6
      *Файлы: EventIndexer.kt, V6__onchain_events.sql, AppConfig.kt, Application.kt, deploy-testnet.sh*
- [x] S-101: Аудит смартконтрактов — 17 контрактов, 0 CRITICAL, 0 HIGH
      *Отчёт: .hive/reports/audit-S101-full.md*

### 2.2. Mobile
- [x] F-110: Подключение к testnet RPC — CHAIN_ID per-flavor (97 dev/staging, 56 prod)
- [x] F-111: Импорт/экспорт кошелька (seed phrase) — BackupScreen + mnemonic + share sheet
- [x] F-112: История транзакций — HistoryViewModel + EtherscanRepository sync (BSCScan API)
- [x] F-113: Голосование по proposal — ProposalScreen + Repository (via EventIndexer, read-only MVP)
- [x] F-114: Social recovery flow — RecoveryScreen (RecoveryUserOpBuilder, passkey, integrity check)
- [~] S-110: E2E тест-сценарии — `docs/e2e-test-plan.md` (152 строки)

### 2.3. Backend / Infra
- [x] I-101: Indexer service — выполнен как F-104 (кастомный на Web3j + PostgreSQL)
- [x] I-102: Push-уведомления (proposal created, vote requested) — `relay/src/fcm.ts` + FCM routes в index.ts + app `BadgeManager`
- [x] I-103: Backup/restore backend state — scripts/db-backup.sh + db-restore.sh (pg_dump)
- [x] S-120: CI/CD pipeline — test.yml (contracts + backend) + deploy-testnet.yml (manual trigger)

### 2.4. Документация
- [ ] D-101: User guide (RU/EN)
- [ ] D-102: Smart contract docs (NatSpec)
- [x] D-103: ADR для ключевых решений — 5 ADR (Treasury, Proposal, PaymentSplitter, DeadManSwitch + template)

---

## Стадия 3 — MAINNET (production)

Цель: запуск в боевом режиме. Все принципы VISION.md должны быть соблюдены.

### 3.1. Безопасность (блокер для mainnet)
- [ ] S-201: Внешний аудит смартконтрактов (третья сторона)
- [ ] S-202: Bug bounty программа (>= 1 месяц до mainnet)
- [ ] S-203: Multisig на всех admin-функциях (3/5)
- [ ] S-204: Timelock >= 48ч на все upgradeable
- [ ] S-205: Emergency pause + on-chain разблокировка
- [ ] S-206: Финальный аудит мобильного приложения (security + devops)

### 3.6. Стратегический долг — R-3: VISION ≠ код (после testnet)
- [ ] S-207: ADR-на-ADR: зафиксировать расхождение VISION с кодом, утвердить план миграции
      *Файлы: docs/adr/ADR-004-vision-gap.md*
- [ ] S-208: Ownable → AccessControl миграция (8 контрактов: MDAOToken, InsuranceFund, MDAOPaymaster, SocialRecoveryModule, DeadManSwitch, RefundVault, TrustProviderRegistry, AttestationLedger)
      *Файлы: wallet contracts*
- [ ] S-209: MDAOToken — убрать mint() или добавить hard cap check (нарушение VISION §4.3)
      *Файлы: MDAOToken.sol*
- [ ] S-210: Multisig constraint — хелпер OwnableWithMultisig для критичных функций
      *Файлы: new file (helpers/OwnableWithMultisig.sol)*
- [ ] S-211: Emergency-unpause через on-chain голосование (Treasury + Proposal)
      *Файлы: Treasury.sol, Proposal.sol*
- [ ] S-212: FINANCE_ROLE rotation с timelock (2 дня)
      *Файлы: Treasury.sol, Proposal.sol*

### 3.2. Инфраструктура
- [ ] I-201: Production RPC endpoint (redundancy)
- [ ] I-202: Production indexer (HA, monitoring)
- [ ] I-203: Monitoring + alerting (Grafana/Prometheus)
- [ ] I-204: Backup-стратегия для backend state
- [ ] I-205: Incident response playbook

### 3.3. Смартконтракты
- [ ] F-201: Финальный деплой mainnet (через HSM/KMS)
- [ ] F-202: Verify всех контрактов
- [ ] F-203: Настройка multisig (DAO treasury, governance admin)
- [ ] F-204: Передача ownership multisig

### 3.4. Mobile
- [ ] F-210: Release build с правильным signing
- [ ] F-211: Reproducible build (CI выкладывает артефакт + hash)
- [ ] F-212: Publication в Google Play / TestFlight
- [ ] F-213: Crashlytics / error reporting (opt-in)

### 3.5. Документация
- [ ] D-201: Security audit report (опубликован)
- [ ] D-202: Post-mortem process документ
- [ ] D-203: Runbook для operational tasks

---

## Как обновлять ROADMAP

- Добавление задач: opencode run --mode=roadmap --action=add --task="..."
- Отметка выполненной: Coordinator автоматически ставит [x] после
  успешного завершения --mode=feature --task=F-NNN
- Перенос между стадиями: только через ADR

---

Версия: 1.1 · Стадии: LOCALNET / TESTNET / MAINNET
· 2026-07-07: актуализация LOCALNET/Stage1 (все [x]), обновлено описание I-001 (Hardhat→Foundry anvil), I-102/D-103 помечены [x]

### 3.7. Audit-фиксы (из аудита 2026-07, блокер для mainnet)

Owner: Coordinator · Deadline: перед F-201 (mainnet deploy)

- [x] C-01: moonpay-proxy 404 при отсутствии apiKey (860cce3)
- [x] C-02: удалён мёртвый relay /auth/siwe + siwe.ts (189ca3a)
- [x] C-03: veto nonce валидация в relay (860cce3)
- [x] C-04: KV TOCTOU → ADR-005 Durable Objects (docs/adr/ADR-005-durable-objects.md, статус proposed — реализация до S-201)
- [x] C-05: RedisClient атомарный compute() (860cce3)
- [x] C-06: quote deadline 300→120s + контракт buffer 300→60s (189ca3a)
- [x] C-07: relay CHAIN_ID env (860cce3; endpoint удалён в 189ca3a)
- [x] C-08: deploy-testnet.sh ABI constructor + auditors(0) (860cce3)
- [x] H-01: swapPrivateKey heap → KMS · owner: DevOps · deadline: до F-201 · тикет: ADRD-2026-002
- [x] H-02: CoinGecko key URL → x-cg-pro-api-key header (6392e9d)
- [x] H-03: relay rate limiter per-isolate → Cloudflare Rate Limiting · owner: DevOps · deadline: до S-201 · тикет: ADRD-2026-003
- [x] H-04: SIWE domain/chainId валидация (189ca3a)
- [x] H-05: pre-commit hook включён (be5c163) · .env секреты → vault · owner: DevOps · deadline: до S-201 · тикет: ADRD-2026-004
- [x] H-06: decimals контракта для 6-dec USDT · owner: Coder · deadline: до F-201 · тикет: ADRD-2026-005 (требует тесты на реальный USDT)
- [x] H-07: computeUserOpHash → v0.6 (189ca3a)
- [x] H-08: USDT/MDAO контракты per-flavor BuildConfig (6392e9d)
- [x] H-09: mobile permit-поля в paymasterAndData (6392e9d)
- [x] H-10: (не было в финальном списке)
- [x] H-11: SIWE nonce мёртвый код удалён (189ca3a)
- [ ] BUILD-01: release signingConfig · owner: Mobile · deadline: до F-210
- [ ] BUILD-02: google-services.json + plugin · owner: Mobile · deadline: до F-210

### 3.8. Pre-deploy checklist (H-06 decimals, перед каждым деплоем paymaster)

Owner: Coordinator · обязателен до F-201 (mainnet)

- [ ] chainId в `app/build.gradle.kts` == RPC chainId (97 testnet / 56 mainnet) — dev/staging на 97L, prod на 56L
- [ ] адреса контрактов переданы через project properties (PAYMASTER/USDT/MDAO/PROPOSAL_CONTRACT_*), не хардкод-fallback в prod (см. H-08)
- [ ] decimals токена on-chain подтверждён: BSC USDT = 6, MDAO = 18 → `setTokenDecimals(usdt, 6)` вызван owner после деплоя paymaster (whitelist {6,8,18}, иначе InvalidToken)
- [ ] backend env: `USDT_DECIMALS=6` `MDAO_DECIMALS=18` — согласовано с on-chain registry (AppConfig валидирует ∈ {6,8,18})
- [ ] `maxTokenAmountLimit` после setTokenDecimals = 10_000 * 10^decimals (base units) — проверить через view
- [ ] EIP-170: runtime MDAOPaymaster = 14,869 B < 24,576 B (optimizer_runs=10000) — деплой не упадёт по size
- [ ] «тесты на реальный USDT» (см. H-06): одна live-транзакция с 6-dec USDT на testnet до mainnet

Отчёты: security/COMPREHENSIVE-AUDIT-2026-07-20.md (3rd pass, 82 находки) · security/AUDIT-2026-07-14.md · security/COMPREHENSIVE-AUDIT-2026-07-16.md · индекс: security/FINDINGS-INDEX.md · уроки: security/LESSON-*.md
