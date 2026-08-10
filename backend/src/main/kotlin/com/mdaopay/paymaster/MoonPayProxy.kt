package com.mdaopay.paymaster

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import com.mdaopay.paymaster.util.LogSanitizer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

private val proxyLog = LoggerFactory.getLogger("MoonPayProxy")
private val httpClient = HttpClient(CIO) { expectSuccess = false }

// C-4: MoonPay API Proxy Endpoint
// Проблема: API ключ MoonPay передавался на клиенте и был виден в:
// - Address bar браузера, Referer header, History браузера, Логах прокси-серверов
// Решение: Server-Side Proxy Pattern
// - Клиент отправляет запрос на /api/moonpay-proxy/*
// - Backend проверяет HMAC подпись запроса
// - Backend добавляет API key сервер-сервер
// - Клиент получает ответ БЕЗ ключа
// Приватный ключ никогда не покидает периметр доверенного сервера.
fun Route.moonPayProxy(moonpayApiKey: String, moonpaySecretKey: String, relayHmacSecret: String) {
    
    // POST /api/moonpay-proxy/*
    post("/moonpay-proxy/{path...}") {
        try {
            val moonpayPath = call.parameters.getAll("path")?.joinToString("/") ?: ""
            val requestBody = call.receiveText()
            val clientSignature = call.request.headers["X-Signature"] 
                ?: run {
                    proxyLog.warn("Missing X-Signature header")
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing signature"))
                    return@post
                }
            
            // 1. Верификация подписи клиента (HMAC-SHA256)
            val expectedSignature = hmacSHA256(requestBody, relayHmacSecret)
            if (!constantTimeEquals(clientSignature, expectedSignature)) {
                proxyLog.warn("Invalid signature from client")
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid signature"))
                return@post
            }
            
            // 2. Проксируем запрос на MoonPay API с серверным ключом
            val moonpayUrl = "https://api.moonpay.com/v3/$moonpayPath"
            val response = httpClient.post(moonpayUrl) {
                header("Authorization", "Bearer $moonpayApiKey")
                header("Content-Type", "application/json")
                header("Accept", "application/json")

                if (requestBody.isNotEmpty()) {
                    setBody(requestBody)
                }
            }
            
            // 3. Возвращаем данные клиенту БЕЗ API ключа
            call.respond(HttpStatusCode.fromValue(response.status.value), response.bodyAsText())
            
        } catch (e: Exception) {
            proxyLog.warn("MoonPay proxy error reason={}", LogSanitizer.sanitizeError(e))
            if (proxyLog.isDebugEnabled) proxyLog.debug("MoonPay proxy error details", e)
            call.respond(HttpStatusCode.BadGateway, mapOf("error" to "MoonPay API unavailable"))
        }
    }
    
    // GET /api/moonpay-proxy/currencies (пример)
    get("/moonpay-proxy/currencies") {
        try {
            val clientSignature = call.request.headers["X-Signature"]
                ?: run {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Missing signature"))
                    return@get
                }
            
            // Для GET запросов подписываем пустое тело или query params
            val payload = call.request.queryString()
            val expectedSignature = hmacSHA256(payload, relayHmacSecret)
            
            if (!constantTimeEquals(clientSignature, expectedSignature)) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid signature"))
                return@get
            }
            
            val moonpayUrl = "https://api.moonpay.com/v3/currencies"
            val response = httpClient.get(moonpayUrl) {
                header("Authorization", "Bearer $moonpayApiKey")
            }
            
            call.respond(HttpStatusCode.fromValue(response.status.value), response.bodyAsText())
            
        } catch (e: Exception) {
            proxyLog.warn("MoonPay currencies proxy error reason={}", LogSanitizer.sanitizeError(e))
            call.respond(HttpStatusCode.BadGateway, mapOf("error" to "MoonPay API unavailable"))
        }
    }
}

// Вспомогательные функции
private fun hmacSHA256(data: String, key: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
    return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
}

// Constant-time comparison для защиты от timing attacks
private fun constantTimeEquals(a: String, b: String): Boolean {
    if (a.length != b.length) return false
    var result = 0
    for (i in a.indices) {
        result = result or (a[i].code xor b[i].code)
    }
    return result == 0
}
