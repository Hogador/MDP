# RISK REGISTRY — MDAOPay

**Обновлён:** 2026-07-06 (после code review аудита)
**Всего рисков:** 8 (активных)

| ID | Риск | P | I | R | Статус | Митигация |
|----|------|---|---|---|--------|-----------|
| R-01 | Reentrancy в InsuranceFund | 2 | 5 | **10** | ✅ **Исправлено** | nonReentrant + CEI |
| R-02 | mint() обесценивает токен | 3 | 5 | **15** | ⬜ | Удалить/DAO-gated (до mainnet) |
| R-03 | SessionKey DoS (permissions без лимита) | 2 | 2 | **4** | ✅ **Исправлено** | MAX_PERMISSIONS=20 |
| R-04 | Flash loan голосование | 2 | 4 | **8** | ⬜ | ERC20Votes snapshot (до mainnet) |
| R-05 | Admin-дренаж Treasury | 1 | 5 | **5** | ⬜ | Timelock (до mainnet) |
| R-06 | API открыт интернету | 4 | 4 | **16** | ⬜ | IAM + Cloudflare Access (до testnet) |
| R-07 | Ключи в env | 3 | 5 | **15** | ⬜ | **AWS KMS** `ECC_SECG_P256K1` (до mainnet) ⚠️ GCP KMS не поддерж. secp256k1 |
| R-08 | Деплой без approval | 3 | 4 | **12** | ⬜ | GitHub Environments (до testnet) |

## Примечание к R-07
GCP Cloud KMS не поддерживает secp256k1 — только P-256/P-384.
Для Ethereum используйте **AWS KMS с `ECC_SECG_P256K1`** или **HashiCorp Vault transit**.
