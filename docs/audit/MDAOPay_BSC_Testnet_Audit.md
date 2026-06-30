# MDAOPay — Предразвёрточный аудит (Pre-BSC-Testnet)

**Дата аудита:** 2026-06-29
**Объект:** MDAOPay — Unified Access Layer for Web3 (MVP)
**Целевая сеть:** BSC Testnet (Chain ID: 97)
**Источники:** PRD (455 строк), TDD v2.7 (2835 строк)
**Методология:** Static architecture & design review по 7 направлениям

---

## Резюме: Критичные находки

| Уровень | Количество | Описание |
|---------|-----------|----------|
| **🔴 Critical** | 3 | Блокеры для testnet: несоответствие recovery threshold, непроверенные аудиторские подписи, P-256 precompile риск |
| **🟠 High** | 7 | Серьёзные риски безопасности и архитектурные дефекты |
| **🟡 Medium** | 12 | Проблемы, требующие исправления до mainnet |
| **🟢 Low** | 8 | Рекомендации по оптимизации и техдолг |

**Вердикт:** 🟡 **Условно готов к BSC Testnet** при условии устранения 3 critical и 4 high находок. Перед mainnet требуется повторный аудит с полным покрытием.

---

## 1. Соответствие кода документации (PRD vs TDD)

### 🔴 CRITICAL: Recovery threshold — реализация противоречит PRD MVP

| Документ | Требование |
|----------|-----------|
| **PRD §7** | "2-of-3 Trusted Contacts → 48h timelock" |
| **PRD §8.2** | "Trusted Contacts Recovery: 2-of-3" |
| **PRD §16** | "SSS: 2-of-3 (единственный режим MVP)" |
| **TDD §3.4** | `THRESHOLD = 3`, `MAX_GUARDIANS = 5` — требуется **3-of-5** |
| **ADR-006** | "K=3, N=5, TIMELOCK=48h" |

**Проблема:** PRD единообразно декларирует 2-of-3 как единственный режим MVP. Код SocialRecoveryModule.sol реализует 3-of-5. Это означает, что пользователю необходимо установить минимум 3 guardians (а не 2), и для восстановления нужны все 3 подтверждения. Это кардинально меняет UX: onboarding становится дольше, barrier to entry выше, а PRD вводит в заблуждение.

**Рекомендация:** Либо (а) привести код в соответствие с PRD — изменить `THRESHOLD = 2`, `MAX_GUARDIANS = 3` для MVP, либо (б) формально обновить PRD и пересмотреть продуктовые метрики (onboarding completion rate target >90% при 3 guardians нереалистичен). Для testnet рекомендуется вариант (а) — соответствие декларированной спецификации.

---

### 🟠 HIGH: Длина никнейма — расхождение PRD ↔ TDD ↔ код

| Источник | Длина | Charset | Примечание |
|----------|-------|---------|------------|
| **PRD §6** | 3-20 | `[a-zA-Z0-9_-]` | Дефис включён |
| **TDD §1.2.2** | 3-20 | `^[a-zA-Z0-9_-]{3,20}$` | Дефис включён |
| **TDD Appendix A** | **3-32** | **`[a-zA-Z0-9_]`** | Дефис **исключён**, длина до 32 |

TDD в Appendix A явно помечает: "Code (PRD #6 says 3-20 — TBD align)" и "Code (PRD #6 includes `-` — TBD align)". Это означает, что в коде реализованы одни ограничения, в документации — другие, и разработчики осознают расхождение, но не исправили его.

**Риск:** Пользователь зарегистрирует ник "user-name" (валидно по PRD), но если backend использует regex из Appendix A (без дефиса) — получит отказ. Или наоборот: зарегистрирует 25-символьный никнейм (валидно по коду), но PRD обещает максимум 20.

**Рекомендация:** Синхронизировать все три источника. Рекомендуемое значение: 3-20 символов, `[a-zA-Z0-9_-]` — баланс между UX и хранением.

---

### 🟡 MEDIUM: SSS threshold конфликт

PRD §16: "SSS 2-of-3 (единственный режим MVP)"
TDD §2.3.3: "Standard mode: K=3, N=5" / "Hermit mode: K=2, N=3"

В PRD заявлен единственный режим MVP — 2-of-3. В TDD два режима, и "стандартный" — 3-of-5. Это создаёт двусмысленность: какой режим используется по умолчанию? Если Hermit mode (2-of-3) — то он соответствует PRD. Но название "Hermit" подразумевает исключительный, а не стандартный сценарий.

**Рекомендация:** Для MVP использовать K=2, N=3 как единственный режим (соответствие PRD). Добавить explicit комментарий в код, что K=3, N=5 — для Phase 1+.

---

### 🟡 MEDIUM: Identity Hash модель — реализация отличается от архитектурного замысла

| Источник | Identity Hash |
|----------|--------------|
| **PRD §3.4** Guardian struct | `keccak256(google:email or apple:sub)` — привязка к OAuth identity |
| **TDD §3.4** confirmGuardian | `identityHash = keccak256(msg.sender)` — привязка к EOA address |
| **TDD Appendix A** HKDF fix | `HKDF-extract(salt, userId) → HKDF-expand(info="MDAOPay-guardian-identity")` |

Три разные модели идентичности в трёх разных частях документации. PRD описывает высокоуровневую концепцию (OAuth-based identity), Appendix A описывает криптографическую реализацию (HKDF), а TDD §3.4 показывает, что на контракте фактически используется просто keccak256(address).

**Рекомендация:** Определить единую модель identityHash. Для testnet — фиксировать `keccak256(HKDF(salt, userId))` на всех уровнях (контракт, backend, mobile).

---

### 🟢 LOW: PRD vs TDD — Identity Connect включён в MVP

PRD §8.3: "Identity Connect (Session Keys) — MVP" (со звездочкой приоритета)
TDD §3.12: SessionKeyModule реализован (112 строк, 17 тестов)

Соответствие есть, но SessionKeyModule использует `bytes32[] permissions` (raw function selectors), а не Capability enum как описано в PRD §17. Это сознательное решение (ADR-023: "Capability Mapping, не Selector Mapping"), но реализовано через client-side mapping (Kotlin PermissionMapper).

**Рекомендация:** Добавить on-chain validation, что permissions соответствуют известным селекторам. Неизвестный selector должен отклоняться на уровне контракта, не только в UI.

---

## 2. Безопасность

### 🔴 CRITICAL: InsuranceFund — полностью непроверенные подписи аудиторов (F-052)

**TDD §3.6:** "`auditorSignatures` in `submitClaim()` are accepted as-is without on-chain verification. The contract lacks an `auditors` mapping and signature verification logic."

**Проблема:** Функция `submitClaim()` принимает `bytes[] calldata auditorSignatures`, проверяет только их количество (≥3), но **не проверяет валидность подписей**. Нет mapping approved auditors, нет ECDSA recovery, нет проверки uniqueness подписей (один аудитор может дать 3 подписи).

**Attack scenario:**
1. Злоумышленник вызывает `submitClaim(victim, amount, hash, [sig1, sig2, sig3])` с любыми 64-байтными массивами
2. Транзакция проходит — `ClaimSubmitted` эмитирован
3. Если owner скомпрометирован или вредоносен — вызывает `approveClaim()` и переводит средства

**Рекомендация:** До mainnet — реализовать on-chain verifier: `mapping(address => bool) public auditors`, setter только через Timelock, проверка ECDSA recover для каждой подписи, проверка uniqueness signer'ов. Для testnet — добавить explicit comment "KNOWN LIMITATION: auditor signatures not verified on-chain (F-052)" и проверку в backend перед вызовом approveClaim.

---

### 🟠 HIGH: TOCTOU в Paymaster — бесплатный griefing

**TDD §3.3 TOCTOU Risk:** Между `validatePaymasterUserOp` (проверка баланса) и `postOp` (списание) существует окно до 300 секунд (quoteDeadline). В течение этого окна пользователь может:
- Перевести токены на другой адрес → `balanceOf < maxTokenAmount`
- Уменьшить allowance → `transferFrom` fails

**Mitigation реализованные:**
- Block-list после 3 consecutive `PaymentFailed` (cooldown 1 час)
- Adaptive deadline (60s для сумм >1000 токенов)

**Остаточный риск:** Злоумышленник может 2 раза получить бесплатный газ (2 failed payments → не заблокирован). Стоимость атаки: 2 UserOperations (газ оплачивается bundler'ом, не paymaster'ом). Paymaster теряет gasCost на validation.

**Рекомендация:**
1. Уменьшить adaptive deadline для всех сумм до 60s (сейчас: 300s для <1000 токенов)
2. Добавить pre-transfer check в `postOp` перед `transferFrom`: если баланс недостаточен — сразу emit `PaymentFailed` без попытки transfer
3. Рассмотреть `deposit` механизм: пользователь вносит небольшой deposit (например, 1 MDAO), который используется как collateral для газа

---

### 🟠 HIGH: JWT_SECRET — entropy check отсутствует

**TDD §1.8:** "Minimum length: 32 characters (256 bits entropy)"

**Проблема:** "32 characters" != "256 bits entropy". Строка `aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa` — 32 символа, но entropy ≈ 0. Применение `JWT_SECRET` с низкой entropy делает HMAC-SHA256 подпись уязвимой к brute-force.

**Рекомендация:** Добавить entropy check при старте приложения:
```kotlin
// Минимум: reject если < 20 уникальных символов
// Оптимально: требовать Base64-encoded 32 random bytes (44 chars)
require(jwtSecret.length >= 44, "JWT_SECRET must be Base64-encoded 256-bit key")
```

---

### 🟠 HIGH: Play Integrity API — warning level в MVP

**TDD §4.5 Mobile Security:**
- Play Integrity API: "warning level, MVP" ⚠
- Root detection: "warning level, MVP" ⚠

**Проблема:** На MVP уровне device integrity checks НЕ блокируют операции. HIGH risk devices (rooted, Play Integrity fail) — могут выполнять wallet operations. Это противоречит принципу безопасности для financial app.

**Attack scenario:**
1. Устройство с root-доступом
2. Keystore keys могут быть извлечены (обход биометрии)
3. SSS shares доступны в plaintext
4. Злоумышленник восстанавливает mnemonic → полный контроль над средствами

**Рекомендация:** Для testnet — acceptable (testnet tokens имеют нулевую стоимость). Для mainnet Phase 1 — обязательно включить `RiskLevel.HIGH` blocking. Добавить feature-flag: `DEVICE_INTEGRITY_BLOCKING = true/false`.

---

### 🟠 HIGH: ALLOW_LOCAL_SIGNING — риск попадания в production

**TDD §4.8 Docker Compose:** `ALLOW_LOCAL_SIGNING=true` (dev-only)
**TDD §1.2.1:** Fallback: env-var signing for non-production environments only, with explicit `ALLOW_LOCAL_SIGNING=true` flag.

**Проблема:** Нет явной проверки `!isProduction()` в коде. Если `PAYMASTER_PRIVATE_KEY` установлен в production без KMS — приложение запустится, но не будет предупреждения. Риск: забыть KMS в staging → приватный ключ в env vars.

**Рекомендация:**
```kotlin
// В коде AppConfig.kt:
if (environment == "production" && ALLOW_LOCAL_SIGNING == "true") {
    throw IllegalStateException("ALLOW_LOCAL_SIGNING forbidden in production")
}
```

---

### 🟡 MEDIUM: ConcurrentHashMap fallback — rate limit bypass при horizontal scaling

**TDD §1.9:** Redis-based rate limiting с in-memory fallback через ConcurrentHashMap.

**Проблема:** Cloud Run/GKE — горизонтальное масштабирование. Каждый pod имеет свой ConcurrentHashMap. Rate limit per IP (20 req/min) становится 20 * N pods/min. При 10 pods — 200 req/min effective limit. Это позволяет distributed DoS.

**Mitigation:** "fail-closed" — но это не решает scaling problem.

**Рекомендация:**
1. Убрать ConcurrentHashMap fallback для rate limiting (оставить только для replay cache)
2. При недоступности Redis — возвращать 503 Service Unavailable для rate-limited endpoints
3. Альтернатива: sticky sessions (не рекомендуется для Cloud Run)

---

### 🟡 MEDIUM: Daily withdrawal cap — edge case в границе суток

**TDD §3.3:** `dailyWithdrawalCapBps = 5000` (50% от баланса). Счётчик сбрасывается по прошествии 24 часов.

**Проблема:** Нет cooldown между `dailyWithdrawalResetAt` и первым withdrawal'ом нового дня. Злоумышленник может:
1. День 1, 23:59: вывести 50% баланса
2. День 2, 00:00: сразу вывести ещё 50% (счётчик сброшен)
3. Итого: 75% баланса за 1 минуту

**Рекомендация:** Добавить `lastWithdrawalTimestamp` с minimum interval (например, 1 час) или использовать sliding window вместо daily reset.

---

### 🟡 MEDIUM: Metrics endpoint — token authentication

**TDD §1.2.1:** "Internal only. Must be behind VPC or require X-Metrics-Token header (random 256-bit value, rotated monthly)."

**Проблема:** Token rotation monthly — ручной процесс. Вероятность утечки token'а через логи, конфигурации. Нет механизма немедленной инвалидации.

**Рекомендация:** Использовать GCP IAM authentication вместо shared token. Или: JWT-based metrics auth с short TTL (5 min).

---

### 🟡 MEDIUM: Nonce gap = 20 — DoS окно

**TDD Appendix A (SEC-24-07):** "MAX_NONCE_GAP = 20 (was 100)"

**Проблема:** Sender может отправить UserOp с nonce = current + 20. Это блокирует 20 sequential nonce slots. Если paymaster принимает — sender может повторить с нового адреса.

**Рекомендация:** Уменьшить до 5-10. Для reference: Alchemy использует nonce gap = 10.

---

## 3. Криптография

### 🔴 CRITICAL: P-256 Precompile — доступность на BSC Testnet

**TDD §3.4:** `P256_VERIFIER = 0x0000000000000000000000000000000000000100` (RIP-7212)
**TDD §3.13:** "Use MockP256 for testnets that don't support RIP-7212"
**SEC-24-04:** `require(block.chainid != 1 && != 56)` в MockP256

**Проблема:** RIP-7212 (P-256 precompile at 0x100) — это относительно новый стандарт. На момент написания документа (июнь 2026):
- Ethereum mainnet: не активен (требует hard fork)
- BSC mainnet (56): статус неясен
- BSC Testnet (97): **вероятно, не активен**

Если precompile не доступен на BSC Testnet — все `staticcall` к `0x100` будут возвращать `success = true, data = 0x00` (Ethereum behavior для несуществующих precompiles). SocialRecoveryModule интерпретирует `return data != 0x01` как invalid signature → **recovery невозможен**.

**Рекомендация:**
1. Перед деплоем на BSC Testnet — проверить наличие RIP-7212:
   ```solidity
   // Test script
   (bool success, bytes memory ret) = address(0x100).staticcall(...);
   require(success && ret.length > 0, "RIP-7212 not available");
   ```
2. Если недоступен — задеплоить `MockP256` и изменить `P256_VERIFIER` constant в SocialRecoveryModule (требует redeploy)
3. Альтернатива: использовать software P-256 verifier (например, FreshCryptoLib) как fallback

---

### 🟠 HIGH: WebAuthn signature format — DER vs raw conversion

**TDD §3.4:** "signature: 64 bytes (r || s)"
**TDD §2.3.4:** "CBOR-decoded authData → extract HMAC-secret output"

**Проблема:** WebAuthn/Passkey возвращает подписи в ASN.1 DER format (variable length, 70-72 bytes). RIP-7212 требует raw format (64 bytes, r || s). В TDD **не описана** конвертация DER → raw.

Если код передаёт DER-encoded signature напрямую в `_verifyP256` — verification всегда будет fail.

**Рекомендация:** Добавить `DERToRS` conversion function:
```solidity
function derToRS(bytes memory der) internal pure returns (bytes32 r, bytes32 s) {
    // ASN.1: 0x30 [total-len] 0x02 [r-len] [r] 0x02 [s-len] [s]
    require(der[0] == 0x30, "Invalid DER");
    // ... parsing logic
}
```
Или реализовать на уровне Kotlin (mobile) перед отправкой на контракт.

---

### 🟡 MEDIUM: AES-256-GCM — random IV entropy

**TDD §2.3.1 / §2.3.2:** "IV: 12 bytes (random, prepended to ciphertext)"

**Проблема:** Random 12-byte IV для GCM — 96 bits entropy. При birthday paradox, вероятность коллизии 50% при ~2^48 операций. Для одного wallet key — не критично (несколько десятков шифрований). Но при масштабе (100k+ wallets) — статистически возможна коллизия.

**Mitigation:** Вероятность крайне низкая для отдельного устройства.

**Рекомендация:** Для Phase 1 — перейти на 96-bit counter-based IV (монотонный счётчик, сохраняемый в encrypted form). Это гарантирует uniqueness без reliance на RNG.

---

### 🟡 MEDIUM: PBKDF2-HMAC-SHA256 vs Argon2id

**TDD §1.8 / §2.3.4:** "PBKDF2WithHmacSHA256, 600,000 iterations (OWASP 2023)"

**Проблема:** PBKDF2 — устаревший KDF. OWASP 2023 рекомендует PBKDF2, но **предпочитает** Argon2id. PBKDF2 уязвим к GPU/ASIC атакам из-за небольшого memory footprint.

**Рекомендация:** Для Phase 1 — мигрировать на Argon2id (OWASP 2025 рекомендация):
```
Argon2id: memory=19 MiB, iterations=2, parallelism=1
```
Для обратной совместимости — support оба KDF с version marker.

---

### 🟡 MEDIUM: SSS over GF(256) — размер secret

**TDD §2.3.3:** "Field: GF(256), Irreducible polynomial: 0x11D"

**Проблема:** GF(256) — 8-битное поле. Для разделения 256-bit master key схема Shamir должна использовать polynomial с 256-bit coefficients (32 байта каждый), не 8-bit. TDD не описывает, как 256-bit secret представляется в GF(256).

Если реализация просто разбивает 256-bit secret на 32 байта и применяет SSS к каждому байту отдельно — это byte-wise SSS, корректный но неэффективный подход.

**Рекомендация:** Добавить explicit specification: "Byte-wise SSS: 32 parallel GF(256) polynomials, one per byte of master key". Или рассмотреть GF(2^256) для single polynomial (сложнее реализация, но более элегантно).

---

### 🟢 LOW: HMAC-secret vs P-256 signature — путаница

**TDD §2.3.4:** "Credential Manager returns PRF evaluation result → CBOR-decoded authData → extract HMAC-secret output → Derive AES-256 key for share decryption"

PRF/HMAC-secret extension используется для **key derivation** (получение ключа для расшифровки share), а не для **signature**. Но TDD §3.4 описывает P-256 signatures для recovery approval. Это две разные криптографические операции:
- PRF: deterministic key derivation (HMAC-based)
- P-256: signature creation (ECDSA-like)

**Рекомендация:** Уточнить в документации различие между PRF key derivation (для share decryption) и P-256 signing key (для recovery approval). В текущем виде документация создаёт впечатление, что HMAC-secret используется напрямую для подписи recovery messages.

---

## 4. Блокчейн

### 🟠 HIGH: ERC-4337 v0.6 — deprecated версия

**TDD §3.1 / PRD §14:** EntryPoint v0.6 (`0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789`)
**ADR-012:** "Revisit before mainnet: align submodule tag with actual interface version"

**Проблема:** ERC-4337 v0.7 — актуальная версия (выпущена в 2024). v0.6 deprecated. Bundler'ы постепенно прекращают поддержку v0.6. Использование v0.6 создаёт риск:
- Несовместимость с новыми bundler'ами (Pimlico, Biconomy уже v0.7+)
- Отсутствие security fixes для v0.6
- Технический долг для миграции

**Рекомендация:** Для testnet — acceptable (Alchemy/StackUp всё ещё поддерживают v0.6). Перед mainnet — обязательная миграция на v0.7. Запланировать 2-3 недели на migration в roadmap.

---

### 🟡 MEDIUM: Price Oracle — 2 из 3 источников в MVP

**TDD §1.10:**
- DexScreener: ✅ Implemented
- CoinGecko: ✅ Implemented
- OnChain TWAP: "Placeholder (NotImplementedError) — Phase 2"

**Проблема:** Только 2 источника в production. Если оба fail — нет fallback на mainnet (fallback prices только для testnet). Circuit breaker откроется, и все `/sign` запросы будут отклоняться.

**Рекомендация:**
1. Реализовать OnChain TWAP (PancakeSwap pair) — minimum viable за 1-2 дня
2. Добавить Binance API как 4-й fallback source
3. Установить hardcoded "last known good price" с deviation check (например, не более 20% от предыдущего)

---

### 🟡 MEDIUM: MDAO Token — max burn fee 10%

**TDD §3.2:** `MAX_BURN_FEE_BPS = 1000` (10%)

**Проблема:** 10% fee на transfer — экстремально высокий. Если owner account скомпрометирован, злоумышленник может установить 10% fee за одну транзакцию (без timelock для `setBurnFeeBps`).

**Рекомендация:** Уменьшить MAX_BURN_FEE_BPS до 300 (3%) или добавить timelock для `setBurnFeeBps` (2 дня).

---

### 🟡 MEDIUM: BSC Testnet — Chain ID confusion

| Среда | Chain ID | Сеть |
|-------|----------|------|
| Dev | 97 | BSC Testnet |
| Staging | 97 | BSC Testnet |
| Prod | 56 | BSC Mainnet |

**Проблема:** TDD §3.13 говорит: "Network: BSC (Chain ID: 56) for MVP", но тестирование на Chain ID 97. Контракты могут вести себя по-разному (разные precompiles, разная latency, разные oracle feeds).

**Рекомендация:** Для testnet-деплоя использовать Chain ID 97 во всех конфигах. Добавить explicit `CHAIN_ID` validation в CI: testnet builds должны reject Chain ID 56.

---

### 🟢 LOW: RPC failover — 3 failures до switch

**TDD §4.2:** "3 consecutive failures → provider cooldown 30s"

С timeout 15s на request: worst case 45s downtime до failover. Для UX — критично.

**Рекомендация:** Уменьшить до 2 failures или уменьшить timeout до 5s для health checks.

---

## 5. Смарт-контракты

### 🟠 HIGH: SocialRecoveryModule — нет проверки P-256 public key validity

**TDD §3.4:** `ErrInvalidPublicKey` — pubKeyX/Y == 0. Но нет проверки, что (X, Y) — валидная точка на кривой P-256.

**Проблема:** Можно добавить guardian с невалидной точкой на кривой. При recovery — `_verifyP256` всегда вернёт false. Guardian не сможет approve recovery, но его присутствие в массиве блокирует добавление другого guardian'а (MAX_GUARDIANS = 5).

**Рекомендация:** Добавить on-curve validation в `addGuardian()`:
```solidity
require(isOnCurveP256(pubKeyX, pubKeyY), "Invalid curve point");
```

---

### 🟡 MEDIUM: NicknameRegistry — неэффективное хранение

**TDD §3.5:**
```solidity
mapping(bytes32 => address) public nicknameToAddress;
mapping(address => bytes32) public addressToNickname;
```

Identity hash = `keccak256(abi.encodePacked(signer))` — просто keccak256(address). Это deterministic mapping: `identityHash` однозначно определяется `signer`. Два mapping избыточны — достаточно одного `mapping(address => bool) public registered`.

**Рекомендация:** Сохранить текущую структуру для future extensibility, но добавить comment: "identityHash is deterministic function of address; redundant storage intentional for future identity models".

---

### 🟡 MEDIUM: SessionKeyModule — нет on-chain selector validation

**TDD §3.12:**
```solidity
struct SessionKey {
    bytes32[] permissions;  // array of allowed selectors
}
```

**Проблема:** Любой selector может быть добавлен в permissions. Неизвестный/dangerous selector (например, selfdestruct, delegatecall) — валиден для контракта.

**Mitigation:** Client-side validation в PermissionMapper.kt.

**Рекомендация:** Добавить on-chain whitelist известных selectors:
```solidity
mapping(bytes4 => bool) public allowedSelectors;
// Only owner can add selectors
```

---

### 🟡 MEDIUM: AttestationLedger — spam risk

**TDD §3.8:** "Anyone can submit a hash + subject + metadata for permanent recording."

Нет access control, нет fee, нет rate limiting. Любой может записать сколько угодно данных.

**Рекомендация:** Добавить `minimumDeposit` (например, 0.001 BNB) для каждой attestation. Или `onlyOwner` для MVP.

---

### 🟡 MEDIUM: DeadManSwitch — нет проверки beneficiary != address(0)

**TDD §3.7:** `setSwitch` проверяет `beneficiary != owner`, но не проверяет `beneficiary != address(0)`.

**Рекомендация:** Добавить:
```solidity
require(beneficiary != address(0), "Zero address");
```

---

### 🟢 LOW: Non-upgradeable contracts — migration complexity

**TDD §3.11:** Сознательное решение — non-upgradeable contracts.

**Trade-off:** Простота аудита vs migration cost. Для MVP — acceptable. Для mainnet — нужен formal migration playbook с automated guardian re-invite flow.

---

### 🟢 LOW: MDAOPaymaster — нет ReentrancyGuard

**TDD §3.3:** Нет explicit `ReentrancyGuard`. Но:
- CEI pattern используется в `postOp`
- `transferFrom` — external call, но выполняется после всех state changes
- Risk: low для текущей реализации

**Рекомендация:** Добавить `nonReentrant` на `postOp` для defense in depth.

---

## 6. Неоптимальность кода

### 🟡 MEDIUM: RecoveryScreen — 1431 строк (God Object)

**TDD §2.2.1:** RecoveryScreen.kt — 1431 строк. RecoveryViewModel.kt — 550 строк.

**Проблема:** Single Responsibility Principle нарушен. 1431 строк UI-кода невозможно эффективно review, test и maintain.

**Рекомендация:** Разбить на:
- `RecoveryScreen.kt` (100-150 lines) — композиция под-компонентов
- `RecoveryStatusSection.kt`
- `GuardianListSection.kt`
- `RecoveryPinInput.kt`
- `RecoveryProgressBar.kt`

---

### 🟡 MEDIUM: HomeScreen — 1059 строк

**TDD §2.2.1:** HomeScreen.kt — 1059 строк.

**Рекомендация:** Аналогичное разбиение на composable-компоненты.

---

### 🟡 MEDIUM: JSON-RPC batching — не реализован

**TDD §1.2.1:** "4 eth_call requests can be combined into 1 HTTP round-trip via JSON-RPC batch (optional optimization, not yet implemented)"

**Проблема:** 4 sequential HTTP requests вместо 1 batched. Latency: ~4x.

**Рекомендация:** Реализовать JSON-RPC batching в Web3j/EthereumClient. Это ~20% latency improvement для `/sign`.

---

### 🟡 MEDIUM: ConcurrentHashMap — memory leak

**TDD §1.9:** Cleanup thread every 300s, но нет per-entry TTL.

**Проблема:** Rate limit entries накапливаются до cleanup. При spike traffic — OOM risk.

**Рекомендация:** Использовать `Caffeine` cache с TTL per entry:
```kotlin
Caffeine.newBuilder()
    .expireAfterWrite(60, TimeUnit.SECONDS)
    .build<String, RateLimitEntry>()
```

---

### 🟢 LOW: Coverage gaps — function coverage <70%

**TDD §5.6:**
| Contract | Function % |
|----------|-----------|
| MDAOPaymaster | 60% |
| SocialRecoveryModule | 59% |
| SessionKeyModule | 67% |

Low function coverage из-за internal helpers. Но перед mainnet — рекомендуется >80%.

---

### 🟢 LOW: Web3j 4.14.0 — outdated

**TDD Appendix A:** "web3j version 4.14.0 — Latest is 5.0.3 (Jun 2026)"

**Рекомендация:** Запланировать upgrade до 5.0.3 перед mainnet. Провести integration testing.

---

## 7. Другие проблемы

### 🟡 MEDIUM: Slither CI — исключение pragma-version

**TDD Appendix A:** `slither . --exclude naming-convention,pragma-version`

**Проблема:** Исключение `pragma-version` скрывает предупреждения об устаревших pragma. Solidity ^0.8.28 — актуальна, но если pragma изменится — предупреждение не появится.

**Рекомендация:** Убрать `--exclude pragma-version`. Вместо этого — использовать统一ную pragma и добавить `solc-version` rule в `solhint.json`.

---

### 🟡 MEDIUM: Cloud SQL HA — single region

**TDD §4.1:** "Cloud SQL HA (PostgreSQL) | 2 zones + read replica"

**Проблема:** 2 zones в одном region — не защищает от regional outage. RTO <15 min, но RPO зависит от cross-region replica.

**Рекомендация:** Добавить cross-region read replica в DR region для critical data (guardian hashes, recovery requests).

---

### 🟡 MEDIUM: HikariCP pool size = 10

**TDD Appendix A (SEC-28-09):** "HikariCP pool size 10, may bottleneck at 1000 RPS"

**Проблема:** 10 connections при 1000 RPS — 100 ops/sec per connection. PostgreSQL connection может обрабатывать ~200-500 simple queries/sec. Bottleneck на 500+ RPS.

**Рекомендация:** Увеличить до 25 connections (как рекомендовано в SEC-28-09). Это one-line fix в `database.kt`.

---

### 🟢 LOW: Touch target — MDAOButton Sm=38dp

**TDD Appendix A (SEC-28-10):** "MDAOButton Sm=38dp < 48dp minimum"

**Проблема:** Accessibility requirement — minimum touch target 48dp. Кнопка 38dp — difficult to tap.

**Рекомендация:** Увеличить до 48dp или добавить padding.

---

### 🟢 LOW: Content description — accessibility

**TDD Appendix A (SEC-28-11):** "ProductCard, ErrorIcon, QR code missing contentDescription"

**Рекомендация:** Добавить `contentDescription` для всех non-text UI elements.

---

### 🟢 LOW: Relay HMAC — single shared secret

**TDD §4.8:** `RELAY_SECRET` — единый shared secret для всех relay instances.

**Проблема:** Нет fine-grained revocation. Если один relay скомпрометирован — нужно ротировать secret для всех.

**Рекомендация:** Для Phase 1 — использовать per-relay JWT tokens с short TTL (5 min) + refresh token rotation.

---

### 🟢 LOW: Load test targets — амбициозные для MVP

**TDD §5.5:**
| Scenario | Target RPS |
|----------|-----------|
| /sign | 50 RPS |
| /nickname/resolve | 500 RPS |

При 5000 установках за 3 месяца — average load <1 RPS. Targets в 50-500 RPS оптимистичны. Но это не проблема — лучше иметь запас производительности.

---

## Таблица: Приоритет устранения

| # | Проблема | Severity | Компонент | Оценка времени | Блокер для testnet |
|---|----------|----------|-----------|---------------|-------------------|
| 1 | Recovery threshold 3-of-5 vs PRD 2-of-3 | 🔴 Critical | SocialRecoveryModule.sol | 2 ч | **ДА** |
| 2 | InsuranceFund — непроверенные аудиторские подписи | 🔴 Critical | InsuranceFund.sol | 1 день | Да (или добавить disclaimer) |
| 3 | P-256 precompile на BSC Testnet | 🔴 Critical | SocialRecoveryModule.sol | 4 ч | **ДА** |
| 4 | WebAuthn DER→raw signature conversion | 🟠 High | Mobile/Contract | 1 день | Да |
| 5 | TOCTOU griefing в Paymaster | 🟠 High | MDAOPaymaster.sol | 4 ч | Нет |
| 6 | JWT_SECRET entropy check | 🟠 High | Backend | 30 мин | Нет |
| 7 | Play Integrity — warning level | 🟠 High | Mobile | 2 ч | Нет (testnet) |
| 8 | ALLOW_LOCAL_SIGNING в production | 🟠 High | Backend | 30 мин | Нет |
| 9 | NicknameRegistry — длина/charset | 🟠 High | Backend/Contract | 1 ч | Да |
| 10 | P-256 public key on-curve check | 🟠 High | SocialRecoveryModule.sol | 2 ч | Нет |
| 11 | ERC-4337 v0.6 deprecated | 🟡 Medium | Architecture | 2-3 нед | Нет (перед mainnet) |
| 12 | Price Oracle — 2 источника | 🟡 Medium | Backend | 1 день | Нет |
| 13 | MDAO burn fee cap 10% | 🟡 Medium | MDAOToken.sol | 30 мин | Нет |
| 14 | Chain ID confusion 56/97 | 🟡 Medium | Config | 30 мин | **ДА** |
| 15 | ConcurrentHashMap scaling | 🟡 Medium | Backend | 2 ч | Нет |
| 16 | Daily withdrawal cap edge case | 🟡 Medium | MDAOPaymaster.sol | 1 ч | Нет |
| 17 | SessionKeyModule selector whitelist | 🟡 Medium | SessionKeyModule.sol | 2 ч | Нет |
| 18 | SSS specification clarity | 🟡 Medium | Mobile | 30 мин | Нет |
| 19 | AES-GCM IV strategy | 🟡 Medium | Mobile | 2 ч | Нет |
| 20 | AttestationLedger spam | 🟡 Medium | AttestationLedger.sol | 30 мин | Нет |
| 21 | RecoveryScreen 1431 lines | 🟡 Medium | Mobile | 1 день | Нет |
| 22 | HomeScreen 1059 lines | 🟡 Medium | Mobile | 4 ч | Нет |
| 23 | JSON-RPC batching | 🟡 Medium | Backend | 4 ч | Нет |
| 24 | PBKDF2 → Argon2id | 🟡 Medium | Mobile | 1 день | Нет (Phase 1) |
| 25 | Nonce gap = 20 | 🟡 Medium | Backend | 15 мин | Нет |
| 26 | God objects refactoring | 🟢 Low | Mobile | 2-3 дн | Нет |
| 27 | HikariCP pool size | 🟢 Low | Backend | 5 мин | Нет |
| 28 | Touch target 48dp | 🟢 Low | Mobile | 30 мин | Нет |
| 29 | Content descriptions | 🟢 Low | Mobile | 1 ч | Нет |
| 30 | Coverage gaps | 🟢 Low | Contracts | 2 дн | Нет |

---

## Чек-лист перед BSC Testnet деплоем

### Must-have (блокеры):
- [ ] Исправить `THRESHOLD` в SocialRecoveryModule (2 для MVP или обновить PRD)
- [ ] Проверить доступность RIP-7212 на BSC Testnet (Chain ID 97)
- [ ] Синхронизировать длину никнейма: 3-20 символов, `[a-zA-Z0-9_-]`
- [ ] Установить Chain ID 97 во всех testnet-конфигах
- [ ] Добавить DER→raw signature conversion для WebAuthn
- [ ] Добавить entropy check для JWT_SECRET
- [ ] Добавить `ALLOW_LOCAL_SIGNING` guard для production

### Should-have (перед публичным beta):
- [ ] Добавить on-curve P-256 public key validation
- [ ] Уменьшить adaptive deadline до 60s для всех сумм
- [ ] Убрать ConcurrentHashMap fallback для rate limiting
- [ ] Добавить InsuranceFund disclaimer (F-052)
- [ ] Уменьшить MDAO burn fee cap до 3%

### Nice-to-have (Phase 1):
- [ ] Миграция на ERC-4337 v0.7
- [ ] Argon2id вместо PBKDF2
- [ ] JSON-RPC batching
- [ ] OnChain TWAP oracle
- [ ] Рефакторинг God Objects (RecoveryScreen, HomeScreen)

---

*Аудит проведён на основе статического анализа PRD и TDD документации. Для production mainnet рекомендуется дополнительно провести dynamic analysis (fuzzing, symbolic execution) и independent third-party audit смарт-контрактов.*
