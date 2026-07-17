# Архитектура решений — Audit Findings MDAOPay

> Дата: 2026-07-17
> Статус: DRAFT — ждёт одобрения перед имплементацией
> Автор: Coordinator (big-pickle)

---

## Порядок приоритетов

| # | Finding | Severity | Effort | Dependencies |
|---|---------|----------|--------|--------------|
| 1 | F-138a P-256 verifier swap | HIGH | S | — |
| 2 | F-156 Registry status bypass + emergency | HIGH | S | — |
| 3 | F-149 Treasury batch revert | HIGH | M | — |
| 4 | F-150 Withdraw daily cap drain | MEDIUM | S | — |
| 5 | F-151 postOp decode crash | MEDIUM | S | — |
| 6 | F-159 Price oracle manipulation | MEDIUM | M | — |
| 7 | F-153 Recovery deposit loss | MEDIUM | S | — |
| 8 | F-146 Infinite proposal retry | MEDIUM | S | — |
| 9 | F-157 Registry no grace period | MEDIUM | S | F-156 |
| 10 | F-158 Paymaster↔Registry upgrade | MEDIUM | L | — |
| 11 | F-166 SocialRecovery SRP violation | MEDIUM | L | — |
| 12 | F-167 Missing ISocialRecoveryModule | MEDIUM | S | F-166 |
| 13 | F-170 Relay ethers.js bloat | MEDIUM | M | — |
| 14 | F-172 Mixed Ownable/AccessControl | MEDIUM | M | — |
| 15 | F-142 SessionKey (BLOCKED: needs test) | HIGH | S | — (test first) |

---

## 1. F-142 HIGH (downgraded per review) — SessionKeyModule.useSessionKey заблокирован

### Важно: требует интеграционного теста ДО фикса

```solidity
// Тест определит: что реально хранится в key.owner?
// Вариант А: EOA → bug реален, delegation сломан
// Вариант Б: SmartAccount → тогда при вызове через SmartAccount.execute()
//            msg.sender == SmartAccount == key.owner, и оригинальный check КОРРЕКТЕН
function test_useSessionKey_actual_msgSender_via_entrypoint() public {
    // 1. Создать session key с owner = ?
    // 2. Собрать UserOp, подписанный session key
    // 3. EntryPoint.handleOps → SmartAccount.execute() → SessionKeyModule.useSessionKey()
    // 4. console.log(msg.sender) — кто это?
    // Только после ЭТОГО теста понятен нужен ли authorizedCallers
}
```

### Проблема (conditional on test result)
```solidity
// SessionKeyModule.sol:182
if (msg.sender != key.owner) revert Unauthorized();
```
`useSessionKey` требует `msg.sender == owner` — но dApp должен вызывать этот метод от имени пользователя (через EntryPoint или SmartAccount). Вся модульность session keys сломана.

### Решение: Authorization mapping (FIXED per planner review)

```solidity
// Storage:
mapping(address => mapping(address => bool)) public authorizedCallers;
event AuthorizedCallerUpdated(address indexed owner, address indexed caller, bool allowed);

function setAuthorizedCaller(address caller, bool allowed) external {
    authorizedCallers[msg.sender][caller] = allowed;
    emit AuthorizedCallerUpdated(msg.sender, caller, allowed);
}

function useSessionKey(bytes32 keyId, bytes32 permission, uint256 amount) external {
    SessionKey storage key = sessionKeys[keyId];
    if (key.owner == address(0)) revert InvalidKey();
    
    // F-142 FIX: owner ИЛИ авторизованный caller
    // Ponytail: одна строка вместо вложенного if/else (planner review)
    require(
        msg.sender == key.owner || authorizedCallers[key.owner][msg.sender],
        "Unauthorized"
    );
    
    if (key.revoked) revert SessionKeyRevoked();
    if (block.timestamp < key.validAfter || block.timestamp > key.validUntil) revert SessionKeyExpired();
    if (!_hasPermission(key, permission)) revert PermissionDenied();
    // ...остальное без изменений
}
```

**Дополнительно**: добавить mapping `mapping(address => mapping(address => bool)) public authorizedCallers;` с функцией `setAuthorizedCaller(address smartAccount, bool allowed)` accessible only by key owner.

### Почему именно так
- **Альтернатива A**: Убрать check entirely → небезопасно, любой может тратить
- **Альтернатива B**: Whitelist SmartAccount адресов → требует хранения, но даёт owner'у контроль
- **Альтернатива C**: `key.owner` хранить SmartAccount, а не EOA → ломает текущий flow (registerWallet привязан к EOA)
- **Выбор B** — баланс: owner контрольирует какие SmartAccount'ы могут вызывать, минимальные изменения в storage layout

### Стоимость
~3K gas на проверку mapping + 1 storage slot на SmartAccount mapping

---

## 2. F-138a HIGH — setP256Verifier без таймлока

### Проблема
```solidity
// SocialRecoveryModule.sol:123
function setP256Verifier(address _p256Verifier) external onlyOwner {
```
Owner может в любой момент подменить P-256 verifier на MockP256 (который принимает любой proof). Если ключ owner'а украден — полный компромисс системы рекавери.

### Решение: Timelock + Pending + Cancel

```solidity
address public pendingP256Verifier;
uint256 public pendingVerifierSetAt;
uint256 public constant VERIFIER_TIMELOCK = 48 hours;

function setP256Verifier(address _p256Verifier) external onlyOwner {
    require(_p256Verifier != address(0), "Invalid verifier");
    pendingP256Verifier = _p256Verifier;
    pendingVerifierSetAt = block.timestamp;
    emit P256VerifierProposed(_p256Verifier, block.timestamp); // community monitoring
}

function confirmP256Verifier() external onlyOwner {
    require(pendingP256Verifier != address(0), "No pending verifier");
    require(block.timestamp >= pendingVerifierSetAt + VERIFIER_TIMELOCK, "Timelock not elapsed");
    address old = P256_VERIFIER;
    P256_VERIFIER = pendingP256Verifier;
    delete pendingP256Verifier;
    delete pendingVerifierSetAt;
    emit P256VerifierUpdated(old, pendingP256Verifier);
}

// F-138a FIX: cancel mechanism (planner review — edge case)
function cancelP256VerifierUpdate() external onlyOwner {
    delete pendingP256Verifier;
    delete pendingVerifierSetAt;
    emit P256VerifierUpdateCancelled();
}
```

### Почему именно так
- **Альтернатива**: Governance vote → слишком тяжело для инфраструктурного изменения (нужно 3 дня + кворум)
- **Альтернатива**: Убрать функцию entirely → невозможно обновлять верификатор (нужно деплоить новый модуль)
- **Выбор Timelock 48h** — совпадает с таймлкомом рекавери (TIMELOCK=48h), что логично: если атакующий ставит MockP256, у легитимного owner'а есть 48h на反应

### Стоимость
~5K gas (2 storage writes) + 1 slot для pendingP256Verifier + 1 для pendingVerifierSetAt

---

## 3. F-156 HIGH — TrustProviderRegistry ACTIVE→SUNSET напрямую

### Проблема
```solidity
// TrustProviderRegistry.sol:40-45
function setProviderStatus(bytes32 providerId, ProviderStatus status) external onlyOwner {
```
Принимает любой ProviderStatus без проверки переходов. Owner может переключить ACTIVE→SUNSET, минуя DEPRECATED. Пользователи с pending UserOps теряют EntryPoint deposits.

### Решение: State Machine с валидацией переходов + Emergency Bypass

```solidity
function setProviderStatus(bytes32 providerId, ProviderStatus status) external onlyOwner {
    if (providers[providerId].verifier == address(0)) revert ProviderNotRegistered();
    ProviderStatus oldStatus = providers[providerId].status;
    
    // F-156 FIX: валидация переходов (planner review — better error messages)
    if (oldStatus == ProviderStatus.ACTIVE && status != ProviderStatus.DEPRECATED) {
        revert InvalidTransition("ACTIVE→DEPRECATED required");
    }
    if (oldStatus == ProviderStatus.DEPRECATED && status != ProviderStatus.SUNSET) {
        revert InvalidTransition("DEPRECATED→SUNSET required");
    }
    if (oldStatus == ProviderStatus.SUNSET) {
        revert InvalidTransition("SUNSET is terminal");
    }
    
    providers[providerId].status = status;
    emit ProviderStatusUpdated(providerId, oldStatus, status);
}

// F-156 FIX: emergency bypass для скомпрометированных провайдеров
// Обходит grace period (F-157) — только для confirmed compromise
address public emergencyGuardian;

function emergencySunset(bytes32 providerId) external {
    require(msg.sender == emergencyGuardian, "Not emergency guardian");
    ProviderStatus oldStatus = providers[providerId].status;
    require(oldStatus != ProviderStatus.SUNSET, "Already sunset");
    
    providers[providerId].status = ProviderStatus.SUNSET;
    emit ProviderEmergencySunset(providerId, oldStatus, msg.sender);
}
```

### Почему именно так
- **Альтернатива A**: Timelock на каждую смену → overkill, DEPRECATED уже является "мягким" шагом
- **Альтернатива B**: Разрешить ACTIVE→SUNSET с warning event → всё равно рискует потерю средств
- **Альтернатива C (отвергнута)**: Только strict state machine → не даёт emergency path для скомпрометированного провайдера
- **Выбор** — strict state machine для штатного использования + emergencySunset для incident response. Emergency guardian = multisig, separate от owner

### Стоимость
~200 gas (branching) — пренебрежимо мало

---

## 4. F-149 HIGH — Treasury batch revert

### Проблема
```solidity
// Treasury.sol:140-148
for (uint256 i; i < recipients.length; i++) {
    alloc.amounts[i] = 0;
    if (token == address(0)) {
        (bool sent, ) = recipients[i].call{value: amounts[i]}("");
        if (!sent) revert ErrTransferFailed();  // ← ВЕСЬ БАТЧ
    } else {
        IERC20(token).safeTransfer(recipients[i], amounts[i]);  // ← ВЕСЬ БАТЧ
    }
}
```
Один отказавший recipient (blacklisted address, full wallet, contract без receive) → все 200 получателей не получают ничего.

### Решение: Pull Pattern (Partial Execution)

```solidity
function executeAllocation(bytes32 id) external {
    Allocation storage alloc = _allocations[id];
    // ...checks...
    alloc.executed = true;
    
    address[] memory recipients = alloc.recipients;
    uint256[] memory amounts = alloc.amounts;
    address token = alloc.token;
    
    uint256 totalFailed;
    uint256 failedCount;
    
    for (uint256 i; i < recipients.length; i++) {
        alloc.amounts[i] = 0;
        bool success;
        
        if (token == address(0)) {
            (success, ) = recipients[i].call{value: amounts[i]}("");
        } else {
            // F-149 FIX: boolean return check (USDT-style false return, same as F-004)
            (bool callOk, bytes memory data) = token.call(
                abi.encodeWithSelector(IERC20.transfer.selector, recipients[i], amounts[i])
            );
            success = callOk && (data.length == 0 || abi.decode(data, (bool)));
        }
        
        if (!success) {
            totalFailed += amounts[i];
            failedCount++;
            emit TransferFailed(id, i, recipients[i], amounts[i]);
            // Ponytail: amounts[i] already zeroed, funds stay in Treasury for re-distribution
        }
    }
    
    // F-149 FIX: rate limit as monitoring metric (post-loop), NOT as require inside loop
    // require inside loop would revert ALL successful transfers on threshold breach
    if (failedCount * 100 / recipients.length > 10) {
        emit HighFailureRateWarning(id, failedCount, recipients.length);
    }
    
    if (totalFailed > 0) {
        emit PartialAllocationExecuted(id, totalFailed);
    } else {
        emit AllocationExecuted(id);
    }
}
```

### Почему именно так
- **Альтернатива A**: Pull pattern (каждый получатель сам claim'ит) → требует нового UX, callbacks, сроков. Радикально меняет архитектуру Treasury
- **Альтернатива B**: Split into smaller batches → не решает проблему, просто уменьшает blast radius
- **Альтернатива C**: Try/catch per transfer → это и есть предложенное решение
- **Выбор C** — минимальные изменения (try/catch вместо прямого call),失败者 остаются в Treasury для повторного распределения

### Стоимость
~500 gas на failed transfer (empty returndata check), +1 event per failure. Storage не растёт — amounts[i] уже обнулён.

---

## 5. F-150 MEDIUM — WithdrawTo daily cap drain

### Проблема
```solidity
// MDAOPaymaster.sol:674-682
if (block.timestamp > dailyWithdrawalResetAt) {
    dailyWithdrawnToday = 0;
    dailyWithdrawalResetAt = block.timestamp + 1 days;
}
uint256 balance = /* ... */;
if (dailyWithdrawnToday + amount > balance * dailyWithdrawalCapBps / 10000) revert DailyCapExceeded();
dailyWithdrawnToday += amount;
```
Balance пересчитывается каждый вызов. При cap=500 (5%) owner может 20 раз вызвать withdrawTo, каждый раз получая 5% от уменьшающегося баланса → ~64% drain за один день.

### Решение: Snapshot баланса на начало дня

```solidity
uint256 public dailyBalanceSnapshot; // snapshot at day start

function withdrawTo(address token, address to, uint265 amount) external onlyOwner {
    if (to == address(0)) revert NoTransferToZeroAddress();
    
    if (block.timestamp > dailyWithdrawalResetAt) {
        dailyWithdrawnToday = 0;
        dailyBalanceSnapshot = token == address(0)
            ? IEntryPointView(entryPoint).balanceOf(address(this))
            : IERC20(token).balanceOf(address(this));
        dailyWithdrawalResetAt = block.timestamp + 1 days;
    }
    
    // F-150 FIX: use snapshot, not live balance
    uint256 cap = dailyBalanceSnapshot * dailyWithdrawalCapBps / 10000;
    if (dailyWithdrawnToday + amount > cap) revert DailyCapExceeded();
    dailyWithdrawnToday += amount;
    
    // ...rest unchanged
}
```

### Почему именно так
- **Альтернатива A**: Hard cap per call (e.g., max 1 withdraw/day) → слишком ограничительно, owner может легитимно дробить
- **Альтернатива B**: Nonce-based (один withdraw per day) → тоже слишком жёстко
- **Альтернатива C**: Snapshot баланса → определяет cap в начале дня, все withdraw'и в течение дня из одного "пула"
- **Выбор C** — один storage read + 1 write в начале дня. Owner всё ещё может сделать несколько withdraw'ов, но cap фиксирован на snapshot. Геометрическая серия обрывается.

### Стоимость
~5K gas (1 extra SLOAD в начале дня)

---

## 6. F-151 MEDIUM — postOp context decode crash

### Проблема
```solidity
// MDAOPaymaster.sol:526-527
(address sender, IERC20 token, uint256 maxTokenAmount) =
    abi.decode(context, (address, IERC20, uint256));
```
Без try/catch. Malformed context (от бага в SDK, мусорные данные) → revert → весь UserOp откатывается. Пользователь платит за газ, но транзакция не проходит.

### Решение: Try/catch с graceful fallback

```solidity
function postOp(
    PostOpMode mode,
    bytes calldata context,
    uint256 actualGasCost,
    uint256
) external onlyEntryPoint whenNotPaused {
    // F-151 FIX: safe decode
    (bool decoded, address sender, IERC20 token, uint256 maxTokenAmount) = _tryDecodeContext(context);
    if (!decoded) {
        // Malformed context — emit event for monitoring, don't revert
        emit MalformedContext(context);
        return;
    }
    
    if (mode == PostOpMode.opReverted) return;
    // ...rest unchanged
}

function _tryDecodeContext(bytes calldata context) internal pure returns (
    bool success, address sender, IERC20 token, uint256 maxTokenAmount
) {
    try this.decodeContext(context) returns (address s, IERC20 t, uint256 m) {
        return (true, s, t, m);
    } catch {
        return (false, address(0), IERC20(address(0)), 0);
    }
}

// External self-call to enable try/catch on abi.decode
function decodeContext(bytes calldata context) external pure returns (
    address sender, IERC20 token, uint256 maxTokenAmount
) {
    (sender, token, maxTokenAmount) = abi.decode(context, (address, IERC20, uint256));
}
```

### Почему именно так
- **Альтернатива**: Просто return → потеря средств (paymaster не получит оплату)
- **Альтернатива**: Менее строгий decode → невозможно, формат фиксирован
- **Выбор self-call try/catch** — стандартный паттерн в Solidity для safe abi.decode. Paymaster теряет оплату за этот UserOp, но не блокирует整个 EntryPoint pipeline

### Стоимость
~3K gas (external self-call overhead). Только при malformed context — штатный путь без изменений.

---

## 7. F-159 MEDIUM — Price oracle manipulation

### Проблема
`tokenPrice[token]` — owner-settable, ±2% per call, 15min cooldown, ±10% hard cap. Но это off-chain oracle (owner обновляет вручную). Flash loan attacker может:
1. Манипулировать DEX-ценой (если owner использует DEX как reference)
2. Вызвать UserOp с завышенным `maxTokenAmount` пока цена устарела

### Решение: TWAP + Median (on-chain)

```solidity
// Добавить в MDAOPaymaster:
uint256[5] public priceHistory; // circular buffer
uint8 public priceHistoryIdx;
uint8 public priceHistoryCount; // F-159 FIX: track fill level
uint8 public constant MIN_PRICE_SOURCES = 3;

function setTokenPrice(address token, uint256 price) external onlyOwner {
    // ...existing checks...
    
    // F-159 FIX: store in circular buffer, validate against median ONLY when full
    priceHistory[priceHistoryIdx] = price;
    priceHistoryIdx = (priceHistoryIdx + 1) % 5;
    if (priceHistoryCount < 5) priceHistoryCount++;
    
    // Only validate when buffer is FULL — avoids warmup bias from zero-filled slots
    if (priceHistoryCount == 5) {
        uint256 median = _getMedian();
        uint256 deviation = price > median ? price - median : median - price;
        if (deviation * 10000 / median > maxDeviationBps) revert PriceChangeTooHigh();
    }
    
    tokenPrice[token] = price;
    // ...
}

function _getMedian() internal view returns (uint256) {
    // Sort and return middle value
    uint256[5] memory sorted = priceHistory;
    // Simple insertion sort for 5 elements
    for (uint i = 1; i < 5; i++) {
        uint256 key = sorted[i];
        uint j = i;
        while (j > 0 && sorted[j-1] > key) {
            sorted[j] = sorted[j-1];
            j--;
        }
        sorted[j] = key;
    }
    return sorted[2]; // median of 5
}
```

### Почему именно так
- **Альтернатива A**: Chainlink oracle → требует интеграции, external dependency, не все токены имеют feed
- **Альтернатива B**: Uniswap TWAP → requires on-chain DEX, gas-heavy, манипулируем в small caps
- **Альтернатива C**: Median из истории цен → простой, on-chain, без external deps
- **Выбор C** — owner всё ещё primary source, но median filter блокирует единичные выбросы. Для BSC (дешёвый газ) — 5 SLOAD'ов пренебрежимы

### Стоимость
~15K gas (5 SLOAD + insertion sort). Только при обновлении цены.

---

## 8. F-153 MEDIUM — Recovery deposit loss

### Проблема
Initiator recovery deposits 0.01 MDAO. Если recovery veto'ирован или истёк таймаут → deposit сжигается (anti-spam). Но legitimate initiator, чья recovery была отменена по причинам вне его контроля (guardians передумали), теряет средства без recourse.

### Решение: Refund to initiator на cleanup

```solidity
function cleanupExpiredRecovery(address wallet) external {
    RecoveryRequest storage req = pendingRecovery[wallet];
    if (req.startedAt == 0) revert ErrNoActiveRecovery();
    if (req.executed) revert ErrRecoveryExecuted();
    if (block.timestamp <= req.startedAt + TIMELOCK + EXECUTION_WINDOW) revert ErrNoExpiredRecovery();

    uint256 deposit = recoveryDeposit[wallet];
    address initiator = req.initiator;
    recoveryDeposit[wallet] = 0;
    delete pendingRecovery[wallet];

    // F-153 FIX: refund deposit to initiator
    // BUG FIX: вызов transfer() НА ТОКЕН-КОНТРАКТЕ, а не на initiator (EOA)
    if (deposit > 0) {
        (bool sent, bytes memory data) = address(mdaoToken).call(
            abi.encodeWithSelector(IERC20.transfer.selector, initiator, deposit)
        );
        // USDT-style false-return handling (F-004 pattern from MDAOPaymaster)
        bool success = sent && (data.length == 0 || abi.decode(data, (bool)));
        if (!success) {
            MDAOToken(address(mdaoToken)).burn(deposit);
        }
    }

    emit RecoveryCleanedUp(wallet, deposit);
}
```

### Почему именно так
- **Альтернатива A**: Убрать deposit entirely → открывает spam vector (ноль成本 запуск recovery)
- **Альтернатива B**: Refund always → если initiator — attacker, он ничего не теряет
- **Альтернатива C**: Refund only if not executed + not vetoed → нужна additional state tracking
- **Выбор C (modified)** — refund на cleanup (истёк timeout) с fallback на burn. Veto всё ещё сжигает deposit (attacker penalty). Timeout refund — потому что initiator мог действовать в good faith, а guardians simply не отреагировали вовремя.

### Стоимость
~5K gas (refund transfer). Если refund fails — burn как раньше.

---

## 9. F-146 MEDIUM — Infinite proposal retry

### Проблема
```solidity
// Proposal.sol:117-140
function retryProposal(uint256 previousProposalId, string calldata description) external returns (uint256) {
    // ...checks that prev was cancelled or failed...
    // NO LIMIT on how many times you can retry
}
```
Проппозал можно retry бесконечно (cancelled → retry → cancel → retry → ...). Это spam vector и governance fatigue.

### Решение: Max retries + cooldown

```solidity
// В ProposalData struct добавить:
uint8 retryCount;

// В Proposal.sol:
uint8 public constant MAX_RETRIES = 3;
uint256 public constant RETRY_COOLDOWN = 1 days;

function retryProposal(uint256 previousProposalId, string calldata description)
    external returns (uint256 proposalId)
{
    ProposalData storage prev = _proposals[previousProposalId];
    if (prev.deadline == 0) revert ErrInvalidProposal();
    
    bool isFailed = !prev.executed && !prev.cancelled
        && block.timestamp > prev.deadline
        && prev.forVotes > prev.againstVotes;
    if (!prev.cancelled && !isFailed) revert ErrNotCancelled();
    
    // F-146 FIX: retry limit
    if (prev.retryCount >= MAX_RETRIES) revert ErrMaxRetriesExceeded();
    
    // F-146 FIX: cooldown after cancel (not after failure)
    if (prev.cancelled && prev.deadline + RETRY_COOLDOWN > block.timestamp) {
        revert ErrRetryCooldown();
    }
    
    if (!hasRole(FINANCE_ROLE, msg.sender) && msg.sender != prev.proposer) revert ErrUnauthorized();
    
    proposalId = ++proposalCount;
    ProposalData storage p = _proposals[proposalId];
    p.allocId = prev.allocId;
    p.proposer = msg.sender;
    p.deadline = block.timestamp + VOTING_PERIOD;
    p.description = description;
    p.retryCount = prev.retryCount + 1; // inherited
    
    emit ProposalCreated(proposalId, prev.allocId, msg.sender, p.deadline, description);
}
```

### Почему именно так
- **Альтернатива A**: Убрать retry entirely → слишком жёстко, legitimate retry (proposal failed из-за бага в alloc) нужен
- **Альтернатива B**: Only proposer can retry → не решает spam
- **Альтернатива C**: MAX_RETRIES=3 + cooldown → баланс: 3 попытки достаточны для legitimate cases, cooldown предотвращает rapid-fire spam
- **Выбор C** — простой, ~10 строк. 3 retry × 3 дня = 9 дней maximum для одного alloc. Этого достаточно.

### Стоимость
~200 gas (1 extra SLOAD retryCount, 1 SSTORE на incremented value)

---

## 10. F-157 MEDIUM — TrustProviderRegistry no grace period

### Проблема
DEPRECATED→SUNSET происходит мгновенно. Если есть pending UserOps с deprecated provider, EntryPoint deposits теряются.

### Решение: Минимальный DEPRECATED duration

```solidity
uint256 public constant MIN_DEPRECATED_PERIOD = 7 days;
mapping(bytes32 => uint256) public deprecatedAt;

function setProviderStatus(bytes32 providerId, ProviderStatus status) external onlyOwner {
    // ...existing transition validation from F-156...
    
    if (oldStatus == ProviderStatus.ACTIVE && status == ProviderStatus.DEPRECATED) {
        deprecatedAt[providerId] = block.timestamp; // F-157 FIX
    }
    
    if (oldStatus == ProviderStatus.DEPRECATED && status == ProviderStatus.SUNSET) {
        require(
            block.timestamp >= deprecatedAt[providerId] + MIN_DEPRECATED_PERIOD,
            "Grace period not elapsed"
        );
    }
    
    // ...rest
}
```

### Почему именно так
- **Альтернатива**: Фиксированный grace period на контракте → уже так и сделано (7 дней)
- **Альтернатива**: Governance vote для SUNSET → overkill для инфраструктурного изменения
- **Выбор** — 7 дней совпадает с typical UserOp lifecycle. EntryPoint deposits в BSC ~48h, 7 дней — с запасом.

### Стоимость
~5K gas (1 SSTORE для deprecatedAt)

---

## 11. F-158 MEDIUM — Paymaster↔Registry upgrade coordination

### Проблема
MDAOPaymaster хранит `registry` address (immutable). Если TrustProviderRegistry обновлён (новый деплой), paymaster не может переключиться без нового контракта.

### Решение: Paymaster registry address не immutable

```solidity
// В MDAOPaymaster.sol: registry должен быть settable
address public registry; // не immutable

function setRegistry(address _registry) external onlyOwner {
    require(_registry != address(0), "Invalid registry");
    address old = registry;
    registry = _registry;
    emit RegistryUpdated(old, _registry);
}
```

**Важно**: сейчас `registry` immutable в конструкторе. Это требует redeploy MDAOPaymaster.

### Почему именно так
- **Альтернатива**: Proxy pattern → radical change, adds complexity, upgradeability concerns
- **Альтернатива**: Leave as immutable →每次 registry update requires paymaster redeploy + user migration
- **Выбор** — settable registry, simple. Owner controls upgrade path. Event for monitoring.

### Стоимость
Minimal — registry уже в storage, просто removing `immutable`.

---

## 12. F-166 MEDIUM — SocialRecoveryModule SRP violation (618 lines, 8+ concerns)

### Проблема
SocialRecoveryModule отвечает за:
1. P-256 WebAuthn verification
2. Guardian management
3. Recovery initiation + timelock
4. Approval/veto logic
5. Recovery execution + ownership transfer
6. Hook system (whitelist, execution, gas limits)
7. Deposit management
8. DER→raw signature conversion

### Решение: Декомпозиция на 3 модуля (long-term)

**Не сейчас.** Это large refactor, который ломает deployment и тесты.

**Рекомендация**: зафиксировать как tech debt (F-166) и декомпозировать в v0.2:

```
SocialRecoveryModule (orchestrator, ~200 lines)
├── GuardianManager.sol (~150 lines) — add/remove/approve/veto
├── RecoveryExecutor.sol (~150 lines) — timelock, execution, hooks
└── PasskeyVerifier.sol (~80 lines) — P-256, DER conversion
```

### Почему именно так
- **Альтернатива**: Разбить сейчас → ломает 15+ тестов, deployment scripts, gas estimates
- **Альтернатива**: Оставить как есть → tech debt растёт, но работает
- **Выбор**: Fix в v0.1 (оставить monolith), decompose в v0.2 с migration plan

---

## 13. F-167 MEDIUM — Missing ISocialRecoveryModule interface

### Решение: Добавить interface

```solidity
interface ISocialRecoveryModule {
    function registerWallet(bytes32 passkeyPubKeyX, bytes32 passkeyPubKeyY) external;
    function initiateRecovery(address wallet, bytes32 newPasskeyPubKeyX, bytes32 newPasskeyPubKeyY, address smartAccount, address newOwner) external;
    function approveRecovery(address wallet) external;
    function vetoRecovery(address wallet) external;
    function executeRecovery(address wallet) external;
    function cleanupExpiredRecovery(address wallet) external;
    function addGuardian(address guardian) external;
    function removeGuardian(address guardian) external;
    // ...view functions
}
```

### Почему именно так
- **Причина**: SessionKeyModule и DeadManSwitch уже implement IRecoveryHook, но SocialRecoveryModule сам не имеет interface. Это нарушение полиморфизма — если появится вторая реализация recovery, её нельзя подставить.

### Стоимость
Нулевая — interface-only change, no storage, no gas impact.

---

## 14. F-170 MEDIUM — Relay ethers.js bloat

### Проблема
Cloudflare Workers bundle включает ethers.js (~150KB). CF Workers limit — 10MB, но bundle size直接影响冷启动.

### Решение: Замена на viem

```typescript
// Вместо:
import { ethers } from "ethers";
const signer = new ethers.Wallet(key);

// Использовать:
import { privateKeyToAccount } from "viem/accounts";
import { signMessage } from "viem";
const account = privateKeyToAccount(key);
const sig = await signMessage({ message, account });
```

### Почему именно так
- **Альтернатива A**: ethers v6 tree-shaking → ethers v6 уже tree-shakeable, но bundle всё ещё ~80KB
- **Альтернатива B**: @ethersproject/signing-key (minimal) → нужен ещё几个人工 package
- **Альтернатива C**: viem → ~20KB, tree-shakeable, modern API, actively maintained
- **Выбор C** — viem is the standard for CF Workers. Smaller bundle, better DX, same security.

### Стоимость
M (refactor all relay files). 6 files to update. No runtime cost difference.

---

## 15. F-172 MEDIUM — Mixed Ownable/AccessControl

### Проблема
- SocialRecoveryModule, TrustProviderRegistry: `Ownable`
- Treasury: `AccessControl` + `Ownable`
- MDAOPaymaster: `Ownable`
- Proposal: no access control (public)

### Решение: Единообразие в v0.2

**Не сейчас.** Changing access control in deployed contracts is dangerous.

**Рекомендация**: в v0.2 все контракты перейти на AccessControl (role-based), кроме DeadManSwitch и RefundVault (простые enough для Ownable).

### Почему именно так
- Менять access control в рабочем контракте = potential privilege escalation
- Всё работает сейчас, mixed approach не создаёт vulnerabilities (просто неудобно)
- v0.2 будет иметь deployment migration anyway

---

## 16. Emergency Pause (NEW — planner proposal)

### Проблема
Individual fixes защищают от конкретных атак, но нет единого circuit breaker для未知/combined векторов.

### Решение: Multisig Emergency Pause

```solidity
// В каждом критичном контракте:
bool public emergencyPaused;
uint256 public emergencyPausedAt;
address public emergencyGuardian; // multisig
uint256 public constant EMERGENCY_MAX_DURATION = 24 hours;

// F-149 FIX: modifier ОБЪЯВЛЁН (user review — undefined modifier)
modifier onlyEmergencyGuardian() {
    require(msg.sender == emergencyGuardian, "Not emergency guardian");
    _;
}

// Auto-expiry: пауза больше 24h автоматически деактивируется
modifier whenNotEmergencyPaused() {
    require(
        !emergencyPaused || block.timestamp > emergencyPausedAt + EMERGENCY_MAX_DURATION,
        "Emergency paused"
    );
    _;
}

function emergencyPause() external onlyEmergencyGuardian {
    emergencyPaused = true;
    emergencyPausedAt = block.timestamp; // F-149 FIX: timestamp для auto-expiry
    emit EmergencyPaused(msg.sender);
}

function emergencyUnpause() external onlyEmergencyGuardian {
    emergencyPaused = false;
    emit EmergencyUnpaused(msg.sender);
}
```

**Контракты с emergency pause**: SocialRecoveryModule, MDAOPaymaster, TrustProviderRegistry, Treasury

### Почему именно так
- **Альтернатива**: Owner-only pause → single point of failure
- **Альтернатива**: Governance vote → слишком медленно для emergencies
- **Выбор**: Multisig guardian — баланс speed vs decentralization. Auto-expiry через 24h предотвращает permanent censorship.

### Стоимость
~1K gas per call (SSTORE bool). Добавить в 4 контракта = ~4K gas на deploys.

---

## Порядок имплементации (UPDATED per user review)

### Phase 1: Critical fixes (all independent, parallel OK)
1. F-138a (P-256 timelock + cancel) — ~25 строк
2. F-156 (Registry state machine + emergencySunset) — ~20 строк
3. F-149 (Treasury partial execution + boolean check) — ~25 строк

### Phase 2: Medium (sequential)
4. F-150 (Withdraw snapshot) — ~10 строк
5. F-151 (postOp decode) — ~20 строк
6. F-153 (Deposit refund → correct address) — ~10 строк
7. F-146 (Retry limit) — ~15 строк
8. F-157 (Grace period, depends on F-156) — ~10 строк
9. F-158 (Registry settable) — ~10 строк

### Phase 3: Deferred to v0.2
10. F-159 (TWAP oracle + priceHistoryCount) — ~50 строк
11. F-166/F-167 (SRP decomposition + interface) — ~200 строк
12. F-170 (viem migration) — ~6 files refactor
13. F-172 (AccessControl unification) — risk, deferred

### BLOCKED: requires integration test
14. F-142 (SessionKey delegation) — ~20 строк IF test confirms bug

### Total Phase 1+2: ~165 строк Solidity
### Tests: ~300 строк (new test cases for each fix)
