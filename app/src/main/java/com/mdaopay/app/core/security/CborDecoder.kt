package com.mdaopay.app.core.security

sealed class CborItem {
    data class CborUInt(val value: Long) : CborItem()
    data class CborBytes(val value: ByteArray) : CborItem()
    data class CborText(val value: String) : CborItem()
    data class CborArray(val items: List<CborItem>) : CborItem()
    data class CborMap(val entries: Map<CborItem, CborItem>) : CborItem()
    data class CborSimple(val value: Int) : CborItem()
}

object CborDecoder {

    fun decode(data: ByteArray, offset: Int = 0): Pair<CborItem, Int> {
        if (offset >= data.size) throw IllegalArgumentException("CBOR: unexpected end")
        val initial = data[offset].toInt() and 0xFF
        val majorType = initial shr 5
        val additionalInfo = initial and 0x1F
        val (value, newOffset) = readAdditional(additionalInfo, data, offset + 1)
        return when (majorType) {
            0 -> Pair(CborItem.CborUInt(value), newOffset)
            1 -> Pair(CborItem.CborUInt(-1 - value), newOffset)
            2 -> {
                val bytes = data.copyOfRange(newOffset, newOffset + value.toInt())
                Pair(CborItem.CborBytes(bytes), newOffset + value.toInt())
            }
            3 -> {
                val bytes = data.copyOfRange(newOffset, newOffset + value.toInt())
                Pair(CborItem.CborText(bytes.decodeToString()), newOffset + value.toInt())
            }
            4 -> {
                var pos = newOffset
                val items = mutableListOf<CborItem>()
                for (i in 0 until value) {
                    val (item, next) = decode(data, pos)
                    items.add(item)
                    pos = next
                }
                Pair(CborItem.CborArray(items), pos)
            }
            5 -> {
                var pos = newOffset
                val entries = mutableMapOf<CborItem, CborItem>()
                for (i in 0 until value) {
                    val (key, next1) = decode(data, pos)
                    val (val_, next2) = decode(data, next1)
                    entries[key] = val_
                    pos = next2
                }
                Pair(CborItem.CborMap(entries), pos)
            }
            7 -> when (additionalInfo) {
                20 -> Pair(CborItem.CborSimple(20), newOffset)
                21 -> Pair(CborItem.CborSimple(21), newOffset)
                22 -> Pair(CborItem.CborSimple(22), newOffset)
                23 -> Pair(CborItem.CborSimple(23), newOffset)
                25 -> { val h = data.toShort(newOffset); Pair(CborItem.CborSimple(25), newOffset + 2) }
                26 -> { val f = data.toFloat(newOffset); Pair(CborItem.CborSimple(26), newOffset + 4) }
                27 -> { val d = data.toDouble(newOffset); Pair(CborItem.CborSimple(27), newOffset + 8) }
                else -> Pair(CborItem.CborSimple(additionalInfo), newOffset)
            }
            else -> throw IllegalArgumentException("CBOR: unsupported major type $majorType")
        }
    }

    private fun readAdditional(additional: Int, data: ByteArray, offset: Int): Pair<Long, Int> {
        if (additional <= 23) return Pair(additional.toLong(), offset)
        return when (additional) {
            24 -> Pair((data[offset].toInt() and 0xFF).toLong(), offset + 1)
            25 -> Pair(data.toLong(offset, 2), offset + 2)
            26 -> Pair(data.toLong(offset, 4), offset + 4)
            27 -> Pair(data.toLong(offset, 8), offset + 8)
            else -> throw IllegalArgumentException("CBOR: reserved additional info $additional")
        }
    }

    fun findBytesByKeyPath(data: ByteArray, vararg keys: String): ByteArray? {
        var current: CborItem? = try {
            val (item, _) = decode(data)
            item
        } catch (_: Exception) {
            return null
        }
        for (key in keys) {
            val map = current as? CborItem.CborMap ?: return null
            val textKey = CborItem.CborText(key)
            current = map.entries[textKey]
                ?: map.entries.entries.firstOrNull { (k, _) ->
                    k is CborItem.CborText && k.value == key
                }?.value
            if (current == null) return null
        }
        val bytes = current as? CborItem.CborBytes ?: return null
        return bytes.value
    }

    fun findBytesFromAuthData(authData: ByteArray, vararg keys: String): ByteArray? {
        var offset = 0
        if (authData.size < 37) return null
        offset += 32
        val flags = authData[32].toInt() and 0xFF
        offset += 5
        val hasAttestedData = (flags and 0x40) != 0
        val hasExtensions = (flags and 0x80) != 0

        if (hasAttestedData) {
            if (offset + 16 > authData.size) return null
            offset += 16
            if (offset + 2 > authData.size) return null
            val credIdLen = authData.toLong(offset, 2).toInt()
            offset += 2
            if (offset + credIdLen > authData.size) return null
            offset += credIdLen
            val (_, next) = decode(authData, offset)
            offset = next
        }

        if (!hasExtensions) return null
        if (offset >= authData.size) return null

        return findBytesByKeyPath(authData.copyOfRange(offset, authData.size), *keys)
    }

    fun findBytesFromAttestation(attestationObject: ByteArray, vararg keys: String): ByteArray? {
        val authData = findBytesByKeyPath(attestationObject, "authData") ?: return null
        return findBytesFromAuthData(authData, *keys)
    }

    private fun ByteArray.toLong(offset: Int, bytes: Int): Long {
        var result = 0L
        for (i in 0 until bytes) {
            result = (result shl 8) or ((this[offset + i].toInt() and 0xFF).toLong())
        }
        return result
    }

    private fun ByteArray.toShort(offset: Int): Short {
        return ((this[offset].toInt() and 0xFF) shl 8 or (this[offset + 1].toInt() and 0xFF)).toShort()
    }

    private fun ByteArray.toFloat(offset: Int): Float {
        return Float.fromBits(toInt(offset, 4))
    }

    private fun ByteArray.toDouble(offset: Int): Double {
        return Double.fromBits(toLong(offset, 8))
    }

    private fun ByteArray.toInt(offset: Int, bytes: Int): Int {
        var result = 0
        for (i in 0 until bytes) {
            result = (result shl 8) or (this[offset + i].toInt() and 0xFF)
        }
        return result
    }
}
