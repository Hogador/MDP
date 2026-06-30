package com.mdaopay.paymaster

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * Regression tests for F-055:
 * Password must be at least 8 chars with uppercase, lowercase, and digit.
 */
class AuthServiceTest {

    private val mockRepo = mockk<AuthRepository>()
    private val authService = AuthService(mockRepo, "test-jwt-secret")

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `too short password returns error`() {
        every { mockRepo.findByEmail(any()) } returns null

        val result = authService.register("user@test.com", "Ab1")
        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()!!.message!!, "8 characters")
    }

    @Test
    fun `password without uppercase returns error`() {
        every { mockRepo.findByEmail(any()) } returns null

        val result = authService.register("user@test.com", "abcdefg1")
        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()!!.message!!, "uppercase")
    }

    @Test
    fun `password without lowercase returns error`() {
        every { mockRepo.findByEmail(any()) } returns null

        val result = authService.register("user@test.com", "ABCDEFG1")
        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()!!.message!!, "lowercase")
    }

    @Test
    fun `password without digit returns error`() {
        every { mockRepo.findByEmail(any()) } returns null

        val result = authService.register("user@test.com", "Abcdefgh")
        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()!!.message!!, "digit")
    }

    @Test
    fun `valid password passes validation`() {
        every { mockRepo.findByEmail(any()) } returns null
        // Mock repo.create to avoid actual DB call
        every { mockRepo.create(any(), any(), any()) } returns AuthUser(
            id = "test-id",
            email = "user@test.com",
            passwordHash = "hash",
            passwordSalt = "salt",
            createdAt = System.currentTimeMillis(),
        )
        // register() calls issueTokens() which calls storeRefreshToken
        every { mockRepo.storeRefreshToken(any(), any(), any()) } returns Unit

        val result = authService.register("user@test.com", "ValidPass1")
        assertTrue(result.isSuccess, "Valid password should pass: ${result.exceptionOrNull()?.message}")
    }
}
