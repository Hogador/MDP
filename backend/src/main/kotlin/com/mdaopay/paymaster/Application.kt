package com.mdaopay.paymaster

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*
import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.ContentType
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.web3j.crypto.ECKeyPair
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Numeric
import org.slf4j.LoggerFactory
import com.mdaopay.paymaster.signing.KmsPaymasterSigner
import com.mdaopay.paymaster.signing.LocalPaymasterSigner
import com.mdaopay.paymaster.signing.PaymasterSigner
import com.mdaopay.paymaster.util.LogSanitizer
import software.amazon.awssdk.services.kms.KmsClient
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

private val log = LoggerFactory.getLogger("MDAOPaymaster")
private val etherscanClient = HttpClient(CIO) {
    expectSuccess = false
}

private const val SENDER_RATE_LIMIT = 1
private const val SENDER_WINDOW_SEC = 30L
private const val IP_RATE_LIMIT = 20
private const val IP_WINDOW_SEC = 60L
private const val AUTH_LOGIN_LIMIT = 5
private const val AUTH_REGISTER_LIMIT = 3
private const val AUTH_REFRESH_LIMIT = 10
private const val AUTH_WINDOW_SEC = 60L

/** Trusted proxy CIDR ranges — Cloudflare + RFC1918 internal networks. */
private val TRUSTED_PROXIES = listOf(
    "10.0.0.0/8",
    "172.16.0.0/12",
    "192.168.0.0/16",
    "173.245.48.0/20",
    "103.21.244.0/22",
    "103.22.200.0/22",
    "103.31.4.0/22",
    "141.101.64.0/18",
    "108.162.192.0/18",
    "190.93.240.0/20",
    "188.114.96.0/20",
    "197.234.240.0/22",
    "198.41.128.0/17",
    "162.158.0.0/15",
    "104.16.0.0/13",
    "104.24.0.0/14",
    "172.64.0.0/13",
    "131.0.72.0/22",
)

/** Check if an IP address falls within a CIDR range (stdlib only). */
private fun isIpInCidr(ip: String, cidr: String): Boolean {
    val parts = cidr.split("/")
    if (parts.size != 2) return false
    val prefixLen = parts[1].toIntOrNull() ?: return false
    val addr = InetAddress.getByName(ip).address
    val net = InetAddress.getByName(parts[0]).address
    if (addr.size != net.size) return false
    val fullBytes = prefixLen / 8
    val remBits = prefixLen % 8
    for (i in 0 until fullBytes) {
        if (addr[i] != net[i]) return false
    }
    if (remBits > 0) {
        val mask = (0xFF shl (8 - remBits)) and 0xFF
        if ((addr[fullBytes].toInt() and 0xFF) and mask != (net[fullBytes].toInt() and 0xFF) and mask) return false
    }
    return true
}

/**
 * Extract the real client IP address behind a reverse proxy.
 * Only trusts X-Forwarded-For / X-Real-IP when the immediate
 * connection comes from a known trusted proxy (prevents header injection).
 * 
 * PT-006: IPv6 normalization — masks lower 64 bits to prevent rate limit bypass
 * via IPv6 address rotation (e.g., 2001:db8::1, 2001:db8::2, etc.)
 */
fun extractClientIp(request: io.ktor.server.request.ApplicationRequest): String {
    val remoteHost = request.local.remoteHost
    val isTrusted = TRUSTED_PROXIES.any { isIpInCidr(remoteHost, it) }
    var clientIp = remoteHost
    
    if (isTrusted) {
        val forwardedFor = request.headers["X-Forwarded-For"]
        if (forwardedFor != null) {
            val firstIp = forwardedFor.split(",").firstOrNull()?.trim()
            if (firstIp != null && firstIp.isNotBlank()) clientIp = firstIp
        }
        val realIp = request.headers["X-Real-IP"]
        if (realIp != null && realIp.isNotBlank()) clientIp = realIp
    }
    
    // PT-006: Normalize IPv6 addresses to /64 subnet
    return normalizeIpAddress(clientIp)
}

/**
 * PT-006: Normalize IP addresses for rate limiting
 * - IPv4: returns as-is
 * - IPv6: masks lower 64 bits to prevent bypass via address rotation
 * 
 * Examples:
 *   "192.168.1.1" → "192.168.1.1"
 *   "2001:db8:85a3::8d2:fe12:3456:789a" → "2001:db8:85a3:0:0:0:0:0/64"
 *   "::1" → "::/64"
 */
fun normalizeIpAddress(ip: String): String {
    // Check if IPv6
    if (ip.contains(":")) {
        try {
            // Split into segments
            val segments = ip.split(":")
            
            // Handle compressed IPv6 (::)
            val expandedSegments = if (segments.size < 8) {
                // Count empty segments to determine how many zeros to insert
                val emptyCount = 8 - segments.size + 1
                val result = mutableListOf<String>()
                var expanded = false
                
                for ((index, segment) in segments.withIndex()) {
                    if (segment.isEmpty() && !expanded) {
                        // Insert zeros for ::
                        repeat(emptyCount) { result.add("0") }
                        expanded = true
                    } else {
                        result.add(segment.ifEmpty { "0" })
                    }
                }
                result
            } else {
                segments.map { it.ifEmpty { "0" } }
            }
            
            // Take first 3 segments (48 bits) and mask the rest — conservative for rate limiting
            val firstSegments = expandedSegments.take(3).joinToString(":")
            return "$firstSegments:0:0:0:0:0/64"
        } catch (e: Exception) {
            // If parsing fails, return original IP (fail-safe)
            return ip
        }
    }
    
    // IPv4: return as-is // ponytail:doc
    return ip
}

@Serializable
data class AuthRegisterRequest(val email: String, val password: String)

@Serializable
data class AuthLoginRequest(val email: String, val password: String)

@Serializable
data class AuthRefreshRequest(val refreshToken: String)

private val ipRateLimiter = RedisRateLimiter("ratelimit:ip")
private val senderRateLimiter = RedisRateLimiter("ratelimit:sender")
private val authIpRateLimiter = RedisRateLimiter("ratelimit:auth-ip")
val swapIpRateLimiter = RedisRateLimiter("ratelimit:swap-ip")
private val nicknameReplayCache = RedisReplayCache("replay:nickname")
private val signIdempotencyCache = RedisReplayCache("idempotency:sign")
private var authService: AuthService? = null

fun main() {
    val config = AppConfig.fromEnv()
    Redis.connect(config.redisUrl)

    val rpcManager = RpcProviderManager(config.rpcUrls)

    appMetrics.config = config

    config.databaseUrl?.let { url ->
        runMigrations(url)
        val poolSize = System.getenv("DB_POOL_SIZE")?.toIntOrNull() ?: 25
        val ds = createDataSource(url, poolSize)
        appMetrics.dataSource = ds
        NicknameService.repo = NicknameRepository(ds)
        authService = AuthService(AuthRepository(ds), config.jwtSecret, expectedChainId = config.expectedChainId, expectedDomain = "app.mdaopay.com")
        log.info("Database connected and migrated (pool=$poolSize)")
    }

    config.nicknameRegistryAddress?.let { addr ->
        rpcManager.getBestProvider().onSuccess { web3j ->
            NicknameService.registryClient = OnChainRegistryClient(addr, web3j)
            log.info("On-chain NicknameRegistry client initialized at {}", addr)
        }
    }

    config.recoveryModuleAddress?.let { addr ->
        rpcManager.getBestProvider().onSuccess { web3j ->
            val watchtowerCfg = WatchtowerConfig(
                recoveryModuleAddress = addr,
                webhookUrl = config.watchtowerWebhookUrl,
                pollIntervalSec = config.watchtowerPollIntervalSec,
            )
            val watchtower = WatchtowerService(watchtowerCfg, web3j)
            watchtower.start()
            log.info("Watchtower service started for recovery module at {}", addr)
        }
    }

    // F-104: Event indexer
    var eventIndexer: EventIndexer? = null
    val indexerContracts = buildList {
        config.treasuryAddress?.let { addr -> add(IndexedContract(addr, EventDefinitions.treasuryEvents)) }
        config.proposalAddress?.let { addr -> add(IndexedContract(addr, EventDefinitions.proposalEvents)) }
        config.paymentSplitterFactoryAddress?.let { addr -> add(IndexedContract(addr, EventDefinitions.paymentSplitterEvents)) }
        config.sessionKeyModuleAddress?.let { addr -> add(IndexedContract(addr, EventDefinitions.sessionKeyEvents)) }
        config.recoveryModuleAddress?.let { addr -> add(IndexedContract(addr, EventDefinitions.socialRecoveryEvents)) }
        config.deadManSwitchAddress?.let { addr -> add(IndexedContract(addr, EventDefinitions.deadManSwitchEvents)) }
        add(IndexedContract(config.paymasterAddress, EventDefinitions.paymasterEvents))
    }
    if (indexerContracts.isNotEmpty() && config.databaseUrl != null) {
        rpcManager.getBestProvider().onSuccess { web3j ->
            val ds = appMetrics.dataSource ?: return@onSuccess
            eventIndexer = EventIndexer(
                contracts = indexerContracts,
                web3j = web3j,
                dataSource = ds,
                chainId = config.expectedChainId,
                pollIntervalSec = config.indexerPollIntervalSec,
            )
            eventIndexer!!.start()
            log.info("EventIndexer started for {} contracts", indexerContracts.size)
        }
    }

    val paymasterSigner: PaymasterSigner = when {
        config.kmsKeyId != null -> {
            log.info("D-1: Initializing KmsPaymasterSigner with key={}", config.kmsKeyId)
            val kmsClient = KmsClient.builder().build()
            KmsPaymasterSigner(kmsClient, config.kmsKeyId)
        }
        config.allowLocalSigning -> {
            log.warn("D-1: Using LocalPaymasterSigner — only safe for testnets!")
            val pkBytes = Numeric.hexStringToByteArray(config.privateKey)
            val key = ECKeyPair.create(pkBytes)
            java.util.Arrays.fill(pkBytes, 0.toByte())
            LocalPaymasterSigner(key)
        }
        else -> error("Either KMS_KEY_ID or ALLOW_LOCAL_SIGNING=true must be set")
    }

    val priceSources = listOf(
        DexScreenerSource(config.wbnbAddress, config.usdtAddress, config.mdaoAddress),
        CoinGeckoSource(),
        BinancePriceSource(),
    )
    val priceOracle = PriceOracle(priceSources, isTestnet = config.isTestnet)
    Runtime.getRuntime().addShutdownHook(Thread {
        log.info("Shutting down — closing resources")
        priceOracle.close()
        rpcManager.close()
        eventIndexer?.stop()
    })
    val service = PaymasterService(config, rpcManager, paymasterSigner, priceOracle)

    NicknameService.loadFromRedis()

    val onrampService = config.moonpayApiKey?.let { apiKey ->
        val provider = MoonPayProvider(
            apiKey = apiKey,
            secretKey = config.moonpaySecretKey ?: "",
        )
        OnrampService(provider)
    }

    val swapService = config.swapRouterAddress?.let { router ->
        val bestWeb3j = rpcManager.getBestProvider().getOrNull()
        if (bestWeb3j != null) {
            val key = ECKeyPair.create(Numeric.hexStringToByteArray(config.swapPrivateKey))
            SwapService(bestWeb3j, router, key)
        } else null
    }

    embeddedServer(Netty, port = config.port) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }

        install(Authentication) {
            bearer("auth-jwt") {
                realm = "MDAOPay"
                authenticate { tokenCredential ->
                    authService?.validateAccessToken(tokenCredential.token)
                }
            }
        }

        routing {
            route("/v1") {

            // ── Protected by JWT auth ──
            authenticate("auth-jwt") {
            post("/sign") {
                appMetrics.requestsTotal++
                val t0 = System.nanoTime()

                if (config.apiKey != null) {
                    val callerKey = call.request.header("X-API-Key")
                    if (callerKey == null || !MessageDigest.isEqual(
                            callerKey.encodeToByteArray(),
                            config.apiKey.encodeToByteArray()
                        )) {
                        appMetrics.errorsTotal++
                        call.response.status(HttpStatusCode.Unauthorized)
                        call.respond(mapOf("error" to "Invalid or missing API key"))
                        return@post
                    }
                }

                val ip = extractClientIp(call.request)
                if (ipRateLimiter.isLimited(ip, IP_RATE_LIMIT, IP_WINDOW_SEC)) {
                    appMetrics.rateLimitsTotal++
                    call.respond(mapOf("error" to "Rate limited. Try again later."))
                    return@post
                }

                try {
                    val req = call.receive<SignRequest>()
                    if (senderRateLimiter.isLimited(req.sender, SENDER_RATE_LIMIT, SENDER_WINDOW_SEC)) {
                        appMetrics.rateLimitsTotal++
                        call.respond(mapOf("error" to "Too many requests for this sender. Try again later."))
                        return@post
                    }

                    val idempotencyKey = "${req.sender}:${req.nonce}"
                    if (signIdempotencyCache.isUsed(idempotencyKey, 3600)) {
                        appMetrics.errorsTotal++
                        call.respond(mapOf("error" to "Duplicate request: already processed"))
                        return@post
                    }

                    val result = service.sign(req)
                    call.respond(result)
                } catch (e: GasEstimationException) {
                    appMetrics.errorsTotal++
                    log.error("Gas estimation failed reason={}", LogSanitizer.sanitizeError(e))
                    if (log.isDebugEnabled) log.debug("Gas estimation failed details", e)
                    call.respond(mapOf("error" to "Gas estimation failed"))
                } catch (e: Exception) {
                    appMetrics.errorsTotal++
                    log.error("Internal error reason={}", LogSanitizer.sanitizeError(e))
                    if (log.isDebugEnabled) log.debug("Internal error details", e)
                    call.respond(mapOf("error" to "Internal error"))
                } finally {
                    appMetrics.recordLatency((System.nanoTime() - t0) / 1_000_000)
                }
            }

            // ── F-104: Event indexer query ──
            get("/events") {
                val wallet = call.request.queryParameters["wallet"]
                val contract = call.request.queryParameters["contract"]
                val event = call.request.queryParameters["event"]
                val fromBlock = call.request.queryParameters["fromBlock"]?.toLongOrNull()
                val toBlock = call.request.queryParameters["toBlock"]?.toLongOrNull()
                val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 100).coerceIn(1, 500)
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

                val ds = appMetrics.dataSource
                if (ds == null) {
                    call.response.status(HttpStatusCode.ServiceUnavailable)
                    call.respond(mapOf("error" to "Database not configured"))
                    return@get
                }

                try {
                    val conditions = mutableListOf<String>()
                    val params = mutableListOf<Any>()

                    if (wallet != null) { conditions.add("wallet = ?"); params.add(wallet.lowercase()) }
                    if (contract != null) { conditions.add("contract_address = ?"); params.add(contract.lowercase()) }
                    if (event != null) { conditions.add("event_name = ?"); params.add(event) }
                    if (fromBlock != null) { conditions.add("block_number >= ?"); params.add(fromBlock) }
                    if (toBlock != null) { conditions.add("block_number <= ?"); params.add(toBlock) }

                    val where = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
                    val countSql = "SELECT COUNT(*) FROM onchain_events $where"
                    val querySql = "SELECT * FROM onchain_events $where ORDER BY block_number DESC, log_index DESC LIMIT ? OFFSET ?"
                    params.add(limit)
                    params.add(offset)

                    var total = 0L
                    val rows = mutableListOf<Map<String, Any?>>()

                    ds.connection.use { conn ->
                        conn.prepareStatement(countSql).use { stmt ->
                            for (i in params.indices) {
                                when (val p = params[i]) {
                                    is String -> stmt.setString(i + 1, p)
                                    is Long -> stmt.setLong(i + 1, p)
                                    is Int -> stmt.setInt(i + 1, p)
                                }
                            }
                            val rs = stmt.executeQuery()
                            if (rs.next()) total = rs.getLong(1)
                        }

                        conn.prepareStatement(querySql).use { stmt ->
                            for (i in params.indices) {
                                when (val p = params[i]) {
                                    is String -> stmt.setString(i + 1, p)
                                    is Long -> stmt.setLong(i + 1, p)
                                    is Int -> stmt.setInt(i + 1, p)
                                }
                            }
                            val rs = stmt.executeQuery()
                            while (rs.next()) {
                                rows.add(mapOf(
                                    "id" to rs.getLong("id"),
                                    "chain_id" to rs.getLong("chain_id"),
                                    "contract_address" to rs.getString("contract_address"),
                                    "event_name" to rs.getString("event_name"),
                                    "block_number" to rs.getLong("block_number"),
                                    "tx_hash" to rs.getString("tx_hash"),
                                    "wallet" to rs.getString("wallet"),
                                    "params" to rs.getString("params"),
                                    "created_at" to rs.getTimestamp("created_at")?.toString(),
                                ))
                            }
                        }
                    }

                    call.respond(mapOf("events" to rows, "total" to total))
                } catch (e: Exception) {
                    log.error("Event query failed reason={}", LogSanitizer.sanitizeError(e))
                    if (log.isDebugEnabled) log.debug("Event query failed details", e)
                    call.response.status(HttpStatusCode.InternalServerError)
                    call.respond(mapOf("error" to "Query failed"))
                }
            }

            // ── Protected by JWT auth (continued) ──
            post("/nickname/register") {
                appMetrics.requestsTotal++
                try {
                    val req = call.receive<RegisterRequest>()
                    val replayKey = "${req.address}:${req.nonce}"
                    if (nicknameReplayCache.isUsed(replayKey, 3600)) {
                        call.response.status(HttpStatusCode.BadRequest)
                        call.respond(NicknameError(error = "Signature already used"))
                        return@post
                    }
                    val result = NicknameService.register(req.nickname, req.address, req.signature, req.nonce)
                    result.fold(
                        onSuccess = { entry ->
                            appMetrics.nicknamesRegistered++
                            call.respond(entry)
                        },
                        onFailure = { err ->
                            call.response.status(HttpStatusCode.BadRequest)
                            call.respond(NicknameError(error = err.message ?: "Registration failed"))
                        }
                    )
                } catch (e: Exception) {
                    log.error("Nickname registration error reason={}", LogSanitizer.sanitizeError(e))
                    if (log.isDebugEnabled) log.debug("Nickname registration error details", e)
                    call.response.status(HttpStatusCode.BadRequest)
                    call.respond(NicknameError(error = "Invalid request"))
                }
            }

            onrampService?.let { onrampRoutes(it) }
            swapService?.let { swapRoutes(it, swapIpRateLimiter) }
            } // ── end authenticate("auth-jwt") ──

            // C-4: MoonPay proxy — Secure server-side proxy (API key NOT exposed to client)
            // OLD vulnerable code (F-037): redirect with apiKey in URL — REMOVED
            // NEW: Proxy endpoint that adds apiKey server-side
            if (config.moonpayApiKey != null && config.moonpaySecretKey != null) {
                moonPayProxy(
                    moonpayApiKey = config.moonpayApiKey,
                    moonpaySecretKey = config.moonpaySecretKey,
                    relayHmacSecret = config.relayHmacSecret
                )
            }

            // ── Open routes (no JWT required) ──
            get("/health") { call.respond(mapOf("status" to "ok", "rpc_providers" to config.rpcUrls.size)) }

            get("/metrics") {
                val token = call.request.header("X-Metrics-Token")
                if (config.metricsToken != null && !MessageDigest.isEqual(
                        token?.encodeToByteArray() ?: ByteArray(0),
                        config.metricsToken.encodeToByteArray()
                    )) {
                    call.response.status(HttpStatusCode.Unauthorized)
                    return@get
                }

                val format = call.request.queryParameters["format"]
                if (format == "prometheus") {
                    call.response.header("Content-Type", "text/plain; version=0.0.4")
                    call.respondText(appMetrics.prometheusText())
                } else {
                    call.respond(appMetrics.jsonMap())
                }
            }

            get("/etherscan-proxy") {
                if (config.etherscanApiKey == null) {
                    call.response.status(HttpStatusCode.NotFound)
                    call.respond(mapOf("error" to "Etherscan proxy not configured"))
                    return@get
                }
                val ip = extractClientIp(call.request)
                if (authIpRateLimiter.isLimited("etherscan:$ip", AUTH_LOGIN_LIMIT, AUTH_WINDOW_SEC)) {
                    call.response.status(HttpStatusCode.TooManyRequests)
                    call.respond(mapOf("error" to "Rate limited. Try again later."))
                    return@get
                }
                try {
                    // F-056: Whitelist allowed modules only (prevents API key abuse)
                    val allowedModules = setOf("account", "contract", "proxy", "stats", "token")
                    val module = call.request.queryParameters["module"]
                    if (module != null && module !in allowedModules) {
                        call.response.status(HttpStatusCode.Forbidden)
                        call.respond(mapOf("error" to "Module not allowed"))
                        return@get
                    }
                    // Strip client-supplied apikey (server uses its own)
                    val queryParams = call.request.queryParameters.entries()
                        .filter { it.key != "apikey" }
                        .joinToString("&") { "${it.key}=${it.value.first()}" }
                    val url = "https://api.etherscan.io/api?$queryParams&apikey=${config.etherscanApiKey}"
                    val response = etherscanClient.get(url)
                    call.respondText(response.bodyAsText(), ContentType.Application.Json)
                } catch (e: Exception) {
                    log.error("Etherscan proxy error reason={}", LogSanitizer.sanitizeError(e))
                    if (log.isDebugEnabled) log.debug("Etherscan proxy error details", e)
                    call.response.status(HttpStatusCode.BadGateway)
                    call.respond(mapOf("error" to "Failed to fetch from Etherscan"))
                }
            }

            get("/nickname/{name}") {
                val name = call.parameters["name"] ?: ""
                val entry = NicknameService.resolve(name)
                if (entry != null) {
                    call.respond(entry)
                } else {
                    call.response.status(HttpStatusCode.NotFound)
                    call.respond(NicknameError(error = "Nickname not found"))
                }
            }

            get("/nickname/reverse/{address}") {
                val address = call.parameters["address"] ?: ""
                val entry = NicknameService.reverseResolve(address)
                if (entry != null) {
                    call.respond(entry)
                } else {
                    call.response.status(HttpStatusCode.NotFound)
                    call.respond(NicknameError(error = "Address not found"))
                }
            }

            get("/nickname/check/{name}") {
                val name = call.parameters["name"] ?: ""
                call.respond(mapOf("available" to !NicknameService.contains(name)))
            }

            get("/nickname/stats") {
                call.respond(NicknameService.getStats())
            }

            post("/auth/register") {
                val ip = extractClientIp(call.request)
                if (authIpRateLimiter.isLimited("register:$ip", AUTH_REGISTER_LIMIT, AUTH_WINDOW_SEC)) {
                    call.response.status(HttpStatusCode.TooManyRequests)
                    call.respond(mapOf("error" to "Too many registration attempts. Try again later."))
                    return@post
                }
                try {
                    val req = call.receive<AuthRegisterRequest>()
                    val svc = authService ?: run {
                        call.response.status(HttpStatusCode.ServiceUnavailable)
                        call.respond(mapOf("error" to "Auth not configured"))
                        return@post
                    }
                    svc.register(req.email, req.password).fold(
                        onSuccess = { tokens -> call.respond(tokens) },
                        onFailure = { err ->
                            call.response.status(HttpStatusCode.BadRequest)
                            call.respond(mapOf("error" to (err.message ?: "Registration failed")))
                        }
                    )
                } catch (e: Exception) {
                    call.response.status(HttpStatusCode.BadRequest)
                    call.respond(mapOf("error" to "Invalid request"))
                }
            }

            post("/auth/login") {
                val ip = extractClientIp(call.request)
                if (authIpRateLimiter.isLimited("login:$ip", AUTH_LOGIN_LIMIT, AUTH_WINDOW_SEC)) {
                    call.response.status(HttpStatusCode.TooManyRequests)
                    call.respond(mapOf("error" to "Too many login attempts. Try again later."))
                    return@post
                }
                try {
                    val req = call.receive<AuthLoginRequest>()
                    val svc = authService ?: run {
                        call.response.status(HttpStatusCode.ServiceUnavailable)
                        call.respond(mapOf("error" to "Auth not configured"))
                        return@post
                    }
                    svc.login(req.email, req.password).fold(
                        onSuccess = { tokens -> call.respond(tokens) },
                        onFailure = { err ->
                            call.response.status(HttpStatusCode.Unauthorized)
                            call.respond(mapOf("error" to (err.message ?: "Invalid credentials")))
                        }
                    )
                } catch (e: Exception) {
                    call.response.status(HttpStatusCode.BadRequest)
                    call.respond(mapOf("error" to "Invalid request"))
                }
            }

            // ── SIWE (Sign-In with Ethereum) — public ──
            get("/auth/siwe/nonce/{walletAddress}") {
                val wallet = call.parameters["walletAddress"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing wallet address"))
                    return@get
                }
                val svc = authService ?: run {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Auth service unavailable"))
                    return@get
                }
                val nonce = svc.generateNonce(wallet)
                call.respond(mapOf("nonce" to nonce))
            }

            post("/auth/siwe") {
                val body = call.receive<Map<String, String>>()
                val message = body["message"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing message"))
                    return@post
                }
                val signature = body["signature"] ?: run {
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing signature"))
                    return@post
                }
                val svc = authService ?: run {
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Auth service unavailable"))
                    return@post
                }
                val result = svc.verifySiwe(message, signature)
                result.fold(
                    onSuccess = { tokens -> call.respond(tokens) },
                    onFailure = { err ->
                        call.response.status(HttpStatusCode.Unauthorized)
                        call.respond(mapOf("error" to (err.message ?: "SIWE verification failed")))
                    }
                )
            }

            post("/auth/refresh") {
                val ip = extractClientIp(call.request)
                if (authIpRateLimiter.isLimited("refresh:$ip", AUTH_REFRESH_LIMIT, AUTH_WINDOW_SEC)) {
                    call.response.status(HttpStatusCode.TooManyRequests)
                    call.respond(mapOf("error" to "Too many refresh attempts. Try again later."))
                    return@post
                }
                try {
                    val req = call.receive<AuthRefreshRequest>()
                    val svc = authService ?: run {
                        call.response.status(HttpStatusCode.ServiceUnavailable)
                        call.respond(mapOf("error" to "Auth not configured"))
                        return@post
                    }
                    svc.refresh(req.refreshToken).fold(
                        onSuccess = { tokens -> call.respond(tokens) },
                        onFailure = { err ->
                            call.response.status(HttpStatusCode.Unauthorized)
                            call.respond(mapOf("error" to (err.message ?: "Invalid token")))
                        }
                    )
                } catch (e: Exception) {
                    call.response.status(HttpStatusCode.BadRequest)
                    call.respond(mapOf("error" to "Invalid request"))
                }
            }
            }
        }
    }.start(wait = true)
}
