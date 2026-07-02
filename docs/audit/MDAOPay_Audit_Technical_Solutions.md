# MDAOPay — Технический аудит: взвешенные решения по 27 findings

> Principal Engineer review · ERC-4337 / BSC / WebAuthn / Kotlin+Ktor / Cloudflare Workers  
> Дата: июль 2026 · Версия: 2.0 (полный повторный аудит)

---

## Методология оценки

Каждый finding оценивается по трём осям:

| Ось | Шкала |
|-----|-------|
| **Риск** | CRITICAL → HIGH → MEDIUM → LOW |
| **Усилие** | XS (< 2ч) · S (< 1д) · M (1–3д) · L (1–2н) · XL (> 2н) |
| **Блокирует** | mainnet · testnet · QA · — |

---

## 🔴 CRITICAL — блокируют mainnet

---

### #1 · KmsPaymasterSigner — заглушка в production

**Риск:** CRITICAL · **Усилие:** L (3–5д) · **Блокирует:** mainnet

#### Что происходит

```kotlin
// Application.kt:168 — фактический код
val signer: PaymasterSigner = if (config.allowLocalSigning) {
    LocalPaymasterSigner(privateKey)
} else {
    // KmsPaymasterSigner() // закомментирован
    throw UnsupportedOperationException("KMS not implemented")
}
```

Backend **физически не может стартовать** с `ALLOW_LOCAL_SIGNING=false` — бросает исключение при инициализации DI-контейнера. Любой production deploy без `ALLOW_LOCAL_SIGNING=true` упадёт до обработки первого запроса.

#### Весовой анализ

Это не просто "техдолг" — это **неработающий security-критический путь**. `LocalPaymasterSigner` хранит приватный ключ в env var: если secret утечёт (CI logs, env dump, K8s secret misconfiguration), атакующий получает полный контроль над paymaster и может одобрять произвольные UserOp без оплаты. KMS решает это изоляцией ключа в HSM — private key физически не покидает KMS.

#### Решение

**Шаг 1: Интерфейс уже есть — реализовать AWS KMS адаптер**

```kotlin
// PaymasterSigner.kt — интерфейс (уже существует)
interface PaymasterSigner {
    suspend fun signHash(hash: ByteArray): ByteArray
    fun getAddress(): String
}

// KmsPaymasterSigner.kt — реализация
class KmsPaymasterSigner(
    private val keyId: String,           // "arn:aws:kms:ap-southeast-1:123:key/..."
    private val kmsClient: KmsClient,    // AWS SDK v2
    private val publicKeyCache: String   // вычислить один раз при init
) : PaymasterSigner {

    companion object {
        suspend fun create(keyId: String): KmsPaymasterSigner {
            val client = KmsClient { region = "ap-southeast-1" }
            // Получаем публичный ключ для деривации адреса
            val pubKeyResponse = client.getPublicKey {
                this.keyId = keyId
            }
            val pubKeyBytes = pubKeyResponse.publicKey!!.toByteArray()
            // DER → raw 64-byte uncompressed pubkey (skip first 23 bytes DER header)
            val rawPub = pubKeyBytes.drop(23).toByteArray()
            val address = Keys.toChecksumAddress(
                Keys.getAddress(Sign.publicKeyFromPrivate(
                    BigInteger(1, rawPub)
                ))
            )
            return KmsPaymasterSigner(keyId, client, address)
        }
    }

    override fun getAddress(): String = publicKeyCache

    override suspend fun signHash(hash: ByteArray): ByteArray {
        // KMS подписывает ECDSA SHA-256
        val response = kmsClient.sign {
            this.keyId = this@KmsPaymasterSigner.keyId
            message = SdkBytes.fromByteArray(hash)
            messageType = MessageType.Digest
            signingAlgorithm = SigningAlgorithmSpec.EcdsaSha256
        }
        val derSig = response.signature!!.toByteArray()
        // DER → R+S → Ethereum v,r,s (65 bytes)
        return derToEthereumSig(derSig, hash)
    }

    private fun derToEthereumSig(der: ByteArray, hash: ByteArray): ByteArray {
        // Разбор DER SEQUENCE { INTEGER r, INTEGER s }
        var offset = 2 // skip 0x30, length
        val rLen = der[offset + 1].toInt() and 0xFF
        val r = der.slice(offset + 2 until offset + 2 + rLen).toByteArray().takeLast(32).toByteArray()
        offset += 2 + rLen
        val sLen = der[offset + 1].toInt() and 0xFF
        val s = der.slice(offset + 2 until offset + 2 + sLen).toByteArray().takeLast(32).toByteArray()

        // Нормализуем S (low-S для Ethereum)
        val sBig = BigInteger(1, s)
        val halfOrder = Sign.CURVE.n.shiftRight(1)
        val sNorm = if (sBig > halfOrder) Sign.CURVE.n.subtract(sBig) else sBig
        val sBytes = Numeric.toBytesPadded(sNorm, 32)

        // Определяем v (27 или 28) перебором
        val rPad = Numeric.toBytesPadded(BigInteger(1, r), 32)
        for (v in 27..28) {
            val sig = Sign.SignatureData(
                (v).toByte(),
                rPad,
                sBytes
            )
            val recovered = Sign.recoverFromSignature(
                (v - 27),
                ECDSASignature(BigInteger(1, rPad), BigInteger(1, sBytes)),
                hash
            ) ?: continue
            if (Keys.getAddress(recovered).lowercase() == getAddress().lowercase()) {
                return byteArrayOf(v.toByte()) + rPad + sBytes
            }
        }
        error("KMS signature recovery failed — key mismatch?")
    }
}
```

**Шаг 2: Wiring в Application.kt**

```kotlin
val signer: PaymasterSigner = when {
    config.allowLocalSigning && config.isTestnet -> {
        log.warn("⚠️  LOCAL SIGNING активен — только для testnet!")
        LocalPaymasterSigner(config.paymasterPrivateKey)
    }
    config.allowLocalSigning && !config.isTestnet -> {
        error("ALLOW_LOCAL_SIGNING=true запрещён в production (chainId=${config.chainId})")
    }
    else -> {
        val keyId = System.getenv("KMS_KEY_ID")
            ?: error("KMS_KEY_ID обязателен когда ALLOW_LOCAL_SIGNING=false")
        runBlocking { KmsPaymasterSigner.create(keyId) }
    }
}
```

**Шаг 3: Terraform KMS ресурс (минимальный)**

```hcl
resource "aws_kms_key" "paymaster" {
  description              = "MDAOPay Paymaster signing key"
  key_usage                = "SIGN_VERIFY"
  customer_master_key_spec = "ECC_SECG_P256K1"  # secp256k1 = Ethereum-совместимо

  policy = jsonencode({
    Statement = [{
      Effect    = "Allow"
      Principal = { AWS = aws_iam_role.paymaster_backend.arn }
      Action    = ["kms:Sign", "kms:GetPublicKey"]
      Resource  = "*"
    }]
  })
}
```

#### Как проверить

```bash
# Интеграционный тест:
ALLOW_LOCAL_SIGNING=false KMS_KEY_ID=arn:aws:kms:... ./gradlew test --tests KmsSignerIntegrationTest
# Убедиться что подпись верифицируется ecrecover на Solidity стороне
```

---

### #2 · SIWE (Sign-In With Ethereum) — wallet auth отсутствует

**Риск:** CRITICAL · **Усилие:** M (2–3д) · **Блокирует:** mainnet, testnet

#### Что происходит

TDD §2.1 описывает flow: `wallet → nickname → JWT(sub=walletAddress)`. Реализован только `POST /auth/login` с email/password. Эндпоинт `/sign` требует JWT — получить его без email невозможно. Весь gasless flow заблокирован для пользователей без email.

#### Решение

```kotlin
// SiweAuthRoute.kt
data class SiweNonceResponse(val nonce: String, val expiresAt: Long)
data class SiweVerifyRequest(
    val message: String,   // EIP-4361 formatted message
    val signature: String  // 65-byte hex sig
)

fun Route.siweAuthRoutes(authService: SiweAuthService, jwtService: JwtService) {

    // Step 1: получить nonce (anti-replay)
    get("/auth/siwe/nonce") {
        val nonce = authService.generateNonce()
        call.respond(SiweNonceResponse(nonce, System.currentTimeMillis() + 300_000))
    }

    // Step 2: верифицировать подпись и выдать JWT
    post("/auth/siwe/verify") {
        val req = call.receive<SiweVerifyRequest>()

        val walletAddress = authService.verifyAndExtract(req.message, req.signature)
            ?: return@post call.respond(HttpStatusCode.Unauthorized, "Invalid signature")

        // Создаём/находим пользователя
        val user = userRepository.findOrCreateByWallet(walletAddress)

        val token = jwtService.generateToken(
            sub = walletAddress,
            wallet = walletAddress,
            nickname = user.nickname
        )
        call.respond(mapOf("token" to token, "nickname" to user.nickname))
    }
}

// SiweAuthService.kt
class SiweAuthService(private val nonceCache: Cache<String, Boolean>) {

    fun generateNonce(): String {
        val nonce = SecureRandom().let { rng ->
            ByteArray(16).also(rng::nextBytes).toHex()
        }
        nonceCache.put(nonce, true) // TTL 5 мин через Caffeine
        return nonce
    }

    fun verifyAndExtract(message: String, signature: String): String? {
        // Парсим EIP-4361 сообщение
        val parsed = parseSiweMessage(message) ?: return null

        // Проверяем nonce (one-time use)
        if (nonceCache.getIfPresent(parsed.nonce) == null) return null
        nonceCache.invalidate(parsed.nonce) // consume

        // Проверяем expiry
        if (parsed.expirationTime?.isBefore(Instant.now()) == true) return null

        // Восстанавливаем адрес из подписи
        val msgHash = Sign.getEthereumMessageHash(message.toByteArray())
        val sigBytes = Numeric.hexStringToByteArray(signature)

        val recovered = Sign.signedMessageHashToKey(msgHash, Sign.SignatureData(
            sigBytes[64], sigBytes.slice(0..31).toByteArray(), sigBytes.slice(32..63).toByteArray()
        ))
        val recoveredAddress = Keys.toChecksumAddress(Keys.getAddress(recovered))

        return if (recoveredAddress.equals(parsed.address, ignoreCase = true)) recoveredAddress else null
    }
}
```

**Мобильная сторона (Android)**

```kotlin
// SiweRepository.kt
class SiweRepository(private val apiClient: ApiClient, private val walletManager: WalletManager) {

    suspend fun authenticate(): String {
        // 1. Получить nonce
        val nonceResp = apiClient.getSiweNonce()

        // 2. Сформировать EIP-4361 сообщение
        val message = buildSiweMessage(
            domain = "mdaopay.com",
            address = walletManager.getAddress(),
            statement = "Sign in to MDAOPay",
            uri = "https://mdaopay.com",
            chainId = NetworkConfig.CHAIN_ID,
            nonce = nonceResp.nonce
        )

        // 3. Подписать ключом из Keystore (не биометрия — это low-risk)
        val signature = walletManager.signMessage(message)

        // 4. Верифицировать и получить JWT
        return apiClient.verifySiwe(message, signature).token
    }
}
```

#### Как проверить

```kotlin
@Test
fun `siwe verify returns JWT with wallet address as sub`() {
    val (privKey, address) = generateTestKeypair()
    val nonce = siweService.generateNonce()
    val message = buildSiweMessage(address = address, nonce = nonce, ...)
    val sig = Sign.signMessage(message.toByteArray(), ECKeyPair.fromPrivate(privKey))
    
    val result = siweService.verifyAndExtract(message, sigToHex(sig))
    assertEquals(address.lowercase(), result?.lowercase())
}
```

---

### #3 · InsuranceFund deploy с `auditor = address(0)`

**Риск:** CRITICAL · **Усилие:** XS (1–2ч) · **Блокирует:** mainnet

#### Что происходит

```solidity
// DeployInsuranceFund.s.sol:12 — фактический код
new InsuranceFund(
    address(mdaoToken),
    address(0)  // ← auditor! Это баг
);
```

Если конструктор не ревертит на `address(0)` (что вероятно, т.к. это не стандартная проверка) — `submitClaim()` будет требовать подпись от `address(0)`, что невозможно. Застраховать средства нельзя никогда.

#### Решение

**Контракт (добавить guard):**

```solidity
// InsuranceFund.sol — конструктор
constructor(address _mdaoToken, address _auditor) {
    if (_mdaoToken == address(0)) revert ZeroAddress("mdaoToken");
    if (_auditor == address(0))   revert ZeroAddress("auditor");
    mdaoToken = IERC20(_mdaoToken);
    auditor = _auditor;
}
error ZeroAddress(string param);
```

**Deploy скрипт:**

```solidity
// DeployInsuranceFund.s.sol — исправленная версия
address auditor = vm.envAddress("INSURANCE_AUDITOR_ADDRESS");
require(auditor != address(0), "INSURANCE_AUDITOR_ADDRESS not set");

InsuranceFund fund = new InsuranceFund(
    address(mdaoToken),
    auditor
);
```

**Как задать адрес:**

```bash
# .env.testnet
INSURANCE_AUDITOR_ADDRESS=0xYourMultisigOrEOA

forge script DeployInsuranceFund --rpc-url $BSC_TESTNET_RPC \
  --broadcast --verify --etherscan-api-key $BSCSCAN_KEY
```

#### Как проверить

```solidity
function testConstructorRevertsOnZeroAuditor() public {
    vm.expectRevert(abi.encodeWithSelector(InsuranceFund.ZeroAddress.selector, "auditor"));
    new InsuranceFund(address(mdaoToken), address(0));
}
```

---

### #4 · Ownership не передан на Gnosis Safe

**Риск:** CRITICAL · **Усилие:** S (1д) · **Блокирует:** mainnet

#### Что происходит

VISION §4.3 требует Gnosis Safe 3-of-5 как owner Paymaster и SocialRecoveryModule. Deploy скрипт передаёт ownership на TimelockController, но Safe не добавлен как proposer — deployer wallet остаётся единственным, кто может вызывать admin-функции.

#### Решение

```solidity
// FinalizeDeploy.s.sol — отдельный скрипт финализации
contract FinalizeDeploy is Script {

    address constant GNOSIS_SAFE   = 0x...; // 3-of-5 Safe (создать заранее)
    address constant TIMELOCK      = 0x...; // уже задеплоен
    uint256 constant TIMELOCK_DELAY = 2 days;

    function run() external {
        // 1. TimelockController: добавить Safe как proposer + executor
        TimelockController timelock = TimelockController(payable(TIMELOCK));

        vm.startBroadcast();
        timelock.grantRole(timelock.PROPOSER_ROLE(), GNOSIS_SAFE);
        timelock.grantRole(timelock.EXECUTOR_ROLE(), GNOSIS_SAFE);
        // Опционально: revoke deployer как proposer
        timelock.revokeRole(timelock.PROPOSER_ROLE(), msg.sender);

        // 2. Убедиться что Paymaster owner = TIMELOCK (не deployer)
        MDAOPaymaster paymaster = MDAOPaymaster(PAYMASTER_ADDRESS);
        require(paymaster.owner() == TIMELOCK, "Paymaster owner must be Timelock");

        // 3. SocialRecoveryModule: owner = TIMELOCK
        SocialRecoveryModule recovery = SocialRecoveryModule(RECOVERY_MODULE);
        require(recovery.owner() == TIMELOCK, "Recovery owner must be Timelock");

        vm.stopBroadcast();

        console.log("Safe:", GNOSIS_SAFE, "is now proposer of Timelock:", TIMELOCK);
    }
}
```

**Safe setup (через UI):**

```
1. safe.global → Create Safe → 3-of-5 signers (team members)
2. Записать адрес Safe в GNOSIS_SAFE константу
3. forge script FinalizeDeploy --broadcast
4. Верифицировать: timelock.hasRole(PROPOSER_ROLE, safe) == true
```

#### Как проверить

```bash
cast call $TIMELOCK \
  "hasRole(bytes32,address)(bool)" \
  $(cast keccak "PROPOSER_ROLE") $GNOSIS_SAFE \
  --rpc-url $BSC_RPC
# должно вернуть: true
```

---

### #5 · ROADMAP.md полностью устарел

**Риск:** LOW (документация) · **Усилие:** XS (2–3ч) · **Блокирует:** —

#### Что происходит

Документ описывает DAO governance, Chainlink, Hardhat, Treasury — ничего из этого в коде нет. Вводит в заблуждение новых разработчиков и инвесторов при due diligence.

#### Решение

Заменить содержимое на актуальный план:

```markdown
# MDAOPay Roadmap

## Текущий стек (факт)
- ERC-4337 Account Abstraction (v0.6) на BSC
- Paymaster: MDAO/USDT gasless транзакции
- Social Recovery: 2-of-3 guardian, P-256 WebAuthn
- Backend: Kotlin/Ktor + AWS KMS
- Mobile: Android/Compose + Passkeys
- Relay: Cloudflare Workers (bundler proxy)

## Phase 1 — Testnet (Q3 2026)
- [ ] KMS signer live
- [ ] SIWE auth flow
- [ ] Guardian push notifications
- [ ] BSC Testnet (chainId=97) smoke tests

## Phase 2 — Mainnet (Q4 2026)
- [ ] Gnosis Safe 3-of-5 ownership
- [ ] InsuranceFund live
- [ ] iOS app
- [ ] ERC-4337 v0.7 migration

## Phase 3 — Ecosystem (Q1 2027)
- [ ] Arena / DEX / Flopi integrations
- [ ] Session keys (F-118)
- [ ] BLS signatures (если bundler поддержка)
```

---

## 🟠 HIGH — требуют фикса до mainnet

---

### #6 · Biometric session window: 300s для high-risk операций

**Риск:** HIGH · **Усилие:** XS (1ч) · **Блокирует:** mainnet

#### Что происходит

```kotlin
// KeystoreCrypto.kt
private fun getOrCreateBiometricKey(alias: String): SecretKey {
    return keyStore.getKey(alias, null) as? SecretKey
        ?: KeyGenerator.getInstance(KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(
                    300,  // ← 5 минут! TDD §2.3.6 требует 0s для high-risk
                    KeyProperties.AUTH_BIOMETRIC_STRONG
                )
                .build()
            )
        }.generateKey()
}
```

300 секунд означает: пользователь прошёл биометрию, положил телефон, через 4 минуты кто-то другой открыл приложение и отправил транзакцию без повторного подтверждения.

#### Решение

```kotlin
// KeystoreCrypto.kt — дифференцированные ключи
enum class KeySecurityLevel {
    STANDARD,    // 300s window — для некритичных операций
    HIGH_RISK    // 0s — каждый раз требует биометрию
}

fun getOrCreateKey(alias: String, level: KeySecurityLevel): SecretKey {
    val timeout = when (level) {
        KeySecurityLevel.HIGH_RISK -> 0    // per-use authentication
        KeySecurityLevel.STANDARD  -> 300
    }
    return keyStore.getKey(alias, null) as? SecretKey
        ?: KeyGenerator.getInstance(KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(
                    timeout,
                    KeyProperties.AUTH_BIOMETRIC_STRONG
                )
                // Для timeout=0 ОБЯЗАТЕЛЕН setInvalidatedByBiometricEnrollment(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
            )
        }.generateKey()
}

// Использование в WalletManager:
val sendKey    = KeystoreCrypto.getOrCreateKey("mdao_send_key",     KeySecurityLevel.HIGH_RISK)
val backupKey  = KeystoreCrypto.getOrCreateKey("mdao_backup_key",   KeySecurityLevel.HIGH_RISK)
val sessionKey = KeystoreCrypto.getOrCreateKey("mdao_session_key",  KeySecurityLevel.STANDARD)
```

**Важно:** ключи с `timeout=0` vs `timeout=300` — физически разные объекты в Keystore. Нужна **миграция**: при первом запуске новой версии переключаться на новые alias'ы и перешифровывать данные.

```kotlin
// MigrationManager.kt
fun migrateKeysIfNeeded() {
    if (!prefs.getBoolean("keys_migrated_v2", false)) {
        // Старый ключ с 300s → новый с 0s
        val oldMnemonic = KeystoreCrypto.decrypt("mdao_wallet_key", encryptedMnemonic)
        KeystoreCrypto.encrypt("mdao_send_key_v2", oldMnemonic, KeySecurityLevel.HIGH_RISK)
        prefs.edit().putBoolean("keys_migrated_v2", true).apply()
    }
}
```

---

### #7 · Certificate Pinning — placeholder хэши

**Риск:** HIGH · **Усилие:** S (полдня) · **Блокирует:** mainnet

#### Решение

**CI pipeline (GitHub Actions):**

```yaml
# .github/workflows/release.yml
- name: Generate cert pins
  run: |
    # Получаем актуальный пин для api.mdaopay.com
    PIN_API=$(openssl s_client -connect api.mdaopay.com:443 -showcerts </dev/null 2>/dev/null \
      | openssl x509 -pubkey -noout \
      | openssl pkey -pubin -outform der \
      | openssl dgst -sha256 -binary \
      | base64)
    
    # Backup пин из Let's Encrypt R3 (промежуточный CA)
    PIN_BACKUP="jQJTbIh0grw0/1TkHSumWb+Fs0Ggogr621gT3PvPKG0="
    
    echo "CERT_PIN_API=sha256/$PIN_API" >> $GITHUB_ENV
    echo "CERT_PIN_BACKUP=sha256/$PIN_BACKUP" >> $GITHUB_ENV

- name: Build release APK
  run: |
    ./gradlew assembleRelease \
      -PCERT_PIN_API="$CERT_PIN_API" \
      -PCERT_PIN_BACKUP="$CERT_PIN_BACKUP"
```

**Дополнительно — backup pin strategy:**

```kotlin
// NetworkModule.kt — правильная pin конфигурация
val pinner = CertificatePinner.Builder()
    // Primary: leaf cert пин
    .add("api.mdaopay.com", BuildConfig.CERT_PIN_API)
    // Backup 1: промежуточный CA (Let's Encrypt R3)
    .add("api.mdaopay.com", BuildConfig.CERT_PIN_BACKUP)
    // Backup 2: root CA (ISRG Root X1) — на случай смены промежуточного
    .add("api.mdaopay.com", "C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=")
    .build()
```

**Ротация:** при смене сертификата — обновить `CERT_PIN_API` в GitHub Secrets, пересобрать + force-update через Play Store.

---

### #8 · setCooldownPeriod: min 1 мин вместо 1 часа

**Риск:** HIGH · **Усилие:** XS (30 мин) · **Блокирует:** mainnet

#### Решение

```solidity
// MDAOPaymaster.sol
uint256 public constant MIN_COOLDOWN = 1 hours;  // было: 1 minutes
uint256 public constant MAX_COOLDOWN = 7 days;

function setCooldownPeriod(uint256 newCooldown) external onlyOwner {
    if (newCooldown < MIN_COOLDOWN) revert ErrCooldownTooShort();
    if (newCooldown > MAX_COOLDOWN) revert ErrCooldownTooLong();
    emit CooldownPeriodUpdated(cooldownPeriod, newCooldown);
    cooldownPeriod = newCooldown;
}
```

#### Тест

```solidity
function testCooldownMinEnforced() public {
    vm.prank(owner);
    vm.expectRevert(MDAOPaymaster.ErrCooldownTooShort.selector);
    paymaster.setCooldownPeriod(30 minutes);  // < 1 hour → revert
}

function testCooldownExactMinAccepted() public {
    vm.prank(owner);
    paymaster.setCooldownPeriod(1 hours);  // = 1 hour → ok
    assertEq(paymaster.cooldownPeriod(), 1 hours);
}
```

---

### #9 · Relay push — уведомление идёт владельцу, не guardian'ам

**Риск:** HIGH · **Усилие:** M (2д) · **Блокирует:** testnet UX

#### Что происходит

```typescript
// relay/src/recovery.ts — фактический код
async function notifyRecovery(walletAddress: string, env: Env) {
    // Уведомляет ВЛАДЕЛЬЦА кошелька, а не guardian'ов
    const fcmToken = await env.KV.get(`fcm:${walletAddress}`);
    await sendPush(fcmToken, "Recovery initiated on your wallet");
    // Guardians не уведомляются!
}
```

Guardian'ы не знают о запросе, не могут подтвердить — UX полностью сломан.

#### Решение

**KV структура (добавить reverse index):**

```typescript
// При регистрации guardian'а — сохранять reverse index
async function addGuardian(
    walletAddress: string,
    guardianAddress: string,
    env: Env
): Promise<void> {
    // Прямой индекс: wallet → guardians
    const guardians: string[] = JSON.parse(
        await env.KV.get(`guardians:${walletAddress}`) ?? "[]"
    );
    guardians.push(guardianAddress);
    await env.KV.put(`guardians:${walletAddress}`, JSON.stringify(guardians));

    // Обратный индекс: guardian → wallets (для нотификации)
    const guardianOf: string[] = JSON.parse(
        await env.KV.get(`guardian_of:${guardianAddress}`) ?? "[]"
    );
    if (!guardianOf.includes(walletAddress)) {
        guardianOf.push(walletAddress);
        await env.KV.put(`guardian_of:${guardianAddress}`, JSON.stringify(guardianOf));
    }
}

// Исправленный notifyRecovery
async function notifyRecovery(
    walletAddress: string,
    initiator: string,
    env: Env
): Promise<void> {
    // 1. Уведомить владельца (что его кошелёк атакуют)
    const ownerToken = await env.KV.get(`fcm:${walletAddress}`);
    if (ownerToken) {
        await sendPush(ownerToken, {
            title: "⚠️ Recovery initiated",
            body: "Someone is trying to recover your wallet. Tap to veto.",
            data: { type: "recovery_alert", wallet: walletAddress, initiator }
        });
    }

    // 2. Уведомить ВСЕХ guardian'ов (они должны подтвердить)
    const guardians: string[] = JSON.parse(
        await env.KV.get(`guardians:${walletAddress}`) ?? "[]"
    );

    await Promise.allSettled(
        guardians
            .filter(g => g.toLowerCase() !== initiator.toLowerCase()) // не нотифицировать инициатора
            .map(async (guardianAddress) => {
                const fcmToken = await env.KV.get(`fcm:${guardianAddress}`);
                if (!fcmToken) return;
                return sendPush(fcmToken, {
                    title: "🔑 Guardian action required",
                    body: `Confirm wallet recovery for ${walletAddress.slice(0, 6)}...`,
                    data: {
                        type: "guardian_confirm",
                        wallet: walletAddress,
                        initiator,
                        action: "confirm_recovery"
                    }
                });
            })
    );
}
```

**Android side — обработать оба типа:**

```kotlin
// MDAOFirebaseMessagingService.kt
override fun onMessageReceived(message: RemoteMessage) {
    when (message.data["type"]) {
        "recovery_alert"   -> showRecoveryAlert(message.data)
        "guardian_confirm" -> showGuardianConfirmation(message.data)
    }
}

private fun showGuardianConfirmation(data: Map<String, String>) {
    val wallet = data["wallet"] ?: return
    val intent = Intent(this, MainActivity::class.java).apply {
        putExtra("deeplink", "mdaopay://guardian/confirm?wallet=$wallet")
    }
    // Показать high-priority notification с кнопками Confirm/Reject
    NotificationCompat.Builder(this, "guardian_channel")
        .setContentTitle("Guardian confirmation needed")
        .addAction(R.drawable.ic_check, "Confirm", getPendingIntent(intent))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()
        .also { NotificationManagerCompat.from(this).notify(GUARDIAN_NOTIF_ID, it) }
}
```

---

### #10 · dailyWithdrawalCapBps: 5% в коде vs 50% в TDD

**Риск:** HIGH · **Усилие:** XS (30 мин) · **Блокирует:** mainnet

#### Анализ

Это не просто опечатка — это принципиальное расхождение в модели безопасности:

| Значение | Смысл | Последствие |
|---------|-------|-------------|
| 5% (код) | Можно вывести 5% баланса/день | Очень консервативно, ограничивает легитимное использование |
| 50% (TDD) | Можно вывести 50% баланса/день | Более разумно, но при компрометации теряется половина |

**Рекомендация:** TDD некорректен — 50% слишком агрессивно. Правильное значение зависит от ожидаемых объёмов. Предлагаем **25%** как компромисс:

```solidity
// MDAOPaymaster.sol — уточнить константу с обоснованием
uint256 public constant DEFAULT_DAILY_CAP_BPS = 2500; // 25%
// Обоснование: при $100k TVL → $25k/day вывод.
// При компрометации максимальный ущерб за 1 день = 25%.
// Timelock (2 days) позволяет среагировать до второго дня.

uint256 public constant MAX_DAILY_CAP_BPS = 5000;  // hard cap 50% (TDD §3.3)
uint256 public constant MIN_DAILY_CAP_BPS = 100;   // min 1% (анти-DoS)
```

**Также синхронизировать TDD:** либо изменить TDD на 25%, либо обосновать 50%.

---

### #11 · Rate limit fail-open при падении Redis

**Риск:** HIGH · **Усилие:** S (1д) · **Блокирует:** mainnet

#### Что происходит

```kotlin
// RateLimiter.kt — фактическая логика
suspend fun isLimited(ip: String): Boolean {
    return try {
        redisClient.incr("rl:$ip").let { count ->
            if (count == 1L) redisClient.expire("rl:$ip", 60)
            count > MAX_REQUESTS_PER_MINUTE
        }
    } catch (e: Exception) {
        log.warn("Redis unavailable, falling back to in-memory")
        isLimitedInMemory(ip)  // ← ПРОБЛЕМА: in-memory пропускает первый запрос
    }
}
```

При падении Redis — rate limiting работает per-instance (нет синхронизации между pod'ами) и первый запрос всегда проходит. DoS возможен через Redis crash.

#### Решение — fail-closed с graceful degradation

```kotlin
// RateLimiter.kt — исправленная версия
class RateLimiter(
    private val redis: RedisClient,
    private val fallback: Cache<String, AtomicInteger>,  // Caffeine
    private val metrics: MetricsService
) {
    suspend fun checkAndIncrement(ip: String): RateLimitResult {
        return try {
            checkRedis(ip)
        } catch (e: RedisException) {
            metrics.increment("rate_limit.redis_failure")
            log.error("Redis unavailable for rate limiting: ${e.message}")

            // Fail-closed: при полном падении Redis — блокируем всё
            // кроме health check endpoint
            if (isRedisHealthy()) {
                checkFallback(ip)  // Redis частично работает — degraded mode
            } else {
                // Redis полностью недоступен — возвращаем "лимит исчерпан"
                // чтобы не пропустить DoS
                RateLimitResult.BLOCKED_REDIS_DOWN
            }
        }
    }

    private suspend fun checkRedis(ip: String): RateLimitResult {
        // Lua script для атомарного incr+expire (предотвращает race condition)
        val luaScript = """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
        """
        val count = redis.eval(luaScript, listOf("rl:$ip"), listOf("60")).toLong()
        return if (count > MAX_REQUESTS_PER_MINUTE) RateLimitResult.BLOCKED else RateLimitResult.ALLOWED
    }

    private fun checkFallback(ip: String): RateLimitResult {
        val counter = fallback.get(ip) { AtomicInteger(0) }!!
        val count = counter.incrementAndGet()
        // In-memory лимит в 2x строже (нет синхронизации между pod'ами)
        return if (count > MAX_REQUESTS_PER_MINUTE / 2) RateLimitResult.BLOCKED else RateLimitResult.ALLOWED
    }
}

enum class RateLimitResult { ALLOWED, BLOCKED, BLOCKED_REDIS_DOWN }

// В роуте — обработать оба blocked варианта одинаково (не раскрывать причину)
val result = rateLimiter.checkAndIncrement(extractClientIp(call))
if (result != RateLimitResult.ALLOWED) {
    call.respond(HttpStatusCode.TooManyRequests, "Rate limit exceeded")
    return@post
}
```

---

## 🟡 MEDIUM

---

### #12 · SSS: N=4 вместо N=5 (TDD §2.3.3)

**Риск:** MEDIUM · **Усилие:** S · **Блокирует:** —

TDD §2.3.3: "Standard recovery: N=5, K=3". Код использует N=4.

```kotlin
// ShamirSecretSharing.kt — найти и исправить
const val SSS_N = 5  // было: 4
const val SSS_K = 3  // threshold остаётся

// Важно: при изменении N, существующие бекапы (N=4) становятся несовместимы
// Нужна версионизация SSS-бекапов:
data class SSSBackup(
    val version: Int = 2,      // v1=N4, v2=N5
    val shares: List<String>,
    val threshold: Int
)
```

**Примечание:** если N=4 — осознанное решение (меньше точек отказа при потере share), обновить TDD и добавить обоснование. N=5/K=3 даёт больше flexibility при потере шерд, но сложнее в UX.

---

### #13 · RootBeer подключён, но не используется

**Риск:** MEDIUM · **Усилие:** XS · **Блокирует:** —

```kotlin
// build.gradle.kts — зависимость есть
implementation("com.scottyab:rootbeer-lib:0.1.0")

// DeviceIntegrityManager.kt — используется ручная проверка
private fun isRooted(): Boolean {
    return checkSuBinary() || checkBusybox() || ...  // ручная, менее надёжная
}
```

#### Решение

```kotlin
// DeviceIntegrityManager.kt — использовать RootBeer
import com.scottyab.rootbeer.RootBeer

private fun isRooted(): Boolean {
    val rootBeer = RootBeer(context)
    return rootBeer.isRootedWithoutBusyBoxCheck()
        // RootBeer + ручные проверки для defence-in-depth
        || checkSuBinary()
        || checkDangerousProps()
}
```

---

### #14 · Нет networkSecurityConfig.xml

**Риск:** MEDIUM · **Усилие:** XS (1ч) · **Блокирует:** —

```xml
<!-- res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- Запрет cleartext для всех доменов -->
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>

    <!-- Dev/staging: разрешить user-trusted CA для Charles/mitmproxy -->
    <!-- Этот блок включается только в debug build через:
         src/debug/res/xml/network_security_config.xml -->

    <!-- Pinning (дополняет OkHttp CertificatePinner) -->
    <domain-config>
        <domain includeSubdomains="true">api.mdaopay.com</domain>
        <pin-set expiration="2027-01-01">
            <pin digest="SHA-256"><!-- PRIMARY_PIN --></pin>
            <pin digest="SHA-256"><!-- BACKUP_PIN --></pin>
        </pin-set>
    </domain-config>
</network-security-config>
```

```xml
<!-- AndroidManifest.xml -->
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

---

### #15 · Cloud backup не реализован (PRD §7)

**Риск:** MEDIUM · **Усилие:** L (1–2н) · **Блокирует:** —

PRD §7 описывает backup через Google Drive/iCloud. Реализован только SSS-шеринг через QR/NFC. Для fullrecovery без guardian'ов нужен независимый путь.

```kotlin
// BackupRepository.kt — заглушка для реализации
interface CloudBackupProvider {
    suspend fun uploadEncryptedBackup(data: EncryptedBackup): Result<String>  // → fileId
    suspend fun downloadBackup(fileId: String): Result<EncryptedBackup>
    suspend fun listBackups(): Result<List<BackupMetadata>>
}

// GoogleDriveBackupProvider.kt
class GoogleDriveBackupProvider(
    private val driveService: Drive  // Google Drive API v3
) : CloudBackupProvider {

    override suspend fun uploadEncryptedBackup(data: EncryptedBackup): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val content = ByteArrayContent("application/octet-stream", data.toBytes())
                val file = com.google.api.services.drive.model.File().apply {
                    name = "mdaopay_backup_${data.timestamp}.enc"
                    parents = listOf("appDataFolder")  // hidden app folder
                }
                driveService.files().create(file, content)
                    .setFields("id")
                    .execute()
                    .id
            }
        }
    }
}
```

---

### #16 · FCM не подключён к recovery flow

**Риск:** MEDIUM · **Усилие:** M · **Блокирует:** testnet UX

Решение описано в #9 (guardian push) — то же самое для recovery flow. FCM token нужно регистрировать при входе:

```kotlin
// AuthRepository.kt — добавить после успешного SIWE login
Firebase.messaging.token.addOnSuccessListener { fcmToken ->
    lifecycleScope.launch {
        apiClient.registerFcmToken(walletAddress, fcmToken)
    }
}

// + обновление при onNewToken:
class MDAOFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        // Обновить токен на сервере
        ...
    }
}
```

---

### #17 · Deep link валидация отсутствует

**Риск:** MEDIUM · **Усилие:** S · **Блокирует:** —

```kotlin
// DeepLinkHandler.kt — добавить валидацию
object DeepLinkHandler {
    private val ALLOWED_SCHEMES = setOf("mdaopay")
    private val ALLOWED_HOSTS   = setOf("pay", "guardian", "recovery", "connect")

    // Паттерны для адресов/сумм
    private val ADDRESS_REGEX = Regex("^0x[0-9a-fA-F]{40}$")
    private val AMOUNT_REGEX  = Regex("^[0-9]+(\\.[0-9]{1,18})?$")

    fun parse(uri: Uri): DeepLinkAction? {
        if (uri.scheme !in ALLOWED_SCHEMES) return null
        if (uri.host !in ALLOWED_HOSTS) return null

        return when (uri.host) {
            "pay" -> {
                val address = uri.getQueryParameter("to") ?: return null
                val amount  = uri.getQueryParameter("amount") ?: return null

                // Валидация параметров
                if (!ADDRESS_REGEX.matches(address)) return null
                if (!AMOUNT_REGEX.matches(amount)) return null

                DeepLinkAction.Pay(address, amount.toBigDecimal())
            }
            "guardian" -> {
                val wallet = uri.getQueryParameter("wallet") ?: return null
                if (!ADDRESS_REGEX.matches(wallet)) return null
                DeepLinkAction.GuardianConfirm(wallet)
            }
            else -> null
        }
    }
}
```

---

### #18 · Две user-таблицы без связи

**Риск:** MEDIUM · **Усилие:** M · **Блокирует:** —

```sql
-- Migration V3__consolidate_users.sql
-- Объединяем users + auth_users в одну таблицу

BEGIN;

-- Добавляем auth поля в основную таблицу
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS wallet_address VARCHAR(42)  UNIQUE,
    ADD COLUMN IF NOT EXISTS email          VARCHAR(255) UNIQUE,
    ADD COLUMN IF NOT EXISTS password_hash  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS auth_type      VARCHAR(20)  NOT NULL DEFAULT 'email';

-- Переносим данные из auth_users (если она существует)
INSERT INTO users (wallet_address, email, password_hash, auth_type, nickname, created_at)
SELECT wallet_address, email, password_hash, 'email', nickname, created_at
FROM auth_users
ON CONFLICT (wallet_address) DO UPDATE
    SET email = EXCLUDED.email,
        password_hash = EXCLUDED.password_hash;

-- Удаляем дублирующую таблицу
DROP TABLE IF EXISTS auth_users;

COMMIT;
```

---

### #19 · Один CircuitBreaker на все 3 price oracle

**Риск:** MEDIUM · **Усилие:** S · **Блокирует:** —

```kotlin
// PriceOracleService.kt — per-source circuit breakers
class PriceOracleService {
    // Отдельный breaker для каждого источника
    private val dexScreenerBreaker = CircuitBreaker.ofDefaults("dexscreener")
    private val coinGeckoBreaker   = CircuitBreaker.ofDefaults("coingecko")
    private val binanceBreaker     = CircuitBreaker.ofDefaults("binance")

    suspend fun getMdaoPrice(): BigDecimal {
        val prices = listOfNotNull(
            fetchWithBreaker(dexScreenerBreaker) { dexScreener.getPrice() },
            fetchWithBreaker(coinGeckoBreaker)   { coinGecko.getPrice() },
            fetchWithBreaker(binanceBreaker)      { binance.getPrice() }
        )

        return when (prices.size) {
            0    -> error("All price oracles failed")
            1    -> prices.single()
            else -> prices.sorted()[prices.size / 2]  // медиана
        }
    }

    private suspend fun fetchWithBreaker(
        breaker: CircuitBreaker,
        fetch: suspend () -> BigDecimal
    ): BigDecimal? = try {
        breaker.executeCoroutine { fetch() }
    } catch (e: Exception) {
        log.warn("Price oracle failed (${breaker.name}): ${e.message}")
        null
    }
}
```

---

### #20 · Таблица recovery_events не создана

**Риск:** MEDIUM · **Усилие:** XS · **Блокирует:** —

```sql
-- Migration V4__add_recovery_events.sql
CREATE TABLE recovery_events (
    id              BIGSERIAL PRIMARY KEY,
    wallet_address  VARCHAR(42)  NOT NULL,
    event_type      VARCHAR(50)  NOT NULL,
    initiator       VARCHAR(42),
    guardian_addr   VARCHAR(42),
    tx_hash         VARCHAR(66),
    block_number    BIGINT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    metadata        JSONB
);

CREATE INDEX idx_recovery_events_wallet ON recovery_events(wallet_address);
CREATE INDEX idx_recovery_events_type   ON recovery_events(event_type, created_at);
```

---

### #21 · MAX_GUARDIANS = 3 (TDD DoS-секция требует 5)

**Риск:** MEDIUM · **Усилие:** XS · **Блокирует:** —

TDD §3.11 «DoS Analysis» предполагает MAX_GUARDIANS=5. С N=3 и threshold=2 — потеря 2 guardian'ов блокирует recovery.

```solidity
// SocialRecoveryModule.sol
uint8 public constant MAX_GUARDIANS = 5; // было: 3
uint8 public constant MIN_THRESHOLD = 2;
// При N=5, K=2: потеря 3 guardian'ов — ещё можно восстановиться
// При N=5, K=3: более безопасно, но жёстче требования
```

**Изменение MAX_GUARDIANS — не breaking change** (существующие кошельки с 2-3 guardian'ами продолжат работать).

---

### #22 · VISION упоминает BLS, код использует P-256

**Риск:** LOW (документация) · **Усилие:** XS · **Блокирует:** —

BLS-подписи (bls12-381) обеспечивают агрегацию нескольких guardian-подписей в одну — это существенно снижает gas cost recovery (1 verify вместо N). Но:
- ERC-4337 v0.6 bundler'ы не поддерживают BLS нативно
- P-256/WebAuthn — стандарт Passkeys, интегрирован в Android/iOS
- BLS библиотеки на Solidity (mcl-BLS12381) — аудитованы ограниченно

**Рекомендация:** оставить P-256, убрать BLS из VISION до тех пор, пока это не станет реальным планом с конкретными сроками.

---

## 🔵 LOW — документация и технический долг

---

### #23 · GF256 полином: 0x11D → 0x11B (опечатка в TDD)

```
// TDD §2.3.3 — исправить
// Было: GF(2^8) irreducible polynomial 0x11D
// Стало: GF(2^8) irreducible polynomial 0x11B (x^8 + x^4 + x^3 + x + 1)
// 0x11D = x^8 + x^4 + x^3 + x^2 + 1 — не является примитивным для GF(2^8)
// 0x11B — стандартный полином AES GF(2^8), используется в Shamir implementations
```

Также проверить реальный полином в `ShamirSecretSharing.kt`:
```kotlin
// Должно быть:
private const val PRIME = 0x11B  // AES-compatible GF(2^8)
```

---

### #24 · Chain ID: 97 в TDD, 56 в коде

Мы разбирали этот блокер ранее. Добавить в TDD явную таблицу окружений:

```markdown
| Environment | Chain ID | RPC | Explorer |
|------------|----------|-----|---------|
| Development | 97 | https://data-seed-prebsc-1-s1.binance.org:8545 | https://testnet.bscscan.com |
| Staging     | 97 | Alchemy BSC Testnet | https://testnet.bscscan.com |
| Production  | 56 | Alchemy BSC Mainnet | https://bscscan.com |
```

---

### #25 · code-roadmap.md: WebSocket не реализован

```markdown
// code-roadmap.md — исправить описание transport layer
// Было: "WebSocket connection for real-time updates"
// Стало: "REST polling + FCM push notifications"
// Обоснование: Cloudflare Workers не поддерживают persistent WebSocket
// в Serverless режиме. FCM достаточен для recovery/guardian events.
// WebSocket — возможный roadmap item для Cloudflare Durable Objects.
```

---

### #26 · 15+ env vars не документированы

Создать `backend/ENV.md`:

```markdown
# Backend Environment Variables

## Обязательные (production)
| Variable | Example | Description |
|----------|---------|-------------|
| `DATABASE_URL` | `jdbc:postgresql://...` | PostgreSQL connection string |
| `REDIS_URL` | `redis://:pass@host:6379` | Redis URL с паролем |
| `JWT_SECRET` | `<base64 256-bit>` | HMAC-SHA256 ключ, min 44 символа |
| `KMS_KEY_ID` | `arn:aws:kms:...` | ARN KMS ключа для Paymaster signing |
| `EXPECTED_CHAIN_ID` | `56` | BSC=56, BSC Testnet=97 |
| `PAYMASTER_ADDRESS` | `0x...` | Адрес задеплоенного MDAOPaymaster |
| `ENTRY_POINT_ADDRESS` | `0x5FF1...` | ERC-4337 EntryPoint v0.6 |
| `INSURANCE_AUDITOR_ADDRESS` | `0x...` | Auditor multisig адрес |

## Опциональные (defaults)
| Variable | Default | Description |
|----------|---------|-------------|
| `ALLOW_LOCAL_SIGNING` | `false` | true только для testnet |
| `PAYMASTER_PRIVATE_KEY` | — | Только если ALLOW_LOCAL_SIGNING=true |
| `MAX_REQUESTS_PER_MINUTE` | `60` | Rate limit per IP |
| `COOLDOWN_PERIOD_SECONDS` | `3600` | Paymaster quote cooldown |
| `DAILY_CAP_BPS` | `2500` | Daily withdrawal cap (25%) |
| `PRICE_BUFFER_BPS` | `500` | Price buffer (5%) |
| `HIRAKICP_POOL_SIZE` | `25` | DB connection pool |
```

---

### #27 · Relay: структуры payload не документированы

Добавить в TDD §4.x (Relay API):

```typescript
// relay/src/types.ts — добавить JSDoc
interface RecoveryNotifyPayload {
    walletAddress: string;          // checksummed address
    initiator: string;              // guardian initiating recovery
    guardianPubKeyX: string;        // P-256 X coordinate (hex, 32 bytes)
    guardianPubKeyY: string;        // P-256 Y coordinate (hex, 32 bytes)
    nonce: number;                  // replay protection (монотонно возрастающий)
    timestamp: number;              // Unix ms
    signature: string;              // ECDSA sig of keccak256(walletAddress + nonce + timestamp)
}

interface PushPayload {
    type: "recovery_alert" | "guardian_confirm" | "recovery_executed" | "recovery_vetoed";
    wallet: string;
    initiator?: string;
    txHash?: string;
    expiresAt?: number;             // Unix ms — когда истекает окно подтверждения
}
```

---

## Итоговая матрица приоритетов

| # | Finding | Риск | Усилие | Блокирует | Статус решения |
|---|---------|------|--------|-----------|----------------|
| 1 | KMS stub | CRITICAL | L | mainnet | ✅ Решение дано (KmsPaymasterSigner) |
| 2 | SIWE отсутствует | CRITICAL | M | mainnet | ✅ Решение дано |
| 3 | InsuranceFund auditor=0 | CRITICAL | XS | mainnet | ✅ 1-строчный guard |
| 4 | Gnosis Safe ownership | CRITICAL | S | mainnet | ✅ FinalizeDeploy.s.sol |
| 5 | ROADMAP устарел | LOW | XS | — | ✅ Шаблон дан |
| 6 | Biometric 300s | HIGH | XS | mainnet | ✅ Решение дано |
| 7 | Cert pinning placeholder | HIGH | S | mainnet | ✅ CI pipeline дан |
| 8 | Cooldown min=1min | HIGH | XS | mainnet | ✅ 1 константа |
| 9 | Guardian push отсутствует | HIGH | M | testnet | ✅ Reverse index дан |
| 10 | DailyCap 5% vs 50% | HIGH | XS | mainnet | ✅ 25% компромисс |
| 11 | RateLimit fail-open | HIGH | S | mainnet | ✅ Lua + fail-closed |
| 12 | SSS N=4 vs N=5 | MEDIUM | S | — | ⚠️ Требует решения по UX |
| 13 | RootBeer не используется | MEDIUM | XS | — | ✅ 3 строки |
| 14 | networkSecurityConfig | MEDIUM | XS | — | ✅ XML дан |
| 15 | Cloud backup | MEDIUM | L | — | ✅ Interface дан |
| 16 | FCM recovery flow | MEDIUM | M | testnet | ✅ Решение дано |
| 17 | Deep link валидация | MEDIUM | S | — | ✅ Regex + whitelist |
| 18 | Две user-таблицы | MEDIUM | M | — | ✅ Migration дана |
| 19 | Один CircuitBreaker | MEDIUM | S | — | ✅ Per-source |
| 20 | recovery_events таблица | MEDIUM | XS | — | ✅ Migration дана |
| 21 | MAX_GUARDIANS=3 vs 5 | MEDIUM | XS | — | ✅ 1 константа |
| 22 | BLS в VISION | LOW | XS | — | ✅ Убрать из VISION |
| 23 | GF256 полином опечатка | LOW | XS | — | ✅ Исправить TDD |
| 24 | Chain ID 97/56 | LOW | XS | — | ✅ Таблица окружений |
| 25 | WebSocket в roadmap | LOW | XS | — | ✅ Исправить |
| 26 | env vars не документированы | LOW | XS | — | ✅ ENV.md дан |
| 27 | Relay payload структуры | LOW | XS | — | ✅ types.ts JSDoc |

---

## Рекомендуемый порядок работ

### Sprint 1 (1 неделя) — разблокировать testnet
1. **#3** InsuranceFund guard (30 мин)
2. **#8** Cooldown min (30 мин)
3. **#21** MAX_GUARDIANS=5 (30 мин)
4. **#6** Biometric 0s для high-risk (2ч)
5. **#14** networkSecurityConfig.xml (1ч)
6. **#9** Guardian push notifications (2д)
7. **#16** FCM recovery flow (1д)

### Sprint 2 (2 недели) — security hardening
1. **#1** KMS signer реализация (5д)
2. **#2** SIWE auth (3д)
3. **#11** Rate limit fail-closed (1д)
4. **#7** CI cert pinning (полдня)
5. **#17** Deep link validation (1д)

### Sprint 3 (до mainnet) — governance + финализация
1. **#4** Gnosis Safe setup + FinalizeDeploy (1д)
2. **#10** DailyCap alignment (30 мин)
3. **#18** User tables consolidation (2д)
4. **#19** Per-source circuit breakers (1д)
5. **#5** ROADMAP.md переписать (2ч)

---

*Аудит проведён на основе кодовой базы: contracts/ (12 .sol), backend/ (22 .kt), app/ (220+ файлов), relay/ (6 .ts), PRD/TDD/VISION/ROADMAP*
