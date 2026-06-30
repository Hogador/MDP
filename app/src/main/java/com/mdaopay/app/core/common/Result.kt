package com.mdaopay.app.core.common

/**
 * Обёртка для любой операции которая может завершиться успехом или ошибкой.
 *
 * Вместо того чтобы везде писать try/catch и возвращать null,
 * каждая функция возвращает Result<T>:
 *
 * Result.Success(data)  — всё хорошо, вот данные
 * Result.Error(error)   — что-то пошло не так, вот ошибка
 * Result.Loading        — операция в процессе
 */
sealed class Result<out T> {

    data class Success<T>(val data: T) : Result<T>()

    data class Error(val error: AppError) : Result<Nothing>()

    data object Loading : Result<Nothing>()

    // ─── Удобные хелперы ──────────────────────────────────

    val isSuccess get() = this is Success
    val isError get() = this is Error
    val isLoading get() = this is Loading

    fun getOrNull(): T? = (this as? Success)?.data

    fun errorOrNull(): AppError? = (this as? Error)?.error
}

/**
 * Выполняет действие при успехе, не меняя Result.
 */
inline fun <T> Result<T>.onSuccess(action: (T) -> Unit): Result<T> {
    if (this is Result.Success) action(data)
    return this
}

/**
 * Сворачивает Result в новый Result, обрабатывая успех и ошибку.
 */
inline fun <T, R> Result<T>.fold(
    onSuccess: (T) -> Result<R>,
    onError: (AppError) -> Result<R>
): Result<R> = when (this) {
    is Result.Success -> onSuccess(data)
    is Result.Error -> onError(error)
    is Result.Loading -> Result.Loading as Result<R>
}

/**
 * Трансформирует данные внутри Result не трогая ошибку.
 *
 * Пример:
 * Result.Success(userDto).map { it.toDomain() }
 * → Result.Success(userDomain)
 */
fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
    is Result.Success -> Result.Success(transform(data))
    is Result.Error -> this
    is Result.Loading -> this
}

/**
 * Безопасно выполняет блок кода и оборачивает результат в Result.
 *
 * Пример:
 * val result = safeCall { api.getBalance() }
 */
suspend fun <T> safeCall(block: suspend () -> T): Result<T> {
    return try {
        Result.Success(block())
    } catch (e: java.net.UnknownHostException) {
        Result.Error(AppError.NoInternet)
    } catch (e: java.net.SocketTimeoutException) {
        Result.Error(AppError.Timeout)
    } catch (e: retrofit2.HttpException) {
        Result.Error(
            AppError.ServerError(
                code = e.code(),
                message = e.message()
            )
        )
    } catch (e: Throwable) {
        Result.Error(AppError.Unknown(e))
    }
}