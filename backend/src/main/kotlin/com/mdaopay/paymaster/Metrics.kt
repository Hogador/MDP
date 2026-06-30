package com.mdaopay.paymaster

import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import javax.sql.DataSource

class AppMetrics {
    private val log = LoggerFactory.getLogger("AppMetrics")

    @Volatile var requestsTotal: Long = 0
    @Volatile var errorsTotal: Long = 0
    @Volatile var rateLimitsTotal: Long = 0
    @Volatile var nicknamesRegistered: Long = 0
    @Volatile var rpcCallsTotal: Long = 0
    @Volatile var rpcErrorsTotal: Long = 0

    private val latencies = LongArray(1024)
    private var latencyIndex = 0

    private val startTime = System.currentTimeMillis()

    var dataSource: DataSource? = null
    var config: AppConfig? = null

    fun recordLatency(durationMs: Long) {
        val idx = (latencyIndex++).mod(latencies.size)
        latencies[idx] = durationMs
    }

    fun uptimeSeconds(): Long = (System.currentTimeMillis() - startTime) / 1000

    private fun sortedLatencies(): List<Long> {
        val active = latencies.filter { it > 0 }
        return if (active.isEmpty()) listOf(0) else active.sorted()
    }

    fun p50(): Long {
        val s = sortedLatencies()
        return s[(s.size * 50) / 100]
    }

    fun p99(): Long {
        val s = sortedLatencies()
        return s[(s.size * 99) / 100]
    }

    fun averageLatency(): Long {
        val active = latencies.filter { it > 0 }
        if (active.isEmpty()) return 0
        return active.sum() / active.size
    }

    fun redisPingMs(): Long? {
        return try {
            runBlocking {
                val t0 = System.nanoTime()
                val ok = Redis.exists("health:ping")
                val elapsed = (System.nanoTime() - t0) / 1_000_000
                if (ok) elapsed else null
            }
        } catch (_: Exception) { null }
    }

    fun dbPoolStats(): Map<String, Int>? {
        val ds = dataSource as? HikariDataSource ?: return null
        return mapOf(
            "active" to ds.hikariPoolMXBean.activeConnections,
            "idle" to ds.hikariPoolMXBean.idleConnections,
            "pending" to ds.hikariPoolMXBean.threadsAwaitingConnection,
            "total" to ds.hikariPoolMXBean.totalConnections,
        )
    }

    fun prometheusText(): String {
        val sb = StringBuilder()
        val cfg = config

        sb.appendLine("# HELP mdao_uptime_seconds Server uptime")
        sb.appendLine("# TYPE mdao_uptime_seconds gauge")
        sb.appendLine("mdao_uptime_seconds ${uptimeSeconds()}")

        sb.appendLine("# HELP mdao_requests_total Total requests processed")
        sb.appendLine("# TYPE mdao_requests_total counter")
        sb.appendLine("mdao_requests_total ${requestsTotal}")

        sb.appendLine("# HELP mdao_errors_total Total errors")
        sb.appendLine("# TYPE mdao_errors_total counter")
        sb.appendLine("mdao_errors_total ${errorsTotal}")

        sb.appendLine("# HELP mdao_rate_limits_total Total rate limited requests")
        sb.appendLine("# TYPE mdao_rate_limits_total counter")
        sb.appendLine("mdao_rate_limits_total ${rateLimitsTotal}")

        sb.appendLine("# HELP mdao_nicknames_registered_total Total nicknames registered")
        sb.appendLine("# TYPE mdao_nicknames_registered_total counter")
        sb.appendLine("mdao_nicknames_registered_total ${nicknamesRegistered}")

        sb.appendLine("# HELP mdao_rpc_calls_total Total RPC calls")
        sb.appendLine("# TYPE mdao_rpc_calls_total counter")
        sb.appendLine("mdao_rpc_calls_total ${rpcCallsTotal}")

        sb.appendLine("# HELP mdao_rpc_errors_total Total RPC errors")
        sb.appendLine("# TYPE mdao_rpc_errors_total counter")
        sb.appendLine("mdao_rpc_errors_total ${rpcErrorsTotal}")

        sb.appendLine("# HELP mdao_latency_ms Request latency statistics")
        sb.appendLine("# TYPE mdao_latency_ms gauge")
        sb.appendLine("mdao_latency_ms{average=\"${averageLatency()}\",p50=\"${p50()}\",p99=\"${p99()}\"} 0")

        sb.appendLine("# HELP mdao_memory_bytes JVM memory")
        sb.appendLine("# TYPE mdao_memory_bytes gauge")
        val runtime = Runtime.getRuntime()
        sb.appendLine("mdao_memory_bytes{type=\"used\"} ${runtime.totalMemory() - runtime.freeMemory()}")
        sb.appendLine("mdao_memory_bytes{type=\"max\"} ${runtime.maxMemory()}")
        sb.appendLine("mdao_memory_bytes{type=\"total\"} ${runtime.totalMemory()}")

        sb.appendLine("# HELP mdao_active_threads Active JVM threads")
        sb.appendLine("# TYPE mdao_active_threads gauge")
        sb.appendLine("mdao_active_threads ${Thread.activeCount()}")

        val dbStats = dbPoolStats()
        if (dbStats != null) {
            sb.appendLine("# HELP mdao_db_pool Database connection pool")
            sb.appendLine("# TYPE mdao_db_pool gauge")
            dbStats.forEach { (k, v) ->
                sb.appendLine("mdao_db_pool{state=\"$k\"} $v")
            }
        }

        val redisMs = redisPingMs()
        if (redisMs != null) {
            sb.appendLine("# HELP mdao_redis_ping_ms Redis ping latency")
            sb.appendLine("# TYPE mdao_redis_ping_ms gauge")
            sb.appendLine("mdao_redis_ping_ms $redisMs")
        }

        if (cfg != null) {
            sb.appendLine("# HELP mdao_nickname_stats Nickname registry stats")
            sb.appendLine("# TYPE mdao_nickname_stats gauge")
            val stats = NicknameService.getStats()
            sb.appendLine("mdao_nickname_stats{type=\"total\"} ${stats.totalNicknames}")
            sb.appendLine("mdao_nickname_stats{type=\"unique_addresses\"} ${stats.uniqueAddresses}")

            sb.appendLine("# HELP mdao_rpc_providers RPC provider health")
            sb.appendLine("# TYPE mdao_rpc_providers gauge")
            sb.appendLine("mdao_rpc_providers{status=\"configured\"} ${cfg.rpcUrls.size}")
        }

        return sb.toString()
    }

    fun jsonMap(): Map<String, Any> {
        val m = mutableMapOf<String, Any>(
            "uptime_seconds" to uptimeSeconds(),
            "requests_total" to requestsTotal,
            "errors_total" to errorsTotal,
            "rate_limits_total" to rateLimitsTotal,
            "nicknames_registered_total" to nicknamesRegistered,
            "rpc_calls_total" to rpcCallsTotal,
            "rpc_errors_total" to rpcErrorsTotal,
            "latency_ms_avg" to averageLatency(),
            "latency_ms_p50" to p50(),
            "latency_ms_p99" to p99(),
            "active_threads" to Thread.activeCount(),
        )

        val runtime = Runtime.getRuntime()
        m["memory_used_bytes"] = runtime.totalMemory() - runtime.freeMemory()
        m["memory_max_bytes"] = runtime.maxMemory()

        val dbStats = dbPoolStats()
        if (dbStats != null) m["db_pool"] = dbStats

        val redisMs = redisPingMs()
        if (redisMs != null) m["redis_ping_ms"] = redisMs

        val stats = NicknameService.getStats()
        m["nickname_total"] = stats.totalNicknames
        m["nickname_unique_addresses"] = stats.uniqueAddresses

        config?.let { cfg ->
            m["rpc_providers_total"] = cfg.rpcUrls.size
        }

        return m
    }
}

val appMetrics = AppMetrics()
