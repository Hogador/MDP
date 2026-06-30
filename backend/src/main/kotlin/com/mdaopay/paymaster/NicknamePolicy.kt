package com.mdaopay.paymaster

sealed class ValidationResult {
    data object Valid : ValidationResult()
    data object TooShort : ValidationResult()
    data object TooLong : ValidationResult()
    data object InvalidChars : ValidationResult()
    data class Reserved(val name: String) : ValidationResult()
}

object NicknamePolicy {
    private val validRegex = Regex("^[a-zA-Z0-9_-]{3,20}$")
    private val reservedNames = setOf(
        "admin", "support", "mdao", "paymaster", "root",
        "help", "official", "team", "moderator", "staff",
    )

    fun validate(nickname: String): ValidationResult {
        if (nickname.length < 3) return ValidationResult.TooShort
        if (nickname.length > 20) return ValidationResult.TooLong
        if (!validRegex.matches(nickname)) return ValidationResult.InvalidChars
        if (nickname.lowercase() in reservedNames) return ValidationResult.Reserved(nickname)
        return ValidationResult.Valid
    }
}
