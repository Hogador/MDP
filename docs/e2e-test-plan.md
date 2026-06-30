# E2E Test Plan — MDAOPay (Sepolia)

## Prerequisites

### Environment
```bash
# gradle.properties (global: ~/.gradle/gradle.properties)
BUNDLER_STACKUP_KEY=sk_live_xxx
BUNDLER_PIMLICO_KEY=pim_xxx

# Backend .env
RPC_URL=https://rpc.sepolia.org
PAYMASTER_PRIVATE_KEY=0x...
PAYMASTER_ADDRESS=0xF6Dca93AF261Bc1ee10ba6cE57cb5AE38588d2d0
MDAO_ADDRESS=<deployed MDAOToken address>
USDT_ADDRESS=0x7169D38820dfd117C3FA1f22a697dBA58d90ba06
ENTRY_POINT=0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789
EXPECTED_CHAIN_ID=11155111
```

### Deploy MDAO Token
```bash
cd contracts
forge script script/DeployMDAOToken.s.sol \
  --rpc-url sepolia \
  --broadcast \
  --verify
# Copy deployed address → backend .env MDAO_ADDRESS
```

### Fund Paymaster
```bash
cast send 0xF6Dca93AF261Bc1ee10ba6cE57cb5AE38588d2d0 \
  --value 0.1ether \
  --rpc-url sepolia
```

### Run Backend
```bash
cd backend
cp .env.example .env  # fill in values
docker compose up -d
```

---

## Flow 1: EIP-712 Registration → Native Send

1. Fresh install app
2. Complete 4-step tutorial
3. **Verify:** `NicknameScreen` creates wallet, registers nickname
4. Fund smart account with SepoliaETH + USDT
5. Go to Send → enter recipient address → amount 1 USDT
6. Confirm with biometric
7. **Verify:** `ProcessingStep` shows elapsed time + status progression
8. **Verify:** `SuccessStep` shows block number + "Open in Explorer" button
9. Tap explorer button → opens sepolia.etherscan.io/tx/...

**Expected:** TxHash visible on Etherscan within 60s

---

## Flow 2: Paymaster Send (MDAO/USDT)

1. Ensure smart account has MDAO or USDT balance
2. Send 1 USDT via `/send`
3. **Verify:** Backend `/sign` called (check docker logs)
4. **Verify:** `postOp` transfers MDAO/USDT to paymaster
5. **Verify:** `GasPaid` event emitted

**Expected:** User pays 0 gas — all gas covered by MDAO/USDT

---

## Flow 3: Recovery (2-of-3)

1. Go to Recovery → BACKUP tab
2. Tap "Create backup"
3. **Verify:** Share 1 saved (EncryptedSharedPrefs), Share 2 saved (AES file)
4. Note: NFC share 3 optional
5. Uninstall app / clear data
6. Open app → tutorial → import wallet → go to Recovery
7. Tap "Restore wallet"
8. Authenticate with biometric
9. **Verify:** Wallet restored, balance visible

**With NFC (optional):**
- After backup creation, tap "Write to NFC"
- Hold NFC tag to phone
- After clearing app, tap "Restore from NFC"
- Hold same NFC tag → wallet restored

---

## Flow 4: Permit Frontrunning Tolerance

1. Fund smart account with MDAO
2. Approve paymaster for MDAO
3. Create permit signature for MDAO
4. Execute permit externally (frontrun via cast)
5. Send UserOp with same permit
6. **Verify:** `_executePermit` doesn't revert — `allowance >= value` check passes

```bash
cast send $MDAO "permit(address,address,uint256,uint256,uint8,bytes32,bytes32)" \
  $OWNER $PAYMASTER $VALUE $DEADLINE $V $R $S \
  --rpc-url sepolia --private-key $PK
```

---

## Flow 5: Deadline Enforcement

1. Set `minimumDeadlineBuffer` to 600 (10 min)
2. Send UserOp with `quoteDeadline = block.timestamp + 60` (< buffer)
3. **Verify:** EntryPoint returns `DeadlineTooSoon` error

```bash
cast send $PAYMASTER "setMinimumDeadlineBuffer(uint256)" 600 \
  --rpc-url sepolia --private-key $OWNER_PK
```

---

## Flow 6: Rate Limiting

1. Send 21 requests in 60 seconds from same IP
2. **Verify:** Request 21 returns 429 "Rate limited"
3. Wait 60s → request succeeds
4. Send 2 requests from same sender in 30s
5. **Verify:** Request 2 returns "Too many requests for this sender"

---

## Debug Commands

```bash
# Check paymaster balance
cast balance 0xF6Dca93AF261Bc1ee10ba6cE57cb5AE38588d2d0 --rpc-url sepolia

# Check smart account balance
cast balance $SMART_ACCOUNT --rpc-url sepolia

# Check UserOperation receipt
# (via bundler eth_getUserOperationReceipt)
# Or via etherscan if txHash known

# View backend logs
docker logs mdaopay-paymaster -f
```
