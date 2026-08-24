# АУДИТ ПОЛНОЙ ПРОВЕРКИ — MDAOPay

Дата начала: 2026-08-10
Метод: атомарные шаги (1 объект = 1 вердикт). Правки НЕ вносятся.
Вердикты: OK | DEFECT | DEBT | OPT
Severity: blocker | high | med | low

---

## Фаза A — Базовые линии (шаги 1-6)

### [A1] git-состояние
- Проверка: статус, ветки, последние коммиты, git log — целостность.
- Вердикт: PENDING (заполняется)

### [A2] Секреты в git history
- Проверка: git log -p по всем коммитам на предмет приватных ключей, .env, токенов.
- Вердикт: PENDING

### [A3] Трекинг файлов
- Проверка: git ls-files — .env, ключи, артефакты сборки, мусор в репо.
- Вердикт: PENDING

### [A1] git-состояние
- Вердикт: OK — main @ df31712, дерево чистое (кроме audit-файла), 3 dependabot-ветки.

### [A2] Секреты в git history
- Вердикт: OK — приватных ключей нет (253 64-hex = тестовые фикстуры/примеры из .opencode промптов), .gitleaksignore: 1 запись (AWS example из офиц. доков). .env не трекается.

### [A3] Трекинг файлов
- Вердикт: OK — 1740 файлов, подозрительное только легитимное (KeystoreCrypto, ShamirSecretSharing, доки). .env.example = пустые заглушки. 7 broadcast/out = fixtures forge-std lib.

### [A4] Baseline-сборка (агент: builder, deepseek-v4-flash-free)
- Backend: BUILD SUCCESSFUL, 158/158 тестов (--no-daemon --max-workers=1 -Xmx1536m).
- Contracts (forge): 407/411 — 4 pre-existing FAIL: UpdateTooSoon ×2 (MDAOToken), PriceCooldownActive vs PriceChangeTooHigh (SmokeScenarios), testFuzz_QuoteDeadlineValidation (fuzz).
- Relay (vitest): 32/33 — 1 NEW FAIL: auth.test.ts verifyP256Signature "returns true for valid signature" (разбирает error_detective).
- DEBT: 12 hs_err_pid*.log (исторические OOM JVM), Gradle drift (Dockerfile gradle:8-jdk21 vs wrapper 9.4.1, deprecated features → Gradle 10).
- Вердикт: OK (с оговорками)

### [A5] Зависимости и CVE (агент: builder)
- Backend: ktor 3.1.2, kotlin 2.0.21, web3j 4.12.3 (осознанный пин под JDK17), lettuce 6.5.3, HikariCP 6.2.1, flyway 10.22.0, aws-sdk-kms 2.30.+.
- Relay: ethers 6.17, wrangler 4.103.0 exact, vite 8, vitest 4.1.9, ts 5.7.
- Contracts: solc 0.8.28 via_ir, optimizer_runs=10000.
- DEFECT (high): relay npm audit 6 HIGH — undici (транзитивно через wrangler): GHSA-4cwx-7wf7-3272, GHSA-m8rv-5g2x-5cg5, GHSA-jr45-8vmc-qm54, GHSA-v3r7-h72x-cjcm. Fix: wrangler@4.120.0 (вне пина).
- DEFECT (med): aws-sdk-kms "2.30.+" плавающая (нерепроизводимо); CI npm audit неблокирующий (|| echo) — CVE проходят молча.
- Вердикт: DEFECT

### [A6] Конфиги окружений (агент: builder)
- DEFECT (high): .env.example НЕ содержит обязательных: WBNB_ADDRESS, JWT_SECRET, TRUSTED_SIGNER, SWAP_PRIVATE_KEY (все error() в fromEnv) — деплой по примеру упадёт.
- DEFECT (high): CI ci.yml backend job `./gradlew :backend:test` из backend/ — FAIL "project 'backend' not found" (dry-run подтверждён).
- DEFECT (high): CI app job `./gradlew assembleDevDebug` из backend/ — FAIL "Task not found" (dry-run; Android в :app на корне).
- DEFECT (med): docker-compose healthcheck использует curl, образ eclipse-temurin:21-jre БЕЗ curl → вечно unhealthy.
- DEFECT (med): backend/docker-compose.yml нет volumes у postgres/redis → потеря данных при recreate.
- DEFECT (med): deploy.yml (Cloud Run) задаёт только 3 env из ~14 обязательных.
- DEBT: jvmToolchain(17) без foojay-resolver; CI Java 21 vs toolchain 17.
- Вердикт: DEFECT

## Фаза B — Backend Kotlin (агент: reviewer, big-pickle)

### B1. Backend core (13 файлов)
| Файл | Вердикт | Severity | Ключевое |
|---|---|---|---|
| AppConfig.kt | DEFECT | med | isTestnet: chainId !in {1,56} (AppConfig:142) vs ==97L (PaymasterService:138) — мина; trustedSigner мёртв; SWAP_PRIVATE_KEY обязателен даже без SWAP_ROUTER |
| Application.kt | DEFECT | high | /v1/sign error-ветки без status → HTTP 200 (336-368); idempotency отравляется ДО sign() на 3600с (350-357); sender-rate-limit без привязки к JWT-принципалу; SIWE nonce без rate-limit → рост siwe_nonces; expectedDomain захардкожен "app.mdaopay.com" (201); IPv4-mapped IPv6 схлопывается в общий /64 бакет (129-168) |
| AuthService.kt | DEBT | low | getUser() мёртв; SiweMessage поля парсятся-не используются; register без email-валидации + user enumeration; nonce удаляется ДО проверки (burn nonce); PBKDF2 600k без параметров в hash; SIWE-userы с пустым passwordHash |
| AuthRepository.kt | OK | — | findAndDeleteSiweNonce TOCTOU (SELECT→DELETE), остальное чисто |
| PaymasterService.kt | DEFECT | med | userOpHash строго v0.6, docs говорят EntryPoint v0.7 — СВЕРИТЬ с деплоем; usePermit=(permitV!=null) без проверки полноты → подпись без allowance (отказ, не потеря) |
| PaymasterSigner.kt (root) | DEBT | low | файл-дубликат, никем не импортируется — мёртвый |
| signing/KeyFactory.kt | DEFECT | low | нет проверки secp256k1 (P-256 ключ → неверный адрес) |
| signing/KmsPaymasterSigner.kt | OK | — | recId 0..3 сверка, DER-парсер с границами; без @Volatile кэш — безвредно |
| signing/LocalPaymasterSigner.kt | OK | — | needToHash=false, v=27/28 корректно |
| signing/PaymasterSigner.kt | OK | — | интерфейс консистентен |
| RedisClient.kt | DEFECT | med | RedisReplayCache.isUsed неатомарен (TOCTOU); Redis-down → 3-6с на запрос; fallback per-instance обходит лимиты |
| Database.kt | OK | — | Hikari+Flyway корректно, minimumIdle ≤ maxPool |
| EventIndexer.kt | DEFECT | med | тип по ИМЕНИ параметра → неверные адреса, wallet=null, массивы теряются; lastPolled в памяти → пропуск после рестарта; paymasterEvents мёртвый |

**Топ B1:** ① /v1/sign 200-на-ошибках (high) ② idempotency-отравление ③ SIWE без rate-limit ④ EntryPoint v0.6 vs док v0.7 ⑤ EventIndexer тип-по-имени ⑥ RedisReplayCache TOCTOU.

### B2. Backend services (16 файлов, агент: reviewer big-pickle; blocker'ы ПОДТВЕРЖДЕНЫ мной детерминированно)
| # | Файл | Verdict | Severity | Проблема |
|---|---|---|---|---|
| 01 | BinancePriceSource.kt | DEFECT | med | HttpClient(CIO) без timeout (14) — зависший источник вешает весь fetch; reliability не читается (35) |
| 02 | FiatOnrampService.kt | DEFECT | high | widgetUrl = GET /moonpay-proxy?... (105-106) — роута НЕТ, подпись в query, а прокси читает X-Signature → 404/401; getOrderStatus всегда "pending" (56-58) — реальный ордер не создаётся |
| 03 | Metrics.kt | OPT | low | latencyIndex++ неатомарен (21) |
| 04 | MoonPayProxy.kt | DEFECT | med | HMAC только body, не путь/время → бесконечный replay (33-46); нет rate-limit; moonpaySecretKey не используется (30) |
| 05 | NicknamePolicy.kt | OK | — | regex ^[a-zA-Z0-9_-]{3,20}$, reserved-сет, lowercase invariant — корректно |
| 06 | NicknameRepository.kt | DEFECT | **blocker** ✅ | ON CONFLICT (LOWER(nickname)) (54) без unique-индекса (V1:37 CREATE INDEX не UNIQUE) → 42P10 на каждом insert. Парадокс: столбец nickname уже UNIQUE (V1:29) — конфликт-выражение несовместимо. Регистрация ников НЕ работает |
| 07 | NicknameService.kt | DEFECT | med | return@runBlocking Result.failure (95-99) — возврат отбрасывается, replay-защита мертва; address-уникальность только in-memory (145-148) |
| 08 | OnChainRegistryClient.kt | OK | — | fail-closed; identityHash=keccak(raw) — сверить с контрактом на тестнете |
| 09 | OnrampRoutes.kt | DEFECT | med | /onramp/status отдаёт err.message сырым (127-131) |
| 10 | PriceOracle.kt | DEFECT | med | ОДИН CircuitBreaker на все источники (200-210) — падение одного убивает все; HTTP без timeout; DexPrices.isValid() мёртв (123) |
| 11 | RpcProviderManager.kt | OPT | low | HttpService без timeout (63-68), health блокирующий |
| 12 | SwapRoutes.kt | OK | — | rate-limit 10/60с + JWT + extractClientIp |
| 13 | SwapService.kt | DEFECT | **high** ✅ | executeSwap (91-135): recipient/amountIn/tokenIn БЕЗ проверок, msg.sender = кошелёк сервиса → JWT-пользователь может вывести токены сервиса (при наличии allowance); nonce LATEST без мьютекса (117-119) — race |
| 14 | WatchtowerService.kt | DEBT | med | watchWallet не вызывается нигде (97-99) — баланс-мониторинг мёртв; topics[0] без проверки (127-128) — IOOBE → застревание poll |
| 15 | util/LogSanitizer.kt | OK | — | маскировка корректна |
| 16 | util/SimpleJsonLayout.kt | DEFECT | low | @timestamp = epoch-millis строкой (29) — ELK не парсит; exception без stacktrace (44-47) |

**Топ B2:** ① NicknameRepository 42P10 (blocker) ② SwapService drain (high) ③ onramp-виджет 404 (high). Итог reviewer: score 52/100, reject — НЕ готов к деплою.

## Фаза B3 — Relay (агент: error_detective; подтверждено мной)
- **Root cause (high)**: `auth.ts:88` безусловно вставляет nonce в HMAC: `${timestamp}.${nonce}.${body}` → при nonce='' получается `ts..body` (2 точки). Тест `auth.test.ts:74` подписывает `${ts}.${body}` (1 точка) → constantTimeEqual false.
- ВАЖНО: падает НЕ P256-тест, а HMAC-тест "verifySignature > returns true for valid signature" (assert строка 75). P256 (строка 38) проходит. 32/33.
- Тип: regression от коммита 83f291e "v0.1: cleanup" (Jul 17) — до нашего merge 718b8f9 (auth.ts не касается). Наш merge не виноват.
- Прод: `index.ts:107` передаёт nonce из X-Nonce с default '' → клиенты без X-Nonce сломаны тем же багом.
- Фикс (1 строка, НЕ внесён): `const msg = nonce ? \`${timestamp}.${nonce}.${body}\` : \`${timestamp}.${body}\``
- Severity: high (blocker CI, ломает prod-верификацию).

## Фаза C — Contracts (агент: smart_contract_auditor, big-pickle)

### C1. Contracts src группа 1 (8 контрактов)
| Контракт | Вердикт | Severity | Ключевое |
|---|---|---|---|
| MDAOToken | OK | — | fee-логика, cooldown, cap — корректно |
| MDAOPaymaster | DEFECT | low | _parseQuoteFields без проверки длины ≥84 → panic; maxDeviationBps=1000 мёртвый (MAX_PRICE_CHANGE=200); refund-fail глотается |
| SocialRecoveryModule | DEFECT | **blocker** ⚠️ НАШ merge | См. ниже |
| SessionKeyModule | DEFECT | med | _ownerKeys без лимита → hook 50k gas выходит → ключи не ревокуются; useSessionKey owner-only (4337-несовместим) |
| MockP256 | DEFECT | med | guard только `!=56` → accept-all на ЛЮБОЙ цепи кроме BSC mainnet (ETH 1 = takeover); fallback без селектор-чека |
| InsuranceFund | DEFECT | med | collectFee не двигает токены, receive() вне учёта → totalFunds отвязан от ETH; approveClaim не потребляет nonce → мультивыплата одного claim |
| DeadManSwitch | DEBT | low | State.Executed не сбрасывается при setSwitch (ре-арм невозможен) |
| NicknameRegistry | OK | — | ECDSA.recover + nonce, кэш domain separator — корректно |

**⚠️ SocialRecoveryModule — BLOCKER (код из нашего merge 718b8f9!):**
- `_isP256Working` (L137-141) шлёт **128 нулевых байт**, а RIP-7212 precompile требует **ровно 160 байт** (hash+r+s+x+y; «strictly enforces 160-byte input length», на невалидной подписи возвращает ПУСТОЙ результат). → probe ВСЕГДА false → MockP256 деплоится всегда → `p256VerifierWorking` всегда false (бессмысленный флаг), confirmP256Verifier (наш фикс) тоже всегда false.
- ПОСЛЕДСТВИЯ: на BSC testnet — accept-all recovery (приемлемо для smoke); на BSC mainnet (56) MockP256 revert → recovery НАМЕРТВО сломан; на других цепях — полный takeover.
- Верификация: websearch подтвердил — 160 байт (ethereum/RIPs rip-7212, cosmos/evm README, daimo p256-verifier). МОЯ ОШИБКА при интеграции: оценивал «логику» без проверки спецификации calldata.
- Дополнительно (валидно независимо): accept-all без challenge-binding (clientDataJSON не парсится → replay перехваченной P-256 подписи на любые wallet/nonce); initiateRecovery permissionless + deposit возвращается → DoS блокировка recovery на 96ч без on-chain cancel; removeGuardian не инвалидирует поданные approvals.
- Фикс (не внесён): probe = 160 байт + известный ВАЛИДНЫЙ тест-вектор, ИЛИ отказаться от probe — выбирать verifier в deploy-скрипте по chainId; mock только на whitelist тестнетов; challenge = keccak(wallet, nonce).

### C2. Contracts src группа 2 (17 файлов)
| Контракт | Вердикт | Severity | Ключевое |
|---|---|---|---|
| AttestationLedger | OK | low | verifyBatch цикл без лимита (gas); повторная attest() дублирует событие |
| EcdsaVerifier | DEFECT | med | verify = intentHash.recover(proof) — OZ recover РЕВЕРТИТ на неверной длине вместо false (ломает ITrustProvider bool-контракт); high-s malleability. ✅ подтверждено |
| MDAOSmartAccount | DEFECT | med | _validateSignature без проверки len==65 → revert вместо SIG_VALIDATION_FAILED (убивает handleOps); recoveryCaller имеет transferOwnership/withdrawDepositTo |
| PaymentSplitterFactory | DEFECT | med | CREATE2 frontrunning: salt предсказуем (без msg.sender/chainId); DEFAULT_ADMIN_ROLE навсегда на сплиттерах |
| PaymentSplitter | DEFECT | med | fee-on-transfer → totalDue<начислено → underflow-revert → выплаты застревают; тысячи payees → DoS раздачи; dust остаётся |
| Proposal | DEFECT | med | голосование без снапшотов → flash-loan; quorum плавает (mintable токен); retryProposal обходит quorum |
| RefundVault | DEFECT | med | depositRefund НЕ сбрасывает entry.claimed → после claim повторный депозит навсегда застревает (потеря средств). ✅ подтверждено (L34-43 vs L49-52) |
| Treasury | DEFECT | med | recipient=0 проходит (code.length==0 → EOA) → ETH сжигается; withdrawStuckTokens может вывести размещённые под allocation средства |
| TrustProviderRegistry | DEFECT | med | renounceOwnership не переопределён → вечная заморозка регистрации; setEmergencyGuardian без check address(0) |
| ITrustProvider | OK | — | но зафиксировать «must not revert» в natspec |
| FCLP256Verifier | DEFECT | med | вход 160 байт ✓; математика верна ✓; НЕТ low-s проверки (s>n/2) → malleability (r, n−s). ✅ подтверждено (L80) |
| P256Verifier | DEFECT | med | то же отсутствие low-s (L44); только fallback без explicit verify(); дублирование с FCLP |
| IPaymentSplitter | OK | — | receive() не отражён (некритично) |
| IProposal | OK | low | ErrMaxRetriesExceeded не в интерфейсе |
| IRecoveryHook | OK | — | сигнатура onRecoveryExecuted(address) ✓ |
| ISplitterFactory | OK | — | deployedSplitters public mapping ✓ |
| ITreasury | OK | low | ErrUnauthorized/ErrTooManyRecipients не в интерфейсе |

**Итог C2:** blocker/critical НЕ найдено; reentrancy-векторов нет (CEI соблюдён, ReentrancyGuard); overflow защищён 0.8.28; tx.origin/delegatecall/selfdestruct нет. Топ-3 перед деплоем: (1) RefundVault застрев средств; (2) P-256 + ECDSA malleability (весь стек подписей); (3) EcdsaVerifier revert-семантика + PaymentSplitter underflow — тихие поломки на боевых данных. Верифицировано детерминированно (grep/чтение): RefundVault, EcdsaVerifier, low-s отсутствие — все три подтверждены.

### C3. Deploy-скрипты (7 файлов, contracts/script/)
| Скрипт | Вердикт | Severity | Ключевое |
|---|---|---|---|
| Deploy.s.sol (главный) | DEFECT | **blocker** | L110-112: addRecoveryHook БЕЗ approveHook → revert ErrHookNotApproved (SRM:430) → forge не отправит НИ ОДНОЙ транзакции. ✅ подтверждено. L247-257: vm.warp+timelock.execute не переживёт --broadcast (2-дневная задержка реальна) → владельцем остаётся deployer |
| Deploy.s.sol P-256 путь | DEFECT | med | Работает «по случайности»: probe 128 байт → P256Verifier.fallback (L5-8: input≠160 → 32 нулевых байта) → success=true → реальный верифаер. ✅ подтверждено. Хрупко: проверяется длина, не значение |
| DeploySocialRecoveryModule.s.sol | DEFECT | **critical** | L11 дефолт vm.envOr("P256_VERIFIER", 0x100): на BSC по 0x100 КОДА НЕТ → probe false → MockP256 accept-all → любой «подписывает» за guardian'а, 2 подписи = захват recovery (testnet); на mainnet (56) MockP256 revert → recovery мёртв (DoS). chainId-проверка только console.log WARNING, без revert |
| DeployMDAOPaymaster.s.sol | DEFECT | high | Нет chainId guard; trustedSigner=envOr(...,0) — конструктор не проверяет zero → тихая мисконфигурация на mainnet; дефолты mainnet (MDAO_BSC/USDT_BSC); EntryPoint 0x5FF1 = v0.6 ✓ для paymaster |
| DeployMDAOToken.s.sol | DEFECT | med | Нет chainId guard: молча задеплоит 100M на mainnet |
| DeployInsuranceFund.s.sol | DEFECT | med | envOr(auditor,0) → невнятный реверт; нет guard; нет вайринга с paymaster |
| DeployNicknameRegistry.s.sol | DEFECT | low | Нет guard; console не импортирован → адрес не логируется |
| DeployDeadManSwitch.s.sol | DEFECT | low | Нет guard; нет setRecoveryCaller (deploy-only) |

**🚨 СИСТЕМНЫЙ РАСКОЛ EntryPoint v0.6 vs v0.7 (critical, ✅ подтверждено детерминированно):**
- `MDAOPaymaster.sol` — **v0.6**: свой struct UserOperation с paymasterAndData (L17), EntryPoint 0x5FF137D4b0FDCD49DcA30c7CF57E578a026d2789.
- `MDAOSmartAccount.sol` — **v0.7**: импортирует PackedUserOperation из account-abstraction v0.9.0 (package.json: "version":"0.9.0"), требует EntryPoint 0x0000000071727De22E5E9d8BAf0edAc6f37da032.
- Paymaster и smart account НЕ могут работать с одним EntryPoint. Deploy-скрипты деплоят только paymaster-сторону (v0.6). UserOp-флоу handleOps→validateUserOp→validatePaymasterUserOp скомбинировать нельзя.
- docs/DEPLOY-TESTNET.md:165,271 подписывают 0x5FF1 как «v0.7» — НЕВЕРНО (это канонический v0.6).
- Backend PaymasterService считает userOpHash по v0.6 — консистентен с paymaster, НО смарт-аккаунт (v0.7) несовместим. Решить в фазе H: либо paymaster → v0.7 (PackedUserOperation), либо smart account → v0.6; зафиксировать реальные адреса в docs.

**Итог C3:** 1 blocker (Deploy.s.sol не деплоит вообще), 1 critical (accept-all recovery при дефолте), 1 high, 4 med, 2 low. Топ-3: (1) approveHook перед addRecoveryHook; (2) запретить дефолт 0x100 на 56/97 + чинить probe (160 байт + проверка значения ==1); (3) решить раскол EntryPoint. Верифицировано детерминированно: approveHook-отсутствие, EntryPoint-раскол (импорты+версия lib), P256Verifier.fallback 160-байт поведение.

---

_Журнал будет дополняться после каждого шага._

---

## C4 — Тесты контрактов (23 файла) [DONE 2026-08-10]

Метод: qa_tester (big-pickle), все ключевые вердикты верифицированы grep'ом.

| # | Файл | Вердикт | Severity | Ключевое |
|---|------|---------|----------|----------|
| 01 | SocialRecoveryModule.t.sol | DEFECT | high | L52-54: mock передаётся руками → probe 128/160 байт НЕ тестируется, fallback-деплой src/MockP256 не исполняется никогда; тесты зелёные из-за accept-all мока |
| 02 | Proposal.t.sol | DEFECT | high | flash-loan голосование не покрыто (снапшота нет ни в src, ни в тестах — grep snapshot пуст) |
| 03 | helpers/P256Verifier, FCLP256Verifier | DEFECT | high | тест-файлов НЕТ вообще; low-s malleability не отсекается и не тестируется |
| 04 | RefundVault.t.sol | DEFECT | med | нет deposit→claim→deposit→claim (баг claimed не сбрасывается не ловится) |
| 05 | TrustProviderRegistry.t.sol | DEFECT | med | L126-127 UUPS_SLOT=0xa3f0ad74... = EIP-1967 BEACON-слот (copy-paste, не implementation-слот) → ложная уверенность; нет renounceOwnership; EcdsaVerifier невалидная длина не покрыта |
| 06 | MDAOSmartAccount.t.sol | DEFECT | med | только 65-байтные сигнатуры (L71-97, _dummyUserOp L163 new bytes(65)) |
| 07 | PaymentSplitter.t.sol | DEFECT | med | MockERC20 без fee-on-transfer → underflow не тестируется |
| 08 | MDAOPaymaster.t.sol | DEFECT | med | короткий paymasterData = ровно 84 байта (L1004); <84 → panic 0x32 не покрыт |
| 09 | mocks/MockP256.sol | DEFECT | med | guard только chainId!=56; vm.chainId(56) нигде; src/MockP256 ни разу не деплоится в тестах |
| 10 | SessionKeyModule.t.sol | DEFECT | low | >50 ключей → hook 50k gas не покрыт |
| 11 | invariant/MDAOPaymaster.invariant.sol | DEFECT | low | L45-56 тавтологии (assertLe x ≤ uint256.max, assertGe ≥ 0); нет handler-контрактов |
| 12 | invariant/PaymasterInvariant.t.sol | DEFECT | low | L26 проверяет константу конструктора — бессмысленный инвариант |
| 13 | integration/RecoveryHooksIntegration.t.sol | DEFECT | low | нет сценария hook-gas-failure → RecoveryHookFailed → ключи не отозваны |
| 14 | DeadManSwitch/InsuranceFund/Treasury/MDAOToken/NicknameRegistry/PaymentSplitterFactory .t.sol | OK | - | покрытие нормальное |
| 15 | Integration/Smoke/BackendContractIntegration .t.sol | OPT | - | дымовые/интеграционные |
| 16 | fuzz/MDAOPaymaster.fuzz.sol | OPT | - | 2 bounded-теста, bound корректны (uint128.max) |

**Покрытие src (25) vs test (23): БЕЗ тестов — helpers/P256Verifier.sol, helpers/FCLP256Verifier.sol, src/MockP256.sol, EcdsaVerifier.sol (нет отдельного файла), ITrustProvider.sol.**

**ТОП-3 что добавить перед деплоем:**
1. Probe-тест SRM: настоящий 160-байтный verifier → 128-байтный probe валится → деплой src/MockP256; + vm.chainId(56) → reject мока.
2. Flash-loan голосование Proposal: mint→vote→transfer→execute — тест должен упасть (сейчас пройдёт).
3. Граничные: paymasterData длины {0..83} (panic 0x32), подпись ≠65 в SmartAccount, deposit→claim→deposit в RefundVault, fee-on-transfer токен в PaymentSplitter.

**Отмечено:** тесты SRM зелёные ровно потому, что подпись НЕ проверяется (accept-all mock) — это то же поведение, что в проде после probe-бага → ложная уверенность по криптографии recovery.

---

## C4 — Тесты контрактов (23 файла) [DONE 2026-08-10]

Метод: qa_tester (deepseek-v4), ключевые вердикты верифицированы мной (grep).

| # | Файл | Вердикт | Severity | Ключевое |
|---|------|---------|----------|----------|
| 01 | SocialRecoveryModule.t.sol | DEFECT | high | L54: mock передаётся РУКАМИ → probe `_isP256Working` не тестируется; ветка fallback-деплоя src/MockP256 не исполняется ни в одном тесте; approveRecovery тестирует accept-all мока (любой 64-байтный мусор проходит) |
| 02 | Proposal.t.sol | DEFECT | high | 35 тестов, но НЕТ снапшота балансов; flash-loan голосование не покрыто (grep snapshot пуст в src и тестах) |
| 03 | helpers/P256Verifier + FCLP256Verifier | DEFECT | high | тест-файлов НЕТ вообще (ls contracts/test/helpers пуст); low-s malleability не отсекается и не тестируется |
| 04 | RefundVault.t.sol | DEFECT | med | нет deposit→claim→deposit→claim (баг claimed не сбрасывается не ловится) |
| 05 | TrustProviderRegistry.t.sol | DEFECT | med→low | L126: UUPS_SLOT = 0xa3f0ad74... = EIP-1967 BEACON-слот (не implementation 0x360894...) — копипаст, но на non-proxy контракте тест всё равно проходит (ложная уверенность безвредна); нет renounceOwnership; EcdsaVerifier невалидная длина не покрыта |
| 06 | MDAOSmartAccount.t.sol | DEFECT | med | только 65-байтные сигнатуры; подпись ≠65 (revert вместо SIG_VALIDATION_FAILED) не покрыта |
| 07 | PaymentSplitter.t.sol | DEFECT | med | MockERC20 без fee-on-transfer → underflow не тестируется |
| 08 | MDAOPaymaster.t.sol | DEFECT | med | короткий paymasterData = ровно 84 байта, ожидает generic revert; <84 (panic 0x32) не тестируется |
| 09 | mocks/MockP256.sol | DEFECT | med | guard только chainId!=56; vm.chainId(56) нигде нет; src/MockP256 ни разу не деплоится в тестах |
| 10 | SessionKeyModule.t.sol | DEFECT | low | нет теста >50 ключей → hook gas fail |
| 11 | invariant/MDAOPaymaster.invariant.sol | DEFECT | low | L45-48,52,56 — тавтологии: assertLe(x, type(uint256).max), assertGe(x, 0) всегда true; нет handler-контрактов |
| 12 | invariant/PaymasterInvariant.t.sol | DEFECT | low | L26: invariant_trustedSignerNotZero проверяет константу конструктора |
| 13 | integration/RecoveryHooksIntegration.t.sol | DEFECT | low | нет gas-failure hook сценария |
| 14 | DeadManSwitch/InsuranceFund/Treasury/MDAOToken/NicknameRegistry/PaymentSplitterFactory | OK | - | покрытие нормальное |
| 15 | Integration/Smoke/BackendContractIntegration | OPT | - | дымовые |
| 16 | fuzz/MDAOPaymaster.fuzz.sol | OPT | - | 2 bounded-теста (uint128.max), но не трогают validate/parse |

**Покрытие src (25) vs test (23): без тестов — helpers/P256Verifier, helpers/FCLP256Verifier, src/MockP256 (никогда не деплоится в тестах), EcdsaVerifier (нет отдельного файла), ITrustProvider.**

**ТОП-3 добавить перед деплоем:**
1. Probe-тест SRM: настоящий 160-байтный verifier → проверить что 128-байтный probe валится и деплоится src/MockP256; + vm.chainId(56) → reject мока.
2. Flash-loan голосование Proposal: mint → vote → transfer → execute — должен упасть (сейчас пройдёт).
3. Граничные: paymasterData {0..83} (panic 0x32), подпись ≠65 в SmartAccount, deposit→claim→deposit в RefundVault, fee-on-transfer токен в PaymentSplitter.

**Критично для деплоя:** тесты SRM зелёные РОВНО потому, что подпись НЕ проверяется (accept-all mock) — это же поведение в проде после probe-бага → ложная уверенность по криптографии recovery. Инварианты-тавтологии дают ложную уверенность по стабильности paymaster.

## Фаза D — Relay (Cloudflare Worker) — agent: reviewer-relay (big-pickle)

**Verdict: reject (2 blocker, 2 high)**

| Файл | Вердикт | Severity | Находка |
|---|---|---|---|
| auth.ts | DEFECT | **blocker** | B3: L88 `${timestamp}.${nonce}.${body}` при nonce='' → `ts..body` (2 точки) vs клиенты/тесты `${ts}.${body}` (1 точка) → HMAC mismatch, 401 всем без X-Nonce; тест auth.test.ts:74-75 красный |
| RelayClient.kt (app) | DEFECT | **blocker** | get()/post() НЕ добавляют X-Timestamp/X-Signature/X-Nonce; в app нет RELAY_HMAC_SECRET (BuildConfig только RELAY_URL) → relay мёртвый транспорт, никто не пройдёт 401 даже после фикса B3 |
| fcm.ts | DEFECT | high | L9-15 legacy API `fcm.googleapis.com/fcm/send` + `Authorization: key=` — выключен Google июнь 2025; требуется HTTP v1 (OAuth2 service account); push не работает |
| index.ts | DEFECT | high | L116-119 лимит 100KB (F-069) только по Content-Length; chunked без CL читается целиком (платф. лимит 100MB) |
| index.ts | DEFECT | med | L134,182,220,269,306,316 JSON.parse битого body → 500 вместо 400 (клиент ретраит 5xx) |
| index.ts | DEFECT | med | L226-234,273-281 + storage.ts:127-137 guardian кошелька A может подписать approve для recovery кошелька B (связка identityHash→wallet не проверяется) |
| auth.ts | DEBT | med | replay-окно 5 мин, nonce нигде не хранится → перехваченный запрос реплеится 5 мин |
| storage.ts | DEFECT | med | push-токены БЕЗ expirationTtl, массив неограничен; FCM batch-лимит 1000 |
| storage.ts | DEBT | med | guardian-ключ 90d → recovery для кошелька старше ~97 дней → 401 'Guardian not found' |
| index.ts | DEBT | med | rate-limit in-memory: multi-isolate обход; Map не эвактуирует ключи |
| index.ts | DEFECT | low | fcmToken/shareIndex не в валидации → "[null]" токены в KV |
| index.ts | DEBT | low | 429 без SECURITY_HEADERS/CORS; /recovery/notify не проверяет pending; approve/veto не проверяют deadline |
| wrangler.toml | DEBT | low | KV id/preview_id пустые → deploy упадёт; секреты корректно вне toml |
| invite.test.ts | DEFECT | med | vi.mock('../auth') мокает подписи → B3 не ловится; нет nonce-пути, 400, 413 без CL |
| types.ts | OK | - | |

**ТОП-3:** (1) B3 auth.ts:88 — nonce ? `ts.nonce.body` : `ts.body` + синхронизировать auth.test.ts; (2) RelayClient.kt — реализовать HMAC-подпись или проксировать через backend (где RELAY_HMAC_SECRET); (3) FCM — миграция на HTTP v1.

## Фаза E — App Android, группа 1/5 (Application/DI/Network/Security/Blockchain/Config) — agent: reviewer (big-pickle)

**Verdict: reject — score 48/100** (1 blocker + 4 high). НЕ готово к тестнету.

| Файл | Вердикт | Severity | Проблема |
|---|---|---|---|
| NetworkConfig.kt | DEFECT | **blocker** | L20 SOCIAL_RECOVERY_MODULE = const 0x0000...0 «Set by deploy script» — НЕ конфигурируется (в build.gradle.kts buildConfigField только MDAO_CONTRACT, нет SOCIAL_RECOVERY_MODULE; ПОДТВЕРЖДЕНО grep) → isConfigured() (L26-28) всегда false → Home «Contracts not deployed» во всех флейворах; RecoveryUserOpBuilder:44,129 executeRecovery против address(0) — успешный no-op |
| PaymasterClient.kt | DEFECT | high | L126-129 POST /v1/sign БЕЗ Authorization: Bearer (во всём app НЕТ ни одного addHeader с JWT — grep пуст): либо backend без авторизации (paymaster abuse), либо gasless сломан → fallback на нативный газ; getQuote()/encodePaymasterAndData() — мёртвый код |
| OfflineSyncWorker.kt + MDAOPayApplication.kt | DEFECT | high | ExistingWorkPolicy.REPLACE при старте отменяет worker во время отправленной userOp → двойная отправка; записи retryCount>=MAX_RETRIES не удаляются → Result.retry() вечно |
| NetworkModule.kt | DEFECT | high | Pinning молча off если CERT_PIN_* placeholder'ы (hasRealPins()==false); покрывает только api.mdaopay.com, НЕ relay.workers.dev/bundler/backend/JWKS Google |
| RecoveryShareManager.kt | DEFECT | high | share1/3 + evalInput на ключах БЕЗ биометрической привязки (share2/4 — с привязкой); exportShare3Hex()/exportShare4Hex() отдают доли открытым hex (скриншот = утечка); saveShare2Encrypted несовместим с getShare2 (AEAD mismatch) |
| SocialAuthManager.kt | DEFECT | med | Google sign-in БЕЗ nonce (OIDC replay); idToken парсится без проверки подписи |
| BiometricManager.kt | DEFECT | med | else-ветка мапит ERROR_UNSUPPORTED/HW_UNAVAILABLE в Available |
| EthereumProviderInjector.kt | DEFECT | med | confirmAction() блокирует JavaBridge-поток latch.await(10s); .show() на уничтоженной Activity → BadTokenException crash |
| SendRepository.kt | DEFECT | med | газ захардкожен (1.5/1 Gwei) без eth_gasPrice; nonce из EntryPoint без инкремента/блокировки → NONCE_TOO_LOW |
| RecoveryUserOpBuilder.kt | DEFECT | high | recovery против address(0); 200 строк копипасты SendRepository; газ захардкожен |
| KeystoreCrypto.kt | DEBT | med | 300s окно биометрии; saveMnemonic молча false |
| DeviceIntegrityManager.kt | DEBT | med | BASIC→TRUSTED для денежных операций; мёртвый decodeJwtPayload |
| PasskeyManager.kt | DEBT | med | salt в открытом DataStore; RP ID mdaopay.app vs домен mdaopay.xyz |
| EtherscanRepository.kt | DEBT | med | пустой API-ключ → история молча пустая |
| MainActivity/NavGraph | DEBT | low | runBlocking×3 на main; base64 URL в deep link |
| OK: DatabaseModule, ConnectivityMonitor, AppLockManager, CborDecoder, GF256, ShamirSecretSharing, BlockchainRepository, TxErrorMapper, UserOperation, BundlerClient, ProductConfig | | | |

**ТОП-5:** (1) NetworkConfig SOCIAL_RECOVERY_MODULE → BuildConfig; (2) JWT-перехватчик OkHttp + /v1/sign с Bearer; (3) Worker KEEP + удаление мёртвых записей; (4) pinning fail-fast в release + пины на relay/bundler; (5) единая auth-политика долей + запрет экспорта без STRONG-биометрии.

## Фаза E — App Android, группа 2/5 (core/ui 40 + datastore 8 + common 5 + notification 5 + guardian 5) — agent: reviewer (big-pickle)

**Verdict: reject — score 32/100** (2 blocker + 3 critical + 3 high). Guardian-флоу не работает end-to-end.

| Файл | Вердикт | Severity | Проблема |
|---|---|---|---|
| core/guardian/RelayClient.kt | DEFECT | **blocker** | L119-150 НЕТ X-Timestamp/X-Nonce/X-Signature; RELAY_HMAC_SECRET отсутствует в app (grep пуст, ПОДТВЕРЖДЕНО) → relay fail-closed 401 на всё |
| core/guardian/GuardianContracts.kt | DEFECT | **critical** | CreateInviteRequest/GuardianInvite БЕЗ guardianPubKeyX/Y (grep пуст, ПОДТВЕРЖДЕНО) → relay index.ts:136-138 400 "Missing required fields" |
| core/guardian/GuardianManager.kt | DEFECT | **critical** | L81-95,131-135 PRF-вывод passkey выдан за ECDSA P-256 подпись (relay verifyP256Signature всегда false); relay-Result игнорируется — guardian сохраняется локально при провале; copyOfRange без size-гарда (L132-133) |
| core/notification/NotificationHelper.kt | DEFECT | high | POST_NOTIFICATIONS не объявлен/не запрошен (grep пуст, ПОДТВЕРЖДЕНО) → пуши мертвы на API 33+; PendingIntent requestCode=0 у всех |
| core/notification/TokenRegistrationWorker.kt | DEFECT | high | POST /register-push-token БЕЗ auth → произвольная привязка wallet↔FCM; JSON конкатенацией строк |
| core/ui/components/BiometricLockOverlay.kt | DEFECT | high | L148-149 LockState.Unavailable → onUnlock() БЕЗ PIN-фолбэка → лок обходится тапом на устройствах без биометрии |
| core/notification/NotificationChannels.kt | DEFECT | med | payments/security каналы VISIBILITY_PUBLIC + setBypassDnd(true) → чувствительные данные на lock screen |
| core/guardian/GuardianStorage.kt | DEFECT | med | identity_salt в plaintext SharedPreferences + blocking .commit(); decode без try/catch |
| core/guardian/GuardianUserOpBuilder.kt | DEFECT | med | on-chain флоу не вызывается из UI; approveRecovery failure глотается (L151-153); veto недостижим |
| core/ui/components/MDAOWebView.kt | DEFECT | med | initial loadUrl НЕ проверяется whitelist'ом (только shouldOverrideUrlLoading); SSL-ошибки делегируются (L63) |
| core/datastore/UserPreferences.kt | DEFECT | med | PIN hash/salt в открытом DataStore |
| core/common/PerformanceMonitor.kt | DEFECT | med | L31-52 trace start/stop сразу → длительность всегда ~0ms (фейковая телеметрия) |
| MDAOFirebaseMessagingService.kt | DEBT | med | push recovery_request без wallet/nonce, нет deep link → approve из пуша недостижим |
| BadgeManager.kt / ContactsStore / TransactionHistory / AppDatabase / Color.kt / ReducedMotion.kt | DEBT | low | стаб-0; decode без runCatching; нет миграций; алиасы; только TalkBack |
| Остальные 30 (ui components/theme/motion, TxQueue*, AppError, Extensions, NicknameGenerator, Result) | OK | — | |

**ТОП-5:** (1) OkHttp Interceptor: X-Signature=HMAC(BuildConfig.RELAY_HMAC_SECRET, "$ts.$nonce.$body"); (2) guardianPubKeyX/Y в CreateInviteRequest; (3) подписывать ECDSA-ключом passkey, не PRF; (4) PIN-фолбэк в BiometricLockOverlay; (5) POST_NOTIFICATIONS + auth токен-регистрации.

## Фаза E — App Android, группа 3/5 (features: settings/recovery/walletconnect/send/onboarding/home/history/assets/exchanger/contacts/connect/receive/main/states) — agent: reviewer (big-pickle)

**Verdict: reject** (1 critical + 5 high). 27 OK / 16 DEFECT / 6 DEBT / 4 DEAD.

| Файл | Вердикт | Severity | Проблема |
|---|---|---|---|
| settings/BackupScreen.kt | DEFECT | **critical** | seed-фраза и QR БЕЗ SecureScreen (grep пуст, ПОДТВЕРЖДЕНО) → скриншот/запись = кража кошелька; clipboard без авто-очистки. В Home/Send/Recovery SecureScreen ЕСТЬ |
| settings/SecurityScreen.kt | DEFECT | high | все тумблеры декоративные: remember{mutableStateOf} без записи в prefs (L55-59, ПОДТВЕРЖДЕНО) → пользователь верит что 2FA/защита включены |
| walletconnect/WalletConnectViewModel.kt | DEFECT | high | initialize(){}, pair()=false, approve/reject/disconnect пустые (L25-31, ПОДТВЕРЖДЕНО) — «подключение к dApp» фейк |
| recovery/RecoveryViewModel.kt | DEFECT | high | runBlocking PBKDF2 на main (L24,370); fcmToken="" (L508) → relay-push не придёт; verifyColdDevicePin мёртв |
| home/HomeViewModel.kt | DEFECT | high | WebView ethereum-мост (L147-155) без origin-валидации; мок-балансы |
| onboarding/OnboardingGuardianScreen.kt | DEFECT | high | выбор guardian'ов (READ_CONTACTS пикер) никуда не сохраняется |
| send/SendScreen.kt | DEFECT | med | фиат «≈ $0.00» захардкожен (L493) |
| home/WalletState.kt | DEFECT | med | TxStatus.valueOf без try/catch → краш на битых данных |
| recovery/RecoveryScreen.kt | DEFECT | med | share-hex в буфер без авто-очистки (L543-580) |
| receive/ReceiveScreen.kt | DEFECT | med | адрес/QR без SecureScreen |
| connect/ConnectViewModel.kt | DEFECT | med | DappInfo захардкожен, connect() = только биометрия |
| networks/NetworksScreen.kt | DEFECT | med | выбор сети не персистится, RPC фейковые |
| send/SendTransactionUseCase.kt | DEBT | med | tempTxHash "pending_{time}" до подтверждения |
| feature/states/* (4 файла ~1500 строк) | DEAD | med | ни одной ссылки из приложения — кандидат на удаление |
| ProfileScreen ShareButton / AssetDetails Float / OnrampScreen | DEBT | low | декоративные кнопки / потеря точности / WebView без SecureScreen |
| Остальные 27 | OK | — | |

**ТОП-5:** (1) BackupScreen → SecureScreen() + чистка clipboard; (2) SecurityScreen → привязать к userPreferences; (3) WalletConnect → реализовать или убрать; (4) RecoveryViewModel → Dispatchers.Default + FCM токен; (5) HomeViewModel origin-валидация моста.

## Фаза E — App Android, группа 4/4 (тесты 23 файла + AndroidManifest + buildTypes) — координатор

**Verdict: DEFECT (med)** — тесты есть (23 файла: core/blockchain, guardian, security, ui, di, domain), release-сборка с minify+shrink, allowBackup=false, но:

| Объект | Вердикт | Severity | Находка |
|---|---|---|---|
| AndroidManifest.xml | DEFECT | med | POST_NOTIFICATIONS НЕ объявлен (grep exit=1, ПОДТВЕРЖДЕНО) → пуши мертвы API 33+; READ_CONTACTS НЕ объявлен, а OnboardingGuardianScreen использует контакты-пикер → SecurityException на 23+; usesCleartextTraffic не задан (default false — ок) |
| app/build.gradle.kts | OK | — | release: isMinifyEnabled=true + shrinkResources + proguard-rules.pro; debug: isDebuggable=true + versionNameSuffix |
| app/src/test (23 файла) | DEBT | med | NetworkModuleTest, EthereumProviderInjectorTest, RpcProviderManagerTest, PaymasterClientTest, NetworkConfigTest, MDAOWebViewTest, PermissionMapperTest, GaslessTransactionOrchestratorTest есть; но UI/ViewModel-тестов нет, фейковые сценарии (WalletConnect, SecurityScreen) не тестируются |

**ТОП-3:** (1) POST_NOTIFICATIONS + READ_CONTACTS в манифест; (2) тесты на реальные флоу (send, guardian) с MockWebServer; (3) release-прогон proguard-правил для web3j/okhttp.

## Фаза F — Scripts + CI/CD (12 скриптов + 3 workflow) — Verdict: reject (score 22)

| Файл | Verdict | Severity | Evidence |
|---|---|---|---|
| deploy-testnet.sh | DEFECT | blocker | TRUSTED_SIGNER проверяется (L66,79) но НЕ передаётся в docker run (L562-572); передан RELAY_SECRET (L567), а AppConfig C-3 требует RELAY_JWT_SECRET/RELAY_HMAC_SECRET и ревертит на legacy → backend не стартует, health-wait 180с (шаг 10) падает |
| deploy-testnet.sh | DEFECT | blocker | relay: скрипт ставит RELAY_SECRET (L611,617-618), worker требует RELAY_HMAC_SECRET (index.ts:102, fail-closed → 401) |
| deploy-testnet.sh (через Deploy.s.sol) | DEFECT | blocker | addRecoveryHook без approveHook → ErrHookNotApproved (SRM:429-432) → forge-деплой (шаг 5) ревертит |
| deploy-testnet.sh | DEFECT | high | fallback INSURANCE_AUDITOR=$DEPLOYER_ADDR (L161-162) vs require(insuranceAuditor != deployer) в Deploy.s.sol:71 → гарантированный revert первого деплоя |
| deploy-testnet.sh | DEFECT | high | wrangler.toml kv_namespaces[].id = "" (L19-20,25) → wrangler deploy --env testnet упадёт (KV не создан) |
| deploy-testnet.sh | DEFECT | med | два источника секретов: скрипт читает AWS SM legacy-имена (jwt-secret, relay-secret), rotate-secrets.sh пишет новые в backend/.env → ротация не достигает системы |
| deploy-testnet.sh | DEBT | med | нет e2e-проверки после деплоя (только readback owner'а paymaster L414-421) |
| deploy-testnet.sh | DEBT | low | GNOSIS_SAFE default=deployer (L89); --private-key виден в ps; unset (L776) не выполнится при раннем abort |
| rotate-secrets.sh | DEFECT | high | ротация иллюзорна: пишет в backend/.env, который docker run НЕ читает (берёт -e из AWS SM); AWS SM не ротируется; инструкция docker compose restart неверна (деплой через docker run) |
| rotate-secrets.sh | DEBT | med | нет окна совместимости (dual-secret) при ротации HMAC → мгновенный разрыв подписей relay |
| db-restore.sh | DEFECT | high | psql БЕЗ --set ON_ERROR_STOP=1/--single-transaction → частичный restore молча exit 0 |
| check-cert-pins.sh | DEFECT | med | не-strict по умолчанию: недоступный домен → skip → pass при невалидных пинах; host не гарантирован; openssl без таймаута |
| scan-secrets.sh | OK | low | exit 1 при находке всегда; backend/.env с hex — ложное срабатывание |
| db-backup.sh | OK | low | нет таймаута pg_dump; пароль в ps |
| compact-findings.sh | DEBT | low | стуб «TODO: full implementation» — ничего не переносит в ARCHIVE |
| swarm-model.sh | DEBT | low | `if _llama_count -eq 0` (L40) — аргументы уходят в wc -l → всегда false → ждёт 5с |
| ci.yml | DEFECT | med | coverage gate no-op: grep "All files" не матчит forge формат ("| Total |") → LINE_COV пуст → порог не enforced (L40-45, 172-182) |
| ci.yml | DEBT | low | checkout@v7 в одной джобе vs @v4 в другой; trufflehog@v3 плавающий тег |
| deploy.yml | DEFECT | med | DATABASE_URL с кредами через --set-env-vars (виден в Cloud Run) вместо --set-secrets; --allow-unauthenticated на paymaster |
| deploy-testnet.yml | DEFECT | critical | ИНВЕРСИЯ dry-run: шаг «Deploy contracts (dry-run)» guarded if:inputs.dry_run, но содержит --broadcast → при dry_run=true РЕАЛЬНО деплоит, при false контракты НЕ деплоятся вообще; --account deployer требует keystore (в CI нет, DEPLOYER_PRIVATE_KEY игнорируется); --rpc-url bsc-testnet — неопределённый профиль → шаг падает в обоих режимах |

Верификация: docker run L562-572 не содержит TRUSTED_SIGNER (grep), содержит RELAY_SECRET; deploy-testnet.yml L34-35 подтверждён чтением; KV id пустые подтверждены.

ТОП-5: (1) Deploy.s.sol:107 approveHook; (2) backend env-набор (TRUSTED_SIGNER+RELAY_JWT/HMAC); (3) relay secret имя + KV; (4) rotate-secrets фиктивен; (5) deploy-testnet.yml инверсия dry-run + db-restore без ON_ERROR_STOP.

## Фаза G — Docs ↔ Code (27 находок, 4 OK) — Verdict: reject по документации деплоя

CRITICAL:
1. DEPLOY-TESTNET.md:3 «скрипт делает всё автоматически» vs Deploy.s.sol:140 addRecoveryHook БЕЗ approveHook (SRM:429-431 ErrHookNotApproved) — деплой ревертится
2. DEPLOY-TESTNET.md:196-197 учит RELAY_SECRET, а AppConfig C-3: RELAY_SECRET → hard error; требуются RELAY_JWT_SECRET/HMAC_SECRET
3. DEPLOY-TESTNET.md:5.3 «скрипт развернёт backend» vs deploy-testnet.sh:562-570 docker run БЕЗ TRUSTED_SIGNER/RELAY_JWT/HMAC → контейнер падает (AppConfig:127,151-159)
4. DEPLOY-TESTNET.md:271 «EntryPoint (v0.7)» — 0x5FF137 это v0.6 (подтверждено: PaymasterService:338 v0.6 hash, MDAOPaymaster IPaymasterV06)
5. Коллизия F-113 (ПОДТВЕРЖДЕНО m0303): ROADMAP_SECURITY_FIXES.md:872 «Paymaster мигрирован на v0.7»[ ] vs ROADMAP.md:150 «Голосование по proposal»[x] vs ROADMAP-STATUS.md:40 «v0.6 deprecated→v0.7»[ ] — три разные вещи под одним ID

HIGH:
6. deploy-testnet.sh:155-158 INSURANCE_AUDITOR fallback=deployer vs Deploy.s.sol:69 require(insuranceAuditor != deployer) → первый запуск ревертится
7. launch-instructions.md:55 `cp .env.bsc .env` — .env.bsc/.env.example не содержат required (TRUSTED_SIGNER, RELAY_JWT/HMAC, SWAP_PRIVATE_KEY, JWT_SECRET, WBNB_ADDRESS)
8. api-keys-setup.md:6-9 BUNDLER_STACKUP_KEY/PIMLICO_KEY — таких ключей нет в app/build.gradle.kts (там BUNDLER_URL_DEV/STAGING/PROD, ETHERSCAN_API_KEY)
9. api-keys-setup.md:13-25 таблица env врёт (EXPECTED_CHAIN_ID «No», но AppConfig:129 required; REDIS_URL, WBNB_ADDRESS required)

MEDIUM:
10. ROADMAP-STATUS.md:35-36 F-110/F-111 [ ] — оба фикса в коде есть (JWT entropy, ALLOW_LOCAL_SIGNING guard)
11. runbooks/PAYMASTER-DOWN.md:8 kubectl — деплой docker, не k8s
12. e2e-test-plan.md:18 PAYMASTER_ADDRESS=0xF6Dca93 (устарел); :152 docker logs mdaopay-paymaster (реальный mdaopay-backend); Flow 6 «429 per-sender» — в relay лимит per-IP (index.ts:36-39)
13. e2e-test-plan.md Flow 3 «2-of-3» vs RecoveryShareManager:283-284 REQUIRED=3,TOTAL=4 (SSS 3-of-4)
14. README.md:37 ./gradlew bootRun — нет такой task (Ktor, build.gradle.kts:7-8 mainClass ApplicationKt; корректно ./gradlew run) — ПОДТВЕРЖДЕНО m0304

LOW/DEBT: README.md:81-86 «336 тестов» (не сходится); ROADMAP.md:191 S-207 ссылка на несуществующий ADR-004-vision-gap; GOOGLE-PLAY targetSdk 34 vs 35; ARCHITECTURE-recovery 72h vs SRM:50 TIMELOCK=48h + упрощённые сигнатуры; ANALYTICS-SETUP заявлен, crashlytics/firebase-perf закомментированы (build.gradle.kts:9-10); DEPLOY-TESTNET --verifier-url api-testnet.bscscan.com vs launch-instructions testnet.bscscan.com; deploy-testnet.sh:494 KMS_REGION мёртв; api-keys-setup «один ключ testnet/mainnet» vs F-035 разделение.

OK (совпадает): DEPLOY-TESTNET.md:5.5 /v1/events ↔ Application.kt:375; 5.4 /health ↔ Application.kt:503; ARCHITECTURE-recovery relay-роуты ↔ index.ts; ROADMAP F-104 поллинг 6 контрактов ↔ Application.kt:227-234; C-04 ADR-005 KV/DO статус честный.

Приоритет обновления: DEPLOY-TESTNET.md (RELAY секреты, EntryPoint v0.6, INSURANCE_AUDITOR, approveHook) → deploy-testnet.sh (env docker run) → env-шаблоны → ROADMAP-STATUS F-110/111 → api-keys-setup/e2e/runbook/README.

## Фаза H — Кросс-слойная сверка (score 38) — Verdict: REJECT

### Матрица EntryPoint (КРИТИЧНО — подтверждено m0311-312)
- MDAOPaymaster.sol:17-51 → **v0.6** (IPaymasterV06, unpacked 10 полей)
- MDAOSmartAccount.sol:9,63 → **v0.7** (PackedUserOperation, lib account-abstraction@0.9.0) — РАСКОЛ
- DeployMDAOPaymaster.s.sol:29 → v0.6 (0x5FF1...)
- backend PaymasterService.kt:340-360, AppConfig.kt:124 → v0.6 (hash v0.6)
- app UserOperation.kt:22-47, NetworkConfig.kt:18 → v0.6 (сверено поле-в-поле с backend — OK)
- relay → не участвует (нет UserOp-кода)
- **Фактический аккаунт**: RecoveryUserOpBuilder.kt:229,286 → SimpleAccount v0.6 (createAccount 0x9406Cc...) — MDAOSmartAccount НИГДЕ НЕ ДЕПЛОИТСЯ (скрипта нет!)
- Вывод: app↔backend↔paymaster↔EP согласованы по v0.6. MDAOSmartAccount v0.7 мёртв (селектор не совпадает). SimpleAccount v0.6 НЕ имеет recoveryCaller → executeRecovery transferOwnership ВСЕГДА ревертится (SRM:410-418, молча глотает). **Recovery-механика неработоспособна в принципе (CRITICAL B1+B2)**

### API match app↔backend (11 находок)
- A1 HIGH: ProposalRepository.kt:62 GET ${BACKEND_URL}/events (БЕЗ /v1) vs Application.kt:375 /v1/events → 404 всегда — ПОДТВЕРЖДЕНО
- A2 HIGH: contract=Proposal (имя) vs contract_address (адрес, lowercase) → пусто всегда — ПОДТВЕРЖДЕНО
- A3 HIGH: поле param_json (L29) vs backend отдаёт params (Application.kt:441) → всегда "{}" — ПОДТВЕРЖДЕНО
- A4 HIGH: TokenRegistrationWorker.kt:39 POST /register-push-token — роута НЕТ в backend — ПОДТВЕРЖДЕНО (grep пуст) → FCM мёртв, вечный retry
- A5 HIGH: CreateInviteRequest БЕЗ guardianPubKeyX/Y vs relay index.ts:135-136 обязательны → 400 на каждый инвайт
- A6 MED: PRF-байты как ECDSA P-256 подпись → verifyP256Signature всегда 401; результат игнорируется (GuardianManager.kt:82-94)
- A7 MED: 5 методов RelayClient мёртвые (вызовов из UI нет); setPendingRecovery только изнутри
- A8 LOW: бизнес-ошибки /v1/sign HTTP 200 {"error"} (Application.kt:359-363) vs app ждёт 400/429 → Unknown(JSONException)
- A9 LOW: ETHERSCAN_API_KEY зашит в клиент; /v1/etherscan-proxy не используется
- A10 DEBT: exchanger-заглушки без HTTP
- OK: POST /v1/sign — пути и SignRequest-поля совпадают (сверено)

### ABI match контракты↔backend↔app (11 находок)
- B1 CRITICAL: MDAOSmartAccount v0.7 vs EP v0.6 — validateUserOp селектор не совпадает
- B2 CRITICAL: SRM:410-418 transferOwnership vs SimpleAccount без recoveryCaller → всегда реверт
- B3 HIGH: SOCIAL_RECOVERY_MODULE=0x000 (NetworkConfig.kt:20) — recovery-UserOps бьют в ноль; isConfigured не проверяется
- B4 HIGH: EventIndexer.kt:276 ProposalCreated(uint256,address,bytes32[],uint256,uint256,string) vs IProposal.sol:18-24 (uint256,bytes32,address,uint256,string, 3 indexed) — topic-hash не совпадает — ПОДТВЕРЖДЕНО
- B5 HIGH: VoteCast(uint256,address,uint8,uint256) vs IProposal.sol:26-31 +string reason — не совпадает — ПОДТВЕРЖДЕНО
- B6 HIGH: SessionKeyCreated(bytes32,address,address,bytes32[],uint256,uint256,uint8) vs SessionKeyModule.sol:45-52 без bytes32[] — не совпадает
- B7 MED: WatchtowerService.kt:49-56 RecoveryInitiated[Address,Bytes32,Uint256,Uint256] vs SRM.sol:108 (address indexed, bytes32 indexed, bytes32 indexed, address, uint256, uint256) — слеп
- B8 MED: EventIndexer.kt:189-192 все 32-байт topics режутся до 20 байт — bytes32 портятся
- B9 LOW: SessionKeyUsed indexedCount=3 (надо 2), SwitchSet=1 (надо 2)
- B10 DEBT: paymasterEvents определён (L319) но paymaster НЕ в indexerContracts (Application.kt:227-234)
- B11 HIGH: confirmGuardian всегда ErrNotGuardian — addGuardian app НЕ вызывает никогда (identityHash=keccak256(msg.sender))
- OK: approveRecovery/vetoRecovery, execute/executeRecovery/createAccount/getNonce, EIP-712 Quote typehash (PaymasterService:34 ↔ Paymaster.sol:144-145), magic 22e325a297439656 (L280↔L369), v0.6 userOpHash app↔backend, Treasury/PaymentSplitter/SR/DMS events OK

### Мёртвый код (доказано grep'ом)
- feature/states/* (4 экрана), RecoveryScreen.kt с ПРОБЕЛОМ в имени (471B заглушка), WalletConnectManager+feature/walletconnect+feature/connect, NotificationsCenterScreen, 8 UI-компонентов (CrashBoundary, MDAOBlock/Card/ChipBadge/PullToRefresh/RadioCard, NebulaOverlay, PageIndicator), core/ui/motion/*, PaymasterClient.getQuote+encodePaymasterAndData, GuardianManager{approve/veto/pollPendingRecoveries}+RelayClient{getPendingRecoveries,submitApproval,submitVeto,registerPushToken,notifyRecoveryInitiated}, relay storage.ts NONCE_KEY, relay index.ts:345-346 env SOCIAL_RECOVERY_MODULE/RPC_URL
- ЖИВОЕ (не трогать): TrustConstellation (RecoveryScreen.kt:937), TokenRegistrationWorker (MDAOFirebaseMessagingService.kt:18), оба RecoveryScreen в MDAONavGraph.kt:228,252

### Рантайм-конфиг (ПОДТВЕРЖДЕНО m0311)
- C1 HIGH: BUNDLER_URL = findProperty("BUNDLER_URL_*") ?: "" — пустой fallback (build.gradle.kts:31,53,69), свойств нет в gradle.properties → BundlerClient.kt:69 .url("") → каждая сборка БЕЗ -P не отправляет ни одного UserOp
- C2 HIGH: PAYMASTER_CONTRACT (dev) без суффикса _DEV (build.gradle.kts:33) ?: 0x000 → нулевой адрес
- C3 HIGH: PROPOSAL_CONTRACT и MDAO_CONTRACT (dev) = 0x000 (L29,37) → усугубляет A2
- C4 LOW: NetworkConfigTest.kt:29-35 проверяет только наличие ключа, не непустоту — зелёный на сломанной конфиге

### Оптимизации (OPT)
- app/build.gradle.kts:184 retrofit.core — НЕ используется (сеть на OkHttp) → удалить
- v0.6-userOpHash реализован 3 раза (UserOperation.kt, PaymasterService.kt:340, relay?) → один модуль + эталонные векторы eth_getUserOpHash
- backend/build.gradle.kts:49-52 TODO web3j 4.14/JDK21
- relay rate-limit in-memory per-isolate; CORS захардкожен на один origin
- app/build.gradle.kts:80 RELAY_URL прод = *.ekzent.workers.dev — личный домен

### Топ-5: (1) EntryPoint-раскол + recovery неработоспособен (B1+B2), (2) Proposal мёртв трижды (A1+A2+A3), (3) FCM в вечном ретрае (A4), (4) guardian мёртв на обоих уровнях (A5+B11), (5) EventIndexer слеп к 3 из 6 контрактов (B4+B5+B6)

## Фаза I — ФИНАЛЬНЫЙ ВЕРДИКТ (сводка A-H)

### Итог по фазам
| Фаза | Объект | Вердикт |
|---|---|---|
| A | Инфраструктура, CI, .env | DEFECT (CI backend/app FAIL, .env.example без 4 required) |
| B | Backend Kotlin (13+16 файлов) | REJECT (score 52): NicknameRepository BLOCKER, SwapService drain HIGH |
| C | Contracts (src 25 + scripts 7 + тесты 23) | REJECT: SRM probe BLOCKER, Deploy.s.sol BLOCKER, EntryPoint-раскол CRITICAL |
| D | relay TS | REJECT: 2 BLOCKER (auth.ts HMAC, RelayClient без HMAC) |
| E | app Android | REJECT: 3 BLOCKER, 4 CRITICAL, 8 HIGH |
| F | scripts + CI/CD | REJECT (score 22): deploy-testnet.sh blocker×3, yml dry-run инверсия CRITICAL |
| G | Docs ↔ Code | REJECT: 5 CRITICAL расхождений |
| H | Кросс-слой | REJECT (score 38): recovery неработоспособен, proposal мёртв, EventIndexer слеп |

### ПОЛНЫЙ СПИСОК BLOCKER (критично для тестнета)
1. **B1** NicknameRepository: ON CONFLICT (LOWER(nickname)) без UNIQUE-индекса → Postgres 42P10 — регистрация ников мертва
2. **B3/D** relay auth.ts:88: nonce='' → `ts..body` (2 точки) vs тест `ts.body` → HMAC mismatch (regression 83f291e)
3. **C1** SocialRecoveryModule `_isP256Working`: 128 нулевых байт vs RIP-7212 требует 160 → probe=false → MockP256 accept-all (НАШ MERGE 718b8f9!)
4. **C3/F** Deploy.s.sol: addRecoveryHook БЕЗ approveHook (SRM:429 ErrHookNotApproved) → деплой ревертится
5. **C3** DeploySocialRecoveryModule.s.sol: default P256_VERIFIER=0x100 → accept-all
6. **C3/H** EntryPoint-раскол: paymaster/backend/app = v0.6 (0x5FF1), MDAOSmartAccount = v0.7 PackedUserOperation — ABI-несовместимы; MDAOSmartAccount НИГДЕ не деплоится; SimpleAccount v0.6 БЕЗ recoveryCaller → recovery-передача владения невозможна в принципе (B2 H)
7. **D/E** RelayClient.kt без HMAC-заголовков → relay мёртвый транспорт для app
8. **E** NetworkConfig.kt:20 SOCIAL_RECOVERY_MODULE=0x000, isConfigured()=false → Home «Contracts not deployed»
9. **E** GuardianContracts без guardianPubKeyX/Y → relay 400 на инвайт
10. **E** GuardianManager PRF-as-ECDSA → verifyP256Signature всегда 401
11. **F** deploy-testnet.sh: docker run БЕЗ TRUSTED_SIGNER/RELAY_JWT/HMAC + deprecated RELAY_SECRET → backend не стартует
12. **F** deploy-testnet.yml: ИНВЕРСИЯ dry-run — --broadcast под if:inputs.dry_run (при dry_run=true РЕАЛЬНО деплоит)
13. **H-C1** BUNDLER_URL fallback="" (build.gradle.kts:31,53,69) → сборка без -P не отправляет UserOp вообще

### CRITICAL (вне блокеров, но фатально для фич)
- Proposal-фича мёртва трижды: /events vs /v1/events, contract=имя vs адрес, param_json vs params (H-A1..A3)
- EventIndexer слеп к 3 из 6 контрактов (ProposalCreated/VoteCast/SessionKeyCreated topic-hash'и не совпадают) + Watchtower слеп к RecoveryInitiated (H-B4..B7)
- FCM: POST /register-push-token не существует на backend (H-A4)
- deploy-testnet.yml --account deployer требует keystore, отсутствующего в CI (F)

### Мёртвый/нейронный код (кандидаты на удаление)
feature/states/* (4 файла), RecoveryScreen.kt с пробелом в имени, WalletConnectManager + feature/walletconnect + feature/connect, NotificationsCenterScreen, 8 UI-компонентов, core/ui/motion/*, getQuote/encodePaymasterAndData, 5 методов RelayClient/GuardianManager, relay NONCE_KEY, retrofit.core зависимость

### Что ОК (не требует починки)
- /v1/sign контракт (пути+поля+EIP-712 Quote typehash+magic 22e325a297439656) — app↔backend согласованы
- v0.6 userOpHash app↔backend (сверено поле-в-поле)
- Treasury/PaymentSplitter/SR/DMS events topic-сигнатуры совпадают
- approveRecovery/vetoRecovery ABI согласованы
- /health, /v1/events структура, relay-роуты — совпадают

### Рекомендованный порядок починки (для Coder после одобрения)
1. Файз 1 (контракты): SRM probe 160 байт (C1) → approveHook в Deploy.s.sol (C3) → P256_VERIFIER default (C3) → решить EntryPoint: единая v0.6 (переписать MDAOSmartAccount) ИЛИ полный v0.7-стек (paymaster+hash+app+bundler+EP-адрес) + скрипт деплоя аккаунта (H)
2. Файз 2 (backend): UNIQUE-индекс nicknames (B1) → SwapService drain-фикс (B2) → /register-push-token роут (H-A4) → EventIndexer topic-сигнатуры из контрактов (H-B4..B7) → bytes32-маппинг (H-B8)
3. Файз 3 (relay): auth.ts:88 фикс + добавить HMAC в RelayClient.kt (B3/D/E)
4. Файз 4 (app): NetworkConfig адреса из BuildConfig (E) → CreateInviteRequest pubKey (E) → BUNDLER_URL дефолты в gradle.properties (H-C1) → ProposalRepository пути/поля (H-A1..A3)
5. Файз 5 (деплой): deploy-testnet.sh env (F) → dry-run инверсия (F) → INSURANCE_AUDITOR (F/G) → доки (G)

### Оценка готовности: НЕ ГОТОВ к тестнету. Ориентир: 5 фаз починки выше + повторный прогон аудита.

## Фаза J — Анализ корневой причины (error_detective, 5 Whys)

**Гипотеза пользователя ПОДТВЕРЖДЕНА: одна корневая причина объясняет ≥85% блокеров/критикалов.**

### Единая формулировка корня (R1+R2 — неразделимая пара):
> «Фича готова, когда зелёный её собственный юнит-тест, а не когда она работает вертикально сквозь слои в интегрированном окружении» — при этом интерфейсы и конфиги, по которым слои стыкуются, нигде не зафиксированы.

- **R1 (порождающая)**: нет единого источника правды для межслойных контрактов (API-поля/роуты, ABI/topic-сигнатуры, calldata-спеки RIP-7212, реестр env-ключей, адреса). Каждый слой пишет их по памяти.
- **R2 (обнаруживающая)**: нет интеграционной проверки вертикального среза как обязательного гейта (contract tests, forge script --dry-run в CI, docs↔code, fail-fast конфигов, enforced merge checks). DoD = «зелёный юнит моего слоя».
- **R3 (усилитель, не самостоятельный корень)**: систематический fail-open — envOr(...,0), ?: "", тесты-заглушки, декоративные тумблеры.

### Доказательства:
1. **Фаза H — первая кросс-слойная сверка в истории проекта** — именно она нашла EntryPoint-раскол, мёртвый Proposal, слепой EventIndexer, FCM, BUNDLER_URL.
2. **Признание автора интеграции** (:126 журнала): «МОЯ ОШИБКА: оценивал логику без проверки спецификации calldata» — R1 в одной строке.
3. **approveHook/dry-run/KV id/TRUSTED_SIGNER** — класс ошибок, каждая ловится ОДНИМ запуском скрипта; forge-скрипты в ci.yml отсутствуют (grep подтвердил).
4. **auth.ts red-тест** (83f291e, 17 июля → 10 августа): CI гоняет npm test, но проверка НЕ enforced — регрессия дожила до аудита.
5. Совпало ровно то, что заставила совпасть криптография (EIP-712 typehash, magic, userOpHash v0.6).

### Матрица: R1 объясняет 13/18 ключевых находок (порождение), R2 — 16/18 (выживание), R1∪R2 ~90%.
НЕ объясняет (~10-15%, отдельные корни): SwapService drain, fcm.ts legacy API, CVE undici, TOCTOU/race, UUPS_SLOT copy-paste.

### Меры (конкретные):
1. **R1**: реестр env-ключей в одном файле + тест «все ключи кода/скриптов/доков ∈ реестр»; деплой публикует JSON (адреса+ABI) → app/backend читают его; EventIndexer/relay генерируют topic-hash из forge-artifacts, не руками.
2. **R2**: forge script --dry-run для каждого Deploy*.s.sol в CI; e2e-смоук «deploy-testnet.sh env → docker run → /health → /v1/sign»; contract-тесты app↔relay↔backend (HMAC-векторы ts.body и ts.nonce.body); CI-проверки required.
3. **R3**: fail-fast — envOr обязательных значений с require(non-zero), BUNDLER_URL без -P = ошибка сборки, NetworkConfigTest проверяет непустоту, isConfigured() гейтит UI.
4. **DoD**: чек-лист «вертикальный срез» — фича не закрыта без e2e-сценария через все слои + синхронизация доков.
