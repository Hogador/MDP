# MDAOPay — Code Implementation Roadmap (v1.0)

> **Дата:** Июнь 2026
> **Статус:** Actionable
> **Принцип:** Платежи создают пользователей. Identity Connect — первый шаг к сетевому эффекту. Беспощадная обрезка всего, что не помогает получить первых 1000 пользователей.

---

## Фаза 0: MVP & First 1000 Users (Q3 2026)

**Цель:** Работающий gasless кошелёк с Trusted Contacts recovery и базовым Identity Connect.

### Недели 1-3: Smart Contracts Core
- [ ] `SimpleAccount.sol` (ERC-4337 v0.6) с P-256 owner validation
- [ ] `MDAOPaymaster.sol` (post-paid deduction, MDAO/USDT fallback)
- [ ] `SocialRecoveryModule.sol` (P-256, 2-of-3, 48h timelock, anti-spam deposit)
- [ ] `NicknameRegistry.sol` (EIP-712, identityHash mapping)
- [ ] Деплой на Sepolia testnet, верификация на Etherscan

### Недели 4-6: Mobile App Foundation
- [ ] Onboarding (Tutorial → Biometric → Nickname → Trusted Contacts setup)
- [ ] Wallet creation (BIP-39 → AES-256-GCM → Keystore)
- [ ] Home Screen (Cards stack, balances, actions)
- [ ] Send Flow (поиск по @username → amount → Face ID → Bundler)

### Недели 7-9: Backend & Infrastructure
- [ ] Ktor backend (Kotlin)
- [ ] PostgreSQL (users, nicknames, transactions, guardians)
- [ ] Redis (rate limiting, replay cache, DEX prices)
- [ ] Nickname Service API (`/v1/nickname/register`, `/v1/nickname/{name}`)
- [ ] Relay Service (WebSocket for recovery requests)
- [ ] RPC Multi-Provider (3 providers, failover)

### Недели 10-12: Identity Connect (MVP Scope)
- [ ] `SessionKeyModule.sol` (scoped permissions, limits, expiry)
- [ ] Capability Mapping engine (mobile side: calldata → human-readable intent)
- [ ] MDAOPay Connect SDK (Kotlin module for dApp integration)
- [ ] Connect Modal UI (permissions list, Face ID, revoke)
- [ ] Интеграция с 1 легитимным dApp (Arena HTML prototype)

### Недели 13-15: Polish & Pre-mainnet
- [ ] UX полировка (Dark/Light theme, animations)
- [ ] Security audit preparation (Slither, Foundry fuzz)
- [ ] Mainnet deployment (BSC)
- [ ] Beta testing (MarsDAO community, 100 users)
- [ ] App Store / Google Play submission

**Результат Phase 0:** 5,000 установок, 1,000 активных пользователей.

---

## Фаза 1: Ecosystem Access & Multi-Sponsor (Q1 2027)

**Цель:** "Login with MDAOPay" работает в экосистеме MarsDAO. Экосистемы платят за gas.

### Backend & Contracts
- [ ] `MultiSponsorPaymaster.sol` (Ecosystem, Merchant, Treasury modes)
- [ ] `EcosystemRegistry.sol` (реестр подключённых dApps)
- [ ] SDK: `MDAOPay.connect()` / `MDAOPay.pay()` (Kotlin)
- [ ] Analytics API для dApps (кто подключился, сколько провёл операций)

### Mobile App
- [ ] Push notifications для recovery (FCM/APNS)
- [ ] Trusted Contacts management UI (добавить, удалить, заменить)
- [ ] Cloud Backup (опциональный fallback, iCloud/Google Drive)
- [ ] Offline QR (состояния B: signed UserOp, C: reverse QR)

### Ecosystem
- [ ] Интеграция Arena (Web + Mobile deep link)
- [ ] Интеграция DEX (1inch wrapper)
- [ ] Интеграция Flopi, VPN
- [ ] 5+ сервисов с "Login with MDAOPay"

**Результат Phase 1:** 50,000 пользователей, 5+ интегрированных dApps.

---

## Фаза 2: Open SDK & UIP Draft (Q3 2027)

**Цель:** Внешние dApps подключаются. Multi-chain.

- [ ] TypeScript SDK (для Web3 dApps)
- [ ] UIP (Universal Identity Protocol) draft specification
- [ ] Multi-chain support (Ethereum, Polygon, Base)
- [ ] iOS app (Swift/Compose Multiplatform)
- [ ] Session Keys: cross-chain support
- [ ] OIDC compatibility (для Web2 сервисов)

**Результат Phase 2:** 200,000 пользователей, 50+ dApps.

---

## Вырезано из roadmap (до Phase 5+)

- Mesh Relay (состояние D: оба офлайн)
- Reputation scores
- Социальные связи (публичный trust graph)
- Credit layer
- Merchant SDK (NFC/QR в физических точках)
