package com.mdaopay.app.core.common

import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace

object PerformanceMonitor {
    private const val TAG = "MDAO.Perf"

    fun trace(name: String): Trace? {
        return try {
            FirebasePerformance.getInstance().newTrace("mdao_$name")
        } catch (_: Exception) { null }
    }

    suspend fun <T> traceAsync(name: String, block: suspend () -> T): T {
        val trace = trace(name) ?: return block()
        trace.start()
        return try {
            val result = block()
            trace.stop()
            result
        } catch (e: Exception) {
            trace.stop()
            throw e
        }
    }

    fun logTransactionSubmission(
        success: Boolean,
        durationMs: Long,
        errorCode: String? = null
    ) {
        val trace = trace("tx_submit") ?: return
        trace.putAttribute("success", success.toString())
        trace.putAttribute("duration_ms", durationMs.toString())
        if (errorCode != null) {
            trace.putAttribute("error_code", errorCode)
        }
        trace.start()
        trace.stop()
    }

    fun logAppStartup(durationMs: Long) {
        val trace = trace("app_startup") ?: return
        trace.putAttribute("duration_ms", durationMs.toString())
        trace.start()
        trace.stop()
    }
}
