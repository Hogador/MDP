# DEPLOY TESTNET — пошаговая инструкция (BSC Testnet)

> Создана: 2026-07-06 · Coordinator: D-050
> Статус: ⬜ не выполнено

Перед деплоем на BSC Testnet (chain 97) — заполните все секреты и выполните шаги ниже.

---

## 1. Получение RPC и faucet (вы руками)

### 1.1. RPC endpoint
- [ ] Публичный RPC: `https://data-seed-prebsc-1-s1.binance.org:8545`
- [ ] Или зарегистрироваться на [QuickNode](https://www.quicknode.com/) / [Chainstack](https://chainstack.com/) и создать BSC Testnet endpoint

### 1.2. Faucet — тестовый tBNB
- [ ] [BNB Chain Faucet (Smart)](https://testnet.bnbchain.org/faucet-smart)
- [ ] [BNB Chain официальный faucet](https://www.bnbchain.org/en/testnet-faucet)
- [ ] Ввести адрес deployer-кошелька
- [ ] Дождаться 0.5+ tBNB на балансе

---

## 2. Заполнение секретов (вы руками)

### 2.1. Установить инструменты
- [ ] [Foundry](https://book.getfoundry.sh/getting-started/installation) (`foundryup`)
- [ ] Docker + Docker Compose
- [ ] jq, curl, openssl
- [ ] AWS CLI (если используете KMS)
- [ ] Node.js + npm (для Cloudflare Workers)

### 2.2. Создать deployer-ключи

Скрипт `scripts/deploy-testnet.sh` требует 3 ключа:

```bash
# Основной deployer-ключ (для деплоя контрактов)
export BSC_TESTNET_DEPLOYER_KEY=0x...

# Ключ для paymaster (подпись UserOp)
export TESTNET_PAYMASTER_KEY=0x...

# Отдельный ключ для swap-операций (никогда не используйте один ключ для всего!)
export TESTNET_SWAP_KEY=0x...
```

**ВАЖНО**: Никогда не используйте mainnet-ключи для testnet.
Создайте новые через `cast wallet new` или MetaMask.

### 2.3. Получить BSCscan API key
- [ ] Зарегистрироваться на [BSCscan](https://bscscan.com/)
- [ ] Создать API Key в [API Keys](https://bscscan.com/myapikey)
```bash
export BSCSCAN_API_KEY=...
```

### 2.4. Trusted signer (для EIP-712 off-chain quote)
```bash
# Адрес кошелька, который подписывает off-chain quote для paymaster
export TRUSTED_SIGNER=0x...
```

### 2.5. (Опционально) AWS KMS
Если используете KMS для production-подписи:
```bash
export AWS_REGION=us-east-1
export KMS_KEY_ID=alias/mdaopay-testnet
```

### 2.6. .env для Coordinator (корень проекта)
```ini
# === TESTNET DEPLOY (BSC Testnet) ===

# RPC
RPC_URL=https://data-seed-prebsc-1-s1.binance.org:8545

# Ключи
PAYMASTER_PRIVATE_KEY=0x...
DEPLOYER_PRIVATE_KEY=0x...
BSCSCAN_API_KEY=...
TRUSTED_SIGNER=0x...

# Сеть
EXPECTED_CHAIN_ID=97
```

---

## 3. Деплой смартконтрактов

### Через deploy-testnet.sh (рекомендуется)

Скрипт делает всё автоматически: деплой контрактов, verify, Docker-инфраструктуру, backend:

```bash
BSC_TESTNET_DEPLOYER_KEY=0x... \
TESTNET_PAYMASTER_KEY=0x... \
TESTNET_SWAP_KEY=0x... \
BSCSCAN_API_KEY=... \
TRUSTED_SIGNER=0x... \
./scripts/deploy-testnet.sh
```

Скрипт развернёт:
1. P256Verifier
2. MDAOToken (ERC20 governance)
3. MDAOPaymaster (ERC-4337)
4. InsuranceFund
5. SocialRecoveryModule + SessionKeyModule
6. NicknameRegistry
7. DeadManSwitch
8. AttestationLedger + RefundVault + TrustProviderRegistry
9. EcdsaVerifier
10. TimelockController
11. **Treasury** + **Proposal** + **PaymentSplitterFactory**
12. Wire зависимости (setProposalContract, grantRole, etc.)
13. Verify всех контрактов на BSCscan

После успешного деплоя скрипт выведет deployment summary.

### Ручной деплой (через forge)

```bash
cd contracts
forge script script/Deploy.s.sol \
  --rpc-url https://data-seed-prebsc-1-s1.binance.org:8545 \
  --broadcast \
  --verify \
  --verifier-url https://api-testnet.bscscan.com/api \
  --etherscan-api-key $BSCSCAN_API_KEY \
  -vvv
```

---

## 4. Верификация на BSCscan (вы проверяете)

- [ ] Зайти в [BSC Testnet Explorer](https://testnet.bscscan.com/)
- [ ] Найти каждый контракт по адресу (из deployment summary)
- [ ] Убедиться что Source Code верифицирован (зелёная галочка)
- [ ] Проверить `owner` каждого контракта — совпадает с deployer

---

## 5. Запуск backend

### 5.1. Backend конфигурация

Скрипт `deploy-testnet.sh` генерирует `backend/.env.testnet.public`.
Если деплоили вручную — создайте его:

```ini
# backend/.env.testnet.public
PORT=8080
EXPECTED_CHAIN_ID=97
IS_TESTNET=true
RPC_URLS=https://data-seed-prebsc-1-s1.binance.org:8545

# Адреса контрактов (из deployment summary)
PAYMASTER_ADDRESS=0x...
MDAO_ADDRESS=0x...
USDT_ADDRESS=0x337610d27c682E347C9cD60BD4b3b107C9d34dDD
WBNB_ADDRESS=0xae13d989daC2f0dEbFf460aC112a837C89BAa7cd
ENTRY_POINT=0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789

# Модули
RECOVERY_MODULE_ADDRESS=0x...
SESSION_KEY_MODULE_ADDRESS=0x...
NICKNAME_REGISTRY_ADDRESS=0x...
INSURANCE_FUND_ADDRESS=0x...
DEAD_MAN_SWITCH_ADDRESS=0x...
REFUND_VAULT_ADDRESS=0x...
ATTESTATION_LEDGER_ADDRESS=0x...
TRUST_PROVIDER_REGISTRY_ADDRESS=0x...
ECDSA_VERIFIER_ADDRESS=0x...
TIMELOCK_ADDRESS=0x...
P256_VERIFIER_ADDRESS=0x...
SWAP_ROUTER_ADDRESS=0x9Ac64Cc6e4415144C455BD8E4837Fea55603e5c3

# F-104 Event Indexer (новые адреса)
TREASURY_ADDRESS=0x...
PROPOSAL_ADDRESS=0x...
PAYMENT_SPLITTER_FACTORY_ADDRESS=0x...

# Подпись
ALLOW_LOCAL_SIGNING=true
```

Также создайте `backend/.env.testnet.secret` (не коммитить!):
```ini
REDIS_URL=redis://localhost:6379
PAYMASTER_PRIVATE_KEY=0x...    # testnet paymaster key
SWAP_PRIVATE_KEY=0x...         # swap key (отдельный от paymaster!)
JWT_SECRET=<base64 256-bit key>
TRUSTED_SIGNER=0x...
RELAY_SECRET=<минимум 32 символа>
DATABASE_URL=jdbc:postgresql://localhost:5432/mdaopay?user=mdaopay&password=...
```

### 5.2. Запустить инфраструктуру (Docker)

```bash
cd backend
docker compose up -d  # PostgreSQL + Redis
```

### 5.3. Запустить backend

```bash
cd backend
# Production (Docker):
docker compose up -d

# Или для разработки:
./gradlew run
```

### 5.4. Проверить health

```bash
curl http://localhost:8080/health
# → {"status":"ok","rpc_providers":1}
```

### 5.5. Проверить event indexer

```bash
# Через минуту после запуска — проверить что события индексируются:
curl "http://localhost:8080/v1/events?event=Deposited&limit=5"
# → {"events":[...],"total":N}
```

---

## 6. Проверка end-to-end (вы руками)

- [ ] Проверить `/health` — backend отвечает
- [ ] `cast balance` — deployer имеет tBNB
- [ ] Проверить `/v1/events` — события индексируются
- [ ] Отправить тестовую UserOp через paymaster `/v1/sign`
- [ ] Создать proposal через Treasury
- [ ] Проголосовать
- [ ] Исполнить proposal
- [ ] Проверить PaymentSplitter

---

## 7. Чек-лист готовности testnet

Перед переходом к Стадии 2 (полноценный testnet):

- [ ] **D-050**: Инструкция прочитана — все шаги выполнены
- [ ] **D-051**: `.env` заполнен всеми ключами
- [ ] **D-052**: Тестовый tBNB получен из faucet
- [ ] **D-053**: CI/CD secrets настроены
- [ ] **F-101**: Деплой-скрипт работает
- [ ] **F-102**: Контракты верифицированы на BSCscan
- [ ] **F-104**: EventIndexer запущен, события индексируются
- [ ] **S-101**: Аудит смартконтрактов пройден
- [ ] Backend запускается (Ktor, port 8080)
- [ ] End-to-end сценарии проходят

### Технические константы BSC Testnet

| Контракт | Адрес |
|---|---|
| USDT (тестовый) | `0x337610d27c682E347C9cD60BD4b3b107C9d34dDD` |
| WBNB | `0xae13d989daC2f0dEbFf460aC112a837C89BAa7cd` |
| PancakeSwap Router | `0x9Ac64Cc6e4415144C455BD8E4837Fea55603e5c3` |
| EntryPoint (v0.7) | `0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789` |

---

## Команды для быстрого старта

```bash
# 1. Полный деплой
export BSC_TESTNET_DEPLOYER_KEY=0x...
export TESTNET_PAYMASTER_KEY=0x...
export TESTNET_SWAP_KEY=0x...
export BSCSCAN_API_KEY=...
export TRUSTED_SIGNER=0x...
./scripts/deploy-testnet.sh

# 2. Проверить баланс deployer
cast balance --rpc-url https://data-seed-prebsc-1-s1.binance.org:8545 $DEPLOYER_ADDRESS

# 3. Прямой деплой (без скрипта)
cd contracts && forge script script/Deploy.s.sol \
  --rpc-url https://data-seed-prebsc-1-s1.binance.org:8545 \
  --broadcast --verify \
  --verifier-url https://api-testnet.bscscan.com/api \
  --etherscan-api-key $BSCSCAN_API_KEY -vvv

# 4. Статус дорожной карты
opencode run --mode=roadmap --target=testnet --action=gap-analysis
```

---

*После выполнения всех шагов — вернитесь ко мне с командой:*
`/swarm roadmap --target=testnet --action=gap-analysis`
