# Chain Fork — Incident Response

## Symptoms
- Transactions stuck
- Nonce mismatches
- Double-spend risk

## Diagnosis
1. Check chain reorgs: eth_getBlockByNumber
2. Check pending txs: eth_pendingTransactions
3. Monitor block production

## Mitigation
1. Pause paymaster (stop signing)
2. Wait for chain finality
3. Resubmit stuck txs with higher gas

## Recovery
1. Verify all txs post-fork
2. Check for double-spend
3. Resume paymaster operations

## Prevention
- Wait for 12+ confirmations
- Monitor chain health
- Use chain finality checkpoints
