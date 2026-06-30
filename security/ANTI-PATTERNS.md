# MDAOPay Anti-Patterns — DO NOT REPEAT

> Each anti-pattern has history (which wave), why it's bad, and correct approach.
> AI agents MUST read this before proposing any fix.

---

## AP-LOG-001: Removing txHash from logs

**First seen:** Wave 6 (L-01)
**Repeats:** Wave 7
**Why bad:** txHash is public blockchain data. Removal breaks audit trail and debugging without privacy gain.
**Correct approach:** Log txHash with structured logging, mark `pii=false`.
```kotlin
log.info(Map.of("event", "swap_submitted", "txHash", txHash, "pii", false))
```

---

## AP-LOG-002: Blanket ${e.message} removal

**First seen:** Wave 6 (L-05)
**Repeats:** Wave 7 (SEC-27-XX, 14 more instances) — **REGRESSION**
**Why bad:** Different exceptions leak different info. SQLException leaks schema, IOException doesn't. Blanket removal destroys observability.
**Correct approach:** Context-aware LogSanitizer utility.
```kotlin
log.error("Failed id={} reason={}", errorId, LogSanitizer.sanitizeError(e))
if (log.isDebugEnabled) log.debug("Stack id={}", errorId, e)
```
See FIX-PATTERNS.md → FP-LOG-001.

---

## AP-LOG-003: Removing wallet addresses from watchtower

**First seen:** Wave 6 (L-02)
**Why bad:** Cannot correlate incidents, debug failures.
**Correct approach:** Short hash `0x1234...5678` for correlation.
```kotlin
val walletShort = "0x" + wallet.takeLast(4)
watchLog.warn("Recovery for wallet={} nonce={}", walletShort, nonce)
```

---

## AP-LOG-004: Error propagation sanitized

**First seen:** Wave 7 (SEC-27-05)
**Why bad:** User can't debug what's wrong with their signature.
**Correct approach:** Error codes.
```kotlin
enum class SignatureError { MALFORMED, WRONG_LENGTH, ADDRESS_MISMATCH }
return Result.failure(IllegalArgumentException(SignatureError.MALFORMED.name))
```

---

## AP-AUTH-001: "Mitigated by multisig" without on-chain enforcement

**First seen:** Wave 5 (F-06, F-11)
**Repeats:** Wave 6, Wave 7 (3 waves, same claim)
**Why bad:** TDD documentation ≠ code enforcement. If multisig keys compromised or deploy uses EOA — no protection.
**Correct approach:** TimelockController + AccessControl roles in contract code.
```solidity
import {TimelockController} from "@openzeppelin/contracts/governance/TimelockController.sol";
contract MDAOPaymaster is TimelockController { ... }
```
See FIX-PATTERNS.md → FP-AUTH-002.

---

## AP-CRYPTO-001: EIP-191 instead of EIP-712 for structured data

**First seen:** Wave 4 (F-05)
**Repeats:** Wave 5 (deferred as "enhancement" — WRONG)
**Why bad:** EIP-191 doesn't protect against cross-contract/cross-chain replay. Wallets can't display structured data.
**Correct approach:** EIP-712 with domain separator.
```solidity
import {EIP712} from "@openzeppelin/contracts/utils/cryptography/EIP712.sol";
contract MDAOPaymaster is EIP712("MDAOPay", "1") { ... }
```
See FIX-PATTERNS.md → FP-AUTH-001.

---

## AP-CRYPTO-002: P-256 with Ethereum prefix instead of WebAuthn

**First seen:** Wave 7 (C-7)
**Why bad:** WebAuthn signs `authenticatorData || SHA256(clientDataJSON)`, not Ethereum-prefixed hash. Face ID / Touch ID won't work.
**Correct approach:** Full WebAuthn verification.
See FIX-PATTERNS.md → FP-CRYPTO-001.

---

## AP-PROCESS-001: Marking "fixed" without regression test

**First seen:** Wave 6
**Repeats:** All subsequent waves
**Why bad:** Without test, regression undetectable. Future agent re-discovers, claims "fixed" again — circular.
**Correct approach:** Mandatory regression test per fix, name in FINDINGS.md.
```kotlin
@Test
@DisplayName("SEC-27-05: Signature error returns error code, not raw message")
fun `signature error returns code`() { ... }
```

---

## AP-PROCESS-002: Severity downgrade without justification

**First seen:** Wave 5 (F-06 HIGH→MEDIUM)
**Repeats:** Wave 6, Wave 7
**Why bad:** Severity drift makes prioritization impossible. Stakeholders lose trust in audit.
**Correct approach:** Document reason in lifecycle entry, reference §0 downgrade rules from FINDINGS.md.

---

## AP-PROCESS-003: Coordinator writes code after failed subagents

**First seen:** Wave 5
**Repeats:** Wave 6, Wave 7
**Why bad:** Coordinator on free model produces lower quality code. Defeats purpose of specialization.
**Correct approach:** Retry with upgraded model (see retry_policy in opencode-swarm.json). If all retries fail — escalate to human, don't fallback to coordinator.

---

## AP-PROCESS-004: Re-discovering same finding in next wave

**First seen:** Wave 5
**Repeats:** 51% of findings are duplicates
**Why bad:** Wastes tokens, creates noise, hides real progress.
**Correct approach:** Compute fingerprint, search FINDINGS.md before reporting. See protocol in AGENTS.md.

---

## AP-PROCESS-005: Claiming "fixed" in commit message but diff shows otherwise

**First seen:** Wave 4 (F-2)
**Why bad:** Misleading, audit trail corrupted.
**Correct approach:** Code review checklist: verify diff matches commit message before commit.

---

## AP-CONFIG-001: Hardcoded default password in docker-compose

**First seen:** Wave 4 (S-1)
**Repeats:** Wave 5, 6, 7 (claimed fixed, wasn't)
**Why bad:** Default password if deployed without override.
**Correct approach:** Fail-fast env var.
```yaml
POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}
```

---

## AP-NETWORK-001: Public RPC for mobile app

**First seen:** Wave 6 (R-01)
**Why bad:** Rate limited, no SLA, guaranteed outage at scale.
**Correct approach:** Private RPC (Alchemy/Infura) with API key, multi-provider failover.

---

## AP-NETWORK-002: No certificate pinning

**First seen:** Wave 6
**Why bad:** MITM with rogue CA possible.
**Correct approach:** CertificatePinner for backend domains, Network Security Config.
