# MDAOPay — Product Requirements Document

> **Статус:** Утверждено
> **Дата:** Июнь 2026
> **Формула:** MDAOPay = Unified Access Layer for Web3

---

## Document Authority Matrix

| Область | Источник истины |
|---------|-----------------|
| Product Vision & Strategy | **PRD (этот документ)** |
| UX Principles | **PRD §3** |
| MVP Feature Set | **PRD §8** |
| Non-Goals | **PRD §9** |
| Smart Contract interfaces | TDD |
| API Contracts | TDD |
| Database Schema | TDD |
| Design System | Design Bible |
| Test Scenarios | Test Scenarios |
| Roadmap | **PRD §21 + Code Roadmap** |

---

# Part 1: Vision & Strategy

---

## 1. Проблема

Криптовалюты обещали свободу. Доставили сложность. Банковский перевод — 5 секунд по имени. Крипто-транзакция — 42-символьный адрес, выбор сети, газ, необратимость.

Индустрия строила инструменты для инженеров, а не для людей.

## 2. Видение

### MDAOPay = Unified Access Layer for Web3

Не кошелёк. Не платёжная система. Универсальный слой доступа.

Платежи, recovery и логин — следствия. Ядро — идентичность.

### Трёхслойная модель

```
Layer 1: Identity (@username) — точка входа
Layer 2: Trust (Trusted Contacts) — восстановление
Layer 3: Access (Login with MDAOPay) — единый вход

Application 1: Payments (gasless) ← MVP
Application 2: Ecosystem Services ← Phase 1
Application 3: Merchant ← Phase 4
```

Принцип: если убрать payments — identity + trust + access остаётся. Если убрать identity — продукт умирает.

### North Star

Не MAU. Не TVL. **Количество экосистем, использующих MDAOPay identity.**

### Apple/Google = auxiliary

Google/Apple = device identity. MDAOPay = financial identity. Recovery работает без Google.

---

## 3. Принципы

### 3.1 Ownership First
Средства принадлежат пользователю. Система не хранит, не замораживает, не контролирует.

### 3.2 Simplicity First
Пользователь не видит адресов, сетей, газа, подписей. Видит: "Отправлено 25 USDT Антону".

### 3.3 Human First
Ошибка — норма. Система минимизирует последствия.

### 3.4 Trust Through Design
Поддерживаемый суверенитет. Пользователь контролирует, система помогает.

### 3.5 Privacy Model
Trust graph = приватный. Trusted Contact видит только @username и аватар. Не видит баланс, историю, других контактов.

### 3.6 Терминология

| В UI | В коде |
|------|--------|
| Доверенный контакт | guardian |
| Восстановление доступа | recovery |
| Подтвердить, что это [имя] | approveRecovery |
| Отклонить запрос | vetoRecovery |
| (Face ID) | passkey |
| Кошелёк | SmartAccount |
| (не показывать) | Paymaster, ERC-4337, seed phrase |

---

## 4. Экосистема

MarsDAO — первый партнёр, не владелец. MDAOPay переживает исчезновение MarsDAO.

Phase 3: UIP (Universal Identity Protocol) — открытый стандарт без привязки к MarsDAO.

---

## 5. Аудитория

Возраст 20-55. Банковские приложения. Не хочет изучать крипту.

Боли: страх потери, непонимание, адреса, цена ошибки, seed-фразы.

---

## 6. Identity Layer

@username = точка входа. 3-20 символов, `[a-zA-Z0-9_-]`.

NicknameRegistry (on-chain) + backend resolution.

---

## 7. Восстановление доступа

### Принцип: подтверждение личности

Trusted Contacts не "дают доступ". Они подтверждают: "Да, это Антон".

### Модель

2-of-3 Trusted Contacts → 48h timelock → veto → доступ восстановлен.

### Что видит пользователь
1. Ввод @username на новом устройстве
2. "Мы отправили запрос вашим доверенным контактам"
3. Статус: ✅ Жена, ⏳ Друг, ⏳ Коллега
4. После 2 из 3: "Доступ восстановлен через 48 часов"
5. Кнопка "Отменить" (48h)

### Что видит Trusted Contact
1. Push: "Антон пытается восстановить доступ"
2. Аватар + @username
3. "Вы подтверждаете, что это Антон?"
4. Face ID → готово

### Защита
- 48h timelock
- Veto = бесплатный (Paymaster покрывает gas)
- Push на все устройства
- Anti-spam: 0.01 MDAO deposit (возвращается при success, сжигается при veto)
- Cloud backup (опционально): зашифрованный мастер-ключ в iCloud/Google Drive, PBKDF2 600k

### Что НЕ делает
- Не требует seed phrase
- Не требует Google/Apple
- Не требует кодов (только Face ID)
- Не требует оплаты газа

---

# Part 2: MVP Feature Set

---

## 8. MVP Features

### 8.1 Username Payments
Перевод по @username. Gasless (Paymaster, post-paid). Smart Account (ERC-4337 v0.6).

### 8.2 Trusted Contacts Recovery
P-256 passkey. 2-of-3. 48h timelock. SSS под капотом. Без seed phrase в UI.

### 8.3 Identity Connect (Session Keys) ⭐ MVP

Замена WalletConnect. Подключение человека (не адреса) к сервису.

**Capability Mapping** (не Selector Mapping):
- 5-7 намерений: ProfileRead, PaymentsSend, NFTTransfer, ApproveTokens, AdminActions
- Неизвестное намерение = WARNING
- Человекочитаемые разрешения (✓/✗)
- Лимиты: max per tx, daily limit, allowed tokens
- Отзыв в любой момент

**UX:**
1. dApp: "Войти через MDAOPay"
2. Список разрешений (✓/✗)
3. Face ID → сессия создана
4. dApp использует Session Key без повторных подтверждений

**Под капотом:** SessionKeyModule.sol (scoped permissions, limits, expiry, revocable)

### 8.4 Контакты
Список часто используемых получателей. Перевод в одно касание.

### 8.5 История
Фильтры, группировка по датам, статусы.

### 8.6 On-Ramp
Мамкин обменник (Telegram-бот). Phase 2: Transak/Onramper.

### 8.7 DEX
1inch Aggregator. Gasless swaps.

### 8.8 Сервисные карточки
Arena, DEX, Flopi, VPN — вход через Identity Connect.

---

## 9. Non-Goals

| ❌ | Функция | Когда |
|---|---------|-------|
| ❌ | Кросс-чейн | Phase 3 |
| ❌ | iOS | Phase 2 |
| ❌ | Мультиаккаунтность | После PMF |
| ❌ | Reputation scores | Никогда |
| ❌ | Социальные связи | Никогда |
| ❌ | Лайки, рейтинги | Никогда |
| ❌ | Кредитный слой | Phase 4 |
| ❌ | Merchant NFC | Phase 4 |
| ❌ | Mesh Relay (оба офлайн) | Phase 5 |
| ❌ | DAO-управление | Через экосистему |

---

## 10. Success Criteria

| Метрика | Цель (3 месяца) |
|---------|-----------------|
| Установок | 5,000 |
| Кошельков | 3,000 |
| WAU | 1,000 |
| Переводов/день | 100+ |
| Экосистем подключено | 1+ (Arena) |

North Star: количество экосистем.

---

## 10A. Growth Strategy

| Phase | Канал |
|-------|-------|
| Phase 0 | MarsDAO community, referral (50 MDAO) |
| Phase 1 | Login with MDAOPay во всех продуктах MarsDAO |
| Phase 2 | Partnerships, DappRadar, UIP draft |
| Phase 3 | Developer grants ($10k за интеграцию UIP) |
| Phase 4 | Merchant partnerships, mass media |

---

# Part 3: Architecture

---

## 14. Архитектура

```
Mobile App (Android, Kotlin/Compose)
    ↕
Backend (Kotlin/Ktor, PostgreSQL, Redis)
    ↕
Smart Contracts (ERC-4337 v0.6, BSC)
    ├── EntryPoint: 0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789 (v0.6!)
    ├── SimpleAccount
    ├── MDAOPaymaster (post-paid)
    ├── SocialRecoveryModule (P-256, 2-of-3, 48h)
    ├── NicknameRegistry (EIP-712)
    └── SessionKeyModule (Capability Mapping)
```

**EntryPoint: v0.6** (НЕ v0.7)
**Network: BSC** (Chain ID: 56)

### Архитектурный фундамент (закладывается, не реализуется)

- Smart Account = модульный (slots для ecosystem, subscription)
- NicknameRegistry = extensible (cross-chain, permissions)
- Paymaster = extensible whitelist (multi-sponsor)
- Backend API = /v1/ prefix
- Mobile app = :core separable для SDK
- Relay = open protocol

---

## 15. Paymaster

**Модель:** Post-paid (НЕ pre-paid). transferFrom в postOp. Нет sponsorshipBalance. Нет custody risk.

| Сценарий | Кто платит gas | Кто видит |
|----------|---------------|----------|
| P2P | Отправитель (Paymaster) | Никто |
| Ecosystem action | Экосистема | Никто |
| Merchant | Merchant | Merchant (в цене) |
| Onboarding (first 3 tx) | MDAOPay treasury | Никто |

---

## 16. Recovery Architecture

### SSS: 2-of-3 (единственный режим MVP)

| Share | Где |
|-------|-----|
| s1 | Телефон (Keystore) |
| s2 | Passkey PRF (биометрия) |
| s3 | Trusted Contact |

### SocialRecoveryModule
- P-256 (WebAuthn), НЕ ECDSA
- HKDF identityHash (domain separation)
- 48h timelock
- 2-of-3 threshold
- Veto: бесплатный (Paymaster)
- Anti-spam: 0.01 MDAO deposit

### Cloud Backup (опционально)
AES-256-GCM + PBKDF2 600k. Backend НЕ участвует. Не custody.

---

## 17. Identity Connect (Session Keys)

### SessionKeyModule

```solidity
contract SessionKeyModule {
    enum Capability { None, ProfileRead, AddressRead, BalanceRead, 
                      PaymentsSend, NFTTransfer, ApproveTokens, AdminActions }
    
    struct Session {
        address dApp;
        Capability[] capabilities;
        uint256 maxAmountPerTx;
        uint256 dailyLimit;
        address[] allowedTokens;
        uint256 expiresAt;
        bool active;
    }
    
    function createSession(...) external;
    function executeWithSession(...) external;
    function revokeSession(address sessionKey) external;
}
```

### Capability Mapping Engine
- transfer(address,uint256) → PaymentsSend
- approve(address,uint256) → ApproveTokens (WARNING)
- safeTransferFrom → NFTTransfer
- Неизвестный selector → WARNING

### Permission Mapper (Kotlin)
```kotlin
object PermissionMapper {
    fun mapSelector(selector: String): Capability
    fun buildPermissionList(...): List<Permission>
}
```

---

## 18. Backend

- /v1/ prefix
- PostgreSQL (users, nicknames, transactions, guardians)
- Redis (rate limit, replay cache, DEX prices)
- Backend НЕ хранит SSS shares (только hashes)
- Backend НЕ хранит приватные ключи
- RPC multi-provider (3, failover, 30s cooldown)

---

# Part 4: Roadmap

---

## 21. Roadmap

### Phase 0 — MVP (Q3 2026, 15 недель)

| Недели | Задача |
|--------|--------|
| 1-3 | Smart Contracts (SimpleAccount, Paymaster, Recovery, Nickname) |
| 4-6 | Mobile App (Onboarding, Wallet, Home, Send) |
| 7-9 | Backend (Ktor, PostgreSQL, Redis, Relay, RPC) |
| 10-12 | Identity Connect (SessionKeyModule, Capability Mapping, SDK, Modal) |
| 13-15 | Polish, Audit, BSC deploy, Beta |

**Метрики:** 5,000 установок, 1,000 WAU

### Phase 1 — Ecosystem (Q1 2027)
- Multi-Sponsor Paymaster
- Login with MDAOPay SDK
- Push notifications для recovery
- Cloud backup
- Offline QR (состояния B, C)
- 5+ интегрированных dApps

**Метрики:** 50,000 пользователей

### Phase 2 — Open SDK (Q3 2027)
- TypeScript SDK
- UIP draft
- Multi-chain
- iOS

**Метрики:** 200,000 пользователей, 50+ dApps

### Phase 3 — Universal Identity (Q2 2028)
- UIP = открытый стандарт
- Recovery as a Service
- 500+ dApps

**Метрики:** 1M пользователей

### Phase 4 — Real World (Q4 2028)
- Merchant SDK
- NFC/QR payments
- Fiat on-ramp/off-ramp

**Метрики:** 5M пользователей, 10k merchants

---

# Appendices

---

## ADR

| # | Решение |
|---|---------|
| 001 | Пользователь не видит адреса |
| 002 | Recovery через SSS (не seed) |
| 003 | MDAO не требуется |
| 004 | Централизованный nickname registry |
| 005 | BSC для MVP |
| 006 | ERC-4337 v0.6 |
| 011 | @username без @ в UI |
| 013 | Social Identity Layer Strategy |
| 014 | Trusted Contacts в UI (guardian в коде) |
| 015 | Identity ≠ Social Network. Payments = Module |
| 016 | UIP (не MIP) |
| 017 | Post-paid Paymaster |
| 018 | P-256 Recovery (не ECDSA) |
| 019 | SSS 2-of-3 (единственный режим) |
| 020 | API /v1/ prefix |
| 021 | OAuth = auxiliary |
| 022 | Identity Connect в MVP (Session Keys) |
| 023 | Capability Mapping (не Selector Mapping) |

---

*MDAOPay PRD · Unified Access Layer for Web3 · Июнь 2026*
