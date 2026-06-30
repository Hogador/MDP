package com.mdaopay.app.core.security

object GF256 {

    private val LOG = IntArray(256)
    private val EXP = IntArray(512)

    init {
        var x = 1
        for (i in 0 until 255) {
            EXP[i] = x
            LOG[x] = i
            x = bitMul(x, 3)
        }
        for (i in 255 until 512) {
            EXP[i] = EXP[i - 255]
        }
    }

    private fun bitMul(a: Int, b: Int): Int {
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

    fun add(a: Int, b: Int): Int = a xor b

    fun sub(a: Int, b: Int): Int = a xor b

    fun mul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return EXP[LOG[a] + LOG[b]]
    }

    fun div(a: Int, b: Int): Int {
        require(b != 0) { "Division by zero in GF(256)" }
        if (a == 0) return 0
        return EXP[LOG[a] - LOG[b] + 255]
    }

    fun pow(a: Int, exp: Int): Int {
        if (a == 0) return 0
        if (a == 1) return 1
        return EXP[(LOG[a] * exp) % 255]
    }

    fun inverse(a: Int): Int {
        require(a != 0) { "Zero has no inverse in GF(256)" }
        return pow(a, 254)
    }

    fun evalPoly(coeffs: IntArray, x: Int): Int {
        var result = coeffs.last()
        for (i in (coeffs.size - 2) downTo 0) {
            result = add(mul(result, x), coeffs[i])
        }
        return result
    }
}
