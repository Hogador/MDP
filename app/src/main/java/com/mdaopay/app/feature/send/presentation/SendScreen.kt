package com.mdaopay.app.feature.send.presentation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.mdaopay.app.core.common.Result
import com.mdaopay.app.core.common.toDisplayAmount
import com.mdaopay.app.core.security.BiometricAuthManager
import com.mdaopay.app.core.ui.components.GradientBackground
import com.mdaopay.app.core.ui.components.HapticManager
import com.mdaopay.app.core.ui.components.MDAOAmountInput
import com.mdaopay.app.core.ui.components.MDAOBottomSheet
import com.mdaopay.app.core.ui.components.MDAOButton
import com.mdaopay.app.core.ui.components.MDAOInputField
import com.mdaopay.app.core.ui.components.MDAOTopBar
import com.mdaopay.app.core.ui.components.SecureScreen
import com.mdaopay.app.core.ui.components.SoundManager
import com.mdaopay.app.core.ui.components.findFragmentActivity
import com.mdaopay.app.core.ui.theme.extended
import com.mdaopay.app.core.ui.theme.MarsMono
import kotlinx.coroutines.delay
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    biometricManager: BiometricAuthManager,
    viewModel: SendViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val d = MaterialTheme.extended.themeColors
    val a = MaterialTheme.extended.accent

    SecureScreen()

    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result: ScanIntentResult ->
        result.contents?.let { contents ->
            HapticManager.click()
            SoundManager.playScan()
            viewModel.onNicknameChanged(contents)
            viewModel.onRecipientConfirmed(contents)
        }
    }

    LaunchedEffect(state) {
        if (state is SendState.Success) {
            HapticManager.sendTransaction()
            SoundManager.playSend()
            delay(1500)
            onSuccess()
        }
        if (state is SendState.Error) {
            HapticManager.error()
            SoundManager.playError()
        }
        if (state is SendState.Queued) {
            HapticManager.light()
            delay(2000)
            onSuccess()
        }
    }

    val currentStep = when (state) {
        is SendState.Idle -> 1
        is SendState.RecipientInput -> 1
        is SendState.AmountInput -> 2
        is SendState.Confirmation -> 3
        is SendState.Processing -> 3
        is SendState.Success -> 4
        is SendState.Queued -> 4
        is SendState.Error -> {
            val e = state as SendState.Error
            when (e.failedState) {
                is SendState.Confirmation -> 3
                is SendState.AmountInput -> 2
                else -> 1
            }
        }
        else -> 1
    }

    val topbarTitle = when (currentStep) {
        1 -> "\u041E\u0442\u043F\u0440\u0430\u0432\u0438\u0442\u044C"
        2 -> "\u0421\u0443\u043C\u043C\u0430"
        3 -> "\u041F\u043E\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043D\u0438\u0435"
        4 -> "\u0413\u043E\u0442\u043E\u0432\u043E"
        else -> "\u041E\u0442\u043F\u0440\u0430\u0432\u0438\u0442\u044C"
    }

    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 6.dp)
        ) {
            MDAOTopBar(
                title = topbarTitle,
                onBack = {
                    if (currentStep == 1) onBack()
                    else when (state) {
                        is SendState.AmountInput -> viewModel.onReset()
                        is SendState.Confirmation -> viewModel.onBackToAmount()
                        else -> onBack()
                    }
                }
            )

            if (currentStep < 4) {
                Spacer(modifier = Modifier.height(4.dp))
                StepDots(currentStep = currentStep, totalSteps = 3)
                Spacer(modifier = Modifier.height(14.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (val s = state) {
                    is SendState.Idle -> RecipientStep(
                        nickname = "",
                        error = null,
                        onNicknameChanged = { viewModel.onNicknameChanged(it) },
                        onConfirmed = { viewModel.onRecipientConfirmed(it) },
                        onScanQr = { qrLauncher.launch(ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setOrientationLocked(true)
                            setPrompt("\u041E\u0442\u0441\u043A\u0430\u043D\u0438\u0440\u0443\u0439\u0442\u0435 \u0430\u0434\u0440\u0435\u0441 \u043A\u043E\u0448\u0435\u043B\u044C\u043A\u0430")
                        })}
                    )
                    is SendState.RecipientInput -> RecipientStep(
                        nickname = s.nickname,
                        error = s.error,
                        onNicknameChanged = { viewModel.onNicknameChanged(it) },
                        onConfirmed = { viewModel.onRecipientConfirmed(it) },
                        onScanQr = { qrLauncher.launch(ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setOrientationLocked(true)
                            setPrompt("\u041E\u0442\u0441\u043A\u0430\u043D\u0438\u0440\u0443\u0439\u0442\u0435 \u0430\u0434\u0440\u0435\u0441 \u043A\u043E\u0448\u0435\u043B\u044C\u043A\u0430")
                        })}
                    )
                    is SendState.AmountInput -> AmountStep(
                        nickname = s.nickname,
                        amount = s.amount,
                        error = s.error,
                        onAmountChanged = { viewModel.onAmountChanged(it) },
                        onConfirmed = { viewModel.onAmountConfirmed(it) },
                        onBack = { viewModel.onReset() }
                    )
                    is SendState.Processing -> ProcessingStep()
                    is SendState.Success -> ResultStep(
                        txHash = s.txHash,
                        amount = BigDecimal.ZERO,
                        recipientName = "",
                        onDone = onSuccess
                    )
                    is SendState.Error -> ErrorStep(
                        message = s.message,
                        retryable = s.retryable,
                        onRetry = { viewModel.onRetry() },
                        onBack = onBack
                    )
                    is SendState.Queued -> QueuedStep(
                        nickname = s.nickname,
                        amount = s.amount
                    )
                    else -> {}
                }
            }

            val isConfirmation = state is SendState.Confirmation
            if (isConfirmation) {
                val s = state as SendState.Confirmation
                Spacer(modifier = Modifier.height(12.dp))
                MDAOButton(
                    text = "\u041F\u043E\u0434\u0442\u0432\u0435\u0440\u0434\u0438\u0442\u044C \u0438 \u043E\u0442\u043F\u0440\u0430\u0432\u0438\u0442\u044C",
                    onClick = {
                        val activity = context.findFragmentActivity()
                        if (activity != null) {
                            if (s.amount >= BigDecimal("1000")) {
                                biometricManager.authenticateHighRisk(
                                    activity = activity,
                                    title = "Подтверждение крупного платежа",
                                    subtitle = "Отправить ${s.amount.toDisplayAmount()} USDT — высокорисковая операция",
                                    onResult = { result ->
                                        when (result) {
                                            is Result.Success -> viewModel.onSendConfirmed()
                                            is Result.Error -> viewModel.onBiometricFailed()
                                            else -> {}
                                        }
                                    }
                                )
                            } else {
                                biometricManager.authenticate(
                                    activity = activity,
                                    title = "Подтверждение платежа",
                                    subtitle = "Отправить ${s.amount.toDisplayAmount()} USDT",
                                    onResult = { result ->
                                        when (result) {
                                            is Result.Success -> viewModel.onSendConfirmed()
                                            is Result.Error -> viewModel.onBiometricFailed()
                                            else -> {}
                                        }
                                    }
                                )
                            }
                        }
                    }
                )
            }

            if (state is SendState.Success) {
                val s = state as SendState.Success
                Spacer(modifier = Modifier.height(8.dp))
                MDAOButton(
                    text = "\u041F\u043E\u0441\u043C\u043E\u0442\u0440\u0435\u0442\u044C \u0442\u0440\u0430\u043D\u0437\u0430\u043A\u0446\u0438\u044E",
                    onClick = { /* TODO(navigation-team): navigate to tx detail */ }
                )
                Spacer(modifier = Modifier.height(8.dp))
                MDAOButton(
                    text = "\u0413\u043E\u0442\u043E\u0432\u043E",
                    onClick = onSuccess
                )
            }
        }
    }

    if (state is SendState.Confirmation) {
        val s = state as SendState.Confirmation
        MDAOBottomSheet(
            visible = true,
            onDismiss = { viewModel.onBackToAmount() },
            title = "\u041F\u0440\u043E\u0432\u0435\u0440\u043A\u0430"
        ) {
            ConfirmationSheetContent(state = s)
        }
    }
}

@Composable
private fun StepDots(currentStep: Int, totalSteps: Int) {
    val d = MaterialTheme.extended.themeColors
    val a = MaterialTheme.extended.accent
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(totalSteps) { index ->
            val step = index + 1
            val isActive = step == currentStep
            val isDone = step < currentStep
            val color = if (isDone || isActive) a else d.tile
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun RecipientStep(
    nickname: String,
    error: String?,
    onNicknameChanged: (String) -> Unit,
    onConfirmed: (String) -> Unit,
    onScanQr: () -> Unit
) {
    var input by remember(nickname) { mutableStateOf(nickname) }
    val d = MaterialTheme.extended.themeColors
    val a = MaterialTheme.extended.accent

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(d.tile)
                .shadow(4.dp, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = a,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "\u041A\u043E\u043C\u0443 \u043E\u0442\u043F\u0440\u0430\u0432\u043B\u044F\u0435\u043C?",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = d.text,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "\u0412\u0432\u0435\u0434\u0438\u0442\u0435 \u0438\u043C\u044F \u043F\u043E\u043B\u044C\u0437\u043E\u0432\u0430\u0442\u0435\u043B\u044F \u0438\u043B\u0438 \u0430\u0434\u0440\u0435\u0441 \u043A\u043E\u0448\u0435\u043B\u044C\u043A\u0430",
            fontSize = 13.sp,
            color = d.text2,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        MDAOInputField(
            value = input,
            onValueChange = {
                input = it
                onNicknameChanged(it)
            },
            placeholder = "@username \u0438\u043B\u0438 0x...",
            error = error ?: "",
            suffix = {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(a.copy(alpha = 0.1f))
                        .clickable { onScanQr() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QrCodeScanner,
                        contentDescription = "\u0421\u043A\u0430\u043D\u0438\u0440\u043E\u0432\u0430\u0442\u044C QR",
                        tint = a,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            affix = {
                Text(
                    text = "@",
                    fontSize = 14.sp,
                    color = d.text2,
                    modifier = Modifier.padding(start = 14.dp)
                )
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        MDAOButton(
            text = "\u041F\u0440\u043E\u0434\u043E\u043B\u0436\u0438\u0442\u044C",
            onClick = { onConfirmed(input) },
            enabled = input.isNotBlank()
        )
    }
}

@Composable
private fun AmountStep(
    nickname: String,
    amount: BigDecimal,
    error: String?,
    onAmountChanged: (String) -> Unit,
    onConfirmed: (BigDecimal) -> Unit,
    onBack: () -> Unit
) {
    var input by remember(amount) { mutableStateOf(if (amount > BigDecimal.ZERO) amount.toPlainString() else "") }
    val d = MaterialTheme.extended.themeColors
    val a = MaterialTheme.extended.accent

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(d.tile)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(a.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = nickname.first().uppercaseChar().toString(),
                    color = a,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = nickname,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = d.text
                )
                Text(
                    text = "@$nickname",
                    fontSize = 11.sp,
                    fontFamily = MarsMono,
                    color = d.text2
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        MDAOAmountInput(
            value = input,
            onValueChange = {
                val sanitized = it.replace(",", ".").filter { c -> c.isDigit() || c == '.' }
                if (sanitized.count { c -> c == '.' } <= 1) {
                    input = sanitized
                    onAmountChanged(sanitized)
                }
            },
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "\u2248 \$0.00",
            fontSize = 18.sp,
            color = d.text2
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(d.tile)
                .clickable {
                    val maxAmount = BigDecimal("1250.50")
                    input = maxAmount.toPlainString()
                    onAmountChanged(maxAmount.toPlainString())
                }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "\u0414\u043E\u0441\u0442\u0443\u043F\u043D\u043E:",
                fontSize = 12.sp,
                color = d.text2
            )
            Text(
                text = "1 250.50",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = MarsMono,
                color = d.text
            )
            Text(
                text = "USDT",
                fontSize = 12.sp,
                color = d.text2
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("25%", "50%", "75%", "MAX").forEach { label ->
                val percent = when (label) {
                    "25%" -> 0.25
                    "50%" -> 0.50
                    "75%" -> 0.75
                    else -> 1.0
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(d.tile)
                        .clickable {
                            val maxAmount = BigDecimal("1250.50")
                            val value = (maxAmount * BigDecimal.valueOf(percent)).setScale(2, java.math.RoundingMode.DOWN)
                            input = value.toPlainString()
                            onAmountChanged(value.toPlainString())
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = d.text
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(d.card)
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "\u041A\u043E\u043C\u0438\u0441\u0441\u0438\u044F \u0441\u0435\u0442\u0438", fontSize = 12.sp, color = d.text2)
                    Text(text = "0.1 USDT", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = MarsMono, color = d.text)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "\u0418\u0442\u043E\u0433\u043E", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = d.text2)
                    Text(text = "0.00 USDT", fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = MarsMono, color = a)
                }
            }
        }

        if (error != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = error, color = MaterialTheme.extended.danger, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MDAOButton(
                text = "\u041D\u0430\u0437\u0430\u0434",
                onClick = onBack,
                modifier = Modifier.weight(1f)
            )
            MDAOButton(
                text = "\u0414\u0430\u043B\u0435\u0435",
                onClick = {
                    val parsed = input.replace(",", ".").toBigDecimalOrNull()
                    if (parsed != null) onConfirmed(parsed)
                },
                enabled = input.isNotBlank(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ConfirmationSheetContent(state: SendState.Confirmation) {
    val d = MaterialTheme.extended.themeColors
    val a = MaterialTheme.extended.accent

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(d.card)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(a.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "\u24C8", color = a, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "${state.amount.toDisplayAmount()} USDT",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MarsMono,
                    color = d.text,
                    letterSpacing = (-1.5).sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "\u2248 \$${state.amount.toDisplayAmount()}",
                    fontSize = 14.sp,
                    color = d.text2
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(d.tile),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "\u2193", color = d.text2, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = state.nickname,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = d.text
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.address.take(10) + "\u2026" + state.address.takeLast(4),
                    fontSize = 11.sp,
                    color = d.text2,
                    fontFamily = MarsMono
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(d.card)
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "\u041E\u0442\u043F\u0440\u0430\u0432\u0438\u0442\u0435\u043B\u044C", fontSize = 12.sp, color = d.text2)
                    Text(text = "@\u2026", fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = MarsMono, color = d.text)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "\u0421\u0435\u0442\u044C", fontSize = 12.sp, color = d.text2)
                    Text(text = "Sepolia", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = d.text)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "\u0421\u0443\u043C\u043C\u0430", fontSize = 12.sp, color = d.text2)
                    Text(text = "${state.amount.toDisplayAmount()} USDT", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = MarsMono, color = d.text)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "\u041A\u043E\u043C\u0438\u0441\u0441\u0438\u044F", fontSize = 12.sp, color = d.text2)
                    Text(text = "${state.feeUsd.toDisplayAmount()} USDT", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = MarsMono, color = d.text)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "\u0418\u0442\u043E\u0433\u043E \u043A \u0441\u043F\u0438\u0441\u0430\u043D\u0438\u044E", fontSize = 12.sp, color = d.text2)
                    Text(text = "${(state.amount + state.feeUsd).toDisplayAmount()} USDT", fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = MarsMono, color = a)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "\u041F\u0440\u043E\u0432\u0435\u0440\u044C\u0442\u0435 \u0434\u0435\u0442\u0430\u043B\u0438. \u0422\u0440\u0430\u043D\u0437\u0430\u043A\u0446\u0438\u044F \u043D\u0435\u043E\u0431\u0440\u0430\u0442\u0438\u043C\u0430 \u043F\u043E\u0441\u043B\u0435 \u043F\u043E\u0434\u0442\u0432\u0435\u0440\u0436\u0434\u0435\u043D\u0438\u044F.",
            fontSize = 11.sp,
            color = d.text2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun ProcessingStep() {
    val d = MaterialTheme.extended.themeColors
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(80.dp))
        CircularProgressIndicator(
            color = MaterialTheme.extended.accent,
            strokeWidth = 3.dp,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "\u041E\u0442\u043F\u0440\u0430\u0432\u043A\u0430\u2026",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = d.text
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "\u0422\u0440\u0430\u043D\u0437\u0430\u043A\u0446\u0438\u044F \u043E\u0431\u0440\u0430\u0431\u0430\u0442\u044B\u0432\u0430\u0435\u0442\u0441\u044F \u0441\u0435\u0442\u044C\u044E",
            fontSize = 13.sp,
            color = d.text2
        )
    }
}

@Composable
private fun ResultStep(
    txHash: String,
    amount: BigDecimal,
    recipientName: String,
    onDone: () -> Unit
) {
    val d = MaterialTheme.extended.themeColors
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.extended.success)
                .shadow(14.dp, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "\u2713", color = Color.White, fontSize = 50.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "\u041E\u0442\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u043E",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = d.text
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = txHash.take(10) + "\u2026" + txHash.takeLast(4),
            fontSize = 12.sp,
            color = d.text2,
            fontFamily = MarsMono
        )
    }
}

@Composable
private fun QueuedStep(nickname: String, amount: BigDecimal) {
    val d = MaterialTheme.extended.themeColors
    val a = MaterialTheme.extended.accent
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(80.dp))
        Icon(
            imageVector = Icons.Rounded.Schedule,
            contentDescription = "\u0412 \u043E\u0447\u0435\u0440\u0435\u0434\u0438",
            tint = a,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "\u041F\u043B\u0430\u0442\u0451\u0436 \u043F\u043E\u0441\u0442\u0430\u0432\u043B\u0435\u043D \u0432 \u043E\u0447\u0435\u0440\u0435\u0434\u044C",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = a,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${amount.toDisplayAmount()} USDT \u2192 @$nickname",
            fontSize = 14.sp,
            color = d.text2,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "\u0422\u0440\u0430\u043D\u0437\u0430\u043A\u0446\u0438\u044F \u0431\u0443\u0434\u0435\u0442 \u043E\u0442\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0430 \u0430\u0432\u0442\u043E\u043C\u0430\u0442\u0438\u0447\u0435\u0441\u043A\u0438\n\u043F\u0440\u0438 \u043F\u043E\u044F\u0432\u043B\u0435\u043D\u0438\u0438 \u0441\u0435\u0442\u0438",
            fontSize = 13.sp,
            color = d.text2,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ErrorStep(
    message: String,
    retryable: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    val d = MaterialTheme.extended.themeColors
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(80.dp))
        Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = "\u041E\u0448\u0438\u0431\u043A\u0430",
            tint = MaterialTheme.extended.danger,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "\u041D\u0435 \u0443\u0434\u0430\u043B\u043E\u0441\u044C \u043E\u0442\u043F\u0440\u0430\u0432\u0438\u0442\u044C",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.extended.danger
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontSize = 13.sp,
            color = d.text2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        if (retryable) {
            MDAOButton(text = "\u041F\u043E\u043F\u0440\u043E\u0431\u043E\u0432\u0430\u0442\u044C \u0441\u043D\u043E\u0432\u0430", onClick = onRetry)
            Spacer(modifier = Modifier.height(12.dp))
        }
        MDAOButton(text = "\u0412\u0435\u0440\u043D\u0443\u0442\u044C\u0441\u044F", onClick = onBack)
    }
}
