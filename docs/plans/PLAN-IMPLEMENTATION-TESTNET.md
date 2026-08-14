# План реализации — от блокера EntryPoint до рабочего testnet

> **Статус**: утверждён пользователем как план (исполнение НЕ начато).
> **Источник**: аудит `docs/audit/AUDIT-FULL-2026-08-10.md` (13 blocker'ов, фазы A–J) + обратная связь пользователя.
> **Анти-галлюцинационный режим**: каждый шаг атомарен (1 шаг = 1 действие), с точным file scope, «было → стало», верификацией и подсказкой «где модель может наврать». Модель НЕ имеет права выходить за file scope шага.

---

## Шаг 0 (делается первым, разблокирует всё остальное): EntryPoint v0.6 vs v0.7

### Решение: консолидация на v0.6. Не v0.7.

**Почему это единственно правильный выбор именно сейчас:**

```
Уже согласованы на v0.6:          На v0.7 (и никогда не задеплоен):
├── MDAOPaymaster.sol              └── MDAOSmartAccount.sol
├── DeployMDAOPaymaster.s.sol          (ни одного deploy-скрипта,
├── backend/PaymasterService.kt        ни одной строки, зависящей
├── backend/AppConfig.kt               от него в проде)
├── app/UserOperation.kt
├── app/NetworkConfig.kt
└── Фактически задеплоен и используется:
    RecoveryUserOpBuilder.kt → SimpleAccount v0.6 (0x9406Cc...)
```

5 из 6 точек интеграции уже на v0.6 и **проверены сверкой поле-в-поле** (аудит H: "OK"). Единственная деталь на v0.7 — контракт, который никогда не деплоился и ни разу не тестировался в интеграции. Мигрировать на v0.7 значит переписать 5 работающих компонентов ради одного нерабочего. Мигрировать на v0.6 значит удалить/заменить один компонент.

### Шаг 0.1 — MDAOSmartAccount.sol: переписать на BaseAccount v0.6

**File scope**: `contracts/src/MDAOSmartAccount.sol` (ТОЛЬКО этот файл)

**Фактическое состояние (проверено по коду 2026-08-11)**:
- Контракт УЖЕ содержит `recoveryCaller` (строка 20), `setRecoveryCaller` (строки 49-54) и `onlyOwner` уже включает `msg.sender != recoveryCaller` (строка 31). **Часть B2 про «нет recoveryCaller» уже закрыта в коде** — осталось проверить, что он *устанавливается* при деплое (см. 0.2).
- Единственная v0.7-зависимость: `import {PackedUserOperation}` (строка 9) и сигнатура `_validateSignature(PackedUserOperation calldata userOp, ...)` (строка 63).

**Разведка ДО правки** (обязательный первый шаг — оценить объём diff'а ДО написания кода, а не проверять постфактум; v0.7-зависимость могла проникнуть глубже, чем в import и сигнатуру):
```bash
# contracts/
grep -n "PackedUserOperation\|accountGasLimits\|gasFees" contracts/src/MDAOSmartAccount.sol
```
- Ожидание: ровно 2 совпадения — L9 (import) и L63 (сигнатура). Если grep покажет больше (execute/executeBatch, getNonce с key-based nonce, event-декларации) — расчёт «2 строки diff» неверен, объём 0.1 расширяется до фактических мест использования; НЕ начинать правку, пока объём не зафиксирован в плане.
- `accountGasLimits`/`gasFees` — поля PackedUserOperation v0.7, отсутствующие в v0.6 UserOperation; любое их использование вне сигнатуры = расширенный diff.

**Стало** (минимальный diff, менять ТОЛЬКО импорт и сигнатуру — после подтверждения объёма grep'ом):
```solidity
import {UserOperation} from "account-abstraction/interfaces/UserOperation.sol";
// удалить: import {PackedUserOperation} from "account-abstraction/interfaces/PackedUserOperation.sol";

function _validateSignature(
    UserOperation calldata userOp,
    bytes32 userOpHash
) internal view override returns (uint256 validationData) {
    if (owner != ECDSA.recover(userOpHash, userOp.signature)) {
        return SIG_VALIDATION_FAILED;   // не revert (см. C2 аудита)
    }
    return SIG_VALIDATION_SUCCESS;
}
```
`recoveryCaller`/`setRecoveryCaller`/`onlyOwner`/`transferOwnership` — НЕ трогать (уже корректны).

**Верификация**:
```bash
grep -n "PackedUserOperation\|accountGasLimits\|gasFees" contracts/src/MDAOSmartAccount.sol   # должен вернуть пусто (0 совпадений)
forge build   # в contracts/
forge test    # все тесты (SRM 47 + др.) должны остаться зелёными
```

**Где модель может наврать**:
- Не менять import-пути lib (account-abstraction содержит обе версии; v0.6 — это `interfaces/UserOperation.sol`, НЕ `PackedUserOperation`).
- `SIG_VALIDATION_FAILED` — константа BaseAccount (v0.6), НЕ придумывать свою.
- Не удалять другие функции контракта, если они не относятся к v0.7-совместимости — минимальный diff.

### Шаг 0.2 — Deploy.s.sol: добавить деплой MDAOSmartAccountFactory

**File scope**: `contracts/script/Deploy.s.sol` (добавить секцию, ничего не удалять)

**Было**: фабрика умного аккаунта не деплоится вообще (SimpleAccountFactory использовался извне).

**Стало**: после существующих деплоев добавить:
```solidity
MDAOSmartAccountFactory factory = new MDAOSmartAccountFactory(ENTRY_POINT_V06);
// recoveryCaller = SocialRecoveryModule — обязательно при создании,
// иначе получаем тот же B2 баг снова
```

**Верификация** (после деплоя на тестнет, в Шаге 5):
```bash
cast call $SMART_ACCOUNT "recoveryCaller()(address)" --rpc-url $RPC
# должен вернуть адрес SocialRecoveryModule, не 0x0
```

**Где модель может наврать**:
- Не вводить новую фабрику, если она уже есть в проекте (проверить grep по `contracts/src/` на `*Factory*`).
- `ENTRY_POINT_V06` — имя переменной из существующего скрипта, не выдумывать новое.

---

## Фаза 1 — Контракты (день 1)

### 1.1 SocialRecoveryModule P-256 probe (Blocker #3)

**File scope**: `contracts/src/SocialRecoveryModule.sol` (только функция `_isP256Working`, строки 137-141)

**Фактическое состояние (проверено 2026-08-11)**: `_isP256Working` делает `new bytes(128)` — 128 нулевых байт — и проверяет `success && result.length == 32`. RIP-7212 требует 160 байт (hash+r+s+x+y), при 128 байтах precompile возвращает malformed → probe всегда false → **конструктор всегда деплоит MockP256 fallback** (L124-133), даже на сети с рабочим precompile.

**Стало** — RIP-7212 строго требует 160 байт: hash(32)+r(32)+s(32)+x(32)+y(32):
```solidity
function _isP256Working() internal view returns (bool) {
    // Известный валидный тест-вектор NIST P-256, не нули —
    // нулевой probe на многих precompile-реализациях трактуется как
    // "malformed input", а не как "invalid signature", и это разное поведение
    bytes memory probe = abi.encodePacked(
        TEST_HASH, TEST_R, TEST_S, TEST_PUBKEY_X, TEST_PUBKEY_Y  // 32×5 = 160
    );
    (bool ok, bytes memory result) = RIP7212_PRECOMPILE.staticcall(probe);
    return ok && result.length == 32 && abi.decode(result, (uint256)) == 1;
}
```

**Почему тест-вектор, а не просто длина**: аудит отметил, что предыдущий фикс «работал по случайности» через fallback P256Verifier, который принимает 32 нулевых байта как «success=true». Проверка одной длины чинит конкретный баг, но не гарантирует, что precompile реально верифицирует. Известный валидный вектор — единственный способ отличить «precompile работает» от «precompile отвечает true на всё».

**Верификация**: `forge test` — тест probe-логики (добавить если нет; в C4 аудита probe не тестировался). Тест должен:
1. Провалиться на старом коде (128 нулевых байт).
2. Пройти на новом (160 байт валидного вектора).

**Где модель может наврать**:
- TEST_HASH/TEST_R/TEST_S/TEST_PUBKEY_X/TEST_PUBKEY_Y — это РЕАЛЬНЫЙ валидный NIST-вектор, НЕ выдумывать «красивые» числа (ложный вектор = ложный результат). **Промпт агенту формулируется как копирование, не генерация**: «Скопируй тестовый вектор ECDSA P-256 из RFC 6979 Appendix A.2.5 (пример для P-256, SHA-256) без изменений. Не вычисляй и не генерируй числа самостоятельно — только копирование заданных hex-значений. Укажи источник в комментарии кода». Retry той же модели не снижает риск галлюцинации крипто-констант — модель с той же вероятностью выдумает второй «красивый» вектор; единственный защитный механизм — жёсткое требование источника (RFC 6979 / NIST CAVP / существующий `contracts/test/`) в самом промпте.
- RIP7212_PRECOMPILE = 0x0000000000000000000000000000000000000100 — захардкожен или константа, НЕ менять на «найденный в интернете» адрес.

### 1.2 approveHook перед addRecoveryHook (Blocker #4)

**File scope**: `contracts/script/Deploy.s.sol` (одна вставка перед L107)

**Фактическое состояние (проверено 2026-08-11)**: L106-107:
```solidity
deadManSwitch.setRecoveryCaller(address(socialRecovery));
socialRecovery.addRecoveryHook(IRecoveryHook(address(deadManSwitch)));  // ревертится ErrHookNotApproved
```
`addRecoveryHook` вызывает approveHook НЕ вызывается нигде в скрипте → деплой падает на L107.

**Стало**:
```solidity
deadManSwitch.setRecoveryCaller(address(socialRecovery));
socialRecovery.approveHook(address(deadManSwitch));  // ← добавить ПЕРЕД addRecoveryHook
socialRecovery.addRecoveryHook(IRecoveryHook(address(deadManSwitch)));
```

**Почему вставка, а не переделка контракта**: whitelist-логика (`approveHook`) в контракте уже правильная (защита от произвольных hook-адресов, MAX_HOOKS + approve). Баг в том, что deploy-скрипт забыл вызвать требуемый шаг.

**Верификация**: `forge script Deploy.s.sol --dry-run` (в CI после Фазы 5); после реального деплоя — `cast call $SRM "approvedHookContracts(address)(bool)" --args $SESSION_KEY_MODULE` → `true`.

**Где модель может наврать**: НЕ менять SocialRecoveryModule.sol (approve-логика верна); НЕ добавлять approveHook в другие места скрипта — ровно одна строка перед addRecoveryHook.

### 1.3 DeploySocialRecoveryModule default P256_VERIFIER (Blocker #5)

**File scope**: `contracts/script/DeploySocialRecoveryModule.s.sol`

**Фактическое состояние (проверено 2026-08-11)**: L11:
```solidity
address p256Verifier = vm.envOr("P256_VERIFIER", address(0x100));
```
Дефолт `0x100` — RIP-7212 precompile, которого **нет на BSC** (в Deploy.s.sol L33-48 это уже установлено: на BSC деплоят P256Verifier). Скрипт лишь выводит WARNING, но деплоит SRM с мёртвым verifier'ом.

**Стало**:
```solidity
address p256Verifier = vm.envAddress("P256_VERIFIER");  // без дефолта вообще
require(p256Verifier != address(0), "P256_VERIFIER must be set explicitly");
require(p256Verifier.code.length > 0, "P256_VERIFIER has no code");
```

**Почему убрать дефолт, а не поставить «правильный»**: любой дефолт для security-критичного параметра создаёт риск тихого accept-all verifier. Явный `require` превращает «забыли передать» из silent vulnerability в build failure — тот же fail-fast, что уже применялся для JWT_SECRET и ALLOW_LOCAL_SIGNING.

**Верификация**: `forge build` + запуск скрипта БЕЗ P256_VERIFIER → должен упасть с require-ошибкой (не деплоить accept-all).

**Где модель может наврать**: НЕ выдумывать «известный адрес P256Verifier» — только из env, только явно.

---

## Фаза 2 — Backend (день 1–2)

### 2.1 UNIQUE-индекс на nickname (Blocker #6)

**File scope**: `backend/src/main/resources/db/migration/` — НОВЫЙ файл `V<next>__unique_nickname.sql`

**Было**: только index, не unique:
```sql
CREATE INDEX idx_users_nickname ON users (nickname);
```

**Стало** (новая миграция, НЕ редактировать старые):
```sql
-- Предмиграционная проверка дубликатов: миграция должна явно сказать,
-- сколько дублей и почему упала, а не тихо упасть на ALTER TABLE
-- (тот же fail-fast с информативным сообщением, что P256_VERIFIER/BUNDLER_URL)
DO $$
DECLARE dup_count INT;
BEGIN
    SELECT COUNT(*) INTO dup_count FROM (
        SELECT LOWER(nickname) FROM users GROUP BY LOWER(nickname) HAVING COUNT(*) > 1
    ) t;
    IF dup_count > 0 THEN
        RAISE EXCEPTION 'Cannot add unique constraint: % duplicate nicknames found', dup_count;
    END IF;
END $$;

ALTER TABLE users ADD CONSTRAINT uq_users_nickname UNIQUE (LOWER(nickname));
```

**Почему DB-уровень, а не application**: приложение может проверять «до», но гонка между check и insert оставляет окно (TOCTOU). UNIQUE-constraint — единственный непротиворечивый способ запретить дубликаты nickname: два параллельных запроса — один получит constraint violation. DO-блок — превентивная проверка: на пустой/тестовой базе (testnet) он всегда проходит, но если база непустая — миграция падает с числом дублей, а не с непонятной ошибкой Postgres.

**Верификация**:
```bash
./gradlew flywayMigrate
# повторный INSERT с тем же nickname в нижнем регистре → SQLException 23505 (или аналог)
```

**Где модель может наврать**:
- Номер миграции `V<next>` — проверить фактический следующий номер через `ls backend/src/main/resources/db/migration/`, НЕ ставить «V3» вслепую.
- `LOWER()` — только если nickname case-insensitive в логике приложения. Если приложение различает «Alice» и «alice» — constraint без LOWER. Уточнить в `UserRepository` как ищет (findByNickname case-sensitive?).

### 2.2 SwapService: привязка recipient к JWT-кошельку (Blocker #7)

**File scope**: `backend/src/main/kotlin/.../service/SwapService.kt` + controller, если параметр идёт из запроса

**Было**: уязвимость произвольного recipient (attacker кошелька A может перевести swap-выручку на свой адрес B). Найти фактическую реализацию — откуда берётся recipient.

**Стало**: recipient — только `authenticatedPrincipal.walletAddress` (JWT-кошелёк), взятый из SecurityContext, НЕ из тела запроса. Если для обычных операций нужен параметр — проверить `recipient == walletAddress` с HTTP 400 в противном случае.

**Верификация**: юнит-тест: запрос с `recipient != walletAddress` → 400 (главное — НЕ swap); `recipient == walletAddress` → OK.

**Где модель может наврать**:
- `authenticatedPrincipal` — проверить grep'ом фактическое имя в SecurityContext, не выдумывать.
- Если swap поддерживает «swap на чужой адрес» как фичу — НЕ ломать молча; сначала спросить (найдено как «possibility of drain», фича не подтверждена).

---

## Фаза 3 — Relay + transport (день 2)

### 3.1 auth.ts: nonce в подписи (Blocker #1, критично для anti-replay)

**File scope**: `relay/src/auth.ts` (строка ~88), только функция подписи/верификации

**Фактическое состояние (проверено по коду 2026-08-11)**:
- `verifySignature` (auth.ts L75-90) УЖЕ вычисляет `expectedSig = hmacSha256(relaySecret, \`${timestamp}.${nonce}.${body}\`)` — nonce в подписи ЕСТЬ (строка 88).
- `requireAuth` в index.ts L100-112 УЖЕ fail-closed: без `RELAY_HMAC_SECRET` все запросы отклоняются 500; без валидной подписи — 401.
- CORS-заголовки (index.ts L71-74) УЖЕ разрешают `X-Timestamp, X-Signature, X-Nonce`.

**НАСТОЯЩИЙ БАГ — в app, не в relay**: `app/.../core/guardian/RelayClient.kt` (проверено 2026-08-11) **вообще НЕ подписывает запросы** — методы `post()/get()` (L153-205) строят Request БЕЗ заголовков `X-Timestamp`/`X-Nonce`/`X-Signature`. Следствие: **каждый POST от app к relay получает 401** — relay для app не работает вообще.

**Стало**: исправление в app (см. 3.2 RelayHmacInterceptor), relay трогать НЕ нужно. Верификация на relay — только тест, что сервер принимает подпись, сгенерированную interceptor'ом (тот же формат `ts.nonce.body`).

### 3.2 RelayHmacInterceptor (OkHttp) (Blocker #2)

**File scope**: `app/src/main/kotlin/.../core/guardian/RelayClient.kt` (или где создаётся OkHttpClient к relay) + тест

**Было (проверено 2026-08-11)**: RelayClient.kt не подписывает запросы вообще — каждый POST получает 401 от relay. Это **полный разрыв transport-слоя** app↔relay, а не «косметика».

**Стало**: единый `Interceptor` на OkHttpClient (okhttp уже в зависимостях app — HmacSHA256 через `javax.crypto.Mac`, без новых библиотек):
```kotlin
class RelayHmacInterceptor(
    private val secret: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val body = request.body?.let { runCatching { it.readUtf8() }.getOrNull() } ?: ""
        val ts = System.currentTimeMillis().toString()
        val nonce = UUID.randomUUID().toString()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        val sig = mac.doFinal("ts.$nonce.$body".toByteArray())
        val signed = request.newBuilder()
            .header("X-Timestamp", ts)
            .header("X-Nonce", nonce)
            .header("X-Signature", sig.joinToString("") { "%02x".format(it) }) // hex lowercase 64 chars — relay hmacSha256 (auth.ts L60-72)
            .build()
        return chain.proceed(signed)
    }
}
```

**Почему Interceptor, а не ручная подпись в каждом вызове**: ровно одна точка создания подписи; формат «ts.nonce.body» обязан совпадать с серверным (`relay/src/auth.ts` L88). Один класс, один тест, все вызовы relay подписаны автоматически.

**ВАЖНО (найдено при ревью кода)**: relay верифицирует подпись по **сырому body** (`verifySignature(bodyText, ...)`, index.ts L109). Если OkHttp-клиент отдаёт body уже сжатый/иной кодировкой, HMAC не сойдётся. Формат совпадает только при identity-кодировке — проверить отсутствие gzip в RelayClient (сейчас его нет — ок).

**Верификация**: юнит-тест — подпись, сгенерированная interceptor'ом, верифицируется функцией из relay `verifySignature` (тот же secret → та же HMAC, тот же формат); интеграционный тест — запрос без заголовков → 401; с заголовками → 200.

**Где модель может наврать**:
- `readUtf8()` **потребляет** body — для повторного чтения нужен `Buffer`, иначе последующий `chain.proceed` отправит пустое тело. (В примере выше body читается один раз для подписи, но сам request.body остаётся оригинальным — при proceed OkHttp перечитает его через buffer; это работает, но тест обязан покрыть «подписанное тело == отправленное тело».)

### 3.3 deploy-testnet.sh: RELAY_SECRET → RELAY_JWT/HMAC + TRUSTED_SIGNER (Blocker #8, утечка)

**File scope**: `scripts/deploy-testnet.sh` (docker run секция relay) + `.github/workflows/deploy-testnet.yml` (env-передача, если нужна)

**Было**: (аудит F1) — `RELAY_SECRET` из .env, передаётся relay-контейнеру; TRUSTED_SIGNER не передаётся (fallback по умолчанию → accept-all).

**Стало**:
```bash
# в docker run relay:
--env RELAY_JWT_SECRET="$RELAY_JWT_SECRET" \
--env RELAY_HMAC_SECRET="$RELAY_HMAC_SECRET" \
--env TRUSTED_SIGNER="$TRUSTED_SIGNER" \
```
+ в .env: `RELAY_JWT_SECRET` и `RELAY_HMAC_SECRET` (два разных секрета, не переиспользовать один на обе роли).

**Почему rename**: `RELAY_SECRET` был одним секретом на две роли (JWT-подпись и HMAC). Split — принцип «один секрет = одна роль»; при компрометации одного меняется только он.

**Верификация**:
```bash
# после деплоя, в CI или вручную:
curl -s $RELAY_URL/health | jq '.trustedSigner'  # ≠ "accept-all"
# плюс grep в .github/workflows/*.yml: TRUSTED_SIGNER передаётся в env deploy
```

**Где модель может наврать**: не удалять RELAY_SECRET из кода, пока не подтверждено, что он нигде не используется как JWT_SECRET (grep по relay/ и app/).

---

## Фаза 4 — App (день 2–3)

### 4.1 NetworkConfig: один источник правды (R1-мера)

**File scope**: `app/src/main/kotlin/.../core/NetworkConfig.kt` (L20)

**Было** (аудит F): адреса контрактов и bundler URL захардкожены/разбросаны; EnvironmentConfig не читает deployment JSON.

**Стало**: NetworkConfig строится из **deployment JSON** (файл, который генерирует `forge script --broadcast`, например `contracts/broadcast/Deploy.s.sol/<chainId>/run-latest.json`) при сборке:
```kotlin
// идея: читать src/main/resources/deployment-<chainId>.json,
// сгенерированный из broadcast — не хардкодить адреса в коде
```
Один источник: контракты → JSON → NetworkConfig → все потребители (UserOperation.kt, RelayClient, EventIndexer-подписки).

**Верификация**: diff-проверка в CI: если broadcast JSON изменился, но app-ресурс нет → job падает («deployment out of sync»).

**Где модель может наврать**:
- Не выдумывать «свой формат JSON» — сначала посмотреть фактическую структуру run-latest.json (там есть `transactions[].contractAddress` и `receipts`), либо договориться о каноничном файле `deployment.json`, который генерится deploy-скриптом. Цель — удалить захардкоженные адреса, не добавить второй формат.
- Синхронизация: один JSON на chainId (97 = BSC testnet, 31337 = anvil).

### 4.2 BUNDLER_URL: fail-fast при сборке (Blocker #9)

**File scope**: `app/build.gradle.kts` (L31, 53, 69 — места, где сейчас fallback) + NetworkConfig

**Было**: `BUNDLER_URL ?: "http://localhost:3000"` — тихий fallback, приложение запускается с мёртвым URL.

**Стало**:
```kotlin
// в BuildConfig: если BUNDLER_URL не задан в gradle.properties/env → build FAILS
// (тот же паттерн fail-fast, что уже используется для других обязательных ключей)
```

**Верификация**: `./gradlew assembleDebug` без BUNDLER_URL в окружении → build падает с понятным сообщением; с URL → проходит.

**Где модель может наврать**: НЕ добавлять fallback «localhost» обратно. Провал сборки намеренный — это и есть защита от «тихого мёртвого URL» в проде.

### 4.3 GuardianContracts: pubKeyX/pubKeyY + реальная WebAuthn-подпись (Blocker #10, критично)

**File scope**: `app/src/main/kotlin/.../guardian/GuardianContracts.kt` + `GuardianManager.kt` + связанные тесты

**Фактическое состояние (проверено 2026-08-11)**:
- relay (index.ts L135) УЖЕ требует `guardianPubKeyX/Y` в `POST /guardian/invite` и верифицирует P-256 подпись на accept (L191-198) и approve/veto (L228-234, L275-281) через Web Crypto API (`verifyP256Signature`, auth.ts L13-47). **Relay-часть WebAuthn-подписи уже реализована.**
- app: `RelayClient.createInvite` (RelayClient.kt L104-106) шлёт только `CreateInviteRequest` — **все запросы app к relay не подписаны HMAC** (401) и не содержат pubKeyX/Y при создании invite — это и есть реальный разрыв на стороне app.

**Стало**:
1. `CreateInviteRequest` (app) получает `guardianPubKeyX` и `guardianPubKeyY` (из WebAuthn registration attestation).
2. HMAC-подпись всех запросов — через RelayHmacInterceptor (см. 3.2).
3. Backend (relay) уже верифицирует P-256 — НЕ менять relay, только убедиться, что app отправляет подписанный challenge от WebAuthn (не «mock»).

**Верификация**: e2e-тест flow: register WebAuthn → invite → подпись проставляет precompile-верификацию → invited guardian может отозвать/recovery. Тест должен падать на старом коде (mock-подпись) и проходить на новом.

**Где модель может наврать**:
- НЕ использовать PRF (Pseudo-Random Function) как «удобную замену» подписи — это не криптографическая подпись, а симметричный ключ; аудит явно отметил разницу.
- Проверить, что backend реально вызывает precompile, а не просто проверяет «подпись непустая» (это был C3 fail-open).
- Challenge-формат: подписывается invite payload hash, не весь JSON вперемешку — определить порядок байт в спецификации (RIP-7212: SHA-256(hash || r || s || x || y), 160 байт, см. 1.1).

---

## Фаза 5 — Deploy + интеграция (день 3)

### 5.1 DEPLOY-TESTNET.md: актуализировать (Blocker #11)

**File scope**: `docs/DEPLOY-TESTNET.md` + `.github/workflows/deploy-testnet.yml`

**Было** (аудит G1–G5): в доке указан v0.7 для контракта v0.6; RELAY_SECRET в docker run без TRUSTED_SIGNER; docker run без --env для новых ключей.

**Стало**: док актуализировать под итоговую конфигурацию после фаз 0–4:
- EntryPoint: v0.6 (не v0.7);
- docker run relay: RELAY_JWT_SECRET, RELAY_HMAC_SECRET, TRUSTED_SIGNER;
- порядок деплоя: contracts → relay → backend → app;
- добавить обязательный шаг «проверить TRUSTED_SIGNER на health-эндпоинте».

**Верификация**: `grep -n "0.7\|v0.7" docs/DEPLOY-TESTNET.md` → 0 совпадений; docker run секция в доке == фактическому docker run в скрипте.

**Где модель может наврать**: не «переписать весь док» — только секции, которые не соответствуют коду после фаз 0–4.

### 5.2 dry-run в 2 шага (явный deploy plan)

**File scope**: `scripts/deploy-testnet.sh`

**Было**: один `forge script ... --dry-run` (аудит G4) — неясно, что именно деплоится.

**Стало**: два явных шага:
```bash
# шаг 1: показать план (что будет задеплоено)
forge script Deploy.s.sol --rpc-url $RPC --dry-run
# шаг 2: если план подтверждён (manual review в CI / HITL) — реальный деплой
forge script Deploy.s.sol --rpc-url $RPC --broadcast --verify
```
Между шагами — пауза на review (в CI: `workflow_dispatch` + комментарий с адресами; вручную: глазами).

**Верификация**: лог dry-run содержит список деплоя (все контракты фаз 0–2: MDAOSmartAccountFactory, SocialRecoveryModule, SessionKeyModule, Paymaster) — сверять со списком.

### 5.3 INSURANCE_AUDITOR: без fallback (Blocker #12)

**File scope**: `contracts/script/Deploy.s.sol` (конструктор/деплой InsuranceFund) + `.env.example` / `.env`

**Фактическое состояние (проверено 2026-08-11)**: Deploy.s.sol L69-71 УЖЕ fail-closed:
```solidity
address insuranceAuditor = vm.envAddress("INSURANCE_AUDITOR_ADDRESS");
require(insuranceAuditor != address(0), "InsuranceFund auditor not set (INSURANCE_AUDITOR_ADDRESS)");
require(insuranceAuditor != deployer, "Auditor cannot be deployer");
```
**Этот пункт уже закрыт в Deploy.s.sol.** Осталось: проверить, что `.env.example` документирует `INSURANCE_AUDITOR_ADDRESS` как обязательную, и что другие скрипты (DeployInsuranceFund.s.sol) используют тот же паттерн, а не fallback. Если оба условия выполняются — пункт помечается DONE без изменений кода.

---

## Дополнительные фиксы из аудита (после 5 основных фаз)

### 5.4 ProposalRepository: endpoint/address/params (B2/B6, аудит G)

**File scope**: `app/src/main/kotlin/.../proposal/domain/ProposalRepository.kt` (L62)

**Было**: запрос `/v1/events` без `contract_address` и без `params` — бэкенд слеп к событиям 3 контрактов (B4–B6 аудита: EventIndexer слушает только один адрес/topic).

**Стало**:
- `contract_address` — адреса всех контрактов, события которых нужны (SocialRecoveryModule, SessionKeyModule, ProposalModule, Paymaster — уточнить по use-case);
- `params` — точные сигнатуры из IProposal.sol/SessionKeyModule.sol (аудит B6 перечислил: ProposalCreated, ProposalExecuted, RecoveryRequested, RecoveryExecuted и т.д. — взять из `contracts/src/**/I*.sol`);
- ретраи/пагинация — проверить фактическую реализацию.

**Верификация**: юнит-тест: мок-ответ /v1/events с `contract_address=X` → EventIndexer парсит события X (раньше падал/молчал).

**Где модель может наврать**: сигнатуры events брать ТОЛЬКО из `contracts/src/**/I*.sol` (grep `event `), не из памяти.

### 5.5 EventIndexer: topics для всех контрактов (B4–B6)

**File scope**: `backend/src/main/kotlin/.../indexer/EventIndexer.kt` + `Application.kt` (конфигурация списка контрактов)

**Фактическое состояние (проверено 2026-08-11, в т.ч. Application.kt L227-234)**: `EventDefinitions` в EventIndexer.kt (L260-324) УЖЕ содержит topics для Proposal, SessionKeyModule, SocialRecoveryModule, DeadManSwitch, Paymaster, Treasury, PaymentSplitter. **Application.kt уже передаёт в EventIndexer 6 контрактов**: Treasury, Proposal, PaymentSplitterFactory, SessionKeyModule, SocialRecoveryModule, DeadManSwitch. То есть SRM и SessionKey УЖЕ индексируются. **Реальный пробел — один: Paymaster НЕ индексируется**, хотя `paymasterEvents` (EventIndexer.kt L319-323) определён, а `config.paymasterAddress` обязателен (AppConfig.kt L118).

**Стало**: добавить Paymaster (и его `paymasterEvents`) в список IndexedContract в Application.kt — единственная недостающая строка конфигурации. В `WatchtowerService.kt` (L49-64) отдельно проверить совпадение topic-сигнатур с реальными сигнатурами в SRM (RecoveryInitiated/RecoveryExecutedEv) — тема с `topics[3]` против `topics[0]` из аудита B5 относится к Watchtower, не к EventIndexer.

### 5.6 FCM push: app шлёт пустой fcmToken (B1, аудит G) — ИСПРАВЛЕНО по коду

**File scope**: `app/.../feature/recovery/presentation/RecoveryViewModel.kt` (L508, хардкод `fcmToken = ""`) + `app/.../core/guardian/RelayClient.kt` (роут `/push/register`, уже есть)

**Фактическое состояние (проверено 2026-08-11)**:
- **Backend-роут НЕ нужен**: весь push-флоу идёт app → **relay**, и в relay роут `POST /push/register` УЖЕ есть (index.ts L303-310) и рабочий (`registerPushToken` в storage.ts, `sendPushNotification` в fcm.ts; вызывается на approve/veto/notify).
- `app/.../fcm/FcmService.kt` — **такого файла в app нет**.
- **Реальная причина неработающего push**: `RecoveryViewModel.kt` L508 хардкодит `fcmToken = ""` → токен никогда не регистрируется на relay → push некому отправлять.

**Стало**: убрать хардкод `fcmToken = ""`; получать реальный FCM-токен (через Firebase Messaging в app) и передавать его в `RelayClient.registerPushToken` (RelayClient.kt L141-145, роут уже есть). Backend не трогать.

**Верификация**: e2e: app логинится → получает FCM-токен → вызывает `POST /push/register` на relay → 200 → токен в KV; затем событие recovery → push приходит на устройство.

### 5.7 Мёртвый код: удалить или пометить (аудит E)

**File scope**: по списку аудита E (объекты без использования: например `REL_SECRET`?/старые env-ключи, неиспользуемые ABI-поля и т.п.)

**Стало**: каждый объект: либо удалён (если не используется), либо помечен `@Deprecated` + ссылка на аудит. ВАЖНО: `RELAY_SECRET` не удалять, пока не подтверждено отсутствие использования в app-коде (см. 3.3).

**Верификация**: `grep -rn "RELAY_SECRET" --include="*.ts" --include="*.kt"` → 0 совпадений (после удаления).

---

## Меры против повторения R1/R2/R3 (корневых причин)

### R1: единый источник правды для межслойных контрактов
- deployment JSON (из broadcast) → app NetworkConfig (4.1);
- signal-сигнатуры в одном месте: `contracts/src/**/I*.sol` → backend topics конфиг (5.5);
- проверка sync в CI: broadcast JSON vs app-ресурс (4.1).

### R2: интеграционный гейт вместо «зелёного юнита»
- В CI добавить job `integration-smoke`: поднять anvil (задеплоить через Deploy.s.sol), прогнать: register → invite → recovery (обвязка relay+backend+app) — вертикальный срез, который падает, если любой слой не синхронизирован;
- DoD фичи = вертикальный срез зелёный, не «мой юнит зелёный».

### R3: fail-open → fail-closed
- Все security-параметры (P256_VERIFIER, TRUSTED_SIGNER, INSURANCE_AUDITOR, BUNDLER_URL): обязательные, без дефолтов/fallback (1.3, 3.3, 4.2, 5.3);
- «подпись непустая» → «подпись валидна» (4.3).

---

## Definition of Done (общий, для всей работы)

- [ ] forge build + forge test зелёные (contracts)
- [ ] ./gradlew build зелёный (backend, app)
- [ ] relay jest зелёные
- [ ] vertical slice на anvil: register → invite → recovery проходит через все слои
- [ ] P256_VERIFIER/TRUSTED_SIGNER/INSURANCE_AUDITOR/BUNDLER_URL заданы явно, без дефолтов
- [ ] git log: никаких секретов в истории (gitleaks на каждом PR)
- [ ] DEPLOY-TESTNET.md соответствует коду (grep 0.7 → 0)

---

## Технические решения и обоснования (ADR-lite)

> Каждое решение привязано к шагу плана и опирается на «Фактическое состояние (проверено 2026-08-11)» из соответствующих секций. Формат: решение → альтернативы → почему → риски.

### Решение TD-01 (Шаг 0.1): переписать MDAOSmartAccount.sol на BaseAccount v0.6
- **Решение**: заменить ровно две v0.7-зависимости — `import {PackedUserOperation}` (L9) и сигнатуру `_validateSignature(PackedUserOperation, ...)` (L63) — на v0.6 (`import {UserOperation}`, сигнатура `_validateSignature(UserOperation, bytes32)`), не трогая `recoveryCaller`/`setRecoveryCaller`/`onlyOwner`.
- **Альтернативы** (рассмотрены и отклонены): 1) мигрировать все 5 работающих точек интеграции на v0.7 — переписывание проверенных компонентов (PaymasterService.kt, UserOperation.kt, NetworkConfig.kt, RecoveryUserOpBuilder.kt) ради контракта, который ни разу не деплоился и не тестировался в интеграции; 2) деплоить контракт в v0.7 как есть — EntryPoint v0.7 не задеплоен нигде, интеграция невозможна физически; 3) писать в контракте adapter «PackedUserOperation → UserOperation» — лишний код в контракте, который дублирует то, что v0.6-интерфейс даёт бесплатно, и создаёт второй источник правды по формату userOp.
- **Почему это лучшее**: факт из плана — 5 из 6 точек интеграции уже на v0.6 и сверены поле-в-поле (аудит H: «OK»), а `recoveryCaller` уже есть в контракте (L20) и корректен (L31, L49-54). Осталось 2 строки diff против переписывания 5 компонентов; минимальный diff = минимальный риск регрессии остальных функций.
- **Риски/что может пойти не так**: `SIG_VALIDATION_FAILED`/`SIG_VALIDATION_SUCCESS` — константы v0.6 BaseAccount, их нельзя заменять своими значениями; **grep-разведка ДО правки** (`PackedUserOperation|accountGasLimits|gasFees` по всему контракту) — если совпадений больше 2 (L9+L63), diff шире заявленного, править нельзя до фиксации объёма в плане (это часть шага 0.1, а не пост-проверка); после правки grep должен вернуть 0 совпадений.

### Решение TD-01a (расширение TD-01, одобрено пользователем 2026-08-12): даунгрейд `account-abstraction` lib v0.9.0 → v0.6.0
- **Решение**: заменить содержимое `contracts/lib/account-abstraction` (vendor-копия, v0.9.0 = EntryPoint v0.7 API) на тег `v0.6.0` (проверено: `refs/tags/v0.6.0` существует на GitHub); после замены — переписать `MDAOSmartAccount.sol` и `MDAOSmartAccount.t.sol` под v0.6 API; добавить `lib/account-abstraction/VENDORED_VERSION.md` с пометкой «PINNED: v0.6.0, НЕ запускать forge update lib/account-abstraction».
- **Альтернативы** (рассмотрены и отклонены): B) локальный struct UserOperation в MDAOSmartAccount без трогания lib — оставляет lib v0.9.0 (v0.7) в репо, MockEntryPoint в тестах остаётся v0.7 → тесты проверяют поведение против интерфейса, которого нет в задеплоенном EntryPoint v0.6 (паттерн «тесты зелёные, потому что тестируют не то, что реально деплоится» — тот же класс, что MockP256 accept-all); две параллельные модели UserOperation в репо = источник будущей путаницы; C) оставить v0.7 контракт против v0.6 EntryPoint — гарантированный ABI mismatch, деплой невозможен.
- **Почему это лучшее**: единообразие стека — 100% остального (Paymaster с локальным struct UserOperation v0.6, backend, app, задеплоенный EntryPoint v0.6 на BSC 0x5FF137...) уже v0.6; радиус взрыва минимален — только 2 файла используют lib (`grep -rln "account-abstraction/"` → `MDAOSmartAccount.sol` + `MDAOSmartAccount.t.sol`), оба и так в scope шага 0.1; v0.6.0 содержит `UserOperation.sol`/`BaseAccount.sol`/`IEntryPoint.sol`/`Helpers.sol` — все нужные интерфейсы.
- **Риски/что может пойти не так**: vendor-замена без git submodule → будущий `forge update` может перезаписать v0.6.0 обратно на v0.9.0 — защита: `VENDORED_VERSION.md` pin + запрет в карте делегирования; `ISenderCreator` (импортируется тестом) может отсутствовать в v0.6.0 — проверить наличие после даунгрейда, при отсутствии адаптировать тест; повторный grep `PackedUserOperation|account-abstraction` по ВСЕМУ проекту после правки (не только по 2 изначальным файлам) — обязателен для ловли оставшихся v0.7-ссылок.

### Решение TD-02 (Шаг 0.2): деплой MDAOSmartAccountFactory внутри Deploy.s.sol
- **Решение**: добавить в конец существующего Deploy.s.sol секцию `new MDAOSmartAccountFactory(ENTRY_POINT_V06)` с немедленной установкой `recoveryCaller = SocialRecoveryModule` при создании аккаунта.
- **Альтернативы** (рассмотрены и отклонены): 1) отдельный скрипт `DeployMDAOSmartAccountFactory.s.sol` — размазывает порядок деплоя на два скрипта, создаёт риск рассинхрона адресов и порядка (SRM должен существовать до фабрики, иначе recoveryCaller некуда указывать); 2) продолжать использовать SimpleAccountFactory извне — её аккаунты не имеют `recoveryCaller`, это ровно тот B2-баг, который аудит зафиксировал; 3) CREATE2 с детерминированным адресом — лишняя сложность: тестнет-деплой одноразовый, детерминизм адреса не нужен, а соль придётся хранить в env.
- **Почему это лучшее**: Deploy.s.sol уже является единой точкой деплоя всех контрактов фаз 0–2; добавление секции туда сохраняет существующий порядок и делает проверку тривиальной (`cast call recoveryCaller()` после деплоя). Комментарий в плане прямо фиксирует требование «recoveryCaller обязательно при создании, иначе тот же B2 снова».
- **Риски/что может пойти не так**: если фабрика уже существует в `contracts/src/` (план требует grep `*Factory*` до написания кода) — секция продублирует деплой; имя `ENTRY_POINT_V06` должно совпадать с уже существующей переменной скрипта, не выдумывать новую.

### Решение TD-03 (Шаг 1.1): P-256 probe на валидном NIST-векторе 160 байт
- **Решение**: заменить `new bytes(128)` в `_isP256Working` (L137-141) на 160-байтный probe `abi.encodePacked(hash, r, s, x, y)` из официального тест-вектора NIST P-256 / RFC 6979 и проверять `ok && result.length == 32 && abi.decode(result, (uint256)) == 1`.
- **Альтернативы** (рассмотрены и отклонены): 1) оставить 128 нулевых байт и чинить только проверку длины — RIP-7212 требует ровно 160 байт (hash+r+s+x+y), при 128 байтах precompile отвечает «malformed», probe вечно false → MockP256 fallback деплоится даже на сети с рабочим precompile (факт L124-133); 2) убрать probe и всегда использовать precompile `0x100` — на BSC precompile нет (факт Deploy.s.sol L33-48: на BSC деплоят P256Verifier), деплой упадёт на тестнете; 3) проверять только `result.length == 32` на нулевом входе — не отличает рабочий precompile от accept-all (именно это аудит назвал «работает по случайности» через fallback P256Verifier).
- **Почему это лучшее**: единственный способ отличить «precompile реально верифицирует» от «precompile отвечает true на всё» — валидная подпись с известным результатом. Длина и ненулевые байты по отдельности не дают этой гарантии.
- **Риски/что может пойти не так**: выдуманный «красивый» вектор даст ложный результат probe (требование — вектор копируется из RFC 6979 Appendix A.2.5 / NIST CAVP или существующего `contracts/test/`, промпт агенту запрещает генерацию чисел и требует ссылку на источник в комментарии кода; retry той же модели НЕ является fallback'ом для этого риска, т.к. не устраняет причину галлюцинации); на реализациях precompile, которые не поддерживают именно этот вектор (редкие баги реализации), probe даст ложно-отрицательный результат → fallback на MockP256 (fail-safe, но маскирует проблему).

### Решение TD-04 (Шаг 1.2): вставка `approveHook` перед `addRecoveryHook`
- **Решение**: добавить ровно одну строку `socialRecovery.approveHook(address(deadManSwitch));` перед L107 `socialRecovery.addRecoveryHook(...)` в Deploy.s.sol.
- **Альтернативы** (рассмотрены и отклонены): 1) изменить SocialRecoveryModule, чтобы `addRecoveryHook` сам вызывал approve — ломает whitelist-логику контракта (защита от произвольных hook-адресов, MAX_HOOKS + approve), превращая security-фичу в дыру; 2) вызывать approveHook в другом контракте/скрипте (например, в самом SocialRecoveryModule при инициализации) — неверный порядок вызовов либо дублирование логики; 3) убрать require approve из контракта — откат защиты ради «удобства деплоя».
- **Почему это лучшее**: факт из плана — approve-логика контракта уже корректна, баг в том, что deploy-скрипт забыл вызвать обязательный шаг (L106-107 ревертятся с `ErrHookNotApproved`). Одна строка в скрипте чинит деплой, не трогая контракт и его защиту.
- **Риски/что может пойти не так**: вставить approveHook ПОСЛЕ addRecoveryHook — реверт останется; добавить approveHook ещё в какое-то место скрипта — избыточный вызов (план требует ровно одну строку перед L107).

### Решение TD-05 (Шаг 1.3): P256_VERIFIER без дефолта, fail-fast require
- **Решение**: заменить `vm.envOr("P256_VERIFIER", address(0x100))` (DeploySocialRecoveryModule.s.sol L11) на `vm.envAddress` без дефолта + `require(p256Verifier != address(0))` + `require(p256Verifier.code.length > 0)`.
- **Альтернативы** (рассмотрены и отклонены): 1) оставить дефолт `0x100` — факт: на BSC precompile RIP-7212 отсутствует (Deploy.s.sol L33-48 деплоит P256Verifier именно поэтому), SRM деплоится с мёртвым verifier'ом, а WARNING уже доказал свою бесполезность (именно так возник этот пункт аудита); 2) захардкодить адрес ранее задеплоенного P256Verifier — адрес отличается между anvil/BSC-тестнет/продом, тихий неверный адрес хуже отсутствия; 3) проверять только на тестнете, в проде доверять дефолту — асимметрия безопасности между средами недопустима для security-критичного параметра.
- **Почему это лучшее**: паттерн fail-fast уже принят в проекте для JWT_SECRET и ALLOW_LOCAL_SIGNING (упомянуто в плане); явный `require` превращает «забыли передать» из silent-уязвимости в build failure. Проверка `code.length > 0` ловит и «передали мусорный адрес».
- **Риски/что может пойти не так**: скрипты/CI, которые раньше полагались на дефолт, начнут падать — это намеренно, но надо обновить `.env.example` и CI в рамках того же шага, иначе деплой встанет.

### Решение TD-06 (Шаг 2.1): UNIQUE-constraint на `LOWER(nickname)` на уровне БД
- **Решение**: новая миграция `V7__unique_nickname.sql` с pre-migration DO-блоком (считает дубликаты LOWER(nickname), RAISE EXCEPTION с числом дублей при ненулевом количестве) + `ALTER TABLE users ADD CONSTRAINT uq_users_nickname UNIQUE (LOWER(nickname))`; старые миграции не редактировать.
- **Альтернативы** (рассмотрены и отклонены): 1) только application-level проверка `findByNickname` перед INSERT — TOCTOU: между check и insert два параллельных запроса проходят проверку и оба вставляют, гонка не закрыта (это и есть Blocker #6); 2) UNIQUE-constraint без `LOWER()` — если приложение ищет nickname case-insensitive (проверить `UserRepository.findByNickname`), то «Alice» и «alice» станут двумя учётками одного пользователя — constraint с LOWER чинит и гонку, и дубли регистра; 3) UUID-суффикс/автогенерация уникального nickname — меняет продуктовое поведение: nickname перестаёт быть выбранным пользователем идентификатором.
- **Почему это лучшее**: DB-constraint — единственный непротиворечивый барьер при гонке: из двух параллельных INSERT один гарантированно получает violation (23505/аналог), что детерминированно ловится тестом и не зависит от того, где живёт проверка.
- **Риски/что может пойти не так**: номер миграции — фактически следующий после V6 (проверено: в `db/migration/` лежат V1…V6), т.е. V7, но перед коммитом сверить `ls`; если в проде уже есть дубликаты nickname — миграция упадёт на существующих данных; это покрыто pre-migration DO-блоком внутри той же миграции: вместо тихой ошибки Postgres — явное сообщение с числом дублей (fail-fast, как в остальных security-шагах плана); если дубликаты реально есть — сначала дедуп данных отдельной миграцией, затем constraint.

### Решение TD-07 (Шаг 2.2): recipient swap-выручки — только JWT-кошелёк
- **Решение**: recipient берётся исключительно из `authenticatedPrincipal.walletAddress` (SecurityContext), не из тела запроса; если API принимает recipient как параметр — валидировать `recipient == walletAddress`, иначе HTTP 400.
- **Альтернативы** (рассмотрены и отклонены): 1) оставить recipient из тела запроса — attacker кошелька A шлёт swap с recipient=B и выводит выручку себе (это и есть Blocker #7, «possibility of drain»); 2) подписывать recipient приватным ключом кошелька в app — у app нет приватного ключа (подписи WebAuthn живут на уровне guardian и не применимы к swap), добавление ключа = новая поверхность атаки; 3) whitelist «доверенных» адресов получателей — лишняя поверхность управления (кто и как добавляет адреса в whitelist), не решает корень: получатель не должен управляться клиентом вообще.
- **Почему это лучшее**: JWT уже выдан после SIWE-аутентификации и содержит walletAddress; привязка к нему делает атаку «перевести на чужой адрес» бессмысленной — получатель всегда равен аутентифицированному кошельку. Минимальная правка одного слоя (SwapService + при необходимости controller), без изменения протокола.
- **Риски/что может пойти не так**: если «swap на чужой адрес» — подтверждённая продуктовая фича, а не баг, строгая привязка её ломает — план прямо требует сначала спросить; имя `authenticatedPrincipal` сверить grep'ом с фактическим SecurityContext, не выдумывать.

### Решение TD-08 (Шаг 3.1): nonce в подписи — чинить app, relay не трогать
- **Решение**: relay остаётся как есть (подпись `ts.nonce.body` уже реализована, auth.ts L88); исправление — в `RelayClient.kt` (app), который должен слать заголовки `X-Timestamp`/`X-Nonce`/`X-Signature`.
- **Альтернативы** (рассмотрены и отклонены): 1) переписать verifySignature в relay — факт: подпись с nonce уже вычисляется (L88), fail-closed `requireAuth` уже есть (index.ts L100-112), CORS уже разрешает нужные заголовки (L71-74) — менять нечего, это был бы рефакторинг ради рефакторинга; 2) отключить HMAC-проверку для app (например, по IP/whitelist) — fail-open: откат security-механизма anti-replay, ровно то, против чего R3; 3) перейти на JWT для машинного доступа relay↔app — у relay JWT уже используется для пользовательских сессий, HMAC — для машинного; введение третьего механизма аутентификации не чинит ни одного байта из реального разрыва.
- **Почему это лучшее**: минимальный diff — relay корректен по всем трём проверкам (подпись, fail-closed, CORS), единственный сломанный участок — app не шлёт заголовки вовсе (RelayClient.kt L153-205), из-за чего каждый POST получает 401 и relay для app не работает вообще. Починка на стороне отправителя закрывает разрыв целиком.
- **Риски/что может пойти не так**: если relay-код в репозитории отличается от задеплоенного — подпись «сойдётся в тесте, но не в проде»; верификация подписи по сырому body (index.ts L109) означает, что любой gzip/трансформация body в app сломает HMAC (см. TD-09).

### Решение TD-09 (Шаг 3.2): единый OkHttp `RelayHmacInterceptor`
- **Решение**: один `Interceptor` на OkHttpClient relay: читает body, считает HMAC-SHA256 от `ts.nonce.body` через `javax.crypto.Mac` (JDK, без новых библиотек — okhttp уже в зависимостях app), проставляет заголовки `X-Timestamp`/`X-Nonce`/`X-Signature`.
- **Альтернативы** (рассмотрены и отклонены): 1) ручная подпись в каждом методе RelayClient (`post()`/`get()` и т.д.) — формат «ts.nonce.body» дублируется в N местах, любой новый вызов можно забыть подписать → тихий 401/дыра, рассинхрон форматов (именно так relay-подпись и перестала совпадать); 2) библиотека okhttp-signing/аналог — новая зависимость ради ~30 строк, которые JDK-`Mac` покрывает полностью; 3) подписывать на уровне выше (в сервисном слое поверх Retrofit) — больше кода и окно, где неподписанный запрос уже отправлен; перехватчик — единственная гарантированная точка перед сетевым стеком.
- **Почему это лучшее**: ровно одна точка создания подписи, применяется ко всем вызовам relay автоматически; формат обязан совпадать с серверным `relay/src/auth.ts` L88 — один класс, один тест, все вызовы подписаны. Ключевой факт: relay верифицирует подпись по сырому body (index.ts L109) — interceptor должен подписывать именно то, что реально уйдёт в сеть (identity-кодировка, сейчас gzip в RelayClient нет — ок).
- **Риски/что может пойти не так**: `readUtf8()` потребляет body — при повторном чтении нужен `Buffer`, иначе `chain.proceed` отправит пустое тело, а подпись будет от полного (тест обязан покрыть «подписанное тело == отправленное тело»); добавление gzip-кодировки в будущем сломает HMAC молча (пометить в комментарии к interceptor'у).

### Решение TD-10 (Шаг 3.3): split `RELAY_SECRET` → RELAY_JWT_SECRET + RELAY_HMAC_SECRET + TRUSTED_SIGNER
- **Решение**: docker run relay-контейнера получает три env-переменных (`RELAY_JWT_SECRET`, `RELAY_HMAC_SECRET`, `TRUSTED_SIGNER`); в `.env` заводится два разных секрета; `RELAY_SECRET` помечается на удаление после grep-подтверждения.
- **Альтернативы** (рассмотрены и отклонены): 1) оставить один `RELAY_SECRET` на обе роли — компрометация одного значения = одновременная компрометация JWT-подписи и HMAC, принцип «один секрет = одна роль» нарушен; 2) переиспользовать RELAY_JWT_SECRET в качестве HMAC-секрета — экономия нулевая (это всё равно два разных значения в .env), а риск тот же, что в (1); 3) генерировать HMAC-секрет автоматически при деплое — секрет обязан быть известен app (3.2 подписывает те же HMAC-ключом), автогенерация в deploy-скрипте не доставит его в app-сборку → app начнёт получать 401.
- **Почему это лучшее**: split — минимальное изменение, закрывающее утечку F1 (один секрет на две роли); `TRUSTED_SIGNER` без fallback переводит accept-all по умолчанию в fail-closed (R3), а health-проверка `.trustedSigner ≠ "accept-all"` даёт детерминированную верификацию после деплоя.
- **Риски/что может пойти не так**: удалить `RELAY_SECRET` из кода до grep-подтверждения, что он нигде не используется как JWT_SECRET (relay/ и app/) — прод-поломка аутентификации; в CI workflow env-передача добавляется отдельно, иначе локальный .env и CI разойдутся.

### Решение TD-11 (Шаг 4.1): NetworkConfig строится из deployment JSON (broadcast)
- **Решение**: адреса контрактов и bundler URL читаются app из ресурса `deployment-<chainId>.json`, генерируемого из `forge script --broadcast` (например, `contracts/broadcast/Deploy.s.sol/<chainId>/run-latest.json`); в CI — diff-проверка «broadcast изменился → app-ресурс устарел → job падает».
- **Альтернативы** (рассмотрены и отклонены): 1) оставить хардкод в NetworkConfig.kt (L20) — источник правды размазан по коду и env, аудит F зафиксировал рассинхрон адресов; 2) новый backend-эндпоинт `/config`, app тянет адреса по сети при старте — добавляет сетевую зависимость для старта app (офлайн-режим ломается), усложняет деплой (backend должен деплоиться раньше и знать адреса), при этом источник правды всё равно broadcast JSON; 3) хардкод + CI-diff — половинчатое решение: две копии правды остаются, diff ловит расхождение постфактум, а не предотвращает.
- **Почему это лучшее**: broadcast JSON уже генерируется forge'ем на каждом деплое — это бесплатный единственный источник правды (контракты → JSON → NetworkConfig → все потребители: UserOperation.kt, RelayClient, EventIndexer-подписки); CI-diff даёт автоматический гейт синхронизации (мера R1), не требующий ручной сверки.
- **Риски/что может пойти не так**: структура run-latest.json (`transactions[].contractAddress`) может не совпасть с ожидаемой — сначала прочитать фактический файл, не выдумывать свой формат; на 97 (BSC testnet) и 31337 (anvil) адреса разные — нужен один JSON на chainId, иначе app возьмёт адреса не той сети.

### Решение TD-12 (Шаг 4.2): BUNDLER_URL — fail-fast при сборке, без fallback
- **Решение**: в `app/build.gradle.kts` убрать `?: "http://localhost:3000"` (L31/53/69); при отсутствии BUNDLER_URL в gradle.properties/env сборка падает с понятным сообщением.
- **Альтернативы** (рассмотрены и отклонены): 1) оставить локальный fallback — тихий мёртвый URL: приложение собирается, запускается, а все UserOperations уходят в никуда (это и есть Blocker #9); 2) проверять доступность bundler в рантайме при старте app — ошибка обнаруживается на устройстве пользователя, а не в CI; для отладки это худшее место и время; 3) дефолт на публичный bundler (Biconomy/Stackup testnet) — внешняя зависимость от стороннего сервиса в критичном пути, недоступность которого мы не контролируем.
- **Почему это лучшее**: паттерн fail-fast уже принят в проекте (JWT_SECRET и другие обязательные ключи, R3); сборка — самая ранняя точка, где ошибка конфигурации видна и дешёвая, прод и тестнет получают одинаковое поведение: нет URL → нет артефакта.
- **Риски/что может пойти не так**: локальная разработка без bundler перестанет собираться — это намеренно, но потребует задокументировать шаг «поднять bundler или задать URL» в README/доке (иначе первый же разработчик потратит время на «внезапную» ошибку сборки).

### Решение TD-13 (Шаг 4.3): реальная P-256/WebAuthn-подпись + pubKeyX/pubKeyY от app
- **Решение**: `CreateInviteRequest` (app) получает `guardianPubKeyX`/`guardianPubKeyY` из WebAuthn registration attestation; challenge подписывается реальной WebAuthn-подписью на устройстве (не mock); все запросы к relay — через RelayHmacInterceptor (TD-09). Relay не меняется.
- **Альтернативы** (рассмотрены и отклонены): 1) «подпись непустая»/mock-подпись на app — аудит C3 fail-open; relay уже верифицирует реальную P-256 подпись (index.ts L191-198, L228-234, L275-281 через `verifyP256Signature`, auth.ts L13-47) — mock просто получит 401/отклонение, фича не заработает; 2) PRF (Pseudo-Random Function) как «удобная замена» подписи — это симметричный ключ, а не криптографическая подпись, аудит явно зафиксировал разницу; relay-верификация P-256 с PRF-«подписью» несовместима в принципе; 3) подписывать challenge на relay (app шлёт только pubkey) — relay не имеет приватного ключа guardian: ключ живёт в WebAuthn-устройстве пользователя и никогда его не покидает, подпись физически возможна только в app.
- **Почему это лучшее**: relay-часть уже реализована и корректна (проверено: relay требует pubKeyX/Y в `POST /guardian/invite` L135 и верифицирует P-256); реальный разрыв — app не шлёт ключи и не подписан HMAC (401). Добавление полей в `CreateInviteRequest` + interceptor закрывает разрыв, не трогая работающий relay.
- **Риски/что может пойти не так**: challenge-формат — подписывается invite payload hash, не «весь JSON вперемешку»; порядок байт должен совпасть с ожиданием relay (RIP-7212: SHA-256(hash‖r‖s‖x‖y), 160 байт — см. TD-03) — рассинхрон формата даст «валидную подпись, которую никто не принимает»; проверить, что backend реально вызывает precompile, а не проверяет непустоту (C3).

### Решение TD-14 (Шаг 5.1): точечная актуализация DEPLOY-TESTNET.md, не переписывание
- **Решение**: обновить только секции дока, не соответствующие коду после фаз 0–4: v0.6 вместо v0.7, docker run с RELAY_JWT_SECRET/RELAY_HMAC_SECRET/TRUSTED_SIGNER, порядок деплоя contracts → relay → backend → app, обязательный шаг проверки TRUSTED_SIGNER на health.
- **Альтернативы** (рассмотрены и отклонены): 1) переписать весь док целиком — риск внести новые расхождения (план прямо предупреждает: «не переписывать весь док»), объём диффа несоразмерен цели; 2) оставить док как есть с пометкой «устарел» — аудит G1–G5 остаётся открытым, следующий человек повторит все ошибки деплоя (v0.7, RELAY_SECRET без TRUSTED_SIGNER); 3) автогенерация дока из deploy-скрипта (markdown из кода) — новый инструмент ради документации, которую читают люди; стоимость поддержки генератора выше пользы.
- **Почему это лучшее**: цель — «док == код», а не «красивый док»; точечные правки соответствуют фактическим изменениям фаз 0–4, а grep-верификация (`grep -n "0.7"` → 0, docker run секция == скрипту) делает соответствие проверяемым автоматически.
- **Риски/что может пойти не так**: пропустить секцию дока, где v0.7 упоминается в другом контексте (например, в истории изменений) — grep-критерий «0 совпадений» может заставить удалять исторические упоминания, что искажает историю; критерий стоит применять к инструктивным секциям.

### Решение TD-15 (Шаг 5.2): dry-run и broadcast как два явных шага с паузой на review
- **Решение**: deploy-testnet.sh разделяется на шаг 1 (`forge script Deploy.s.sol --dry-run` — показать план) и шаг 2 (`--broadcast --verify` — реальный деплой); между ними пауза на review (в CI — `workflow_dispatch` + комментарий с адресами, вручную — глазами).
- **Альтернативы** (рассмотрены и отклонены): 1) один `forge script --broadcast` без dry-run — нет точки review, непонятно что деплоится (это и есть G4 аудита); 2) только dry-run в CI, реальный деплой вручную — два разных набора команд в двух местах, риск расхождения «как проверяли» и «как деплоят»; 3) полностью автоматический деплой по merge — на тестнет допустимо, но теряется возможность заметить неожиданный контракт в плане деплоя до его создания на chain.
- **Почему это лучшее**: forge из коробки даёт dry-run и broadcast одной командой с разными флагами — оба шага используют один и тот же скрипт, расхождение исключено; HITL-пауза на security-критичном деплое (фабрика, SRM, Paymaster) соответствует принципу «план до действия», уже принятому в проекте.
- **Риски/что может пойти не так**: в CI пауза на review реализуется как отдельный запуск workflow — если workflow_dispatch не поддерживает продолжение с адресами, шаг 2 придётся параметризовать (передавать подтверждённый план), иначе review становится формальностью.

### Решение TD-16 (Шаг 5.3): INSURANCE_AUDITOR — проверка и DONE без изменения кода
- **Решение**: проверить два условия — (1) `.env.example` документирует `INSURANCE_AUDITOR_ADDRESS` как обязательный, (2) `DeployInsuranceFund.s.sol` использует тот же fail-closed паттерн (require без дефолта), а не fallback; если оба выполнены — пункт закрывается без изменения кода.
- **Альтернативы** (рассмотрены и отклонены): 1) менять Deploy.s.sol — факт: L69-71 уже fail-closed (`vm.envAddress` + `require(insuranceAuditor != address(0))` + `require(insuranceAuditor != deployer)`), менять нечего, правка была бы шумом; 2) добавить CI-проверку «деплой без INSURANCE_AUDITOR падает» — усиливает гарантию, но не требуется для закрытия пункта: require уже даёт fail-fast на уровне скрипта; 3) разрешить fallback на deployer — прямо запрещено вторым require (аудитор не может быть деплоером), это осознанная защита от конфликта интересов.
- **Почему это лучшее**: минимальное действие, соответствующее факту — основной код уже исправлен (вероятно, тем же fail-fast-паттерном, что и другие security-параметры, R3); оставшаяся неизвестность — документация и «сестринские» скрипты, и она разрешается чтением, а не рефакторингом.
- **Риски/что может пойти не так**: если `.env.example` не документирует ключ — пункт не DONE, а требует дописать одну строку в .env.example (это в скоупе шага); «проверить и не менять» легко вырождается в «посмотрел и забыл» — результат проверки надо зафиксировать в PR-описании.

### Решение TD-17 (Шаг 5.4): ProposalRepository — contract_address + params из I*.sol, а не переделка индексации
- **Решение**: `ProposalRepository` (L62) начинает слать `/v1/events` с `contract_address` (все контракты, чьи события нужны: SocialRecoveryModule, SessionKeyModule, ProposalModule, Paymaster — уточнить по use-case) и `params` с точными сигнатурами событий из `contracts/src/**/I*.sol` (grep `event `).
- **Альтернативы** (рассмотрены и отклонены): 1) переделать EventIndexer на «индексировать все события всех контрактов без фильтра» — дороже (память/парсинг), шумнее, и не чинит корень: запрос к `/v1/events` без адреса всё равно не вернёт нужные события; 2) захардкодить topic-сигнатуры в ProposalRepository — дублирование источника правды, рассинхрон при изменении контрактов (нарушение R1); 3) поллить события напрямую через RPC из app — у app нет RPC-доступа с устройства, это сетевая и security-нагрузка не по адресу.
- **Почему это лучшее**: сигнатуры событий уже существуют в `contracts/src/**/I*.sol` (единый источник правды, R1) — их надо прочитать grep'ом, а не восстанавливать из памяти; запрос с фильтром адресов — штатный режим `/v1/events`, изменение на стороне клиента, а не переделка индексатора.
- **Риски/что может пойти не так**: точный список `contract_address` зависит от use-case (какие события реально нужны app — ProposalCreated/Executed, RecoveryRequested/Executed) — список надо сверить с фактическим кодом app, иначе либо пере-индексация (лишние), либо слепота к нужным событиям (B4–B6 снова); ретраи/пагинация — проверить фактическую реализацию, не предполагать.

### Решение TD-18 (Шаг 5.5): EventIndexer — добавить Paymaster в конфигурацию, не переписывать
- **Решение**: добавить Paymaster (с его `paymasterEvents`, EventIndexer.kt L319-323) в список `IndexedContract`, передаваемый в EventIndexer в `Application.kt` (L227-234), — единственная недостающая строка конфигурации: список УЖЕ полный на 6 контрактов (Treasury, Proposal, PaymentSplitterFactory, SessionKeyModule, SocialRecoveryModule, DeadManSwitch), SRM и SessionKey уже индексируются, а `config.paymasterAddress` при этом обязателен (AppConfig.kt L118, `error("PAYMASTER_ADDRESS required")`); отдельно, вне зоны EventIndexer, сверить topic-сигнатуры в `WatchtowerService.kt` (L49-64) с фактическими сигнатурами SRM (тема B5 с `topics[3]` против `topics[0]` — зона Watchtower, не EventIndexer).
- **Альтернативы** (рассмотрены и отклонены): 1) полная переделка EventIndexer.kt — факт: `EventDefinitions` уже содержит topics для Proposal, SessionKeyModule, SocialRecoveryModule, DeadManSwitch, Paymaster, Treasury, PaymentSplitter, а `IndexedEvent` с `indexedCount`/`paramNames`/`walletParamIdx` уже умеет разбирать topics — переделка чинит несуществующую проблему; 2) переезд на сторонний индексатор (SubQuery/TheGraph) — внешняя инфраструктура вне репозитория ради 3–5 контрактов, новая операционная зависимость; 3) продублировать topics в ProposalRepository и WatchtowerService независимо — тот же рассинхрон источников правды, против которого R1.
- **Почему это лучшее**: факт подтверждён кодом — механика разбора (`IndexedEvent` с `indexedCount`/`paramNames`/`walletParamIdx`) и определения событий (`EventDefinitions`) уже готовы, в списке Application.kt не хватает ровно одного контракта; «добавить Paymaster» — минимальный diff (одна строка конфигурации) против рефакторинга, и он опирается на уже обязательную настройку `paymasterAddress` — индексация Paymaster получается без новой инфраструктуры и без переделки парсера.
- **Риски/что может пойти не так**: после добавления нужен тест разбора событий Paymaster по `paymasterEvents` (по аналогии с уже покрытыми контрактами — убедиться, что `IndexedEvent` корректно разбирает topics Paymaster); смешение зон ответственности (EventIndexer vs Watchtower) — B5 относится к Watchtower, править его в рамках «EventIndexer» шага нельзя; тема с `topics[3]`/`topics[0]` — сверить с реальным SRM-кодом, а не с памятью.

### Решение TD-19 (Шаг 5.6): убрать хардкод `fcmToken = ""` в app; backend не трогать
- **Решение**: в `RecoveryViewModel.kt` (L508) убрать хардкод `fcmToken = ""`; получать реальный FCM-токен (Firebase Messaging в app) и передавать его в `RelayClient.registerPushToken` (RelayClient.kt L141-145) — этот метод уже ведёт в существующий роут relay `POST /push/register` (index.ts L303-310, рабочий: `registerPushToken` в storage.ts, `sendPushNotification` в fcm.ts). Backend не трогать.
- **Альтернативы** (рассмотрены и отклонены): 1) отправлять push напрямую из app через FCM Admin SDK — серверный API-ключ FCM нельзя класть в app (компрометация ключа = спам/подмена push от имени всех пользователей); 2) добавить роут `POST /register-push-token` в backend — ОТКЛОНЕНА новым фактом: весь push-флоу идёт app → relay, backend в нём не участвует, поэтому backend-роут не будет вызван никогда — это тот же класс бага A4 («мёртвый код на несуществующем пути»); 3) периодический polling БД на новые события с отправкой из app — push должен триггериться backend-событиями (индексация on-chain событий), polling с устройства не даёт гарантии доставки и тратит батарею.
- **Почему это лучшее**: минимальный diff на стороне app — push-инфраструктура уже полностью есть на relay (роут, хранение токена, отправка через fcm.ts), а неработающий push вызван ровно одной строкой хардкода; серверный ключ FCM остаётся на relay и не попадает в app (см. альтернативу 1); факт подтверждён кодом: файла `app/.../fcm/FcmService.kt` в app нет, а `RelayClient.registerPushToken` уже указывает на правильный роут — править backend нечего.
- **Риски/что может пойти не так**: получение FCM-токена в app требует инициализации Firebase Messaging и обработки недоступности (нет Google Play Services / отказ в разрешениях) — нельзя снова подставлять пустой токен (это и был корень бага); формат тела запроса в `RelayClient.registerPushToken` (RelayClient.kt L141-145) сверить с ожиданиями relay `POST /push/register` (index.ts L303-310), не выдумывать; «токен в storage.ts» — уточнить поведение при нескольких устройствах одного пользователя (перезапись по кошельку или мульти-токен), иначе push уйдёт только на последнее устройство.

### Решение TD-20 (Шаг 5.7): мёртвый код — каждый объект решается индивидуально «удалить/пометить»
- **Решение**: для каждого объекта из списка аудита E (неиспользуемые env-ключи, ABI-поля и т.п.) — либо удалить (если не используется), либо пометить `@Deprecated` + ссылка на аудит; `RELAY_SECRET` не удалять, пока grep по `relay/` и `app/` не подтвердит отсутствие использования (см. TD-10).
- **Альтернативы** (рассмотрены и отклонены): 1) удалить всё сразу по списку аудита — `RELAY_SECRET` может использоваться как JWT_SECRET в app-коде (3.3), слепое удаление = прод-поломка аутентификации; 2) оставить всё с TODO-комментариями — аудит E не закрывается, мёртвый код продолжает вводить в заблуждение grep-аудиты и ревью; 3) «не трогать, пока не мешает» — мёртвый код не ломает рантайм, но ломает расследование (каждый следующий аудит тратит время на различение живого/мёртвого — это уже произошло с RELAY_SECRET).
- **Почему это лучшее**: точечные решения по каждому объекту с явным критерием (grep-доказательство неиспользования) сочетают безопасность (ничего живого не удаляется) и гигиену (мёртвое не остаётся); grep-верификация (`grep -rn "RELAY_SECRET"` → 0) делает результат проверяемым и обратимым.
- **Риски/что может пойти не так**: grep по именам переменных даёт false-negative, если использование идёт через динамическое имя/маппинг (например, `env[name]`) — при удалении проверять не только имя, но и «не используется как строка в config-маппинге»; `@Deprecated` без ссылки на аудит не помогает будущему читателю понять причину — требование «+ ссылка на аудит» обязательно.

---

*Конец раздела ADR-lite. Все 20 решений (TD-01…TD-20) соответствуют шагам плана 0.1–5.7; неопределённость по TD-18 (список IndexedContract в Application.kt) и TD-19 (роут push) снята проверкой кода: оба решения опираются на подтверждённые факты (6 контрактов уже индексируются, недостаёт Paymaster; роут `POST /push/register` уже есть на relay, backend в push-флоу не участвует). Только TD-17 остаётся зависимым от неопределённости (точный список contract_address по use-case) и потому предписывает «сверить, затем дополнить» вместо переделки.*

---

## Карта делегирования агентам (под ограничения моделей)

> Дополнение к плану: маршрутизация 20 шагов по агентам/моделям с учётом подтверждённых ограничений. big-pickle (opencode) НЕСТАБИЛЕН (аварии сегодня) — он используется ТОЛЬКО как planner/reviewer, 1 вызов на фазу, никогда 2 подряд. Весь код и тесты — на стабильных deepseek-v4-flash-free / nemotron-3-ultra-free. Локальные модели (8080/8081) ОФЛАЙН — не использовать. Промпт любому агенту ≤ 2000 токенов, 1 файл-scope на шаг (контекст free-моделей неизвестен).

### Таблица маршрутизации (все 20 шагов)

| Шаг | Агент | Модель | Файлы в scope | Риск | Fallback |
|---|---|---|---|---|---|
| 0.1 | coder | deepseek-v4-flash-free | `contracts/src/MDAOSmartAccount.sol` | HIGH: неверный import (PackedUserOperation) ломает сборку всех контрактов | retry той же модели ×1, промпт меньше |
| 0.2 | coder | deepseek-v4-flash-free | `contracts/script/Deploy.s.sol` | MED: recoveryCaller не установлен при создании → баг B2 возвращается | retry той же модели ×1 |
| 1.1 | security_auditor | nemotron-3-ultra-free | `contracts/src/SocialRecoveryModule.sol` (только `_isP256Working`) | HIGH: выдуманный «красивый» тест-вектор = ложный probe, accept-all. Retry той же модели НЕ снижает риск (галлюцинация крипто-констант повторится) — промпт жёстко требует КОПИРОВАНИЕ из RFC 6979 Appendix A.2.5 / NIST CAVP с ссылкой в комментарии, генерация чисел запрещена | retry ×1 (промпт ≤ 1000, источник указан); если вектор снова «из головы» — остановить шаг, вектор подставить вручную из RFC 6979 |
| 1.2 | coder | deepseek-v4-flash-free | `contracts/script/Deploy.s.sol` (вставка перед L107) | LOW: вставка не перед addRecoveryHook | retry той же модели ×1 |
| 1.3 | coder | deepseek-v4-flash-free | `contracts/script/DeploySocialRecoveryModule.s.sol` | MED: вернули дефолт verifier = тихий accept-all | retry той же модели ×1 |
| 2.1 | coder | deepseek-v4-flash-free | `backend/src/main/resources/db/migration/V<next>__unique_nickname.sql` (НОВЫЙ файл) | MED: неверный номер миграции; LOWER там, где nickname case-sensitive; пропущен pre-migration DO-блок (тихий фейл на дублях) | retry той же модели ×1 |
| 2.2 | coder | deepseek-v4-flash-free | `backend/.../service/SwapService.kt` + controller + юнит-тест | MED: сломан легитимный «swap на чужой адрес», если это фича | retry той же модели ×1 |
| 3.1 | tester | deepseek-v4-flash-free | `relay/src/auth.ts` + `app/.../RelayClient.kt` (read-only, код НЕ менять) | LOW: вывод «всё ок» без фиксации результата в PR | retry той же модели ×1 |
| 3.2 | coder | deepseek-v4-flash-free | `app/.../core/guardian/RelayClient.kt` + тест | HIGH: `readUtf8()` съедает body → HMAC по пустому телу, все запросы 401 | retry той же модели ×1 |
| 3.3 | coder | deepseek-v4-flash-free | `scripts/deploy-testnet.sh` + `.github/workflows/deploy-testnet.yml` | MED: RELAY_SECRET удалён раньше grep-подтверждения (см. 5.7) | retry той же модели ×1 |
| 4.1 | coder | deepseek-v4-flash-free | `app/.../core/NetworkConfig.kt` + `deployment-<chainId>.json` | MED: придуман «свой формат JSON» = второй источник правды (нарушение R1) | retry той же модели ×1 |
| 4.2 | coder | deepseek-v4-flash-free | `app/build.gradle.kts` + NetworkConfig | MED: fallback «localhost» вернулся = тихий мёртвый URL | retry той же модели ×1 |
| 4.3a | security_auditor | nemotron-3-ultra-free | `app/.../guardian/GuardianContracts.kt` (поля CreateInviteRequest) | HIGH: pubKeyX/Y не из attestation, а «из головы» | retry той же модели ×1 |
| 4.3b | security_auditor | nemotron-3-ultra-free | `app/.../guardian/GuardianManager.kt` (WebAuthn assertion) | HIGH: PRF вместо P-256 подписи; неверный challenge-формат (160 байт, см. 1.1) | retry той же модели ×1 |
| 4.3c | qa_tester | deepseek-v4-flash-free | e2e-тест (register → invite → recovery) | MED: тест не падает на старом коде (mock-подпись) | retry той же модели ×1 |
| 5.1 | coder | deepseek-v4-flash-free | `docs/DEPLOY-TESTNET.md` + `.github/workflows/deploy-testnet.yml` | LOW: «переписал весь док» вместо точечной актуализации | retry той же модели ×1 |
| 5.2 | coder | deepseek-v4-flash-free | `scripts/deploy-testnet.sh` | LOW: dry-run и broadcast слиты в один шаг (теряется review-пауза) | retry той же модели ×1 |
| 5.3 | tester | deepseek-v4-flash-free | `contracts/script/Deploy.s.sol` + `.env.example` (read-only) | LOW: «посмотрел и забыл» — результат не зафиксирован в PR | retry той же модели ×1 |
| 5.4 | coder | deepseek-v4-flash-free | `app/.../proposal/domain/ProposalRepository.kt` (L62) | MED: сигнатуры events из памяти, а не grep из `contracts/src/**/I*.sol` | retry той же модели ×1 |
| 5.5 | coder | deepseek-v4-flash-free | `backend/.../indexer/EventIndexer.kt` + `Application.kt` (конфиг) | MED: полез в WatchtowerService (B5 — чужая зона, read-only) | retry той же модели ×1 |
| 5.6 | coder | deepseek-v4-flash-free | `app/.../RecoveryViewModel.kt` (L508) + `app/.../RelayClient.kt` | MED: снова подставлен пустой fcmToken — корень бага B1 | retry той же модели ×1 |
| 5.7 | coder | deepseek-v4-flash-free | по списку аудита E (несколько файлов) | MED: удалён живой RELAY_SECRET (grep-доказательство обязательно) | retry той же модели ×1 |

### Порядок исполнения (S1–S29)

**Правила**: после каждого вызова big-pickle (planner/reviewer) — минимум 2 шага на deepseek-v4-flash-free; никогда 2 вызова big-pickle подряд; параллелить можно только агентов РАЗНЫХ моделей с непересекающимися файлами (rate limit внутри одной модели).

| # | Действие | Агент / Модель | big-pickle? |
|---|---|---|---|
| S1 | planner: верификация этой карты перед стартом | planner / big-pickle | ✅ |
| S2 | 0.1 MDAOSmartAccount.sol → BaseAccount v0.6 | coder / deepseek-v4-flash-free | — |
| S3 | 0.2 Deploy.s.sol: деплой MDAOSmartAccountFactory | coder / deepseek-v4-flash-free | — |
| S4 | reviewer: ревью фазы 0 (0.1, 0.2) | reviewer / big-pickle | ✅ |
| S5 | 1.2 approveHook перед addRecoveryHook | coder / deepseek-v4-flash-free | — |
| S6 | 1.3 P256_VERIFIER без дефолта, fail-fast | coder / deepseek-v4-flash-free | — |
| S7 | 1.1 P-256 probe на валидном NIST-векторе | security_auditor / nemotron-3-ultra-free | — |
| S8 | reviewer: ревью фазы 1 (1.1–1.3) | reviewer / big-pickle | ✅ |
| S9 | 2.1 UNIQUE-индекс на nickname | coder / deepseek-v4-flash-free | — |
| S10 | 2.2 SwapService: recipient = JWT-кошелёк | coder / deepseek-v4-flash-free | — |
| S11 | reviewer: ревью фазы 2 (2.1, 2.2) | reviewer / big-pickle | ✅ |
| S12 | 3.1 верификация nonce в auth.ts (read-only) | tester / deepseek-v4-flash-free | — |
| S13 | 3.2 RelayHmacInterceptor (OkHttp) | coder / deepseek-v4-flash-free | — |
| S14 | 3.3 RELAY_SECRET → RELAY_JWT/HMAC + TRUSTED_SIGNER | coder / deepseek-v4-flash-free | — |
| S15 | reviewer: ревью фазы 3 (3.1–3.3) | reviewer / big-pickle | ✅ |
| S16 | 4.1 NetworkConfig из deployment JSON | coder / deepseek-v4-flash-free | — |
| S17 | 4.2 BUNDLER_URL fail-fast при сборке | coder / deepseek-v4-flash-free | — |
| S18 | 4.3a CreateInviteRequest: guardianPubKeyX/Y | security_auditor / nemotron-3-ultra-free | — |
| S19 | 4.3b WebAuthn assertion (P-256, 160 байт) | security_auditor / nemotron-3-ultra-free | — |
| S20 | 4.3c e2e-тест recovery-флоу | qa_tester / deepseek-v4-flash-free | — |
| S21 | reviewer: ревью фазы 4 (4.1–4.3) | reviewer / big-pickle | ✅ |
| S22 | 5.1 DEPLOY-TESTNET.md актуализировать | coder / deepseek-v4-flash-free | — |
| S23 | 5.2 dry-run в 2 шага | coder / deepseek-v4-flash-free | — |
| S24 | 5.3 INSURANCE_AUDITOR проверка (read-only) | tester / deepseek-v4-flash-free | — |
| S25 | 5.4 ProposalRepository: address + params | coder / deepseek-v4-flash-free | — |
| S26 | 5.5 EventIndexer: добавить Paymaster | coder / deepseek-v4-flash-free | — |
| S27 | 5.6 FCM push: убрать хардкод fcmToken | coder / deepseek-v4-flash-free | — |
| S28 | 5.7 мёртвый код: удалить/пометить | coder / deepseek-v4-flash-free | — |
| S29 | reviewer: ревью фазы 5 (5.1–5.7) + финальный DoD | reviewer / big-pickle | ✅ |

**Проверка разноса big-pickle**: между S1→S4: S2,S3 (2 deepseek) ✓; S4→S8: S5,S6,S7 (2 deepseek + nemotron) ✓; S8→S11: S9,S10 ✓; S11→S15: S12,S13,S14 ✓; S15→S21: S16,S17 (+nemotron S18,S19, deepseek S20) ✓; S21→S29: S22–S28 (7 deepseek) ✓. Двух вызовов big-pickle подряд нет.

**Зависимости, которые порядок уже учитывает**: 0.2 после 0.1 (фабрика опирается на v0.6-аккаунт); 1.1–1.3 независимы по файлам (1.1 — контракт, 1.2/1.3 — скрипты); 4.3 после 3.2 (HMAC-подпись — обязательный вход для e2e 4.3c); 5.1 после фаз 0–4 (док актуализируется под итоговую конфигурацию).

### Что НЕ делать (запреты)

1. **НЕ запускать 2 вызова big-pickle подряд или параллельно** — подтверждённые аварии; соблюдать разнос из S1–S29 (минимум 2 шага deepseek между ними).
2. **НЕ запускать агентов одной модели параллельно** — rate limit (подтверждено); параллелить только разные модели с непересекающимися файлами (например, nemotron S18 и deepseek S20).
3. **НЕ делать молчаливый fallback на другую модель** при пустом ответе — только retry той же модели 1 раз с промптом ≤ 2000 токенов (для big-pickle — retry не в том же окне, а после ≥2 шагов deepseek).
4. **НЕ превышать лимит промпта** — ≤ 2000 токенов и 1 файл-scope на шаг; контекст free-моделей неизвестен, длинный промпт = потеря контекста.
5. **НЕ использовать локальные модели (8080/8081)** — офлайн, недоступны.
6. **НЕ назначать big-pickle на код/тесты** — только planner/reviewer, 1 вызов ревью на фазу, не на шаг; весь код — deepseek-v4-flash-free, security-шаги 1.1 и 4.3 — nemotron-3-ultra-free.
7. **НЕ выходить за file scope шага** (анти-галлюцинационный режим плана): grep-подтверждения обязательны перед удалением (RELAY_SECRET, сигнатуры events из I*.sol), read-only шаги (3.1, 5.3) — только чтение и фиксация результата.

### Разбиение крупных шагов на под-шаги

- **4.3 (разбит, 3 под-шага)**: a) `CreateInviteRequest` — поля `guardianPubKeyX/Y`; b) WebAuthn assertion (P-256, challenge-формат 160 байт из 1.1) → `GuardianManager.kt`; c) e2e-тест register → invite → recovery (обязан падать на старом коде). Причина: diff > 200 строк, крипто-логика отделена от полей модели и от теста.
- **5.7 (условно)**: не разбит заранее — список аудита E неизвестен точно; если в ходе выполнения grep покажет diff > 200 строк — разбить по объектам (каждый объект = 1 под-шаг coder, общий файл-scope «по аудиту E»).
- **Остальные шаги с 2 файлами (3.3, 5.1, 5.5, 5.6)**: не разбиты — diff мал (1 строка конфигурации в 5.5, пара env-строк в 3.3); второй файл — сопутствующий (workflow/env/read-only).
- **Итого в карте: 22 задачи** (20 шагов плана, из них 4.3 → 3 под-шага) + 6 ревью фаз + 1 planner = 29 операций (S1–S29).

**Итог**: вызовов big-pickle во всей карте — **7** (1 planner + 6 reviewer — по 1 на фазу 0–5; это минимум при правиле «1 ревью на фазу»). Остальные 22 операции — стабильные deepseek-v4-flash-free (19) и nemotron-3-ultra-free (3: 1.1, 4.3a, 4.3b); ревью фаз — строго reviewer/big-pickle, nemotron в ревью не участвует.
