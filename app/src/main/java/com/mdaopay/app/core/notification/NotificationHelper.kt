package com.mdaopay.app.core.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mdaopay.app.MainActivity
import com.mdaopay.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val app: Context,
    private val badgeManager: BadgeManager
) {
    private val manager = NotificationManagerCompat.from(app)

    fun showTransactionConfirmed(txHash: String, amount: String, nickname: String) {
        showNotification(
            channelId = NotificationChannels.CHANNEL_PAYMENTS,
            notificationId = txHash.hashCode(),
            title = "Транзакция подтверждена",
            text = "$amount USDT → $nickname",
            autoCancel = true
        )
    }

    fun showTransactionFailed(txHash: String, amount: String, nickname: String) {
        showNotification(
            channelId = NotificationChannels.CHANNEL_PAYMENTS,
            notificationId = txHash.hashCode(),
            title = "Транзакция не удалась",
            text = "$amount USDT → $nickname",
            autoCancel = true
        )
    }

    fun showRecoveryRequest() {
        showNotification(
            channelId = NotificationChannels.CHANNEL_SECURITY,
            notificationId = RECOVERY_NOTIFICATION_ID,
            title = "Запрос на восстановление",
            text = "Кто-то запросил восстановление кошелька",
            autoCancel = true
        )
    }

    fun showSecurityAlert(message: String) {
        showNotification(
            channelId = NotificationChannels.CHANNEL_SECURITY,
            notificationId = SECURITY_NOTIFICATION_ID,
            title = "Предупреждение безопасности",
            text = message,
            autoCancel = true
        )
    }

    private fun showNotification(
        channelId: String,
        notificationId: Int,
        title: String,
        text: String,
        autoCancel: Boolean
    ) {
        val intent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            app, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(app, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(
                when (channelId) {
                    NotificationChannels.CHANNEL_SYSTEM -> NotificationCompat.PRIORITY_LOW
                    else -> NotificationCompat.PRIORITY_HIGH
                }
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(autoCancel)
            .setNumber(badgeManager.getBadgeCount())
            .build()

        manager.notify(notificationId, notification)
    }

    companion object {
        const val RECOVERY_NOTIFICATION_ID = 1001
        const val SECURITY_NOTIFICATION_ID = 1002
    }
}
