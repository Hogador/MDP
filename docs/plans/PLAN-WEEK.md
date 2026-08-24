# PLAN-WEEK: Testnet Deployment — Week Tasks (T1-T8)

**Статус:** В РАБОТЕ
**Дата:** 2026-08-17
**Контекст:** S1-S29 завершены. Осталось: relay accept gap, fail-fast, gradle, NavGraph, build, deploy.

---

## 1. МОДЕЛЬНЫЙ МАППИНГ (кто делает что)

### Доступные модели и их возможности

| Модель | Провайдер | Сила | Слабость | Назначение |
|--------|-----------|------|----------|------------|
| **deepseek-v4-flash-free** | opencode | Быстрый, хорошо пишет код, 128K ctx | Галлюцинации крипто-констант (S7), не различает хеш-порядок | coder, debugger, tester |
| **mistral-medium-2505** | Mistral | Сильный reasoning, 128K ctx | Дорогой, медленный | coordinator, planner |
| **codestral-2508** | Mistral | Специалист по коду, 256K ctx | 2 раза падал на Solidity (S20), нестабильный | ⚠️ РЕЗЕРВНЫЙ |
| **big-pickle** | opencode | Coordinator, orchestration | 6/8 → fallback (S22-S28) | coordinator (текущая модель) |

### Критические ограничения

1. **ВСЕ subagent'ы используют deepseek-v4-flash-free** — единственная модель для coding
2. **Последовательное выполнение** — никогда параллельно (S22-S28 протокол)
3. **deepseek галлюцинирует крипто-константы** — S7: байт менялся при повторном запуске
4. **cross-language risk** — relay (TypeScript) + app (Kotlin) должен работать с ОДНИМ хеш-алгоритмом
5. **big-pickle нестабилен** — используем только для планирования/координации

---

## 2. АНАЛИЗ ТЕКУЩЕГО БАГА (T1 Relay Accept Gap)

### Цепочка вызовов (текущая)

```
App (GuardianManager.acceptInvite):
  1. passkeyManager.authenticateWithPasskey(random evalInput) → random challenge
  2. extractWebAuthnAssertion(authenticationJson) → {authenticatorData, clientDataJSON, signature}
  3. signatureR = signature[0..32], signatureS = signature[32..64]
  4. relayClient.acceptInvite(inviteId, signatureR, signatureS, guardianIdentityHash)

Relay (index.ts L176-204):
  1. verifyP256Signature("accept:${inviteId}:${walletAddress}", signatureR, signatureS, pubKeyX, pubKeyY)
  2. ← НЕВЕРНО! Relay проверяет подпись над СТРОКОЙ, а app подписал WebAuthn assertion
```

### Проблема

| Что проверяет relay | Что подписывает app |
|---------------------|---------------------|
| `ECDSA_P256("accept:inviteId:walletAddr", r, s)` | `ECDSA_P256(SHA-256(authData \|\| SHA-256(clientDataJSON)), r, s)` |
| Простая строка | WebAuthn hash pipeline |

**Результат:** 401 Invalid guardian signature — ВСЕГДА.

### Три компонента бага

1. **Relay не получает authenticatorData/clientDataJSON** — types.ts: `AcceptInviteRequest` только `{signatureR, signatureS, guardianIdentityHash}`
2. **App генерирует случайный challenge** — `PasskeyManager.authenticateWithPasskey(random32)` — inviteId не передаётся
3. **Relay не вычисляет WebAuthn hash** — нет `crypto.subtle.digest('SHA-256', ...)` в accept handler

### Что ДОЛЖНО быть

```
App:
  1. challenge = base64url(inviteId.toByteArray()) — НЕ случайный!
  2. authenticateWithPasskey(challenge bytes) → {authenticatorData, clientDataJSON, signature}
  3. Отправить relay: {authenticatorData, clientDataJSON, signatureR, signatureS, guardianIdentityHash}

Relay:
  1. clientDataHash = SHA-256(clientDataJSON)
  2. signedData = authenticatorData || clientDataHash
  3. messageHash = SHA-256(signedData)
  4. verifyP256Signature(messageHash, signatureR, signatureS, pubKeyX, pubKeyY)
```

### Формат хеша (из contracts/src/SocialRecoveryModule.sol L637-664)

```solidity
// Step 1: clientDataHash = SHA-256(clientDataJSON)
bytes32 clientDataHash = sha256(clientDataJSON);

// Step 2: signedData = authenticatorData || clientDataHash
bytes memory signedData = new bytes(authenticatorData.length + 32);
// assembler: calldatacopy(authenticatorData), mstore(clientDataHash)

// Step 3: messageHash = SHA-256(signedData)
bytes32 messageHash = sha256(signedData);

// Step 4: ECDSA.recover(messageHash, signature) == publicKey
```

---

## 3. ПЛАН ЗАДАЧ (детальный)

### T3: Gradle Anomaly Check (investigation)

**Агент:** coordinator (я сам)
**Модель:** big-pickle (текущая)
**Риск:** НИЗКИЙ
**Ожидаемый результат:** `./gradlew clean compileKotlin compileDevDebugKotlin -PBUNDLER_URL_DEV=http://bundler.local:4337` → BUILD SUCCESSFUL

**Что делаю:**
1. Запускаю clean build
2. Проверяю что нет dependency conflicts после S28 удаления
3. Если есть ошибки → анализирую, является ли это следствием удаления мёртвого кода
4. Записываю результат

**Anti-hallucination:** Не доверяю "BUILD SUCCESSFUL" — проверяю exit code и warnings.

---

### T3.5: Генерация WebAuthn Fixture (shared source of truth)

**Агент:** coordinator (я сам)
**Модель:** big-pickle (текущая)
**Риск:** НИЗКИЙ — я вычисляю вручную
**Ожидаемый результат:** JSON файл с exact hex values

**КРИТИЧЕСКИЙ ШАГ:** Эта фикстура — ЕДИНСТВЕННЫЙ источник правды для T1a (relay) и T1b (app).

**Что делаю:**
1. Генерирую тестовые данные:
   - `inviteId` = "550e8400-e29b-41d4-a716-446655440000" (UUID)
   - `walletAddress` = "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD78"
   - `pubKeyX` = 32-byte hex (тестовый P-256 ключ)
   - `pubKeyY` = 32-byte hex
   - `authenticatorData` = 37-byte hex (rpIdHash 32 + flags 1 + signCount 4)
   - `clientDataJSON` = raw JSON string с `"challenge": "base64url(inviteId)"`
2. Вычисляю:
   - `clientDataHash = SHA-256(clientDataJSON bytes)` — точный hex
   - `signedData = authenticatorData bytes || clientDataHash bytes`
   - `messageHash = SHA-256(signedData)` — точный hex
3. Подписываю `messageHash` тестовым приватным ключом P-256 → `signatureR`, `signatureS`
4. Сохраняю всё в `relay/src/__tests__/fixtures/webauthn-accept.json`

**Формат фикстуры:**
```json
{
  "inviteId": "550e8400-e29b-41d4-a716-446655440000",
  "walletAddress": "0x742d35Cc6634C0532925a3b844Bc9e7595f2bD78",
  "pubKeyX": "abcd...",
  "pubKeyY": "1234...",
  "authenticatorData": "4a5b6c...",
  "clientDataJSON": "{\"type\":\"webauthn.get\",\"challenge\":\"...\",\"origin\":\"...\",\"crossOrigin\":false}",
  "clientDataHash": "sha256 hex of clientDataJSON",
  "signedData": "authenticatorData || clientDataHash",
  "messageHash": "sha256 hex of signedData",
  "signatureR": "...",
  "signatureS": "...",
  "signatureDER": "..."
}
```

**Self-challenge:**
1. Вычисляю хеш вручную через `echo -n "..." | sha256sum`
2. Проверяю что `authenticatorData || clientDataHash` конкатенация верна
3. Проверяю что base64url encoding правильный (NO_PADDING, NO_WRAP)

---

### T1a: Relay Coder — WebAuthn Hash Verification

**Агент:** coder
**Модель:** deepseek-v4-flash-free
**Риск:** ВЫСОКИЙ — cross-language hash computation
**Файлы:** `relay/src/types.ts`, `relay/src/auth.ts`, `relay/src/index.ts`

**Prompt (≤2000 токенов):**
```
## Task
Update relay accept handler to verify WebAuthn hash instead of plain string.

## Files in scope
- relay/src/types.ts
- relay/src/auth.ts
- relay/src/index.ts

## Fixture (shared source of truth)
Use exact values from relay/src/__tests__/fixtures/webauthn-accept.json

## Changes needed

### 1. types.ts: AcceptInviteRequest — add fields
```typescript
export interface AcceptInviteRequest {
  signatureR: string
  signatureS: string
  guardianIdentityHash: string
  // NEW: WebAuthn fields
  authenticatorData: string  // hex-encoded
  clientDataJSON: string     // raw JSON string
}
```

### 2. auth.ts: add computeWebAuthnHash function
```typescript
export async function computeWebAuthnHash(
  authenticatorData: string,  // hex
  clientDataJSON: string      // raw JSON
): Promise<string> {
  // Step 1: clientDataHash = SHA-256(clientDataJSON)
  const encoder = new TextEncoder()
  const clientDataBytes = encoder.encode(clientDataJSON)
  const clientDataHash = await crypto.subtle.digest('SHA-256', clientDataBytes)
  
  // Step 2: signedData = authenticatorData || clientDataHash
  const authBytes = hexToBytes(authenticatorData)
  const signedData = new Uint8Array(authBytes.length + 32)
  signedData.set(authBytes, 0)
  signedData.set(new Uint8Array(clientDataHash), authBytes.length)
  
  // Step 3: messageHash = SHA-256(signedData)
  const messageHash = await crypto.subtle.digest('SHA-256', signedData)
  return Array.from(new Uint8Array(messageHash))
    .map(b => b.toString(16).padStart(2, '0')).join('')
}
```

### 3. auth.ts: add verifyWebAuthnSignature (CORRECTED — FACT, not assumption)

**PROVEN by node test:** `crypto.subtle.verify({hash:'SHA-256'}, pubKey, sig, signedData)` computes
`SHA-256(signedData)` internally — matches Solidity `_verifyWebAuthn` exactly.

```typescript
// ponytail: WebAuthn signature verification — matches Solidity _verifyWebAuthn
// FACT: crypto.subtle.verify with hash:'SHA-256' computes SHA-256(input) internally
export async function verifyWebAuthnSignature(
  authenticatorData: Uint8Array,  // raw bytes from WebAuthn assertion
  clientDataJSON: Uint8Array,     // raw bytes (UTF-8 of JSON string)
  signatureR: string,
  signatureS: string,
  pubKeyX: string,
  pubKeyY: string,
): Promise<boolean> {
  try {
    // Step 1: clientDataHash = SHA-256(clientDataJSON) — Solidity L649
    const clientDataHash = await crypto.subtle.digest('SHA-256', clientDataJSON)
    
    // Step 2: signedData = authenticatorData || clientDataHash — Solidity L654-659
    const signedData = new Uint8Array(authenticatorData.length + 32)
    signedData.set(authenticatorData, 0)
    signedData.set(new Uint8Array(clientDataHash), authenticatorData.length)
    
    // Step 3: verify ECDSA P-256 — Solidity L662-664
    const keyBytes = new Uint8Array(65)
    keyBytes[0] = 0x04
    keyBytes.set(hexToBytes(pubKeyX), 1)
    keyBytes.set(hexToBytes(pubKeyY), 33)
    const publicKey = await crypto.subtle.importKey('raw', keyBytes, {name: 'ECDSA', namedCurve: 'P-256'}, false, ['verify'])
    const rawSig = new Uint8Array(64)
    rawSig.set(hexToBytes(signatureR), 0)
    rawSig.set(hexToBytes(signatureS), 32)
    
    // CRITICAL: pass signedData (NOT messageHash!) — verify computes SHA-256(signedData) internally
    return await crypto.subtle.verify({name: 'ECDSA', hash: 'SHA-256'}, publicKey, rawSig, signedData)
  } catch {
    return false
  }
}
```

### 4. index.ts: update accept handler
Replace L192-196:
```typescript
// OLD: verifyP256Signature(`accept:${inviteId}:${invite.walletAddress}`, ...)
// NEW: verifyWebAuthnSignature with raw bytes
const authDataBytes = hexToBytes(body.authenticatorData)
const clientDataBytes = new TextEncoder().encode(body.clientDataJSON)
const valid = await verifyWebAuthnSignature(
  authDataBytes,
  clientDataBytes,
  body.signatureR, body.signatureS,
  invite.guardianPubKeyX, invite.guardianPubKeyY,
)
```

## CRITICAL: Hash order must be EXACTLY
1. clientDataHash = SHA-256(clientDataJSON)
2. signedData = authenticatorData || clientDataHash
3. verifyP256 computes SHA-256(signedData) internally
See contracts/src/SocialRecoveryModule.sol L637-664 for reference.

## Verification (fixture)
```
node verify-fixture.ts → verified: true
relay/src/__tests__/fixtures/webauthn-accept.json — exact hex values
```

## Output
Return the 3 modified files (types.ts, auth.ts, index.ts).
```

**Anti-hallucination protocol:**
1. Читаю auth.ts — проверяю что `verifyWebAuthnSignature` принимает `Uint8Array` (НЕ string)
2. Читаю index.ts — проверяю что `hexToBytes(body.authenticatorData)` и `new TextEncoder().encode(body.clientDataJSON)` передаются в `verifyWebAuthnSignature`
3. Проверяю порядок: `signedData = authenticatorData || SHA-256(clientDataJSON)` — НЕ наоборот!
4. **KEY CHECK:** `crypto.subtle.verify({hash:'SHA-256'}, pubKey, sig, signedData)` — передаём `signedData`, НЕ `messageHash`!
5. Запускаю `cd relay && npx vitest run` — должны пройти существующие тесты + новые

---

### T1b: App Coder — Challenge Encoding + Extended Signature

**Агент:** coder
**Модель:** deepseek-v4-flash-free
**Риск:** ВЫСОКИЙ — challenge encoding mismatch между Android и relay
**Файлы:** `app/.../PasskeyManager.kt`, `app/.../GuardianManager.kt`, `app/.../RelayClient.kt`

**Prompt (≤2000 токенов):**
```
## Task
Fix accept flow: use inviteId as challenge, send authenticatorData + clientDataJSON to relay.

## Files in scope
- app/.../core/security/PasskeyManager.kt
- app/.../core/guardian/GuardianManager.kt
- app/.../core/guardian/RelayClient.kt

## Fixture (shared source of truth)
Use exact values from relay/src/__tests__/fixtures/webauthn-accept.json

## Changes needed

### 1. PasskeyManager.kt: add acceptWithInviteId method
```kotlin
// ponytail: inviteId as WebAuthn challenge — relay verifies the same hash
suspend fun acceptWithInviteId(inviteId: String): Result<PasskeyAuthResult> {
    val challenge = inviteId.toByteArray(Charsets.UTF_8)  // raw bytes, NOT base64
    val evalInput = ByteArray(32).also { secureRandom.nextBytes(it) }
    return authenticateWithPasskey(challenge, evalInput)
}
```

### 2. PasskeyManager.kt: update buildAuthJson to accept custom challenge
Already takes `challenge: ByteArray` — just ensure challenge encoding is correct:
```kotlin
// buildAuthJson already does: Base64.encodeToString(challenge, URL_SAFE | NO_PADDING | NO_WRAP)
// This is CORRECT for WebAuthn — relay must match this encoding
```

### 3. GuardianManager.kt: update acceptInvite
```kotlin
suspend fun acceptInvite(inviteId: String, userId: String): Result<Unit> {
    // ... existing invite fetch ...
    
    // NEW: use inviteId as challenge (NOT random)
    val authResult = passkeyManager.acceptWithInviteId(inviteId)
    val authData = authResult.getOrElse { return authResult.map { } }
    val assertion = GuardianUserOpBuilder.extractWebAuthnAssertion(authData.authenticationJson)
        ?: return Result.failure(IllegalStateException("Failed to extract WebAuthn assertion"))
    
    // ... existing proof building ...
    
    // NEW: send authenticatorData + clientDataJSON to relay
    relayClient.acceptInvite(
        inviteId = inviteId,
        signatureR = signatureR,
        signatureS = signatureS,
        guardianIdentityHash = guardianIdentityHash,
        authenticatorData = assertion.authenticatorData,  // hex string
        clientDataJSON = assertion.clientDataJSON,         // raw JSON string
    )
}
```

### 4. RelayClient.kt: update acceptInvite signature
```kotlin
suspend fun acceptInvite(
    inviteId: String,
    signatureR: String,
    signatureS: String,
    guardianIdentityHash: String,
    authenticatorData: String,  // hex-encoded
    clientDataJSON: String,     // raw JSON string
): Result<Unit> {
    val body = json.encodeToString(mapOf(
        "signatureR" to signatureR,
        "signatureS" to signatureS,
        "guardianIdentityHash" to guardianIdentityHash,
        "authenticatorData" to authenticatorData,
        "clientDataJSON" to clientDataJSON,
    ))
    return post("/guardian/invite/$inviteId/accept", body)
}
```

## CRITICAL: Challenge encoding
- `challenge = inviteId.toByteArray(Charsets.UTF_8)` — raw UTF-8 bytes
- `buildAuthJson` does `Base64.encodeToString(challenge, URL_SAFE | NO_PADDING | NO_WRAP)`
- This produces: `"challenge": "NTUwZTg0MDAtZTI5Yi00MWQ0LWE3MTYtNDQ2NjU1NDQwMDAw"` (for UUID)
- Relay receives this in clientDataJSON and must base64url-decode it back to raw bytes for comparison

## Output
Return the 3 modified files.
```

**Anti-hallucination protocol:**
1. Читаю PasskeyManager.kt — проверяю что `acceptWithInviteId` использует `inviteId.toByteArray()` (НЕ base64!)
2. Читаю GuardianManager.kt — проверяю что передаёт authenticatorData + clientDataJSON
3. Читаю RelayClient.kt — проверяю что acceptInvite принимает 6 параметров (не 4)
4. Запускаю `./gradlew :app:compileDevDebugKotlin -PBUNDLER_URL_DEV=http://bundler.local:4337` — должен компилироваться
5. **KEY CHECK:** Проверяю что `clientDataJSON` из Android содержит `"challenge": "base64url(inviteId)"` — это то что relay будет хешировать

---

### T1c: Relay Tester — WebAuthn Fixture Tests

**Агент:** tester
**Модель:** deepseek-v4-flash-free
**Риск:** СРЕДНИЙ — тесты могут тестировать не то
**Файлы:** `relay/src/__tests__/invite.test.ts`

**Prompt (≤2000 токенов):**
```
## Task
Update invite tests to use real WebAuthn hash verification with fixture.

## Files in scope
- relay/src/__tests__/invite.test.ts
- relay/src/__tests__/fixtures/webauthn-accept.json

## Fixture
Load from webauthn-accept.json — exact hex values.

## Tests needed

### Test 1: verifyWebAuthnSignature returns true for valid fixture
- Load fixture from webauthn-accept.json
- Call `verifyWebAuthnSignature(authenticatorDataBytes, clientDataJSONBytes, r, s, pubKeyX, pubKeyY)`
- Assert result === true (byte-exact match with Solidity contract)

### Test 2: accept with valid WebAuthn assertion → 200
- Mock KV storage with invite
- POST /guardian/invite/:id/accept with fixture values
- Assert 200 + accepted: true

### Test 3: accept with wrong authenticatorData → 401
- Same as Test 2 but flip one byte in authenticatorData
- Assert 401 Invalid guardian signature

### Test 4: accept with wrong clientDataJSON → 401
- Same as Test 2 but change challenge in clientDataJSON
- Assert 401 Invalid guardian signature

## CRITICAL: Do NOT mock verifyP256Signature
The whole point is to test the REAL hash pipeline.
Mock only KV storage and push notifications.

## Output
Return the modified test file.
```

**Anti-hallucination protocol:**
1. Читаю тесты — проверяю что НЕТ `vi.mock('../auth')` в новых тестах (хеш должен быть реальным)
2. Проверяю что fixture загружается и messageHash сравнивается byte-exact
3. Запускаю `cd relay && npx vitest run` — все тесты должны пройти

---

### T1d: Coordinator Verification — Bytecode-Level

**Агент:** coordinator (я сам)
**Модель:** big-pickle (текущая)
**Риск:** НИЗКИЙ — ручная проверка
**Что проверяю:**

1. **Challenge encoding:**
   - Android: `inviteId.toByteArray()` → `Base64.encodeToString(URL_SAFE|NO_PADDING)` → в clientDataJSON.challenge
   - Relay: `clientDataJSON` → `SHA-256(clientDataJSON)` → clientDataHash
   - **ПРОВЕРКА:** Кодировка в clientDataJSON одинаковая с обеих сторон

2. **Hash order:**
   - Contract: `SHA-256(authenticatorData || SHA-256(clientDataJSON))`
   - Relay: `SHA-256(authenticatorData || SHA-256(clientDataJSON))`
   - **ПРОВЕРКА:** Порядок concat: authenticatorData ПЕРВЫЙ, clientDataHash ВТОРОЙ

3. **Signature format:**
   - App: `signatureR = sig[0..32]`, `signatureS = sig[32..64]` (hex)
   - Relay: `hexToBytes(signatureR)`, `hexToBytes(signatureS)` → `crypto.subtle.verify`
   - **ПРОВЕРКА:** Relay правильно декодирует r,s из hex

4. **Full build:**
   - `cd relay && npx vitest run` — все тесты проходят
   - `./gradlew :app:compileDevDebugKotlin -PBUNDLER_URL_DEV=http://bundler.local:4337` — BUILD SUCCESSFUL

---

### T2: Fail-Fast CHAIN_ID for Release Builds

**Агент:** coder
**Модель:** deepseek-v4-flash-free
**Риск:** НИЗКИЙ — простое изменение в build.gradle.kts
**Файлы:** `app/build.gradle.kts`

**Prompt (≤2000 токенов):**
```
## Task
Add CHAIN_ID fail-fast for release builds, same pattern as BUNDLER_URL fail-fast.

## File in scope
- app/build.gradle.kts

## Pattern to follow (from L218-227)
```kotlin
tasks.matching { it.name.matches(Regex("generate(Dev|Staging|Prod)(Debug|Release)BuildConfig")) }.configureEach {
    doFirst {
        val flavor = name.removePrefix("generate").removeSuffix("BuildConfig")
            .removeSuffix("Debug").removeSuffix("Release")
        // EXISTING: BUNDLER_URL check
        val bundlerProp = "BUNDLER_URL_${flavor.uppercase()}"
        requireNotNull(project.findProperty(bundlerProp)) { ... }
        // NEW: CHAIN_ID check for release only
        if (name.contains("Release")) {
            val chainProp = "CHAIN_ID_${flavor.uppercase()}"
            val chainId = project.findProperty(chainProp) as? String
            requireNotNull(chainId) {
                "$chainProp is required for ${flavor}Release build. Pass -P$chainProp=..."
            }
            // Warn if deploying to mainnet
            if (chainId.toLongOrNull() == 56L) {
                println("⚠️ WARNING: CHAIN_ID=56 (BSC Mainnet) — ensure this is intentional")
            }
        }
    }
}
```

## Output
Return the modified build.gradle.kts.
```

---

### T4: NavGraph Route Check (grep)

**Агент:** coordinator (я сам)
**Модель:** big-pickle (текущая)
**Риск:** НИЗКИЙ — grep check

**Что делаю:**
1. `grep -r "composable(" app/src/main/java/com/mdaopay/app/ui/navigation/ | wc -l` — количество роутов
2. `grep -r "NavHost" app/src/main/java/com/mdaopay/app/ui/navigation/` — точка входа
3. Проверяю что все роуты зарегистрированы в NavHost
4. Если есть роуты без навигации → помечаю

---

### T5-T8: Build / Test / Deploy (операционная работа)

**T5:** `./gradlew assembleDevDebug -PBUNDLER_URL_DEV=... -PCHAIN_ID=97` → APK
**T6:** Ручной чек-лист на устройстве
**T7:** `cd contracts && forge test -vv` → все тесты проходят
**T8:** Деплой на BSC Testnet (testnet faucets, bridge)

---

## 4. ПОСЛЕДОВАТЕЛЬНОСТЬ ВЫПОЛНЕНИЯ

```
T3 (gradle check) ──→ T3.5 (fixture) ──→ T1a (relay coder)
                                          ──→ T1b (app coder)   ← ПОСЛЕ T1a!
                                          ──→ T1c (relay tester) ← ПОСЛЕ T1a!
                              T1d (verification) ← ПОСЛЕ T1a+T1b+T1c
                              T2 (fail-fast) ← НЕЗАВИСИМО от T1
                              T4 (NavGraph) ← НЕЗАВИСИМО от T1
                              T5-T8 (build/deploy) ← ПОСЛЕ T1d+T2+T4
```

**Критичный путь:** T3 → T3.5 → T1a → T1b → T1d → T5 → T8

---

## 5. RISK REGISTER

| Риск | Вероятность | Влияние | Mitigation |
|------|-------------|---------|------------|
| Deepseek галлюцинирует хеш-порядок | ВЫСОКАЯ | КРИТИЧЕСКОЕ | T3.5 fixture + T1d verification |
| Challenge encoding mismatch (Android vs relay) | СРЕДНЯЯ | КРИТИЧЕСКОЕ | T1d bytecode-level check |
| relay vitest не покрывает WebAuthn path | СРЕДНЯЯ | ВЫСОКОЕ | T1c добавляет 4 реальных теста |
| big-pickle падает при verification | СРЕДНЯЯ | СРЕДНЕЕ | Fallback на deepseek |
| Gradle build ломается после S28 удаления | НИЗКАЯ | ВЫСОКОЕ | T3 clean build |
| NavGraph имеет невалидные роуты | НИЗКАЯ | СРЕДНЕЕ | T4 grep check |

---

## 6. STOP CONDITIONS

- T3.5 fixture НЕ совпадает с контрактным хешем → STOP, пересчитать
- T1a relay не компилируется → STOP, исправить
- T1b app не компилируется → STOP, исправить
- T1d hash order mismatch → STOP, переделать T1a и T1b
- T1c тесты не проходят → STOP, исправить
- forge test падает → STOP, не деплоить

---

## 7. SELF-CHALLENGE

1. **Что в моём решении может оказаться неправильным?**
   - ~~Relay `verifyP256Signature` принимает message как string~~ → ИСПРАВЛЕНО: новая функция `verifyWebAuthnSignature` принимает `Uint8Array` напрямую
   - `inviteId.toByteArray()` ≠ `base64url(inviteId)` — если Android CredentialManager ожидает base64url, а мы передаём raw UTF-8, challenge будет другим. НО: `buildAuthJson` делает base64url编码, поэтому это ОК.

2. **Какой самый сильный аргумент против?**
   - Мы не можем протестировать end-to-end на реальном Android устройстве в этом плане. T1c тестирует relay side, но app side проверяется только компиляцией. Полный e2e только на T6 (ручной тест).

3. **Есть ли вариант проще?**
   - Можно было бы сделать relay верификацию через plain string (как сейчас), заставив app подписывать "accept:inviteId:walletAddr". Но это:
     - Несовместимо с контрактом (SRM._verifyWebAuthn)
     - Теряет security properties WebAuthn
     - Требует отдельного signing path для relay vs on-chain

4. **Что я предположил без доказательств?** (обновлено после верификации)
   - ~~`crypto.subtle.digest('SHA-256', ...)` доступен в CF Workers~~ → **FACT** (used in auth.ts hmacSha256)
   - ~~`crypto.subtle.verify` принимает `Uint8Array`~~ → **FACT** (node test: `verify with Uint8Array: true`)
   - ~~`hash: 'SHA-256'` в verify означает "verify computes SHA-256(input) internally"~~ → **FACT** (node test: verified round-trip)
   - ~~Android CredentialManager принимает raw UTF-8 challenge~~ → **OBSERVATION** (buildAuthJson L245: `Base64.encodeToString(challenge, URL_SAFE | NO_PADDING)`)

## 8. ДОКАЗАННЫЕ ФАКТЫ (verification log)

| # | Утверждение | Статус | Доказательство |
|---|-------------|--------|----------------|
| F1 | `crypto.subtle.digest('SHA-256', ...)` работает | **FACT** | auth.ts L65, node test |
| F2 | `crypto.subtle.verify` принимает `Uint8Array` | **FACT** | node test: `verify with Uint8Array: true` |
| F3 | `hash: 'SHA-256'` в verify = SHA-256(input) internally | **FACT** | node test: round-trip signed+verified |
| F4 | `signedData = authenticatorData \|\| SHA-256(clientDataJSON)` = 69 bytes | **FACT** | fixture: signedDataLength: 69 |
| F5 | `messageHash = SHA-256(signedData)` matches Solidity | **FACT** | fixture: verified: true |
| F6 | `Base64.encodeToString(challenge, URL_SAFE)` = base64url | **FACT** | Android Base64 API spec |
| F7 | `inviteId.toByteArray()` = UTF-8 bytes | **FACT** | Kotlin stdlib |
| F8 | `TextEncoder().encode(clientDataJSON)` = UTF-8 bytes | **FACT** | Web Crypto API spec |
| F9 | relay builds `BUILD SUCCESSFUL` | **FACT** | vitest 32/33 (1 pre-existing) |
| F10 | app builds `BUILD SUCCESSFUL` | **FACT** | gradle compileDevDebugKotlin |
