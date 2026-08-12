# EXECUTION-RESULTS — Журнал результатов реализации (для внешней проверки)

> Назначение: фиксировать результат КАЖДОГО шага S1–S29 по плану
> `docs/plans/PLAN-IMPLEMENTATION-TESTNET.md` (TD-01…TD-20 + TD-01a).
> Формат — самодостаточный, пригоден для независимой верификации.
> Каждый шаг: Задача → Что сделано → Файлы → Верификация → Self-challenge.

---

## S1 — Карта делегирования S1–S29 (planner, верифицирована)

- **Задача**: разбить 22 задачи на 29 операций с учётом ограничений моделей (big-pickle нестабилен → 7 вызовов, разнос ≥2 шага; deepseek → coder; nemotron → security).
- **Результат**: карта S1–S29, все 4 проверки planner'а зелёные (a) разнос big-pickle ≥2 шага между вызовами; (b) зависимости: 0.2>0.1, 4.3>3.2, 5.1>фаз 0–4; (c) ровно 7 big-pickle; (d) 29 операций.
- **Файлы**: `docs/plans/PLAN-IMPLEMENTATION-TESTNET.md` (строки 611–722).
- **Статус**: ✅ завершено, одобрено пользователем.

---

## S2 — Шаг 0.1: даунгрейд account-abstraction lib v0.9.0 → v0.6.0 + MDAOSmartAccount на v0.6 API (TD-01a)

- **Задача**: консолидация EntryPoint v0.6. План предполагал diff «2 строки», coder обнаружил: lib `contracts/lib/account-abstraction` = **v0.9.0 (v0.7 API)**, `UserOperation.sol` физически отсутствует → сборка невозможна. Coder остановился, откатил, сообщил (протокол «остановись и спроси»).
- **Решение (одобрено пользователем)**: Вариант A — даунгрейд lib до v0.6.0 (тег существует на GitHub), единообразие стека: 100% остального (Paymaster, backend, app, задеплоенный EntryPoint v0.6 на BSC 0x5FF137...) уже v0.6. Против B (локальный struct): тесты останутся на v0.7-Mock → «тесты зелёные, потому что тестируют не то, что деплоится». Против C (оставить v0.7): ABI mismatch гарантирован.
- **Что сделано**:
  1. lib заменена на тег v0.6.0 (vendor-copy, без git): `UserOperation.sol` появился, `PackedUserOperation.sol` удалён, `ISenderCreator` отсутствует.
  2. `MDAOSmartAccount.sol` (диф 11 строк): `import {UserOperation}`; `_validateSignature(UserOperation calldata, bytes32)`; `SIG_VALIDATION_SUCCESS` → `0` (в v0.6 BaseAccount только `SIG_VALIDATION_FAILED=1`); `recoveryCaller`/`onlyOwner` не тронуты.
  3. `MDAOSmartAccount.t.sol` (диф 49 строк): `_dummyUserOp()`, `_hashUserOp()`, `MockEntryPoint` → v0.6; удалён `test_ExecuteRevertsNotEntryPoint` (execute — v0.7-фича SimpleAccount); убран ISenderCreator.
  4. `lib/account-abstraction/VENDORED_VERSION.md`: «PINNED: v0.6.0 (vendored manually, do NOT run forge update lib/account-abstraction)».
  5. `.gitmodules`: удалена рудиментная запись про account-abstraction (в HEAD `040000 tree`, gitlink отсутствует — это vendor-copy, не submodule).
- **Верификация**:
  - `forge build` ✅
  - `forge test` MDAOSmartAccount: 16/16 PASS (2 прогона) ✅
  - `forge test` всего: 406 passed / 4 failed — **pre-existing**, не связаны с даунгрейдом (MDAOToken.t.sol cooldown ×2, SmokeScenarios.t.sol PAY_ECONOMICS_B price-cooldown, fuzz MDAOPaymaster QuoteDeadline). Доказательство: эти файлы не импортируют account-abstraction (grep), ошибки тайминговые/экономические.
  - grep `PackedUserOperation` по contracts/ (вне vendor): **0 совпадений** ✅
- **Коммит**: `b36b593` (178 файлов: lib v0.6.0 + 2 исходника + .gitmodules).
- **Self-challenge**: 4 failed теста квалифицированы pre-existing логически, не эмпирически (оригинальное состояние lib невосстановимо без переклонирования). lib v0.6.0 сверена по package.json + структуре интерфейсов, не по каждому файлу.
- **Статус**: ✅ завершено, одобрено пользователем.

---

(журнал продолжается — каждый завершённый шаг добавляется в конец)

## S3 — Шаг 0.2: деплой MDAOSmartAccountFactory (TD-02)

- **Задача**: добавить деплой MDAOSmartAccountFactory в Deploy.s.sol. Разведка: фабрики не существовало (grep пуст), `MDAOSmartAccount` в скрипте не деплоился (только paymaster), ENTRY_POINT читается из env на строке 60.
- **Что сделано**:
  1. Новый `contracts/src/MDAOSmartAccountFactory.sol` (49 строк): `createAccount(owner, salt)` + `getAddress(owner, salt)` — CREATE2: `keccak256(0xff, this, bytes32(salt), keccak256(creationCode, abi.encode(entryPoint, owner)))`; возвращает существующий адрес (для EntryPoint.getSenderAddress); revert на zero entryPoint. Конструктор принимает `address` (кастит в IEntryPoint) — чтобы скрипт не тащил лишний импорт.
  2. `Deploy.s.sol` (+7 строк): импорт + деплой фабрики с `vm.envAddress("ENTRY_POINT")` сразу после paymaster + console2.log. Остальной скрипт не тронут.
  3. Новый `contracts/test/MDAOSmartAccountFactory.t.sol`: 4 теста — counterfactual == deployed, идемпотентность, различимость salt/owner, revert на zero EP.
- **Верификация**: `forge build` ✅; `forge test` фабрики — 4/4 PASS (2 прогона, повторён Coordinator'ом — ✅).
- **Self-challenge**: (a) конструктор `address` вместо `IEntryPoint` — отклонение от v0.6-референса, поведение идентично; (b) v0.6 SimpleAccountFactory использует ERC1967Proxy+initialize, а задача дала явную формулу с creationCode — прямое CREATE2, т.к. MDAOSmartAccount не upgradeable и без initialize(); (c) YAGNI-альтернатива «не делать фабрику» отклонена — явно заказана задачей; (d) допущение: salt uint256→bytes32 (как v0.6), ENTRY_POINT из env — тот же v0.6, что у paymaster.
- **Статус**: ✅ завершено.

## S4 — Reviewer: ревью фазы 0 (0.1 + 0.2), снятие допущений coder'а

- **Задача**: пользователь потребовал «никаких допущений» — проверить 2 допущения S3 по коду, а не по доверию. Reviewer (big-pickle, разнесён ≥2 шага от S1) + повторная верификация Coordinator'ом по цитатам кода.
- **Результат reviewer**: verdict **OK**, оба допущения CONFIRMED, критических findings нет (все severity low/medium — подтверждения корректности, не баги).
- **Доказательства (цитаты кода, проверены Coordinator'ом)**:
  1. **Допущение salt uint256→bytes32 — CONFIRMED**: v0.6 `SimpleAccountFactory.getAddress` → `Create2.computeAddress(bytes32(salt), ...)` — тот же каст `bytes32(salt)`. Наш `getAddress` идентичен по формуле: `keccak256(0xff, this, bytes32(salt), keccak256(creationCode, abi.encode(entryPoint, owner)))`.
  2. **Допущение ENTRY_POINT из env — CONFIRMED**: `Deploy.s.sol` строка 61 (paymaster) и строка 71 (factory) — оба `vm.envAddress("ENTRY_POINT")`, один и тот же адрес.
  3. **Различие с v0.6 SimpleAccountFactory** (осознанное, не баг): v0.6 деплоит ERC1967Proxy + `initialize(owner)`; наш деплоит `new MDAOSmartAccount{salt}(entryPoint, owner)` напрямую — корректно, т.к. MDAOSmartAccount не upgradeable и без `initialize()` (его конструктор принимает оба аргумента). v0.6-референс использует только owner в initialize, наш — entryPoint+owner в конструкторе: формула консистентна с конструктором.
  4. **Прочее проверено reviewer'ом**: salt-синтаксис `new X{salt: bytes32}` поддерживается solc 0.8.28; `account.code.length > 0` — защита от редеплоя на месте; конструктор аккаунта проверяет zero для entryPoint и owner; фабрика деплоится после paymaster (зависимостей нет, порядок не ломает); адреса логируются для шага 4.1 (NetworkConfig).
- **Статус**: ✅ завершено. Фаза 0 (Шаг 0.1 + 0.2) закрыта с OK.

## S5 — Шаг 1.2 (TD-04): approveHook перед addRecoveryHook в Deploy.s.sol

- **Задача**: Deploy.s.sol L113-114 вызывает `deadManSwitch.setRecoveryCaller(...)` + `socialRecovery.addRecoveryHook(IRecoveryHook(address(deadManSwitch)))` БЕЗ предварительного `approveHook` → деплой падал бы с `ErrHookNotApproved` (SocialRecoveryModule.sol L430: `if (!approvedHookContracts[address(hook)]) revert ErrHookNotApproved();`).
- **Что сделано**: coder (deepseek-v4-flash-free) вставил +1 строку в существующий broadcast-блок:
  ```
  deadManSwitch.setRecoveryCaller(address(socialRecovery));
  + socialRecovery.approveHook(address(deadManSwitch));
  socialRecovery.addRecoveryHook(IRecoveryHook(address(deadManSwitch)));
  ```
  Порядок соблюдён (approveHook ПЕРЕД addRecoveryHook, иначе revert). Новых broadcast-блоков не добавлял.
- **Верификация**: `forge build` ✅ (только pre-existing lint-warning на addRecoveryHook-касте); `forge test --match-path test/MDAOSmartAccountFactory.t.sol` 4/4 PASS (2 прогона); повторная проверка Coordinator'ом: `git diff` = ровно +1 строка, корректно.
- **Self-challenge**: (a) approveHook не требует отдельного broadcast-блока — она включена в существующий `vm.startBroadcast()...stopBroadcast()`, вызов external от deployer'а — корректно; (b) риск: hook-контракт (DeadManSwitch) одобряется, но сама функция добавления в approvedHookContracts сработает только если вызвана от owner — socialRecovery на момент вызова принадлежит deployer'у (передача владения timelock'у идёт позже в скрипте) — верно; (c) альтернатива «вызвать approveHook позже в скрипте» — хуже, т.к. addRecoveryHook упадёт раньше; (d) допущение: `approveHook` вызывается от deployer'а, который на этот момент — owner SRM (проверено по порядку скрипта).
- **Статус**: ✅ завершено.
