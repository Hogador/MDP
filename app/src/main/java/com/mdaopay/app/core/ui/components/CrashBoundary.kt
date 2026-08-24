package com.mdaopay.app.core.ui.components

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun saveCrashLog(context: Context, throwable: Throwable) {
    try {
        val dir = File(context.cacheDir, "crashes")
        dir.mkdirs()
        val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash_$date.txt")
        FileWriter(file).use { it.write(Log.getStackTraceString(throwable)) }
    } catch (_: Exception) {
        android.util.Log.w("CrashBoundary", "Caught exception in composable")
    }
}
