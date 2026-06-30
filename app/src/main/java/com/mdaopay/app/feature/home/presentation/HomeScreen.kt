package com.mdaopay.app.feature.home.presentation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import com.mdaopay.app.core.common.formatTxTime
import com.mdaopay.app.core.ui.motion.MDAOSpring
import com.mdaopay.app.core.ui.motion.MDAOTween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.mdaopay.app.core.common.toDisplayAmount
import com.mdaopay.app.core.config.homeServices
import com.mdaopay.app.core.datastore.Contact
import com.mdaopay.app.core.ui.components.GradientBackground
import com.mdaopay.app.core.ui.components.HapticManager
import com.mdaopay.app.core.ui.components.MDAOButton
import com.mdaopay.app.core.ui.components.OfflineBanner
import com.mdaopay.app.core.ui.components.SecureScreen
import com.mdaopay.app.core.ui.theme.AppTheme
import com.mdaopay.app.core.ui.theme.MarsMono
import com.mdaopay.app.core.ui.theme.MDAOGlow
import com.mdaopay.app.core.ui.theme.ThemeHolder
import com.mdaopay.app.core.ui.theme.MDAOPurple
import com.mdaopay.app.core.ui.theme.extended
import com.mdaopay.app.core.ui.theme.SuccessGreen
import com.mdaopay.app.feature.home.domain.model.TransactionItem
import com.mdaopay.app.feature.home.domain.model.TxStatus
import com.mdaopay.app.feature.home.domain.model.WalletState
import java.math.BigDecimal

@Composable
fun HomeScreen(
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onProfileClick: (nickname: String, address: String) -> Unit = { _, _ -> },
    onAssetClick: (symbol: String, balance: String) -> Unit = { _, _ -> },
    onFavPersonClick: (String) -> Unit = {},
    onAddClick: () -> Unit = {},
    onServiceClick: (url: String, title: String) -> Unit = { _, _ -> },
    onBuyClick: (url: String, title: String) -> Unit = { _, _ -> },
    onSellClick: (url: String, title: String) -> Unit = { _, _ -> },
    onProductsClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    SecureScreen()

    GradientBackground {
        when (val state = uiState) {
            is HomeUiState.Loading -> HomeLoadingSkeleton()
            is HomeUiState.Ready -> HomeContent(
                wallet = state.wallet,
                transactions = state.recentTransactions,
                contacts = state.contacts,
                isConnected = state.isConnected,
                displayName = state.displayName,
                onSendClick = onSendClick,
                onReceiveClick = onReceiveClick,
                onHistoryClick = onHistoryClick,
                onSettingsClick = onSettingsClick,
                onProfileClick = onProfileClick,
                onAssetClick = onAssetClick,
                onFavPersonClick = onFavPersonClick,
                onAddClick = onAddClick,
                onServiceClick = onServiceClick,
                onBuyClick = onBuyClick,
                onSellClick = onSellClick,
                onProductsClick = onProductsClick
            )
            is HomeUiState.Error -> HomeErrorState(
                message = state.message,
                onRetry = { viewModel.loadWallet() },
                onSettingsClick = onSettingsClick,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    wallet: WalletState,
    transactions: List<TransactionItem>,
    contacts: List<Contact> = emptyList(),
    isConnected: Boolean = true,
    displayName: String = "",
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onProfileClick: (nickname: String, address: String) -> Unit = { _, _ -> },
    onAssetClick: (symbol: String, balance: String) -> Unit = { _, _ -> },
    onFavPersonClick: (String) -> Unit = {},
    onAddClick: () -> Unit = {},
    onServiceClick: (url: String, title: String) -> Unit = { _, _ -> },
    onBuyClick: (url: String, title: String) -> Unit = { _, _ -> },
    onSellClick: (url: String, title: String) -> Unit = { _, _ -> },
    onProductsClick: () -> Unit = {}
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = MDAOTween.normal,
        label = "homeAlpha"
    )

    var selectedToken by remember { mutableStateOf("MDAO") }
    var balanceHidden by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var showAddressSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        OfflineBanner(isOffline = !isConnected)
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.clickable {
                    HapticManager.light()
                    showAddressSheet = true
                }
            ) {
                Text(
                    text = "Привет,",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = displayName.ifBlank { wallet.address },
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = {
                        HapticManager.light()
                        val next = when (ThemeHolder.current.value) {
                            AppTheme.DARK -> AppTheme.LIGHT
                            else -> AppTheme.DARK
                        }
                        ThemeHolder.setTheme(next)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                ) {
                    Icon(
                        imageVector = if (ThemeHolder.current.value == AppTheme.DARK)
                            Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                        contentDescription = "Тема",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = {
                        HapticManager.light()
                        onSettingsClick()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Настройки",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        TokenCardSwipableStack(
            wallet = wallet,
            selectedToken = selectedToken,
            onSelectedTokenChanged = { selectedToken = it },
            balanceHidden = balanceHidden,
            onSendClick = onSendClick,
            onReceiveClick = onReceiveClick,
            onHistoryClick = onHistoryClick,
            onBuyClick = onBuyClick,
            onSellClick = onSellClick,
            onAssetClick = { symbol, bal -> onAssetClick(symbol, bal) }
        )

        Spacer(modifier = Modifier.height(28.dp))

        FavoritePeopleSection(
            contacts = contacts,
            onAddClick = onAddClick,
            onPersonClick = { contact -> onFavPersonClick(contact.nickname) }
        )

        Spacer(modifier = Modifier.height(28.dp))

        ServicesSection(
            onServiceClick = onServiceClick
        )

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showAddressSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddressSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Мои адреса",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold
                )

                NetworkAddressCard(
                    network = "Ethereum (Sepolia)",
                    address = wallet.address,
                    chainId = "11155111",
                    context = context
                )

                NetworkAddressCard(
                    network = "MDAO Mainnet",
                    address = wallet.address,
                    chainId = "31337",
                    context = context
                )
            }
        }
    }
}

@Composable
private fun TokenActions(
    selectedToken: String,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    val context = LocalContext.current

    val buttons = when (selectedToken) {
        "USDT" -> listOf(
            Triple("Купить USDT", Icons.Rounded.ArrowDownward) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/mommys_trader_bot"))
                context.startActivity(intent)
            },
            Triple("Продать USDT", Icons.Rounded.ArrowUpward) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/mommys_trader_bot"))
                context.startActivity(intent)
            },
            Triple("История", Icons.Rounded.History, onHistoryClick)
        )
        "Sepolia ETH" -> listOf(
            Triple("Отправить ETH", Icons.Rounded.ArrowUpward, onSendClick),
            Triple("Получить ETH", Icons.Rounded.ArrowDownward, onReceiveClick),
            Triple("История", Icons.Rounded.History, onHistoryClick)
        )
        else -> listOf(
            Triple("Отправить MDAO", Icons.Rounded.ArrowUpward, onSendClick),
            Triple("Получить MDAO", Icons.Rounded.ArrowDownward, onReceiveClick),
            Triple("История", Icons.Rounded.History, onHistoryClick)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        buttons.forEach { (label, icon, onClick) ->
            ActionButton(
                label = label,
                icon = icon,
                onClick = onClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val btnScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = MDAOSpring.press,
        label = "actionBtnScale"
    )

    Card(
        onClick = {
            HapticManager.light()
            onClick()
        },
        modifier = modifier.scale(btnScale),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.extended.borderBright),
        interactionSource = interactionSource
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MDAOGlow, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MDAOPurple,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun TransactionRow(tx: TransactionItem) {
    val isIncoming = tx.amountUsdt > BigDecimal.ZERO
    val amountColor = if (isIncoming) SuccessGreen else MaterialTheme.colorScheme.onBackground
    val prefix = if (isIncoming) "↗ +" else "↘ "
    val context = LocalContext.current
    val isAddress = tx.nickname.isBlank()
    val displayCounterparty = if (tx.nickname.isNotBlank()) tx.nickname
        else tx.counterparty.let { if (it.length > 10) "${it.take(6)}...${it.takeLast(4)}" else it }

    Card(
        onClick = {
            if (tx.status == TxStatus.CONFIRMED && tx.txHash.isNotBlank()) {
                val url = "https://sepolia.etherscan.io/tx/${tx.txHash}"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.extended.borderBright)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$prefix${tx.amountUsdt.abs().toDisplayAmount()} ${tx.tokenSymbol}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = amountColor,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (tx.isExternal) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "⚡",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = displayCounterparty,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = if (isAddress) MarsMono else null,
                    maxLines = 1
                )
            }
            Text(
                text = tx.timestamp.formatTxTime(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyTransactions() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Операций пока нет",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HomeLoadingSkeleton() {
    val infinite = rememberInfiniteTransition()
    val alpha by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            MDAOTween.normal,
            RepeatMode.Reverse
        ),
        label = "loadingPulse"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha)
        ) {
            Text(
                text = "M",
                style = MaterialTheme.typography.displayLarge,
                color = MDAOPurple,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "MDAO Pay",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Загрузка кошелька...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun HomeErrorState(
    message: String,
    onRetry: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Не удалось загрузить",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MDAOButton(text = "Попробовать снова", onClick = onRetry)
            TextButton(onClick = onSettingsClick) {
                Text("Настройки")
            }
        }
    }
}

private data class TokenInfo(
    val symbol: String,
    val balance: java.math.BigDecimal,
    val accentColor: androidx.compose.ui.graphics.Color
)

private fun walletToTokens(wallet: WalletState): List<TokenInfo> = buildList {
    if (wallet.balanceMdao > java.math.BigDecimal.ZERO)
        add(TokenInfo("MDAO", wallet.balanceMdao, MDAOPurple))
    if (wallet.balanceUsdt > java.math.BigDecimal.ZERO)
        add(TokenInfo("USDT", wallet.balanceUsdt, SuccessGreen))
    if (wallet.balanceEth > java.math.BigDecimal.ZERO)
        add(TokenInfo("Sepolia ETH", wallet.balanceEth, androidx.compose.ui.graphics.Color(0xFF627EEA)))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TokenCardSwipableStack(
    wallet: WalletState,
    selectedToken: String,
    onSelectedTokenChanged: (String) -> Unit,
    balanceHidden: Boolean,
    onSendClick: () -> Unit = {},
    onReceiveClick: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onBuyClick: (url: String, title: String) -> Unit = { _, _ -> },
    onSellClick: (url: String, title: String) -> Unit = { _, _ -> },
    onAssetClick: (symbol: String, balance: String) -> Unit = { _, _ -> }
) {
    val tokens = remember(wallet) { walletToTokens(wallet) }
    if (tokens.isEmpty()) return

    val context = LocalContext.current
    val initialPage = tokens.indexOfFirst { it.symbol == selectedToken }.coerceAtLeast(0)
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { tokens.size }
    )

    LaunchedEffect(pagerState.currentPage) {
        onSelectedTokenChanged(tokens[pagerState.currentPage].symbol)
    }

    val currentToken = tokens[pagerState.currentPage]

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                userScrollEnabled = true
            ) { page ->
                val token = tokens[page]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    token.accentColor,
                                    token.accentColor.copy(alpha = 0.55f)
                                )
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (token.symbol) {
                                        "Sepolia ETH" -> "Ξ"
                                        "USDT" -> "₮"
                                        else -> "M"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = token.symbol,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                                Text(
                                    text = "Token",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Balance",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (balanceHidden) "••••••"
                            else "▲ ${token.balance.toDisplayAmount(if (token.symbol == "Sepolia ETH") 6 else 2)}",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val actions = when (token.symbol) {
                                "USDT" -> listOf(
                                    Triple("Buy", Icons.Rounded.ArrowDownward) {
                                        onBuyClick("https://t.me/mommys_trader_bot?start=buy_usdt", "Купить USDT")
                                    },
                                    Triple("Sell", Icons.Rounded.ArrowUpward) {
                                        onSellClick("https://t.me/mommys_trader_bot?start=sell_usdt", "Продать USDT")
                                    },
                                    Triple("History", Icons.Rounded.History, onHistoryClick)
                                )
                                else -> listOf(
                                    Triple("Send", Icons.Rounded.ArrowUpward, onSendClick),
                                    Triple("Receive", Icons.Rounded.ArrowDownward, onReceiveClick),
                                    Triple("History", Icons.Rounded.History, onHistoryClick)
                                )
                            }
                            actions.forEach { (label, icon, onClick) ->
                                BankCardActionButton(
                                    label = label,
                                    icon = icon,
                                    onClick = onClick,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            if (tokens.size > 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(currentToken.accentColor)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tokens.forEachIndexed { i, token ->
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        if (i == pagerState.currentPage) Color.White
                                        else Color.White.copy(alpha = 0.35f),
                                        CircleShape
                                    )
                                    .clickable {
                                        onSelectedTokenChanged(token.symbol)
                                        HapticManager.light()
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BankCardActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val btnScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = MDAOSpring.press,
        label = "bankActionScale"
    )

    Card(
        onClick = {
            HapticManager.light()
            onClick()
        },
        modifier = modifier.scale(btnScale),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.15f)
        ),
        interactionSource = interactionSource
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun FavoritePeopleSection(
    contacts: List<Contact> = emptyList(),
    onAddClick: () -> Unit = {},
    onPersonClick: (Contact) -> Unit = {}
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Избранные",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Все",
                style = MaterialTheme.typography.labelMedium,
                color = MDAOPurple,
                modifier = Modifier.clickable {
                    HapticManager.light()
                    onAddClick()
                }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val items = contacts.take(3).ifEmpty { null }
            if (items != null) {
                items.forEach { contact ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                .clickable {
                                    HapticManager.light()
                                    onPersonClick(contact)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = contact.nickname.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MDAOPurple,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = contact.nickname,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
            val slots = if (items != null) 1 else 3
            val emptyCount = if (items != null) 1.coerceAtMost(3 - items.size) else 3
            repeat(emptyCount) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .clickable {
                                HapticManager.light()
                                onAddClick()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Добавить",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ServicesSection(
    onServiceClick: (url: String, title: String) -> Unit = { _, _ -> }
) {
    val services = homeServices.take(4)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Сервисы",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Все сервисы",
                style = MaterialTheme.typography.labelMedium,
                color = MDAOPurple,
                modifier = Modifier.clickable {
                    HapticManager.light()
                    onServiceClick("https://t.me/v_utushkin/66560", "Продукты MarsDAO")
                }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            services.forEach { product ->
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            HapticManager.light()
                            onServiceClick(product.url, product.name)
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.extended.borderBright)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(MDAOGlow, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = product.icon,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkAddressCard(
    network: String,
    address: String,
    chainId: String,
    context: android.content.Context
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = network,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = MarsMono),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1
                )
            }
            IconButton(
                onClick = {
                    val clip = ClipData.newPlainText("wallet", address)
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(clip)
                    Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
