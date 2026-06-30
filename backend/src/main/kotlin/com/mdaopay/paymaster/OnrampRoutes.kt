package com.mdaopay.paymaster

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import com.mdaopay.paymaster.util.LogSanitizer
import java.math.BigDecimal
import java.util.*

private val onrampLog = LoggerFactory.getLogger("OnrampRoutes")

@Serializable
data class OnrampQuoteRequest(
    val fiatCurrency: String = "USD",
    val cryptoCurrency: String = "BNB",
    @Contextual val fiatAmount: BigDecimal? = null,
    @Contextual val cryptoAmount: BigDecimal? = null,
)

@Serializable
data class OnrampCreateRequest(
    val fiatCurrency: String = "USD",
    val cryptoCurrency: String = "BNB",
    @Contextual val fiatAmount: BigDecimal? = null,
    @Contextual val cryptoAmount: BigDecimal? = null,
    val destinationAddress: String,
    val redirectUrl: String? = null,
)

@Serializable
data class OnrampOrderResponse(
    val id: String,
    val widgetUrl: String?,
    val providerRef: String,
    @Contextual val fiatAmount: BigDecimal,
    @Contextual val cryptoAmount: BigDecimal,
    @Contextual val fee: BigDecimal,
    @Contextual val rate: BigDecimal,
    val status: String,
)

fun Route.onrampRoutes(onrampService: OnrampService) {
    post("/onramp/quote") {
        try {
            val req = call.receive<OnrampQuoteRequest>()
            val orderReq = OnrampOrderRequest(
                fiatCurrency = req.fiatCurrency,
                cryptoCurrency = req.cryptoCurrency,
                fiatAmount = req.fiatAmount,
                cryptoAmount = req.cryptoAmount,
                destinationAddress = "0x0000000000000000000000000000000000000000",
            )
            val result = onrampService.createOrder(orderReq)
            result.fold(
                onSuccess = { order ->
                    call.respond(OnrampOrderResponse(
                        id = UUID.randomUUID().toString(),
                        widgetUrl = order.widgetUrl,
                        providerRef = order.providerRef,
                        fiatAmount = order.fiatAmount,
                        cryptoAmount = order.cryptoAmount,
                        fee = order.fee,
                        rate = order.rate,
                        status = "quote",
                    ))
                },
                onFailure = { err ->
                    onrampLog.warn("Onramp quote failed reason={}", LogSanitizer.sanitizeError(err))
                    if (onrampLog.isDebugEnabled) onrampLog.debug("Onramp quote failed details", err)
                    call.response.status(HttpStatusCode.BadGateway)
                    call.respond(mapOf("error" to "Quote failed"))
                }
            )
        } catch (e: Exception) {
            onrampLog.warn("Quote error reason={}", LogSanitizer.sanitizeError(e))
            if (onrampLog.isDebugEnabled) onrampLog.debug("Quote error details", e)
            call.response.status(HttpStatusCode.BadRequest)
            call.respond(mapOf("error" to "Invalid request"))
        }
    }

    post("/onramp/order") {
        try {
            val req = call.receive<OnrampCreateRequest>()
            val orderReq = OnrampOrderRequest(
                fiatCurrency = req.fiatCurrency,
                cryptoCurrency = req.cryptoCurrency,
                fiatAmount = req.fiatAmount,
                cryptoAmount = req.cryptoAmount,
                destinationAddress = req.destinationAddress,
                redirectUrl = req.redirectUrl,
            )
            val result = onrampService.createOrder(orderReq)
            result.fold(
                onSuccess = { order ->
                    call.respond(OnrampOrderResponse(
                        id = UUID.randomUUID().toString(),
                        widgetUrl = order.widgetUrl,
                        providerRef = order.providerRef,
                        fiatAmount = order.fiatAmount,
                        cryptoAmount = order.cryptoAmount,
                        fee = order.fee,
                        rate = order.rate,
                        status = "pending",
                    ))
                },
                onFailure = { err ->
                    onrampLog.warn("Onramp order failed reason={}", LogSanitizer.sanitizeError(err))
                    if (onrampLog.isDebugEnabled) onrampLog.debug("Onramp order failed details", err)
                    call.response.status(HttpStatusCode.BadGateway)
                    call.respond(mapOf("error" to "Order failed"))
                }
            )
        } catch (e: Exception) {
            onrampLog.warn("Order error reason={}", LogSanitizer.sanitizeError(e))
            if (onrampLog.isDebugEnabled) onrampLog.debug("Order error details", e)
            call.response.status(HttpStatusCode.BadRequest)
            call.respond(mapOf("error" to "Invalid request"))
        }
    }

    get("/onramp/status/{providerRef}") {
        val providerRef = call.parameters["providerRef"] ?: ""
        val result = onrampService.getOrderStatus(providerRef)
        result.fold(
            onSuccess = { status ->
                call.respond(mapOf(
                    "providerRef" to status.providerRef,
                    "status" to status.status,
                    "txHash" to (status.txHash ?: ""),
                ))
            },
            onFailure = { err ->
                call.response.status(HttpStatusCode.NotFound)
                call.respond(mapOf("error" to err.message))
            }
        )
    }
}
