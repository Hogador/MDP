# Guardian Compromise — Incident Response

## Symptoms
- Unauthorized recovery initiated
- Guardian reports compromise

## Diagnosis
1. Check RecoveryInitiated events
2. Verify guardian signatures
3. Check relay logs

## Mitigation
1. Veto recovery if within 48h timelock
2. Revoke compromised guardian
3. Pause recovery module (if pause exists)

## Recovery
1. Replace compromised guardian
2. Re-initiate recovery with new guardian set
3. User regains access

## Prevention
- 48h timelock for recovery
- Veto mechanism
- Canary guardian monitoring
