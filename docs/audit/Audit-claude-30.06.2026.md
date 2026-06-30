## Аудит MDAOPay — на основе доступных файлов

Важная оговорка сначала: у меня нет доступа к `contracts/src/`, `backend/src/`, `relay/src/` — только к mobile-кодовой базе (Kotlin/Compose) и текстам PRD/TDD. Все находки ниже подтверждены конкретными файлами из контекста; там, где нужна проверка контракта/бэкенда, я это явно помечаю.

---

### 🔴 Блокеры тестнета (приложение не заработает как есть)

#### 1. Certificate pinning на плейсхолдерах — все HTTPS-запросы упадут

`NetworkModule.kt`:

kotlin

```kotlin
val pinner = CertificatePinner.Builder()
    .add("api.mdaopay.com", BuildConfig.CERT_PIN_API)
    .add("mdaopay.com", BuildConfig.CERT_PIN_BACKUP)
    .build()
```

а в `app/build.gradle.kts`:

kotlin

```kotlin
buildConfigField("String", "CERT_PIN_API", "\"${project.findProperty("CERT_PIN_API") ?: "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="}\"")
```

Если реальные SHA-256 пины не переданы через project properties (а в CI/локальной сборке без них они и не передаются), `OkHttpClient` будет настроен на заведомо несуществующий pin → **любой TLS-хендшейк с этим клиентом завершится `SSLPeerUnverifiedException`**. Этот `OkHttpClient` — синглтон через Hilt, используется везде (Paymaster, Relay, Etherscan, Nickname resolver косвенно). Без реальных пинов приложение не сможет говорить с бэкендом вообще.

**Как исправить:** перед тестнетом — сгенерировать реальные пины (`openssl s_client ... | openssl x509 -pubkey ... | openssl dgst -sha256`) и прокинуть через CI secrets / `local.properties`. Дополнительно: добавить **backup pin** (запасной CA) — сейчас два пина это primary+backup для разных доменов, а не primary+backup для одного домена, что не защищает от ротации сертификата api.mdaopay.com.

**Как проверить:** собрать `assembleDevDebug` без project properties и сделать любой network-call (например onboarding → backend ping) — должно упасть с `SSLPeerUnverifiedException`.

---

#### 2. RPC URL (Sepolia) не соответствует Chain ID (BSC=56)

`NetworkConfig.kt`:

kotlin

```kotlin
const val CHAIN_ID = 56L  // BSC Mainnet
```

`app/build.gradle.kts` (dev flavor):

kotlin

```kotlin
buildConfigField("String", "RPC_URL_1", "\"${project.findProperty("RPC_URL_1_DEV") ?: "https://ethereum-sepolia.publicnode.com"}\"")
buildConfigField("String", "RPC_URL_2", "\"...rpc.ankr.com/eth_sepolia\"")
```

Это два совершенно разных Chain ID: BSC = 56, Ethereum Sepolia = 11155111. При этом `EntryPoint`, `SimpleAccountFactory` и контрактные адреса в `NetworkConfig` — захардкожены под BSC. Любой вызов `eth_call`/`eth_sendUserOperation` на Sepolia RPC с этими адресами либо упадёт (`no code at address`), либо тихо вернёт мусор.

**Это прямой блокер**: HomeViewModel.loadWalletInternal вызывает `blockchainRepository.getUsdtBalance/getMdaoBalance/getEthBalance` через этот RPC — баланс будет либо нулевым, либо ошибкой.

**Как исправить:** либо сменить `CHAIN_ID` на 97 (BSC Testnet) и подставить BSC Testnet RPC + адреса тестовых контрактов, либо для dev-флейвора подставить настоящий BSC Testnet RPC (`https://data-seed-prebsc-1-s1.binance.org:8545` или аналог). Также синхронизировать `EtherscanRepository` → сейчас `ETHERSCAN_API_URL = "https://api.bscscan.com/api"` — это уже BSC, что лишний раз подтверждает: RPC указывает не туда.

**Как проверить:** `EthereumClient.getWeb3j().ethChainId().send()` — сравнить с `NetworkConfig.CHAIN_ID`, должно совпасть.

---

#### 3. `MDAO_CONTRACT` и `SOCIAL_RECOVERY_MODULE` — нулевой адрес

`NetworkConfig.kt`:

kotlin

```kotlin
const val MDAO_CONTRACT = "0x0000000000000000000000000000000000000000" // Set by deploy script
const val SOCIAL_RECOVERY_MODULE = "0x0000000000000000000000000000000000000000" // Set by deploy script
```

Это означает:

- `BlockchainRepository.getMdaoBalance()` всегда будет дергать `balanceOf` у нулевого адреса (контракта там нет) → ошибка или 0.
- `RecoveryUserOpBuilder`/`GuardianUserOpBuilder` шлют `execute(SOCIAL_RECOVERY_MODULE, ...)` на нулевой адрес → recovery/guardian-флоу гарантированно сломан on-chain (UserOp будет успешно засабмичен бандлером, но revert-нется при исполнении, либо EntryPoint его просто не выполнит).

**Это знакомый блокер** — `NetworkConfig.isConfigured()` уже существует в коде как ровно такая проверка:

kotlin

```kotlin
fun isConfigured(): Boolean =
    MDAO_CONTRACT != "0x0000...0000" && SOCIAL_RECOVERY_MODULE != "0x0000...0000"
```

**Проблема:** эта функция определена, но **нигде не вызывается** — ни в `MainActivity`, ни в `HomeViewModel`, ни в `RecoveryViewModel`. Сейчас приложение тихо продолжит работу с неконфигурированными контрактами, вместо явного экрана "контракты не задеплоены".

**Как исправить:** вызывать `NetworkConfig.isConfigured()` в `HomeViewModel.loadWalletInternal()` и блокировать onramp/send/recovery с понятным сообщением, пока деплой не подставит реальные адреса.

---

### 🟠 Критические находки (безопасность / целостность данных)

#### 4. `eth_sendTransaction` через injected provider всегда возвращает фейковый хэш — реальная транзакция не отправляется

`EthereumProviderInjector.kt`, `handleSendTransaction`:

kotlin

```kotlin
private fun handleSendTransaction(params: JSONArray, origin: String): String {
    ...
    if (!confirmAction(origin, "Send transaction to:\n$to\nValue: $value")) {
        return """{"jsonrpc":"2.0","id":"1","error":{"code":-32000,"message":"User rejected transaction"}}"""
    }
    return """{"jsonrpc":"2.0","id":"1","result":"0x0000000000000000000000000000000000000000000000000000000000000000"}"""
}
```

Даже после того как пользователь нажал "Approve" в системном диалоге, метод **не строит и не отправляет UserOperation** — он просто возвращает захардкоженный нулевой хэш. dApp, подключенный через этот мост (`window.ethereum.request({method: 'eth_sendTransaction', ...})`), получит "успешный" хэш транзакции, которой никогда не существовало. Это либо незавершённый стаб (наиболее вероятно, судя по комментарию архитектуры в TDD §2.7 "eth_sendTransaction → build UserOperation → Paymaster → Bundler", что не реализовано), либо реальная дыра, если кто-то полагается на этот хэш для UX-подтверждения.

**Почему опасно:** dApp-интеграции (Identity Connect / SessionKeyModule из PRD §17) построены вокруг этого моста. Если он замокан — вся ecosystem-интеграция (Arena, DEX, Flopi) не работает на уровне транзакций, при этом пользователю показывается "успех".

**Как исправить:** либо явно вернуть `error` ("eth_sendTransaction not yet supported, use native UI"), либо реально прокинуть вызов через `SendRepository.buildUserOp` → `PaymasterClient.signUserOp` → `BundlerClient.sendUserOperation`, как это уже сделано для нативного Send-флоу.

**Как проверить:** открыть WebView с тестовым dApp на `app.mdaopay.xyz`, вызвать `eth_sendTransaction`, посмотреть, что хэш всегда `0x000...000` независимо от параметров.

---

#### 5. TxQueue (очередь офлайн-транзакций) хранится в открытом виде, вопреки TDD

TDD §2.2.3 прямо требует:

> `signedRawTx` contains a fully signed, broadcast-ready transaction. It MUST be encrypted at rest... encrypt the `signedRawTx` field with a Keystore-bound AES-256-GCM key

Но в реальности `TxQueueEntity.kt`:

kotlin

```kotlin
@Entity(tableName = "tx_queue")
data class TxQueueEntity(
    @PrimaryKey val idempotencyKey: String,
    val recipientAddress: String,
    val weiAmount: String,
    val nickname: String,
    val displayAmount: String,
    val createdAt: Long,
    val retryCount: Int = 0,
    val lastError: String? = null
)
```

Полей `signedRawTx` тут вообще нет — `TxQueue.enqueue()` хранит сырые `recipientAddress`/`weiAmount`/`displayAmount` без какой-либо подписи и без шифрования (Room-таблица — обычный SQLite файл, доступный при root/backup). Это расхождение с архитектурным описанием в TDD: реализация ушла от "broadcast-ready signed tx" к "queue intent and re-sign on retry" (что подтверждается `OfflineSyncWorker`, который вызывает `sendRepository.sendUsdt()` заново, а не broadcast'ит готовую подпись).

**Почему это всё равно проблема:** даже без подписи, `recipientAddress`+`weiAmount` в открытом SQLite — это информация о намерении пользователя отправить деньги, которая переживёт удаление приложения через `adb backup` на rooted-устройстве (хотя `allowBackup="false"` в манифесте это частично митигирует).

**Как исправить:** либо обновить TDD под фактическую модель (intent queue, не signed-tx queue) — это безопаснее текущей модели и проще; либо, если решили оставить как есть, явно задокументировать риск и обеспечить, что `tx_queue.db` создаётся в `context.filesDir`, а не в общедоступном месте (нужно проверить `DatabaseModule.kt` — `Room.databaseBuilder(context.applicationContext, ...)` использует дефолтный путь, что нормально, но без encryption-at-rest, как и предупреждает TDD).

---

#### 6. `KeystoreCrypto.encrypt()` всегда использует biometric-bound ключ, но вызывается без BiometricPrompt

`KeystoreCrypto.kt`:

kotlin

```kotlin
fun encrypt(keyAlias: String, plaintext: ByteArray): ByteArray {
    val key = getOrCreateBiometricKey(keyAlias)
    val cipher = Cipher.getInstance(AES_GCM)
    cipher.init(Cipher.ENCRYPT_MODE, key)   // ← может бросить UserNotAuthenticatedException
    ...
}
```

`getOrCreateBiometricKey` создаёт ключ с `setUserAuthenticationRequired(true)`. На API 30+ это означает `setUserAuthenticationParameters(300, BIOMETRIC_STRONG | DEVICE_CREDENTIAL)` — окно действия 300 секунд. Но `WalletManager.saveMnemonic()` вызывает `KeystoreCrypto.encrypt(KEY_ALIAS, ...)` **напрямую**, без предшествующего `BiometricPrompt`-разблокирования:

kotlin

```kotlin
fun saveMnemonic(mnemonic: String): Boolean {
    return try {
        val encrypted = KeystoreCrypto.encrypt(KEY_ALIAS, mnemonic.encodeToByteArray())
        ...
```

И вызывается это из `OnboardingNicknameViewModel.confirmNickname()` — **на онбординге, когда биометрия только что подтверждалась на ПРЕДЫДУЩЕМ экране** (`OnboardingBiometricScreen`). Если auth-окно (300с) ещё не истекло — сработает. Но это implicit-зависимость между экранами, не выраженная в коде явно: если кто-то переставит порядок экранов в навиграфе (`MDAONavGraph.kt`) или добавит retry-логику, `saveMnemonic()` начнёт падать с `InvalidKeyException`/`UserNotAuthenticatedException`, и это будет тихо проглочено в `catch (e: Exception) { false }`.

**Как исправить:** либо использовать `getOrCreateKey` (без auth requirement) для wallet-ключа (что противоречит TDD §2.3.1, где явно описана biometric-привязка mnemonic-ключа), либо явно документировать и проверять auth-окно перед вызовом `saveMnemonic`, добавив осмысленную ошибку вместо silent `false`.

**Как проверить:** в `OnboardingNicknameViewModel.confirmNickname()` добавить лог при `catch (e: Exception)` с реальным исключением (сейчас сообщение `"Wallet creation failed: ${e.message}"` — должно быть видно в проде, но стоит явно протестировать сценарий "биометрия истекла между экранами").

---

#### 7. WebAuthn raw-message signing без EIP-191-подобной защиты от подмены контекста

Раз контракта `SocialRecoveryModule.sol` нет в контексте, не могу подтвердить «raw authenticatorData без EIP-191 prefix» из твоего чек-листа этапа 2 — **нужно проверить в файле `contracts/src/SocialRecoveryModule.sol`, функция `_verifyWebAuthn`**, как описано в TDD §3.4 (она там задокументирована: `SHA-256(authenticatorData || SHA-256(clientDataJSON))`, что соответствует WebAuthn-стандарту, а не EIP-191 — это ожидаемо правильно для WebAuthn, EIP-191 тут неприменим). На мобильной стороне `PasskeyManager.kt` и `GuardianUserOpBuilder.extractWebAuthnAssertion` корректно извлекают `authenticatorData`/`clientDataJSON`/`signature` без модификации — выглядит соответствующим WebAuthn-спеке. **Без файла контракта не могу подтвердить или опровергнуть твой пункт про EIP-191 — нужно проверить `contracts/src/SocialRecoveryModule.sol`.**

---

### 🟡 Существенные находки (баги/несоответствия)

#### 8. Дублирующийся `RecoveryScreen.kt` в одном пакете — конфликт компиляции

Два файла, ОБА объявляют `@Composable fun RecoveryScreen` в `com.mdaopay.app.feature.recovery.presentation`:

- Документ 50: полная реализация (1400+ строк, с `RecoveryViewModel`, share-management, guardians).
- Документ 91, путь `app/.../RecoveryScreen.kt` (с **пробелом в конце имени файла**):

kotlin

```kotlin
@Composable
fun RecoveryScreen(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text("Recovery Screen — coming soon")
    }
}
```

Сигнатуры разные (`onBack: () -> Unit` vs полная с `biometricManager`, `inviteId`, `viewModel`), так что формально это **redeclaration error** при компиляции — два `@Composable fun RecoveryScreen` с разными сигнатурами в одном пакете не скомпилируются (Kotlin это допускает только как overload, что технически возможно, но точно не задумано — `MDAONavGraph.kt` вызывает версию с `biometricManager`, значит файл-заглушка — мёртвый/забытый артефакт, который надо удалить).

**Как исправить:** удалить файл-стаб (документ 91) полностью — он не используется навиграфом и создаёт риск, что кто-то случайно соберёт не ту версию или конфликт imports.

---

#### 9. `DeviceIntegrityManager.isRooted()` блокирует **все** операции, включая `APP_OPEN`

kotlin

```kotlin
suspend fun checkIntegrity(operation: WalletOperation): IntegrityResult {
    if (isRooted()) {
        return IntegrityResult(IntegrityLevel.BLOCKED, listOf("Rooted device"))
    }
    if (isEmulator()) {
        return IntegrityResult(IntegrityLevel.BLOCKED, listOf("Emulator"))
    }
    if (operation.riskLevel == RiskLevel.LOW) {
        return IntegrityResult(IntegrityLevel.TRUSTED, emptyList())
    }
    ...
```

`APP_OPEN` имеет `RiskLevel.LOW`, но проверка `isRooted()`/`isEmulator()` стоит **до** ветвления по riskLevel — то есть даже низкорисковые операции на рутованном/эмуляторном устройстве блокируются безусловно. Согласно TDD §2.3.8, таблица "Risk tier → Action mapping" говорит, что HIGH risk должен блокировать только recovery/send/backup/import, а не сам факт открытия приложения. Текущая реализация жёстче собственной спецификации — на эмуляторе (что важно для QA/CI тестирования перед тестнетом!) приложение вообще не пускает дальше.

**Это прямо мешает тестированию на тестнете**, если QA использует эмулятор для smoke-тестов (Maestro E2E из TDD §5.3 как раз гоняется на эмуляторе/CI).

**Как исправить:**

kotlin

```kotlin
suspend fun checkIntegrity(operation: WalletOperation): IntegrityResult {
    if (operation.riskLevel == RiskLevel.LOW) {
        return IntegrityResult(IntegrityLevel.TRUSTED, emptyList())
    }
    if (isRooted()) return IntegrityResult(IntegrityLevel.BLOCKED, listOf("Rooted device"))
    if (isEmulator()) return IntegrityResult(IntegrityLevel.BLOCKED, listOf("Emulator"))
    return checkPlayIntegrity(operation)
}
```

(переставить low-risk проверку наверх, как и задумано таблицей рисков).

---

#### 10. `ShowTab` (показ seed-фразы) — мёртвый код, но с риском копирования в буфер обмена

`RecoveryScreen.kt` (комментарий прямо в коде):

kotlin

```kotlin
// ponytail: SHOW tab removed — seed phrase contradicts PRD §3.2/ADR 001. Recovery via SSS only.
listOf("Импорт", "Бекап").plus(...)
```

Это правильно по PRD (ADR 002 — "Recovery через SSS, не seed"). Но функция `ShowTab` со всей логикой реального отображения и **копирования мнемоники в системный clipboard** (`onCopy` → `context.copyToClipboard`) осталась в файле как недостижимый мёртвый код. Любой будущий PR может случайно вернуть вкладку в табы — а реализация копирования seed-фразы в буфер обмена (общий ресурс ОС, читаемый другими приложениями на старых Android) уже готова и просто ждёт.

**Как исправить:** удалить `ShowTab` целиком как часть PR, либо явно пометить `@Deprecated`/перенести в архив, чтобы случайное подключение было невозможно без явного ревью.

---

#### 11. Аватар-инициалы у контактов: `colorPair[0]` используется только для кружка, `colorPair[1]` не используется нигде

`ContactsScreen.kt`:

kotlin

```kotlin
val colorPair = avatarColors[contact.nickname.length % avatarColors.size]
...
.drawBehind { drawCircle(colorPair[0]) }
```

Палитра задумана как градиент (как в `SettingsScreen`/`WalletsTokensScreen`, где используется `Brush.linearGradient(listOf(...))`), но тут взят только первый цвет — мелкая визуальная недоработка, не баг, но расхождение с дизайн-системой остальных экранов.

---

#### 12. `HomeScreen.kt`: токены `MDAO`/`USDT` показываются всегда, даже с нулевым балансом

kotlin

```kotlin
private fun walletToTokens(wallet: WalletState): List<TokenInfo> = buildList {
    add(TokenInfo("MDAO", wallet.balanceMdao, MDAOPurple))
    add(TokenInfo("USDT", wallet.balanceUsdt, SuccessGreen))
    if (wallet.balanceEth > java.math.BigDecimal.ZERO)
        add(TokenInfo("Sepolia ETH", wallet.balanceEth, ...))
}
```

Несогласованная логика: ETH показывается только при ненулевом балансе, а MDAO/USDT — всегда, независимо от баланса (даже 0). Если `MDAO_CONTRACT` не задеплоен (см. пункт 3), пользователь увидит карточку токена MDAO с перманентным нулём/ошибкой — UX-путаница на тестнете, когда контракты ещё не до конца раскатаны.

---

### 🔵 Среднее / гигиена кода

- **`SettingsScreen.kt`**, странный паттерн:

kotlin

```kotlin
  .clip(java.lang.Float.valueOf(MDARadius.xxl.value).let { androidx.compose.foundation.shape.RoundedCornerShape(it.dp) })
```

вместо простого `RoundedCornerShape(MDARadius.xxl)`. Не баг, но избыточный boxing/unboxing и нечитаемо — рефактор тривиален.

- **`OnboardingTutorialViewModel`** объявлен как обычный `ViewModel()` без `@HiltViewModel`, но содержит только статичный enum — нормально, просто не Hilt-инжектится нигде по делу (используется напрямую через enum, ViewModel фактически не нужен).
- **`SocialAuthManager.WEB_CLIENT_ID`** захардкожен в исходниках:

kotlin

```kotlin
  const val WEB_CLIENT_ID = "925151210559-01ge4gml47c0u6pnpu88ebf6hbfntmu2.apps.googleusercontent.com"
```

Google OAuth Client ID — по природе публичный (это нормально для Android OAuth), но стоит вынести в `BuildConfig` per-flavor, чтобы dev/staging/prod использовали разные OAuth consent screens — иначе тестовые логины на dev-сборке попадут в продакшен Google Cloud проект.

- **`EthereumProviderInjector.confirmAction`** использует `CountDownLatch.await()` без таймаута — если пользователь свернёт приложение во время диалога подтверждения транзакции (или диалог не покажется по какой-то Android-причине), JS-вызов из WebView повиснет навсегда (не ANR главного потока, поскольку `send()` дергается с JS-bridge-потока, но зависший Promise на стороне dApp — плохой UX и потенциальная утечка потока).
- **`MainActivity`** ставит `FLAG_SECURE` глобально в `onCreate`, при этом `SecureScreen()` composable дополнительно добавляет/убирает тот же флаг per-screen через `DisposableEffect`. Поскольку глобальный флаг уже стоит постоянно, локальный toggle в `SecureScreen` по факту no-op (флаг и так всегда включён) — мёртвая абстракция, вводящая в заблуждение (выглядит так, будто скриншоты разрешены не на всех экранах, а на деле — нигде).

---

### Сводка: что блокирует тестнет прямо сейчас

|#|Блокер|Файл|
|---|---|---|
|1|Placeholder cert-pins → все HTTPS-запросы упадут|`NetworkModule.kt`, `app/build.gradle.kts`|
|2|RPC (Sepolia) не совпадает с `CHAIN_ID=56` (BSC)|`NetworkConfig.kt`, `app/build.gradle.kts`|
|3|`MDAO_CONTRACT`/`SOCIAL_RECOVERY_MODULE` = нулевой адрес, проверка `isConfigured()` нигде не используется|`NetworkConfig.kt`|
|4|`eth_sendTransaction` в WebView-мосте всегда возвращает фейковый хэш, реальный UserOp не отправляется|`EthereumProviderInjector.kt`|
|9|`DeviceIntegrityManager` блокирует low-risk операции (включая запуск на эмуляторе) — мешает QA/CI на тестнете|`DeviceIntegrityManager.kt`|

Что нужно прислать, чтобы закрыть Этапы 2 и 3 из твоего исходного плана: содержимое `contracts/src/SocialRecoveryModule.sol` и `backend/src/main/kotlin/.../PaymasterService.kt` — без них пункты про THRESHOLD/MAX_GUARDIANS/EIP-712/KMS-signing я подтвердить не могу, только то, что описано в TDD как design intent.