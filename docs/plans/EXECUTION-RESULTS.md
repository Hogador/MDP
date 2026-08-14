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
