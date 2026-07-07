# ADR-004: DeadManSwitch — чистый таймер/триггер без финансовых операций

**Дата:** 2026-07-06
**Статус:** accepted
**Supersedes:** —
**Superseded by:** —

## Контекст

S-147 поставил вопрос: "DeadManSwitch при claim должен переводить средства из Treasury?"

Изначальный дизайн DeadManSwitch содержал:
- `deposits` mapping + `receive()` — пользователь отправляет ETH как "депозит бенефициару"
- `claimFunds()` — бенефициар забирает депозит после периода неактивности
- `Ownable` + `ReentrancyGuard` — защита финансовых операций

Проблемы:
1. **Self-custody (VISION ст.17):** если бенефициар получает депозит, это не self-custody, а временное владение
2. **SSS guardian recovery bypass:** DeadManSwitch с депозитом создаёт single-point-of-failure (бенефициар), минуя систему 2/3 guardian'ов
3. **Минимальная внешняя поверхность (VISION ст.30):** payable-функции + transfer создают reentrancy-риски
4. **BSC gas:** экономического смысла в 0.01 BNA депозите нет

## Решение

DeadManSwitch — **чистый таймер/триггер без финансовых операций**:

- Убраны: `deposits`, `receive()`, `claimFunds()`, `Ownable`, `ReentrancyGuard`
- Добавлен: `IRecoveryHook.onRecoveryExecuted` (сброс таймера при guardian recovery)
- `executeClaim()` — только emit `OwnershipClaimTriggered` (watchtower реагирует off-chain)
- `recoveryCaller` — access control для `onRecoveryExecuted` (по аналогии с SessionKeyModule)

### Ownership transfer

DeadManSwitch **не передаёт ownership напрямую** — это невозможно (требует `onlyOwner` на SmartAccount).
Вместо этого:
1. Owner не активен N дней → beneficiary зовёт `initiateClaim` → challenge period
2. Challenge period истекает → `executeClaim` → emit `OwnershipClaimTriggered`
3. Watchtower (off-chain) получает событие → инициирует guardian recovery через SocialRecoveryModule
4. Ownership переходит через штатный guardian recovery (2/3 guardians + P-256 passkey)

## Альтернативы

### Альтернатива A: Ownership transfer напрямую
- Плюсы: код проще, бенефициар получает всё сразу
- Минусы: нарушает self-custody, bypass-ит guardian recovery, требует опасных привилегий на SmartAccount
- Почему не выбрано: противоречит VISION.md и архитектуре recovery

### Альтернатива B: Депозит + transfer как было
- Плюсы: уже реализовано, есть тесты
- Минусы: reentrancy-риски, 0.01 BNA не мотивирует, bypass-ит guardians
- Почему не выбрано: экономически неэффективно и небезопасно

## Последствия

**Позитивные:**
- Self-custody сохранён — никто не может "забрать" кошелёк через таймер
- Guardian recovery (2/3) остаётся единственным путём смены ownership
- Минимальная внешняя поверхность — нет payable, нет call, нет transfer
- IRecoveryHook автоматически сбрасывает таймер при guardian recovery
- 28 unit-тестов + 3 интеграционных теста (301 total, все PASS)

**Негативные:**
- Зависимость от off-chain watchtower для реакции на `OwnershipClaimTriggered`
- Бенефициар не получает прямого доступа к кошельку

**Нейтральные:**
- Watchtower предстоит реализовать отдельно (out of scope данного ADR)

## Верификация

- 28 unit-тестов DeadManSwitch: `forge test --match-contract DeadManSwitch -vvv` — PASS
- 3 integration-теста RecoveryHooksIntegration: `forge test --match-contract RecoveryHooksIntegration -vvv` — PASS
- Полная регрессия: 301 тест, 0 failures
- `forge build` — успешно

## Ссылки

- Задача: S-147, S-146, S-145
- Файлы: `contracts/src/DeadManSwitch.sol`, `contracts/test/DeadManSwitch.t.sol`, `contracts/test/integration/RecoveryHooksIntegration.t.sol`, `contracts/script/Deploy.s.sol`
- KB: KB-SOL-016 (будет добавлен lessons-learned)
