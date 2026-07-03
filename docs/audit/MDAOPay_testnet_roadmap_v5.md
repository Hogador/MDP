# MDAOPay — Дорожная карта v5 (исправления технических решений)

> **Дата:** 2026-07-02
> **Версия:** v5 — исправлены 5 критических багов в коде + 4 пробела из code review v4
> **Базируется на:** v4 + code review v4
> **Статус:** 🔴 NO-GO (6 blocker'ов подтверждены кодом)

---

## 0. Executive Summary

### Что исправлено в v5 vs v4

| # | Проблема v4 | Категория | Исправление v5 |
|---|-------------|-----------|-----------------|
| 1 | `MessageHashUtils.hashEthereumMessage` — OZ Solidity, не Kotlin | 🔴 CRITICAL | `Hash.sha3` с ручным EIP-191 prefix |
| 2 | `Sign.recoverFromSignature` с v (27/28) вместо recId (0/1) | 🔴 CRITICAL | Конвертация v→recId: `v==27||v==0 → 0; v==28||v==1 → 1` |
| 3 | `Keys.getAddress()` без `0x` prefix | 🔴 CRITICAL | `"0x" + Keys.getAddress(key).lowercase()` |
| 4 | `WatchtowerMetrics` не thread-safe | 🔴 CRITICAL | `AtomicLong` / `AtomicInteger` |
| 5 | FCL import path неверный | 🔴 CRITICAL | Daimo P256Verifier (drop-in precompile replacement) |
| 6 | Phase 3.2 JWT_SECRET inline | 🟠 MAJOR | Убрать inline, только через AWS SM |
| 7 | `parseSiweMessage` — пустая заглушка | 🟠 MAJOR | Полная реализация EIP-4361 парсера |
| 8 | Soak Day 6 — нереалистичен без F-059 fix | 🟠 MAJOR | Caveat: write ops — PENDING F-059 |
| 9 | Appendix A Item 15 — ✅ вместо ⚠️ | 🟡 MINOR | Исправлено на ✅ (Gnosis Safe — proposer, проверено кодом) |
| 10 | `msg.sender` до `startBroadcast` ненадёжен | 🟡 MINOR | `vm.startBroadcast()` перед чтением `msg.sender` |
| 11 | R-15/R-16 — факты, не риски | 🔵 IMPROVEMENT | Заменить на реальные неопределённости |
| 12 | `TRUSTED_SIGNER` не в AppConfig | 🟠 MAJOR | Добавить `trustedSignerAddress` field |

### Что в критике v4 было ошибочным

| Пункт критики | Проверка кодом | Результат |
|---------------|----------------|-----------|
| #9: "Gnosis Safe не добавлен как proposer Timelock" | `Deploy.s.sol:114-118` → `proposers[0] = gnosisSafe` | ❌ **Критика неверна** — Safe уже proposer |

### Несоответствие в самой критике

**Пункт #9 критики** утверждает, что Gnosis Safe не добавлен как proposer. Проверка кода:

```solidity
// Deploy.s.sol:110-118
address gnosisSafe = vm.envOr("GNOSIS_SAFE", deployer);
address[] memory proposers = new address[](1);
proposers[0] = gnosisSafe;  // ← Gnosis Safe IS proposer
address[] memory executors = new address[](1);
executors[0] = gnosisSafe;  // ← Gnosis Safe IS executor
TimelockController timelock = new TimelockController(2 days, proposers, executors, address(0));
```

**Вывод:** Gnosis Safe добавлен как proposer И executor. Статус в Appendix A — `✅` корректен. Critical #9 — **ошибочное замечание**.

---

## Phase 0 — CRITICAL Blockers (исправленные технические решения)

### 0.1 [CRITICAL] P-256 verifier — Daimo P256Verifier (рекомендуется) вместо FCL

**Состояние кода:** ❌ FCL не существует. RIP-7212 на BSC отсутствует.

**Техническое решение — Daimo P256Verifier** (предпочтительнее FCL):

**Почему Daimo, а не FCL:**
1. **Drop-in precompile replacement** — `fallback(bytes)` принимает тот же формат, что RIP-7212 (160 bytes input → 32 bytes output)
2. **Audited** — используется в production на Base, Optimism
3. **Активно поддерживается** — последний commit 2026, FCL — 2024
4. **Проще интеграция** — не нужно менять логику контракта, только заменить адрес `P256_VERIFIER`

**Установка:**

```bash
cd contracts
git submodule add https://github.com/daimo-eth/p256-verifier.git lib/p256-verifier
```

**Структура Daimo (проверено clone):**
```
p256-verifier/
  src/
    P256Verifier.sol          ← основной файл
    utils/Base64URL.sol
  test/
    P256Verifier.t.sol
    WebAuthn.t.sol            ← пример WebAuthn верификации
  script/
    Deploy.s.sol
```

**Интеграция в SocialRecoveryModule.sol** — минимальные изменения:

```solidity
// SocialRecoveryModule.sol — оставить P256_VERIFIER pattern, только деплоить Daimo вместо MockP256

// Deploy.s.sol — заменить MockP256 fallback на Daimo P256Verifier
import {P256Verifier} from "lib/p256-verifier/src/P256Verifier.sol";

contract Deploy is Script {
    function run() external {
        // ... existing chain ID check ...

        // ── P-256 verifier setup ──
        (bool precompileOk, bytes memory precompileData) = address(0x100).staticcall(
            abi.encodePacked(bytes32(0), bytes32(0), bytes32(0), bytes32(0), bytes32(0))
        );
        bool rip7212Available = precompileOk && precompileData.length >= 32;

        address p256Verifier;
        if (rip7212Available) {
            p256Verifier = address(0x100);
            console.log("Using RIP-7212 P-256 precompile at 0x100");
        } else {
            // ИСПОЛЬЗОВАТЬ Daimo P256Verifier ВМЕСТО MockP256
            P256Verifier verifier = new P256Verifier();
            p256Verifier = address(verifier);
            console.log("Deployed Daimo P256Verifier at:", address(verifier));
        }

        // ... rest unchanged (SocialRecoveryModule still uses P256_VERIFIER address) ...
    }
}
```

**Преимущество:** SocialRecoveryModule.sol **не требует изменений** — он уже использует `P256_VERIFIER.staticcall(abi.encodePacked(messageHash, r, s, pubKeyX, pubKeyY))`, что совместимо с Daimo `fallback(bytes)`.

**Альтернатива — FCL** (если Daimo не подходит):

```bash
git submodule add https://github.com/rdubois-crypto/FreshCryptoLib.git lib/FreshCryptoLib
# Реальная структура (проверено clone):
# FreshCryptoLib/solidity/src/FCL_Webauthn.sol  ← WebAuthn-specific
# FreshCryptoLib/solidity/src/FCL_ecdsa.sol     ← low-level ECDSA
```

```solidity
import {FCL_Webauthn} from "lib/FreshCryptoLib/solidity/src/FCL_Webauthn.sol";

function _verifyWebAuthn(...) internal view returns (bool) {
    // FCL_Webauthn имеет другой API — требует изменения логики контракта
    return FCL_Webauthn.WebAuthn_verify(authenticatorData, clientDataJSON, signature, pubKeyX, pubKeyY);
}
```

**Рекомендация:** Daimo — проще (drop-in), FCL — больше функций но требует рефакторинга.

**Оценка:** 1-2 недели (Daimo) / 2 недели + рефакторинг (FCL).

**Verification:**
- [ ] `git submodule status` показывает p256-verifier
- [ ] `forge test --match-test test_VerifyWebAuthn_RealSignature` с реальным WebAuthn assertion
- [ ] Gas measurement: Daimo ~150k, FCL ~150-200k
- [ ] On-chain: `cast call $SOCIAL_RECOVERY` с реальным P-256 sig → `true`
- [ ] MockP256 удалён из production path (оставить только для unit tests)

---

### 0.2 [CRITICAL] SIWE auth — исправленный Kotlin код

**Состояние кода:** ❌ SIWE полностью отсутствует.

**Техническое решение — исправленный AuthService.kt:**

#### 0.2.1 Migration V5__siwe_auth.sql

```sql
ALTER TABLE auth_users ADD COLUMN wallet_address TEXT;
CREATE UNIQUE INDEX idx_auth_users_wallet ON auth_users(wallet_address) WHERE wallet_address IS NOT NULL;

CREATE TABLE siwe_nonces (
    nonce VARCHAR(64) PRIMARY KEY,
    wallet_address TEXT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE
);
CREATE INDEX idx_siwe_nonces_expires ON siwe_nonces(expires_at);
```

#### 0.2.2 AuthService.kt — исправленная реализация

```kotlin
package com.mdaopay.paymaster

import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import org.web3j.crypto.ECDSASignature
import org.web3j.crypto.Hash
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Serializable
data class SiweMessage(
    val domain: String,
    val address: String,
    val statement: String?,
    val uri: String,
    val version: String,
    val chainId: Long,
    val nonce: String,
    val issuedAt: String,
    val expirationTime: String?,
    val requestId: String?,
)

class AuthService(
    private val repo: AuthRepository,
    private val jwtSecret: String,
    private val config: SiweConfig,
    private val accessTtlMin: Long = 15,
    private val refreshTtlDays: Long = 30,
) {
    data class SiweConfig(
        val expectedDomain: String,        // e.g. "mdaopay.com"
        val expectedChainId: Long,         // 97 for testnet, 56 for mainnet
        val nonceTtlMinutes: Long = 5,
    )

    // ✅ ИСПРАВЛЕНО: правильная генерация nonce
    fun generateSiweNonce(walletAddress: String): String {
        require(walletAddress.matches(Regex("^0x[a-fA-F0-9]{40}$"))) { "Invalid address" }
        val bytes = ByteArray(16)  // 128 bits
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }  // 32-char hex
    }

    fun signInWithEthereum(message: String, signature: String): Result<TokenPair> {
        // 1. Парсить SIWE message
        val siwe = parseSiweMessage(message)
            ?: return Result.failure(IllegalArgumentException("Invalid SIWE message format"))

        // 2. Валидация полей
        if (siwe.domain != config.expectedDomain) {
            return Result.failure(IllegalArgumentException(
                "Domain mismatch: expected=${config.expectedDomain} got=${siwe.domain}"
            ))
        }
        if (siwe.expirationTime != null) {
            try {
                val exp = Instant.parse(siwe.expirationTime)
                if (exp.isBefore(Instant.now())) {
                    return Result.failure(IllegalArgumentException("Message expired"))
                }
            } catch (e: Exception) {
                return Result.failure(IllegalArgumentException("Invalid expiration time format"))
            }
        }
        if (siwe.chainId != config.expectedChainId) {
            return Result.failure(IllegalArgumentException(
                "Wrong chain: expected=${config.expectedChainId} got=${siwe.chainId}"
            ))
        }

        // 3. Проверить nonce (single-use, atomically consumed)
        val nonceValid = repo.consumeSiweNonce(siwe.nonce, siwe.address)
        if (!nonceValid) {
            return Result.failure(IllegalArgumentException("Invalid or already used nonce"))
        }

        // 4. ✅ ИСПРАВЛЕНО: EIP-191 recover с правильным prefix
        val signerAddress = recoverEthereumSigner(message, signature)
            ?: return Result.failure(IllegalArgumentException("Invalid signature"))

        // 5. ✅ ИСПРАВЛЕНО: сравнение с 0x prefix
        if (!signerAddress.equals(siwe.address, ignoreCase = true)) {
            return Result.failure(IllegalArgumentException(
                "Signature mismatch: recovered=$signerAddress expected=${siwe.address}"
            ))
        }

        // 6. Найти или создать пользователя
        val user = repo.findByWalletAddress(signerAddress) ?: repo.createWithWallet(signerAddress)
        return Result.success(issueTokens(user.id, wallet = signerAddress))
    }

    /**
     * ✅ ИСПРАВЛЕНО: Web3j EIP-191 recovery
     *
     * 1. Hash = keccak256("\x19Ethereum Signed Message:\n" + len + message)
     * 2. Parse signature: r(32) + s(32) + v(1)
     * 3. Convert v (27/28) → recId (0/1)
     * 4. Sign.recoverFromSignature(recId, sig, digest)
     * 5. Keys.getAddress(key) → "0x" + lowercase hex
     */
    private fun recoverEthereumSigner(message: String, hexSignature: String): String? {
        return try {
            val sigBytes = Numeric.hexStringToByteArray(hexSignature.removePrefix("0x"))
            require(sigBytes.size == 65) { "Expected 65-byte signature, got ${sigBytes.size}" }

            val r = BigInteger(1, sigBytes.copyOfRange(0, 32))
            val s = BigInteger(1, sigBytes.copyOfRange(32, 64))
            val v = sigBytes[64].toInt() and 0xFF

            // ✅ ИСПРАВЛЕНО: конвертация v (Ethereum 27/28) → recId (0/1)
            val recId = when (v) {
                27, 0 -> 0
                28, 1 -> 1
                else -> {
                    log.warn("Invalid v value: {}", v)
                    return null
                }
            }

            // ✅ ИСПРАВЛЕНО: ручной EIP-191 prefix (НЕ MessageHashUtils — это Solidity)
            val msgBytes = message.toByteArray(Charsets.UTF_8)
            val prefix = "\u0019Ethereum Signed Message:\n${msgBytes.size}".toByteArray()
            val digest = Hash.sha3(prefix + msgBytes)

            val key = Sign.recoverFromSignature(recId, ECDSASignature(r, s), digest)
                ?: return null

            // ✅ ИСПРАВЛЕНО: Keys.getAddress возвращает hex без 0x, добавляем prefix
            "0x" + Keys.getAddress(key).lowercase()
        } catch (e: Exception) {
            log.warn("Signature recovery failed: ${e.message}")
            null
        }
    }

    /**
     * ✅ ИСПРАВЛЕНО: полная реализация EIP-4361 парсера
     *
     * Формат:
     *   {domain} wants you to sign in with your Ethereum account:
     *   {address}
     *
     *   {statement}  ← optional
     *
     *   URI: {uri}
     *   Version: {version}
     *   Chain ID: {chainId}
     *   Nonce: {nonce}
     *   Issued At: {issuedAt}
     *   [Expiration Time: {expirationTime}]
     *   [Request ID: {requestId}]
     */
    fun parseSiweMessage(raw: String): SiweMessage? = runCatching {
        val lines = raw.lines()
        require(lines.size >= 8) { "SIWE message too short" }

        // Line 0: "${domain} wants you to sign in with your Ethereum account:"
        val headerRegex = Regex("^(.+) wants you to sign in with your Ethereum account:$")
        val headerMatch = headerRegex.matchEntire(lines[0])
            ?: return@runCatching null
        val domain = headerMatch.groupValues[1]

        // Line 1: address
        val address = lines[1].trim()
        require(address.matches(Regex("^0x[a-fA-F0-9]{40}$"))) { "Invalid address: $address" }

        // Parse "Key: Value" fields, ignoring blank lines
        val fields = mutableMapOf<String, String>()
        var statement: String? = null
        var inStatement = false

        for (i in 2 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) {
                inStatement = true
                continue
            }
            // Check if line matches "Key: Value" pattern
            val colonIdx = line.indexOf(": ")
            if (colonIdx > 0 && !line.substring(0, colonIdx).contains(" ")) {
                // Looks like a field
                val key = line.substring(0, colonIdx)
                val value = line.substring(colonIdx + 2)
                fields[key] = value
                inStatement = false
            } else if (inStatement) {
                // Part of statement
                statement = if (statement == null) line else "$statement\n$line"
            }
        }

        val uri = fields["URI"] ?: return@runCatching null
        val version = fields["Version"] ?: "1"
        val chainIdStr = fields["Chain ID"] ?: return@runCatching null
        val nonce = fields["Nonce"] ?: return@runCatching null
        val issuedAt = fields["Issued At"] ?: return@runCatching null

        val chainId = chainIdStr.toLongOrNull() ?: return@runCatching null

        SiweMessage(
            domain = domain,
            address = address,
            statement = statement,
            uri = uri,
            version = version,
            chainId = chainId,
            nonce = nonce,
            issuedAt = issuedAt,
            expirationTime = fields["Expiration Time"],
            requestId = fields["Request ID"],
        )
    }.getOrNull()

    // ... existing register/login/refresh/validateAccessToken methods unchanged ...

    private companion object {
        private val log = LoggerFactory.getLogger(AuthService::class.java)
    }
}
```

#### 0.2.3 Application.kt — routes

```kotlin
// Внутри route("/v1"), НО вне authenticate("auth-jwt") блока
get("/auth/siwe/nonce/{walletAddress}") {
    val walletAddress = call.parameters["walletAddress"] ?: ""
    if (!walletAddress.matches(Regex("^0x[a-fA-F0-9]{40}$"))) {
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid address"))
        return@get
    }
    val ip = extractClientIp(call.request)
    if (authIpRateLimiter.isLimited("siwe-nonce:$ip", AUTH_REGISTER_LIMIT, AUTH_WINDOW_SEC)) {
        call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "Rate limited"))
        return@get
    }
    val nonce = authService.generateSiweNonce(walletAddress)
    call.respond(mapOf("nonce" to nonce, "expiresIn" to 300))
}

post("/auth/siwe") {
    val req = call.receive<SiweAuthRequest>()
    val ip = extractClientIp(call.request)
    if (authIpRateLimiter.isLimited("siwe:$ip", AUTH_LOGIN_LIMIT, AUTH_WINDOW_SEC)) {
        call.respond(HttpStatusCode.TooManyRequests, mapOf("error" to "Rate limited"))
        return@post
    }
    authService.signInWithEthereum(req.message, req.signature).fold(
        onSuccess = { tokens -> call.respond(tokens) },
        onFailure = { err ->
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to (err.message ?: "Auth failed")))
        }
    )
}
```

#### 0.2.4 Mobile SiweAuthManager

```kotlin
class SiweAuthManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val walletManager: WalletManager,
) {
    suspend fun signInWithEthereum(): Result<TokenPair> = withContext(Dispatchers.IO) {
        val wallet = walletManager.getWalletData()
            ?: return@withContext Result.failure(IllegalStateException("No wallet"))
        val address = wallet.address

        // 1. Получить nonce
        val nonceResponse = okHttpClient.newCall(
            Request.Builder()
                .url("${BuildConfig.BACKEND_URL}/v1/auth/siwe/nonce/$address")
                .get().build()
        ).execute()
        if (!nonceResponse.isSuccessful) {
            return@withContext Result.failure(Exception("Nonce request failed: ${nonceResponse.code}"))
        }
        val nonce = JSONObject(nonceResponse.body!!.string()).getString("nonce")

        // 2. Сгенерировать SIWE message
        val message = buildSiweMessage(address, nonce)

        // 3. Подписать (Web3j Sign.signMessage добавляет EIP-191 prefix автоматически)
        val signatureData = Sign.signMessage(message.toByteArray(Charsets.UTF_8), wallet.keyPair)
        // signatureData.v — это 27 или 28 (Ethereum-style)
        val sigHex = "0x" +
            Numeric.toHexStringNoPrefix(signatureData.r) +
            Numeric.toHexStringNoPrefix(signatureData.s) +
            String.format("%02x", signatureData.v.last())

        // 4. Отправить
        val body = JSONObject().apply {
            put("message", message)
            put("signature", sigHex)
        }
        val response = okHttpClient.newCall(
            Request.Builder()
                .url("${BuildConfig.BACKEND_URL}/v1/auth/siwe")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
        ).execute()
        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: ""
            return@withContext Result.failure(Exception("SIWE failed: ${response.code} $errBody"))
        }
        val tokens = JSONObject(response.body!!.string())
        Result.success(TokenPair(
            accessToken = tokens.getString("accessToken"),
            refreshToken = tokens.getString("refreshToken"),
            expiresIn = tokens.getLong("expiresIn"),
        ))
    }

    private fun buildSiweMessage(address: String, nonce: String): String {
        val issuedAt = java.time.Instant.now().toString()
        val expirationTime = java.time.Instant.now()
            .plus(5, java.time.temporal.ChronoUnit.MINUTES).toString()
        return buildString {
            appendLine("${BuildConfig.SIWE_DOMAIN} wants you to sign in with your Ethereum account:")
            appendLine(address)
            appendLine()
            appendLine("Sign in to MDAOPay")
            appendLine()
            appendLine("URI: ${BuildConfig.SIWE_URI}")
            appendLine("Version: 1")
            appendLine("Chain ID: ${NetworkConfig.CHAIN_ID}")
            appendLine("Nonce: $nonce")
            appendLine("Issued At: $issuedAt")
            appendLine("Expiration Time: $expirationTime")
        }
    }
}
```

**Оценка:** 3-5 дней + 1 день буфер.

**Verification:**
- [ ] Unit test: valid signature → tokens (использует `Sign.signMessage` для генерации, `recoverEthereumSigner` для проверки)
- [ ] Unit test: invalid nonce → rejected
- [ ] Unit test: replay same nonce → rejected
- [ ] Unit test: wrong chain ID → rejected
- [ ] Unit test: v=27 и v=28 оба восстанавливаются корректно
- [ ] Fuzz test: `parseSiweMessage` 20+ variations (с/без statement, с/без requestId, multiline statement, special chars)
- [ ] E2E: пользователь без email → gasless send

---

### 0.3 [CRITICAL] AWS KMS — без изменений (корректно в v4)

См. v4 Phase 0.3. Реализация `KmsPaymasterSigner` с `parseDerSignature` корректна.

---

### 0.4 [CRITICAL] Deploy.s.sol fix — исправленный msg.sender

**Состояние кода:** ❌ `Deploy.s.sol:21` — `vm.envUint("DEPLOYER_PRIVATE_KEY")` конфликтует с `--private-key`.

**Техническое решение — исправленный Deploy.s.sol:**

```solidity
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {Script, console} from "forge-std/Script.sol";
import {MDAOToken} from "../src/MDAOToken.sol";
// ... other imports ...

contract Deploy is Script {
    function run() external {
        // ✅ ИСПРАВЛЕНО: НЕ использовать vm.envUint — конфликтует с --private-key
        // Foundry автоматически подставляет --private-key как broadcaster после startBroadcast

        // ── Chain ID validation (F-117) ──
        uint256 chainId = block.chainid;
        require(chainId == 56 || chainId == 97, "Unsupported chain ID (56=BSC, 97=BSC Testnet)");
        console.log("Chain ID:", chainId);

        // ── P-256 verifier setup (использовать Daimo, не MockP256) ──
        (bool precompileOk, bytes memory precompileData) = address(0x100).staticcall(
            abi.encodePacked(bytes32(0), bytes32(0), bytes32(0), bytes32(0), bytes32(0))
        );
        bool rip7212Available = precompileOk && precompileData.length >= 32;

        address p256Verifier;
        if (rip7212Available) {
            p256Verifier = address(0x100);
            console.log("Using RIP-7212 P-256 precompile at 0x100");
        } else {
            // ✅ Daimo P256Verifier вместо MockP256
            vm.startBroadcast();
            P256Verifier verifier = new P256Verifier();
            p256Verifier = address(verifier);
            vm.stopBroadcast();
            console.log("Deployed Daimo P256Verifier at:", address(verifier));
        }

        // ✅ ИСПРАВЛЕНО: startBroadcast ПЕРЕД чтением msg.sender
        // Внутри broadcast msg.sender = signer из --private-key
        vm.startBroadcast();
        address deployer = msg.sender;
        require(deployer != address(0), "No deployer — use --private-key flag");
        vm.stopBroadcast();

        console.log("Deployer:", deployer);

        // ── MDAOToken ──
        vm.startBroadcast();
        MDAOToken token = new MDAOToken(deployer);
        vm.stopBroadcast();
        console.log("MDAOToken:", address(token));

        // ── MDAOPaymaster ──
        vm.startBroadcast();
        MDAOPaymaster paymaster = new MDAOPaymaster(
            vm.envAddress("ENTRY_POINT"),
            address(token),
            vm.envAddress("USDT_ADDRESS"),
            vm.envAddress("TRUSTED_SIGNER")
        );
        vm.stopBroadcast();
        console.log("MDAOPaymaster:", address(paymaster));

        // ── ✅ ИСПРАВЛЕНО: InsuranceFund с auditor из env ──
        address auditor = vm.envAddress("INSURANCE_AUDITOR_ADDRESS");
        require(auditor != address(0), "INSURANCE_AUDITOR_ADDRESS not set");
        require(auditor != deployer, "Auditor must differ from deployer");

        vm.startBroadcast();
        InsuranceFund insuranceFund = new InsuranceFund(auditor);
        vm.stopBroadcast();
        console.log("InsuranceFund:", address(insuranceFund));
        console.log("Auditor:", auditor);

        // ... rest unchanged (SocialRecoveryModule, NicknameRegistry, etc.) ...
    }
}
```

**Альтернатива** (если не хотим `startBroadcast` для чтения `msg.sender`):

```solidity
function run() external {
    // Использовать vm.envUint для PRIVATE_KEY (НЕ DEPLOYER_PRIVATE_KEY)
    uint256 privateKey = vm.envUint("PRIVATE_KEY");
    address deployer = vm.addr(privateKey);

    require(block.chainid == 56 || block.chainid == 97, "Unsupported chain");

    // ... rest of deploy logic, using vm.startBroadcast(deployer) explicitly ...
    vm.startBroadcast(deployer);
    MDAOToken token = new MDAOToken(deployer);
    vm.stopBroadcast();
}
```

**В этом случае** forge script запускается БЕЗ `--private-key`:
```bash
PRIVATE_KEY=0x... forge script script/Deploy.s.sol --rpc-url $RPC_URL --broadcast
```

**Рекомендация:** первый вариант (`--private-key` + `startBroadcast` перед `msg.sender`) чище.

**Оценка:** 1 час код + 1 час тесты.

**Verification:**
- [ ] `forge script Deploy.s.sol --private-key $KEY --rpc-url $RPC_URL --broadcast` работает
- [ ] Без `--private-key` → понятная ошибка
- [ ] `INSURANCE_AUDITOR_ADDRESS` required
- [ ] `cast call $INSURANCE_FUND "auditor()(address)"` возвращает auditor

---

### 0.5 [CRITICAL] scripts/deploy-testnet.sh — без изменений (корректно в v4)

См. v4 Phase 0.5. Скрипт корректен, создаётся с нуля.

---

### 0.6 [CRITICAL] wrangler.toml — без изменений (корректно в v4)

См. v4 Phase 0.6. Настройка `[env.testnet]` секции.

---

### 0.7 [HIGH → CRITICAL] AppConfig — добавить trustedSignerAddress + 7 contract addresses

**Состояние кода:** ❌ `AppConfig.kt` имеет только 2 nullable contract addresses (nickname, recovery). `TRUSTED_SIGNER` есть в env, но не как `trustedSignerAddress` field.

**Техническое решение:**

```kotlin
// AppConfig.kt
data class AppConfig(
    // ... existing fields ...
    val paymasterAddress: String,
    val mdaoAddress: String,
    val usdtAddress: String,
    val wbnbAddress: String,
    val entryPoint: String,
    val trustedSigner: String,  // ← УЖЕ ЕСТЬ, но это required (для paymaster quote verification)

    // Добавить nullable contract addresses
    val nicknameRegistryAddress: String? = null,
    val recoveryModuleAddress: String? = null,
    val insuranceFundAddress: String? = null,
    val deadManSwitchAddress: String? = null,
    val sessionKeyModuleAddress: String? = null,
    val attestationLedgerAddress: String? = null,
    val trustProviderRegistryAddress: String? = null,
    val ecdsaVerifierAddress: String? = null,
    val timelockAddress: String? = null,

    // ... other existing fields ...
) {
    companion object {
        fun fromEnv(): AppConfig {
            // ... existing ...

            // ✅ ДОБАВИТЬ: 7 новых contract addresses
            val insuranceFundAddress = env["INSURANCE_FUND_ADDRESS"]
            val deadManSwitchAddress = env["DEAD_MAN_SWITCH_ADDRESS"]
            val sessionKeyModuleAddress = env["SESSION_KEY_MODULE_ADDRESS"]
            val attestationLedgerAddress = env["ATTESTATION_LEDGER_ADDRESS"]
            val trustProviderRegistryAddress = env["TRUST_PROVIDER_REGISTRY_ADDRESS"]
            val ecdsaVerifierAddress = env["ECDSA_VERIFIER_ADDRESS"]
            val timelockAddress = env["TIMELOCK_ADDRESS"]

            // Format validation for all addresses
            listOf(
                insuranceFundAddress, deadManSwitchAddress, sessionKeyModuleAddress,
                attestationLedgerAddress, trustProviderRegistryAddress,
                ecdsaVerifierAddress, timelockAddress
            ).forEach { addr ->
                if (addr != null && !ADDRESS_REGEX.matches(addr)) {
                    error("Invalid address format: $addr")
                }
            }

            return AppConfig(
                // ... existing ...
                insuranceFundAddress = insuranceFundAddress,
                deadManSwitchAddress = deadManSwitchAddress,
                sessionKeyModuleAddress = sessionKeyModuleAddress,
                attestationLedgerAddress = attestationLedgerAddress,
                trustProviderRegistryAddress = trustProviderRegistryAddress,
                ecdsaVerifierAddress = ecdsaVerifierAddress,
                timelockAddress = timelockAddress,
                // ...
            )
        }
    }
}
```

**Примечание:** `trustedSigner` уже существует в `AppConfig.kt:30` как required field — **критика #12 ошибочна**. Не нужно добавлять `trustedSignerAddress`, он уже есть под именем `trustedSigner`.

**Оценка:** 1 час.

**Verification:**
- [ ] 7 новых address fields добавлены
- [ ] `fromEnv()` читает все env vars
- [ ] Format validation
- [ ] Backend start с полным набором env vars → success

---

## Phase 1 — HIGH + Verification

### 1.1-1.5 — без изменений (корректно в v4)

См. v4 Phase 1.1-1.5.

### 1.6 — verify-findings.sh — без изменений

---

## Phase 2 — Hardening

### 2.1-2.2 — без изменений

### 2.3 [MEDIUM] Deep health endpoint — без изменений

См. v4 Phase 2.3.

### 2.4 [MEDIUM] Watchtower metrics — ИСПРАВЛЕННАЯ thread-safe реализация

**Состояние кода:** ❌ `Metrics.kt` экспортирует `mdao_*`, нет watchtower metrics.

**Техническое решение — thread-safe metrics:**

```kotlin
// Metrics.kt
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger

// ✅ ИСПРАВЛЕНО: AtomicLong/AtomicInteger для thread safety
class WatchtowerMetrics {
    val lastPollTimestamp = AtomicLong(0)
    val activeRecoveriesMonitored = AtomicInteger(0)
    val recoveryEventsDetected = AtomicLong(0)
    val webhooksSent = AtomicLong(0)
    val webhookFailures = AtomicLong(0)
    val balanceDropsDetected = AtomicLong(0)
}

// В appMetrics или как singleton
val watchtowerMetrics = WatchtowerMetrics()

// В prometheusText():
sb.appendLine("# HELP mdaopay_watchtower_last_poll_timestamp Unix timestamp of last poll")
sb.appendLine("# TYPE mdaopay_watchtower_last_poll_timestamp gauge")
sb.appendLine("mdaopay_watchtower_last_poll_timestamp ${watchtowerMetrics.lastPollTimestamp.get()}")

sb.appendLine("# HELP mdaopay_watchtower_active_recoveries Currently monitored active recoveries")
sb.appendLine("# TYPE mdaopay_watchtower_active_recoveries gauge")
sb.appendLine("mdaopay_watchtower_active_recoveries ${watchtowerMetrics.activeRecoveriesMonitored.get()}")

sb.appendLine("# HELP mdaopay_watchtower_events_total Total recovery events detected")
sb.appendLine("# TYPE mdaopay_watchtower_events_total counter")
sb.appendLine("mdaopay_watchtower_events_total ${watchtowerMetrics.recoveryEventsDetected.get()}")

sb.appendLine("# HELP mdaopay_watchtower_webhooks_total Total webhooks sent")
sb.appendLine("# TYPE mdaopay_watchtower_webhooks_total counter")
sb.appendLine("mdaopay_watchtower_webhooks_total ${watchtowerMetrics.webhooksSent.get()}")

sb.appendLine("# HELP mdaopay_watchtower_webhook_failures_total Total webhook failures")
sb.appendLine("# TYPE mdaopay_watchtower_webhook_failures_total counter")
sb.appendLine("mdaopay_watchtower_webhook_failures_total ${watchtowerMetrics.webhookFailures.get()}")

sb.appendLine("# HELP mdaopay_watchtower_balance_drops_total Total balance drops detected")
sb.appendLine("# TYPE mdaopay_watchtower_balance_drops_total counter")
sb.appendLine("mdaopay_watchtower_balance_drops_total ${watchtowerMetrics.balanceDropsDetected.get()}")
```

```kotlin
// WatchtowerService.kt — обновлять metrics thread-safely
private fun pollEvents() {
    // ... existing logic ...
    // ✅ ИСПРАВЛЕНО: atomic updates
    watchtowerMetrics.lastPollTimestamp.set(System.currentTimeMillis() / 1000)
    watchtowerMetrics.activeRecoveriesMonitored.set(activeRecoveries.size)
}

private fun handleRecoveryInitiated(log: Log) {
    // ... existing logic ...
    watchtowerMetrics.recoveryEventsDetected.incrementAndGet()
}

private fun notifyWebhook(event: String, data: Map<String, String>) {
    scope.launch {
        try {
            withTimeout(5000) {
                httpClient.post(url) { /* ... */ }
            }
            watchtowerMetrics.webhooksSent.incrementAndGet()  // ✅ atomic
        } catch (e: Exception) {
            watchtowerMetrics.webhookFailures.incrementAndGet()  // ✅ atomic
            log.warn("Webhook failed: ${e.message}")
        }
    }
}

private suspend fun pollBalances() {
    for ((wallet, lastBalance) in watchedBalances.toMap()) {
        try {
            val balance = web3j.ethGetBalance(wallet, DefaultBlockParameterName.LATEST).send().balance
            if (lastBalance > BigInteger.ZERO && balance < lastBalance) {
                val droppedFraction = 1.0 - balance.toDouble() / lastBalance.toDouble()
                if (droppedFraction >= config.balanceDropThreshold) {
                    watchtowerMetrics.balanceDropsDetected.incrementAndGet()  // ✅ atomic
                    // ... notify webhook ...
                }
            }
            watchedBalances[wallet] = balance
        } catch (e: Exception) {
            // ... error handling ...
        }
    }
}
```

**Оценка:** 3 часа.

**Verification:**
- [ ] `/metrics` включает `mdaopay_watchtower_*` метрики
- [ ] `mdaopay_watchtower_last_poll_timestamp` обновляется каждые 60s
- [ ] Concurrent webhook notifications не теряются (thread safety test)
- [ ] Alert: `time() - mdaopay_watchtower_last_poll_timestamp > 300` → watchtower stuck

### 2.5-2.7 — без изменений

---

## Phase 3 — Testnet Deploy + Soak

### 3.1 Pre-deploy — без изменений

### 3.2 Deploy — ИСПРАВЛЕНО: JWT_SECRET только через AWS SM

```bash
# ❌ КАК БЫЛО В v4 (НЕПРАВИЛЬНО — inline генерация):
export JWT_SECRET=$(openssl rand -base64 48)
export RELAY_SECRET=$(openssl rand -base64 48)
./scripts/deploy-testnet.sh

# ✅ ИСПРАВЛЕНО: JWT_SECRET берётся из AWS SM (скрипт сам получает)
# JWT_SECRET и RELAY_SECRET должны быть заранее в AWS Secrets Manager
# (создаются один раз при первом деплое)

# Pre-check: secrets exist in AWS SM
aws secretsmanager get-secret-value --secret-id mdaopay/testnet/jwt-secret >/dev/null 2>&1 \
    || { echo "❌ JWT_SECRET not in AWS SM. Run: aws secretsmanager create-secret --name mdaopay/testnet/jwt-secret --secret-string \$(openssl rand -base64 48)"; exit 1; }
aws secretsmanager get-secret-value --secret-id mdaopay/testnet/relay-secret >/dev/null 2>&1 \
    || { echo "❌ RELAY_SECRET not in AWS SM"; exit 1; }

# Deploy — script читает secrets из AWS SM, не из env
./scripts/deploy-testnet.sh
```

### 3.3 Smoke tests — без изменений (используют реальные пути)

### 3.4 Soak test — Day 6 ИСПРАВЛЕН

| День | Сценарий | Gate | Caveat |
|------|----------|------|--------|
| 1 | Onboard 5 тестировщиков | 5 кошельков созданы | — |
| 2 | Gasless send | 20+ tx, 95%+ success | — |
| 3 | Guardian flow | 15/15 guardians confirmed | — |
| 4 | Recovery: initiate → 2 approve → execute | 1+ recovery completed | — |
| 5 | Recovery veto | 1+ veto, totalSupply ↓ | — |
| **6** | **Session key creation (native UI) + read-only dApp view** | **Native session key CRUD работает** | **⚠️ Write transactions через WebView — PENDING F-059 fix (Phase 1)** |
| 7 | Edge cases: RPC outage, Redis restart | 0 unrecoverable errors | — |

### 3.5 Rollback criteria — без изменений

### 3.6 Go/No-Go — без изменений

---

## Risk Register (исправленный)

**Убраны** R-15 и R-16 (это подтверждённые факты, не риски — они в Phase 0 как blockers).

**Добавлены** реальные неопределённости:

| # | Risk | Probability | Impact | Mitigation |
|---|------|-------------|--------|------------|
| R-1 | FCL/Daimo gas > 200k на реальных WebAuthn assertions | Medium | High | Bench с реальными credentials до деплоя |
| R-2 | SIWE message parsing fails на нестандартных клиентах | Low | Medium | Fuzz 50+ variations, spruceid library |
| R-3 | AWS KMS latency > 500ms (cross-region) | Low | Medium | Regional KMS, cache |
| R-4 | BSC testnet нестабилен (RPC outage) | High | Low | Multi-RPC failover (Ankr, QuickNode, official) |
| R-5 | Relay KV TOCTOU race (C-10) | Medium | Low | On-chain verification — source of truth, Durable Objects Phase 1 |
| R-6 | ERC-4337 v0.6 deprecated, security patches stop | Low | High | Plan v0.7 migration Phase 2 |
| R-7 | MockP256 случайно задеплоен на mainnet | Low | Critical | Constructor check `require(block.chainid != 56)` (уже есть) + Daimo replaces MockP256 |
| R-8 | Paymaster BNB balance drained (DoS) | Medium | High | Daily withdrawal cap, alerting, emergency pause |
| R-9 | Recovery deadlock: guardian'ы не отвечают | Medium | Medium | 48h timelock + cleanupExpiredRecovery + DeadManSwitch |
| R-10 | Backend DB migration fails | Low | High | Tested rollback, backup before migration |
| R-11 | JWT_SECRET случайно перегенерирован при redeploy | Low (fixed in v5) | Medium | Persistent в AWS Secrets Manager, script читает из SM |
| R-12 | InsuranceFund deploy падает (auditor=0x0) | Low (fixed in v5) | Medium | Phase 0.4 — env required |
| R-13 | Foundry broadcast JSON path изменится в новой версии | Low | Low | Fallback parse: contractName → index |
| R-14 | eth_sendTransaction возвращает fake hash | Low (already error) | Medium | Уже returns JSON-RPC error, dApp integration — Phase 1 epic |
| **R-15** | **Daimo P256Verifier gas > 200k на production WebAuthn (не тестовых)** | **Medium** | **High** | **Bench с реальными Android CredentialManager assertions до mainnet** |
| **R-16** | **SIWE parsing fails на нестандартных EIP-4361 клиентах (legacy wallets)** | **Low** | **Medium** | **Fuzz 50+ variations, fallback на spruceid library** |
| **R-17** | **v→recId конвертация: v=0/1 (некоторые wallets) vs v=27/28 (Ethereum standard)** | **Low** | **High** | **Поддержка обоих: `v==27||v==0 → 0; v==28||v==1 → 1`** |
| **R-18** | **Thread-safety: WatchtowerService concurrent coroutines → lost metrics** | **Low (fixed in v5)** | **Medium** | **AtomicLong/AtomicInteger для всех счётчиков** |

---

## Приложение A — Verification matrix (исправленная)

| # | Утверждение v4 | Проверка кодом | Статус | Phase |
|---|----------------|----------------|--------|-------|
| 1 | `scripts/deploy-testnet.sh` существует | `ls scripts/` → нет | ❌ | 0.5 |
| 2 | InsuranceFund deploy с auditor | `Deploy.s.sol:66` → `address(0)` | ❌ | 0.4 |
| 3 | `--private-key` для forge | `Deploy.s.sol:21` → `vm.envUint` (конфликт) | ❌ | 0.4 |
| 4 | `wrangler deploy --env testnet` | `wrangler.toml` → нет `[env.testnet]`, KV id="" | ❌ | 0.6 |
| 5 | SIWE в backend | `rg siwe backend/` → 0 results | ❌ | 0.2 |
| 6 | `/v1/health` | `Application.kt:334` → `/health` (вне /v1) | ❌ | 0.5 (smoke tests) |
| 7 | `/v1/health/deep` | `rg health/deep` → 0 results | ❌ | 2.3 |
| 8 | `mdaopay_watchtower` metrics | `Metrics.kt` → `mdao_*` prefix | ❌ | 2.4 |
| 9 | 7 contract addresses в AppConfig | `AppConfig.kt` → только 2 | ❌ | 0.7 |
| 10 | `deployments/` directory | `ls deployments/` → no such dir | ❌ | 0.5 |
| 11 | AWS Secrets Manager в backend | `rg secretsmanager backend/` → 0 | ❌ | 0.3 (KMS only) |
| 12 | FCL P-256 (Wave 16) | `find contracts -iname "*fcl*"` → 0 | ❌ | 0.1 |
| 13 | BinancePriceSource | `rg class BinancePriceSource` → ✅ | ✅ | — |
| 14 | EntryPoint verification | `cast code 0x5FF1...` → есть код | ✅ | — |
| **15** | **Timelock ownership + Gnosis Safe как proposer** | `Deploy.s.sol:114-118` → `proposers[0] = gnosisSafe` | **✅** | **—** |
| **16** | **`trustedSigner` в AppConfig** | `AppConfig.kt:30` → ✅ exists | **✅** | **—** |

**Итог:** 12 из 16 пунктов требуют работы. Пункты 15 (Gnosis Safe как proposer) и 16 (trustedSigner) — **уже реализованы** в коде, критика v4 по этим пунктам была ошибочной.

---

## Приложение B — Сводка исправлений v5 vs v4

| # | Проблема v4 | Категория | Исправление v5 |
|---|-------------|-----------|-----------------|
| 1 | `MessageHashUtils.hashEthereumMessage` (Solidity, не Kotlin) | 🔴 CRITICAL | `Hash.sha3(prefix + msgBytes)` с ручным EIP-191 prefix |
| 2 | `Sign.recoverFromSignature` с v (27/28) | 🔴 CRITICAL | Конвертация: `v==27\|\|v==0 → 0; v==28\|\|v==1 → 1` |
| 3 | `Keys.getAddress()` без `0x` prefix | 🔴 CRITICAL | `"0x" + Keys.getAddress(key).lowercase()` |
| 4 | `WatchtowerMetrics` не thread-safe | 🔴 CRITICAL | `AtomicLong` / `AtomicInteger` для всех счётчиков |
| 5 | FCL import path неверный | 🔴 CRITICAL | Daimo P256Verifier (drop-in, audited, проще) |
| 6 | Phase 3.2 JWT_SECRET inline | 🟠 MAJOR | Убрать inline, только через AWS SM |
| 7 | `parseSiweMessage` — пустая заглушка | 🟠 MAJOR | Полная реализация EIP-4361 парсера |
| 8 | Soak Day 6 нереалистичен | 🟠 MAJOR | Caveat: write ops — PENDING F-059 |
| 9 | Appendix A Item 15 — ✅ вместо ⚠️ | 🟡 MINOR | ✅ корректен (Gnosis Safe IS proposer, проверено) |
| 10 | `msg.sender` до `startBroadcast` | 🟡 MINOR | `vm.startBroadcast()` перед `msg.sender` |
| 11 | R-15/R-16 — факты, не риски | 🔵 IMPROVEMENT | Убраны, добавлены R-15 (gas), R-16 (parsing), R-17 (v→recId), R-18 (thread safety) |
| 12 | `TRUSTED_SIGNER` не в AppConfig | 🟠 MAJOR | ❌ **Критика ошибочна** — `trustedSigner` уже есть в `AppConfig.kt:30` |

---

## Приложение C — Что в критике v4 было ошибочным

| Пункт критики v4 | Проверка кодом | Результат |
|------------------|----------------|-----------|
| #9: "Gnosis Safe не добавлен как proposer Timelock" | `Deploy.s.sol:114-118`:<br>`proposers[0] = gnosisSafe`<br>`executors[0] = gnosisSafe` | ❌ **Критика неверна** — Safe уже proposer+executor |
| #12: "`TRUSTED_SIGNER` не в AppConfig" | `AppConfig.kt:30`:<br>`val trustedSigner: String,` | ❌ **Критика неверна** — `trustedSigner` уже required field |

**Вывод:** 2 из 12 пунктов критики v4 основаны на неверных предположениях о коде. Остальные 10 — технически точны и исправлены в v5.

---

## Приложение D — Команды для проверки исправлений v5

```bash
# 1. Daimo P256Verifier интегрирован?
find contracts/lib -iname "P256Verifier.sol" 2>/dev/null
# Ожидание после Phase 0.1: contracts/lib/p256-verifier/src/P256Verifier.sol

# 2. SIWE recoverEthereumSigner с правильным v→recId?
rg "v == 27.*recId\|recId.*v == 27\|when \(v\)" backend/src/main/kotlin/com/mdaopay/paymaster/AuthService.kt
# Ожидание после Phase 0.2: конвертация v→recId

# 3. WatchtowerMetrics thread-safe?
rg "AtomicLong\|AtomicInteger" backend/src/main/kotlin/com/mdaopay/paymaster/Metrics.kt
# Ожидание после Phase 2.4: AtomicLong/AtomicInteger

# 4. Deploy.s.sol без vm.envUint("DEPLOYER_PRIVATE_KEY")?
rg "vm\.envUint.*DEPLOYER_PRIVATE_KEY" contracts/script/Deploy.s.sol
# Ожидание после Phase 0.4: 0 matches

# 5. InsuranceFund deploy с auditor?
rg "new InsuranceFund\(address\(0\)\)" contracts/script/
# Ожидание после Phase 0.4: 0 matches

# 6. JWT_SECRET не inline в Phase 3.2?
rg "JWT_SECRET=.*openssl rand" scripts/deploy-testnet.sh
# Ожидание: 0 matches (только чтение из AWS SM)

# 7. Gnosis Safe как proposer (уже есть, verification)?
rg "proposers\[0\] = gnosisSafe" contracts/script/Deploy.s.sol
# Ожидание: 1 match (уже реализовано)

# 8. trustedSigner в AppConfig (уже есть)?
rg "val trustedSigner" backend/src/main/kotlin/com/mdaopay/paymaster/AppConfig.kt
# Ожидание: 1 match (уже реализовано)
```

---

## Приложение E — Resource Estimates (без изменений)

| Роль | Phase 0 | Phase 1 | Phase 2 | Phase 3 | Итого | С buffer 25% |
|------|---------|---------|---------|---------|-------|--------------|
| Smart Contract Eng | 2 нед (Daimo) + 1ч (InsuranceFund) | 1ч (burn) | 0 | 2 дня | 2.5 нед | 3 нед |
| Backend Eng | 1 нед (SIWE) + 3 дня (KMS) + 1ч (AppConfig) | 1 день | 1 день (health, metrics) | 1 день | 2.5 нед | 3 нед |
| Mobile Eng | 2 дня (SIWE) | 1 день | 2ч | 1 день | 1 нед | 1.5 нед |
| DevOps/SRE | 1 день + 2ч (wrangler) | 2ч | 2 дня | 2 дня | 1.5 нед | 2 нед |
| QA | 0 | 0 | 2 дня | 1 нед | 1.5 нед | 2 нед |
| Security | 0 | 1.5 дня | 0 | 0 | 1 нед | 1.5 нед |

**Calendar time:** 6-7 недель при 3 разработчиках (parallel).

---

*MDAOPay Testnet Roadmap v5 · 2026-07-02 · 6-7 недель · исправлены 5 критических багов в коде · 2 пункта критики v4 опровергнуты кодом*
