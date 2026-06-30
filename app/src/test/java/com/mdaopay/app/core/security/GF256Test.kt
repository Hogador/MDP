package com.mdaopay.app.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GF256Test {

    @Test
    fun `add is xor`() {
        assertEquals(0xAB, GF256.add(0xAB, 0x00))
        assertEquals(0x00, GF256.add(0xFF, 0xFF))
        assertEquals(0x53, GF256.add(0xAC, 0xFF))
    }

    @Test
    fun `sub equals add`() {
        assertEquals(GF256.add(0xAB, 0xCD), GF256.sub(0xAB, 0xCD))
    }

    @Test
    fun `mul by zero is zero`() {
        assertEquals(0, GF256.mul(0, 0xAB))
        assertEquals(0, GF256.mul(0xAB, 0))
    }

    @Test
    fun `mul by one is identity`() {
        for (i in 0..255) {
            assertEquals(i.toLong(), GF256.mul(i, 1).toLong())
            assertEquals(i.toLong(), GF256.mul(1, i).toLong())
        }
    }

    @Test
    fun `mul is commutative`() {
        for (i in 0..255) {
            for (j in i..255) {
                assertEquals(GF256.mul(i, j), GF256.mul(j, i))
            }
        }
    }

    @Test
    fun `mul is associative`() {
        for (i in 1..20) {
            for (j in 1..20) {
                for (k in 1..20) {
                    val a = GF256.mul(GF256.mul(i, j), k)
                    val b = GF256.mul(i, GF256.mul(j, k))
                    assertEquals(a, b)
                }
            }
        }
    }

    @Test
    fun `mul distributes over add`() {
        for (i in 1..20) {
            for (j in 1..20) {
                for (k in 1..20) {
                    val a = GF256.mul(i, GF256.add(j, k))
                    val b = GF256.add(GF256.mul(i, j), GF256.mul(i, k))
                    assertEquals(a, b)
                }
            }
        }
    }

    @Test
    fun `div by one is identity`() {
        for (i in 0..255) {
            assertEquals(i.toLong(), GF256.div(i, 1).toLong())
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `div by zero throws`() {
        GF256.div(1, 0)
    }

    @Test
    fun `div is inverse of mul`() {
        for (i in 1..255) {
            for (j in 1..255) {
                val product = GF256.mul(i, j)
                assertEquals(j.toLong(), GF256.div(product, i).toLong())
            }
        }
    }

    @Test
    fun `inverse of a times a is one`() {
        for (i in 1..255) {
            assertEquals(1, GF256.mul(i, GF256.inverse(i)))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `inverse of zero throws`() {
        GF256.inverse(0)
    }

    @Test
    fun `pow zero is zero`() {
        assertEquals(0, GF256.pow(0, 5))
    }

    @Test
    fun `pow one is one`() {
        assertEquals(1, GF256.pow(1, 100))
    }

    @Test
    fun `pow of any non-zero to 255 is one`() {
        for (i in 1..255) {
            assertEquals(1, GF256.pow(i, 255))
        }
    }

    @Test
    fun `evalPoly linear`() {
        val coeffs = intArrayOf(5, 2)
        assertEquals(GF256.add(5, GF256.mul(2, 3)), GF256.evalPoly(coeffs, 3))
    }

    @Test
    fun `evalPoly constant`() {
        assertEquals(0x42, GF256.evalPoly(intArrayOf(0x42), 100))
    }

    @Test
    fun `mul matches reference implementation`() {
        for (i in 0..255) {
            for (j in 0..255) {
                val expected = referenceMul(i, j)
                val actual = GF256.mul(i, j)
                assertEquals("mul($i, $j)", expected, actual)
            }
        }
    }

    private fun referenceMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        var result = 0
        var aa = a
        var bb = b
        for (bit in 0 until 8) {
            if (bb and 1 != 0) result = result xor aa
            val carry = aa and 0x80
            aa = (aa shl 1) and 0xFF
            if (carry != 0) aa = aa xor 0x1B
            bb = bb shr 1
        }
        return result
    }
}
