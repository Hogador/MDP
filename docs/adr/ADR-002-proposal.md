# ADR-002: Proposal Contract (DAO голосование)

## Статус
Принято

## Контекст
DAO требуется механизм создания, голосования и исполнения предложений.
Без Proposal невозможно управление казной (Treasury) — распределение средств
должно проходить через голосование держателей токена MDAOToken.

## Решение
Создан контракт `Proposal.sol` с интеграцией Treasury:
- **createProposal(allocId, description)** — FINANCE_ROLE создаёт предложение, привязывая allocation из Treasury
- **vote(proposalId, support)** — голосование токенами MDAOToken (For/Against/Abstain)
- **executeProposal(proposalId)** — после deadline: проверка кворума (4% totalSupply) и победы For → вызов Treasury.executeAllocation
- **cancelProposal(proposalId)** — FINANCE_ROLE, только до deadline

### Параметры
- Voting period: 3 дня (блоки)
- Quorum: 4% от totalSupply MDAOToken (forVotes + againstVotes)
- Execution delay: 0 (исполнение сразу после победы)
- Вес голоса = баланс MDAOToken на момент голосования

## Альтернативы
1. **OpenZeppelin Governor** — отклонён (избыточен, требует дополнительных контрактов, сложная конфигурация)
2. **Snapshot off-chain + on-chain execute** — отклонён (VISION требует on-chain голосование)
3. **Tally integration** — избыточен для текущей стадии

## Последствия
- FINANCE_ROLE из Treasury используется и в Proposal (единый источник истины)
- При executeProposal → treasury.executeAllocation(allocId)
- 28 unit-тестов покрывают все сценарии
- Потребуется upgrade для более сложных сценариев (delegation, voting power)

## VISION Compliance
- ✅ Голосование = on-chain tx — п.2 "Управление | On-chain DAO"
- ✅ Только FINANCE_ROLE создаёт proposal — п.4.3 (multisig)
- ✅ CEI-паттерн, ReentrancyGuard

## Ссылки
- [F-003] ROADMAP: Proposal contract
- [ADR-001] Treasury
- [Proposal.sol] contracts/src/Proposal.sol
