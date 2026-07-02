Разберу отчёт методично — что в нём корректно, что завышено/занижено, что пропущено, и где технические решения содержат ошибки.

---

## Общая оценка отчёта

**Тезис отчёта: "высокая степень готовности, GO to testnet"** — **частично согласен, с существенными оговорками.**

Главная методологическая проблема: из 106 findings — **90 CLAIMED_FIXED, только 9 VERIFIED**. Это означает, что аудитор верифицировал менее 10% заявленных исправлений. "GO" с таким соотношением — это не вердикт аудита, а вердикт самооценки команды. Настоящий вердикт звучал бы: "GO после верификации CRITICAL и HIGH findings".

---

## 🔴 Ошибки в самом отчёте

### 1. GCP KMS не поддерживает secp256k1 — предложенное решение KMS неработоспособно

Раздел 4.1 предлагает:
```kotlin
val response = kmsClient.asymmetricSign(keyName,
    Digest.newBuilder().setSha256(...).build()
)
```
с использованием `com.google.cloud:google-cloud-kms`. **GCP Cloud KMS не поддерживает secp256k1** — только `EC_SIGN_P256_SHA256` (NIST P-256) и `EC_SIGN_P384_SHA384`. Ethereum использует secp256k1. Это означает, что предложенный GCP-код подписывает на P-256, а `ecrecover` в Solidity ожидает secp256k1 — подпись никогда не верифицируется.

**Правильные варианты:**

| Провайдер | Поддержка secp256k1 | Метод |
|-----------|---------------------|-------|
| AWS KMS | ✅ `ECC_SECG_P256K1` | `kmsClient.sign()` |
| Azure Key Vault | ✅ `EC-K256` | `cryptographyClient.sign()` |
| HashiCorp Vault | ✅ через transit secrets engine | `vault.logical().write()` |
| GCP Cloud KMS | ❌ нет secp256k1 | — |

Если инфра на GCP — нужен либо HashiCorp Vault рядом, либо переход на AWS KMS для подписи (можно держать остальное в GCP). Предложенный код нужно переписать под AWS SDK:

```kotlin
// Правильный KMS для Ethereum — AWS, не GCP
class KmsPaymasterSigner(
    private val kmsClient: software.amazon.awssdk.services.kms.KmsClient,
    private val keyId: String  // ECC_SECG_P256K1 key
) : PaymasterSigner {
    override suspend fun signHash(hash: ByteArray): ByteArray {
        val response = kmsClient.sign {
            this.keyId = this@KmsPaymasterSigner.keyId
            message = SdkBytes.fromByteArray(hash)
            messageType = MessageType.DIGEST
            signingAlgorithm = SigningAlgorithmSpec.ECDSA_SHA_256
        }
        return derToEthereumSignature(response.signature().asByteArray(), hash)
    }
}
```

---

### 2. RIP-7212 на BSC — неверифицированное утверждение с критическим риском

Отчёт утверждает: `✅ RIP-7212 P-256 на BSC — F-108 VERIFIED — precompile 0x100 работает`.

**Это требует тщательной проверки.** RIP-7212 (EIP-7212, P-256 precompile по адресу `0x100`) реализован в: zkSync Era, Polygon zkEVM, Base, Optimism, Arbitrum — но **BSC (BEP-171/Parlia consensus) не включил его в стандартный набор precompile**. BSC использует свой набор precompile (0x01–0x09, + BNB-специфичные).

Если precompile 0x100 не существует на BSC, то `SocialRecoveryModule._verifyWebAuthn` с `P256_VERIFIER = address(0x100)` будет вызывать несуществующий адрес, staticcall вернёт `(false, "")`, и все WebAuthn-верификации провалятся — тихо, без revert в самой функции.

**Как проверить прямо сейчас:**
```bash
# Проверить наличие precompile на BSC mainnet
cast call 0x0000000000000000000000000000000000000100 \
  "0x" \
  --rpc-url https://bsc-dataseed.binance.org

# Если returns empty bytes → precompile нет → вся WebAuthn-верификация сломана
```

```solidity
// Корректный fallback в контракте:
constructor(address _p256Verifier) {
    // На BSC без RIP-7212 — использовать библиотечную реализацию
    P256_VERIFIER = _p256Verifier != address(0) 
        ? _p256Verifier 
        : address(new FallbackP256Verifier()); // pure Solidity верификатор
}
```

Это потенциально самый критичный непроверенный риск всего проекта.

---

### 3. L-5 неверно классифицирован как LOW — это потенциальный compile error

```
L-5 | BinancePriceSource — нет в codebase, но упоминается в Application.kt:185
```

Если класс `BinancePriceSource` упоминается в `Application.kt:185` как зависимость DI — это **не LOW, а COMPILE ERROR**. Kotlin/JVM не скомпилирует код со ссылкой на несуществующий класс. Если backend "собирается" (чеклист §1), то либо: а) класс существует но пустой/стаб; б) он закомментирован вместе с Binance oracle. Нужно явно проверить статус третьего price source — если он отсутствует, медиана из 2 источников не защищает от single point of failure.

---

### 4. L-6 неверно классифицирован как LOW — это функциональный баг recovery

```
L-6 | WatchtowerService.kt:199 | if (approvals >= BigInteger.valueOf(3)) — hardcoded 3, но GUARDIAN_THRESHOLD=2
```

Это не "косметика" — **watchtower никогда не отправит push "threshold reached"**, потому что требует 3 аппрувала, а контракт срабатывает на 2. Пользователи не получат уведомление о том, что recovery выполнимо. Сценарий: 2 guardian'а одобрили → контракт готов к execute → пользователь не знает → 48h window истекает. Severity: **HIGH**.

```kotlin
// WatchtowerService.kt:199 — исправление
val threshold = config.guardianThreshold  // читать из конфига, не хардкодить
if (approvals >= BigInteger.valueOf(threshold.toLong())) {
    notifyThresholdReached(walletAddress)
}
```

---

## 🟠 Существенные пропуски отчёта

### 5. SIWE auth полностью отсутствует — не упомянуто вообще

Из нашего предыдущего аудита: вся auth-система реализована через email/password. `GET /v1/sign` требует JWT. Получить JWT без email — невозможно. Это означает, что gasless flow **физически заблокирован для пользователей без email** (то есть для основной аудитории Web3-кошелька). Wave 15 отчёт об этом молчит — либо SIWE был реализован и не упомянут, либо это gap в аудите.

### 6. eth_sendTransaction в WebView возвращает фейковый хэш — не упомянуто

`EthereumProviderInjector.handleSendTransaction` всегда возвращает `0x000...000` без отправки UserOp. Все dApp-интеграции (Arena, DEX, Flopi из PRD §17) технически нефункциональны. Это HIGH finding, отсутствующее в отчёте.

### 7. DeviceIntegrityManager блокирует эмулятор — не упомянуто

Из первого аудита: проверка `isRooted()/isEmulator()` выполняется **до** ветвления по riskLevel. Это блокирует QA на эмуляторах, которые нужны для автоматизированных e2e тестов из чеклиста (§1, строка "E2E test plan"). Maestro/CI тесты на эмуляторе гарантированно падают.

### 8. Certificate pinning — placeholder SHA-256 не упомянут

`sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=` в build.gradle.kts — всё ещё там? Если да, все HTTPS-запросы с реальным cert PIN failят. Отчёт обходит это молчанием.

---

## 🟡 Оценки, с которыми я не согласен

### 9. SwapService MEV risk — это MEDIUM/HIGH, не LOW

```kotlin
// 4.7: "30 минут работы" — это верно про fix, но severity неверный
val function = Function("swapExactTokensForETH",
    listOf(
        // amountOutMin ОТСУТСТВУЕТ
        Uint256(amountIn), ...
    ), emptyList()
)
```

Если swap-функционал используется реальными пользователями на mainnet — 100% MEV exposure на каждый своп. Для testnet это не критично, но severity надо поднять до MEDIUM и включить в Phase 0, не Phase 1.

### 10. cleanupExpiredRecovery "soft-burn" — downplayed

Отчёт называет `transfer(0xdEaD, deposit)` "осознанным решением" и ссылается на тест. Но упускает важный нюанс: `MDAOToken._update` берёт 0.5% fee с любого transfer (если адрес не в `isExempt`). Значит:
- Реально к `0xdEaD` попадает `deposit * 0.9950`
- 0.5% идёт как fee (куда именно — в контракт токена? в burn? нужно проверить `_update`)
- `totalSupply` не уменьшается

Если fee идёт не в burn — появляются "свободные" MDAO нигде, что ломает токеномику. **Замена на `burn()` — правильное решение, не "спорное"**.

### 11. "90 CLAIMED_FIXED, 9 VERIFIED" — вывод GO неоправдан

Это методологическая проблема всего отчёта. Стандарт аудита:
- OPEN → фиксируется аудитором
- CLAIMED_FIXED → команда говорит "исправили"
- VERIFIED → аудитор проверил исправление

С 9 VERIFIED из 90 CLAIMED_FIXED — верифицировано **10%**. Среди непроверенных могут быть любые из 5 CRITICAL и 15 HIGH findings. "GO to testnet" при таком соотношении — это доверие команде, а не результат аудита.

---

## ✅ Что в отчёте действительно сильно

**Раздел 3 (strong points) — корректный и детальный:**
- Anti-griefing с failure type differentiation — нетривиальное решение
- CEI pattern в `postOp` + low-level call для refund — правильно
- EIP-712 с domain separator — корректно (из нашего собственного аудита подтверждено)
- PBKDF2 600k iterations — соответствует OWASP 2024
- Relay fail-closed при отсутствии RELAY_SECRET — правильный подход
- BiometricStrong/Weak разделение по risk tier — зрелое решение

**Раздел 4.3 (v0.7 не блокер) — правильная приоритизация.** v0.6 работает в production, migration за 2 недели — адекватная оценка трудозатрат.

**Раздел 4.4 (TOCTOU relay) — правильный анализ.** Durable Objects — верное решение, on-chain — source of truth, relay — coordination layer. Риск правильно квалифицирован как не блокер.

**Чеклист деплоя (Appendix B)** — один из лучших элементов отчёта, конкретный и полный.

---

## Итоговая таблица расхождений

| Пункт | В отчёте | Реальная оценка |
|-------|----------|-----------------|
| Вердикт GO | При 90 CLAIMED_FIXED | Условный GO: нужна верификация CRITICAL+HIGH |
| RIP-7212 на BSC | ✅ VERIFIED | ⚠️ Требует on-chain проверки прямо сейчас |
| GCP KMS решение | Корректное | ❌ GCP не поддерживает secp256k1 → нерабочее |
| L-5 BinancePriceSource | LOW | CRITICAL если compile error |
| L-6 threshold=3 vs 2 | LOW | HIGH — watchtower push сломан |
| SwapService MEV | LOW | MEDIUM — 100% slippage на production |
| SIWE auth | Не упомянуто | CRITICAL gap — весь gasless flow заблокирован |
| eth_sendTransaction WebView | Не упомянуто | HIGH — dApp интеграции фиктивны |
| Emulator blocking | Не упомянуто | MEDIUM — блокирует e2e CI тесты |
| Cert pinning | Не упомянуто | HIGH — нужна проверка |
| soft-burn cleanup | "Осознанное решение" | Должен быть `burn()`, fee-on-transfer делает его некорректным |

---

## Что сделать прямо сейчас (до любого деплоя)

```bash
# 1. Проверить RIP-7212 на BSC testnet (5 минут)
cast call 0x0000000000000000000000000000000000000100 "0x" \
  --rpc-url https://data-seed-prebsc-1-s1.binance.org:8545

# 2. Проверить что BinancePriceSource существует
grep -r "BinancePriceSource" backend/src/ --include="*.kt" | grep "class BinancePriceSource"

# 3. Проверить threshold в WatchtowerService
grep -n "BigInteger.valueOf(3)" backend/src/ -r

# 4. Проверить SIWE endpoints
grep -r "siwe\|SIWE\|signIn" backend/src/ --include="*.kt"
```

Ответы на эти 4 вопроса изменят оценку готовности больше, чем весь остальной отчёт.
