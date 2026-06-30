# Passkey / Social Recovery — Дорожная карта

## Архитектура (итоговая)

```
fact-key:
  SSS 3-of-4

  s1 📱 Телефон (Keystore + биометрия)
  s2 ☁️ Passkey PRF (Google/Apple FIDO2 sync)
  s3 👤 Guardian A (FIDO2 passkey guardian'a)
  s4 👤 Guardian B (FIDO2 passkey guardian'a)

  Любые 3 из 4 = восстановление seed phrase

layers:
  ┌─────────────────────────────────────────────┐
  │  ERC-4337 Account Abstraction                │
  │  └─ SocialRecoveryModule (Solidity)          │
  │     └─ P-256 verify (EIP-7951 precompile)    │
  │     └─ Timelock 48h + Veto                   │
  ├─────────────────────────────────────────────┤
  │  Android App                                 │
  │  └─ SSS (возвращён, GF256 + Shamir)          │
  │  └─ Android Credential Manager (passkey)      │
  │  └─ GuardianManager (инвайты, approve/veto)   │
  │  └─ BackupStorage (Keystore AES-256)         │
  ├─────────────────────────────────────────────┤
  │  Trustless Relay (FW/CF Worker)              │
  │  └─ push-уведомления (FCM/APNS)              │
  │  └─ сбор подписей в UserOp                  │
  │  └─ не может украсть / подделать             │
  └─────────────────────────────────────────────┘
```

## Принципы

| Принцип | Решение |
|---------|---------|
| Zero proprietary infra | Relay — только push (replaceable, trustless) |
| Self-custody | Seed шифруется, SSS 3-of-4, никто не может расшифровать 1 фактор |
| Анти-social-engineering | Timelock 48h + любой guardian veto |
| UX для guardian'ов | Google/Apple Sign-In, Face ID, никаких ETH ключей |
| Gas | Paymaster в MDAO/USDT — платит владелец |
| Seed всегда доступен | Экспорт seed phrase для MetaMask и других кошельков |
| Без guardian'ов | SSS 2-of-3: телефон + passkey + cold device / seed phrase |

---

## Дорожная карта

### S1 (2-3 недели) — Вернуть SSS + Clean up

**Что делаем:**
- Вернуть `ShamirSecretSharing.split()` / `join()` в `RecoveryShareManager`
- Удалить `BackupStorage` (он дублировал s1, теперь s1 = `RecoveryShareManager.share1`)
- Сделать s1: Keystore + биометрия (уже есть, просто зафиксировать)
- Написать тесты для SSS 3-of-4
- Почистить: `BackupStorage.kt` → удалить, `SettingsViewModel` → вернуть `RecoveryShareManager`
- Удалить `PasskeyManager.createRecoveryPasskey()` из UI (passkey пока не primary)

**Файлы:**
| Файл | Действие |
|------|----------|
| `ShamirSecretSharing.kt` | Вернуть, проверить 3-of-4 |
| `RecoveryShareManager.kt` | Вернуть `generateShares(3,4)`, `recover()` — compatible |
| `BackupStorage.kt` | **Удалить** |
| `PasskeyManager.kt` | **Удалить** (создадим новый в S2) |
| `RecoveryViewModel.kt` | Вернуть SSS flow, убрать passkey |
| `RecoveryScreen.kt` | Вернуть старый UI с 3 shares |
| `SettingsViewModel.kt` | Вернуть `RecoveryShareManager` |
| `build.gradle.kts` | Убрать `credentials` dependency (пока) |
| `libs.versions.toml` | Убрать `credentials` (пока) |

**Результат:** 👆 Всё работает как до Phase 1, но SSS 3-of-4 вместо 2-of-3.

---

### S2 (3-4 недели) — Passkey как s2 (PRF extension)

**Что делаем:**
- **Новый** `PasskeyManager.kt` с PRF extension
- `CreatePublicKeyCredentialRequest` с `"extensions": {"prf": {"eval": ...}}`
- PRF output → HKDF → AES ключ для s2
- `RecoveryShareManager` v2: s1 = Keystore, s2 = passkey PRF
- `CredentialManager` dependency возвращается

**Файлы:**
| Файл | Действие |
|------|----------|
| `PasskeyManager.kt` | Создать заново (PRF API) |
| `RecoveryShareManager.kt` | Расширить: `saveShare2FromPasskey()`, `loadShare2FromPasskey()` |
| `RecoveryViewModel.kt` | Passkey + SSS (auto: passkey PRF → s2 → SSS join) |
| `RecoveryScreen.kt` | Добавить "Настроить Passkey recovery" |
| `build.gradle.kts` | Вернуть `credentials` |
| `libs.versions.toml` | Вернуть `credentials` |

**Результат:** 👆 SSS 3-of-4. s1 (Keystore) + s2 (Passkey PRF). s3/s4 пока пусты — recovery работает через 2 фактора.

```
SSS 3-of-4, но пока есть только 2 фактора:
  Если есть s1 + s2 → recovery (3-of-4 satisfied by s1 + s2 + backfill)
```

---

### S3 (3-4 недели) — Smart contract: SocialRecoveryModule

**Что делаем:**
- Написать `SocialRecoveryModule.sol`
  - P-256 verification (EIP-7951 precompile `0x100`)
  - `addGuardian(bytes32 identityHash, bytes32 passkeyPubKeyHash)`
  - `initiateRecovery(bytes memory newPasskeyPubKey)`
  - `approveRecovery(address wallet, bytes calldata p256Signature)`
  - `vetoRecovery(address wallet)`
  - `executeRecovery(address wallet)` — после 48h
- Написать тесты (Foundry / Hardhat)
- Развернуть на Sepolia
- Проверить P-256 precompile на Sepolia (если нет — MockP256)

**Файлы:**
| Файл | Действие |
|------|----------|
| `contracts/SocialRecoveryModule.sol` | Создать |
| `contracts/mocks/MockP256.sol` | Создать (если precompile нет) |
| `contracts/test/RecoveryTest.t.sol` | Создать |
| `NetworkConfig.kt` | Добавить `SOCIAL_RECOVERY_MODULE` |
| `hardhat.config.ts` / `foundry.toml` | Настроить |

**Результат:** 👆 Контракт на Sepolia. Можно звать `addGuardian()`.

---

### S4 (4-6 недель) — Guardian on Android

**Что делаем:**
- `GuardianManager.kt` — инвайты, approve/veto
- `GuardianService.kt` — фоновая служба для push-уведомлений
- `RelayClient.kt` — общение с relay (Firebase Cloud Function / CF Worker)
- Push-уведомления: FCM → guardian открывает → видит запрос
- Guardian approve: биометрия → P-256 assertion → relay → контракт
- Veto: биометрия → relay → контракт
- UI:
  - Добавить guardian'а (выбор из контактов)
  - Статус guardian'ов (кто принял, кто нет)
  - Recovery request карточка
  - Approve / Veto кнопки
- SSS: s3 = Guardian A decrypt, s4 = Guardian B decrypt

**Файлы:**
| Файл | Действие |
|------|----------|
| `GuardianManager.kt` | Создать |
| `GuardianService.kt` | Создать (FirebaseMessagingService) |
| `RelayClient.kt` | Создать (HTTP client к CF Worker) |
| `RecoveryViewModel.kt` | Расширить guardian flow |
| `RecoveryScreen.kt` | Guardian UI |
| `GuardianInviteActivity.kt` | Deeplink обработка инвайта |

**Результат:** 👆 Полный guardian recovery. SSS 3-of-4, push-уведомления, approve/veto.

---

### S5 (1-2 недели) — Paymaster + сборка UserOp

**Что делаем:**
- UserOperation для recovery: `executeRecovery()` через bundler
- Paymaster: оплата в MDAO/USDT
- Сборка подписей в одну UserOp:
  ```
  UserOp.signature = passkeySig || guardianASig || guardianBSig
  ```
- Если нет guardian'ов (затворник):
  - SSS 2-of-3: телефон + passkey → seed → обычная UserOp

**Результат:** 👆 Recovery как обычная транзакция, газ в MDAO/USDT.

---

### S6 (2 недели) — Cold device + Hermit mode

**Что делаем:**
- При создании кошелька: выбор сценария
  - **Default:** телефон + passkey (и добавить guardian'ов позже)
  - **Без guardian'ов:** рекомендация cold device или seed phrase
- Cold device flow: PIN-код вместо биометрии (сеть не нужна)
- Hermit mode в UI: скрыть guardian раздел, показать "Seed phrase = ваша ответственность"
- Предупреждения при отсутствии guardian'ов:
  - "Если потеряете телефон и passkey — кошелёк не восстановить"
  - "Рекомендуем: cold device / seed phrase в банковской ячейке"

**Результат:** 👉 Полная поддержка всех сценариев.

---

### S7 (1 неделя) — Relay deployment

**Что делаем:**
- Cloudflare Worker (или Firebase Cloud Function)
  - Webhook от SocialRecoveryModule (RecoveryInitiated event)
  - Push-уведомления guardian'ам
  - Приём approve/veto, отправка tx в контракт
- Worker не хранит секреты, не может подделать подпись
- Worker может быть заменён на EPNS / Push Protocol

**Результат:** ✅ Продакшен.

---

### S8 (2-3 недели) — Audit + Testnet → Mainnet

**Что делаем:**
- Аудит контрактов (OpenZeppelin / Code4rena)
- E2E тесты на Sepolia
- Развёртывание на mainnet L2 (Arbitrum / Optimism)
- Документация для пользователей

**Результат:** ✅ Релиз.

---

## Итого

```
S1 ── Вернуть SSS, удалить временный PasskeyManager  ── 2-3 нед
 │
S2 ── Passkey PRF (s2)                               ── 3-4 нед
 │
S3 ── SocialRecoveryModule.sol + тесты                ── 3-4 нед
 │
S4 ── Guardian на Android (push, approve, veto)        ── 4-6 нед
 │
S5 ── Paymaster + UserOp сборка                       ── 1-2 нед
 │
S6 ── Cold device + Hermit mode                       ── 2 нед
 │
S7 ── Relay deployment                                ── 1 нед
 │
S8 ── Audit + Mainnet                                 ── 2-3 нед
 │
   ≈ 18-26 недель total
```

**Начать с S1: вернуть SSS 3-of-4, убрать временный код?**
