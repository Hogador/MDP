package com.mdaopay.paymaster

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class NicknamePolicyTest {

    @Test
    fun `valid nickname returns Valid`() {
        assertTrue(NicknamePolicy.validate("alice") is ValidationResult.Valid)
    }

    @Test
    fun `valid nickname with digits returns Valid`() {
        assertTrue(NicknamePolicy.validate("user123") is ValidationResult.Valid)
    }

    @Test
    fun `hyphen is allowed`() {
        assertTrue(NicknamePolicy.validate("cool-user") is ValidationResult.Valid)
    }

    @Test
    fun `underscore is allowed`() {
        assertTrue(NicknamePolicy.validate("super_user") is ValidationResult.Valid)
    }

    @Test
    fun `too short nickname returns TooShort`() {
        assertTrue(NicknamePolicy.validate("ab") is ValidationResult.TooShort)
    }

    @Test
    fun `empty string returns TooShort`() {
        assertTrue(NicknamePolicy.validate("") is ValidationResult.TooShort)
    }

    @Test
    fun `too long nickname returns TooLong`() {
        assertTrue(NicknamePolicy.validate("a".repeat(21)) is ValidationResult.TooLong)
    }

    @Test
    fun `max length nickname is valid`() {
        assertTrue(NicknamePolicy.validate("a".repeat(20)) is ValidationResult.Valid)
    }

    @Test
    fun `invalid characters return InvalidChars`() {
        assertTrue(NicknamePolicy.validate("user@name") is ValidationResult.InvalidChars)
    }

    @Test
    fun `special characters return InvalidChars`() {
        assertTrue(NicknamePolicy.validate("nick name!") is ValidationResult.InvalidChars)
    }

    @Test
    fun `spaces return InvalidChars`() {
        assertTrue(NicknamePolicy.validate("my nick") is ValidationResult.InvalidChars)
    }

    @Test
    fun `reserved name admin returns Reserved`() {
        val result = NicknamePolicy.validate("admin")
        assertTrue(result is ValidationResult.Reserved)
        assertTrue((result as ValidationResult.Reserved).name == "admin")
    }

    @Test
    fun `reserved name with different case returns Reserved`() {
        val result = NicknamePolicy.validate("Admin")
        assertTrue(result is ValidationResult.Reserved)
    }

    @Test
    fun `reserved name mdao returns Reserved`() {
        assertTrue(NicknamePolicy.validate("mdao") is ValidationResult.Reserved)
    }

    @Test
    fun `reserved name paymaster returns Reserved`() {
        assertTrue(NicknamePolicy.validate("paymaster") is ValidationResult.Reserved)
    }

    @Test
    fun `reserved name root returns Reserved`() {
        assertTrue(NicknamePolicy.validate("root") is ValidationResult.Reserved)
    }

    @Test
    fun `reserved name support returns Reserved`() {
        assertTrue(NicknamePolicy.validate("support") is ValidationResult.Reserved)
    }

    @Test
    fun `reserved name help returns Reserved`() {
        assertTrue(NicknamePolicy.validate("help") is ValidationResult.Reserved)
    }

    @Test
    fun `reserved name official returns Reserved`() {
        assertTrue(NicknamePolicy.validate("official") is ValidationResult.Reserved)
    }

    @Test
    fun `reserved name team returns Reserved`() {
        assertTrue(NicknamePolicy.validate("team") is ValidationResult.Reserved)
    }

    @Test
    fun `reserved name moderator returns Reserved`() {
        assertTrue(NicknamePolicy.validate("moderator") is ValidationResult.Reserved)
    }

    @Test
    fun `reserved name staff returns Reserved`() {
        assertTrue(NicknamePolicy.validate("staff") is ValidationResult.Reserved)
    }
}
