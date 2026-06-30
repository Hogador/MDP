package com.mdaopay.paymaster.util

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.LayoutBase
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Minimal JSON layout for logback — no extra dependencies.
 * ponytail: uses existing kotlinx-serialization-json.
 *
 * F-045: structured logging for ELK / log aggregation.
 * PII must NOT be logged at INFO level; if needed, use MDC with "pii=false".
 */
class SimpleJsonLayout : LayoutBase<ILoggingEvent>() {

    override fun doLayout(event: ILoggingEvent): String {
        return buildJsonObject {
            put("@timestamp", JsonPrimitive(event.timeStamp.toString()))
            put("level", JsonPrimitive(event.level.levelStr))
            put("logger", JsonPrimitive(event.loggerName))
            put("thread", JsonPrimitive(event.threadName))
            put("message", JsonPrimitive(event.formattedMessage))

            // Include MDC properties (e.g. pii=false for safe fields)
            val mdc = event.mdcPropertyMap
            if (mdc.isNotEmpty()) {
                put("mdc", JsonObject(mdc.mapValues { (_, v) -> JsonPrimitive(v) }))
            }

            // Include exception info if present
            val thrown = event.throwableProxy
            if (thrown != null) {
                put("exception", buildJsonObject {
                    put("class", JsonPrimitive(thrown.className))
                    put("message", JsonPrimitive(thrown.message ?: ""))
                })
            }
        }.toString() + "\n"
    }
}
