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

## S6 — Шаг 1.3 (TD-05): P256_VERIFIER без дефолта, fail-fast

- **Задача**: DeploySocialRecoveryModule.s.sol L11 — `vm.envOr("P256_VERIFIER", address(0x100))`: RIP-7212 precompile 0x100 не существует на BSC (56/97) → SRM деплоился бы с мёртвым verifier'ом (только WARNING, без stop).
- **Что сделано**: coder (deepseek-v4-flash-free):
  1. `vm.envAddress("P256_VERIFIER")` без fallback (ревертит, если env не задан — fail-fast);
  2. два require: `!= address(0)` и `code.length > 0` (адрес обязан указывать на задеплоенный P256Verifier);
  3. мёртвая ветка `p256Verifier == address(0x100)` + chainId-блок удалены, console.log безусловный.
- **Верификация**: `forge build` ✅; `forge test --match-path test/MDAOSmartAccountFactory.t.sol` 4/4 PASS; повторная проверка Coordinator'ом: дифф −7/+6 строк, все 4 пункта выполнены, цепочка require необратимо останавливает до broadcast.
- **Self-challenge**: (a) удаление chainId-блока безопасно — проверки chainId больше не нужны, fail-fast покрывает все сети; (b) `vm.envAddress` ревертит при отсутствии переменной — это и есть fail-fast, отдельный require на пустую env не нужен; (c) код в require (`code.length > 0`) защищает от опечатки в адресе — при деплое на неверный адрес без кода скрипт упадёт ДО broadcast; (d) допущение: P256Verifier всегда деплоится на BSC в рамках основного Deploy.s.sol — подтверждено (Deploy.s.sol L216 логирует EcdsaVerifier/P256).
- **Статус**: ✅ завершено.

## S7 — Шаг 1.1 (TD-03): P-256 probe на валидном NIST-векторе

- **Задача**: `SocialRecoveryModule.sol` `_isP256Working` (L137-141) — `new bytes(128)` (RIP-7212 формат = abi.encodePacked(hash,r,s,x,y) = 160 байт) + проверка только длины результата (не значения) → с 128-байтным нулевым вводом P256Verifier.sol (fallback: `input.length != 160` → возвращает 0) даёт success=true, length=32 → probe возвращал TRUE для НЕРАБОТАЮЩЕГО verifier'а. Проверка обязана доказывать: verifier реально валидирует валидную подпись.
- **Вектор**: RFC 6979 A.2.5 (P-256, SHA-256, сообщение "sample"). Ключ: Ux=60FED4BA..., Uy=7903FE10..., hash=SHA-256("sample")=af2bdbe1..., r=EFD48B2A.... s из RFC (F7CB1C94...) > n/2 → нормализован low-S: **s = n − s = 0x0834E36AD29A83BF2BC9385E491D6099C8FDF9D1ED67AA7EA5F51F93782857A9**.
- **Математическая верификация вектора (Python, Coordinator)**: (x,y) на кривой secp256r1 ✓; hash == SHA-256(b"sample") ✓; подпись валидна (w=s⁻¹ mod n, u1·G + u2·Q → r) ✓; s ≤ n/2 (low-S, требование RIP-7212) ✓. Полный 64-символьный hex — с ведущим байтом 0x08 (Python hex() отбрасывает ведущий ноль — при вставке в Solidity требуется ровно 64 hex-символа, иначе int_const не конвертируется в bytes32).
- **Что сделано** (security_auditor + детерминированное исправление Coordinator'ом):
  1. В `SocialRecoveryModule.sol` добавлены 5 констант P256_PROBE_HASH/R/S/X/Y (валидный вектор) + `_isP256Working` переписан: `abi.encodePacked(...5×bytes32...)` (160 байт) + `return success && result.length == 32 && abi.decode(result, (uint256)) == 1`.
  2. Новый `contracts/test/P256Probe.t.sol` (3 теста).
- **⚠️ Инцидент при делегировании (зафиксирован для внешней проверки)**: 1-я попытка security_auditor — задача отменена (Task cancelled), в частичном выводе константа S искажена (0x0834e36a... — на самом деле корректна!). 2-я попытка (resume) — агент добавил лишний байт в S (0x...57a90 — заменил последний байт a9→90): подпись стала НЕВАЛИДНОЙ, probe вернул бы false → регрессия к исходному багу (всегда MockP256), при этом forge build проходит. Плюс агент не вывел реальные результаты тестов. **Все константы перепроверены Coordinator'ом детерминированно** (Python + forge test): значение S восстановлено на 0x0834E36A...A9. Этот случай подтверждает необходимость внешней перепроверки каждого security-вектора.
- **Верификация (перепроверена Coordinator'ом)**:
  - `forge build` ✅ (только pre-existing lint unsafe-typecast на addRecoveryHook).
  - `forge test --match-path test/P256Probe.t.sol` — **3/3 PASS**: ValidVector (staticcall P256Verifier: вектор→1, нули→0), ValidVerifierIsUsed (SRM с P256Verifier → p256VerifierWorking=true, P256_VERIFIER=адрес verifier), DeadVerifierFallsBackToMock (адрес без кода → false, P256_VERIFIER=MockP256).
  - Регрессия `test/{SocialRecoveryModule,Integration,MDAOSmartAccount}.t.sol` — **67/67 PASS** (MockP256 в тестах возвращает 1 на любой ввод → probe=true → поведение тестов не изменилось).
- **Self-challenge**: (a) риск: high-S подпись была бы отвергнута precompile/RIP-7212 — нормализация s=n−s обязательна, сделано; (b) риск: hex без ведущего нуля (63 символа) не компилируется в bytes32 — выявлено на сборке, исправлено на 64-символьное; (c) риск: изменение последнего байта константы проходит компиляцию, но ломает валидность подписи — поймано только математической перепроверкой, а не сборкой; (d) допущение: SHA-256 precompile и P256Verifier локально в forge работают — подтверждено тестами (вектор→1).
- **Статус**: ✅ завершено. Фаза 1 (1.1, 1.2, 1.3) реализована; осталось ревью S8.

## S8 — Reviewer: ревью фазы 1 (1.1, 1.2, 1.3)

- **Задача**: внешнее ревью трёх коммитов фазы 1 (S5 e2d0e6c, S6 efb7c20, S7 56e1288) на соответствие критериям TD-03/TD-04/TD-05; допущений быть не должно — только факты с цитатами кода.
- **Что сделано** (reviewer): проверены 5 файлов: Deploy.s.sol (approveHook), DeploySocialRecoveryModule.s.sol (fail-fast), SocialRecoveryModule.sol (_isP256Working), P256Probe.t.sol, helpers/P256Verifier.sol. Кросс-проверка probe-вектора RFC 6979 A.2.5.
- **Вердикт**: **OK**, score 100, findings: пусто.
- **Claims (все FACT)**:
  1. Probe-вектор — валидная P-256 подпись для SHA-256("sample") по RFC 6979 A.2.5 (SocialRecoveryModule.sol L137-141).
  2. `_isP256Working` не может вернуть true, если verifier мёртв или возвращает 0 (L143-149).
  3. approveHook стоит ПЕРЕД addRecoveryHook в одном broadcast-блоке (Deploy.s.sol L112-115).
  4. v0.7-остатков (PackedUserOperation) в проверенных файлах нет.
- **Self-challenge**: (a) reviewer отработал на модели mistral-medium-2505, а не big-pickle (fallback в рантайме task tool) — результат полный и корректный, все claims с цитатами строк; (b) единственное, что не проверил reviewer отдельным прогоном — повторный forge test (это уже сделано Coordinator'ом при S7: 3/3 + регрессия 67/67); (c) гипотез, блокирующих корректность, нет.
- **Статус**: ✅ завершено. Фаза 1 закрыта ревью. Следующий шаг — S9 (фаза 2, backend: миграция UNIQUE nickname).

## S9 — Шаг 2.1 (TD-06): UNIQUE-индекс на nickname

- **Задача (по плану)**: миграция `V7__unique_nickname.sql` — `ALTER TABLE users ADD CONSTRAINT uq_users_nickname UNIQUE (LOWER(nickname))`.
- **Что сделано** (coder deepseek-v4-flash-free): создан `backend/src/main/resources/db/migration/V7__unique_nickname.sql`:
  ```sql
  CREATE UNIQUE INDEX uq_nicknames_nickname ON nicknames (LOWER(nickname));
  DROP INDEX idx_nicknames_nickname_lower;
  ```
- **Отклонения от плана (обоснованные, перепроверены Coordinator'ом по коду V1)**:
  1. Таблица — **`nicknames`, не `users`**: V1 L27 `CREATE TABLE nicknames`, L29 `nickname VARCHAR(20) UNIQUE NOT NULL`; у `users` (L17) колонки nickname НЕТ. План исходил из неверного допущения.
  2. `ALTER TABLE ... UNIQUE (LOWER(...))` — **не существует в PostgreSQL** (выраженческие UNIQUE-ограничения не поддерживаются; только UNIQUE INDEX). Использован эквивалент по смыслу.
  3. `DROP INDEX idx_nicknames_nickname_lower` (V1 L37, тот же expression LOWER(nickname)) — новый UNIQUE INDEX полностью его заменяет, удаление не вредит (индексы не ссылаются по имени в приложении).
- **Верификация**: `./gradlew build` заблокирован pre-existing поломкой конфигурации (Ktor plugin 3.1.2 vs Kotlin 2.0.21, `mainClassName` — зафиксировано в репо до нашего изменения, git diff по build-файлам пуст). Coder вместо этого прогнал SQL на реальном Postgres 16 (Docker, scratch-схема по V1): CREATE ок; fail-fast подтверждён — существующие `ALICE`/`Alice` → `ERROR: duplicate key value violates unique constraint "uq_nicknames_nickname"`; будущие дубли (`INSERT 'ALICE'` после `'Alice'`) → та же ошибка. Контейнер очищен.
- **Self-challenge**: (a) старый UNIQUE на колонке (V1 L29, case-sensitive) остаётся — избыточен (новый UNIQUE INDEX покрывает и точные дубли), но не конфликтует, удалять не стали (минимальный дифф); (b) если бы в БД уже были case-insensitive дубли — миграция упала бы (fail-fast, задумано, dedup не добавляем); (c) допущение: Flyway — единственный источник схемы (проверено: schema.sql/ddl-auto в репо нет) — подтверждено.
- **Статус**: ✅ завершено. Следующий шаг — S10 (2.2 SwapService: recipient = JWT-кошелёк).

## S10 — Шаг 2.2 (TD-07): SwapService — recipient = JWT-кошелёк

- **Задача (по плану)**: recipient (L52 из тела запроса) уходил в `to` (L110) → swap на произвольный адрес (атака: жертва платит gas, средства уходят атакующему). Fix: привязать к `authenticatedPrincipal.walletAddress` из SecurityContext.
- **Что сделано** (coder deepseek-v4-flash-free; задача была отменена пользователем после завершения, изменения в worktree сохранены — проверены Coordinator'ом):
  1. `AuthService.kt`: новый `WalletPrincipal(userId, wallet) : Principal`; `validateAccessToken()` возвращает `WalletPrincipal` вместо `String?`; `wallet` читается из claim `wallet` JWT (ставится при SIWE-логине L275: `issueTokens(user.id, wallet = signer)`, L385 `put("wallet", it)`).
  2. `Application.kt`: bearer authenticate возвращает `WalletPrincipal` (вместо `UserIdPrincipal`).
  3. `SwapRoutes.kt`: `val wallet = call.principal<WalletPrincipal>()?.wallet`; если пусто → **403** «No wallet on account»; иначе `swapService.executeSwap(req, wallet)`.
  4. `SwapService.kt`: поле `recipient` **удалено** из `SwapExecuteRequest` (DTO), сигнатура `executeSwap(request, recipient: String)`, `Address(recipient)` — получатель из principal, НЕ из тела.
  5. `SwapRoutesAuthTest.kt`: +1 тест — «swap execute uses authenticated wallet as recipient»: в теле шлётся `"recipient":"0xattacker-wallet"`, `coVerify { executeSwap(any(), "0xprincipal-wallet") }` — доказывает, что recipient из тела игнорируется. Тест auth-подстановки переведён на WalletPrincipal.
- **Верификация** (Coordinator, независимая): `./gradlew test --tests SwapRoutesAuthTest --rerun-tasks` → **BUILD SUCCESSFUL 27s, 5/5 PASS, 0 failures** (XML подтверждён). Это также подтверждает, что pre-existing поломка gradle (mainClassName), мешавшая S9, **более не блокирует** — сборка работает (видимо, обход был найден при S9/S10; точная причина не выяснена).
- **Self-challenge**: (a) register/login (email+password, L306/L320) вызывают `issueTokens(user.id)` БЕЗ wallet → такие пользователи получат 403 на swap (fail-closed, безопасно, но функциональный нюанс: email-пользователи не смогут свопить, пока кошелёк не привязан — соответствует правилу «recipient никогда из тела»); (b) claim `wallet` не подписан отдельно — он часть JWT (HS256, тот же секрет), подмена требует секрета — риск низкий; (c) открытый вопрос: нужен ли явный UX для привязки кошелька к email-аккаунту — выходит за scope S10, зафиксировать в ISSUES.
- **Статус**: ✅ завершено. Следующий шаг — S11 (reviewer: ревью фазы 2).

## S11 — Reviewer: ревью фазы 2 (2.1, 2.2)

**Задача:** независимая проверка изменений S9 (V7 unique nickname index) и S10 (SwapService recipient = JWT-кошелёк).

**Что сделано:**
- Reviewer (mistral-medium-2505, fallback от big-pickle) прочитал 13 файлов: V1–V7 миграции, AuthService.kt, Application.kt, SwapRoutes.kt, SwapService.kt, SwapRoutesAuthTest.kt.
- **Verdict: OK, score 95.**
- Findings: 2 × low (косметика): SwapRoutes.kt L58 — можно специфичнее 403-ответа; SwapService.kt L94 — recipient не валидируется на null/blank (но приходит из principal, где null уже отсечён 403).
- Claims (все FACT, подтверждены кодом):
  1. WalletPrincipal создаётся в AuthService.kt L359 из JWT-claim 'wallet'.
  2. SwapRoutes.kt L57-63 корректно использует authenticated wallet как recipient.
  3. SwapExecuteRequest НЕ содержит поле recipient (SwapService.kt L47-53).
  4. Тест SwapRoutesAuthTest.kt L122-148 проверяет recipient = principal wallet.
  5. register/login (email+password) НЕ включают wallet в токен — осознанный fail-closed (зафиксировано в S10).

**Верификация:** reviewer прочитал все миграции V1-V7 (конфликтов индексов нет — DROP idx_nicknames_nickname_lower оправдан, тот же expression LOWER(nickname)), код swap-цепочки целиком. Противоречий с S9/S10 отчётами нет.

**Self-challenge:**
1. Что может быть неправильно: 2 low-findings не исправлены (косметика, не баги).
2. Сильнейший аргумент против: recipient не валидируется внутри executeSwap — но входной путь единственный (principal, уже проверен на null/blank).
3. Проще вариант: нет — это read-only ревью.
4. Допущение без доказательств: что JWT-claim 'wallet' не может быть подделан — это гарантирует подпись JWT (вне scope ревью).

**Коммит:** нет (read-only шаг). Журнал: этот блок.

## S12 — 3.1 Верификация HMAC-подписи relay (read-only)

**Задача:** подтвердить фактами, что relay уже подписывает и проверяет HMAC, а баг — в app-клиенте.

**Что сделано:** tester (deepseek-v4-flash-free) прочитал relay/src/auth.ts и relay/src/index.ts + app RelayClient.kt.

**Verdict: CONFIRMED** — все 4 факта:
1. FACT: relay подписывает запросы HMAC (формат `ts.nonce.body`) с заголовками X-Timestamp, X-Nonce, X-Signature — auth.ts L82-89.
2. FACT: проверка через verifySignature(), алгоритм HmacSHA256, кодировка base64 — auth.ts L60-90.
3. FACT: requireAuth() middleware требует подпись для всех POST — index.ts L100-112.
4. OBSERVATION: Android-клиент НЕ шлёт X-Timestamp/X-Nonce/X-Signature — RelayClient.kt.

**Вывод:** finding CRITICAL — все POST-запросы app к relay получают 401. Подтверждает план: нужен S13 (RelayHmacInterceptor в app).

**Self-challenge:**
1. Что может быть неправильно: кодировка base64 не уточнена (NO_WRAP/STANDARD) — S13 проверит.
2. Сильнейший аргумент против: тест read-only, формата подписи не воспроизводил — но цитаты кода подтверждают.
3. Проще вариант: нет.
4. Допущение: что app вообще использует эти роуты (createInvite/registerPushToken) — да, RelayClient их вызывает.

**Коммит:** нет (read-only шаг). Журнал: этот блок.

## S13 — 3.2 RelayHmacInterceptor (OkHttp)

**Задача:** добавить HMAC-подпись в Android-клиент (relay требует X-Timestamp/X-Nonce/X-Signature для всех POST).

**Инцидент — STOP и отклонение от плана:**
- Coder остановился (STOP protocol): план TD-09 требовал base64, но relay/src/auth.ts L60-72 реально ожидает **hex lowercase** (64 символа): `b.toString(16).padStart(2, '0')`. Base64 → 401 навсегда.
- Coordinator перепроверил факт по auth.ts (строка hmacSha256) → подтверждено.
- Решение: hex. Relay НЕ меняется (он — единый источник формата). План исправлен (строка 275: Base64.encodeToString → joinToString %02x).

**Что сделано (4 файла):**
1. `RelayHmacInterceptor.kt` (новый): ts=сек, nonce=16 байт→32 hex, sig=hex HMAC-SHA256(`$ts.$nonce.$body`), body через Buffer().writeTo (НЕ потребляется), fail-fast на пустом секрете в intercept() (не конструкторе — старые тесты с пустым секретом не падают).
2. `RelayClient.kt`: `client.newBuilder().addInterceptor(...)` — НЕ в NetworkModule (тот клиент делят 8+ потребителей: PaymasterClient, BundlerClient, RpcProviderManager, EtherscanRepository, EthereumClient, TokenRegistrationWorker, ProposalRepository — подпись сломала бы не-relay трафик).
3. `app/build.gradle.kts`: +buildConfigField RELAY_HMAC_SECRET (buildFeatures.buildConfig уже true; секрета нигде не было — grep пуст).
4. `RelayClientTest.kt`: +2 теста (POST: заголовки/пересчёт HMAC/body читается; GET: sig = hex(`$ts.$nonce.`)).

**Верификация (независимая, Coordinator):** `./gradlew :app:compileDevDebugKotlin` BUILD SUCCESSFUL; `./gradlew :app:testDevDebugUnitTest --tests "*RelayClient*" --rerun-tasks` — BUILD SUCCESSFUL 29s, 13/13 (11 старых + 2 новых), прогон 2 раза. Pre-existing Ktor/Kotlin поломок НЕ обнаружено (в отличие от backend).

**Self-challenge (coder):**
1. hex завязан на текущий relay — смена формата на base64 → молчаливый 401.
2. Секрет в BuildConfig — реверс-инжиниринг клиента возможен; HMAC = аутентификация приложения, не секретность (та же модель, что у relay-воркера).
3. Проще некуда: Interceptor + constructor secret — минимально.
4. Допущения: lowercase hex (подтверждено auth.ts + auth.test.ts), %02x = lowercase, SecureRandom thread-safe, ts-дрейф 5 мин ок.

**Коммит:** следующий (после S14 или отдельно — журнал пополнен).

## S13.5 — Coordinator: отклонение TD-09 (base64 → hex) зафиксировано
План строка 275 исправлен: `.header("X-Signature", sig.joinToString("") { "%02x".format(it) }) // hex lowercase 64 chars — relay hmacSha256 (auth.ts L60-72)`. Причина: факт кода перевешивает букву плана (тот же паттерн, что S9: таблица nicknames вместо users).

## S14 — 3.3 Split секретов relay (RELAY_SECRET → JWT/HMAC) + TRUSTED_SIGNER

**Задача:** Blocker #8 (утечка): один RELAY_SECRET на две роли → разделить; TRUSTED_SIGNER в docker run.

**Инцидент:** 1-й запуск coder завис (Task cancelled пользователем). Агент успел сделать 95%: split в deploy-testnet.sh (два AWS-секрета, openssl rand -hex 32, разные значения), docker run -e RELAY_JWT_SECRET/RELAY_HMAC_SECRET, wrangler secret put RELAY_HMAC_SECRET, docs/.env.example. Недоделка: TRUSTED_SIGNER не был в docker run (backend AppConfig.kt L141 требует → контейнер упал бы при старте). По указанию пользователя («перезапусти кодера с контекстом») coder доделал 1 строку: `-e "TRUSTED_SIGNER=$TRUSTED_SIGNER" \` (L585).

**Верификация (независимая, Coordinator):**
- bash -n SYNTAX OK; grep RELAY_SECRET$ в deploy-testnet.sh = 0 (split полный); 19 вхождений RELAY_JWT/HMAC.
- Факты кода: backend AppConfig.kt L80-87 требует RELAY_JWT_SECRET ≥32 + RELAY_HMAC_SECRET ≥64, и они ДОЛЖНЫ отличаться (скрипт генерит независимые rand -hex 32 — ок); relay/index.ts L102/L109 использует только RELAY_HMAC_SECRET (wrangler secret put корректен); JWT_SECRET (base64 256-bit) — отдельный backend-секрет, не тронут.
- TRUSTED_SIGNER: валидируется L66-80 (0x+40 hex), export L213, docker run L585 — порядок верный; AppConfig L141/L195 формат совпадает.

**Self-challenge (coder, доделка):** env-передача приватных адресов — уже существующий паттерн блока; --env-file .env.testnet.public не подходит (публичный файл, приватный адрес не должен там быть); допущения проверены grep'ом.

**Коммит:** d96831f → следующий (S14.5 в этом блоке).

---

## S15 — Reviewer: ревью фазы 3 (3.1–3.3) — HMAC-аутентификация relay

**Задача:** ревью 4 изменений фазы 3: relay HMAC-формат, RelayHmacInterceptor, split секретов, TRUSTED_SIGNER.

**Что сделано (reviewer, mistral-medium-2505):**
- Verdict **OK**, score 100, findings 0.
- Claims (все FACT): формат подписи hex совпадает с relay (RelayHmacInterceptor.kt:32 ↔ auth.ts hmacSha256); body не потребляется (Buffer().writeTo, L38-43); секреты разделены и значения различны (deploy-testnet.sh:141-170, AppConfig.kt:79-87); TRUSTED_SIGNER в docker run (deploy-testnet.sh:585), формат валиден (AppConfig.kt:34); non-relay трафик не подписывается (RelayClient.kt:30-31).

**Верификация (независимая, Coordinator):**
- Interceptor: `ts=(ms/1000)`, nonce=32 hex, sig=hex HMAC-SHA256("$ts.$nonce.$body") — совпадает с auth.ts (hex lowercase 64).
- `addInterceptor(RelayHmacInterceptor())` — только в RelayClient.kt:32, других использований в app/src нет.
- AppConfig.kt L80-87: RELAY_JWT_SECRET ≥32, RELAY_HMAC_SECRET ≥64, должны отличаться (нарушение = CRITICAL).
- Коммит: f7030a0 (S14) — S15 без новых коммитов (reviewer read-only).

---

## S16 — 4.1 NetworkConfig из deployment JSON (TD-11)

**Задача:** убрать хардкод адресов наших контрактов из NetworkConfig.kt; единый источник правды = foundry broadcast run-latest.json.

**Что сделано (coder, deepseek-v4-flash-free):**
1. app/build.gradle.kts (+56): таск `generateDeploymentConfig` — читает `../contracts/broadcast/Deploy.s.sol/${CHAIN_ID ?: 97}/run-latest.json`, парсит transactions с transactionType==CREATE через groovy.json.JsonSlurper, генерирует build/generated/source/deploymentConfig/java/.../DeploymentConfig.kt (object с SOCIAL_RECOVERY_MODULE + SMART_ACCOUNT_FACTORY). Условный inputs.file (JSON может отсутствовать → 0x0 + WARN, Gradle 9.4: .optional() для файлов не работает). sourceSets.main.java.srcDir + preBuild dependsOn.
2. NetworkConfig.kt (+2/−1): SOCIAL_RECOVERY_MODULE и SMART_ACCOUNT_FACTORY — get() из DeploymentConfig; ENTRY_POINT и SIMPLE_ACCOUNT_FACTORY остались константами (canonical v0.6 / legacy сторонняя — не наш деплой, в broadcast не появляются; ponytail-комментарии).

**Верификация (независимая, Coordinator):**
- :app:generateDeploymentConfig + :app:compileDevDebugKotlin → BUILD SUCCESSFUL (EXIT=0).
- chain 97 (JSON нет): 0x0 + WARN (fail-closed — isConfigured() вернёт false, «Contracts not deployed»).
- Синтетический broadcast (временный): SocialRecoveryModule→0x1111..., MDAOSmartAccountFactory→0x2222..., CREATE2 исключён — таск парсит корректно. Удалён после проверки.
- Diff: 56 строк gradle + 8 строк NetworkConfig (opencode.json — pre-existing, не наш).
- Сгенерированный файл в build/ — не коммитится.
- Известный футган (self-challenge): chainId из -PCHAIN_ID на конфигурации (flavor-переменная не читается на этапе таска); prod-сборка обязана передавать -PCHAIN_ID=56, иначе тихий 0x0 + WARN. Fail-closed, не использование чужого адреса.

## S17 — 4.2 BUNDLER_URL fail-fast (TD-12)
- **Задача:** BUNDLER_URL fail-fast при сборке (BuildConfig).
- **Что сделано:** coder (deepseek-v4-flash-free). 1-я попытка — STOP: `error()` в productFlavors срабатывает в configuration phase → ломает ЛЮБУЮ таску :app (generateDeploymentConfig, CI assembleDevDebug). Решение Architect'а: вариант B. Откат config-time error → `tasks.matching { generate(Dev|Staging|Prod)(Debug|Release)BuildConfig }.configureEach { doFirst { requireNotNull(findProperty("BUNDLER_URL_<FLAVOR>")) } }`. ci.yml:84: assembleDevDebug + `-PBUNDLER_URL_DEV=http://bundler.local:4337`. test.yml/deploy-testnet.yml собирают только backend — не тронуты.
- **Файлы:** app/build.gradle.kts, .github/workflows/ci.yml.
- **Верификация (эмпирическая):** generateDeploymentConfig без -P → PASS; compileDevDebugKotlin -PBUNDLER_URL_DEV → PASS; без -P → FAIL «BUNDLER_URL_DEV is required for dev build...»; все 3 -P → PASS; generateStagingDebugBuildConfig без -P → FAIL «BUNDLER_URL_STAGING...»; generateProdDebugBuildConfig без -P → FAIL «BUNDLER_URL_PROD...». Release покрыты тем же matching-блоком.
- **Self-challenge:** regex завязан на конвенцию имён AGP — новый flavor молча выпадет из guard (риск принят, YAGNI); release-таски не прогнаны (только debug), поведение идентично по matching-блоку.

## S18 — 4.3a CreateInviteRequest: guardianPubKeyX/Y (TD-13)

**Задача:** Добавить guardianPubKeyX/Y в CreateInviteRequest (app GuardianContracts.kt L62-68); relay уже верифицирует P-256 (index.ts L135, L191-198); НЕ PRF.

**Кто:** coder (deepseek-v4-flash-free).

**Что сделано:**
1. GuardianContracts.kt: +guardianPubKeyX/Y: String (без дефолтов — компилятор заставляет обновить все call sites).
2. GuardianManager.kt (inviteGuardian, единственный call site): создаёт реальный WebAuthn passkey (passkeyManager.createRecoveryPasskey), извлекает P-256 X/Y через GuardianUserOpBuilder.extractP256PublicKey (тот же паттерн, что в acceptInvite).
3. Новый тест CreateInviteRequestTest.kt: пиннит wire-контракт (имена полей в JSON + 64-char hex без 0x).

**Формат:** без 0x-префикса, 64 символа lowercase hex. relay/src/auth.ts L2-9 (hexToBytes) срезает опциональный 0x — оба формата принял бы; конвенция app и тесты relay — без префикса. types.ts L7-8 уже объявлял поля; index.ts L135 валидирует непустоту; L191-198 верифицирует accept-подпись через verifyP256Signature.

**Верификация:** :app:testDevDebugUnitTest BUILD SUCCESSFUL ×2 (CreateInviteRequestTest 1/1, GuardianUserOpBuilderTest, RelayClientTest зелёные). Relay npm test 32/33 — единственный фейл pre-existing (auth.test.ts старый HMAC-формат без nonce, подтверждено на базовом коммите через git stash); relay не менялся.

**Self-challenge coder'а:** passkey, созданный в inviteGuardian, — ключ owner'а, а relay верифицирует accept-подпись guardian'а → на accept возможен 401. Это pre-existing дизайн-разрыв (app создаёт ключ guardian'а только на accept); 4.3a чинит только DTO, сквозной флоу — зона 4.3b/4.3c.

**Верификация Coordinator (независимо):** git diff подтверждён (5+/1− в GuardianContracts.kt, GuardianManager.kt 11+/1−); поля на месте, GuardianManager передаёт реальные pubKey из WebAuthn. opencode.json — pre-existing diff, не наш scope.

**Статус:** DONE.

## S19 — 4.3b WebAuthn assertion (P-256, 160 байт)

**Задача:** Сквозной recovery-флоу accept в Android-приложении: guardian создаёт WebAuthn assertion (P-256), app собирает 160 байт (hash+r+s+x+y) как в SRM _verifyWebAuthn L629-676, подписывает accept для relay.

**Кто:** coder (deepseek-v4-flash-free).

**Что сделано:**
1. GuardianUserOpBuilder.kt: +buildWebAuthnProof — собирает 160 байт messageHash(32)||r(32)||s(32)||x(32)||y(32), зеркалит SRM L648-680 (messageHash = SHA-256(authenticatorData || SHA-256(clientDataJSON))). Fail-closed на не-64-байтной подписи.
2. GuardianManager.acceptInvite: guardian создаёт реальный assertion (authenticateWithPasskey + extractWebAuthnAssertion) вместо PRF-«подписи» (была L90-100, удалена); собирает и валидирует 160-байтный proof; accept подписан реальными r/s.

**Критичное обнаружение (дизайн-разрыв, pre-existing, НЕ чинилось — relay read-only):**
- До S19 accept подписывал PRF output — verifyP256Signature всегда false.
- После S19: guardian подписывает своим passkey — НО relay верифицирует против invite.guardianPubKeyX/Y (ключ, созданный на устройстве owner'а в inviteGuardian, S18 gap) → 401.
- Вторая независимая причина: relay подписывает/верифицирует над СТРОКОЙ `accept:${inviteId}:${walletAddress}` (index.ts L192), а WebAuthn assertion подписывает SHA-256(authenticatorData || SHA-256(clientDataJSON)) — другой message. passkey private key non-exportable → app физически не может подписать `accept:...` ключом из invite.
- Вывод: relay accept-эндпоинт неудовлетворим passkey-подписью (ключ + message). Это флажок для S20 (e2e: проверить on-chain recovery через SRM _verifyWebAuthn, а не relay accept) и для ISSUES (relay accept требует пересмотра: либо подпись WebAuthn-флоу, либо ключ guardian'а с invite-time).

**Верификация (независимо, Coordinator):** :app:testDevDebugUnitTest --tests "com.mdaopay.app.core.guardian.*" BUILD SUCCESSFUL (8 тестов, 2 новых) — с -PBUNDLER_URL_DEV=http://bundler.local:4337 (иначе S17 fail-fast валит generateDevDebugBuildConfig). grep подтвердил: authenticateWithPasskey L78, buildWebAuthnProof L386, relay `accept:` L192.

**Статус:** DONE. Коммит 9628bdf. Зафиксировано в ISSUES-кандидатах: relay accept-флоу несовместим с passkey-подписью.

## S20 — 4.3c e2e recovery-флоу (qa_tester → fallback coder)

**Задача:** сквозной e2e recovery-флоу: register → invite → accept → initiate → execute с реальной P-256 WebAuthn-подписью.

**Что сделано** (fallback: qa_tester codestral-2508 дважды провалил — создал каталог вместо файла, компиляция падала 30+ раз; задача передана coder deepseek-v4-flash-free):
- `contracts/test/E2ERecoveryFlow.t.sol` (209 строк): деплой аккаунта через MDAOSmartAccountFactory → setRecoveryCaller(SRM) → 2 guardian'а → initiateRecovery → 2 approve с реальными P-256 sig (RFC-6979, messageHash = SHA-256(authData||SHA-256(clientDataJSON)), формула _verifyWebAuthn) → warp 48h → executeRecovery → owner сменён, старый EOA больше не owner.
- Использован РЕАЛЬНЫЙ P256Verifier (не MockP256): требование «мусорный r/s → revert» физически невыполнимо на MockP256 (fallback возвращает 1 на любой ввод).
- Негативные: test_RevertWhen_NonGuardianApproves (ErrNotGuardian), test_RevertWhen_GarbageSignature (ErrInvalidSignature).

**Верификация (независимо, Coordinator):**
- forge test --match-path test/E2ERecoveryFlow.t.sol → 3/3 PASS ×2 прогона
- Регрессия test/{SocialRecoveryModule,Integration}.t.sol → 51/51 PASS (совпадает с baseline)

**Отклонения:** qa_tester → coder (fallback модели); MockP256 → P256Verifier (обосновано, см. выше).

**Коммит:** 8aa720e.

## S21 — Reviewer: ревью фазы 4 (4.1 NetworkConfig, 4.2 BUNDLER_URL, 4.3a/b/c guardian)

**Задача:** внешняя перепроверка всех изменений фазы 4 по claims-списку (6 claims).

**Результат (reviewer, рантайм-fallback big-pickle → mistral-medium-2505, результат полный):**
- Verdict: **approve, score 95**.
- Claims 1-5 — все FACT, подтверждены по коду:
  1. generateDeploymentConfig парсит broadcast run-latest.json (CREATE), извлекает SOCIAL_RECOVERY_MODULE + SMART_ACCOUNT_FACTORY, генерирует DeploymentConfig.kt; нет JSON → 0x0 + WARN.
  2. BUNDLER_URL fail-fast на execution-time (regex generate(Dev|Staging|Prod)(Debug|Release)BuildConfig + doFirst requireNotNull), ci.yml с -PBUNDLER_URL_DEV; config-time error() откачен.
  3. CreateInviteRequest +guardianPubKeyX/Y (без дефолтов); inviteGuardian создаёт реальный passkey, X/Y через extractP256PublicKey; 64-char hex без 0x.
  4. buildWebAuthnProof собирает 160 байт messageHash||r||s||x||y, messageHash=SHA-256(authData||SHA-256(clientDataJSON)); fail-closed на не-64-байт sig.
  5. E2ERecoveryFlow.t.sol: e2e через реальный P256Verifier + реальные RFC-6979 sig; негативные ErrNotGuardian/ErrInvalidSignature.
  6. OBSERVATION подтверждена: relay accept верифицирует над строкой `accept:${inviteId}:${walletAddress}` против invite.guardianPubKeyX/Y (index.ts L191-198) — структурно несовместим с passkey assertion. Известный дизайн-разрыв, зафиксирован ранее, НЕ новый баг.

**Findings (все приняты к сведению, НЕ исправлялись — YAGNI/известные):**
- low: app/build.gradle.kts L22 — путь к JSON хардкод (осознанно, монолитный репозиторий).
- low: GuardianUserOpBuilder.kt L327 — magic numbers gas-лимитов (пре-существующие).
- medium: relay/src/index.ts L191 — это НАШ известный дизайн-разрыв (claim 6), подтверждён как существующий.

**Коммит:** (журнал; код фазы 4 в коммитах 75e0d6f, 34e18a7, 0af6ab1, 9628bdf, 8aa720e).

---

## S22 — 5.1 DEPLOY-TESTNET.md актуализировать (TD-14)

**Задача:** убрать упоминания EntryPoint v0.7 → v0.6 (grep v0.7 → 0).

**Что сделано (coder deepseek-v4-flash-free):**
- docs/DEPLOY-TESTNET.md L272: `| EntryPoint (v0.7) | 0x5FF137D4... |` → `| EntryPoint (v0.6, canonical) | 0x5FF137D4... |`.
- Адрес сверен с NetworkConfig.kt:18 (ENTRY_POINT = 0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789, canonical v0.6) — совпадает.
- PackedUserOperation / 0x100 / DeploySocialRecoveryModule.s.sol в документе НЕ упоминаются — править нечего.

**Верификация (независимо, Coordinator):**
- `grep -in "v0\.7\|PackedUserOperation" docs/DEPLOY-TESTNET.md` → 0 совпадений (exit=1).
- grep EntryPoint → единственная строка 272 с v0.6.

**Self-challenge (coder):** метка «canonical» — из комментария NetworkConfig.kt, не выдумана. Адрес не предполагался — проверен grep'ом.

**Коммит:** S22.

---

## S23 — 5.2 dry-run в 2 шага (TD-15) — DONE

**Задача:** разделить деплой на dry-run (план) и broadcast (реальный) с HITL-паузой.

**Агент:** coder (deepseek-v4-flash-free). **Коммит:** TBD.

**Что сделано** (scripts/deploy-testnet.sh, L224-260, +28 строк):
- Шаг 1/2: `forge script script/Deploy.s.sol --rpc-url "$RPC_URL" --private-key "$BSC_TESTNET_DEPLOYER_KEY" --dry-run --slow`
- HITL-пауза: маркер `DRY-RUN OK — подтвердите план перед broadcast`; `CONFIRM_DEPLOY=no` → exit 0 (dry-run-only, CI); TTY + не задано → `read -p "Proceed with broadcast? [Y/n]"`, n/N → exit 1; не-TTY без переменной → идёт дальше (обратная совместимость)
- Шаг 2/2: исходный broadcast с `--verify --etherscan-api-key "$BSCSCAN_API_KEY" --slow`

**Верификация (независимо, Coordinator):**
- `bash -n scripts/deploy-testnet.sh` → SYNTAX OK
- grep: `--dry-run` (L230), `--broadcast` (L255), `--verify` (L256), `CONFIRM_DEPLOY` (L236/241), маркер (L234)
- Переменные подтверждены в скрипте: RPC_URL (L47), BSC_TESTNET_DEPLOYER_KEY (L6), BSCSCAN_API_KEY (L9), log_* (L37-40)

**Self-challenge coder:** dry-run с `--private-key`/`--slow` отличается от буквы спеки, но без private-key симуляция в headless упадёт (reuse существующих флагов). Риск: не-TTY без CONFIRM_DEPLOY продолжает деплой (обратная совместимость, CI должен задавать переменную явно).

## S24 — 5.3 INSURANCE_AUDITOR проверка (read-only, security_auditor nemotron)

**Задача:** Проверить fail-closed паттерн INSURANCE_AUDITOR (TD-16): Deploy.s.sol L76-78 уже fail-closed; проверить .env.example + DeployInsuranceFund.s.sol.

**Результат: ISSUES_FOUND** (условия TD-16 НЕ выполнены):
1. **High** DeployInsuranceFund.s.sol L13: `vm.envOr("INSURANCE_AUDITOR_ADDRESS", address(0))` — fallback на нулевой адрес; конструктор InsuranceFund.sol (L55-71) НЕ проверяет auditor != 0 → фонд залочен навсегда.
2. **Medium** .env.example: переменная не документирована.
3. **Low** deploy-testnet.sh L176-178: placeholder INSURANCE_AUDITOR=$DEPLOYER_ADDR — не тихий (revert require'ом L78), но грязный опыт (dry-run OK → broadcast revert).

**Перепроверка Coordinator (независимо):** основной путь БЕЗОПАСЕН — deploy-testnet.sh вызывает только Deploy.s.sol (L227/252), который fail-closed (envAddress + require != 0 + require != deployer). DeployInsuranceFund.s.sol — standalone, не используется в основном деплое, но нарушает паттерн плана.

## S24b — Фикс INSURANCE_AUDITOR fail-closed (coder deepseek-v4-flash-free)

1. DeployInsuranceFund.s.sol: `envOr` → `vm.envAddress` + `require != address(0)` + `require != deployer` (deployer уже в scope, reuse).
2. deploy-testnet.sh: placeholder → `log_error` + `exit 1` (fail-fast; log_error уже существовал L39). Бонус: закрывает и downstream-проверку AUDITOR_ONCHAIN != AUDITOR_ENV (L455).
3. .env.example: документирован INSURANCE_AUDITOR_ADDRESS=0xYourMultisigOrEOA (комментарий "required, cannot be deployer"). **Нюанс: .env.example gitignored (gitignore:42 .env.*) — правка на диске, не попадёт в git. Это pre-existing состояние репо, НЕ наш scope.**

**Верификация (независимо, Coordinator):** bash -n SYNTAX OK; grep: envAddress + 2 require на месте, log_error + exit 1 на месте; forge build — 0 errors (только pre-existing lint unsafe-typecast); .env.example grep INSURANCE_AUDITOR найден.

**Коммит:** 5b96a65 (2 файла, +6/−4). .env.example в коммит не вошёл (gitignored).

## S25 — 5.4 ProposalRepository: contract_address по use-case (TD-17)

**Задача:** `/events?contract=Proposal&limit=500` — бекенд ищет по `contract_address` (lowercase), клиент слал имя контракта → пусто всегда (аудит A2 HIGH).

**Агент:** coder (deepseek-v4-flash-free).

**Что сделано:**
1. Бекенд read-only: `/v1/events` (Application.kt:372-379) — query-параметр называется `contract`, но сравнивается с колонкой `contract_address` и приводится к lowercase (L393); параметров `params`/`chainId` в роуте НЕТ (TD-17-идея «слать сигнатуры» нереализуема против текущего бекенда); ответ содержит ключ `params` (L438).
2. ProposalRepository.kt: URL → `/v1/events?contract=${NetworkConfig.PROPOSAL_CONTRACT}` (адрес, источник — NetworkConfig.kt:24 → BuildConfig.PROPOSAL_CONTRACT, тот же, что в VoteRepository.kt:58). Убран 404-путь (нет `/v1`).
3. Бонус (H-A3): `OnChainEvent.param_json` → `params` (бекенд возвращает именно `params`; с ignoreUnknownKeys=true старое поле всегда было "{}" → фича мертва). 9 использований обновлены.
4. URL вынесен в `internal fun proposalEventsUrl(baseUrl, contractAddress)` — для тестируемости.
5. Новый тест ProposalRepositoryTest.kt (2 теста): формат URL + разбор ответа.

**Верификация (Coordinator, независимо):** git diff подтверждён (ProposalRepository.kt 26 строк, тест новый); grep: `/v1/events?contract=` L135, `params` L30/77-96, `PROPOSAL_CONTRACT` L63; VoteRepository.kt:58 уже использует тот же источник — консистентно. `./gradlew :app:compileDevDebugKotlin :app:testDevDebugUnitTest --tests "*Proposal*" -PBUNDLER_URL_DEV=http://bundler.local:4337` → EXIT=0 (BUILD SUCCESSFUL).

**Self-challenge:** dev-конфиг без PROPOSAL_CONTRACT_DEV даёт 0x0 → пусто (конфиг деплоя, не код); расширение скоупа param_json→params обосновано (иначе TD-17 не закрыт); значение адреса Proposal на тестнете не верифицировано (вне app-кода).

---

## S26 — 5.5 EventIndexer: добавить Paymaster (TD-18)

**Задача:** EventIndexer не индексирует события Paymaster (Application.kt передавал 6 контрактов, Paymaster отсутствовал).

**Кто:** coder (deepseek-v4-flash-free).

**Что сделано:**
- Root cause: `EventDefinitions.paymasterEvents` УЖЕ существовал (EventIndexer.kt L318-323) с корректными сигнатурами — Paymaster просто не был подключён в список контрактов Application.kt. Фикс = 1 строка: `add(IndexedContract(config.paymasterAddress, EventDefinitions.paymasterEvents))` (Application.kt L234).
- Сигнатуры сверены с contracts/src/MDAOPaymaster.sol: GasPaid(address,address,uint256,uint256) L203, PaymentFailed(address,address,uint256,uint8) L219, PriceUpdated(address,uint256,uint256) L216 — все совпадают (IERC20→address, enum→uint8).
- UserOperationEvent НЕ добавлен: его эмитит EntryPoint, не paymaster (grep 797-строчного контракта подтвердил — все события свои). Индексация под адресом paymaster никогда бы не сматчила лог.
- config.paymasterAddress обязателен (AppConfig.kt L118, non-null) → `?.let` не нужен.

**Верификация (независимо, Coordinator):**
- git diff: 1 строка, Application.kt L234 ✓
- grep: paymasterEvents EventIndexer.kt L319 ✓, paymasterAddress AppConfig.kt L118 (required) ✓
- ./gradlew compileKotlin (backend) → BUILD SUCCESSFUL (2 прогона у coder'а, 1 у Coordinator'а)

**Self-challenge:** список из 3 «key events» не покрывает все ~21 событие контракта (SenderBlocked, WithdrawalExecuted и т.д.) — pre-existing скоуп, осознанно не расширялся (YAGNI, добавить когда попросит продукт).

**Файлы:** backend/src/main/kotlin/com/mdaopay/paymaster/Application.kt (1 строка).

**Коммит:** (следующий в цепочке S26)
