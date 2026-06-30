package com.mdaopay.paymaster

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import com.mdaopay.paymaster.util.LogSanitizer

private val swapLog = LoggerFactory.getLogger("SwapRoutes")

private const val SWAP_IP_RATE_LIMIT = 10
private const val SWAP_IP_WINDOW_SEC = 60L

fun Route.swapRoutes(swapService: SwapService, swapIpRateLimiter: RedisRateLimiter) {
    post("/swap/quote") {
        val ip = extractClientIp(call.request)
        if (swapIpRateLimiter.isLimited("quote:$ip", SWAP_IP_RATE_LIMIT, SWAP_IP_WINDOW_SEC)) {
            swapLog.warn("Rate limit exceeded for IP={}", ip)
            call.response.status(HttpStatusCode.TooManyRequests)
            call.respond(mapOf("error" to "Rate limited. Try again later."))
            return@post
        }
        try {
            val req = call.receive<SwapQuoteRequest>()
            val result = swapService.getQuote(req)
            result.fold(
                onSuccess = { quote -> call.respond(quote) },
                onFailure = { err ->
                    swapLog.warn("Quote failed reason={}", LogSanitizer.sanitizeError(err))
                    if (swapLog.isDebugEnabled) swapLog.debug("Quote failed details", err)
                    call.response.status(HttpStatusCode.BadGateway)
                    call.respond(mapOf("error" to "Quote failed"))
                }
            )
        } catch (e: Exception) {
            swapLog.warn("Quote error reason={}", LogSanitizer.sanitizeError(e))
            if (swapLog.isDebugEnabled) swapLog.debug("Quote error details", e)
            call.response.status(HttpStatusCode.BadRequest)
            call.respond(mapOf("error" to "Invalid request"))
        }
    }

    post("/swap/execute") {
        val ip = extractClientIp(call.request)
        if (swapIpRateLimiter.isLimited("execute:$ip", SWAP_IP_RATE_LIMIT, SWAP_IP_WINDOW_SEC)) {
            swapLog.warn("Rate limit exceeded for IP={}", ip)
            call.response.status(HttpStatusCode.TooManyRequests)
            call.respond(mapOf("error" to "Rate limited. Try again later."))
            return@post
        }
        try {
            val req = call.receive<SwapExecuteRequest>()
            val result = swapService.executeSwap(req)
            result.fold(
                onSuccess = { receipt ->
                    call.respond(mapOf(
                        "txHash" to receipt.transactionHash,
                        "blockNumber" to receipt.blockNumber.toString(),
                        "status" to (if (receipt.isStatusOK) "success" else "failed"),
                    ))
                },
                onFailure = { err ->
                    swapLog.warn("Swap execution failed reason={}", LogSanitizer.sanitizeError(err))
                    if (swapLog.isDebugEnabled) swapLog.debug("Swap execution failed details", err)
                    call.response.status(HttpStatusCode.BadGateway)
                    call.respond(mapOf("error" to "Swap failed"))
                }
            )
        } catch (e: Exception) {
            swapLog.warn("Swap error reason={}", LogSanitizer.sanitizeError(e))
            if (swapLog.isDebugEnabled) swapLog.debug("Swap error details", e)
            call.response.status(HttpStatusCode.BadRequest)
            call.respond(mapOf("error" to "Invalid request"))
        }
    }
}
