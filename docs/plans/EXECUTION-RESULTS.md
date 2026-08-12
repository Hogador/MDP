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
