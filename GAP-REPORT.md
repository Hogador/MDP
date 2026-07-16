# GAP-REPORT: Блокеры testnet деплоя MDAOPay

> Создан: 2026-07-09 · Coordinator
> Статус: ⬜ не выполнено

---

## Критические блокеры (без них деплой невозможен)

| # | Блокер | Где | Действие |
|---|--------|-----|----------|
| **B1** | `RPC_URL=` пуст | `.env` строка 9 | Вставить `https://data-seed-prebsc-1-s1.binance.org:8545` |
| **B2** | `DEPLOYER_PRIVATE_KEY=` пуст | `.env` строка 16 | Создать кошелёк через `cast wallet new` или MetaMask |
| **B3** | `PAYMASTER_PRIVATE_KEY=` пуст | `.env` строка 17 | Создать отдельный кошелёк (не совпадает с deployer) |
| **B4** | tBNB = 0 | faucet | Залить 0.5+ tBNB из [BNB Chain Faucet](https://testnet.bnbchain.org/faucet-smart) |
| **B5** | `BSCSCAN_API_KEY` не получен | — | Зарегистрироваться на BSCscan, создать API Key |
| **B6** | CI/CD secrets не настроены | GitHub repo | Добавить `secrets.DEPLOYER_PRIVATE_KEY`, `secrets.TESTNET_SSH_*`, `vars.BSC_TESTNET_RPC` и др. |
| **B7** | `TRUSTED_SIGNER` не создан | — | Создать кошелёк для подписи EIP-712 off-chain quote |
| **B8** | `TESTNET_SWAP_KEY` не создан | — | Отдельный ключ для swap-операций (F-035: не совпадает с paymaster) |
| **B9** | `./scripts/deploy-testnet.sh` требует AWS CLI + AWS Secrets Manager | — | Настроить AWS или модифицировать скрипт для работы без AWS |

## Некритические (можно после деплоя)

| # | Замечание | Где | Комментарий |
|---|-----------|-----|-------------|
| **W1** | `.env` имеет `EXPECTED_CHAIN_ID=56` (mainnet) | `.env` строка 10 | Для testnet нужно `97`. Скрипт `deploy-testnet.sh` использует свою константу `97`, так что не блокер, но лучше согласовать |
| **W2** | `USDT_ADDRESS` и `MDAO_ADDRESS` пусты | `.env` строка 19-20 | Заполнятся **после** деплоя контрактов |
| **W3** | `RELAY_SECRET` пуст | `.env` строка 39 | Скрипт генерирует через AWS Secrets Manager |
| **W4** | InsuranceFund auditor = deployer по умолчанию | `deploy-testnet.sh` строка 162 | Нарушает `require(insuranceAuditor != deployer)` в Deploy.s.sol строка 70. Будет ошибка при деплое |
| **W5** | DeployMDAOPaymaster.s.sol комментарий "BSC testnet: deploy your own" для MDAO | строка 25 | Нужно будет указать адрес MDAOToken после его деплоя |
| **W6** | DeploySocialRecoveryModule.s.sol WARNING о RIP-7212 | строка 17-18 | BSC не имеет P-256 precompile — Deploy.s.sol корректно деплоит P256Verifier |

## Архитектурные блокеры (решены сегодня)

| # | Проблема | Решение |
|---|----------|---------|
| ~~A1~~ | task tool падает: DeepSeek V3.2 32K контекста | ✅ thinker/researcher комбо: Mistral Large 128K primary |
| ~~A2~~ | Subagent получает 54K мусора из контекста Coordinator | ✅ IndexedStepFeedback: 500 токенов фактов вместо 54K |
| ~~A3~~ | Qwythos-9B (:8081) не запущен | ⏸ audit mode отложен |

## Checked: что НЕ является блокером

| Проверка | Статус |
|----------|--------|
| Deploy.s.sol поддерживает chainId 97 | ✅ (строка 29) |
| Нет захардкоженных Sepolia адресов | ✅ |
| Treasury, Proposal, PaymentSplitterFactory присутствуют | ✅ (строки 127-167) |
| TimelockController: proposers=[gnosisSafe] а не [deployer] | ✅ (F-107, строка 180) |
| DEPLOY-TESTNET.md соответствует реальным скриптам | ✅ |
| Верификация через BSCscan настроена | ✅ (строка 213-214) |
| InsuranceFund auditor not deployer — проверка | ✅ (строка 71, но W4) |

## Следующие шаги (вы руками)

1. **B1-B8**: Создать кошельки, получить tBNB, BSCscan API Key
2. **B6**: Настроить GitHub secrets (DEPLOYER_PRIVATE_KEY, BSC_TESTNET_RPC, etc.)
3. **W4**: Исправить `deploy-testnet.sh` — auditor не должен быть deployer
4. **B9**: Решить — использовать AWS или модифицировать скрипт
5. **Запустить**: `BSC_TESTNET_DEPLOYER_KEY=0x... TESTNET_PAYMASTER_KEY=0x... ./scripts/deploy-testnet.sh`

---

*Полный план деплоя: `docs/DEPLOY-TESTNET.md`*
