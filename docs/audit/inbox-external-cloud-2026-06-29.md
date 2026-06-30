Проанализируй ответ claude и дай техническую реализацию на каждое замечание, предложение объясни почему это наилучшее решение ## 1. Сильные стороны проекта (Strengths)

**1.1 Crypto Agility через TrustProviderRegistry — архитектурно зрелое решение**
`MDAOPaymaster.sol` не завязан жёстко на один способ верификации подписи. `_verifySignerIfConfigured()` делает fallback-цепочку: если `registry == address(0)` → legacy `trustedSigner` (прямой ECDSA), если задан — делегирует в `TrustProviderRegistry.verify(providerId, intentHash, proof)`, который сам диспатчит на `ITrustProvider` (сейчас `EcdsaVerifier`, но интерфейс позволяет добавить P-256/BLS/мультисиг-провайдера без изменения Paymaster). Это классический Strategy pattern, реализованный on-chain с обратной совместимостью — редкость для MVP-стадии проекта. Большинство команд на этом этапе хардкодят один способ подписи и потом мигрируют со сломанной совместимостью.

**1.2 Post-paid Paymaster модель устранена custody risk на уровне дизайна**
ADR-017 и реализация в `MDAOPaymaster.postOp()` — `transferFrom` происходит **после** факта газовых трат, а не предоплата на баланс. Это убирает целый класс проблем (sponsorshipBalance manipulation, refund-логику, riски недостачи депозита пользователя) и делает экономику Paymaster прозрачной: `computeAmountToCharge()` точно бьёт фактический `actualGasCost`. Differentiated failure handling (`FailureReason` enum: `InsufficientAllowance`/`InsufficientBalance`/`TokenRevert`) с раздельной реакцией (не блокировать за user-fixable ошибки, блокировать только за злонамеренный revert) — это именно то решение из `FP-ANTIGRIEF-001`, доведённое до продакшен-качества, а не наивный "3 fails → ban all".

**1.3 SSS-реализация не зависит от внешних крипто-библиотек и явно протестирована**
`ShamirSecretSharing.kt` + `GF256.kt` — самостоятельная реализация Шамира над GF(256) с явной таблицей логарифмов/экспонент, без внешних зависимостей (`web3j` тут не нужен). 17+18 unit-тестов на алгебраические свойства полей и round-trip split/join (TDD §5.1). Для security-критичного примитива это правильный инженерный выбор: меньше supply-chain risk, чем тащить стороннюю SSS-библиотеку с непрозрачной поддержкой.

**1.4 Изоляция доменов в Android Keystore с разными требованиями к auth**
`KeystoreCrypto.kt`: чёткое разделение `getOrCreateBiometricKey` (auth required, `setInvalidatedByBiometricEnrollment(true)`) и `getOrCreateKey` (no-auth) — и это сознательно мапится на разные shares в SSS (`shares 1,3 → no-auth file storage`, `shares 2,4 → biometric SharedPreferences`). Плюс F-062 (`authenticateHighRisk`) вводит отдельный auth-tier с `BIOMETRIC_STRONG`-only и нулевым session window именно для recovery/send/backup-export — то есть разработчики осознанно делят "обычный UX" и "high-risk операции" вместо одного универсального биометрик-промпта на всё приложение. Это зрелая модель угроз для мобильного non-custodial wallet.

**1.5 EIP-712 вместо EIP-191 для структурированных данных — закрыт целый класс replay-атак**
И в `MDAOPaymaster` (Quote-подпись), и в `NicknameRegistry` (Registration), и в `SocialRecoveryModule` (через `block.chainid` в WebAuthn-сообщении) используется правильный паттерн с domain separator, включающим `chainId` и `verifyingContract`. История находок (`ERRORS-MEMORY.md` EM-003) показывает, что команда сначала допустила EIP-191-ошибку и **осознанно** её исправила с задокументированным rationale — это говорит о работающем процессе пост-мортем анализа, а не только о коде.

---

## 2. Слабые стороны и узкие места (Weaknesses & Bottlenecks)

**2.1 Account Abstraction слой не доведён до сквозной интеграции — Paymaster существует, но не используется**
`SendRepository.sendUsdt()` строит `UserOperation` с `paymasterAndData = ByteArray(0)`. `PaymasterClient.encodePaymasterAndData()` существует как класс, но не вызывается из реального send-флоу. Это означает: пользователь в текущей реализации платит газ из своего нативного баланса (BNB) — что прямо противоречит PRD §15 ("Модель: Post-paid... Нет custody risk") и §3.2 ("не видит... газа").
**Риск:** на демо/тестнете это работает (у тестера есть testnet BNB), но архитектурно весь Paymaster-контракт, backend `/sign` эндпоинт, EIP-712 quote-механизм — это мёртвый код относительно мобильного клиента. Это не баг одной строки, это разрыв между бэкенд/контрактным слоем (полностью готовым) и Android-клиентом (не доинтегрированным) — системная проблема release-readiness, которую легко не заметить, потому что каждый слой "работает" изолированно.

**2.2 Social Recovery: on-chain guardian-флоу полностью отсутствует в клиенте, при этом депозит не сжигается — двойной разрыв между моделью угроз и реализацией**
`GuardianManager.kt` оперирует исключительно через `RelayClient` (Cloudflare Worker, офчейн REST), а методы `SocialRecoveryModule.sol` (`addGuardian`, `confirmGuardian`, `approveRecovery`, `vetoRecovery`) никогда не вызываются ни из одного Kotlin-файла. `GuardianInfo.pubKeyX/pubKeyY` создаются как пустые строки в `acceptInvite()` — то есть даже если бы вызов был, P-256 ключ гаранта никогда не генерируется на клиенте.
Параллельно — сам контракт (`vetoRecovery`) **возвращает депозит initiator'у при вето**, а не сжигает его, что обнуляет anti-spam-защиту, заявленную в PRD §7.
**Риск:** это означает, что "вторая фундаментальная фича после payments" (PRD: "Application 1: Payments... Layer 2: Trust") в текущем состоянии не функциональна end-to-end и не доказывает анти-спам экономику даже на уровне контракта. Для wallet, чья ключевая дифференциация — это recovery без seed phrase, это самый высокий продуктовый риск в проекте.

**2.3 Несинхронизированные версии one и того же контракта между TDD и кодом — потеря единого source of truth**
TDD §3.4 описывает `SocialRecoveryModule` с `THRESHOLD=3`, `MAX_GUARDIANS=5`, EIP-191-style WebAuthn-верификацией. Фактический `contracts/src/SocialRecoveryModule.sol` — другая реализация: `MAX_GUARDIANS=3`, `GUARDIAN_THRESHOLD=2`, корректная WebAuthn без лишнего prefix, депозит в MDAO через ERC-20 `transferFrom`.
**Риск:** это не просто "доку забыли обновить" — это означает, что **аудиторы безопасности (внешние Trail of Bits/OpenZeppelin) могут аудировать неправильную версию**, если ориентируются на TDD как scope document. Учитывая, что в `security/FINDINGS-INDEX.md` уже зафиксированы случаи "claimed fixed, but code shows otherwise" (`AP-PROCESS-005`), отсутствие единого source-of-truth между документацией и кодом — системный процессный риск, а не разовая ошибка.

**2.4 Off-chain инфраструктура (relay) — единая точка отказа без отказоустойчивости и observability**
`RelayClient.kt` — захардкоженный single URL (`mdaopay-relay.ekzent.workers.dev`) для всех окружений (dev/staging/prod не разделены, в отличие от `BACKEND_URL` в build.gradle.kts). Relay — Cloudflare Worker, держит весь guardian invite/accept/approve/veto notification флоу, при этом:
- Нет multi-region failover в документации (TDD §4.8 docker-compose даже не включает relay health-check зависимостей в общую матрицу backend↔relay).
- F-070 (rate limiting) и F-069 (body size limit) числятся `OPEN` в `FINDINGS-INDEX.md`.
- `RelayClient` не делает retry/circuit breaker — единичный network blip на момент guardian-approval теряет запрос без UI-фидбека пользователю кроме общего `Result.failure`.
**Риск:** для системы, чья ключевая ценность — "восстановление доступа когда всё остальное недоступно", relay является SPOF именно в критический момент (когда пользователь уже потерял телефон и пытается восстановиться через второе устройство).

**2.5 Backend↔Contract сигнатурная схема — историческая хрупкость без regression-гарантий на уровне CI**
`ERRORS-MEMORY.md` (EM-065/F-034) фиксирует, что backend и контракт уже расходились в схеме подписи (EIP-191 vs EIP-712) и это привело к 100% `InvalidSigner()`. Сейчас заявлено "исправлено", но в предоставленном репозитории нет интеграционного теста, который реально гоняет ответ `/sign` от backend через Foundry-форк с реальным `trustedSigner`/`registry` адресом — только unit-тесты на каждой стороне отдельно (`PaymasterServiceTest.kt` мокает, `MDAOPaymaster.t.sol` тестирует контракт изолированно).
**Риск:** контракт и backend могут разойтись повторно при любом рефакторинге EIP-712 domain (например, смена `version` в `EIP712("MDAOPay", "1")`) без единого теста, который бы это поймал до деплоя — именно тот паттерн, который уже стоил проекту целой Wave аудита (Wave 9, F-034).

---

## 3. Идеи для улучшения (Innovations & Missing Features)

**3.1 Risk-scored Session Keys вместо бинарного allow/deny в Identity Connect**
Сейчас `SessionKeyModule.sol` даёт только статичный `spendingLimit` + `validUntil`. Конкуренты (Coinbase Smart Wallet, ZeroDev) уже двигаются к динамическим policy engines. Предложение: добавить on-chain `RiskScore` модуль, который комбинирует `PermissionMapper`-категории (`PAYMENTS_SEND` low / `APPROVE_TOKENS` high) с поведенческим скорингом dApp (количество successful sessions без revoke = trust score), и динамически модулирует `spendingLimit` без участия пользователя для повторных низко-рисковых операций (как Apple Pay делает с "transit mode" — без Face ID на маленькие суммы). Технически: расширить `SessionKey` struct полем `riskTier` (uint8), и в `validateSessionKey` снижать требуемый biometric-tier для `riskTier == LOW`.

**3.2 Paymaster Multi-Sponsor Auction вместо фиксированного MDAO/USDT курса**
Текущая модель — owner вручную двигает `tokenPrice` через `setTokenPrice` (max ±2% за вызов, 15-минутный cooldown). Это работает для MVP, но не масштабируется на Phase 1 (Multi-Sponsor Paymaster из roadmap). Идея: ввести Dutch-auction механику для экосистемных партнёров (Arena, DEX) — они "торгуются" за право спонсировать газ для своих пользователей через staking MDAO в Paymaster pool, а реальная цена газа в их токене вычисляется через TWAP с встроенным spread, который идёт в `InsuranceFund` (контракт уже есть, не используется на полную). Это превращает Paymaster из cost-center в revenue-share инструмент для партнёров — прямое усиление North Star метрики ("количество экосистем").

**3.3 Passive Guardian Health Monitoring через Dead Man Switch как fallback для Trust Layer**
`DeadManSwitch.sol` уже задеплоен, но используется изолированно (наследство — beneficiary получает ETH при неактивности). Идея: интегрировать его как **fallback recovery-механизм**, если ни один guardian не отвечает на approve/veto N дней — DeadManSwitch триггерит автоматический "soft escalation" (push-уведомление на email/Apple/Google linked account из ADR-021 "OAuth = auxiliary"), снижая порог threshold с 2-of-2 до 1-of-2 с увеличенным timelock (например, 7 дней вместо 48ч). Это закрывает edge-case "оба гаранта недоступны", не нарушая принцип "OAuth не владеет кошельком".

**3.4 Client-side Capability Mapping → Verifiable On-chain Attestation**
`PermissionMapper.kt` сейчас маппит selectors в человекочитаемые intent'ы только на клиенте (ADR-023: "не on-chain enum, gas-efficient"). Но `AttestationLedger.sol` уже существует как примитив для записи хэшей. Идея: при первом подключении нового dApp через Identity Connect, бэкенд может публиковать community-verified mapping (через DappRadar-интеграцию из roadmap Phase 2) в `AttestationLedger`, и клиент при незнакомом selector сверяется с этим реестром вместо дефолтного `UNKNOWN → WARNING`. Это решает проблему "ложноположительных warning" для легитимных, но redким selector'ов (например, ERC-1155 batch-операции), не теряя security-модель "unknown = danger by default".

**3.5 Compose-уровень: Biometric-gated Animation State для "Trust Visualization"**
PRD §13 уже описывает детальную haptic-систему (H1-H4). Идея для UX-дифференциации: визуализировать guardian-граф (Layer 2: Trust) не как список (`GuardiansTab` сейчас), а как живую "constellation"-диаграмму (используя уже имеющийся `NebulaOverlay.kt` стиль) — каждый гарант — узел, который "пульсирует" при онлайн-статусе (через FCM presence) и "темнеет" при долгом отсутствии активности. Это превращает абстрактную SSS-математику в понятный пользователю mental model "кто меня защищает прямо сейчас", что прямо отвечает PRD-принципу "Trust Through Design".

---

## 4. Технические рекомендации (Technical Recommendations)

**Проблема:** `SendRepository.sendUsdt()` и `RecoveryUserOpBuilder` строят `UserOperation` без paymasterAndData в основном send-флоу, при этом весь Paymaster/Quote-инфраструктура полностью реализована на backend и в `MDAOPaymaster.sol`.
**Рекомендация:** добавить промежуточный слой `GaslessTransactionOrchestrator` между `SendViewModel` и `SendRepository`, который перед построением `UserOperation`:
1. запрашивает `/sign` quote с backend (`PaymasterClient` уже умеет энкодить, не хватает вызова),
2. подставляет `paymasterAndData` в `UserOperation`,
3. при ошибке quote (rate limit / oracle down) — деградирует на native-gas путь с явным UI-уведомлением "оплата газа из вашего баланса".
**Обоснование:** закрывает разрыв между PRD-обещанием gasless UX и текущей реализацией; деградация вместо хард-фейла сохраняет доступность сервиса при простое backend.

---

**Проблема:** Guardian on-chain методы (`addGuardian`, `confirmGuardian`, `approveRecovery`, `vetoRecovery`) не вызываются из Android-кода; `GuardianInfo.pubKeyX/pubKeyY` создаются пустыми.
**Рекомендация:** добавить `GuardianUserOpBuilder` (по образцу `RecoveryUserOpBuilder`), который:
1. при `acceptInvite()` реально генерирует P-256 ключевую пару через `PasskeyManager` (WebAuthn), а не оставляет поля пустыми,
2. строит UserOp на `registerWallet`/`addGuardian`/`confirmGuardian` с paymaster-спонсированием (PRD: "Veto = бесплатный"),
3. `approveRecovery`/`vetoRecovery` подписываются через WebAuthn assertion (`authenticatorData`+`clientDataJSON`+signature), как уже корректно реализовано в Solidity-стороне `_verifyWebAuthn`.
**Обоснование:** без этого core-фича продукта (Trusted Contacts Recovery) не функциональна end-to-end; это прямой блокер для публичного testnet, не говоря о mainnet.

---

**Проблема:** `SocialRecoveryModule.vetoRecovery` возвращает депозит initiator'у при достижении veto-порога вместо сжигания, что обнуляет anti-spam защиту, заявленную в PRD §7.
**Рекомендация:** изменить ветвь успешного veto:
```solidity
if (currentVetoes >= VETO_THRESHOLD) {
    req.vetoed = true;
    uint256 deposit = recoveryDeposit[wallet];
    if (deposit > 0) {
        recoveryDeposit[wallet] = 0;
        mdaoToken.transfer(BURN_ADDRESS, deposit); // было: transfer(req.initiator, deposit)
    }
}
```
**Обоснование:** без сжигания атакующий может спамить recovery-инициации бесконечно (теряя только газ), что превращает депозит-механизм в декоративный — прямое противоречие документированной модели угроз.

---

**Проблема:** Backend↔Contract EIP-712 Quote верификация уже однажды расходилась (F-034, Wave 9) и не покрыта сквозным интеграционным тестом — каждая сторона тестируется изолированно.
**Рекомендация:** добавить CI-шаг `contract-backend-integration` (Foundry `ffi` или anvil fork + Kotlin test harness), который:
1. поднимает локальный anvil с задеплоенным `MDAOPaymaster`,
2. реально вызывает backend `/sign` (или его pure-функцию подписи) с фиксированными тестовыми параметрами,
3. прогоняет результат через `validatePaymasterUserOp` в том же тесте, ожидая успех.
**Обоснование:** регрессия именно такого типа уже стоила проекту целой волны аудита; unit-тесты по отдельности эту категорию багов структурно не ловят, нужен сквозной тест.

---

**Проблема:** `CertificatePinner` в `NetworkModule.kt` содержит placeholder-хэши (`sha256/AAAA...=`), что гарантированно сломает весь HTTPS-трафик в проде/staging при первом реальном TLS handshake.
**Рекомендация:** вычислить реальные SPKI pin для `api.mdaopay.com`/`mdaopay.com` через `openssl s_client` + `openssl dgst -sha256`, добавить минимум 2 пина (текущий + backup на случай ротации сертификата), завести их через `BuildConfig` per-flavor (а не хардкод в `NetworkModule`), и добавить CI-проверку (smoke test на staging), что pinning не блокирует легитимный трафик перед каждым релизом.
**Обоснование:** на текущем коде любой релиз с включённым pinning'ом недоступен для сети — это блокер, а не security-улучшение в текущем виде.

---

**Проблема:** `TrustProviderRegistry.registerProvider`/`setProviderStatus` защищены простым `onlyOwner` без timelock, в то время как `MDAOPaymaster.setRegistry()` (который читает из этого registry) защищён 2-дневным `TimelockController`.
**Рекомендация:** перевести owner `TrustProviderRegistry` на тот же `TimelockController`/Gnosis Safe, что владеет `MDAOPaymaster`, либо явно задокументировать в деплой-чеклисте (`script/Deploy.s.sol`) обязательную передачу ownership сразу после деплоя, с CI-проверкой post-deploy (`cast call TrustProviderRegistry owner()` должен вернуть адрес timelock, не EOA).
**Обоснование:** иначе компрометация одного EOA-ключа в обход всех таймлоков Paymaster меняет верификатора подписи — Crypto Agility слой становится самым слабым звеном вместо самого защищённого.

---

**Проблема:** `RelayClient.RELAY_URL` захардкожен единым значением для всех окружений (dev/staging/prod), в отличие от `BACKEND_URL`, который правильно разнесён через `BuildConfig` per-flavor.
**Рекомендация:** вынести `RELAY_URL_DEV/STAGING/PROD` в `app/build.gradle.kts` аналогично существующему паттерну для `BACKEND_URL`/`RPC_URL_*`, плюс добавить retry-with-backoff и circuit breaker (по аналогии с `RpcProviderManager` cooldown-логикой) в сам `RelayClient`, чтобы guardian approve/veto не терялись на единичном network blip.
**Обоснование:** relay — единственный канал критичного recovery-флоу; смешение тестового и продового трафика в одном Worker создаёт риск порчи состояния прод-инвайтов тестовыми сценариями, а отсутствие retry делает самый важный (recovery) UX-путь наименее устойчивым к сети.

# Техническая реализация замечаний Claude

Это **блестящий аудит**. Claude нашёл 2 критических системных разрыва (2.1 и 2.2), которые не видны при изолированном тестировании слоёв. Разберу каждое замечание с техническим решением.

---

## Часть 2: Слабые стороны — техническая реализация

### 2.1 Paymaster не используется в send-флоу (КРИТИЧНО)

**Проблема:** `SendRepository.sendUsdt()` строит UserOp с пустым `paymasterAndData`. Весь Paymaster-инфра — мёртвый код.

**Решение:** `GaslessTransactionOrchestrator` — промежуточный слой между ViewModel и Repository.

**Почему это лучшее решение:**
- Не ломает существующий `SendRepository` (он продолжает работать для native-gas fallback)
- Деградация graceful: если backend `/sign` недоступен → пользователь платит BNB
- Чёткий SRP: Orchestrator отвечает за gasless-логику, Repository — за блокчейн-отправку

```kotlin
// app/.../domain/usecase/GaslessTransactionOrchestrator.kt
class GaslessTransactionOrchestrator(
    private val paymasterClient: PaymasterClient,
    private val sendRepository: SendRepository,
    private val walletManager: WalletManager
) {
    suspend fun sendUsdtGasless(
        recipient: String,
        amount: BigInteger,
        fallbackToNativeGas: Boolean = true
    ): Result<TransactionReceipt> {
        return try {
            // 1. Получить quote от backend
            val quote = paymasterClient.getQuote(
                sender = walletManager.getAddress(),
                token = Constants.USDT_ADDRESS,
                maxTokenAmount = amount
            )
            
            // 2. Построить UserOp с paymasterAndData
            val userOp = sendRepository.buildUserOp(
                recipient = recipient,
                amount = amount,
                paymasterAndData = quote.paymasterAndData // ← Ключевое: подставляем!
            )
            
            // 3. Подписать и отправить через Bundler
            sendRepository.executeUserOp(userOp)
            
        } catch (e: PaymasterQuoteException) {
            if (fallbackToNativeGas) {
                // Деградация: платим BNB
                sendRepository.sendUsdtNative(recipient, amount)
            } else {
                Result.failure(e)
            }
        }
    }
}

// UI уведомление при деградации:
// "Gasless-режим недоступен. Оплата газа из вашего BNB баланса."
```

---

### 2.2 Guardian on-chain флоу отсутствует + депозит не сжигается (КРИТИЧНО)

**Проблема A:** Android не вызывает on-chain методы. GuardianInfo.pubKeyX/Y пустые.

**Решение A:** `GuardianUserOpBuilder` — генерация P-256 ключей + on-chain регистрация.

```kotlin
// app/.../core/guardian/GuardianUserOpBuilder.kt
class GuardianUserOpBuilder(
    private val passkeyManager: PasskeyManager,
    private val bundlerClient: BundlerClient,
    private val paymasterClient: PaymasterClient // gasless для guardian ops
) {
    suspend fun acceptInviteAndRegister(invite: GuardianInvite): Result<Unit> {
        // 1. Генерируем P-256 ключ через WebAuthn
        val passkey = passkeyManager.createPasskey(
            rpId = "mdaopay.xyz",
            challenge = invite.challengeHash
        )
        
        // 2. Строим UserOp для confirmGuardian
        val userOp = UserOperation(
            sender = invite.walletAddress,
            callData = encodeConfirmGuardian(
                pubKeyX = passkey.pubKeyX,    // ← Реальные значения!
                pubKeyY = passkey.pubKeyY,
                identityHash = passkey.identityHash
            ),
            // 3. Paymaster спонсирует gas (PRD: "Veto = бесплатный")
            paymasterAndData = paymasterClient.getQuote(
                sender = invite.walletAddress,
                token = Constants.MDAO_ADDRESS,
                maxTokenAmount = BigInteger.ZERO // guardian ops бесплатные
            ).paymasterAndData
        )
        
        // 4. Отправляем через Bundler
        return bundlerClient.sendUserOperation(userOp)
    }
    
    suspend fun approveRecovery(
        wallet: String,
        newPasskeyPubKey: ByteArray,
        signature: WebAuthnAssertion
    ): Result<Unit> {
        // Подпись через WebAuthn (authenticatorData + clientDataJSON + sig)
        val userOp = UserOperation(
            sender = wallet,
            callData = encodeApproveRecovery(
                wallet = wallet,
                newPasskeyPubKey = newPasskeyPubKey,
                authenticatorData = signature.authenticatorData,
                clientDataJSON = signature.clientDataJSON,
                r = signature.r,
                s = signature.s
            ),
            paymasterAndData = paymasterClient.getQuote(...).paymasterAndData
        )
        return bundlerClient.sendUserOperation(userOp)
    }
}
```

**Проблема B:** `vetoRecovery` возвращает депозит вместо сжигания.

**Решение B:** Сжигание в контракте.

```solidity
// contracts/src/SocialRecoveryModule.sol
function vetoRecovery(
    bytes32 wallet,
    bytes calldata authenticatorData,
    bytes calldata clientDataJSON,
    uint256 r,
    uint256 s
) external {
    // ... verification ...
    
    currentVetoes++;
    emit RecoveryVetoed(wallet, msg.sender, currentVetoes);
    
    if (currentVetoes >= VETO_THRESHOLD) {
        req.vetoed = true;
        uint256 deposit = recoveryDeposit[wallet];
        if (deposit > 0) {
            recoveryDeposit[wallet] = 0;
            // БЫЛО: mdaoToken.transfer(req.initiator, deposit);
            // СТАЛО: Сжигаем!
            mdaoToken.transfer(BURN_ADDRESS, deposit);
            emit DepositBurned(wallet, deposit);
        }
    }
}
```

**Почему сжигание лучше:**
- PRD §7: «сжигается при veto» — буквально
- Атакующий теряет MDAO при каждой попытке → экономический anti-spam
- Сжигание выводит токены из обращения → дефляционный эффект для MDAO

---

### 2.3 TDD vs Code расхождение

**Решение:** Синхронизировать TDD §3.4 с фактическим кодом.

```markdown
# TDD §3.4 — обновить:
| THRESHOLD | 2 | Approvals needed for recovery |
| MAX_GUARDIANS | 3 | Maximum guardians per wallet |
| VETO_THRESHOLD | 2 | Vetoes needed to block recovery |
| Deposit | 0.01 MDAO | ERC-20 transferFrom, сжигается при veto |
| WebAuthn | RIP-7212 P-256 | Без EIP-191 prefix (raw authenticatorData) |
```

---

### 2.4 Relay SPOF

**Решение:** Per-flavor URL + Retry/Circuit Breaker.

```kotlin
// app/build.gradle.kts
android {
    productFlavors {
        create("dev") {
            buildConfigField("String", "RELAY_URL", "\"https://mdaopay-relay-dev.ekzent.workers.dev\"")
        }
        create("staging") {
            buildConfigField("String", "RELAY_URL", "\"https://mdaopay-relay-staging.ekzent.workers.dev\"")
        }
        create("prod") {
            buildConfigField("String", "RELAY_URL", "\"https://mdaopay-relay.ekzent.workers.dev\"")
        }
    }
}

// app/.../core/guardian/RelayClient.kt
class RelayClient(
    private val client: HttpClient,
    private val relayUrl: String = BuildConfig.RELAY_URL
) {
    private var failureCount = 0
    private var circuitOpenUntil: Long = 0
    
    suspend fun approveRecovery(request: ApproveRequest): Result<Unit> {
        // Circuit breaker
        if (System.currentTimeMillis() < circuitOpenUntil) {
            return Result.failure(RelayUnavailableException("Circuit open"))
        }
        
        // Retry with exponential backoff
        return retryWithBackoff(maxAttempts = 3) {
            client.post("$relayUrl/recovery/approve", request)
        }.getOrElse {
            failureCount++
            if (failureCount >= 3) {
                circuitOpenUntil = System.currentTimeMillis() + 30_000 // 30s cooldown
            }
            Result.failure(it)
        }
    }
    
    private suspend fun <T> retryWithBackoff(maxAttempts: Int, block: suspend () -> T): Result<T> {
        var lastError: Throwable? = null
        for (attempt in 0 until maxAttempts) {
            try {
                return Result.success(block())
            } catch (e: Exception) {
                lastError = e
                delay((1000L shl attempt)) // 1s, 2s, 4s
            }
        }
        return Result.failure(lastError!!)
    }
}
```

---

### 2.5 Нет сквозного интеграционного теста EIP-712

**Решение:** Foundry `ffi` + Anvil fork.

```solidity
// contracts/test/integration/BackendContractIntegration.t.sol
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "forge-std/Test.sol";
import "../src/MDAOPaymaster.sol";

contract BackendContractIntegrationTest is Test {
    MDAOPaymaster paymaster;
    
    function setUp() public {
        // Деплоим на локальный anvil
        paymaster = new MDAOPaymaster(
            0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789, // EntryPoint
            address(this) // temporary trustedSigner
        );
    }
    
    function testBackendSigns_ContractVerifies() public {
        // 1. Вызываем backend /sign через forge ffi
        string[] memory cmds = new string[](3);
        cmds[0] = "bash";
        cmds[1] = "-c";
        cmds[2] = string.concat(
            "curl -s -X POST http://localhost:8080/sign ",
            "-H 'Content-Type: application/json' ",
            "-d '{\"sender\":\"0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266\",",
            "\"nonce\":\"0x0\",...}' | jq -r '.paymasterAndData'"
        );
        
        bytes memory paymasterAndData = vm.ffi(cmds);
        
        // 2. Строим UserOp с этим paymasterAndData
        UserOperation memory userOp = UserOperation({
            sender: 0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266,
            nonce: 0,
            // ... остальные поля ...
            paymasterAndData: paymasterAndData,
            signature: bytes("")
        });
        
        // 3. Вызываем validatePaymasterUserOp — должно пройти
        bytes32 userOpHash = keccak256(abi.encode(userOp));
        (bytes memory context, uint256 validationData) = 
            paymaster.validatePaymasterUserOp(userOp, userOpHash, 0);
        
        // 4. Assert: validationData == 0 (success)
        assertEq(validationData, 0, "Backend signature must verify on-chain");
    }
}
```

**Почему это лучшее решение:**
- `forge ffi` позволяет вызывать внешние команды (backend) из Foundry теста
- Тест гоняет реальный backend → реальный контракт → ни одного мока
- Любое расхождение EIP-712 domain (version, chainId, verifyingContract) → тест падает
- CI: поднять anvil + backend → запустить тест → убедиться в совместимости

---

## Часть 3: Идеи — техническая реализация

### 3.1 Risk-scored Session Keys

**Реализация:** Расширение SessionKey struct.

```solidity
// contracts/src/SessionKeyModule.sol
struct Session {
    address dApp;
    Capability[] capabilities;
    uint256 maxAmountPerTx;
    uint256 dailyLimit;
    address[] allowedTokens;
    uint256 expiresAt;
    bool active;
    uint8 riskTier;        // ← НОВОЕ: 0=LOW, 1=MEDIUM, 2=HIGH
    uint256 successCount;  // ← НОВОЕ: история успешных ops
}

function executeWithSession(
    address sessionKey,
    address target,
    bytes calldata data,
    uint256 value
) external nonReentrant {
    Session storage s = sessions[sessionKey];
    require(s.active && block.timestamp < s.expiresAt);
    
    // Dynamic risk scoring
    uint256 dynamicLimit = s.maxAmountPerTx;
    uint8 requiredAuthTier = 1; // MEDIUM по умолчанию
    
    if (s.riskTier == 0 && s.successCount > 10) {
        // LOW risk + 10+ успешных ops → повышаем лимит на 20%
        dynamicLimit = dynamicLimit * 120 / 100;
        requiredAuthTier = 0; // не требовать биометрию
    }
    
    require(value <= dynamicLimit, "ExceedsDynamicLimit");
    
    // ... execute ...
    s.successCount++;
}
```

**Почему:** Apple Pay работает так для transit mode (маленькие суммы без Face ID). Для MDAOPay это значит: повторные низко-рисковые операции (например, подписка на сервис) не требуют Face ID каждый раз → лучше UX.

---

### 3.2 Paymaster Multi-Sponsor Auction

**Реализация:** Dutch auction для спонсоров.

```solidity
// contracts/src/MultiSponsorPaymaster.sol (Phase 1)
struct Sponsor {
    address token;      // Токен спонсора (Arena, DEX)
    uint256 stakedMDAO; // Залог в MDAO
    uint256 gasCovered; // Сколько газа уже спонсировано
    uint256 maxGasPerDay;
}

mapping(address => Sponsor) public sponsors;

// Спонсор stake'ит MDAO за право спонсировать газ
function registerSponsor(
    address token,
    uint256 mdaoStake,
    uint256 maxGasPerDay
) external {
    require(mdaoStake >= MIN_STAKE, "Insufficient stake");
    sponsors[msg.sender] = Sponsor(token, mdaoStake, 0, maxGasPerDay);
    mdaoToken.transferFrom(msg.sender, address(this), mdaoStake);
}

// Paymaster выбирает спонсора по Dutch auction (кто больше stake'ил)
function _selectSponsor(address user) internal view returns (address) {
    // Логика: спонсор с наибольшим stakedMDAO и остатком daily quota
    // Spread (разница между реальным газом и курсом токена) → InsuranceFund
}
```

**Почему:** Превращает Paymaster из cost-center в revenue-share. Экосистемы (Arena) платят MDAO за право спонсировать газ своих пользователей. Это усиливает North Star метрику (количество экосистем).

---

### 3.3 Dead Man Switch как fallback recovery

**Реализация:** Интеграция DMS в recovery flow.

```solidity
// contracts/src/SocialRecoveryModule.sol
uint256 public constant SOFT_ESCALATION_PERIOD = 7 days;
uint256 public constant SOFT_ESCALATION_THRESHOLD = 1; // 1-of-3 вместо 2-of-3

function executeRecovery(bytes32 wallet) external {
    RecoveryRequest storage req = recoveries[wallet];
    
    if (req.approvals >= GUARDIAN_THRESHOLD) {
        // Нормальный путь: 2-of-3, 48h timelock
        require(block.timestamp >= req.startedAt + TIMELOCK);
    } else if (req.approvals >= SOFT_ESCALATION_THRESHOLD && 
               block.timestamp >= req.startedAt + SOFT_ESCALATION_PERIOD) {
        // Soft escalation: 1-of-3, 7 дней
        // Требует дополнительно OAuth-верификацию (Google/Apple)
        require(oAuthVerified[wallet][msg.sender], "OAuth required for soft escalation");
    } else {
        revert("Recovery conditions not met");
    }
    
    // ... execute ...
}
```

**Почему:** Закрывает edge-case «оба гаранта недоступны». Пользователь не теряет доступ навсегда. OAuth = auxiliary (не владеет кошельком, только подтверждает личность для soft escalation).

---

### 3.4 Verifiable On-chain Attestation для Capability Mapping

**Реализация:** AttestationLedger для dApp mappings.

```solidity
// contracts/src/AttestationLedger.sol
function attestDAppMapping(
    address dApp,
    bytes4 selector,
    uint8 capability,  // 0=ProfileRead, 1=PaymentsSend, etc.
    bool isVerified
) external onlyAttestor {
    bytes32 key = keccak256(abi.encodePacked(dApp, selector));
    dAppMappings[key] = DAppMapping(capability, isVerified, block.timestamp);
}

// Client запрашивает: для dApp X, selector Y → какой capability?
function getDAppMapping(address dApp, bytes4 selector) 
    external view returns (uint8 capability, bool isVerified) 
{
    bytes32 key = keccak256(abi.encodePacked(dApp, selector));
    DAppMapping memory m = dAppMappings[key];
    return (m.capability, m.isVerified);
}
```

```kotlin
// app/.../SessionKeyManager.kt
suspend fun resolveCapability(dApp: String, selector: ByteArray): Capability {
    // 1. Проверяем on-chain attestation
    val onChain = attestationLedger.getDAppMapping(dApp, selector)
    if (onChain.isVerified) {
        return Capability.fromId(onChain.capability)
    }
    
    // 2. Fallback на client-side PermissionMapper
    return permissionMapper.mapSelector(selector) ?: Capability.UNKNOWN
}
```

**Почему:** Решает проблему false-positive WARNING для легитимных, но редких selectors. Community-verified mappings → доверие выше, чем client-side эвристика.

---

### 3.5 Trust Visualization (Compose)

**Реализация:** Constellation diagram.

```kotlin
// app/.../ui/recovery/TrustConstellation.kt
@Composable
fun TrustConstellation(
    guardians: List<GuardianInfo>,
    recoveryStatus: RecoveryStatus?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(Brush.radialGradient(
                colors = listOf(MDAOPayDarkBlue, Color.Black)
            ))
    ) {
        // Центральный узел — пользователь
        UserNode(
            modifier = Modifier.align(Alignment.Center),
            status = recoveryStatus?.toNodeStatus() ?: NodeStatus.Active
        )
        
        // Guardian узлы по кругу
        guardians.forEachIndexed { index, guardian ->
            val angle = (360f / guardians.size) * index
            GuardianNode(
                guardian = guardian,
                angle = angle,
                isPulsing = guardian.isOnline,        // FCM presence
                isDimmed = guardian.lastActiveDaysAgo > 30,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        
        // Линии связей
        Canvas(modifier = Modifier.fillMaxSize()) {
            guardians.forEach { guardian ->
                drawLine(
                    color = if (guardian.isOnline) MDAOPayAccent else Color.Gray.copy(alpha = 0.3f),
                    start = center,
                    end = calculateGuardianPosition(guardian, center),
                    strokeWidth = if (guardian.isOnline) 2.dp.toPx() else 1.dp.toPx()
                )
            }
        }
    }
}
```

**Почему:** Превращает абстрактную математику SSS в визуальную метафору. Пользователь видит: «Эти 3 человека защищают меня. Двое активны, один давно не заходил». Это снижает anxiety (PRD §3.3: «Human First»).

---

## Часть 4: Технические рекомендации — реализация

### 4.1 GaslessTransactionOrchestrator
(см. 2.1 выше)

### 4.2 GuardianUserOpBuilder + сжигание депозита
(см. 2.2 выше)

### 4.3 Сжигание депозита при veto
(см. 2.2 выше)

### 4.4 Сквозный интеграционный тест EIP-712
(см. 2.5 выше)

### 4.5 CertificatePinner — реальные SPKI pins

```bash
# Получить реальный SPKI pin:
echo | openssl s_client -connect api.mdaopay.com:443 2>/dev/null \
    | openssl x509 -pubkey -noout \
    | openssl pkey -pubin -outform der \
    | openssl dgst -sha256 -binary \
    | openssl enc -base64
# Вывод: sha256/ABCdef123...=
```

```kotlin
// app/build.gradle.kts
buildConfigField("String", "CERT_PIN_API", "\"sha256/ABCdef123...=\"")
buildConfigField("String", "CERT_PIN_BACKUP", "\"sha256/XYZ789...=\"") // backup CA

// app/.../di/NetworkModule.kt
val pinner = CertificatePinner.Builder()
    .add("api.mdaopay.com", BuildConfig.CERT_PIN_API)
    .add("api.mdaopay.com", BuildConfig.CERT_PIN_BACKUP) // backup
    .build()
```

### 4.6 TrustProviderRegistry → TimelockController

```solidity
// script/Deploy.s.sol
TimelockController timelock = new TimelockController(
    48 hours,       // delay
    proposers,      // [deployer]
    executors,      // [address(0)] = anyone can execute
    address(0)      // admin
);

TrustProviderRegistry registry = new TrustProviderRegistry();
registry.transferOwnership(address(timelock)); // ← Ключевое!

// Post-deploy verification
require(registry.owner() == address(timelock), "Registry not owned by timelock");
```

---

## Задание для opencode swarm

Скопируй и отправь координатору:

```text
/agent coordinator
рассмотри внешний аудит

Где файл с аудитом? Вставляю текстом ниже.

# Внешний аудит: Claude Opus — критические разрывы и улучшения

## ПРИОРИТЕТ 1: Критические разрывы (блокеры testnet)

### F-100: Paymaster не используется в send-флоу
SendRepository.sendUsdt() строит UserOp без paymasterAndData.
Решение: GaslessTransactionOrchestrator между SendViewModel и SendRepository.
1. Запросить /sign quote от backend
2. Подставить paymasterAndData в UserOp
3. При ошибке — fallback на native-gas с UI уведомлением

### F-101: Guardian on-chain флоу отсутствует в клиенте
GuardianManager оперирует через RelayClient, on-chain методы не вызываются. pubKeyX/Y пустые.
Решение: GuardianUserOpBuilder
1. Генерация P-256 ключа через PasskeyManager при acceptInvite
2. UserOp на confirmGuardian с реальными pubKeyX/Y
3. approveRecovery/vetoRecovery через WebAuthn assertion

### F-102: Депозит не сжигается при veto
SocialRecoveryModule.vetoRecovery возвращает депозит initiator'у.
Решение: Изменить на mdaoToken.transfer(BURN_ADDRESS, deposit)

### F-103: TDD vs Code расхождение (THRESHOLD, MAX_GUARDIANS, WebAuthn)
TDD §3.4: THRESHOLD=3, MAX_GUARDIANS=5, EIP-191
Code: THRESHOLD=2, MAX_GUARDIANS=3, raw WebAuthn
Решение: Обновить TDD §3.4 на 2/3/2 + raw WebAuthn + депозит в MDAO

## ПРИОРИТЕТ 2: Инфраструктурные риски

### F-104: Relay SPOF — единый URL без retry
Решение:
1. RELAY_URL per-flavor в build.gradle.kts (dev/staging/prod)
2. Circuit breaker + retry with backoff в RelayClient

### F-105: Нет сквозного интеграционного теста EIP-712
Решение: Foundry ffi тест (BackendContractIntegration.t.sol)
1. Поднять anvil + backend
2. Вызвать /sign через forge ffi
3. Передать paymasterAndData в validatePaymasterUserOp
4. Assert validationData == 0

### F-106: CertificatePinner placeholder хэши
Решение:
1. Вычислить реальные SPKI pins
2. Добавить через BuildConfig per-flavor
3. Backup pin на случай ротации

### F-107: TrustProviderRegistry без timelock
Решение:
1. Deploy TimelockController отдельно
2. registry.transferOwnership(timelock)
3. Post-deploy verification в Deploy.s.sol

## ПРИОРИТЕТ 3: Улучшения (post-testnet)

### I-50: Risk-scored Session Keys
Добавить riskTier + successCount в Session struct. Динамический лимит для LOW risk.

### I-51: Dead Man Switch как fallback recovery
Soft escalation: 1-of-3 + 7 days + OAuth verification.

### I-52: Trust Visualization (Compose)
Constellation diagram для guardian графа.

## Порядок выполнения

### Фаза 1: Критические фиксы (P1)
1. Task(architect): спецификация F-100..F-103
2. Task(implementer): F-102 (сжигание депозита) + F-103 (TDD sync) — быстро
3. Task(implementer): F-100 (GaslessTransactionOrchestrator)
4. Task(implementer): F-101 (GuardianUserOpBuilder)
5. Task(tester): тесты на все P1
6. Task(tdd-updater): обновить TDD

### Фаза 2: Инфраструктура (P2)
7. Task(implementer): F-104 (Relay per-flavor + retry)
8. Task(implementer): F-105 (сквозной тест)
9. Task(implementer): F-106 (CertPinner)
10. Task(implementer): F-107 (Registry timelock)
11. Task(tester): проверить P2

### Фаза 3: Улучшения (P3, post-testnet)
12. Task(architect): I-50, I-51, I-52 спецификации
13. (после testnet) Task(implementer): I-50..I-52

Жду подтверждения и спецификацию от architect для Фазы 1.
```
Вот аудит твоего решения: # Ревью предложенных технических решений

Здесь хорошая структура и правильное направление мысли, но **значительная часть кода не пройдёт компиляцию/ревью против реальных сигнатур контрактов и существующих классов** — это типичная проблема AI-generated патчей, написанных по описанию проблемы, а не по факту чтения файла. Разберу по пунктам — что оставить, что переписать, и почему.

---

## 2.1 GaslessTransactionOrchestrator — направление верное, реализация наивная

**Критическая ошибка:** `paymasterClient.getQuote(...)` в коде предполагает, что это сетевой вызов к backend `/sign`. Но реальный `PaymasterClient.encodePaymasterAndData()` в репозитории — это **чисто локальная функция упаковки байт**, без HTTP-вызова и без подписи:
```kotlin
// реальный код, core/blockchain/paymaster/PaymasterClient.kt
fun encodePaymasterAndData(token: String, maxTokenAmount: BigInteger, ...): ByteArray {
    // ... просто паддинг адресов/чисел, quoteDeadline = now+300s локально
    return paymasterAddress + tokenBytes + maxAmountBytes + quoteDeadlineBytes
}
```
Контракт же в `_verifySignerIfConfigured` ожидает **суффикс с подписью backend** (`sigHex || lenHex || magic`, `_SUFFIX_LEN = 75 bytes`). Если orchestrator вызовет текущий `encodePaymasterAndData` и вставит результат в UserOp — `validatePaymasterUserOp` упадёт с `InvalidSigner()` на каждом вызове, потому что суффикса с подписью просто нет.

**Что поправить:**
1. Нужен **новый метод** в `PaymasterClient` (или новый `PaymasterApiClient`), который реально делает `POST /sign` к backend (Ktor client, см. TDD §1.2.1), получает в ответе `paymasterAndData` (уже с подписью), `userOpHash`, `maxFee` — и именно его подставлять в UserOp. Текущий `encodePaymasterAndData` — это вспомогательная функция для построения **запроса** к `/sign`, а не финальных данных для UserOp.
2. `PaymasterQuoteException` в коде orchestrator-а не существует ни в одном файле — нужно завести явный sealed-тип ошибок (`PaymasterError.RateLimited`, `PaymasterError.OracleDown`, `PaymasterError.NetworkTimeout`), а не ловить один общий тип, иначе `catch` не будет работать как задумано.
3. **UX-проблема, не упомянутая в решении:** quote имеет `quoteDeadline` (300s буфер на backend, см. `minimumDeadlineBuffer`). Между получением quote и фактической отправкой UserOp проходит: подтверждение биометрии пользователем + сборка/подпись UserOp + отправка в bundler + время на майнинг. Если пользователь долго подтверждает Face ID — quote протухнет, и транзакция ревертнётся с `QuoteExpired()` **уже после оплаты комиссии** (если bundler успел её включить) — то есть это не бесплатный retry. Нужно: запрашивать quote **после** биометрии, не до неё, либо инвалидировать и перезапрашивать quote прямо перед сборкой final UserOp.
4. Silent fallback на native-gas — нарушает PRD §3.2 ("Simplicity First", пользователь не должен внезапно узнавать, что платит газ из BNB, только когда что-то сломалось). Нужно: если gasless недоступен **на этапе quote**, показать это explicit в UI *до* подтверждения суммы (на экране Confirmation), а не делать silent degrade после попытки.

---

## 2.2.A GuardianUserOpBuilder — здесь самая серьёзная техническая ошибка во всём документе

Код вызывает несуществующую функцию `encodeConfirmGuardian(pubKeyX, pubKeyY, identityHash)`. Смотрим на реальный контракт:

```solidity
// реальный SocialRecoveryModule.sol
function addGuardian(address wallet, bytes32 identityHash, bytes32 pubKeyX, bytes32 pubKeyY)
    external onlyWalletOwner(wallet)   // ← вызывает ВЛАДЕЛЕЦ кошелька, не гарант!

function confirmGuardian(address wallet) external {
    bytes32 identityHash = keccak256(abi.encodePacked(msg.sender));  // ← без параметров вообще!
    ...
}
```

**Проблема:** в реальном контракте это **два разных вызова от двух разных подписантов**:
- `addGuardian` — владелец кошелька регистрирует гаранта с его pubKeyX/Y (которые он должен получить *заранее*, off-chain, через relay-инвайт).
- `confirmGuardian` — сам гарант подтверждает роль, **подписывая транзакцию своим собственным аккаунтом** (`msg.sender`), без каких-либо параметров — `identityHash` выводится из адреса вызывающего, а не передаётся.

Предложенный `GuardianUserOpBuilder.acceptInviteAndRegister()` смешивает оба шага в один UserOp с одним sender'ом — это физически невозможно реализовать как написано: либо это будет вызов `addGuardian` от имени гаранта (упадёт на `onlyWalletOwner`, потому что гарант — не владелец восстанавливаемого кошелька), либо `confirmGuardian` — но тогда pubKeyX/pubKeyY передавать туда незачем, контракт их не принимает на этом шаге.

**Скрытая продуктовая проблема, которую это обнажает:** `confirmGuardian` требует у гаранта **собственный смарт-аккаунт** (msg.sender = гарант), который должен существовать на момент подтверждения. Это означает: **ваш гарант обязан сам быть пользователем MDAOPay** (или минимум иметь задеплоенный smart account на той же сети) — этого нет ни в PRD, ни в онбординге UI (`OnboardingGuardianScreen.kt` просто пикает контакт из телефонной книги, без проверки, есть ли у него кошелёк). Это блокер сам по себе, и предложенное решение его не вскрывает, а маскирует неработающим кодом.

**Что переделать:**
1. Разделить на два явных шага в `GuardianUserOpBuilder`:
   - `inviteGuardian()` — собирает UserOp от **владельца** на `addGuardian(wallet=ownerAddress, identityHash, pubKeyX, pubKeyY)`, где `pubKeyX/Y` получены через relay-инвайт (P-256 ключ гарант генерирует на *своём* устройстве при принятии инвайта, до этого шага).
   - `confirmAsGuardian()` — отдельный UserOp **от смарт-аккаунта гаранта**, вызывающий `confirmGuardian(wallet=ownerAddress)` без параметров.
2. Добавить явную проверку в `RelayClient`/`GuardianManager`: при `acceptInvite()` — если у принимающего гаранта ещё нет смарт-аккаунта MDAOPay, инициировать его onboarding (deep link на установку приложения), либо явно задокументировать в PRD ограничение "гарант обязан иметь MDAOPay".
3. Про "guardian ops бесплатные" с `maxTokenAmount = BigInteger.ZERO`: посмотрите на `computeAmountToCharge`:
```solidity
function computeAmountToCharge(uint256 maxTokenAmount, uint256 actualGasCost, address tokenAddr) ... {
    uint256 actualTokenAmount = actualGasCost * price / 1e18;
    if (actualTokenAmount >= maxTokenAmount) return (maxTokenAmount, 0); // charge = maxTokenAmount = 0
    ...
}
```
При `maxTokenAmount=0` charge всегда будет `0` (capped), и `postOp` попытается `transferFrom(sender, this, 0)` — это пройдёт, но **Paymaster не получит компенсации газа вообще**, то есть треasury теряет реальные BNB на каждый guardian-вызов без покрытия. Это нормально *только если* это осознанная субсидия казны (PRD упоминает "Onboarding (first 3 tx) — MDAOPay treasury" как прецедент), но нужно: явный лимит на количество бесплатных guardian-операций на кошелёк/день (иначе спам confirmGuardian/addGuardian — дешёвая DoS-атака на treasury, т.к. подпись от backend `/sign` всё равно нужна, но цена 0 не отсеивает спам сама по себе — нужен rate-limit конкретно под этот случай, отдельный от общего `/sign` rate limit).

---

## 2.2.B Сжигание депозита — логика верна, но сигнатура функции придумана, а механизм сжигания неверен экономически

Сигнатура в патче (`vetoRecovery(bytes32 wallet, bytes calldata authenticatorData, ..., uint256 r, uint256 s)`) не соответствует реальной:
```solidity
function vetoRecovery(address wallet, bytes32 guardianIdentityHash, bytes calldata authenticatorData, bytes calldata clientDataJSON, bytes calldata p256Signature) external
```
`wallet` — `address`, не `bytes32`; подпись передаётся как один `bytes calldata p256Signature` (64 байта r||s), а не раздельные `uint256 r, uint256 s`. Diff, написанный против несуществующей сигнатуры, никто не сможет применить как patch — это нужно переписать с реальным кодом перед передачей в swarm, иначе implementer-агент потратит цикл на угадывание.

**Более важная техническая претензия:** `mdaoToken.transfer(BURN_ADDRESS, deposit)` отправляет токены на мёртвый адрес, но **не уменьшает `totalSupply()`**, потому что это не вызов `burn()`. `MDAOToken` уже наследует `ERC20Burnable` — у него есть метод `burn(uint256 amount)`, который жжёт *баланс вызывающего*. Поскольку депозит в момент veto лежит на балансе самого `SocialRecoveryModule`, контракт может (и должен) вызвать:
```solidity
MDAOToken(address(mdaoToken)).burn(deposit); // уменьшает totalSupply, эмитит Transfer(this, 0x0, deposit)
```
вместо `transfer(BURN_ADDRESS, deposit)`. Разница принципиальна для CoinGecko/индексаторов circulating supply и для прозрачности — "сжигание" в строгом смысле means total supply изменился, а не "токены лежат на недоступном адресе" (тем более что у `0x...dEaD` нет криптографической гарантии недоступности — это конвенция, а не invariant). Учитывая, что `MDAOToken._update` уже имеет встроенный burn-механизм через `isExempt`, нужно решить: либо `SocialRecoveryModule` должен быть в `isExempt`, либо явно дёргать `.burn()`.

---

## 2.3 TDD-синхронизация — ок, но список неполный

Помимо THRESHOLD/MAX_GUARDIANS/WebAuthn, в синхронизацию обязательно нужно включить:
- Тип депозита: TDD до сих пор не указывает, что это **ERC-20 MDAO через `transferFrom`** (а не native ETH/BNB, как было в `FP-RECOVERY-001` шаблоне с `payable`/`msg.value`). Это меняет UX-флоу — пользователю нужен approve на MDAO перед `initiateRecovery`, что не отражено ни в `RecoveryScreen.kt`, ни в TDD.
- `EXECUTION_WINDOW = 48 hours` в реальном коде (не 7 days, как в части документации/findings).

---

## 2.4 Relay retry/circuit-breaker — концепция верная, реализация не потокобезопасна

```kotlin
private var failureCount = 0
private var circuitOpenUntil: Long = 0
```
Это поля простого класса, вызываемого из множественных корутин (Hilt singleton, как и реальный `RelayClient`). Без `AtomicInteger`/`AtomicLong` или `Mutex` — race condition при параллельных вызовах (`approveRecovery` и `acceptInvite` одновременно из разных частей UI).

**Также:** один глобальный circuit breaker на весь `RelayClient` — это излишне грубо. Если упадёт low-priority endpoint (`/push/register`), он заблокирует критичный `/recovery/approve` на следующие 30 секунд. Нужен circuit breaker **по эндпоинту**, не по клиенту целиком.

**И:** `retryWithBackoff` ретраит **все** ошибки одинаково, включая клиентские (4xx — например, "уже подтверждено", "invite expired"). Ретраить 4xx — это 3x бесполезная задержка (1s+2s+4s = 7 секунд) на критичном пути recovery, где каждая секунда на счету. Нужно различать retryable (timeout, 5xx, connection refused) и terminal (4xx) ошибки **до** входа в retry-цикл.

---

## 2.5 Forge ffi интеграционный тест — нарушает собственные ограничения CI проекта

TDD §4.3 явно фиксирует для contracts job: *"Constraints: 30 min timeout per job, **no external dependencies**"*. Предложенный тест:
1. Требует `--ffi` флаг Foundry (по умолчанию отключён в большинстве CI-раннеров как security risk — произвольное выполнение shell-команд из теста).
2. Требует **поднятый backend на localhost:8080** как внешнюю зависимость прямо во время `forge test` — это противоречит документированному ограничению "no external dependencies" для этого CI job.
3. `validatePaymasterUserOp` имеет `onlyEntryPoint` модификатор — вызов из теста без `vm.prank(entryPoint)` ревертнётся с `Unauthorized()` независимо от корректности подписи. В сниппете прыжок этот пропущен.
4. UserOp в тесте собран с нулевыми `callGasLimit`/`verificationGasLimit`/`preVerificationGas` — это не баг теста, но искажает реалистичность проверки (нулевые газ-лимиты могут давать другой код пути в `_validateFields`).

**Правильная альтернатива:** не смешивать integration-тест с unit-suite контрактов. Варианты:
- **Kotlin-side integration test:** backend поднимается в Testcontainers/docker-compose как часть backend-тестов, реальный anvil-форк через web3j вызывается из Kotlin-теста (направление зависимости: backend-тест → anvil, а не contracts-тест → backend через curl). Это естественнее, не требует `--ffi`.
- Либо: зафиксировать "fixture"-подход — один раз вручную записать реальный ответ backend `/sign` для known test vector, закоммитить как JSON-fixture в `contracts/test/fixtures/`, и Foundry-тест проверяет именно этот зафиксированный байтовый ответ против `validatePaymasterUserOp` (без живого backend, без ffi) — а отдельный nightly/manual job дополнительно прогоняет live integration test вне основного CI gate.

---

## 3.1 Risk-scored Session Keys — реальная уязвимость в самой идее ("reputation farming")

`s.successCount > 10` как условие для повышения лимита и снижения требуемого auth-tier — классическая дыра: атакующий (или скомпрометированный dApp) проводит 11 мелких легитимных операций специально, чтобы разблокировать +20% лимит и сниженный auth-tier, затем на 12-й операции выводит по максимуму. Это особенно опасно, потому что `successCount` ничем не лимитирован по времени (можно накрутить за минуты).

Также есть архитектурная путаница: в текущей модели `SessionKeyModule` биометрия проверяется **один раз при создании сессии** (`ConnectViewModel.connect()` → `biometricAuth.authenticate()`), а не на каждое исполнение — dApp потом действует без повторных промптов в рамках лимитов сессии. Поле `requiredAuthTier`, которое "снижается" для повторных операций, не соответствует этой модели — там просто нет per-execution auth, который можно было бы понижать.

**Что поправить:** если делать риск-скоринг — то только для модуляции `spendingLimit`/`dailyLimit` (числовых лимитов), не для auth-требований. И score должен расти медленно с time-decay (например, не более +1 в день, обнуление при подозрительном паттерне — резкий скачок суммы, смена allowedTokens), а не простым счётчиком успехов.

---

## 3.2 "Dutch auction" — терминология неточна, но идея рабочая

Классический Dutch auction — это убывающая цена для единичного лота. Описанный механизм — это **staking-based allocation с квотами**, не аукцион в строгом смысле. Рекомендую переименовать в спеке, чтобы не сбивать будущих имплементеров с пути при реализации (название определяет архитектуру — implementer, ожидая аукцион, может начать писать ценовой decay-механизм, которого тут не нужно).

`_selectSponsor` — пустая заглушка, что нормально для будущей фазы, но нужно сразу зафиксировать: что происходит при `gasCovered >= maxGasPerDay` у всех спонсоров одновременно (fallback на native paymaster-режим? отказ транзакции?) — иначе на стадии имплементации это станет открытым вопросом архитектуры, а не деталью реализации.

---

## 3.3 Dead Man Switch как fallback recovery — серьёзный регресс безопасности, не готов к принятию как есть

Это самое спорное предложение во всём материале, и я бы **не пропускал его в Фазу 3 без отдельного threat-model ревью**:

1. **Несуществующие сущности в коде:** `recoveries[wallet]` с `bytes32` ключом — в реальном контракте маппинг называется `pendingRecovery[wallet]` и ключ `address`. `oAuthVerified[wallet][msg.sender]` — такого маппинга нет нигде в кодовой базе, его пришлось бы создавать с нуля, и это означает **появление нового источника истины для recovery — backend, который подтверждает OAuth и пишет on-chain**.
2. **Прямое противоречие ADR-021** ("OAuth = auxiliary", не владеет кошельком) и принципу PRD §2 ("Recovery работает без Google"). Снижение порога с 2-of-3 guardians до 1-of-3 + OAuth-подтверждение **превращает Google/Apple-аккаунт в де-факто второй фактор восстановления** — а фишинг Google-аккаунта статистически куда более вероятен, чем компрометация двух независимых физических гарантов одновременно. Это не "fallback для edge-case", это **новый, более слабый путь восстановления**, который атакующий будет предпочитать первым.
3. Даже если оставить идею — необходимо: дополнительный timelock (точно не 7 дней, скорее 30), уведомления **всем** гарантам (не только инициатору) с возможностью моментального veto в течение soft escalation, и привязка не к одному OAuth-провайдеру, а к нескольким независимым факторам.

**Рекомендация:** вынести в отдельный RFC/security review с привлечением внешнего аудитора, не включать в обычный implementation backlog как "I-51 improvement".

---

## 3.4 Attestation для capability mapping — концепция ок, не хватает децентрализации

`onlyAttestor` — единая роль без мультисига/таймлока для модификации mapping'ов, которые напрямую влияют на то, увидит ли пользователь WARNING на потенциально опасный selector. Компрометация одного attestor-ключа = возможность скрыть `APPROVE_TOKENS`/`NFT_TRANSFER` как "verified safe" для произвольного dApp, что хуже текущего client-side fallback (где неизвестный selector **всегда** даёт WARNING). Нужно: либо мультисиг на `onlyAttestor`, либо механизм community challenge/dispute (кто угодно может оспорить attestation с депозитом, как в InsuranceFund claims).

Также нет revocation/deprecation механизма для mapping, если dApp скомпрометирован после первичной attestation.

---

## 4.5 / 4.6 — без претензий

CertificatePinner-команда и схема с `TimelockController(admin=address(0))` — корректны и соответствуют рекомендациям OZ. Единственное уточнение к 4.6: `proposers` массив в скрипте показан как `[deployer]` — это EOA-предложение для timelock-операций по `TrustProviderRegistry`, что воспроизводит ту же проблему единой точки отказа, которую мы пытаемся закрыть. `proposers` должен быть тот же Gnosis Safe (3-of-5), что владеет `MDAOPaymaster`, а не deployer-EOA — иначе timelock защищает от "мгновенной" атаки, но не от компрометации единственного ключа, который может *предложить* (queue) злонамеренную операцию.

---

## Сводный список: что обязательно переписать перед отправкой координатору

| № | Проблема | Действие |
|---|----------|----------|
| 1 | `PaymasterClient.getQuote()` не существует, `encodePaymasterAndData()` не подписан backend'ом | Добавить реальный HTTP-клиент к `/sign`, не путать локальное энкодирование с подписанным quote |
| 2 | `encodeConfirmGuardian` вызывает несуществующую функцию, смешивает addGuardian/confirmGuardian | Разделить на 2 UserOp от 2 разных подписантов; явно зафиксировать требование "гарант = пользователь MDAOPay" |
| 3 | `vetoRecovery` diff написан против несуществующей сигнатуры | Переписать против реальной `vetoRecovery(address, bytes32, bytes, bytes, bytes)` |
| 4 | "Сжигание" через `transfer(BURN_ADDRESS,...)` не уменьшает totalSupply | Заменить на `MDAOToken.burn(deposit)` |
| 5 | Forge ffi тест нарушает CI-constraint "no external dependencies", забыт `vm.prank(entryPoint)` | Перенести в Kotlin-side integration test или fixture-based подход без `--ffi` |
| 6 | Relay circuit breaker не потокобезопасен, ретраит 4xx | `AtomicInteger`/per-endpoint breaker, различать retryable/terminal ошибки |
| 7 | Risk-scored Session Keys гарантируют reputation farming атаку | Time-decay score, не убирать auth полностью, только модулировать лимиты |
| 8 | Dead Man Switch fallback — security regression, противоречит ADR-021 | Отдельный RFC + внешний security review, не в общий backlog |
| 9 | `proposers=[deployer]` в timelock-скрипте — SPOF, который мы и пытаемся устранить | `proposers` = тот же Safe-мультисиг, что владеет Paymaster |

После этих правок материал готов передавать координатору — концептуально направление верное везде, но **implementer-агент на основе текущих сниппетов потратит циклы на компиляционные ошибки и неработающую логику**, а не на реальную доработку.

# Исправленная техническая реализация v2 (по аудиту)

Аудитор абсолютно прав по всем 9 пунктам. Я написал код против выдуманных сигнатур — это блокер для implementer-агента. Ниже — переписанные решения против **реальных** сигнатур контрактов и классов.

---

## 1. PaymasterApiClient (вместо несуществующего getQuote)

**Реальная проблема:** `PaymasterClient.encodePaymasterAndData()` — локальная упаковка байт. Backend `/sign` возвращает `paymasterAndData` с суффиксом подписи (`sigHex || lenHex || magic`, 75 bytes). Нужен HTTP-клиент.

```kotlin
// app/.../data/api/PaymasterApiClient.kt (НОВЫЙ КЛАСС)
class PaymasterApiClient(
    private val client: HttpClient,
    @Named("backendUrl") private val baseUrl: String = BuildConfig.BACKEND_URL
) {
    suspend fun requestSign(request: SignRequest): Result<SignResponse> {
        return try {
            val response: SignResponse = client.post("$baseUrl/sign") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
            Result.success(response)
        } catch (e: ClientRequestException) {
            when (e.response.status) {
                HttpStatusCode.TooManyRequests -> Result.failure(PaymasterError.RateLimited)
                HttpStatusCode.BadRequest -> Result.failure(PaymasterError.InvalidRequest)
                else -> Result.failure(PaymasterError.ServerError(e.message))
            }
        } catch (e: HttpRequestTimeoutException) {
            Result.failure(PaymasterError.NetworkTimeout)
        } catch (e: Exception) {
            Result.failure(PaymasterError.Unknown(e.message ?: "Unknown"))
        }
    }
}

// Точное соответствие TDD §1.2.1
@Serializable
data class SignRequest(
    val sender: String,
    val nonce: String,           // hex string
    val initCode: String,        // "0x"
    val callData: String,        // hex string
    val verificationGasLimit: String,
    val callGasLimit: String,
    val preVerificationGas: String,
    val maxPriorityFeePerGas: String,
    val maxFeePerGas: String,
    val paymasterAndData: String, // "0x"
    val signature: String,       // "0x"
    val mdaoMaxAmount: String,
    val usdtMaxAmount: String,
    val permitDeadline: String,
    val permitV: String,
    val permitR: String,
    val permitS: String
)

@Serializable
data class SignResponse(
    val paymasterAndData: String, // hex string — с подписью backend
    val userOpHash: String,
    val maxFee: String
)

sealed class PaymasterError(override val message: String) : Exception(message) {
    data object RateLimited : PaymasterError("Rate limited. Try again later.")
    data object InvalidRequest : PaymasterError("Invalid request")
    data object NetworkTimeout : PaymasterError("Network timeout")
    data class ServerError(val detail: String) : PaymasterError("Server error: $detail")
    data class Unknown(val detail: String) : PaymasterError("Unknown error: $detail")
}
```

**Orchestrator — quote ПОСЛЕ биометрии:**

```kotlin
class GaslessTransactionOrchestrator(
    private val paymasterApiClient: PaymasterApiClient,
    private val sendRepository: SendRepository,
    private val walletManager: WalletManager,
    private val paymasterClient: PaymasterClient // существующий локальный энкодер
) {
    // UI вызывает ДО показа экрана подтверждения — для статуса gasless
    suspend fun checkGaslessAvailable(): Boolean {
        // Лёгкий ping без реального quote
        return try {
            paymasterApiClient.requestSign(buildTestRequest()).isSuccess
        } catch (e: Exception) {
            false
        }
    }
    
    // Вызывается ПОСЛЕ Face ID, прямо перед сборкой final UserOp
    suspend fun sendGasless(
        recipient: String,
        amount: BigInteger,
        signedUserOp: UserOperation // уже подписанный Face ID
    ): Result<TransactionReceipt> {
        // 1. Строим запрос к /sign используя локальный энкодер для полей
        val encodedPaymasterData = paymasterClient.encodePaymasterAndData(
            token = Constants.USDT_ADDRESS,
            maxTokenAmount = amount,
            // ... остальные параметры из TDD §1.3
        )
        
        val signRequest = SignRequest(
            sender = walletManager.getAddress(),
            nonce = signedUserOp.nonce.toString(16),
            callData = signedUserOp.callData.toHexString(),
            paymasterAndData = encodedPaymasterData.toHexString(),
            // ... остальные поля
            mdaoMaxAmount = amount.toString(),
            usdtMaxAmount = amount.toString(),
            // permit fields если нужны
        )
        
        // 2. Получаем подписанный paymasterAndData от backend
        val signResult = paymasterApiClient.requestSign(signRequest)
        if (signResult.isFailure) {
            // Явная ошибка — UI показывает "Gasless недоступен"
            return Result.failure(signResult.exceptionOrNull()!!)
        }
        
        val signedPaymasterAndData = signResult.getOrThrow().paymasterAndData.hexToByteArray()
        
        // 3. Подставляем в UserOp (заменяем локальный на подписанный)
        val finalUserOp = signedUserOp.copy(
            paymasterAndData = signedPaymasterAndData
        )
        
        // 4. Отправляем
        return sendRepository.executeUserOp(finalUserOp)
    }
}
```

**UX-поток:**
1. `checkGaslessAvailable()` → если false, UI показывает: «Gasless недоступен. Газ будет списан из BNB баланса»
2. Face ID
3. `sendGasless()` → quote запрашивается ПОСЛЕ биометрии
4. Если quote протух (300s) → явная ошибка, не silent fallback

---

## 2. GuardianUserOpBuilder (реальные сигнатуры)

**Реальные сигнатуры контракта:**
```solidity
function addGuardian(address wallet, bytes32 identityHash, bytes32 pubKeyX, bytes32 pubKeyY)
    external onlyWalletOwner(wallet)

function confirmGuardian(address wallet) external
    // identityHash = keccak256(abi.encodePacked(msg.sender)) — вычисляется, не передаётся
```

```kotlin
class GuardianUserOpBuilder(
    private val passkeyManager: PasskeyManager,
    private val bundlerClient: BundlerClient,
    private val paymasterApiClient: PaymasterApiClient,
    private val walletManager: WalletManager
) {
    // Шаг 1: ВЛАДЕЛЕЦ добавляет гаранта (от имени владельца кошелька)
    suspend fun inviteGuardian(
        guardianWalletAddress: String,     // смарт-аккаунт гаранта (должен существовать!)
        guardianIdentityHash: ByteArray,   // keccak256(google:email или apple:sub гаранта)
        guardianPubKeyX: ByteArray,        // P-256 pubKeyX гаранта (получен через relay-инвайт)
        guardianPubKeyY: ByteArray         // P-256 pubKeyY гаранта
    ): Result<Unit> {
        // Проверка: гарант обязан иметь MDAOPay кошелёк
        require(walletManager.hasWallet(guardianWalletAddress)) {
            "Guardian must have MDAOPay wallet. Send them an invite link."
        }
        
        // Строим UserOp от ВЛАДЕЛЬЦА
        val ownerAddress = walletManager.getAddress()
        val callData = SocialRecoveryModule.addGuardian.encode(
            wallet = ownerAddress,
            identityHash = bytes32(guardianIdentityHash),
            pubKeyX = bytes32(guardianPubKeyX),
            pubKeyY = bytes32(guardianPubKeyY)
        )
        
        // Gasless: получаем подписанный paymasterAndData
        val signRequest = buildSignRequest(ownerAddress, callData)
        val signResponse = paymasterApiClient.requestSign(signRequest)
        if (signResponse.isFailure) return Result.failure(signResponse.exceptionOrNull()!!)
        
        val userOp = UserOperation(
            sender = ownerAddress,
            callData = callData,
            paymasterAndData = signResponse.getOrThrow().paymasterAndData.hexToByteArray()
        )
        
        return bundlerClient.sendUserOperation(userOp)
    }
    
    // Шаг 2: ГАРАНТ подтверждает (от имени своего смарт-аккаунта)
    suspend fun confirmAsGuardian(walletToProtect: String): Result<Unit> {
        // msg.sender = смарт-аккаунт гаранта
        // confirmGuardian принимает ТОЛЬКО wallet, identityHash вычисляется из msg.sender
        val guardianAddress = walletManager.getAddress()
        
        val callData = SocialRecoveryModule.confirmGuardian.encode(
            wallet = walletToProtect
        )
        
        // Gasless для гаранта (PRD: "Veto = бесплатный", но confirm тоже субсидируется)
        // maxTokenAmount = реальная оценка газа, не ZERO
        val signRequest = buildSignRequest(guardianAddress, callData)
        val signResponse = paymasterApiClient.requestSign(signRequest)
        if (signResponse.isFailure) return Result.failure(signResponse.exceptionOrNull()!!)
        
        val userOp = UserOperation(
            sender = guardianAddress,
            callData = callData,
            paymasterAndData = signResponse.getOrThrow().paymasterAndData.hexToByteArray()
        )
        
        return bundlerClient.sendUserOperation(userOp)
    }
    
    // approveRecovery — от имени гаранта, с WebAuthn подписью
    suspend fun approveRecovery(
        walletToRecover: String,
        authenticatorData: ByteArray,
        clientDataJSON: ByteArray,
        p256Signature: ByteArray // 64 bytes r||s
    ): Result<Unit> {
        val guardianAddress = walletManager.getAddress()
        val guardianIdentityHash = keccak256(guardianAddress.toByteArray())
        
        // Реальная сигнатура контракта:
        // approveRecovery(address wallet, bytes32 guardianIdentityHash, 
        //   bytes authenticatorData, bytes clientDataJSON, bytes p256Signature)
        val callData = SocialRecoveryModule.approveRecovery.encode(
            wallet = walletToRecover,
            guardianIdentityHash = bytes32(guardianIdentityHash),
            authenticatorData = authenticatorData,
            clientDataJSON = clientDataJSON,
            p256Signature = p256Signature
        )
        
        // Gasless
        val signRequest = buildSignRequest(guardianAddress, callData)
        val signResponse = paymasterApiClient.requestSign(signRequest)
        if (signResponse.isFailure) return Result.failure(signResponse.exceptionOrNull()!!)
        
        val userOp = UserOperation(
            sender = guardianAddress,
            callData = callData,
            paymasterAndData = signResponse.getOrThrow().paymasterAndData.hexToByteArray()
        )
        
        return bundlerClient.sendUserOperation(userOp)
    }
    
    // vetoRecovery — реальная сигнатура контракта
    suspend fun vetoRecovery(
        walletToRecover: String,
        guardianIdentityHash: ByteArray,
        authenticatorData: ByteArray,
        clientDataJSON: ByteArray,
        p256Signature: ByteArray
    ): Result<Unit> {
        val guardianAddress = walletManager.getAddress()
        
        // Реальная сигнатура:
        // vetoRecovery(address wallet, bytes32 guardianIdentityHash,
        //   bytes authenticatorData, bytes clientDataJSON, bytes p256Signature)
        val callData = SocialRecoveryModule.vetoRecovery.encode(
            wallet = walletToRecover,
            guardianIdentityHash = bytes32(guardianIdentityHash),
            authenticatorData = authenticatorData,
            clientDataJSON = clientDataJSON,
            p256Signature = p256Signature
        )
        
        // Gasless
        val signRequest = buildSignRequest(guardianAddress, callData)
        val signResponse = paymasterApiClient.requestSign(signRequest)
        if (signResponse.isFailure) return Result.failure(signResponse.exceptionOrNull()!!)
        
        val userOp = UserOperation(
            sender = guardianAddress,
            callData = callData,
            paymasterAndData = signResponse.getOrThrow().paymasterAndData.hexToByteArray()
        )
        
        return bundlerClient.sendUserOperation(userOp)
    }
}
```

**Продуктовое требование (в PRD):**
```markdown
# PRD §7 (добавить):
**Требование к guardian:** Guardian обязан иметь установленный MDAOPay кошелёк 
и задеплоенный смарт-аккаунт на той же сети. Onboarding гаранта: 
при acceptInvite() если у гаранта нет кошелька → deep link на установку приложения.
```

---

## 3. Сжигание депозита (реальная сигнатура + burn())

**Реальная сигнатура vetoRecovery:**
```solidity
function vetoRecovery(
    address wallet,
    bytes32 guardianIdentityHash,
    bytes calldata authenticatorData,
    bytes calldata clientDataJSON,
    bytes calldata p256Signature
) external
```

**Исправленный код контракта:**

```solidity
// contracts/src/SocialRecoveryModule.sol
import "@openzeppelin/contracts/token/ERC20/extensions/IERC20Burnable.sol";

// ... в контракте ...
IERC20Burnable public immutable mdaoToken; // ← IERC20Burnable, не IERC20

constructor(address _mdaoToken) {
    mdaoToken = IERC20Burnable(_mdaoToken);
}

function vetoRecovery(
    address wallet,                    // ← address, не bytes32
    bytes32 guardianIdentityHash,
    bytes calldata authenticatorData,
    bytes calldata clientDataJSON,
    bytes calldata p256Signature       // ← bytes (64 bytes r||s), не отдельные r, s
) external {
    // ... верификация WebAuthn ...
    
    currentVetoes++;
    emit RecoveryVetoed(wallet, msg.sender, currentVetoes);
    
    if (currentVetoes >= VETO_THRESHOLD) {
        req.vetoed = true;
        uint256 deposit = recoveryDeposit[wallet];
        if (deposit > 0) {
            recoveryDeposit[wallet] = 0;
            // БЫЛО: mdaoToken.transfer(BURN_ADDRESS, deposit);
            // СТАЛО: Реальное сжигание через ERC20Burnable.burn()
            // burn() уменьшает totalSupply и эмитит Transfer(this, 0x0, deposit)
            mdaoToken.burn(deposit);
            emit DepositBurned(wallet, deposit);
        }
    }
}
```

**Почему burn() лучше transfer(BURN_ADDRESS):**
1. `totalSupply` реально уменьшается → корректный circulating supply для индексаторов
2. `0x...dEaD` — конвенция, а не invariant (приватный ключ технически существует)
3. `ERC20Burnable.burn()` — стандартный OZ интерфейс, auditors понимают его без вопросов
4. `Transfer(this, 0x0, deposit)` event — прозрачное событие сжигания

---

## 4. TDD-синхронизация (полный список)

```markdown
# TDD §3.4 — обновить:

## Constants
| Name | Value | Description |
|------|-------|-------------|
| THRESHOLD | 2 | Approvals needed for recovery |
| MAX_GUARDIANS | 3 | Maximum guardians per wallet |
| VETO_THRESHOLD | 2 | Vetoes needed to block recovery |
| TIMELOCK | 48 hours | Time delay before recovery executes |
| MAX_EXECUTION_WINDOW | 48 hours | Max time after TIMELOCK to execute (не 7 days) |
| DEPOSIT_AMOUNT | 0.01 * 10**18 | 0.01 MDAO (ERC-20, 18 decimals) |

## Deposit mechanism
- Token: MDAO (ERC-20), не ETH/BNB
- Method: `mdaoToken.transferFrom(msg.sender, address(this), DEPOSIT_AMOUNT)` в `initiateRecovery`
- UX: пользователю нужен MDAO approve перед initiateRecovery
- При success: возвращается initiator'у
- При veto: сжигается через `mdaoToken.burn(deposit)`

## WebAuthn verification
- Format: raw `authenticatorData || SHA256(clientDataJSON)` (без EIP-191 prefix)
- Signature: 64 bytes (r || s), передаётся как `bytes calldata p256Signature`
- Precompile: `0x0000000000000000000000000000000000000100` (RIP-7212)

## Guardian requirement
- Гарант обязан иметь задеплоенный MDAOPay смарт-аккаунт
- `confirmGuardian(wallet)` вызывается от имени смарт-аккаунта гаранта
- `identityHash = keccak256(abi.encodePacked(msg.sender))` — вычисляется контрактом
```

---

## 5. Relay Retry/Circuit Breaker (потокобезопасный, per-endpoint)

```kotlin
class RelayClient(
    private val client: HttpClient,
    @Named("relayUrl") private val relayUrl: String = BuildConfig.RELAY_URL
) {
    // Per-endpoint circuit breakers — потокобезопасные
    private val circuitBreakers = ConcurrentHashMap<String, EndpointBreaker>()
    
    private inner class EndpointBreaker {
        private val failureCount = AtomicInteger(0)
        @Volatile private var circuitOpenUntil: Long = 0
        
        fun allowRequest(): Boolean = System.currentTimeMillis() >= circuitOpenUntil
        
        fun recordSuccess() {
            failureCount.set(0)
            circuitOpenUntil = 0
        }
        
        fun recordFailure() {
            if (failureCount.incrementAndGet() >= 3) {
                circuitOpenUntil = System.currentTimeMillis() + 30_000
            }
        }
    }
    
    private fun getBreaker(endpoint: String) = 
        circuitBreakers.getOrPut(endpoint) { EndpointBreaker() }
    
    suspend fun approveRecovery(request: ApproveRequest): Result<Unit> =
        executeWithBreaker("/recovery/approve") {
            client.post("$relayUrl/recovery/approve") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
    
    private suspend fun <T> executeWithBreaker(
        endpoint: String,
        block: suspend () -> HttpResponse
    ): Result<T> {
        val breaker = getBreaker(endpoint)
        
        if (!breaker.allowRequest()) {
            return Result.failure(RelayUnavailableException("Circuit open for $endpoint"))
        }
        
        return try {
            val response = retryRetryable(block)
            breaker.recordSuccess()
            Result.success(response)
        } catch (e: ClientRequestException) {
            // 4xx = terminal, НЕ ретраить
            breaker.recordFailure()
            Result.failure(e)
        } catch (e: Exception) {
            // 5xx, timeout = retryable, уже ретраили
            breaker.recordFailure()
            Result.failure(e)
        }
    }
    
    // Ретраить ТОЛЬКО timeout и 5xx, НЕ 4xx
    private suspend fun retryRetryable(
        maxAttempts: Int = 3,
        block: suspend () -> HttpResponse
    ): HttpResponse {
        var lastError: Throwable? = null
        for (attempt in 0 until maxAttempts) {
            try {
                val response = block()
                if (response.status.value in 200..299) return response
                if (response.status.value in 400..499) throw ClientRequestException(response)
                // 5xx = retryable
                lastError = ServerResponseException(response)
            } catch (e: HttpRequestTimeoutException) {
                lastError = e
            } catch (e: ClientRequestException) {
                throw e // 4xx = terminal
            }
            if (attempt < maxAttempts - 1) {
                delay(1000L shl attempt) // 1s, 2s, 4s
            }
        }
        throw lastError!!
    }
}
```

---

## 6. Интеграционный тест (fixture-based, без ffi)

```solidity
// contracts/test/integration/EIP712FixtureTest.t.sol
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import "forge-std/Test.sol";
import "../src/MDAOPaymaster.sol";

contract EIP712FixtureTest is Test {
    MDAOPaymaster paymaster;
    address constant ENTRY_POINT = 0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789;
    
    // Fixture: реальный ответ backend /sign для known test vector
    // Генерируется один раз: запускаем backend, отправляем known request, 
    // копируем ответ в JSON, коммитим в contracts/test/fixtures/
    // Файл: contracts/test/fixtures/eip712_quote.json
    
    function setUp() public {
        paymaster = new MDAOPaymaster(ENTRY_POINT, address(this));
        
        // Читаем fixture из файла
        string memory fixtureJson = vm.readFile(
            "contracts/test/fixtures/eip712_quote.json"
        );
        // Парсим paymasterAndData из JSON
        bytes memory fixturePaymasterAndData = vm.parseJsonBytes(
            fixtureJson,
            ".paymasterAndData"
        );
    }
    
    function testBackendQuoteVerifiesOnChain() public {
        // Реалистичный UserOp с ненулевыми gas limits
        UserOperation memory userOp = UserOperation({
            sender: 0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266,
            nonce: 0,
            initCode: bytes(""),
            callData: hex"a9059cbb", // transfer(address,uint256)
            verificationGasLimit: 100000,  // реалистичное значение
            callGasLimit: 100000,
            preVerificationGas: 100000,
            maxFeePerGas: 1 gwei,
            maxPriorityFeePerGas: 1 gwei,
            paymasterAndData: fixturePaymasterAndData,
            signature: bytes("")
        });
        
        bytes32 userOpHash = keccak256(abi.encode(userOp));
        
        // КРИТИЧНО: prank as EntryPoint (onlyEntryPoint модификатор)
        vm.prank(ENTRY_POINT);
        (bytes memory context, uint256 validationData) = 
            paymaster.validatePaymasterUserOp(userOp, userOpHash, 100000);
        
        assertEq(validationData, 0, "Backend quote must verify on-chain");
    }
}
```

**Процесс обновления fixture:**
```bash
# Nightly/manual job (не в основном CI gate):
# 1. Поднять anvil + backend
# 2. Отправить known request на /sign
# 3. Сравнить ответ с существующим fixture
# 4. Если расходится → alert, ручное обновление fixture
```

---

## 7. Risk-scored Session Keys (time-decay, без auth-модуляции)

```solidity
struct Session {
    address dApp;
    Capability[] capabilities;
    uint256 maxAmountPerTx;
    uint256 dailyLimit;
    address[] allowedTokens;
    uint256 expiresAt;
    bool active;
    uint256 trustScore;        // 0-100, начинается с 0
    uint256 lastScoreUpdate;   // block.timestamp последнего обновления
}

function executeWithSession(...) external nonReentrant {
    Session storage s = sessions[sessionKey];
    require(s.active && block.timestamp < s.expiresAt);
    
    // Dynamic limit ТОЛЬКО на spendingLimit, НЕ на auth
    uint256 dynamicLimit = s.maxAmountPerTx;
    
    if (s.trustScore >= 50) {
        // High trust: +20% к лимиту
        dynamicLimit = dynamicLimit * 120 / 100;
    }
    
    require(value <= dynamicLimit, "ExceedsDynamicLimit");
    
    // ... execute ...
    
    // Update trust score: max +1 per day
    if (block.timestamp >= s.lastScoreUpdate + 1 days) {
        s.trustScore = Math.min(s.trustScore + 1, 100);
        s.lastScoreUpdate = block.timestamp;
    }
    
    // Сброс при suspicious pattern
    if (value > s.maxAmountPerTx * 2) {
        s.trustScore = 0;
        emit TrustScoreReset(sessionKey, "Amount spike detected");
    }
}
```

---

## 8. Dead Man Switch — ОТЛОЖЕНО

```markdown
# Вынести в отдельный RFC:
## RFC-001: Dead Man Switch as Fallback Recovery
Status: RESEARCH ONLY. Requires external security audit.
Risks:
- Contradicts ADR-021 (OAuth = auxiliary)
- Creates weaker recovery path (1-of-3 + OAuth vs 2-of-3)
- Requires on-chain OAuth verification (new source of truth)
Mitigations needed (if ever implemented):
- 30-day timelock (not 7)
- Notify ALL guardians
- Multi-provider OAuth (not just Google/Apple)
- External security review required before implementation
```

---

## 9. TrustProviderRegistry → Timelock с Gnosis Safe

```solidity
// script/Deploy.s.sol
address constant GNOSIS_SAFE = 0x...; // 3-of-5 multisig

TimelockController timelock = new TimelockController(
    48 hours,
    new address[]{GNOSIS_SAFE},   // proposers = Safe, НЕ deployer
    new address[]{address(0)},    // executors = anyone
    address(0)                    // admin = 0 (никто не может отменить)
);

TrustProviderRegistry registry = new TrustProviderRegistry();
registry.transferOwnership(address(timelock));

// Post-deploy verification
require(registry.owner() == address(timelock), "Registry not owned by timelock");
```

---
