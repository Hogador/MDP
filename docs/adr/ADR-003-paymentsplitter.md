# ADR-003: PaymentSplitter (рекуррентные платежи)

## Статус
Принято

## Контекст
DAO требует механизм recurrent-платежей: "30% дохода — dev-фонду, 70% — операциям".
В отличие от Treasury.allocation (разовая выплата по голосованию), PaymentSplitter
позволяет настроить доли и многократно получать платежи.

## Решение
Создан контракт `PaymentSplitter.sol`:
- **initialize(payees, shares)** — однократная установка получателей и долей (admin)
- **release(token)** — отправляет получателю его долю накопленных средств
- Поддерживает ETH и любые ERC-20

### Интеграция с Proposal → Treasury
1. Proposal голосует за финансирование PaymentSplitter
2. Treasury.executeAllocation отправляет токены в PaymentSplitter
3. Получатели вызывают release() в любой момент

## Альтернативы
1. **OZ PaymentSplitter** — не установлен в lib, зависимости не синхронизированы
2. **Простая эмуляция через Treasury.allocation** — не даёт recurrent-механизма

## Последствия
- Каждый PaymentSplitter — независимый экземпляр с одной конфигурацией долей
- Для разных схем распределения — разные экземпляры
- 22 unit-теста, включая интеграционные с Treasury + Proposal

## VISION Compliance
- ✅ Нет admin-функций после инициализации (автономный)
- ✅ CEI-паттерн, ReentrancyGuard
- ✅ No tx.origin, no inline assembly

## Ссылки
- [F-004] ROADMAP
- [ADR-001] Treasury
- [ADR-002] Proposal
- [PaymentSplitter.sol] contracts/src/PaymentSplitter.sol
