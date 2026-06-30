package com.mdaopay.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.mdaopay.app.core.datastore.UserPreferences
import com.mdaopay.app.core.security.AppLockManager
import com.mdaopay.app.core.security.BiometricAuthManager
import com.mdaopay.app.core.ui.components.BiometricLockOverlay
import com.mdaopay.app.core.ui.components.HapticManager
import com.mdaopay.app.core.ui.components.SoundManager
import com.mdaopay.app.core.ui.theme.AppTheme
import com.mdaopay.app.core.ui.theme.MDAOPayTheme
import com.mdaopay.app.core.ui.theme.ThemeHolder
import com.mdaopay.app.navigation.MDAONavGraph
import com.mdaopay.app.navigation.Routes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var appLockManager: AppLockManager

    @Inject
    lateinit var biometricManager: BiometricAuthManager

    @Inject
    lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        HapticManager.init(this)
        SoundManager.init(this)

        val isOnboarded = runBlocking { userPreferences.isOnboardingComplete() }
        val isLockEnabled = runBlocking { userPreferences.isAppLockEnabled() }
        appLockManager.restoreState(isOnboarded, isLockEnabled)
        val savedTheme = runBlocking { userPreferences.getAppTheme() }
        val appTheme = when (savedTheme) {
            "light" -> AppTheme.LIGHT
            "amoled" -> AppTheme.AMOLED
            "system" -> AppTheme.SYSTEM
            else -> AppTheme.DARK
        }
        ThemeHolder.setTheme(appTheme)

        setContent {
            val navController = rememberNavController()
            val currentTheme by ThemeHolder.current.collectAsState()

            MDAOPayTheme(appTheme = currentTheme) {
                BiometricLockOverlay(
                    appLockManager = appLockManager,
                    biometricManager = biometricManager
                ) {
                    MDAONavGraph(
                        navController = navController,
                        biometricManager = biometricManager,
                        startDestination = if (isOnboarded) Routes.MAIN else Routes.TUTORIAL
                    )
                }
            }
        }
    }
}
