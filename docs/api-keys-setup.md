# API Keys Setup

## Bundler Keys (Stackup + Pimlico)

### Local Development
Create or edit `~/.gradle/gradle.properties`:
```properties
BUNDLER_STACKUP_KEY=sk_live_xxx
BUNDLER_PIMLICO_KEY=pim_xxx
```

These override the `YOUR_API_KEY` placeholders in the project's `gradle.properties`.

### CI/CD (GitHub Actions / GitLab CI)
Set as repository secrets, injected into `~/.gradle/gradle.properties` during build.

### How to get keys
- **Stackup:** https://app.stackup.sh/ → Create API key
- **Pimlico:** https://dashboard.pimlico.io/ → Create API key

---

## Backend Keys (Paymaster)

### Environment Variables
| Variable | Required | Description |
|---|---|---|
| `RPC_URL` | Yes | Sepolia: `https://rpc.sepolia.org`, BSC: `https://bsc-dataseed1.binance.org` |
| `PAYMASTER_PRIVATE_KEY` | Yes | Private key for signing UserOperations |
| `PAYMASTER_ADDRESS` | Yes | Deployed MDAOPaymaster address |
| `MDAO_ADDRESS` | Yes | MDAO token address on target chain |
| `USDT_ADDRESS` | Yes | USDT token address on target chain |
| `EXPECTED_CHAIN_ID` | No | If set, backend verifies chain ID before signing |
| `PORT` | No | Default: 8080 |

### Key Separation Warning

**⚠️ CRITICAL:** Currently `Sepolia_KEY` and `BSC_KEY` use the same private key.

**Before mainnet launch, you MUST:**
1. Generate a new key: `cast wallet new`
2. Fund the new key on BSC
3. Update `PAYMASTER_ADDRESS` to match the new key
4. Update BSC deployment with new key

### Secure Key Handling

**⚠️ Recommended:** Use Foundry keystore instead of environment variables:

```bash
# Безопасно (keystore с паролем)
cast wallet import deployer --interactive
forge script script/Deploy.s.sol --account deployer --sender <addr> --broadcast

# Менее безопасно (ключ в истории shell)
export DEPLOYER_PRIVATE_KEY=0x...
forge script script/Deploy.s.sol --broadcast
unset DEPLOYER_PRIVATE_KEY   # обязательно после использования
```

See `contracts/script/DeployMDAOPaymaster.s.sol` for deployment instructions.
