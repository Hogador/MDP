# Paymaster Down — Incident Response

## Symptoms
- /sign endpoint returns 500
- Users cannot send transactions

## Diagnosis
1. Check backend logs: kubectl logs -l app=backend
2. Check RPC providers: curl $RPC_URL
3. Check Redis: redis-cli ping

## Mitigation
1. If RPC down: switch to backup provider
2. If Redis down: restart redis
3. If backend down: restart deployment

## Recovery
1. Verify /health endpoint
2. Test /sign with test UserOp
3. Monitor error rate

## Prevention
- Health checks every 30s
- Auto-failover RPC providers
