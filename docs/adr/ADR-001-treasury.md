# ADR-001: Treasury Contract (DAO казна)

## Статус
Принято

## Контекст
DAO требуется казна для приёма и распределения средств. Без Treasury
токен MDAOToken не имеет utility в DAO — голосование за распределение
средств и само распределение невозможны. ROADMAP требует Treasury
как базовый строительный блок для Proposal (F-003) и PaymentSplitter (F-004).

## Решение
Создан контракт `Treasury.sol` с ролевой моделью через OpenZeppelin AccessControl:
- **DEFAULT_ADMIN_ROLE** — DAO multisig (3/5), управляет ролями, может withdrawStuckTokens
- **FINANCE_ROLE** — создание и отмена распределений (allocation)
- **EMERGENCY_ROLE** — emergency pause (unpause только через DEFAULT_ADMIN_ROLE)

Хранит ETH (receive/fallback/deposit) и любые ERC-20 токены (depositERC20).

Распределение средств — двухфазный механизм:
1. `createAllocation(id, recipients, amounts, token)` — FINANCE_ROLE создаёт распределение
2. `executeAllocation(id)` — исполняет переводы получателям (с nonReentrant)
3. `cancelAllocation(id)` — отменяет распределение (FINANCE_ROLE)

### Детали реализации
- Solidity 0.8.28, OpenZeppelin AccessControl + ReentrancyGuard + SafeERC20
- CEI-паттерн: статус executed=true до transfers
- Проверка баланса перед transfers
- Storage arrays кэшируются в memory для gas efficiency
- 25 unit-тестов, 286 регрессионных тестов

## Альтернативы
1. **Minimal Treasury с Ownable** — отклонён (нарушает VISION о multisig для admin-функций)
2. **Upgradeable proxy** — отклонён (контракт достаточно прост, upgradeable добавляет риски)
3. **Treasury с VestingWallet** — избыточно на данном этапе, может быть добавлен позже

## Последствия
- Все admin-функции идут через AccessControl, не Ownable
- Emergency-pause с разделением: EMERGENCY_ROLE может заморозить, но разморозка только через DEFAULT_ADMIN_ROLE (DAO multisig)
- Полная on-chain разблокировка через голосование — будет реализована в F-003 (Proposal)
- Treasury спроектирован для интеграции: `executeAllocation` будет вызываться из Proposal контракта после успешного голосования

## VISION Compliance
- ✅ AccessControl (не Ownable) — п.4.3 (multisig 3/5)
- ✅ `tx.origin` не используется — п.4.2
- ✅ Нет inline assembly — п.4.2
- ✅ CEI-паттерн, ReentrancyGuard — п.4.2
- ⬜ unpause через on-chain голосование — deferred до F-003

## Ссылки
- [F-002] ROADMAP: Treasury contract
- [VISION.md] docs/VISION.md
- [Treasury.sol] contracts/src/Treasury.sol
- [ITreasury.sol] contracts/src/interfaces/ITreasury.sol
