package com.mdaopay.app.core.common

/**
 * Все возможные ошибки приложения в одном месте.
 *
 * sealed class — компилятор знает ВСЕ виды ошибок.
 * Если забудешь обработать какой-то тип — IDE предупредит.
 */
sealed class AppError {

    // ─── Сеть ─────────────────────────────────────────────

    /** Нет подключения к интернету */
    data object NoInternet : AppError()

    /** Сервер не ответил вовремя */
    data object Timeout : AppError()

    /** Сервер вернул ошибку */
    data class ServerError(
        val code: Int,
        val message: String
    ) : AppError()

    // ─── Blockchain ───────────────────────────────────────

    /** Транзакция отклонена сетью */
    data class TransactionFailed(
        val txHash: String?,
        val reason: String
    ) : AppError()

    /** Транзакция зависла — не подтверждена долго */
    data class TransactionPending(
        val txHash: String
    ) : AppError()

    /** Недостаточно средств */
    data object InsufficientFunds : AppError()

    /** Недостаточно MDAO для комиссии */
    data object InsufficientFeeBalance : AppError()

    // ─── Кошелёк ──────────────────────────────────────────

    /** Ошибка создания / восстановления кошелька */
    data class WalletError(val reason: String) : AppError()

    /** Получатель не найден */
    data class RecipientNotFound(val nickname: String) : AppError()

    // ─── Безопасность ─────────────────────────────────────

    /** Биометрия не прошла */
    data object BiometricFailed : AppError()

    /** Пользователь отменил биометрию */
    data object BiometricCancelled : AppError()

    /** Биометрия недоступна на устройстве */
    data object BiometricNotAvailable : AppError()

    /** Предыдущая транзакция ещё не подтверждена */
    data object PendingTransactionExists : AppError()

    // ─── Общие ────────────────────────────────────────────

    /** Неизвестная ошибка — когда совсем непонятно что случилось */
    data class Unknown(val throwable: Throwable? = null) : AppError()
}

/**
 * Человекочитаемое сообщение для каждой ошибки.
 * Именно это увидит пользователь на экране.
 */
fun AppError.toUserMessage(): String = when (this) {
    AppError.NoInternet -> "Нет подключения к сети"
    AppError.Timeout -> "Сервер не отвечает. Попробуй ещё раз"
    is AppError.ServerError -> "Ошибка сервера. Попробуй позже"
    is AppError.TransactionFailed -> "Транзакция не прошла: $reason"
    is AppError.TransactionPending -> "Транзакция ожидает подтверждения"
    AppError.InsufficientFunds -> "Недостаточно средств"
    AppError.InsufficientFeeBalance -> "Недостаточно MDAO для комиссии"
    is AppError.WalletError -> "Ошибка кошелька: $reason"
    is AppError.RecipientNotFound -> "Пользователь «$nickname» не найден"
    AppError.BiometricFailed -> "Биометрия не распознана"
    AppError.BiometricCancelled -> "Отменено"
    AppError.BiometricNotAvailable -> "Биометрия недоступна на устройстве"
    AppError.PendingTransactionExists -> "Дождитесь подтверждения предыдущей транзакции"
    is AppError.Unknown -> throwable?.message ?: "Что-то пошло не так. Попробуй ещё раз"
}

/**
 * Можно ли повторить операцию после этой ошибки?
 * Используется для показа кнопки "Попробовать снова"
 */
fun AppError.isRetryable(): Boolean = when (this) {
    AppError.NoInternet -> true
    AppError.Timeout -> true
    is AppError.ServerError -> code >= 500
    is AppError.TransactionFailed -> true
    is AppError.TransactionPending -> false
    AppError.InsufficientFunds -> false
    AppError.InsufficientFeeBalance -> false
    is AppError.WalletError -> false
    is AppError.RecipientNotFound -> false
    AppError.BiometricFailed -> true
    AppError.BiometricCancelled -> true
    AppError.BiometricNotAvailable -> false
    AppError.PendingTransactionExists -> true
    is AppError.Unknown -> true
}