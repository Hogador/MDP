package com.mdaopay.app.feature.onboarding.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mdaopay.app.BuildConfig
import com.mdaopay.app.core.common.AppError
import com.mdaopay.app.core.common.Result
import com.mdaopay.app.core.ui.components.MDAOButton
import com.mdaopay.app.core.ui.components.findFragmentActivity
import com.mdaopay.app.core.ui.theme.MarsFont
import com.mdaopay.app.core.ui.theme.extended

@Composable
fun OnboardingBiometricScreen(
    onSuccess: () -> Unit,
    viewModel: OnboardingBiometricViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    LaunchedEffect(Unit) {
        viewModel.checkBiometricAvailability()
    }

    LaunchedEffect(uiState) {
        if (uiState is BiometricUiState.AutoSkipped || uiState is BiometricUiState.Success) {
            onSuccess()
        }
    }

    LaunchedEffect(uiState) {
        if (uiState is BiometricUiState.Authenticating) {
            val activity = context.findFragmentActivity()
            if (activity == null) {
                viewModel.onBiometricResult(Result.Error(AppError.BiometricNotAvailable))
                return@LaunchedEffect
            }
            viewModel.biometricManager.authenticate(
                activity = activity,
                title = "Добро пожаловать в MDAO Pay",
                subtitle = "Подтвердите личность для продолжения",
                onResult = { viewModel.onBiometricResult(it) }
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ring")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tc.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(ringScale)
                        .clip(CircleShape)
                        .background(extended.accentSoft)
                )
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(extended.accent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "\uD83D\uDC45",
                        fontSize = 40.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "Защити свой кошелёк",
                fontFamily = MarsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = tc.text,
                textAlign = TextAlign.Center,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Используй Face ID или отпечаток пальца.\nТвои ключи хранятся только на устройстве.",
                fontFamily = MarsFont,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = tc.text2,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            AnimatedVisibility(
                visible = uiState is BiometricUiState.Error,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val err = uiState as? BiometricUiState.Error
                Text(
                    text = err?.message ?: "",
                    fontFamily = MarsFont,
                    fontSize = 13.sp,
                    color = extended.danger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )
            }

            AnimatedVisibility(visible = uiState is BiometricUiState.BiometricUnavailable) {
                Text(
                    text = "Биометрия недоступна. Настрой в Настройках позже.",
                    fontFamily = MarsFont,
                    fontSize = 13.sp,
                    color = tc.text2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            val buttonText = when (uiState) {
                is BiometricUiState.Error ->
                    if ((uiState as BiometricUiState.Error).canRetry) "Попробовать снова"
                    else "Продолжить"
                is BiometricUiState.Authenticating -> "Ожидание..."
                is BiometricUiState.AutoSkipped -> "Переходим..."
                else -> "Подтвердить"
            }

            MDAOButton(
                text = buttonText,
                onClick = {
                    when (uiState) {
                        is BiometricUiState.Error -> viewModel.onRetry()
                        is BiometricUiState.BiometricUnavailable -> onSuccess()
                        else -> viewModel.onAuthenticateClick()
                    }
                },
                isLoading = uiState is BiometricUiState.Authenticating || uiState is BiometricUiState.AutoSkipped,
                enabled = true
            )

            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Пропустить (debug)",
                    fontFamily = MarsFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = tc.text2,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
