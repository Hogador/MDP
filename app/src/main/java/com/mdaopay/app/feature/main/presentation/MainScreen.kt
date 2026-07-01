package com.mdaopay.app.feature.main.presentation

import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mdaopay.app.core.config.allProducts
import com.mdaopay.app.core.config.ProductItem
import com.mdaopay.app.core.ui.components.GradientBackground
import com.mdaopay.app.core.ui.components.HapticManager
import com.mdaopay.app.core.ui.components.MDAONavigationBar
import com.mdaopay.app.core.ui.components.MDAOWebView
import com.mdaopay.app.core.ui.components.NavItem
import com.mdaopay.app.core.ui.theme.extended
import com.mdaopay.app.feature.exchanger.presentation.ExchangerScreen
import com.mdaopay.app.feature.home.presentation.HomeScreen
import com.mdaopay.app.feature.home.presentation.HomeViewModel
import com.mdaopay.app.navigation.Routes

@Composable
fun MainScreen(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val d = MaterialTheme.extended.themeColors
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = remember(selectedTab) {
        listOf(
            NavItem(
                label = "\u041A\u043E\u0448\u0435\u043B\u0451\u043A",
                isActive = selectedTab == 0,
                onClick = { selectedTab = 0 },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.AccountBalanceWallet,
                        contentDescription = "\u041A\u043E\u0448\u0435\u043B\u0451\u043A",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(22.dp)
                    )
                }
            ),
            NavItem(
                label = "\u0424\u0438\u0430\u0442",
                isActive = selectedTab == 1,
                onClick = { selectedTab = 1 },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.CurrencyExchange,
                        contentDescription = "\u0424\u0438\u0430\u0442",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(22.dp)
                    )
                }
            ),
            NavItem(
                label = "\u0421\u0435\u0440\u0432\u0438\u0441\u044B",
                isActive = selectedTab == 2,
                onClick = { selectedTab = 2 },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Apps,
                        contentDescription = "\u0421\u0435\u0440\u0432\u0438\u0441\u044B",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(22.dp)
                    )
                }
            )
        )
    }

    Scaffold(
        bottomBar = {
            MDAONavigationBar(items = tabs)
        },
        containerColor = d.bg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(d.bg)
        ) {
            when (selectedTab) {
                0 -> HomeScreen(
                    onSendClick = { navController.navigate(Routes.sendRoute()) },
                    onReceiveClick = { navController.navigate(Routes.RECEIVE) },
                    onHistoryClick = { navController.navigate(Routes.HISTORY) },
                    onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                    onProfileClick = { nickname, address ->
                        navController.navigate(Routes.profileRoute(nickname, address))
                    },
                    onAssetClick = { symbol, balance ->
                        navController.navigate(Routes.assetDetailsRoute(symbol, balance))
                    },
                    onFavPersonClick = { nickname ->
                        navController.navigate(Routes.sendRoute(to = nickname))
                    },
                    onAddClick = {
                        navController.navigate(Routes.CONTACTS)
                    },
                    onServiceClick = { url, title ->
                        navController.navigate(Routes.webviewRoute(url, title))
                    },
                    onBuyClick = { url, title ->
                        navController.navigate(Routes.onrampRoute(url, title))
                    },
                    onSellClick = { url, title ->
                        navController.navigate(Routes.onrampRoute(url, title))
                    },
                    onProductsClick = { selectedTab = 2 },
                    viewModel = viewModel
                )
                1 -> ExchangerScreen(
                    onBuyClick = { url, title ->
                        navController.navigate(Routes.onrampRoute(url, title))
                    },
                    onSellClick = { url, title ->
                        navController.navigate(Routes.onrampRoute(url, title))
                    }
                )
                2 -> ProductsTab(
                    onOpenWebView = { url, title ->
                        navController.navigate(Routes.webviewRoute(url, title))
                    }
                )
            }
        }
    }
}

@Composable
private fun ProductWebView(url: String, title: String) {
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf(false) }
    val d = MaterialTheme.extended.themeColors

    GradientBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = d.text,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            Box(modifier = Modifier.fillMaxSize()) {
                MDAOWebView(
                    url = url,
                    modifier = Modifier.fillMaxSize(),
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                            error = false
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                        }
                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            error = true
                            isLoading = false
                        }
                    },
                    onWebViewCreated = { webView ->
                        webView.webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        }
                    }
                )

                if (isLoading && progress < 100) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.extended.accent,
                        trackColor = d.border,
                    )
                }

                if (error) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u0437\u0430\u0433\u0440\u0443\u0437\u0438\u0442\u044C",
                            style = MaterialTheme.typography.titleMedium,
                            color = d.text
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "\u041F\u0440\u043E\u0432\u0435\u0440\u044C\u0442\u0435 \u043F\u043E\u0434\u043A\u043B\u044E\u0447\u0435\u043D\u0438\u0435 \u043A \u0438\u043D\u0442\u0435\u0440\u043D\u0435\u0442\u0443",
                            style = MaterialTheme.typography.bodyMedium,
                            color = d.text2,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductsTab(
    onOpenWebView: (url: String, title: String) -> Unit = { _, _ -> }
) {
    val d = MaterialTheme.extended.themeColors
    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "\u041F\u0440\u043E\u0434\u0443\u043A\u0442\u044B MarsDAO",
                style = MaterialTheme.typography.headlineMedium,
                color = d.text,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "\u0412\u0441\u044F \u044D\u043A\u043E\u0441\u0438\u0441\u0442\u0435\u043C\u0430 \u0432 \u043E\u0434\u043D\u043E\u043C \u043C\u0435\u0441\u0442\u0435",
                style = MaterialTheme.typography.bodyMedium,
                color = d.text2
            )
            Spacer(modifier = Modifier.height(20.dp))

            allProducts.forEach { product ->
                ProductCard(
                    product = product,
                    onClick = { onOpenWebView(product.url, product.name) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ProductCard(
    product: ProductItem,
    onClick: () -> Unit = {}
) {
    val d = MaterialTheme.extended.themeColors
    val a = MaterialTheme.extended.accent
    Card(
        onClick = {
            HapticManager.light()
            onClick()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = d.card),
        border = androidx.compose.foundation.BorderStroke(1.dp, d.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(a.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = product.icon,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = d.text,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = product.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = d.text2
                )
            }
            Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = "Открыть",
                tint = d.text2.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
