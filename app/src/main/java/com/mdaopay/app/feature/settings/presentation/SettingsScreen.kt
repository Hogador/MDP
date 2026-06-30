package com.mdaopay.app.feature.settings.presentation

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mdaopay.app.core.common.copyToClipboard
import com.mdaopay.app.core.ui.components.GradientBackground
import com.mdaopay.app.core.ui.components.ListIconVariant
import com.mdaopay.app.core.ui.components.MDAOButton
import com.mdaopay.app.core.ui.components.MDAOButtonVariant
import com.mdaopay.app.core.ui.components.MDAOListItem
import com.mdaopay.app.core.ui.components.MDAOListSection
import com.mdaopay.app.core.ui.components.MDAOTopBar
import com.mdaopay.app.core.ui.theme.ErrorRed
import com.mdaopay.app.core.ui.theme.MDARadius
import com.mdaopay.app.core.ui.theme.MarsFont
import com.mdaopay.app.core.ui.theme.MarsMono
import com.mdaopay.app.core.ui.theme.extended
import com.mdaopay.app.core.ui.theme.AppTheme
import com.mdaopay.app.core.ui.theme.ThemeHolder
import com.mdaopay.app.core.security.SocialUser

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onRecoveryClick: () -> Unit,
    onCreateNewWallet: () -> Unit,
    onProfileClick: () -> Unit = {},
    onSecurityClick: () -> Unit = {},
    onBackupClick: () -> Unit = {},
    onAppearanceClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onLanguageClick: () -> Unit = {},
    onNetworksClick: () -> Unit = {},
    onWalletsTokensClick: () -> Unit = {},
    onHelpClick: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val address by viewModel.address.collectAsState()
    val appLockEnabled by viewModel.appLockEnabled.collectAsState()
    val appVersion by viewModel.appVersion.collectAsState()
    val socialUser by viewModel.socialUser.collectAsState()
    val socialError by viewModel.socialError.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    var showClearDialog by remember { mutableStateOf(false) }
    var showNewWalletDialog by remember { mutableStateOf(false) }

    val currentTheme by ThemeHolder.current.collectAsState()
    val ext = MaterialTheme.extended
    val d = ext.themeColors

    val avatarName = socialUser?.name?.take(2)?.uppercase() ?: "A"
    val displayName = socialUser?.name ?: "Антон"
    val displayNick = socialUser?.let { "@${it.name.lowercase().replace(" ", "-")}" } ?: "@user"
    val displayDiscriminator = "#2215"

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = d.card,
            title = {
                Text(
                    text = "Удалить кошелёк?",
                    color = d.text,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Seed-фраза и все данные будут удалены. Убедитесь, что у вас есть резервная копия seed-фразы.",
                    color = d.text2
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearWallet()
                    showClearDialog = false
                    onBack()
                }) {
                    Text("Удалить", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Отмена", color = d.text2)
                }
            }
        )
    }

    if (showNewWalletDialog) {
        AlertDialog(
            onDismissRequest = { showNewWalletDialog = false },
            containerColor = d.card,
            title = {
                Text(
                    text = "Создать новый кошелёк?",
                    color = d.text,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Текущий кошелёк и все данные будут удалены. " +
                            "Убедитесь, что у вас есть резервная копия seed-фразы.",
                    color = d.text2
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createNewWallet()
                    showNewWalletDialog = false
                    onCreateNewWallet()
                }) {
                    Text("Создать", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewWalletDialog = false }) {
                    Text("Отмена", color = d.text2)
                }
            }
        )
    }

    GradientBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            MDAOTopBar(
                title = "Настройки",
                onBack = onBack
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Profile Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(MDARadius.xxl))
                        .background(d.card)
                        .shadow(elevation = 4.dp, shape = androidx.compose.foundation.shape.RoundedCornerShape(MDARadius.xxl), clip = false)
                        .clickable { onProfileClick() }
                        .padding(14.dp, 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .shadow(6.dp, CircleShape, clip = false)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            ext.accent,
                                            Color(0xFFFF9A4D)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = avatarName,
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = MarsFont
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = d.text,
                                fontFamily = MarsFont,
                                letterSpacing = (-0.3).sp
                            )
                            Text(
                                text = displayNick,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ext.accent,
                                fontFamily = MarsMono
                            )
                            Text(
                                text = "Дискриминатор: $displayDiscriminator",
                                fontSize = 11.sp,
                                color = d.text2
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Canvas(modifier = Modifier.size(20.dp)) {
                            val path = Path().apply {
                                moveTo(size.width * 0.3f, size.height * 0.2f)
                                lineTo(size.width * 0.7f, size.height * 0.5f)
                                lineTo(size.width * 0.3f, size.height * 0.8f)
                            }
                            drawPath(
                                path = path,
                                color = d.text3,
                                style = Stroke(
                                    width = 2.4f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Backup Warning
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(MDARadius.lg))
                        .background(ext.warningSoft)
                        .clickable { onBackupClick() }
                        .padding(10.dp, 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val path = Path().apply {
                                moveTo(size.width * 0.25f, size.height * 0.25f)
                                lineTo(size.width * 0.75f, size.height * 0.75f)
                                moveTo(size.width * 0.75f, size.height * 0.25f)
                                lineTo(size.width * 0.25f, size.height * 0.75f)
                            }
                            drawPath(
                                path = path,
                                color = ext.warning,
                                style = Stroke(
                                    width = 2f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                            val circle = Path().apply {
                                addOval(
                                    androidx.compose.ui.geometry.Rect(
                                        size.width * 0.35f, size.height * 0.35f,
                                        size.width * 0.65f, size.height * 0.65f
                                    )
                                )
                            }
                            drawPath(
                                path = circle,
                                color = ext.warning,
                                style = Stroke(width = 2f)
                            )
                        }
                        Text(
                            text = "Резервная копия не создана · Сделайте backup, чтобы не потерять доступ",
                            fontSize = 11.sp,
                            color = d.text,
                            lineHeight = 16.sp,
                            fontFamily = MarsFont,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Account Section
                MDAOListSection {
                    MDAOListItem(
                        icon = {
                            Canvas(modifier = Modifier.size(18.dp)) {
                                drawCircle(color = Color.White, radius = size.minDimension / 2f)
                                drawCircle(
                                    color = ext.themeColors.tile,
                                    radius = size.minDimension / 4f
                                )
                            }
                        },
                        iconVariant = ListIconVariant.Accent,
                        title = "Профиль",
                        subtitle = "Ник, адрес, QR-код",
                        onClick = onProfileClick
                    )
                    MDAOListItem(
                        icon = {
                            Canvas(modifier = Modifier.size(18.dp)) {
                                val p = Path().apply {
                                    moveTo(size.width * 0.15f, size.height * 0.85f)
                                    lineTo(size.width * 0.15f, size.height * 0.35f)
                                    lineTo(size.width * 0.5f, size.height * 0.1f)
                                    lineTo(size.width * 0.85f, size.height * 0.35f)
                                    lineTo(size.width * 0.85f, size.height * 0.85f)
                                    close()
                                }
                                drawPath(p, color = Color.White, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.45f), end = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.65f), strokeWidth = 2f)
                            }
                        },
                        iconVariant = ListIconVariant.Success,
                        title = "Безопасность",
                        subtitle = "PIN, биометрия, автоблокировка",
                        onClick = onSecurityClick
                    )
                    MDAOListItem(
                        icon = {
                            Canvas(modifier = Modifier.size(18.dp)) {
                                val p = Path().apply {
                                    moveTo(size.width * 0.15f, size.height * 0.55f)
                                    lineTo(size.width * 0.5f, size.height * 0.85f)
                                    lineTo(size.width * 0.85f, size.height * 0.55f)
                                    moveTo(size.width * 0.5f, size.height * 0.85f)
                                    lineTo(size.width * 0.5f, size.height * 0.1f)
                                }
                                drawPath(p, color = Color.White, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                        },
                        title = "Резервная копия",
                        subtitle = "Recovery phrase, экспорт",
                        badge = "!",
                        onClick = onBackupClick
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Appearance Section
                MDAOListSection(header = "Внешний вид") {
                    MDAOListItem(
                        icon = {
                            Canvas(modifier = Modifier.size(18.dp)) {
                                drawCircle(color = Color.White, radius = size.minDimension * 0.3f)
                                repeat(4) { i ->
                                    val angle = Math.toRadians((i * 90).toDouble())
                                    val cx = size.width / 2f + (size.width / 2.5f) * kotlin.math.cos(angle).toFloat()
                                    val cy = size.height / 2f + (size.height / 2.5f) * kotlin.math.sin(angle).toFloat()
                                    drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f), end = androidx.compose.ui.geometry.Offset(cx, cy), strokeWidth = 1.5f)
                                }
                            }
                        },
                        title = "Тема",
                        value = when (currentTheme) {
                            AppTheme.LIGHT -> "Светлая"
                            AppTheme.DARK -> "Тёмная"
                            AppTheme.AMOLED -> "AMOLED"
                            AppTheme.SYSTEM -> "Системная"
                        },
                        onClick = onAppearanceClick
                    )
                    MDAOListItem(
                        icon = {
                            Canvas(modifier = Modifier.size(18.dp)) {
                                drawCircle(color = ext.accent, radius = size.minDimension * 0.35f)
                            }
                        },
                        title = "Акцентный цвет",
                        value = "Оранжевый",
                        onClick = onAppearanceClick
                    )
                    MDAOListItem(
                        icon = {
                            Canvas(modifier = Modifier.size(18.dp)) {
                                val p = Path().apply {
                                    moveTo(size.width * 0.3f, size.height * 0.2f)
                                    lineTo(size.width * 0.7f, size.height * 0.2f)
                                    lineTo(size.width * 0.7f, size.height * 0.4f)
                                    lineTo(size.width * 0.3f, size.height * 0.4f)
                                    close()
                                    moveTo(size.width * 0.3f, size.height * 0.5f)
                                    lineTo(size.width * 0.7f, size.height * 0.5f)
                                    lineTo(size.width * 0.7f, size.height * 0.7f)
                                    lineTo(size.width * 0.3f, size.height * 0.7f)
                                    close()
                                }
                                drawPath(p, color = Color.White)
                            }
                        },
                        title = "Уведомления",
                        subtitle = "Push, категории, тихие часы",
                        onClick = onNotificationsClick
                    )
                    MDAOListItem(
                        icon = {
                            Canvas(modifier = Modifier.size(18.dp)) {
                                drawCircle(color = Color.White, radius = size.minDimension / 2f, style = Stroke(width = 1.5f))
                                drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(size.width * 0.3f, size.height / 2f), end = androidx.compose.ui.geometry.Offset(size.width * 0.7f, size.height / 2f), strokeWidth = 1.5f)
                                drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.3f), end = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.7f), strokeWidth = 1.5f)
                            }
                        },
                        title = "Язык",
                        value = "Русский",
                        onClick = onLanguageClick
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Wallet Section
                MDAOListSection(header = "Кошелёк") {
                    MDAOListItem(
                        icon = {
                            Canvas(modifier = Modifier.size(18.dp)) {
                                drawCircle(color = Color.White, radius = size.minDimension / 2f, style = Stroke(width = 1.5f))
                                drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(size.width * 0.3f, size.height / 2f), end = androidx.compose.ui.geometry.Offset(size.width * 0.7f, size.height / 2f), strokeWidth = 1.5f)
                                drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.3f), end = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.7f), strokeWidth = 1.5f)
                            }
                        },
                        title = "Сети",
                        subtitle = "RPC, эксплореры",
                        value = "BNB Chain",
                        onClick = onNetworksClick
                    )
                    MDAOListItem(
                        icon = {
                            Canvas(modifier = Modifier.size(18.dp)) {
                                val p = Path().apply {
                                    addRoundRect(androidx.compose.ui.geometry.RoundRect(
                                        androidx.compose.ui.geometry.Rect(size.width * 0.1f, size.height * 0.2f, size.width * 0.9f, size.height * 0.9f),
                                        3f, 3f
                                    ))
                                }
                                drawPath(p, color = Color.White, style = Stroke(width = 1.5f))
                                drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.5f), end = androidx.compose.ui.geometry.Offset(size.width * 0.9f, size.height * 0.5f), strokeWidth = 1.5f)
                            }
                        },
                        title = "Кошельки и токены",
                        subtitle = "Аккаунты, скрытые токены",
                        onClick = onWalletsTokensClick
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Support Section
                MDAOListSection(header = "Поддержка") {
                    MDAOListItem(
                        icon = {
                            Canvas(modifier = Modifier.size(18.dp)) {
                                drawCircle(color = Color.White, radius = size.minDimension / 2f - 1f, style = Stroke(width = 1.5f))
                                drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(size.width * 0.35f, size.height * 0.35f), end = androidx.compose.ui.geometry.Offset(size.width * 0.35f, size.height * 0.45f), strokeWidth = 1.5f)
                                drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(size.width * 0.35f, size.height * 0.55f), end = androidx.compose.ui.geometry.Offset(size.width * 0.35f, size.height * 0.6f), strokeWidth = 1.5f)
                            }
                        },
                        title = "Помощь и FAQ",
                        onClick = onHelpClick
                    )
                    MDAOListItem(
                        icon = {
                            Canvas(modifier = Modifier.size(18.dp)) {
                                drawCircle(color = Color.White, radius = size.minDimension / 2f - 1f, style = Stroke(width = 1.5f))
                                drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.3f), end = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.45f), strokeWidth = 1.5f)
                                drawLine(color = Color.White, start = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.55f), end = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.6f), strokeWidth = 1.5f)
                            }
                        },
                        title = "О приложении",
                        subtitle = "Версия, лицензии, статус",
                        onClick = onAboutClick
                    )
                }

                // Social account section
                Spacer(modifier = Modifier.height(16.dp))
                MDAOListSection(header = "Привязка аккаунта") {
                    if (socialUser != null) {
                        MDAOListItem(
                            icon = {
                                Canvas(modifier = Modifier.size(18.dp)) {
                                    drawCircle(color = Color.White, radius = size.minDimension * 0.35f)
                                    val p = Path().apply {
                                        addOval(androidx.compose.ui.geometry.Rect(size.width * 0.2f, size.height * 0.6f, size.width * 0.8f, size.height * 0.95f))
                                    }
                                    drawPath(p, color = Color.White)
                                }
                            },
                            iconVariant = ListIconVariant.Accent,
                            title = socialUser!!.name,
                            subtitle = "${socialUser!!.provider.uppercase()} · ${socialUser!!.email}",
                            showChevron = false
                        )
                    } else {
                        MDAOListItem(
                            icon = {
                                Canvas(modifier = Modifier.size(18.dp)) {
                                    drawCircle(color = Color.White, radius = size.minDimension * 0.35f)
                                    val p = Path().apply {
                                        addOval(androidx.compose.ui.geometry.Rect(size.width * 0.2f, size.height * 0.6f, size.width * 0.8f, size.height * 0.95f))
                                    }
                                    drawPath(p, color = Color.White)
                                }
                            },
                            title = "Привязать аккаунт",
                            subtitle = "Google · Apple для восстановления",
                            showChevron = false
                        )
                    }
                }
                if (socialUser == null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MDAOButton(
                            text = "Google",
                            onClick = { activity?.let { viewModel.signInWithGoogle(it) } },
                            modifier = Modifier.weight(1f),
                            size = com.mdaopay.app.core.ui.components.MDAOButtonSize.Sm
                        )
                        MDAOButton(
                            text = "Apple",
                            onClick = { activity?.let { viewModel.signInWithApple(it) } },
                            modifier = Modifier.weight(1f),
                            size = com.mdaopay.app.core.ui.components.MDAOButtonSize.Sm,
                            variant = MDAOButtonVariant.Secondary
                        )
                    }
                }
                socialError?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        fontSize = 11.sp,
                        color = ErrorRed,
                        fontFamily = MarsFont
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Danger zone buttons
                MDAOButton(
                    text = "Удалить кошелёк",
                    onClick = { showClearDialog = true },
                    variant = MDAOButtonVariant.Danger,
                    size = com.mdaopay.app.core.ui.components.MDAOButtonSize.Sm
                )

                Spacer(modifier = Modifier.height(8.dp))

                MDAOButton(
                    text = "Создать новый кошелёк",
                    onClick = { showNewWalletDialog = true },
                    variant = MDAOButtonVariant.Secondary,
                    size = com.mdaopay.app.core.ui.components.MDAOButtonSize.Sm
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Version block
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "MDAOPay",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = ext.accent,
                        fontFamily = MarsFont,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "Версия ${appVersion.ifEmpty { "1.0.0" }} (build 2215)",
                        fontSize = 11.sp,
                        color = d.text3,
                        fontFamily = MarsMono
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
