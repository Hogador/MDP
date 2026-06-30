# Архитектура Guardian Recovery

## Философия

1. **Zero proprietary infrastructure** — нет наших серверов
2. **Guardian = контакт из Google/Apple** — не нужно знать про Ethereum
3. **Push-уведомления нативно** — guardian видит операцию как обычное уведомление
4. **SSS 3-of-4** — ни один фактор не даёт доступ
5. **Защита от силового принуждения** — timelock + veto

---

## Факторы (SSS 3-of-4)

```
                    K = seed phrase (BIP-39)
                    SSS.split(K, 3, 4) = [s1, s2, s3, s4]

┌──────────────┬──────────────────────┬──────────────────────────────┐
│ Фактор       │ Технология           │ Где хранится                 │
├──────────────┼──────────────────────┼──────────────────────────────┤
│ s1: Телефон  │ Android Keystore     │ EncryptedSharedPreferences   │
│              │ AES-256 + биометрия  │ (на самом устройстве)        │
├──────────────┼──────────────────────┼──────────────────────────────┤
│ s2: Passkey  │ FIDO2 PRF extension  │ Google Password Manager      │
│              │ HMAC(passkey, input) │ iCloud Keychain              │
├──────────────┼──────────────────────┼──────────────────────────────┤
│ s3: Guardian │ FIDO2 в приложении   │ EncryptedSharedPreferences   │
│      A       │ guardian'а           │ (на устройстве guardian'а)   │
├──────────────┼──────────────────────┼──────────────────────────────┤
│ s4: Guardian │ FIDO2 в приложении   │ EncryptedSharedPreferences   │
│      B       │ guardian'а           │ (на устройстве guardian'а)   │
└──────────────┴──────────────────────┴──────────────────────────────┘
```

---

## On-chain: SocialRecoveryModule

Единственный контракт. Хранит только:

```solidity
struct Guardian {
    bytes32 identityHash;  // keccak256("google:" || googleSub)
    bytes32 passkeyPublicKeyHash;  // keccak256(P-256 public key)
    uint256 addedAt;
}

struct RecoveryRequest {
    address newPasskeyPublicKey;  // куда менять
    address[2] approvedBy;        // кто уже подписал
    uint256 startedAt;            // когда инициирован
    bool vetoed;
}

// State
mapping(address wallet => Guardian[4]) public guardians;
mapping(address wallet => RecoveryRequest) public pendingRecovery;
uint256 constant TIMELOCK = 48 hours;
```

**Никаких зашифрованных данных на chain**. Только хеши и публичные ключи.

---

## Поток: добавление guardian'a

```
Владелец кошелька:
  1. Открывает приложение → Настройки → Добавить guardian
  2. Выбирает контакт из Google/Apple контактов
  3. Приложение генерирует ссылку-приглашение:
     mdaopay://guardian?wallet=0x...&nonce=0x...

Контакт (будущий guardian):
  4. Получает ссылку (через SMS/WhatsApp/Telegram)
  5. Открывает → если нет приложения → Google Play / App Store
  6. Приложение: "Пользователь X просит вас стать guardian'ом"
  7. Guardian нажимает "Принять"
  8. Приложение guardian'a:
     a. Sign-In with Google/Apple → получаем googleSub
     b. Создаёт passkey (FIDO2) для подписи recovery-запросов
     c. Публичный ключ passkey → hash → отправляется в SocialRecoveryModule
     d. Отправляет tx: addGuardian(wallet, identityHash, passkeyPubKeyHash)

Транзакцию платит guardian (или owner через relay). 
Газ = ~50k gas/guardian на L2.
```

---

## Поток: восстановление

```
1. Владелец на новом устройстве:
   a. Открывает приложение → "У меня есть кошелёк"
   b. Приложение проверяет: passkey синхронизирован? → passkey auth
   c. Получает s2 (через PRF extension от passkey)

2. Приложение инициирует recovery:
   a. Отправляет tx: initiateRecovery(newPasskeyPubKey)
   b. Начинается TIMELOCK (48h)
   c. Контракт эмитит событие:
      RecoveryInitiated(wallet, guardianAddresses...)

3. Push-уведомление guardian'ам:
   a. Весь узел 3 требует relay (FCM/APNS).
      Relay — единственный централизованный компонент.
   b. Guardian видит:
      ┌─────────────────────────────────┐
      │  🔐 Запрос на восстановление    │
      │                                  │
      │  Алексей (alex@email.com)        │
      │  хочет восстановить кошелёк      │
      │                                  │
      │  ┌──────────┐  ┌──────────┐     │
      │  │ 👍 Да     │  │ 🚫 Нет   │     │
      │  └──────────┘  └──────────┘     │
      └─────────────────────────────────┘
   c. Guardian нажимает "Да" → биометрия
   d. Приложение guardian'a подписывает approveRecovery(wallet)
      своей passkey (P-256 подпись)
   e. Подпись отправляется в контракт (через relay или напрямую)

4. После 2 из 2 guardian'ов + 48h:
   executeRecovery(wallet) → новый passkey установлен

5. Владелец логинится с новым passkey → доступ к кошельку

6. Share 1 (s1) теряется со старым устройством — OK.
   SSS 3-of-4: s2 (passkey) + s3 (guardian A) + s4 (guardian B) = seed восстановлен.
```

---

## Защита от силового принуждения

```
Принуждение: атакующий заставляет жертву позвонить guardian'ам

Guardian A: "Привет, подтверди recovery" → подписывает (думает реально)
Guardian B: "Привет, подтверди recovery" → подписывает (думает реально)

✅ 2 guardian'а подписали → таймер 48h пошёл

НО:
- Guardian A получает уведомление: "Recovery инициирован для 
  кошелька Алексея. Если это не вы — нажмите Cancel"
- Guardian B получает то же самое

🔥 Если ХОТЯ БЫ ОДИН guardian нажимает Cancel → recovery отменён
🔥 Даже если атакующий держит жертву 48 часов:
   Guardian может отменить в 1 клик из любого места

Дополнительно: Canary Guardian (3-й скрытый)
- Владелец может добавить 3-го guardian'a "на всякий случай"
- Этот guardian НЕ указан в явном списке (или указан как fallback)
- Recovery требует 3-of-5: s1 + s2 + s3 + s4 + s5(canary)
  или timelock 72h если canary молчит
- Если атакующий знает только про 2 guardian'ов — canary veto
```

---

## Relay (единственный централизованный компонент)

**Что делает:**
1. Принимает `RecoveryInitiated` events от контракта
2. Отправляет FCM/APNS уведомления guardian'ам
3. Принимает подписанные approve/veto от guardian'ов
4. Отправляет tx в контракт

**Почему relay trustless:**
- Relay НЕ может подделать approve — нужна P-256 подпись guardian'a
- Relay НЕ может украсть средства — нет ключей
- Relay НЕ может заблокировать recovery — guardian может отправить tx напрямую (через Etherscan, любой RPC)
- Relay может только задержать (не показывать уведомления) → но guardian сам видит on-chain event

**Как сделать relay децентрализованным (опционально):**
- Использовать EPNS (Push Protocol) для уведомлений
- Использовать The Graph для индексации событий
- Использовать AnyRelay (e.g. Gelato, OpenRelay) для отправки tx

---

## Хранение зашифрованных шардов

Никакого центрального хранилища. Каждый фактор хранит свой шард сам:

| Фактор | Хранилище | Механизм |
|--------|-----------|----------|
| s1 (телефон) | EncryptedSharedPreferences | AES-256, ключ в Keystore |
| s2 (passkey) | Google Password / iCloud | PRF от passkey → AES ключ |
| s3..s4 (guardian) | EncryptedSharedPreferences guardian'a | AES-256, ключ в Keystore guardian'a |
| s5 (canary) | EncryptedSharedPreferences canary guardian'a | То же |

**Шарды НЕ передаются по сети.** SSS вычисляется на устройстве восстанавливающего. Guardian'ы не отдают свой шард — они только подписывают approve.

---

## Компоненты для Android

### SocialRecoveryModule.sol
```solidity
// Minimal interface
function addGuardian(bytes32 identityHash, bytes32 passkeyP256Hash) external;
function initiateRecovery(bytes memory newPasskeyPublicKey) external;
function approveRecovery(address wallet, bytes calldata p256Signature) external;
function vetoRecovery(address wallet) external;
function executeRecovery(address wallet) external;
```

### GuardianManager.kt (новый модуль)
```kotlin
class GuardianManager @Inject constructor(
    private val passkeyManager: PasskeyManager
) {
    suspend fun inviteGuardian(contact: Contact): String
    suspend fun acceptGuardianInvite(invite: GuardianInvite): Result<Unit>
    suspend fun getPendingRecoveries(): List<RecoveryRequest>
    suspend fun approveRecovery(wallet: String): Result<Unit>
    suspend fun vetoRecovery(wallet: String): Result<Unit>
}
```

### RecoveryRelayClient.kt (общение с relay)
```kotlin
class RecoveryRelayClient @Inject constructor() {
    // Relay endpoints (trustless — не может подделать подпись)
    suspend fun notifyGuardians(wallet: String, guardians: List<String>)
    suspend fun submitApproval(wallet: String, signature: ByteArray)
    suspend fun submitVeto(wallet: String)
}
```

### Модификация существующих файлов

| Файл | Изменение |
|------|-----------|
| `RecoveryShareManager.kt` | Вернуть `generateShares()` / `recover()` (SSS возвращается) |
| `BackupStorage.kt` | s1: Keystore + биометрия |
| `PasskeyManager.kt` | Добавить PRF extension для s2 |
| `RecoveryViewModel.kt` | Guardian recovery flow |
| `RecoveryScreen.kt` | UI guardian'ов, статус recovery |
| `NetworkConfig.kt` | Адрес SocialRecoveryModule |

---

## Почему это децентрализовано

| Компонент | Централизован? | Можно заменить на |
|-----------|:--------------:|:-----------------:|
| FIDO2 passkey | ❌ Google/Apple | Любой WebAuthn провайдер |
| Google Sign-In | ❌ Google | Apple Sign-In / любой OIDC |
| Push-уведомления | ⚠️ Relay (наш) | EPNS / The Graph → любой |
| Ethereum RPC | ❌ Любой узел | Любой RPC |
| IPFS storage | ❌ IPFS | Filecoin / Arweave |
| SocialRecoveryModule | ❌ ERC-4337 | Любой L2 |

Единственный replaceable компонент — relay. Он:
1. Не хранит секреты
2. Не может подделать approve
3. Может быть заменён на децентрализованный (Push Protocol, The Graph) без изменения контрактов

---

## Что дальше

1. Вернуть SSS в `RecoveryShareManager`
2. Добавить PRF extension в `PasskeyManager` (s2)
3. Написать `SocialRecoveryModule.sol`
4. Написать `GuardianManager.kt`
5. Поднять relay (serverless — Cloudflare Worker / Firebase Functions)
6. UI guardian'ов (выбор контактов, статус, approve/veto)

Начать с возврата SSS + PRF + SocialRecoveryModule?
