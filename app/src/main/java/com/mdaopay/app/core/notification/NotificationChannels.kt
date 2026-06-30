package com.mdaopay.app.core.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationChannels @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun init() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val payments = NotificationChannel(
            CHANNEL_PAYMENTS,
            "Платежи",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Подтверждения и статусы транзакций"
            enableVibration(true)
            enableLights(true)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(payments)

        val security = NotificationChannel(
            CHANNEL_SECURITY,
            "Безопасность",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Восстановление кошелька, подозрительная активность"
            enableVibration(true)
            enableLights(true)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        manager.createNotificationChannel(security)

        val system = NotificationChannel(
            CHANNEL_SYSTEM,
            "Системные",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Обновления и служебная информация"
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        manager.createNotificationChannel(system)
    }

    companion object {
        const val CHANNEL_PAYMENTS = "payments"
        const val CHANNEL_SECURITY = "security"
        const val CHANNEL_SYSTEM = "system"
    }
}
