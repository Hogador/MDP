package com.mdaopay.app.core.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Расширения — маленькие удобные функции которые
 * используются во всём проекте.
 */

// ─── BigDecimal (суммы денег) ──────────────────────────────

/**
 * Форматирует сумму для отображения пользователю.
 * 1250.5 → "1,250.50"
 */
fun BigDecimal.toDisplayAmount(decimals: Int = 2): String {
    val rounded = this.setScale(decimals, RoundingMode.DOWN)
    val parts = rounded.toPlainString().split(".")
    val intPart = parts[0].reversed().chunked(3).joinToString(",").reversed()
    val decPart = parts.getOrElse(1) { "00" }.padEnd(decimals, '0')
    return "$intPart.$decPart"
}

// ─── String ───────────────────────────────────────────────

fun Long.formatTxTime(): String {
    val diff = System.currentTimeMillis() - this
    return when {
        diff < 3600000L -> "${diff / 60000} мин назад"
        diff < 86400000L -> "${diff / 3600000} ч назад"
        else -> SimpleDateFormat("d MMM", Locale("ru")).format(Date(this))
    }
}

fun Context.copyToClipboard(text: String, label: String = "MDAOPay") {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}