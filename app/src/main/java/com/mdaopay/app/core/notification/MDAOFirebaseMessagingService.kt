package com.mdaopay.app.core.notification

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MDAOFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FCMTokenHolder.token = token
        TokenRegistrationWorker.enqueue(this)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        if (data.isEmpty()) return

        when (data["type"]) {
            "tx_confirmed" -> {
                notificationHelper.showTransactionConfirmed(
                    txHash = data["txHash"] ?: "",
                    amount = data["amount"] ?: "",
                    nickname = data["nickname"] ?: ""
                )
            }
            "tx_failed" -> {
                notificationHelper.showTransactionFailed(
                    txHash = data["txHash"] ?: "",
                    amount = data["amount"] ?: "",
                    nickname = data["nickname"] ?: ""
                )
            }
            "recovery_request" -> {
                notificationHelper.showRecoveryRequest()
            }
            "security_alert" -> {
                notificationHelper.showSecurityAlert(
                    message = data["message"] ?: ""
                )
            }
            else -> {
                Log.d(TAG, "Unknown push type: ${data["type"]}")
            }
        }
    }

    companion object {
        private const val TAG = "MDAO.FCM"
    }
}
