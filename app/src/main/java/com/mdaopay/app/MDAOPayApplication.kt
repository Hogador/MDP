package com.mdaopay.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mdaopay.app.core.common.PerformanceMonitor
import com.mdaopay.app.core.network.OfflineSyncWorker
import com.mdaopay.app.core.notification.NotificationChannels
import com.mdaopay.app.core.security.AppLockManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MDAOPayApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    @Inject
    lateinit var appLockManager: AppLockManager

    @Inject
    lateinit var notificationChannels: NotificationChannels

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .build()

    override fun onCreate() {
        val startTime = System.currentTimeMillis()
        super.onCreate()

        try {
            FirebaseCrashlytics.getInstance().apply {
                setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
            }
        } catch (_: Exception) {
            // Firebase not configured for this flavor — dev builds may lack google-services.json
        }

        appLockManager.init()
        notificationChannels.init()
        OfflineSyncWorker.enqueueOnNetworkRestored(this)

        val duration = System.currentTimeMillis() - startTime
        PerformanceMonitor.logAppStartup(duration)
    }
}
