# MDAOPay — Архитектурный аудит (PRD ↔ TDD ↔ Code)

> Дата аудита: 2026-06-30
> Репозиторий: https://github.com/Hogador/MDAOPay.git (commit: HEAD на дату аудита)
> Объём: PRD (15 KB), TDD (130 KB), 9 .sol контрактов, Kotlin/Ktor backend, Android/Kotlin mobile, TS relay
> Аудитор: Principal Engineer, Web3/fintech

---

## Этап 1 — Изучение архитектуры (PRD + TDD)

### 1.1 Что реализовано (по фактам из кода)

| Слой | Реализовано | Файл(ы) |
|------|-------------|---------|
| Smart Contracts | `SocialRecoveryModule`, `MDAOPaymaster`, `MDAOToken` (ERC20Burnable+Permit+Pausable), `NicknameRegistry`, `SessionKeyModule`, `InsuranceFund`, `DeadManSwitch`, `AttestationLedger`, `TrustProviderRegistry`, `RefundVault`, `EcdsaVerifier` | `contracts/src/*.sol` |
| ERC-4337 | EntryPoint v0.6 (`0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789`), SimpleAccount factory, bundler-клиент | `app/.../erc4337/BundlerClient.kt`, `NetworkConfig.kt` |
| Backend | `/v1/sign`, `/v1/nickname/*`, `/v1/auth/*`, `/v1/onramp`, `/v1/swap`, `/v1/etherscan-proxy`, `/health`, `/metrics` (Prometheus), rate-limit (Redis, IP+sender), idempotency, watchtower, FCM-proxy | `backend/.../Application.kt`, `PaymasterService.kt`, `AuthService.kt`, `WatchtowerService.kt` |
| Paymaster quote | EIP-712 Quote digest (`keccak256(0x1901 ‖ domainSeparator ‖ structHash)`), raw ECDSA через Bouncy Castle (без EIP-191 rehash) | `PaymasterService.kt:277-341` |
| IP extraction | `extractClientIp()` с whitelist доверенных прокси (Cloudflare + RFC1918), fallback на `remoteHost` | `Application.kt:93-106` |
| Mobile onboarding | nickname, biometric, guardian, tutorial — 4 экрана | `app/.../onboarding/presentation/*` |
| Recovery (SSS) | 2-of-3 (4 share в не-hermit), passkey PRF, BiometricPrompt, Keystore, PBKDF2 600k | `RecoveryShareManager.kt`, `PasskeyManager.kt`, `BiometricManager.kt` |
| Guardian flow | invite (relay) → acceptInviteAndRegister (passkey + confirmGuardian on-chain) → approve/veto via WebAuthn | `GuardianUserOpBuilder.kt`, `GuardianManager.kt`, `RelayClient.kt` |
| WebAuthn на контракте | raw `authenticatorData` + SHA-256, без EIP-191 prefix, RIP-7212 precompile (`0x100`) | `SocialRecoveryModule.sol:368-410` |
| Identity Connect | `SessionKeyModule`, Capability Mapping (Kotlin) | `SessionKeyModule.sol`, `PermissionMapper.kt` |
| Relay | Cloudflare Workers, HMAC auth, invite storage, FCM push | `relay/src/*.ts` |
| Observability | LogSanitizer (PII redaction), SimpleJsonLayout, metrics, runbooks | `backend/.../util/*`, `docs/runbooks/*` |

### 1.2 Что НЕ реализовано / помечено как Non-Goal или Phase 2+

- iOS (Non-Goal → Phase 2)
- Multi-chain (Phase 3)
- Merchant NFC (Phase 4)
- Mesh Relay — оба офлайн (Phase 5)
- UIP как открытый стандарт (Phase 3)
- Transak/Onramper on-ramp (Phase 2) — сейчас только MoonPay + "мамкин обменник"

### 1.3 Расхождения PRD ↔ TDD ↔ Code (верхнеуровнево)

| # | Тема | PRD | TDD | Code | Severity |
|---|------|-----|-----|------|----------|
| D-1 | Paymaster private key | не уточняет | KMS remote signing обязательно, env-var только через `ALLOW_LOCAL_SIGNING=true` | env-var `PAYMASTER_PRIVATE_KEY`, флаг `ALLOW_LOCAL_SIGNING` отсутствует | **CRITICAL** |
| D-2 | vetoRecovery burn | "сжигается при veto" | (должно соответствовать PRD) | `mdaoToken.transfer(BURN_ADDRESS, deposit)` вместо `burn()` | **HIGH** |
| D-3 | cleanupExpiredRecovery | anti-spam deposit | anti-spam deposit | возвращает депозит инициатору при истечении → спам бесплатен | **HIGH** |
| D-4 | Veto = бесплатный (Paymaster gas) | "Veto = бесплатный (Paymaster покрывает gas)" | — | `GuardianUserOpBuilder` использует `paymasterAndData = ByteArray(0)` | **HIGH** |
| D-5 | BIOMETRIC_STRONG для recovery | (implicit high-risk) | F-062: high-risk только BIOMETRIC_STRONG | `RecoveryScreen` вызывает `biometricManager.authenticate(...)`, не `authenticateHighRisk()` | **HIGH** |
| D-6 | gasless send USDT | "Gasless (Paymaster, post-paid)" | — | `SendRepository.sendUsdt()` использует `paymasterAndData = ByteArray(0)`; только `GaslessTransactionOrchestrator` реально вызывает /sign | **CRITICAL** |
| D-7 | API contract /sign | — | SignRequest: sender, nonce, initCode, callData, verificationGasLimit, callGasLimit, preVerificationGas, maxPriorityFeePerGas, maxFeePerGas | `PaymasterClient.getQuote()` шлёт только `{sender, token, maxTokenAmount}` → 400 от бэкенда | **CRITICAL** |
| D-8 | Recovery deposit = 0.01 MDAO | "0.01 MDAO deposit" | — | `RECOVERY_DEPOSIT = 0.01 ether` (значение верно, но `ether` литерал для токена — code smell) | LOW |

---

## Этап 2 — Аудит `SocialRecoveryModule.sol`

**Файл:** `contracts/src/SocialRecoveryModule.sol` (411 строк)

### 2.1 Чек-лист пользователя

| Проверка | Статус | Доказательство |
|----------|--------|----------------|
| `THRESHOLD = 2` (не 3) | ✅ | `uint256 public constant GUARDIAN_THRESHOLD = 2;` (строка 37) |
| `MAX_GUARDIANS = 3` (не 5) | ✅ | `uint256 public constant MAX_GUARDIANS = 3;` (строка 36) |
| Депозит в MDAO (не ETH) | ✅ | `IERC20 public immutable mdaoToken;` (42), `mdaoToken.transferFrom(msg.sender, address(this), RECOVERY_DEPOSIT)` (195) |
| `vetoRecovery`: `burn()`, не `transfer` | ❌ | `mdaoToken.transfer(BURN_ADDRESS, deposit)` (строка 272) — см. D-2 |
| WebAuthn: raw `authenticatorData` без EIP-191 prefix | ✅ | `_verifyWebAuthn` использует SHA-256 напрямую (380, 393), без `toEthSignedMessageHash()` (см. 359-367) |

### 2.2 Расхождение D-2 — `vetoRecovery` использует `transfer` вместо `burn`

**Что не так** (`contracts/src/SocialRecoveryModule.sol:266-275`):

```solidity
if (currentVetoes >= VETO_THRESHOLD) {
    req.vetoed = true;
    // Burn deposit when recovery is vetoed
    uint256 deposit = recoveryDeposit[wallet];
    if (deposit > 0) {
        recoveryDeposit[wallet] = 0;
        mdaoToken.transfer(BURN_ADDRESS, deposit);   // ← transfer, не burn
        emit DepositBurned(wallet, deposit);
    }
}
```

`MDAOToken` наследует `ERC20Burnable` (`contracts/src/MDAOToken.sol:5,10`) — у него есть `burn(uint256)` и `burnFrom(address,uint256)`. Контракт вызывает `transfer(BURN_ADDRESS, deposit)` — это НЕ настоящий burn.

**Почему опасно:**

1. **Срабатывает fee-on-transfer.** `MDAOToken._update` (`MDAOToken.sol:41-49`) снимает `burnFeeBps = 50` (0.5%) с любого не-exempt перевода `from != 0 && to != 0`. Социальный модуль **не в `isExempt`** (в конструкторе токена exempt только `address(this)` — сам токен). В итоге:
   - 0.5% депозита идёт на `BURN_ADDRESS` как fee
   - оставшиеся 99.5% идут на `BURN_ADDRESS` как перевод
   - `totalSupply()` **не уменьшается** — токены просто меняют владельца на 0xdEaD
   - с точки зрения on-chain аналитики и DAO-метрик, MDAO "в обращении" (по totalSupply) хотя фактически выведены из оборота
2. **Семантическое расхождение с PRD.** PRD §7 прямо говорит "сжигается при veto". `transfer` ≠ "сжечь". Любой ревьюер security-audit это отметит.
3. **Несогласованность с `executeRecovery`.** Там `mdaoToken.transfer(req.initiator, deposit)` — перевод инициатору (PRD: "возвращается при success") — это правильно. Но для veto семантически должно быть удаление из supply, не перевод на dead-адрес.
4. **Лишний gas.** Из-за `burnFeeBps` контракт делает две записи в storage (`_update` → `super._update(from, BURN_ADDRESS, fee)`, затем `super._update(from, to, value)`), вместо одного `_burn`.

**Как исправить** (реальный патч):

```solidity
// SPDX: в интерфейс IERC20Burnable не нужно — MDAOToken уже ERC20Burnable
// Сохранить IERC20-ссылку на более узкий тип нельзя (burn в IERC20 нет),
// поэтому меняем тип storage-поля:

import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import {IERC20Burnable} from "@openzeppelin/contracts/token/ERC20/extensions/IERC20Burnable.sol"; // OZ v5 имеет этот интерфейс

contract SocialRecoveryModule {
    // ...
    IERC20Burnable public immutable mdaoToken;   // ← расширяем интерфейс

    constructor(address _mdaoToken) {
        mdaoToken = IERC20Burnable(_mdaoToken);
    }
    // ...
    function vetoRecovery(...) external {
        // ...
        if (currentVetoes >= VETO_THRESHOLD) {
            req.vetoed = true;
            uint256 deposit = recoveryDeposit[wallet];
            if (deposit > 0) {
                recoveryDeposit[wallet] = 0;
                // Real burn: вызывает _burn внутри ERC20Burnable,
                // уменьшает balanceOf(this) и totalSupply().
                // Fee-on-transfer НЕ срабатывает, т.к. _burn использует _update(from, address(0), value),
                // а в MDAOToken._update fee применяется только если from != 0 && to != 0.
                mdaoToken.burn(deposit);
                emit DepositBurned(wallet, deposit);
            }
        }
        // ...
    }
}
```

Если `IERC20Burnable` недоступен (старая OZ), альтернатива — `SafeERC20` + низкоуровневый call:

```solidity
import {SafeERC20} from "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";
// ...
using SafeERC20 for IERC20;
// ...
(bool ok,) = address(mdaoToken).call(abi.encodeWithSignature("burn(uint256)", deposit));
require(ok, "burn failed");
```

**Дополнительно:** для `executeRecovery` (строка 297) и `cleanupExpiredRecovery` (строка 316) также желательно использовать `SafeERC20.safeTransfer` вместо `transfer` — текущий код не проверяет return value, что для fee-on-transfer токенов может дать silent failure.

### 2.3 Расхождение D-3 — `cleanupExpiredRecovery` возвращает депозит инициатору

**Что не так** (`SocialRecoveryModule.sol:303-320`):

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

    // Return deposit to initiator instead of burning  ← КОММЕНТАРИЙ В КОДЕ
    if (deposit > 0 && initiator != address(0)) {
        mdaoToken.transfer(initiator, deposit);
    }
    emit RecoveryCleanedUp(wallet, deposit);
}
```

**Почему опасно:**

PRD §7 определяет депозит как **anti-spam**: "Anti-spam: 0.01 MDAO deposit (возвращается при success, сжигается при veto)". Из трёх исходов recovery:
- **success** → вернуть инициатору ✅ (`executeRecovery` это делает)
- **veto** → сжечь ✅ (`vetoRecovery` это делает — хотя и через `transfer`, см. D-2)
- **expiry** (не success, не veto) → PRD не уточняет, но anti-spam-семантика подразумевает loss

Текущий код возвращает депозит по истечении. Это означает, что **злоумышленник может бесконечно спамить recovery-запросами без каких-либо затрат**: инициировать → подождать 96 часов (`TIMELOCK + EXECUTION_WINDOW`) → вызвать `cleanupExpiredRecovery` → вернуть депозит → повторить.

Каждая итерация:
- триггерит push-уведомления всем 3 guardian'ам
- блокирует владельца кошелька от легитимного recovery на 96 часов (т.к. `ErrRecoveryAlreadyActive`)
- нагружает watchtower, бэкенд, relay
- расходует gas у guardian'ов если они попытаются veto

**Как исправить** — два варианта, оба правильные:

**Вариант A (безопаснее, рекомендуется):** сжигать депозит при истечении.

```solidity
function cleanupExpiredRecovery(address wallet) external {
    RecoveryRequest storage req = pendingRecovery[wallet];
    if (req.startedAt == 0) revert ErrNoActiveRecovery();
    if (req.executed) revert ErrRecoveryExecuted();
    if (block.timestamp <= req.startedAt + TIMELOCK + EXECUTION_WINDOW) revert ErrNoExpiredRecovery();

    uint256 deposit = recoveryDeposit[wallet];
    address walletOwner = wallet;   // вернуть владельцу, не инициатору
    recoveryDeposit[wallet] = 0;
    delete pendingRecovery[wallet];

    if (deposit > 0) {
        // Вариант 1: сжечь (соответствует anti-spam семантике)
        mdaoToken.burn(deposit);
        // Вариант 2: вернуть владельцу кошелька (компенсация за беспокойство)
        // mdaoToken.transfer(walletOwner, deposit);
        emit DepositBurned(wallet, deposit);
    }
    emit RecoveryCleanedUp(wallet, deposit);
}
```

**Вариант B:** вернуть владельцу кошелька, а не инициатору. Так инициатор-злоумышленник теряет депозит, а пострадавший владелец получает компенсацию.

```solidity
// В коде выше — раскомментировать вариант 2 и убрать burn.
```

**Как проверить:**

```bash
# 1. Юнит-тест на истёкшее recovery + проверка, что инициатор НЕ получил депозит обратно
forge test -vv --match-test test_CleanupExpired_BurnsDepositOrRefundsOwner -C contracts
# 2. Инвариант: суммарный balanceOf(SocialRecoveryModule) + totalSupply() не увеличивается после cleanup
# 3. Fuzz-тест: случайные sequence initiateRecovery → cleanupExpiredRecovery не дают initiator профит
```

### 2.4 Дополнительно: `RECOVERY_DEPOSIT = 0.01 ether` — code smell

`uint256 public constant RECOVERY_DEPOSIT = 0.01 ether;` (строка 40) — значение **численно верное** (`0.01 * 10^18 = 10^16` wei, и MDAO имеет 18 decimals), но использование литерала `ether` для токена вводит в заблуждение. Любой ревьюер, читающий контракт, на секунду подумает, что депозит в ETH.

**Исправление:**

```solidity
uint256 public constant RECOVERY_DEPOSIT = 10_000_000_000_000_000; // 0.01 MDAO (18 decimals)
// или
uint256 public constant RECOVERY_DEPOSIT = 0.01 * 10**18;            // 0.01 MDAO
```

### 2.5 Дополнительно: `removeGuardian` — рискованная арифметика

`if (guardianCount[wallet] - 1 < MIN_GUARDIANS_FOR_RECOVERY)` (строка 155) — в Solidity 0.8+ это revert при `guardianCount == 0`, но если когда-нибудь переключат на unchecked-блок, возникнет underflow и проверка пройдёт. Безопаснее:

```solidity
if (guardianCount[wallet] <= MIN_GUARDIANS_FOR_RECOVERY) revert ErrCannotRemove();
```

---

## Этап 3 — Аудит backend (`PaymasterService.kt` + `Application.kt` + `AppConfig.kt`)

### 3.1 Чек-лист пользователя

| Проверка | Статус | Доказательство |
|----------|--------|----------------|
| EIP-712 (не EIP-191) для подписи quote | ✅ | `computeEIP712QuoteHash` (277-307), `signEIP712Digest` через Bouncy Castle `ECDSASigner` без rehash (323-341) |
| `extractClientIp()` для rate limiting за reverse proxy | ✅ | `Application.kt:93-106`, trusted proxy CIDR check (47-86), используется в `/sign` (226), `/auth/*`, `/etherscan-proxy` |
| KMS remote signing (не env var private key) | ❌ | `AppConfig.kt:56` + `Application.kt:162` — env var, без KMS — см. D-1 |

### 3.2 Расхождение D-1 — Env var вместо KMS

**Что не так** (`backend/src/main/kotlin/com/mdaopay/paymaster/AppConfig.kt:56`):

```kotlin
val privateKey = env["PAYMASTER_PRIVATE_KEY"] ?: error("PAYMASTER_PRIVATE_KEY required")
```

И `Application.kt:162`:

```kotlin
val paymasterKey = ECKeyPair.create(Numeric.hexStringToByteArray(config.privateKey))
val service = PaymasterService(config, rpcManager, paymasterKey, priceOracle)
```

Приватный ключ пэймастера загружается из env var как hex-строка и материализуется в `ECKeyPair` в памяти процесса. Никакого KMS нет.

**TDD говорит обратное** (`TDD/TDD.md:107`):

> **Key security:** `PAYMASTER_PRIVATE_KEY` must use KMS remote signing (GCP Cloud KMS or AWS KMS). The private key never exists in application memory or environment variables. The signing service calls `kms.sign()` via gRPC/HTTP API. Fallback: env-var signing for non-production environments only, with explicit `ALLOW_LOCAL_SIGNING=true` flag.

И `TDD.md:2379`:

```
| `ALLOW_LOCAL_SIGNING` | true | Dev-only: env-var signing instead of KMS |
```

**Почему опасно:**

1. **Компрометация памяти процесса = дрен пэймастера.** Любой attackers с доступом к heap-dump (JVM Profiler, OOM dump, `-XX:+HeapDumpOnOutOfMemoryError`) восстанавливает `ECKeyPair`.
2. **Env var = leak surface.** `/proc/<pid>/environ`, docker inspect, k8s describe pod, CI logs при ошибке запуска, crash reports (Sentry) — везде может оказаться `PAYMASTER_PRIVATE_KEY`.
3. **Нет ротации.** TDD требует ротацию 90 дней (`TDD.md:2264: Paymaster private key | 90 days | Automated via secret rotation`). Env var требует передеплоя; KMS-ключ ротируется без изменения приложения.
4. **Нет audit log.** KMS пишет в Cloud Audit Log каждый вызов `sign()`. Env var — нет.
5. **`PAYMASTER_PRIVATE_KEY` отдаётся в `ECKeyPair.create()` без zeroing.** В Kotlin/Java byte-массивы не zero'ятся после использования (GC не детерминирован, да и `byte[]` mutable но не очищается).

**Как исправить** — абстракция `PaymasterSigner` с двумя реализациями:

```kotlin
// backend/src/main/kotlin/com/mdaopay/paymaster/PaymasterSigner.kt
package com.mdaopay.paymaster

import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric
import java.math.BigInteger

/** Абстракция над подписью EIP-712 Quote digest'а. */
interface PaymasterSigner {
    /** Подписывает 32-байтовый EIP-712 digest, возвращает (v, r, s). */
    suspend fun signDigest(digest: ByteArray): Triple<Byte, ByteArray, ByteArray>

    /** Ethereum-адрес подписывающего (для проверки в тестах). */
    fun address(): String
}

/** Локальная реализация — ТОЛЬКО для dev/test, явно включается флагом. */
class LocalPaymasterSigner(private val keyPair: ECKeyPair) : PaymasterSigner {
    override suspend fun signDigest(digest: ByteArray): Triple<Byte, ByteArray, ByteArray> {
        // Без Sign.signMessage() — он добавляет EIP-191 prefix.
        // Использует Bouncy Castle напрямую, как в существующем signEIP712Digest().
        return PaymasterService.signEIP712DigestPublic(digest, keyPair)
    }
    override fun address() = org.web3j.crypto.Keys.getAddress(keyPair.publicKey)
}

/** KMS-реализация для production (GCP Cloud KMS). */
class KmsPaymasterSigner(
    private val kmsClient: com.google.cloud.kms.v1.KeyManagementServiceClient,
    private val keyName: String,           // projects/{p}/locations/global/keyRings/{r}/cryptoKeys/{k}
    private val publicKeyPoint: Pair<BigInteger, BigInteger>,  // (x, y) — публикуется, не секрет
) : PaymasterSigner {

    override suspend fun signDigest(digest: ByteArray): Triple<Byte, ByteArray, ByteArray> {
        // 1. KMS.sign() с DigestType.SHA256 — возвращает DER-encoded ECDSA signature.
        val response = kmsClient.asymmetricSign(
            keyName,
            com.google.cloud.kms.v1.Digest.newBuilder()
                .setSha256(com.google.protobuf.ByteString.copyFrom(digest))
                .build()
        )
        val derSig = response.signature.toByteArray()

        // 2. DER → (r, s)
        val (r, s) = derToRs(derSig)

        // 3. Low-S normalization (EIP-2) — KMS может вернуть high-s
        val n = org.bouncycastle.crypto.ec.CustomNamedCurves.getByName("secp256k1").n
        val sNormalized = if (s > n.shiftRight(1)) n.subtract(s) else s

        // 4. recovery id — нужно либо хранить pubKey в KMS и пробовать оба,
        //    либо подписывать дважды (тестируется в CI).
        //    Простейший подход: KMS возвращает signature без recovery id,
        //    поэтому ecrecover-проверкой на клиенте (как в текущем signEIP712Digest).
        val recId = (0..1).firstOrNull { rec ->
            try {
                val recovered = org.web3j.crypto.Sign.recoverFromSignature(
                    rec, org.web3j.crypto.ECDSASignature(r, sNormalized), digest
                )
                recovered == publicKeyFromPoint()
            } catch (_: RuntimeException) { false }
        } ?: error("Cannot determine recovery ID for KMS signature")

        return Triple(
            (27 + recId).toByte(),
            Numeric.toBytesPadded(r, 32),
            Numeric.toBytesPadded(sNormalized, 32)
        )
    }

    override fun address() = org.web3j.crypto.Keys.getAddress(
        org.web3j.crypto.Sign.publicPointFromComponents(publicKeyPoint.first, publicKeyPoint.second)
    )

    private fun publicKeyFromPoint() = org.web3j.crypto.Sign.publicPointFromComponents(
        publicKeyPoint.first, publicKeyPoint.second
    )

    private fun derToRs(der: ByteArray): Pair<BigInteger, BigInteger> {
        val parser = org.bouncycastle.asn1.ASN1InputStream(der)
        val seq = parser.readObject() as org.bouncycastle.asn1.DLSequence
        val r = (seq.getObjectAt(0) as org.bouncycastle.asn1.ASN1Integer).value
        val s = (seq.getObjectAt(1) as org.bouncycastle.asn1.ASN1Integer).value
        return Pair(r, s)
    }
}
```

В `Application.kt`:

```kotlin
val signer: PaymasterSigner = when {
    config.kmsKeyName != null -> {
        val kmsClient = com.google.cloud.kms.v1.KeyManagementServiceClient.create()
        val pubKey = fetchKmsPublicKey(kmsClient, config.kmsKeyName)  // (x, y) из KMS public key API
        KmsPaymasterSigner(kmsClient, config.kmsKeyName, pubKey)
    }
    config.allowLocalSigning == true -> {
        log.warn("⚠️ Local signing ENABLED — DO NOT use in production")
        LocalPaymasterSigner(ECKeyPair.create(Numeric.hexStringToByteArray(config.privateKey)))
    }
    else -> error("Production requires KMS_KEY_NAME; set ALLOW_LOCAL_SIGNING=true only for dev")
}

val service = PaymasterService(config, rpcManager, signer, priceOracle)
```

В `AppConfig.kt` добавить:

```kotlin
data class AppConfig(
    // ...
    val kmsKeyName: String?,                // GCP KMS: projects/{p}/locations/global/keyRings/{r}/cryptoKeys/{k}
    val allowLocalSigning: Boolean,         // явный opt-in для dev
) {
    companion object {
        fun fromEnv(): AppConfig {
            // ...
            val kmsKeyName = env["KMS_KEY_NAME"]    // null в dev
            val allowLocalSigning = env["ALLOW_LOCAL_SIGNING"]?.toBooleanStrictOrNull() ?: false

            // Если KMS не настроен и локальный signing не разрешён — отказ:
            if (kmsKeyName == null && !allowLocalSigning) {
                error("Either KMS_KEY_NAME (prod) or ALLOW_LOCAL_SIGNING=true (dev) required")
            }
            // Если KMS настроен, но кто-то забыл выключить local — игнорируем local:
            if (kmsKeyName != null && allowLocalSigning) {
                log.warn("KMS_KEY_NAME set — ignoring ALLOW_LOCAL_SIGNING")
            }
            // private key нужен только для local signing:
            val privateKey = if (allowLocalSigning && kmsKeyName == null) {
                env["PAYMASTER_PRIVATE_KEY"] ?: error("PAYMASTER_PRIVATE_KEY required when ALLOW_LOCAL_SIGNING=true")
            } else null
            // ...
        }
    }
}
```

`PaymasterService` модифицируется: вместо `private val key: ECKeyPair` принимает `private val signer: PaymasterSigner`, и `stampPaymasterAndData` вызывает `signer.signDigest(hash)`.

**Как проверить:**

```bash
# 1. Dev-режим без KMS и без флага → приложение падает при старте:
unset KMS_KEY_NAME ALLOW_LOCAL_SIGNING
./gradlew run   # должно упасть с "Either KMS_KEY_NAME (prod) or ALLOW_LOCAL_SIGNING=true (dev) required"

# 2. Dev-режим с флагом → работает как раньше:
ALLOW_LOCAL_SIGNING=true PAYMASTER_PRIVATE_KEY=0x... ./gradlew run

# 3. Prod-режим с KMS:
KMS_KEY_NAME=projects/my/locations/global/keyRings/paymaster/cryptoKeys/signing-key ./gradlew run
# В логе должно быть "KmsPaymasterSigner initialized", никаких WARN про local signing.

# 4. Интеграционный тест: подпись от KmsPaymasterSigner восстанавливается
#    Solidity ecrecover в тот же адрес, что и LocalPaymasterSigner.
#    (Нужен KMS mock в CI — GCP KMS emulator или HSM mock.)
```

### 3.3 Дополнительно: `paymasterKey` не zero'ится после загрузки

Даже если оставить env-var подход, `config.privateKey: String` остаётся в heap до GC. Минимум:

```kotlin
val privateKeyBytes = Numeric.hexStringToByteArray(config.privateKey)
val paymasterKey = ECKeyPair.create(privateKeyBytes)
java.util.Arrays.fill(privateKeyBytes, 0.toByte())   // затереть исходные байты
```

Это не защищает от heap-dump (строка `config.privateKey` всё ещё в String pool), но уменьшает surface. Полное решение — только KMS.

### 3.4 Положительные находки (что сделано правильно)

- ✅ **EIP-712 правильно реализован.** `computeEIP712QuoteHash` использует `0x1901` prefix (строки 286-306), `signEIP712Digest` использует Bouncy Castle `ECDSASigner` без re-hashing (323-341). Комментарий (265-268) явно предупреждает: "Do NOT use Sign.signMessage() — it applies Hash.sha3() internally".
- ✅ **`extractClientIp` корректно проверяет trusted proxy.** `TRUSTED_PROXIES` список (47-66) включает Cloudflare ranges + RFC1918. `isIpInCidr` (69-86) проверяет CIDR без внешних зависимостей. `extractClientIp` (93-106) читает `X-Forwarded-For` только если `remoteHost` в trusted ranges.
- ✅ **Rate limit есть на обоих уровнях.** Sender (1/30s) и IP (20/60s) — `Application.kt:227-239`. Idempotency cache по `sender:nonce` (242-246).
- ✅ **EIP-2 (low-s) проверка в NicknameRegistry flow.** `TDD.md:228` упоминает, и `AuthService.kt` должен это делать (нужно проверить в файле).

---

## Этап 4 — Аудит mobile (`SendRepository.kt` + `GuardianUserOpBuilder.kt` + `BiometricManager.kt` + `RecoveryScreen.kt`)

### 4.1 Чек-лист пользователя

| Проверка | Статус | Доказательство |
|----------|--------|----------------|
| `paymasterAndData` не пустой (реальный /sign вызов) | ❌ | `SendRepository.sendUsdt` (32-40) дефолтит `ByteArray(0)`; `GuardianUserOpBuilder.buildUserOp` (413-426) хардкодит `ByteArray(0)` |
| `GuardianUserOpBuilder`: 2 шага (invite + confirm) | ⚠️ Частично | `acceptInviteAndRegister` делает только `confirmGuardian` (1 шаг). Invite (`addGuardian`) делается владельцем отдельно через `GuardianManager` |
| `BIOMETRIC_STRONG` (не WEAK) для recovery | ❌ | `RecoveryScreen.kt:170, 973` вызывает `biometricManager.authenticate(...)`, не `authenticateHighRisk()` |

### 4.2 Расхождение D-6 + D-7 — Gasless send broken on multiple layers

**Что не так (часть 1 — `SendRepository.kt`):**

```kotlin
// app/src/main/java/com/mdaopay/app/core/blockchain/SendRepository.kt:24-28
@Singleton
class SendRepository @Inject constructor(
    private val walletManager: WalletManager,
    private val ethereumClient: EthereumClient,
    private val bundlerClient: BundlerClient
    // ← PaymasterClient НЕ инжектирован
) {
    // ...
    suspend fun sendUsdt(recipientAddress: String, amount: BigInteger): Result<String> {
        val userOpResult = buildUserOp(recipientAddress, amount)   // ← 2 аргумента
        // ...
    }

    suspend fun buildUserOp(
        recipientAddress: String,
        amount: BigInteger,
        paymasterAndData: ByteArray = ByteArray(0)   // ← дефолт пустой
    ): Result<UserOperation> = withContext(Dispatchers.IO) {
        // ... build UserOp with empty paymasterAndData
    }
}
```

`SendRepository` не имеет ссылки на `PaymasterClient`. Метод `sendUsdt()` никогда не вызывает `/sign`. Любой код, который дёргает `sendRepository.sendUsdt()`, отправляет UserOp с **пустым** `paymasterAndData` — это значит, что gas платится из нативного BNB баланса SmartAccount, а не покрывается пэймастером.

Это нарушает PRD §15: "P2P | Отправитель (Paymaster) | Никто" — то есть все P2P переводы должны быть gasless.

**Что не так (часть 2 — `GaslessTransactionOrchestrator.kt`):**

Оркестратор пытается делать правильно, но его API-контракт с бэкендом не соответствует:

```kotlin
// app/.../GaslessTransactionOrchestrator.kt:52-64
val quote = paymasterClient.getQuote(
    sender = walletData.address,
    token = NetworkConfig.USDT_CONTRACT,
    maxTokenAmount = amount
)
val userOpResult = sendRepository.buildUserOp(
    recipientAddress = recipient,
    amount = amount,
    paymasterAndData = quote.paymasterAndData
)
```

```kotlin
// app/.../paymaster/PaymasterClient.kt:35-48
suspend fun getQuote(
    sender: String,
    token: String,
    maxTokenAmount: BigInteger
): Quote = withContext(Dispatchers.IO) {
    val bodyJson = JSONObject().apply {
        put("sender", sender)
        put("token", token)
        put("maxTokenAmount", Numeric.toHexStringWithPrefix(maxTokenAmount))
    }
    // POST ${BACKEND_URL}/sign
}
```

А бэкенд ждёт (`PaymasterService.kt:42-60`):

```kotlin
@Serializable
data class SignRequest(
    val sender: String,
    val nonce: String,                              // ← обязательно
    val initCode: String = "0x",
    val callData: String,                           // ← обязательно
    val verificationGasLimit: String,               // ← обязательно
    val callGasLimit: String,                       // ← обязательно
    val preVerificationGas: String,                 // ← обязательно
    val maxPriorityFeePerGas: String,               // ← обязательно
    val maxFeePerGas: String,                       // ← обязательно
    val paymasterAndData: String = "0x",
    // ...
)
```

Мобильный клиент не отправляет `nonce`, `callData`, gas limits, gas fees — бэкенд вернёт 400 `Missing field: nonce` (Kotlinx serialization отказывает на missing required fields). Gasless path полностью нерабочий.

**Дополнительно**, `PaymasterClient.encodePaymasterAndData()` (строки 79-107) собирает `paymasterAndData` **локально на клиенте** без подписи бэкенда. Это значит, что `RecoveryUserOpBuilder.buildRecoveryWithPaymaster` (который использует `encodePaymasterAndData`) отправит UserOp с unsigned paymasterAndData — пэймастер отклонит его в `validatePaymasterUserOp` (нет валидной EIP-712 подписи).

**Почему опасно:**

1. **PRD-функция не работает.** "Gasless" — ключевое MVP-свойство (PRD §8.1: "Перевод по @username. Gasless (Paymaster, post-paid)"). Сейчас любой вызов `sendUsdt` либо упадёт (через orchestrator → 400), либо тихо отправится с native gas (через SendRepository напрямую → пользователь платит BNB).
2. **Несогласованный UX.** `SendViewModel` вызывает `sendTransactionUseCase.send()` (строка 274) — нужно проверить в `SendTransactionUseCase`, какой путь он использует. Если orchestrator, то перевод всегда падает в fallback (см. `GaslessTransactionOrchestrator:72-81`) → `sendRepository.sendUsdt()` → native gas. Получается, gasless НИКОГДА не работает, всегда BNB.
3. **Recovery flow сломан.** `RecoveryUserOpBuilder.buildRecoveryWithPaymaster` генерирует unsigned paymasterAndData → bundler отклонит. `buildRecoveryExecutionUserOp` (без paymaster) работает, но платит BNB — а PRD говорит recovery бесплатный.

**Как исправить:**

Шаг 1 — привести `PaymasterClient.getQuote` в соответствие с `SignRequest`:

```kotlin
// app/.../paymaster/PaymasterClient.kt
data class SignRequest(
    val sender: String,
    val nonce: String,                              // hex, 32 байта
    val initCode: String,
    val callData: String,
    val verificationGasLimit: String,               // hex
    val callGasLimit: String,                       // hex
    val preVerificationGas: String,                 // hex
    val maxPriorityFeePerGas: String,               // hex
    val maxFeePerGas: String,                       // hex
    val paymasterAndData: String = "0x",
    val signature: String = "0x",
    val mdaoMaxAmount: String? = null,
    val usdtMaxAmount: String? = null,
    val permitDeadline: String? = null,
    val permitV: String? = null,
    val permitR: String? = null,
    val permitS: String? = null,
)

data class SignResponse(
    val paymasterAndData: String,
    val userOpHash: String,
    val maxFee: String,
    val token: String,
)

@Singleton
class PaymasterClient @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val jsonMediaType = "application/json".toMediaType()

    /**
     * Запросить подпись пэймастера для UserOp.
     * Вызывается ПОСЛЕ построения UserOp с пустым paymasterAndData
     * и ПОСЛЕ оценки gas бандлером.
     */
    suspend fun signUserOp(
        userOp: UserOperation,
        mdaoMaxAmount: BigInteger? = null,
        usdtMaxAmount: BigInteger? = null,
    ): SignResponse = withContext(Dispatchers.IO) {
        val request = SignRequest(
            sender = userOp.sender,
            nonce = Numeric.toHexStringWithPrefix(userOp.nonce),
            initCode = Numeric.toHexString(userOp.initCode),
            callData = Numeric.toHexString(userOp.callData),
            verificationGasLimit = Numeric.toHexStringWithPrefix(userOp.verificationGasLimit),
            callGasLimit = Numeric.toHexStringWithPrefix(userOp.callGasLimit),
            preVerificationGas = Numeric.toHexStringWithPrefix(userOp.preVerificationGas),
            maxPriorityFeePerGas = Numeric.toHexStringWithPrefix(userOp.maxPriorityFeePerGas),
            maxFeePerGas = Numeric.toHexStringWithPrefix(userOp.maxFeePerGas),
            paymasterAndData = "0x",
            signature = "0x",
            mdaoMaxAmount = mdaoMaxAmount?.let { Numeric.toHexStringWithPrefix(it) },
            usdtMaxAmount = usdtMaxAmount?.let { Numeric.toHexStringWithPrefix(it) },
        )

        val httpRequest = Request.Builder()
            .url("${BuildConfig.BACKEND_URL}/v1/sign")
            .post(JSONObject.toJsonString(request).toRequestBody(jsonMediaType))
            .build()

        // ... обработка 429/400/5xx, парсинг SignResponse
    }
}
```

Шаг 2 — `GaslessTransactionOrchestrator` должен построить UserOp сначала, потом звать `/sign`:

```kotlin
suspend fun sendUsdtGasless(recipient: String, amount: BigInteger): Result<GaslessSendResult> {
    val walletData = walletManager.getWalletData() ?: return Result.Error(...)

    // 1. Построить UserOp с ПУСТЫМ paymasterAndData
    val emptyOpResult = sendRepository.buildUserOp(
        recipientAddress = recipient,
        amount = amount,
        paymasterAndData = ByteArray(0)   // ← явно пустой
    )
    val emptyOp = (emptyOpResult as? Result.Success)?.data ?: return emptyOpResult as Result.Error

    // 2. Оценить gas через bundler (уже сделано в buildUserOp, но можно повторить с pm)
    // ...

    // 3. Запросить подпись пэймастера с ПОЛНЫМ UserOp
    val signResponse = try {
        paymasterClient.signUserOp(
            userOp = emptyOp,
            mdaoMaxAmount = amount,
            usdtMaxAmount = amount,
        )
    } catch (e: PaymasterError) {
        if (fallbackToNativeGas) {
            return sendRepository.sendUsdt(recipient, amount).map {
                GaslessSendResult(it, usedFallback = true)
            }
        } else return Result.Error(AppError.Unknown(e))
    }

    // 4. Пересобрать UserOp с настоящим paymasterAndData
    val signedOp = emptyOp.copy(
        paymasterAndData = Numeric.hexStringToByteArray(signResponse.paymasterAndData)
    )

    // 5. Опционально — переоценить gas с новым paymasterAndData (увеличится verificationGasLimit)
    // 6. Отправить
    return sendRepository.executeUserOp(signedOp).map { GaslessSendResult(it) }
}
```

Шаг 3 — добавить `paymasterClient` в `SendRepository` напрямую (не только в orchestrator), чтобы `sendUsdt` тоже был gasless:

```kotlin
@Singleton
class SendRepository @Inject constructor(
    private val walletManager: WalletManager,
    private val ethereumClient: EthereumClient,
    private val bundlerClient: BundlerClient,
    private val paymasterClient: PaymasterClient   // ← добавить
) {
    suspend fun sendUsdt(recipientAddress: String, amount: BigInteger): Result<String> {
        // 1. buildUserOp с пустым paymasterAndData
        // 2. paymasterClient.signUserOp(...)
        // 3. copy userOp с signed paymasterAndData
        // 4. executeUserOp
    }
}
```

Шаг 4 — `GuardianUserOpBuilder` тоже должен использовать paymaster для veto (см. D-4 ниже).

**Как проверить:**

```bash
# 1. Юнит-тест PaymasterClient: отправка корректного SignRequest → mock-сервер возвращает SignResponse
./gradlew :app:testDebugUnitTest --tests "PaymasterClientTest.signUserOp sends full UserOp"

# 2. Интеграционный тест: реальный бэкенд + bundler testnet → UserOp с paymasterAndData принимается EntryPoint
./gradlew :app:connectedAndroidTest --tests "GaslessSendE2ETest"

# 3. Контракт-тест: в логе бэкенда виден полный SignRequest (sender, nonce, callData, gas limits)
#    и успешный ответ с paymasterAndData, содержащим magic-суффикс 22e325a297439656.
```

### 4.3 Расхождение D-4 — `GuardianUserOpBuilder` не использует paymaster (veto не бесплатный)

**Что не так** (`app/.../guardian/GuardianUserOpBuilder.kt:413-426`):

```kotlin
private fun buildUserOp(
    sender: String,
    nonce: BigInteger,
    initCode: ByteArray,
    callData: ByteArray
): UserOperation {
    return UserOperation(
        // ...
        paymasterAndData = ByteArray(0)   // ← ВСЕГДА пустой
    )
}
```

Этот `buildUserOp` используется в `sendConfirmGuardian`, `sendApproveRecovery`, `sendVetoRecovery` (строки 154, 195, 251). Все три операции отправляются без пэймастера.

PRD §7: "**Veto = бесплатный (Paymaster покрывает gas)**". Контракт `MDAOPaymaster` должен быть способен спонсировать эти UserOp'ы — но клиент их даже не пытается отправить gasless.

**Почему опасно:**

1. **PRD-требование нарушено.** Guardian, который хочет veto, должен иметь BNB на своём SmartAccount. Если у него нет BNB (а большинство users не держат BNB, потому что MDAOPay = gasless), он не сможет veto → атакующий может довести recovery до execute, даже если guardian против.
2. **Экономика безопасности ломается.** Veto — единственный механизм защиты от ложного recovery. Если он недоступен для среднего guardian'а, social recovery теряет смысл.
3. **`GuardianUserOpBuilder` уже инжектит `PaymasterClient`** (строка 47), но не использует его. Это либо забытый код, либо недореализация.

**Как исправить:**

```kotlin
// app/.../guardian/GuardianUserOpBuilder.kt

private suspend fun buildUserOp(
    sender: String,
    nonce: BigInteger,
    initCode: ByteArray,
    callData: ByteArray,
    usePaymaster: Boolean = true   // ← новый параметр
): UserOperation {
    val baseOp = UserOperation(
        sender = sender,
        nonce = nonce,
        initCode = initCode,
        callData = callData,
        callGasLimit = BigInteger.valueOf(200_000),
        verificationGasLimit = BigInteger.valueOf(150_000),
        preVerificationGas = BigInteger.valueOf(50_000),
        maxFeePerGas = BigInteger.valueOf(1_500_000_000),
        maxPriorityFeePerGas = BigInteger.valueOf(1_000_000_000),
        paymasterAndData = ByteArray(0)
    )

    if (!usePaymaster) return baseOp

    // Запросить подпись пэймастера (по аналогии с GaslessTransactionOrchestrator)
    return try {
        val signResponse = paymasterClient.signUserOp(baseOp)
        baseOp.copy(
            paymasterAndData = Numeric.hexStringToByteArray(signResponse.paymasterAndData)
        )
    } catch (e: PaymasterError) {
        // Fallback на native gas — guardian с BNB сможет всё равно veto
        log.warn("Paymaster unavailable for guardian op, falling back to native gas", e)
        baseOp
    }
}

private suspend fun sendVetoRecovery(...): String? {
    // ...
    val userOp = buildUserOp(
        sender = smartAccountAddress,
        nonce = nonce,
        initCode = initCode,
        callData = Numeric.hexStringToByteArray(executeCalldata),
        usePaymaster = true   // ← veto всегда пытается через paymaster
    )
    // ...
}
```

Важно: `MDAOPaymaster.sol` должен в своём whitelist-механизме разрешать UserOp с `callData`, нацеленной на `SocialRecoveryModule.approveRecovery` / `vetoRecovery` / `confirmGuardian`. Нужно проверить `MDAOPaymaster.sol` (не входил в этот аудит, но это критично — иначе пэймастер отклонит).

### 4.4 Расхождение D-5 — Recovery использует BIOMETRIC_WEAK, не STRONG

**Что не так** (`app/.../recovery/presentation/RecoveryScreen.kt`):

```kotlin
// строка 170:
biometricManager.authenticate(
    activity = activity,
    title = "Доступ к seed-фразе",
    subtitle = "Подтвердите личность",
    // ...
)

// строка 973:
biometricManager.authenticate(
    activity = activity,
    title = "Импорт кошелька",
    subtitle = "Подтвердите личность для импорта seed-фразы",
    // ...
)
```

`BiometricAuthManager` (`app/.../core/security/BiometricManager.kt:14-68`) предоставляет два метода:

| Метод | Authenticators | Назначение |
|-------|----------------|------------|
| `authenticate()` | `BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL` | general use, "allows BIOMETRIC_WEAK for UX" |
| `authenticateHighRisk()` | `BIOMETRIC_STRONG` только | HIGH risk ops, per F-062 |

Recovery-флоу (раскрытие seed, импорт кошелька, trigger recovery) — это **high-risk операции** по любой классификации: полный доступ к кошельку. Но `RecoveryScreen` вызывает слабый `authenticate()`, принимающий 2D face unlock (BIOMETRIC_WEAK), который обходится фотографией пользователя.

Тест `BiometricManagerTest.kt` (строки 14-43) явно фиксирует контракт: high-risk → STRONG only. Контракт реализован, но не используется.

**Почему опасно:**

1. **2D face unlock bypass.** На устройствах с BIOMETRIC_WEAK (2D camera face unlock, класс Android < 4.x face unlock) злоумышленник с фотографией владельца разблокирует recovery → раскрывает seed → уводит кошелёк.
2. **DEVICE_CREDENTIAL fallback.** `authenticate()` принимает также `DEVICE_CREDENTIAL` (PIN/паттерн). Если у пользователя включён слабый PIN (4 цифры), recovery защищён только этим PIN — ну абсолютно не уровень high-risk.
3. **F-062 (security finding) не закрыт.** TDD говорит `F-062 — fixed by BiometricAuthManager.authenticateHighRisk()`. По факту — метод есть, но не вызывается.

**Дополнительно** — `SendScreen.kt:241` тоже использует `authenticate()` для подтверления перевода (любой суммы, даже крупной). Это та же проблема, но хотя бы ограничено `sendAmount` (хотя в коде нет门槛 — даже 10000 USDT пройдёт через WEAK).

**Как исправить:**

```kotlin
// RecoveryScreen.kt, строка 170 — заменить:
biometricManager.authenticate(
    activity = activity,
    title = "Доступ к seed-фразе",
    subtitle = "Подтвердите личность",
    onResult = { /* ... */ }
)
// на:
biometricManager.authenticateHighRisk(
    activity = activity,
    title = "Доступ к seed-фразе",
    subtitle = "Требуется сильная биометрия (Face ID / отпечаток)",
    onResult = { /* ... */ }
)

// То же для строки 973 (импорт кошелька) и для всех вызовов в recovery-флоу.
```

Дополнительно — добавить fallback UX, если BIOMETRIC_STRONG не доступен:

```kotlin
// в BiometricAuthManager
fun isStrongBiometricAvailable(): Boolean {
    val manager = BiometricManager.from(context)
    return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
           BiometricManager.BIOMETRIC_SUCCESS
}

// в RecoveryScreen:
if (!biometricManager.isStrongBiometricAvailable()) {
    // Показать диалог: "Для восстановления нужен отпечаток или 3D-лицо.
    // На этом устройстве сильная биометрия не настроена.
    // Перейдите в Настройки → Безопасность → Отпечаток пальца."
    showStrongBiometricRequiredDialog()
    return
}
biometricManager.authenticateHighRisk(...)
```

Для `SendScreen` — аналогично, но с порогом по сумме (мелкие переводы могут позволить WEAK):

```kotlin
// SendScreen.kt, строка 241:
val authFun = if (sendAmount >= BigDecimal("1000")) {
    biometricManager::authenticateHighRisk   // крупные → STRONG
} else {
    biometricManager::authenticate           // мелкие → WEAK допустим
}
authFun(activity, title, subtitle, onResult)
```

**Как проверить:**

```bash
# 1. Юнит-тест: RecoveryScreen с BIOMETRIC_WEAK-only устройством → отказ
./gradlew :app:testDebugUnitTest --tests "RecoveryScreenTest.highRiskBiometricRequired"

# 2. Instrumented test на эмуляторе с 2D face unlock:
adb shell settings put secure biometric_weak_enabled 1
./gradlew connectedAndroidTest --tests "RecoveryFlowInstrumentedTest.weakBiometricBlocksRecovery"

# 3. Code review check:
rg "biometricManager\.authenticate\(" app/src/main/   # должен возвращать 0 совпадений в recovery/
```

### 4.5 Чек-лист пользователя — "2 шага (invite + confirm)" — частично корректен

`acceptInviteAndRegister` (`GuardianUserOpBuilder.kt:59-86`) делает только один on-chain шаг:

```kotlin
suspend fun acceptInviteAndRegister(invite, userId): Result<GuardianKeyData> {
    // 1. createRecoveryPasskey → passkeyData
    // 2. extractP256PublicKey → keyData (x, y)
    // 3. sendConfirmGuardian(invite.walletAddress)   ← единственный on-chain вызов
    return Result.success(keyData)
}
```

Это **правильно** по дизайну контракта `SocialRecoveryModule`:

| Шаг | Кто вызывает | Что вызывает | Где |
|-----|--------------|--------------|-----|
| 1. Invite | Владелец кошелька | `addGuardian(wallet, identityHash, pubKeyX, pubKeyY)` | `GuardianManager` / отдельный flow владельца |
| 2. Confirm | Guardian | `confirmGuardian(wallet)` | `GuardianUserOpBuilder.acceptInviteAndRegister` |

Контракт (`SocialRecoveryModule.sol:120-151`):

```solidity
function addGuardian(address wallet, bytes32 identityHash, bytes32 pubKeyX, bytes32 pubKeyY)
    external onlyWalletOwner(wallet)   // ← только владелец
// ...
function confirmGuardian(address wallet) external {
    bytes32 identityHash = keccak256(abi.encodePacked(msg.sender));   // ← guardian = msg.sender
    // ...
}
```

Guardian не может `addGuardian` сам себя — только владелец. Guardian может только `confirmGuardian`. Это правильное разделение.

**НО** — есть тонкость. `identityHash` в `addGuardian` — это `keccak256(abi.encodePacked(msg.sender))` от будущего guardian'а, то есть от адреса guardian'а SmartAccount. Владелец должен знать этот адрес заранее. Это работает, потому что invite-флоу через relay передаёт `walletAddress` (guardian's SmartAccount) владельцу, и тот вызывает `addGuardian` с правильным `identityHash`. **Но** в коде `GuardianManager.inviteGuardian` (нужно проверить в `GuardianManager.kt`) — если invite создаётся до того, как guardian знает свой SmartAccount-адрес, будет несоответствие.

**Что нужно проверить (не критично, но важно):**

В файле `app/.../core/guardian/GuardianManager.kt` — как `inviteGuardian` вычисляет `identityHash` для записи в контракт? Если он использует какой-то другой идентификатор (не `keccak256(guardianSmartAccountAddress)`), то `confirmGuardian` упадёт с `ErrNotGuardian`. Это нужно проверить в `GuardianManager.kt` — не входил в исходный чек-лист, но это критическая точка.

---

## Сводка расхождений и приоритетов

| # | Расхождение | Severity | Файл | Строка |
|---|-------------|----------|------|--------|
| D-1 | Env var вместо KMS для paymaster key | **CRITICAL** | `AppConfig.kt`, `Application.kt` | 56, 162 |
| D-6 | `SendRepository.sendUsdt` без paymaster | **CRITICAL** | `SendRepository.kt` | 32-40 |
| D-7 | `PaymasterClient.getQuote` API ≠ backend SignRequest | **CRITICAL** | `PaymasterClient.kt` | 35-48 |
| D-2 | `vetoRecovery` — transfer вместо burn | **HIGH** | `SocialRecoveryModule.sol` | 272 |
| D-3 | `cleanupExpiredRecovery` возвращает депозит | **HIGH** | `SocialRecoveryModule.sol` | 314-317 |
| D-4 | Guardian ops без paymaster (veto не бесплатный) | **HIGH** | `GuardianUserOpBuilder.kt` | 424 |
| D-5 | Recovery использует BIOMETRIC_WEAK | **HIGH** | `RecoveryScreen.kt` | 170, 973 |
| D-8 | `0.01 ether` литерал для MDAO | LOW | `SocialRecoveryModule.sol` | 40 |

### Что сделано правильно (не требует изменений)

- ✅ `GUARDIAN_THRESHOLD = 2`, `MAX_GUARDIANS = 3` (соответствует PRD)
- ✅ WebAuthn без EIP-191 prefix, RIP-7212 precompile
- ✅ EIP-712 для quote signing (правильная реализация в `PaymasterService`)
- ✅ `extractClientIp` с trusted proxy check
- ✅ Rate limiting (sender + IP), idempotency, replay cache
- ✅ `BiometricAuthManager.authenticateHighRisk()` существует (просто не используется)
- ✅ SSS 2-of-3, PBKDF2 600k, passkey PRF
- ✅ `SafeERC20` уже есть в зависимостях (но не используется — см. рекомендации)
- ✅ `MDAOToken` — `ERC20Burnable`, `ERC20Permit`, `ERC20Pausable` (всё нужное есть)

### Рекомендации по порядку фиксов

1. **D-1 (KMS)** — приоритет 0. Без этого любой следующий деплой в production = компрометация ключа. Внедрить `PaymasterSigner` интерфейс и `KmsPaymasterSigner` реализацию.
2. **D-6 + D-7 (gasless send)** — приоритет 1. Сейчас gasless просто не работает. Привести `PaymasterClient` в соответствие с `SignRequest`, исправить `GaslessTransactionOrchestrator`.
3. **D-2 + D-3 (burn + cleanup)** — приоритет 1. Контрактные фиксы, требуют redeploy. Сначала devnet, потом audit, потом mainnet.
4. **D-4 (guardian paymaster)** — приоритет 2. После D-6/D-7 становится тривиальным (переиспользовать `PaymasterClient.signUserOp`). Проверить, что `MDAOPaymaster` whitelist'ит `SocialRecoveryModule` callData.
5. **D-5 (BIOMETRIC_STRONG)** — приоритет 2. Простой фикс (заменить вызов), но требует UX-сопровождения (диалог "устройство не поддерживается").
6. **D-8 (ether literal)** — приоритет 3. Косметика.

---

## Что НЕ проверено (требует отдельного аудита)

- `MDAOPaymaster.sol` (684 строки) — какой whitelist callData он пропускает? Спонсирует ли recovery? Это критично для D-4.
- `SessionKeyModule.sol` — реализация Capability Mapping, лимиты, expiry.
- `GuardianManager.kt` — как вычисляется `identityHash` при invite? Соответствует ли `keccak256(guardianAddress)` из контракта?
- `SimpleAccount.sol` — где он? Не нашёл в `contracts/src/`. Используется eth-infinitism Account-v0.6?
- `BundlerClient.kt` — поддерживает ли `estimateUserOperationGas` с пустым paymasterAndData? (нужно для D-6/D-7 фикса — bundler оценит gas без пэймастера, потом переоценить с подписанным paymasterAndData).
- `SwapService.kt` — отдельный `swapPrivateKey`, но тоже env var. Та же проблема, что D-1.
- `AuthService.kt` — low-s проверка (EIP-2) для nickname registration signatures.
- Тест `SocialRecoveryModule.t.sol:179` комментирует "Actual deposit accounts for MDAOToken burn fee (50 bps)" — это значит, что тестЫ УЖЕ знают про fee-on-transfer. Но `assertEq(recovery.recoveryDeposit(alice), EXPECTED_DEPOSIT)` — какая `EXPECTED_DEPOSIT`? Если `0.01 ether`, то тест не ловит потерю 0.5% на fee. Нужно проверить.
