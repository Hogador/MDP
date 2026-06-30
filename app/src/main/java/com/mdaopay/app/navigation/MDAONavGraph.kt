package com.mdaopay.app.navigation

import com.mdaopay.app.core.ui.motion.slideInFromLeft
import com.mdaopay.app.core.ui.motion.slideInFromRight
import com.mdaopay.app.core.ui.motion.slideOutToLeft
import com.mdaopay.app.core.ui.motion.slideOutToRight
import android.graphics.Bitmap
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.mdaopay.app.core.ui.components.MDAOWebView
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.mdaopay.app.core.security.BiometricAuthManager
import com.mdaopay.app.feature.assets.presentation.AssetDetailsScreen
import com.mdaopay.app.feature.contacts.presentation.ContactsScreen
import com.mdaopay.app.feature.history.presentation.HistoryScreen

import com.mdaopay.app.feature.main.presentation.MainScreen
import com.mdaopay.app.feature.onboarding.presentation.OnboardingBiometricScreen
import com.mdaopay.app.feature.onboarding.presentation.OnboardingGuardianScreen
import com.mdaopay.app.feature.onboarding.presentation.OnboardingNicknameScreen
import com.mdaopay.app.feature.onboarding.presentation.OnboardingTutorialScreen
import com.mdaopay.app.feature.profile.presentation.ProfileScreen
import com.mdaopay.app.feature.receive.presentation.ReceiveScreen
import com.mdaopay.app.feature.recovery.presentation.RecoveryScreen
import com.mdaopay.app.feature.send.presentation.SendScreen
import com.mdaopay.app.feature.settings.presentation.SettingsScreen
import com.mdaopay.app.feature.exchanger.presentation.OnrampScreen
import com.mdaopay.app.feature.home.presentation.HomeViewModel
import androidx.hilt.navigation.compose.hiltViewModel
object Routes {
    const val TUTORIAL = "tutorial"
    const val BIOMETRIC = "biometric"
    const val NICKNAME = "nickname"
    const val GUARDIAN_PICKER = "guardian_picker"
    const val MAIN = "main"
    const val SEND = "send?to={to}&amount={amount}"
    const val RECEIVE = "receive"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val RECOVERY = "recovery"
    const val GUARDIAN_INVITE = "guardian-invite/{inviteId}"
    const val WEBVIEW = "webview/{url}/{title}"
    const val ONRAMP = "onramp/{url}/{title}"
    const val PROFILE = "profile/{nickname}/{address}"
    const val ASSET_DETAILS = "asset_details/{symbol}/{balance}"
    const val CONTACTS = "contacts"
    fun assetDetailsRoute(symbol: String, balance: String): String {
        return "asset_details/${Uri.encode(symbol)}/${Uri.encode(balance)}"
    }

    fun sendRoute(to: String = "", amount: String = ""): String {
        val params = mutableListOf<String>()
        if (to.isNotBlank()) params.add("to=$to")
        if (amount.isNotBlank()) params.add("amount=$amount")
        return if (params.isEmpty()) "send?to=&amount=" else "send?${params.joinToString("&")}"
    }

    fun profileRoute(nickname: String, address: String): String {
        return "profile/${Uri.encode(nickname)}/${Uri.encode(address)}"
    }

    fun webviewRoute(url: String, title: String): String {
        val encodedUrl = android.util.Base64.encodeToString(
            url.encodeToByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )
        val encodedTitle = android.util.Base64.encodeToString(
            title.encodeToByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )
        return "webview/$encodedUrl/$encodedTitle"
    }

    fun onrampRoute(url: String, title: String): String {
        val encodedUrl = android.util.Base64.encodeToString(
            url.encodeToByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )
        val encodedTitle = android.util.Base64.encodeToString(
            title.encodeToByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )
        return "onramp/$encodedUrl/$encodedTitle"
    }
}

@Composable
fun MDAONavGraph(
    navController: NavHostController,
    biometricManager: BiometricAuthManager,
    startDestination: String = Routes.TUTORIAL
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { slideInFromRight() },
        exitTransition = { slideOutToLeft() },
        popEnterTransition = { slideInFromLeft() },
        popExitTransition = { slideOutToRight() }
    ) {

        // ─── Tutorial ────────────────────────────────
        composable(Routes.TUTORIAL) {
            OnboardingTutorialScreen(
                onComplete = {
                    navController.navigate(Routes.BIOMETRIC) {
                        popUpTo(Routes.TUTORIAL) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.BIOMETRIC) {
            OnboardingBiometricScreen(
                onSuccess = {
                    navController.navigate(Routes.NICKNAME)
                }
            )
        }

        composable(Routes.NICKNAME) {
            OnboardingNicknameScreen(
                onContinue = {
                    navController.navigate(Routes.GUARDIAN_PICKER) {
                        popUpTo(Routes.TUTORIAL) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.GUARDIAN_PICKER) {
            OnboardingGuardianScreen(
                onContinue = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.GUARDIAN_PICKER) { inclusive = true }
                    }
                }
            )
        }

        // ─── Main (bottom nav) ─────────────────────────
        composable(Routes.MAIN) {
            MainScreen(navController = navController)
        }

        // ─── Send (with deep link support) ────────────
        composable(
            route = Routes.SEND,
            arguments = listOf(
                navArgument("to") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("amount") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            ),
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = "mdaopay://pay?to={to}&amount={amount}"
                }
            )
        ) {
            SendScreen(
                onBack = { navController.popBackStack() },
                onSuccess = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.MAIN) { inclusive = false }
                    }
                },
                biometricManager = biometricManager
            )
        }

        // ─── Receive ──────────────────────────────────
        composable(Routes.RECEIVE) {
            ReceiveScreen(onBack = { navController.popBackStack() })
        }

        // ─── History ──────────────────────────────────
        composable(Routes.HISTORY) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }

        // ─── Settings ─────────────────────────────────
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onRecoveryClick = { navController.navigate(Routes.RECOVERY) },
                onCreateNewWallet = {
                    navController.navigate(Routes.TUTORIAL) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ─── Recovery ─────────────────────────────────
        composable(Routes.RECOVERY) {
            RecoveryScreen(
                onBack = { navController.popBackStack() },
                onComplete = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                },
                biometricManager = biometricManager
            )
        }

        // ─── Guardian Invite ─────────────────────────
        composable(
            route = Routes.GUARDIAN_INVITE,
            arguments = listOf(
                navArgument("inviteId") { type = NavType.StringType }
            ),
            deepLinks = listOf(
                navDeepLink {
                    uriPattern = "mdaopay://guardian-invite/{inviteId}"
                }
            )
        ) { entry ->
            val inviteId = entry.arguments?.getString("inviteId") ?: ""
            RecoveryScreen(
                onBack = { navController.popBackStack() },
                onComplete = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                },
                biometricManager = biometricManager,
                inviteId = inviteId
            )
        }

        // ─── Profile ─────────────────────────────────
        composable(
            route = Routes.PROFILE,
            arguments = listOf(
                navArgument("nickname") { type = NavType.StringType },
                navArgument("address") { type = NavType.StringType }
            )
        ) { entry ->
            val nickname = java.net.URLDecoder.decode(
                entry.arguments?.getString("nickname") ?: "", "UTF-8"
            )
            val address = java.net.URLDecoder.decode(
                entry.arguments?.getString("address") ?: "", "UTF-8"
            )
            ProfileScreen(
                username = nickname,
                walletAddress = address,
                onBack = { navController.popBackStack() },
                onSecurityClick = {
                    navController.popBackStack()
                    navController.navigate(Routes.SETTINGS)
                },
                onRecoveryClick = { navController.navigate(Routes.RECOVERY) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) }
            )
        }

        // ─── Asset Details ─────────────────────────────
        composable(
            route = Routes.ASSET_DETAILS,
            arguments = listOf(
                navArgument("symbol") { type = NavType.StringType },
                navArgument("balance") { type = NavType.StringType }
            )
        ) { entry ->
            val symbol = entry.arguments?.getString("symbol") ?: "MDAO"
            val balance = entry.arguments?.getString("balance") ?: "0"
            AssetDetailsScreen(
                symbol = symbol,
                balance = balance,
                onBack = { navController.popBackStack() },
                onSendClick = {
                    navController.popBackStack()
                    navController.navigate(Routes.sendRoute())
                },
                onReceiveClick = {
                    navController.popBackStack()
                    navController.navigate(Routes.RECEIVE)
                }
            )
        }

        // ─── Contacts ─────────────────────────────────
        composable(Routes.CONTACTS) {
            ContactsScreen(
                onBack = { navController.popBackStack() },
                onContactClick = { nickname ->
                    navController.popBackStack()
                    navController.navigate(Routes.sendRoute(to = nickname))
                },
                onAddClick = {
                    navController.popBackStack()
                }
            )
        }

        // ─── WebView (ecosystem products) ─────────────
        composable(
            route = Routes.WEBVIEW,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val urlArg = backStackEntry.arguments?.getString("url") ?: ""
            val titleArg = backStackEntry.arguments?.getString("title") ?: ""
            val url = try {
                String(android.util.Base64.decode(urlArg, android.util.Base64.URL_SAFE))
            } catch (_: Exception) { urlArg }
            val title = try {
                String(android.util.Base64.decode(titleArg, android.util.Base64.URL_SAFE))
            } catch (_: Exception) { titleArg }
            WebViewScreen(url = url, title = title, onBack = { navController.popBackStack() })
        }

        // ─── On-Ramp (buy/sell crypto) ──────────────────
        composable(
            route = Routes.ONRAMP,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val urlArg = backStackEntry.arguments?.getString("url") ?: ""
            val titleArg = backStackEntry.arguments?.getString("title") ?: ""
            val url = try {
                String(android.util.Base64.decode(urlArg, android.util.Base64.URL_SAFE))
            } catch (_: Exception) { urlArg }
            val title = try {
                String(android.util.Base64.decode(titleArg, android.util.Base64.URL_SAFE))
            } catch (_: Exception) { titleArg }
            OnrampScreen(
                url = url,
                title = title,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebViewScreen(
    url: String,
    title: String,
    onBack: () -> Unit
) {
    val homeViewModel: HomeViewModel = hiltViewModel()
    val webViewClient = remember {
        object : WebViewClient() {
            override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // F-059: Update bridge origin on every main-frame navigation
                view?.let { homeViewModel.onEthereumBridgeNavigation(it, url) }
            }
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                super.onPageFinished(view, url)
                // F-059: inject() checks origin internally — safe to call multiple times
                view?.let { homeViewModel.injectEthereumProvider(it) }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )

            MDAOWebView(
                url = url,
                modifier = Modifier.fillMaxSize(),
                webViewClient = webViewClient
            )
        }
    }
}
