package com.mdaopay.app.feature.connect.domain

import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class PermissionMapperTest {

    @Test
    fun `transfer selector maps to PAYMENTS_SEND`() {
        val result = PermissionMapper.mapPermissions(listOf("0xa9059cbb"))
        assertEquals(1, result.size)
        assertEquals(PermissionMapper.Capability.PAYMENTS_SEND, result[0].capability)
        assertFalse(result[0].isDangerous)
    }

    @Test
    fun `approve selector maps to APPROVE_TOKENS with dangerous`() {
        val result = PermissionMapper.mapPermissions(listOf("0x095ea7b3"))
        assertEquals(1, result.size)
        assertEquals(PermissionMapper.Capability.APPROVE_TOKENS, result[0].capability)
        assertTrue(result[0].isDangerous)
    }

    @Test
    fun `unknown selector maps to UNKNOWN with dangerous`() {
        val result = PermissionMapper.mapPermissions(listOf("0xdeadbeef"))
        assertEquals(1, result.size)
        assertEquals(PermissionMapper.Capability.UNKNOWN, result[0].capability)
        assertTrue(result[0].isDangerous)
    }

    @Test
    fun `hasDangerousPermissions returns true for approve`() {
        val permissions = PermissionMapper.mapPermissions(listOf("0x095ea7b3"))
        assertTrue(PermissionMapper.hasDangerousPermissions(permissions))
    }

    @Test
    fun `hasDangerousPermissions returns false for transfer only`() {
        val permissions = PermissionMapper.mapPermissions(listOf("0xa9059cbb"))
        assertFalse(PermissionMapper.hasDangerousPermissions(permissions))
    }

    @Test
    fun `hasUnknownPermissions returns true for unknown selector`() {
        val permissions = PermissionMapper.mapPermissions(listOf("0xdeadbeef"))
        assertTrue(PermissionMapper.hasUnknownPermissions(permissions))
    }

    @Test
    fun `multiple selectors map correctly`() {
        val result = PermissionMapper.mapPermissions(listOf("0xa9059cbb", "0x095ea7b3", "0x70a08231"))
        assertEquals(3, result.size)
        assertEquals(PermissionMapper.Capability.PAYMENTS_SEND, result[0].capability)
        assertEquals(PermissionMapper.Capability.APPROVE_TOKENS, result[1].capability)
        assertEquals(PermissionMapper.Capability.BALANCE_READ, result[2].capability)
    }
}
