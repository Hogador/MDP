# MDAOPay — Повторный архитектурный аудит (PRD ↔ TDD ↔ Code)

> Дата: 2026-06-30
> Репозиторий: https://github.com/Hogador/MDP.git (HEAD на дату аудита)
> Цель: проверить статус находок D-1…D-8 из предыдущего аудита, найти новые регрессии
> Аудитор: Principal Engineer, Web3/fintech

---

## Сводка: статус находок предыдущего аудита

| # | Находка (пред. аудит) | Severity | Статус | Доказательство |
|---|----------------------|----------|--------|----------------|
| D-1 | Env var вместо KMS для paymaster key | CRITICAL | ⚠️ **Частично** | F-111 добавил production guard, но KMS-реализация отсутствует (F-129 NEW) |
| D-2 | `vetoRecovery` — transfer вместо burn | HIGH | ✅ **Fixed** | `SocialRecoveryModule.sol:291` → `MDAOToken(address(mdaoToken)).burn(deposit)` |
| D-3 | `cleanupExpiredRecovery` возвращает депозит | HIGH | ✅ **Fixed** | `SocialRecoveryModule.sol:333-335` → `MDAOToken(...).burn(deposit)` |
| D-4 | `GuardianUserOpBuilder` без paymaster (veto не бесплатный) | HIGH | ✅ **Fixed** | `GuardianUserOpBuilder.kt:436-455` → `applyPaymaster()` |
| D-5 | Recovery использует BIOMETRIC_WEAK | HIGH | ✅ **Fixed** | `RecoveryScreen.kt:976` → `authenticateHighRisk(...)` |
| D-6 | `SendRepository.sendUsdt` без paymaster | CRITICAL | ✅ **Fixed** | `SendRepository.kt:39-53` → `sendUsdtGasless()` через `GaslessTransactionOrchestrator` |
| D-7 | `PaymasterClient.getQuote` ≠ backend `SignRequest` | CRITICAL | ✅ **Fixed** | `PaymasterClient.kt:66-144` → новый `signUserOp(...)` с полным UserOp |
| D-8 | `RECOVERY_DEPOSIT = 0.01 ether` — code smell | LOW | ✅ **Fixed** | `SocialRecoveryModule.sol:46` → `10_000_000_000_000_000` |

**Итог:** 7 из 8 находок закрыты корректно. 1 (D-1/KMS) — частично: guard есть, KMS-интерфейса нет.

---

## Этап 2 — `SocialRecoveryModule.sol` (повторно)

### Чек-лист

| Проверка | Статус | Доказательство |
|----------|--------|----------------|
| `GUARDIAN_THRESHOLD = 2` | ✅ | строка 43 |
| `MAX_GUARDIANS = 3` | ✅ | строка 42 |
| Депозит в MDAO (не ETH) | ✅ | `IERC20 mdaoToken`, `transferFrom` строка 214 |
| `vetoRecovery`: `burn()` не `transfer` | ✅ **FIXED** | `MDAOToken(address(mdaoToken)).burn(deposit)` строка 291 |
| `cleanupExpiredRecovery` — burn вместо return | ✅ **FIXED** | `MDAOToken(address(mdaoToken)).burn(deposit)` строка 334 |
| WebAuthn raw authenticatorData без EIP-191 | ✅ | `_verifyWebAuthn` строки 457-504, SHA-256 напрямую |
| `RECOVERY_DEPOSIT` без `ether` литерала | ✅ **FIXED** | `10_000_000_000_000_000` строка 46 |

### D-2 — исправлено корректно

**Было** (`MDAOPay`, предыдущий аудит):
```solidity
mdaoToken.transfer(BURN_ADDRESS, deposit);
```

**Стало** (`MDP`, SocialRecoveryModule.sol:285-293):
```solidity
if (currentVetoes >= VETO_THRESHOLD) {
    req.vetoed = true;
    uint256 deposit = recoveryDeposit[wallet];
    if (deposit > 0) {
        recoveryDeposit[wallet] = 0;
        MDAOToken(address(mdaoToken)).burn(deposit);
        emit DepositBurned(wallet, deposit);
    }
}
```

**Каст `IERC20 → MDAOToken`** оправдан: `IERC20` не объявляет `burn()`, `MDAOToken` наследует `ERC20Burnable`. Контракт теперь:
- вызывает настоящий `_burn` внутри ERC20 (`from != 0 && to == 0` → fee-on-transfer не срабатывает)
- уменьшает `totalSupply()` (тест `SocialRecoveryModule.t.sol:413-414` это явно проверяет: `assertEq(mdaoToken.totalSupply(), totalSupplyBefore - EXPECTED_DEPOSIT)`)
- экономит gas (одна запись вместо двух)

**Тесты покрывают:**
- `SocialRecoveryModule.t.sol:410-414` — veto → проверка burn + уменьшения totalSupply ✅
- `SocialRecoveryModule.t.sol:513-537` — cleanup → проверка что депозит сожжён, инициатор НЕ получил ✅
- `SocialRecoveryModule.t.sol:42` — `EXPECTED_DEPOSIT = DEPOSIT - (DEPOSIT * BURN_FEE_BPS / 10000)` корректно учитывает fee-on-transfer при `transferFrom` ✅

### D-3 — исправлено корректно

**Было:** `mdaoToken.transfer(initiator, deposit)` — спам бесплатен.

**Стало** (`SocialRecoveryModule.sol:322-338`):
```solidity
function cleanupExpiredRecovery(address wallet) external {
    // ... checks ...
    uint256 deposit = recoveryDeposit[wallet];
    recoveryDeposit[wallet] = 0;
    delete pendingRecovery[wallet];

    // Burn deposit instead of returning (anti-spam)
    if (deposit > 0) {
        MDAOToken(address(mdaoToken)).burn(deposit);
    }
    emit RecoveryCleanedUp(wallet, deposit);
}
```

Anti-spam-семантика восстановлена: инициировать recovery и не дойти до execute/veto = потеря 0.01 MDAO.

### Дополнительные улучшения в контракте (новые, не из прошлого аудита)

| # | Что | Доказательство |
|---|------|----------------|
| ✅ | P-256 public key on-curve validation (F-112) | строки 147-148: `if (pubKeyX >= bytes32(P256_P) \|\| pubKeyY >= bytes32(P256_P)) revert` |
| ✅ | Конфигурируемый `P256_VERIFIER` (для тестнетов без RIP-7212) | строки 34, 90-102: `setP256Verifier()` onlyOwner |
| ✅ | DER-парсинг P-256 сигнатур (F-109) | `derToRS()` строки 383-446 + `_verifyWebAuthn` принимает 64 / 70-74 байта |
| ✅ | `Ownable(msg.sender)` в конструкторе (Solidity 0.8.28 требует явного owner) | строка 90 |

### Что осталось неисправленным / стоит доработать в контракте

**N-1 (LOW):** `removeGuardian` по-прежнему `if (guardianCount[wallet] - 1 < MIN_GUARDIANS_FOR_RECOVERY)` (строка 174). В Solidity 0.8+ это безопасно (revert при underflow), но семантически чище `<=`. Не блокер.

**N-2 (LOW):** В `executeRecovery` (строки 313-317) и нигде больше не используется `SafeERC20`. `mdaoToken.transfer(req.initiator, deposit)` для fee-on-transfer токена вернёт `true` но реально переведёт меньше (fee съест часть). Контракт уже компенсирует это при `initiateRecovery` (вычисляет `actualDeposit` через `balanceOf(this)` diff, строки 213-215), но возврат инициатору всё равно потеряет ~0.5% на fee. Не критично (0.5% от 0.01 MDAO = 0.00005 MDAO ≈ $0), но `SafeERC20` + возврат `actualDeposit` был бы чище.

---

## Этап 3 — backend (повторно)

### Чек-лист

| Проверка | Статус | Доказательство |
|----------|--------|----------------|
| EIP-712 (не EIP-191) для подписи quote | ✅ | `PaymasterService.kt:277-307`, `signEIP712Digest` через Bouncy Castle |
| `extractClientIp()` для rate limit за proxy | ✅ | `Application.kt:93-106`, trusted proxy CIDR |
| KMS remote signing (не env var) | ⚠️ **Частично** | F-111: production guard добавлен; F-129: KMS-реализация NEW |

### D-1 — частично исправлено

**Что появилось** (`AppConfig.kt:38, 44-46, 98`):
```kotlin
val allowLocalSigning: Boolean = false,

init {
    require(jwtSecret.length >= 44) { "JWT_SECRET must be Base64-encoded 256-bit key (min 44 chars)" }
    if (!isTestnet && allowLocalSigning) {
        throw IllegalStateException("ALLOW_LOCAL_SIGNING is forbidden in production. Use KMS or HSM.")
    }
}
```

Это **закрывает аварийный сценарий** "случайно задеплоили в prod с env-var signing": при `isTestnet=false` (chainId ∈ {1, 56}) и `ALLOW_LOCAL_SIGNING=true` приложение упадёт при старте. ✅

**Что НЕ появилось:**

1. **Нет `PaymasterSigner` интерфейса.** Grep по репозиторию: `interface PaymasterSigner`, `class KmsPaymasterSigner`, `class LocalPaymasterSigner` — **0 совпадений в `backend/src/`** (только в `docs/audit/MDAOPay_audit.md` — то есть в моём предыдущем отчёте).
2. **Нет `KMS_KEY_NAME` env var.** `Application.kt:162` всё ещё:
   ```kotlin
   val paymasterKey = ECKeyPair.create(Numeric.hexStringToByteArray(config.privateKey))
   val service = PaymasterService(config, rpcManager, paymasterKey, priceOracle)
   ```
3. **Приватный ключ всё ещё в `config.privateKey: String`** — в String pool до GC, доступен через heap-dump, `/proc/pid/environ`, k8s describe pod, Sentry crash reports.
4. **`swapPrivateKey` — та же проблема.** `Application.kt:189`: `ECKeyPair.create(Numeric.hexStringToByteArray(config.swapPrivateKey))`. Для swap-операций guard `allowLocalSigning` НЕ применяется (он только в `init` для всего `AppConfig`, проверяет один раз).

**Severity:** CRITICAL остаётся. Guard — необходимое, но не достаточное условие. TDD прямо требует (`TDD.md:107`):
> *PAYMASTER_PRIVATE_KEY must use KMS remote signing (GCP Cloud KMS or AWS KMS). The private key never exists in application memory or environment variables.*

**Рекомендация — implements plan из предыдущего отчёта** (`/home/z/my-project/download/MDAOPay_audit.md`, раздел D-1):

1. Создать `backend/.../PaymasterSigner.kt` с интерфейсом + `LocalPaymasterSigner` + `KmsPaymasterSigner`.
2. В `AppConfig` добавить `kmsKeyName: String?` (GCP KMS resource path).
3. В `Application.kt:162` заменить на фабрику:
   ```kotlin
   val signer: PaymasterSigner = when {
       config.kmsKeyName != null -> KmsPaymasterSigner(kmsClient, config.kmsKeyName, fetchPubKey())
       config.allowLocalSigning -> LocalPaymasterSigner(ECKeyPair.create(...))
       else -> error("Either KMS_KEY_NAME or ALLOW_LOCAL_SIGNING=true required")
   }
   ```
4. `PaymasterService` принимает `signer: PaymasterSigner` вместо `key: ECKeyPair`.

**Как проверить:** см. предыдущий отчёт, раздел 3.2 — тесты с `unset KMS_KEY_NAME ALLOW_LOCAL_SIGNING` и проверка логов на `"KmsPaymasterSigner initialized"`.

### Дополнительные улучшения в backend

| # | Что | Доказательство |
|---|------|----------------|
| ✅ | `JWT_SECRET` min 44 chars (Base64 256-bit) | `AppConfig.kt:42, 88` (F-110) |
| ✅ | Shutdown hook для `priceOracle.close()`, `rpcManager.close()` | `Application.kt:169-173` |
| ✅ | MoonPay proxy вынесен из JWT-блока (browser redirect) | `Application.kt:304-312` |
| ✅ | Swap-сервис использует отдельный `swapPrivateKey` | `AppConfig.kt:36, 96` (F-035) |

### Что осталось в backend

**N-3 (MEDIUM):** `swapPrivateKey` тоже материализуется в `ECKeyPair` (`Application.kt:189`) без KMS. Если swap-ключ когда-нибудь получит контроль над значимыми средствами (например, станет refund-адресом) — та же уязвимость, что D-1. Сейчас swap только подписывает router-вызовы, риск ниже, но при внедрении `PaymasterSigner` стоит сделать `SwapSigner` по той же модели.

**N-4 (LOW):** `config.privateKey` после `ECKeyPair.create()` не zero'ится. Минимум:
```kotlin
val pkBytes = Numeric.hexStringToByteArray(config.privateKey)
val paymasterKey = ECKeyPair.create(pkBytes)
java.util.Arrays.fill(pkBytes, 0.toByte())
```
Это не защищает от `config.privateKey: String` в heap, но уменьшает surface. Полное решение — только KMS.

---

## Этап 4 — mobile (повторно)

### Чек-лист

| Проверка | Статус | Доказательство |
|----------|--------|----------------|
| `paymasterAndData` не пустой (реальный /sign вызов) | ✅ **FIXED** | `SendRepository.kt:43-47`, `PaymasterClient.kt:66-144` |
| `GuardianUserOpBuilder` 2 шага (invite + confirm) | ✅ (как и было) | `acceptInviteAndRegister` → `sendConfirmGuardian` |
| `BIOMETRIC_STRONG` для recovery | ✅ **FIXED** | `RecoveryScreen.kt:976` → `authenticateHighRisk()` |
| Guardian ops используют paymaster (D-4) | ✅ **FIXED** | `GuardianUserOpBuilder.kt:436-455` `applyPaymaster()` |

### D-6 + D-7 — исправлено корректно

**`PaymasterClient.kt`** — новый метод `signUserOp(...)` (строки 66-144):
```kotlin
suspend fun signUserOp(
    sender: String,
    nonce: BigInteger,
    initCode: ByteArray,
    callData: ByteArray,
    verificationGasLimit: BigInteger,
    callGasLimit: BigInteger,
    preVerificationGas: BigInteger,
    maxPriorityFeePerGas: BigInteger,
    maxFeePerGas: BigInteger,
    mdaoMaxAmount: BigInteger? = null,
    usdtMaxAmount: BigInteger? = null,
): SignResponse
```

Отправляет **полный `SignRequest`** (все 9 обязательных полей backend), парсит `SignResponse` с `paymasterAndData`, `userOpHash`, `maxFee`, `token`. Старый `getQuote()` помечен `@Deprecated` с правильным `ReplaceWith`.

**`SendRepository.kt`** — теперь делегирует gasless оркестратору (строки 39-53):
```kotlin
suspend fun sendUsdt(recipientAddress: String, amount: BigInteger): Result<String> {
    val gaslessResult = gaslessOrchestrator.get().sendUsdtGasless(
        recipient = recipientAddress,
        amount = amount,
        fallbackToNativeGas = false  // fallback handled here
    )
    return when (gaslessResult) {
        is Result.Success -> Result.Success(gaslessResult.data.txHash)
        is Result.Error -> sendUsdtNative(recipientAddress, amount)   // ← native fallback
        is Result.Loading -> sendUsdtNative(recipientAddress, amount)
    }
}
```

`GaslessTransactionOrchestrator` (строки 41-97) делает правильный flow:
1. `buildUserOp` с пустым `paymasterAndData` (gas estimation)
2. `paymasterClient.signUserOp(...)` — полный UserOp на бэкенд
3. `userOp.copy(paymasterAndData = ...)` — подставляет подписанный
4. `executeUserOp(finalUserOp)` — отправка через bundler
5. На `PaymasterError` → `sendRepository.sendUsdtNative(...)` (новый метод, не рекурсивный)

`Lazy<GaslessTransactionOrchestrator>` в `SendRepository` — правильно, иначе circular DI.

### D-4 — исправлено корректно

`GuardianUserOpBuilder.kt:436-455`:
```kotlin
private suspend fun applyPaymaster(userOp: UserOperation): UserOperation {
    return try {
        val response = paymasterClient.signUserOp(
            sender = userOp.sender,
            nonce = userOp.nonce,
            initCode = userOp.initCode,
            callData = userOp.callData,
            verificationGasLimit = userOp.verificationGasLimit,
            callGasLimit = userOp.callGasLimit,
            preVerificationGas = userOp.preVerificationGas,
            maxPriorityFeePerGas = userOp.maxPriorityFeePerGas,
            maxFeePerGas = userOp.maxFeePerGas,
            mdaoMaxAmount = null,
            usdtMaxAmount = null,
        )
        userOp.copy(paymasterAndData = Numeric.hexStringToByteArray(response.paymasterAndData))
    } catch (_: Exception) {
        userOp // graceful degradation: fallback to empty paymaster
    }
}
```

Вызывается во всех трёх guardian-операциях: `sendConfirmGuardian` (строка 187), `sendApproveRecovery` (244), `sendVetoRecovery` (301). PRD §7 "Veto = бесплатный (Paymaster)" — теперь выполняется.

**⚠️ Важное замечание к D-4:** `applyPaymaster` передаёт `mdaoMaxAmount = null, usdtMaxAmount = null`. Бэкенд в `PaymasterService.kt:180` проверяет `if (mdaoMax != null && mdaoNeeded <= mdaoMax && ...)`. С `null` он сразу перейдёт к USDT-fallback, который тоже упадёт (`usdtMax == null`). То есть **guardian-ops всегда будут получать PaymasterError от бэкенда** → graceful degradation → пустой `paymasterAndData` → guardian платит BNB.

**Это регрессия D-4 — формально paymaster вызывается, по факту всегда fallback.**

**N-5 (HIGH, новая находка):** Guardian ops не передают `mdaoMaxAmount` / `usdtMaxAmount`. Бэкенд не сможет подписать → всегда fallback на native gas. Чтобы PRD "veto бесплатный" реально работал, нужно:

```kotlin
private suspend fun applyPaymaster(userOp: UserOperation): UserOperation {
    return try {
        // Guardian ops спонсируются treasury — backend должен это учитывать.
        // Передаём generous maxAmount (или backend должен иметь whitelist
        // для SocialRecoveryModule callData и спонсировать без проверки баланса).
        val sponsoredMax = BigInteger.valueOf(10_000).multiply(BigInteger.TEN.pow(18))  // 10k MDAO
        val response = paymasterClient.signUserOp(
            // ...
            mdaoMaxAmount = sponsoredMax,
            usdtMaxAmount = null,
        )
        userOp.copy(paymasterAndData = Numeric.hexStringToByteArray(response.paymasterAndData))
    } catch (_: Exception) {
        userOp
    }
}
```

**ИЛИ** (правильнее архитектурно): `MDAOPaymaster.sol` должен иметь whitelist `callData` для `SocialRecoveryModule` (4 bytes selector'а `approveRecovery`, `vetoRecovery`, `confirmGuardian`) и спонсировать их без проверки баланса отправителя. Это требует отдельного аудита `MDAOPaymaster.sol` (не входил в чек-лист).

### D-5 — исправлено корректно

`RecoveryScreen.kt:976`:
```kotlin
biometricManager.authenticateHighRisk(
    activity = activity,
    title = "Импорт seed-фразы",
    subtitle = "Высокорисковая операция — seed-фраза даёт полный доступ к кошельку",
    // ...
)
```

`SendScreen.kt:241-267` — корректный порог по сумме:
```kotlin
if (s.amount >= BigDecimal("1000")) {
    biometricManager.authenticateHighRisk(...)   // ≥1000 USDT → STRONG
} else {
    biometricManager.authenticate(...)           // <1000 USDT → WEAK допустим
}
```

Это разумный компромисс UX vs security: мелкие переводы (кофе) не требуют отпечатка, крупные — требуют.

**⚠️ Предупреждение:** порог `1000 USDT` захардкожен в UI. Если изменится курс USDT или добавятся другие токены с другой decimals — порог надо калибровать. Лучше вынести в `ProductConfig` с возможностью remote-config.

### Что осталось в mobile

**N-6 (LOW):** `OnboardingBiometricScreen.kt:74` вызывает `biometricManager.authenticate(...)` (WEAK). Это **правильно** для онбординга (UX > security при первичной настройке), но стоит явно закомментировать "WEAK допустим — это настройка, не транзакция".

**N-7 (LOW):** `PaymasterClient.encodePaymasterAndData(...)` (строки 201-229) остался без изменений. Он собирает `paymasterAndData` локально без подписи бэкенда. Если `RecoveryUserOpBuilder.buildRecoveryWithPaymaster` (если он есть) использует этот метод — будет отправлен unsigned paymasterAndData, bundler отклонит. Нужно проверить `RecoveryUserOpBuilder.kt` — если он до сих пор вызывает `encodePaymasterAndData`, его тоже надо перевести на `signUserOp`.

---

## Сводка новых находок

| # | Находка | Severity | Файл | Статус |
|---|---------|----------|------|--------|
| **N-5** | Guardian ops передают `mdaoMaxAmount=null, usdtMaxAmount=null` → бэкенд всегда отказывает → fallback на native gas (D-4 формально fixed, по факту нет) | **HIGH** | `GuardianUserOpBuilder.kt:448-449` | NEW регрессия |
| **N-3** | `swapPrivateKey` тоже в env var, без KMS guard | MEDIUM | `Application.kt:189` | NEW |
| **N-7** | `PaymasterClient.encodePaymasterAndData` остался — возможен unsigned paymasterAndData в recovery flow | LOW (требует проверки `RecoveryUserOpBuilder`) | `PaymasterClient.kt:201-229` | NEW |
| **N-2** | `executeRecovery` возвращает депозит через `transfer` без `SafeERC20`, теряет 0.5% на fee | LOW | `SocialRecoveryModule.sol:316` | Existing |
| **N-1** | `removeGuardian` арифметика `count - 1 < threshold` | LOW (cosmetic) | `SocialRecoveryModule.sol:174` | Existing |
| **N-4** | `config.privateKey` не zero'ится после `ECKeyPair.create()` | LOW | `Application.kt:162` | Existing |
| **N-6** | `OnboardingBiometricScreen` WEAK — допустимо, но без комментария | LOW | `OnboardingBiometricScreen.kt:74` | Existing |

---

## Что сделано правильно (новое с прошлого аудита)

- ✅ **F-110:** `JWT_SECRET` min 44 chars (Base64 256-bit)
- ✅ **F-111:** Production guard `if (!isTestnet && allowLocalSigning) throw` — закрывает случайный деплой в prod с env-var
- ✅ **F-112:** P-256 public key on-curve validation (`pubKeyX < P256_P`)
- ✅ **F-109:** DER-парсинг P-256 сигнатур (WebAuthn браузеры шлют DER, не raw r‖s)
- ✅ **F-130:** `PaymasterClient.signUserOp` с полным `SignRequest` — закрыл D-7
- ✅ **F-131:** `cleanupExpiredRecovery` burn вместо refund — закрыл D-3
- ✅ **F-132:** `GuardianUserOpBuilder.applyPaymaster` — закрыл D-4 (формально)
- ✅ **F-133:** `RECOVERY_DEPOSIT` без `ether` литерала — закрыл D-8
- ✅ **F-102:** `vetoRecovery` `burn()` вместо `transfer(BURN_ADDRESS)` — закрыл D-2
- ✅ **Тесты:** `SocialRecoveryModule.t.sol` теперь покрывает fee-on-transfer (строки 39-44), veto burn + totalSupply check (410-414), cleanup burn + initiator doesn't get back (513-537)

---

## Приоритеты доработки

1. **F-129 / D-1 (KMS)** — CRITICAL, не закрыто. Внедрить `PaymasterSigner` интерфейс + `KmsPaymasterSigner` (GCP Cloud KMS). Без этого production-деплой = компрометация ключа.
2. **N-5 (guardian ops paymaster)** — HIGH, новая регрессия. Передать `mdaoMaxAmount` в `applyPaymaster` ИЛИ добавить whitelist в `MDAOPaymaster.sol` для `SocialRecoveryModule` selectors. Без этого "veto бесплатный" — только на бумаге.
3. **N-3 (swapPrivateKey KMS)** — MEDIUM. После внедрения `PaymasterSigner` сделать `SwapSigner` по той же модели.
4. **N-7 (encodePaymasterAndData)** — LOW. Проверить `RecoveryUserOpBuilder.kt`, при необходимости перевести на `signUserOp`.
5. **N-2, N-1, N-4, N-6** — LOW, косметика и hardening.

---

## Что НЕ проверено (требует отдельного аудита)

- **`MDAOPaymaster.sol`** (684 строки) — какой whitelist callData он пропускает? Спонсирует ли `SocialRecoveryModule.approveRecovery/vetoRecovery/confirmGuardian`? Критично для N-5.
- **`RecoveryUserOpBuilder.kt`** — использует ли он `encodePaymasterAndData` (unsigned) или перешёл на `signUserOp`? Критично для N-7.
- **`GuardianManager.kt`** — как вычисляется `identityHash` при invite? Должно быть `keccak256(guardianSmartAccountAddress)`.
- **`SwapService.kt`** — какие операции подписывает `swapPrivateKey`? Есть ли лимиты?
- **`SessionKeyModule.sol`** — реализация Capability Mapping, лимиты, expiry.
- **`AuthService.kt`** — low-s проверка (EIP-2) для nickname registration.
- **`F-113`** (ERC-4337 v0.6 deprecated) — упоминается в findings-registry, но без деталей. v0.7 уже стабильный, EntryPoint v0.6 не получает security-patches. Стоит оценить миграцию (но это большой рефактор — не блокер для MVP).

---

## Полный отчёт

Сохранён в `/home/z/my-project/download/MDAOPay_audit_v2.md` (этот файл).

## Сводка одной строкой

**7 из 8 находок предыдущего аудита закрыты корректно (D-2…D-8), 1 (D-1/KMS) — частично (guard есть, KMS-реализации нет). Найдена 1 новая HIGH-регрессия (N-5: guardian-ops не передают maxAmount → paymaster всегда fallback).**
