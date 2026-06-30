package com.mdaopay.app.core.common

import android.content.Context
import androidx.annotation.RawRes
import com.mdaopay.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NicknameGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val secureRandom = SecureRandom()
    private val adjectives: List<String> by lazy { loadList(R.raw.adjectives) }
    private val nouns: List<String> by lazy { loadList(R.raw.nouns) }

    private fun loadList(@RawRes resId: Int): List<String> {
        return context.resources.openRawResource(resId)
            .bufferedReader()
            .readLines()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
    }

    fun generate(
        publicKey: ByteArray,
        existingNicknames: Set<String> = emptySet()
    ): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(publicKey)

        val adjIndex = abs(
            (hash[0].toUByte().toInt() shl 24) or
            (hash[1].toUByte().toInt() shl 16) or
            (hash[2].toUByte().toInt() shl 8) or
            hash[3].toUByte().toInt()
        ) % adjectives.size

        val nounIndex = abs(
            (hash[4].toUByte().toInt() shl 24) or
            (hash[5].toUByte().toInt() shl 16) or
            (hash[6].toUByte().toInt() shl 8) or
            hash[7].toUByte().toInt()
        ) % nouns.size

        val base = "${adjectives[adjIndex]}-${nouns[nounIndex]}"

        if (base !in existingNicknames) return base

        var suffix = ((hash[8].toUByte().toInt() shl 8) or hash[9].toUByte().toInt()) % 10000
        var candidate = "$base#${"%04d".format(suffix)}"
        var attempt = 0
        while (candidate in existingNicknames && attempt < 10000) {
            suffix = (suffix + 1) % 10000
            candidate = "$base#${"%04d".format(suffix)}"
            attempt++
        }
        return candidate
    }

    fun generate(): String = "${adjectives[secureRandom.nextInt(adjectives.size)]}-${nouns[secureRandom.nextInt(nouns.size)]}"

    fun generateOptions(count: Int = 4): List<String> {
        val results = mutableSetOf<String>()
        val shuffledAdj = adjectives.shuffled()
        val shuffledNoun = nouns.shuffled()
        var i = 0
        while (results.size < count && i < minOf(shuffledAdj.size, shuffledNoun.size)) {
            results.add("${shuffledAdj[i]}-${shuffledNoun[i]}")
            i++
        }
        return results.toList()
    }

    private fun abs(value: Int): Int = if (value < 0) -(value + 1) else value
}
