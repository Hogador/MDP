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
запускается на эмуляторе с локальным hardhat-узлом.

- [ ] F-001: Базовый ERC20 DAO-токен (governance)
- [ ] F-002: Treasury contract (приём/распределение средств)
- [ ] F-003: Proposal contract (создание/голосование/исполнение)
- [ ] F-004: PaymentSplitter (раздельные платежи по proposal)
- [ ] F-005: SocialRecovery (guardians, threshold, BLS)
- [ ] I-001: Hardhat-конфиг с локальным узлом
- [ ] I-002: Mobile: создание кошелька (seed → keystore)
- [ ] I-003: Mobile: отправка транзакции на local node
- [ ] S-001: Покрытие контрактов unit-тестами >= 80%
- [ ] D-001: README с инструкцией запуска localnet

---

## Стадия 2 — TESTNET (Sepolia / Holesky)

Цель: развёртывание на публичном тестнете, end-to-end сценарии с реальной
сетью, но без реальных средств.

### 2.1. Смартконтракты
- [ ] F-101: Деплой-скрипт для testnet (forge script)
- [ ] F-102: Verify контрактов на Etherscan
- [ ] F-103: Интеграция с Chainlink price feeds (если нужно)
- [ ] F-104: Event-индексация для backend
- [ ] S-101: Аудит смартконтрактов (5 Researcher: security/arch/perf/ux/devops)

### 2.2. Mobile
- [ ] F-110: Подключение к testnet RPC
- [ ] F-111: Импорт/экспорт кошелька (seed phrase)
- [ ] F-112: История транзакций (с backend indexing)
- [ ] F-113: Голосование по proposal из приложения
- [ ] F-114: Social recovery flow (initiate/confirm/veto)
- [ ] S-110: Тест-сценарии v5 (см. test-scenarios-v5-final.md)

### 2.3. Backend / Infra
- [ ] I-101: Indexer service (subgraph или кастомный)
- [ ] I-102: Push-уведомления (proposal created, vote requested)
- [ ] I-103: Backup/restore backend state
- [ ] S-120: CI/CD pipeline с reproducible builds

### 2.4. Документация
- [ ] D-101: User guide (RU/EN)
- [ ] D-102: Smart contract docs (NatSpec)
- [ ] D-103: ADR для всех ключевых решений

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

Версия: 1.0 · Стадии: LOCALNET / TESTNET / MAINNET
