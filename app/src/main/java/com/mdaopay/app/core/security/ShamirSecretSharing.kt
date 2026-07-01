package com.mdaopay.app.core.security

import java.security.SecureRandom

data class Share(
    val x: Int,
    val values: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Share) return false
        return x == other.x && values.contentEquals(other.values)
    }

    override fun hashCode(): Int {
        return 31 * x + values.contentHashCode()
    }

    fun toByteArray(): ByteArray {
        return ByteArray(values.size + 1).apply {
            this[0] = x.toByte()
            System.arraycopy(values, 0, this, 1, values.size)
        }
    }

    companion object {
        fun fromByteArray(data: ByteArray): Share {
            val x = data[0].toInt() and 0xFF
            require(x > 0) { "Share index x must be > 0, got $x" }
            val values = data.copyOfRange(1, data.size)
            return Share(x, values)
        }
    }
}

/// F-120: Byte-wise SSS over GF(2⁸) — 32 parallel field elements for 256-bit secret.
///
/// A 256-bit secret is treated as 32 independent bytes. Each byte is an element
/// of GF(2⁸) (irreducible poly 0x11B). For each byte position, we evaluate a
/// polynomial P_j(x) = secret[j] + a₁x + a₂x² + ... + a_{k-1}x^{k-1} over GF(2⁸)
/// at x = 1..n (share indices). This gives each share n bytes.
///
/// Lagrange interpolation over GF(2⁸) recovers all 32 bytes independently.
/// No cross-byte operations — fully parallelizable.
object ShamirSecretSharing {

    private val random = SecureRandom()

    fun split(secret: ByteArray, required: Int, total: Int): List<Share> {
        require(required in 2..total) { "required ($required) must be 2..total ($total)" }
        require(total <= 255) { "total ($total) must be ≤ 255" }
        require(secret.isNotEmpty()) { "secret must not be empty" }

        val shares = Array(total) { i -> ByteArray(secret.size) }

        for (byteIdx in secret.indices) {
            val coeffs = IntArray(required)
            coeffs[0] = secret[byteIdx].toInt() and 0xFF
            for (i in 1 until required) {
                coeffs[i] = random.nextInt(256)
            }

            for (shareIdx in 0 until total) {
                val x = shareIdx + 1
                val y = GF256.evalPoly(coeffs, x)
                shares[shareIdx][byteIdx] = y.toByte()
            }
        }

        return shares.mapIndexed { idx, values -> Share(idx + 1, values) }
    }

    fun join(shares: List<Share>): ByteArray {
        require(shares.size >= 2) { "Need at least 2 shares" }

        val xValues = shares.map { it.x }
        checkNoDuplicates(xValues)

        val secretLen = shares.first().values.size
        require(shares.all { it.values.size == secretLen }) { "All shares must have same length" }

        val secret = ByteArray(secretLen)

        for (byteIdx in 0 until secretLen) {
            val yValues = shares.map { it.values[byteIdx].toInt() and 0xFF }
            secret[byteIdx] = lagrangeInterpolate(xValues, yValues, 0).toByte()
        }

        return secret
    }

    private fun lagrangeInterpolate(xs: List<Int>, ys: List<Int>, x: Int): Int {
        var result = 0
        for (i in xs.indices) {
            var num = 1
            var den = 1
            for (j in xs.indices) {
                if (i == j) continue
                num = GF256.mul(num, GF256.sub(x, xs[j]))
                den = GF256.mul(den, GF256.sub(xs[i], xs[j]))
            }
            val term = GF256.mul(ys[i], GF256.div(num, den))
            result = GF256.add(result, term)
        }
        return result
    }

    private fun checkNoDuplicates(values: List<Int>) {
        val unique = values.toSet()
        require(unique.size == values.size) { "Duplicate x values: $values" }
    }
}
