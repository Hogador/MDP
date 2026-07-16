# MDAOPay — Risk Registry Memory

## Open Risks (must fix before mainnet)
| ID | Risk | P | I | R | Status | Mitigation |
|----|------|---|---|---|--------|------------|
| R-02 | mint() обесценивает токен | 3 | 5 | **15** | OPEN | Удалить/DAO-gated до mainnet |
| R-04 | Flash loan голосование | 2 | 4 | **8** | OPEN | ERC20Votes snapshot до mainnet |
| R-05 | Admin-дренаж Treasury | 1 | 5 | **5** | OPEN | Timelock до mainnet |
| R-06 | API открыт интернету | 4 | 4 | **16** | OPEN | IAM + Cloudflare Access до testnet |
| R-07 | Ключи в env | 3 | 5 | **15** | OPEN | AWS KMS ECC_SECG_P256K1 до mainnet |
| R-08 | Деплой без approval | 3 | 4 | **12** | OPEN | GitHub Environments до testnet |

## Fixed Risks
| ID | Risk | Fix |
|----|------|-----|
| R-01 | Reentrancy в InsuranceFund | nonReentrant + CEI |
| R-03 | SessionKey DoS | MAX_PERMISSIONS=20 |

## Critical Notes
- GCP Cloud KMS does NOT support secp256k1 — only P-256/P-384
- For Ethereum: use AWS KMS with ECC_SECG_P256K1 or HashiCorp Vault transit
