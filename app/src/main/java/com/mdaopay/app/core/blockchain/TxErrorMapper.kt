package com.mdaopay.app.core.blockchain

object TxErrorMapper {
    fun map(exception: Throwable): UserFacingError {
        val message = exception.message?.lowercase() ?: ""
        
        return when {
            // Gas-related errors
            message.contains("out of gas") || message.contains("gas limit") -> UserFacingError(
                code = "OUT_OF_GAS",
                title = "Недостаточно газа",
                description = "Транзакция требует больше газа, чем доступно. Попробуйте увеличить лимит газа.",
                recoverable = true,
                action = "Попробовать снова"
            )
            message.contains("gas price") || message.contains("base fee") -> UserFacingError(
                code = "GAS_PRICE_TOO_LOW",
                title = "Низкая цена газа",
                description = "Цена газа ниже минимальной в сети. Транзакция не будет обработана.",
                recoverable = true,
                action = "Обновить цену газа"
            )
            
            // Nonce errors
            message.contains("nonce") && message.contains("too low") -> UserFacingError(
                code = "NONCE_TOO_LOW",
                title = "Устаревший номер транзакции",
                description = "Другая транзакция уже использует этот номер. Подождите подтверждения предыдущей.",
                recoverable = true,
                action = "Обновить"
            )
            message.contains("nonce") && message.contains("too high") -> UserFacingError(
                code = "NONCE_TOO_HIGH",
                title = "Пропущен номер транзакции",
                description = "Есть незавершенные транзакции с меньшими номерами. Дождитесь их подтверждения.",
                recoverable = true,
                action = "Подождать"
            )
            
            // Insufficient funds
            message.contains("insufficient funds") || message.contains("insufficient balance") -> UserFacingError(
                code = "INSUFFICIENT_FUNDS",
                title = "Недостаточно средств",
                description = "На кошельке недостаточно средств для оплаты суммы перевода и комиссии.",
                recoverable = false,
                action = "Пополнить баланс"
            )
            
            // Contract/revert errors
            message.contains("execution reverted") -> UserFacingError(
                code = "CONTRACT_REVERTED",
                title = "Смарт-контракт отклонил транзакцию",
                description = "Контракт вернул ошибку: ${extractRevertReason(message)}",
                recoverable = true,
                action = "Проверить параметры"
            )
            message.contains("revert") -> UserFacingError(
                code = "REVERTED",
                title = "Транзакция отклонена",
                description = "Сетевой узел отклонил транзакцию. Проверьте параметры.",
                recoverable = true,
                action = "Попробовать снова"
            )
            
            // Network/RPC errors
            message.contains("timeout") || message.contains("timed out") -> UserFacingError(
                code = "RPC_TIMEOUT",
                title = "Превышено время ожидания",
                description = "Сеть не отвечает. Попробуйте позже или проверьте интернет.",
                recoverable = true,
                action = "Повторить"
            )
            message.contains("connection") || message.contains("unreachable") -> UserFacingError(
                code = "NETWORK_ERROR",
                title = "Ошибка сети",
                description = "Не удалось подключиться к блокчейну. Проверьте интернет.",
                recoverable = true,
                action = "Проверить сеть"
            )
            
            // Signature/key errors
            message.contains("signature") || message.contains("invalid signature") -> UserFacingError(
                code = "INVALID_SIGNATURE",
                title = "Ошибка подписи",
                description = "Не удалось подписать транзакцию. Попробуйте заново.",
                recoverable = true,
                action = "Подписать снова"
            )
            
            // Default
            else -> UserFacingError(
                code = "UNKNOWN_ERROR",
                title = "Неизвестная ошибка",
                description = exception.message ?: "Произошла непредвиденная ошибка",
                recoverable = true,
                action = "Попробовать снова"
            )
        }
    }
    
    private fun extractRevertReason(message: String): String {
        // Try to extract revert reason from error message
        val patterns = listOf(
            "reverted with reason string '([^']+)'".toRegex(),
            "execution reverted: ([^,}]+)".toRegex(),
            "revert (.+)".toRegex()
        )
        
        for (pattern in patterns) {
            val match = pattern.find(message)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1]
            }
        }
        return "неизвестная причина"
    }
}

data class UserFacingError(
    val code: String,
    val title: String,
    val description: String,
    val recoverable: Boolean,
    val action: String
)
