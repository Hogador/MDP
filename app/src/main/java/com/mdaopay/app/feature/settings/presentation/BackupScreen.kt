package com.mdaopay.app.feature.settings.presentation

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdaopay.app.core.ui.components.HapticManager
import com.mdaopay.app.core.ui.components.MDAOButton
import com.mdaopay.app.core.ui.components.MDAOButtonVariant
import com.mdaopay.app.core.ui.components.MDAOInputField
import com.mdaopay.app.core.ui.components.MDAOListItem
import com.mdaopay.app.core.ui.components.MDAOListSection
import com.mdaopay.app.core.ui.components.MDAOTopBar
import com.mdaopay.app.core.ui.theme.MDARadius
import com.mdaopay.app.core.ui.theme.MarsFont
import com.mdaopay.app.core.ui.theme.MarsMono
import com.mdaopay.app.core.ui.theme.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import android.content.Intent
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel()
) {
    val ext = MaterialTheme.extended
    val d = ext.themeColors
    val phrase by viewModel.mnemonicWords.collectAsState()
    val hasWallet by viewModel.hasWallet.collectAsState()
    var isRevealed by remember { mutableStateOf(false) }
    var word3 by remember { mutableStateOf("") }
    var word7 by remember { mutableStateOf("") }
    var word11 by remember { mutableStateOf("") }
    val verifyResult = remember { mutableStateOf<Boolean?>(null) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var exportPassword by remember { mutableStateOf("") }
    var exportPasswordConfirm by remember { mutableStateOf("") }
    var showQrDialog by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(d.bg)) {
        MDAOTopBar(title = "Резервная копия", onBack = onBack)

        if (!hasWallet) {
            // Wallet not created yet
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Кошелёк не создан", fontSize = 16.sp, color = d.text2, fontFamily = MarsFont)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Сначала создайте кошелёк через приветственный экран.", fontSize = 14.sp, color = d.text3, fontFamily = MarsFont, textAlign = TextAlign.Center)
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Status
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MDARadius.xxl))
                    .background(d.card)
                    .drawBehind {
                        drawRoundRect(color = d.softBorder, cornerRadius = androidx.compose.ui.geometry.CornerRadius(MDARadius.xxl.toPx()), style = Stroke(width = 1.dp.toPx()))
                    }
                    .padding(24.dp, 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(50))
                            .background(ext.warningSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(32.dp)) {
                            val p = Path().apply {
                                moveTo(size.width * 0.3f, size.height * 0.15f)
                                lineTo(size.width * 0.7f, size.height * 0.15f)
                                lineTo(size.width * 0.85f, size.height * 0.35f)
                                lineTo(size.width * 0.85f, size.height * 0.85f)
                                lineTo(size.width * 0.15f, size.height * 0.85f)
                                lineTo(size.width * 0.15f, size.height * 0.35f)
                                close()
                            }
                            drawPath(p, color = ext.warning, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            drawLine(color = ext.warning, start = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.4f), end = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.55f), strokeWidth = 2f)
                            drawLine(color = ext.warning, start = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.65f), end = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.7f), strokeWidth = 2f)
                        }
                    }
                    Text(
                        text = "Backup не создан",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = d.text,
                        fontFamily = MarsFont
                    )
                    Text(
                        text = "Без резервной копии вы потеряете доступ к кошельку при потере устройства. Сделайте backup сейчас.",
                        fontSize = 14.sp,
                        color = d.text2,
                        fontFamily = MarsFont,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Warning
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MDARadius.lg))
                    .background(ext.dangerSoft)
                    .padding(14.dp, 14.dp)
            ) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Canvas(modifier = Modifier.size(20.dp)) {
                        drawCircle(color = ext.danger, radius = size.minDimension / 2f, style = Stroke(width = 2f))
                        drawLine(color = ext.danger, start = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.3f), end = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.55f), strokeWidth = 2f)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Никому не показывайте recovery phrase. Любой с этими словами получит полный доступ к вашим средствам. MDAOPay никогда не попросит вашу фразу.",
                            fontSize = 11.sp,
                            color = d.text,
                            fontFamily = MarsFont,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recovery Phrase
            MDAOListSection(header = "Recovery phrase (12 слов)") {
                Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    val gridSize = 3
                    val rows = phrase.chunked(gridSize)
                    rows.forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEachIndexed { _, word ->
                                val idx = phrase.indexOf(word)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(MDARadius.sm))
                                        .background(d.surface)
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "${idx + 1}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = d.text3,
                                            fontFamily = MarsMono
                                        )
                                        Text(
                                            text = if (isRevealed) word else "\u2022\u2022\u2022\u2022",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isRevealed) d.text else d.text3,
                                            fontFamily = MarsMono
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            // Reveal button
            MDAOButton(
                text = if (isRevealed) "Скрыть фразу" else "Показать фразу",
                onClick = { isRevealed = !isRevealed; HapticManager.light() },
                variant = if (isRevealed) MDAOButtonVariant.Secondary else MDAOButtonVariant.Primary,
                size = com.mdaopay.app.core.ui.components.MDAOButtonSize.Sm
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Verify
            MDAOListSection(header = "Проверка (необязательно)") {
                Text(
                    text = "Введите указанные слова из вашей фразы для проверки:",
                    fontSize = 12.sp,
                    color = d.text2,
                    fontFamily = MarsFont,
                    lineHeight = 16.sp,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    VerifyInput(
                        label = "Слово #3",
                        value = word3,
                        onValueChange = { word3 = it },
                        modifier = Modifier.weight(1f)
                    )
                    VerifyInput(
                        label = "Слово #7",
                        value = word7,
                        onValueChange = { word7 = it },
                        modifier = Modifier.weight(1f)
                    )
                    VerifyInput(
                        label = "Слово #11",
                        value = word11,
                        onValueChange = { word11 = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (verifyResult.value == true) {
                    Text(
                        text = "✓ Верно",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ext.success,
                        fontFamily = MarsFont,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 8.dp)
                    )
                } else if (verifyResult.value == false) {
                    Text(
                        text = "✗ Неверно, попробуйте снова",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ext.danger,
                        fontFamily = MarsFont,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 8.dp)
                    )
                }
                Box(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    MDAOButton(
                        text = "Проверить",
                        onClick = {
                            HapticManager.light()
                            val ok = viewModel.verifyWord(2, word3) &&
                                    viewModel.verifyWord(6, word7) &&
                                    viewModel.verifyWord(10, word11)
                            verifyResult.value = ok
                        },
                        variant = MDAOButtonVariant.Secondary,
                        size = com.mdaopay.app.core.ui.components.MDAOButtonSize.Sm
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Export
            MDAOListSection(header = "Экспорт") {
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
                    title = "Экспорт JSON keystore",
                    subtitle = "Зашифрованный файл с паролем",
                    onClick = {
                        HapticManager.light()
                        showPasswordDialog = true
                    }
                )
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                addRoundRect(androidx.compose.ui.geometry.RoundRect(
                                    androidx.compose.ui.geometry.Rect(size.width * 0.1f, size.height * 0.1f, size.width * 0.45f, size.height * 0.45f),
                                    2f, 2f
                                ))
                                addRoundRect(androidx.compose.ui.geometry.RoundRect(
                                    androidx.compose.ui.geometry.Rect(size.width * 0.55f, size.height * 0.1f, size.width * 0.9f, size.height * 0.45f),
                                    2f, 2f
                                ))
                                addRoundRect(androidx.compose.ui.geometry.RoundRect(
                                    androidx.compose.ui.geometry.Rect(size.width * 0.1f, size.height * 0.55f, size.width * 0.45f, size.height * 0.9f),
                                    2f, 2f
                                ))
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 1.5f))
                        }
                    },
                    title = "Экспорт в QR",
                    subtitle = "Для импорта на другое устройство",
                    onClick = {
                        HapticManager.light()
                        qrBitmap = viewModel.generateQrBitmap()
                        showQrDialog = qrBitmap != null
                    }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Password dialog for keystore export
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false; exportPassword = ""; exportPasswordConfirm = "" },
            title = { Text("Создать JSON keystore", fontWeight = FontWeight.Bold, fontFamily = MarsFont) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Введите пароль для шифрования keystore:", fontSize = 14.sp, color = d.text2, fontFamily = MarsFont)
                    MDAOInputField(
                        value = exportPassword,
                        onValueChange = { exportPassword = it },
                        placeholder = "Пароль",
                        singleLine = true
                    )
                    MDAOInputField(
                        value = exportPasswordConfirm,
                        onValueChange = { exportPasswordConfirm = it },
                        placeholder = "Подтвердите пароль",
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = exportPassword.length >= 4 && exportPassword == exportPasswordConfirm,
                    onClick = {
                        val json = viewModel.exportKeystoreJson(exportPassword)
                        if (json != null) {
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, json)
                                putExtra(Intent.EXTRA_SUBJECT, "MDAOPay Keystore")
                            }
                            context.startActivity(Intent.createChooser(share, "Сохранить keystore"))
                        }
                        showPasswordDialog = false
                        exportPassword = ""
                        exportPasswordConfirm = ""
                    }
                ) {
                    Text("Создать", fontFamily = MarsFont)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false; exportPassword = ""; exportPasswordConfirm = "" }) {
                    Text("Отмена", fontFamily = MarsFont)
                }
            }
        )
    }

    // QR dialog
    if (showQrDialog && qrBitmap != null) {
        AlertDialog(
            onDismissRequest = { showQrDialog = false; qrBitmap = null },
            title = {
                Text("QR-код recovery phrase", fontWeight = FontWeight.Bold, fontFamily = MarsFont,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = qrBitmap!!.asImageBitmap(),
                        contentDescription = "QR recovery phrase",
                        modifier = Modifier.size(280.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showQrDialog = false; qrBitmap = null }) {
                    Text("Закрыть", fontFamily = MarsFont)
                }
            }
        )
    }
}

@Composable
private fun VerifyInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ext = MaterialTheme.extended
    val d = ext.themeColors
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = d.text2,
            fontFamily = MarsFont
        )
        Spacer(modifier = Modifier.height(4.dp))
        MDAOInputField(
            value = value,
            onValueChange = onValueChange,
            placeholder = "\u2014",
            singleLine = true
        )
    }
}
