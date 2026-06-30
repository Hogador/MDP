package com.mdaopay.app.core.ui.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import com.mdaopay.app.core.ui.motion.MDAOTween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mdaopay.app.core.common.AppError
import com.mdaopay.app.core.common.Result
import com.mdaopay.app.core.common.toUserMessage
import com.mdaopay.app.core.security.AppLockManager
import com.mdaopay.app.core.security.BiometricAuthManager
import com.mdaopay.app.core.ui.theme.DarkBackground
import com.mdaopay.app.core.ui.theme.DarkOnSurfaceMuted
import com.mdaopay.app.core.ui.theme.ErrorRed
import com.mdaopay.app.core.ui.theme.MDAOGlow
import com.mdaopay.app.core.ui.theme.MDAOPurple

fun Context.findFragmentActivity(): FragmentActivity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is FragmentActivity) return context
        context = context.baseContext
    }
    return null
}

@Composable
fun BiometricLockOverlay(
    appLockManager: AppLockManager,
    biometricManager: BiometricAuthManager,
    content: @Composable () -> Unit
) {
    val isLocked by appLockManager.isLocked.collectAsState()
    val isOnboarded by appLockManager.isOnboarded.collectAsState()

    if (isLocked && isOnboarded) {
        BiometricLockScreen(
            biometricManager = biometricManager,
            onUnlock = { appLockManager.unlock() }
        )
    } else {
        content()
    }
}

@Composable
private fun BiometricLockScreen(
    biometricManager: BiometricAuthManager,
    onUnlock: () -> Unit
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<LockState>(LockState.Ready) }

    val infiniteTransition = rememberInfiniteTransition(label = "lockPulse")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = MDAOTween.slow,
            repeatMode = RepeatMode.Reverse
        ),
        label = "lockRingScale"
    )

    fun authenticate() {
        state = LockState.Authenticating
        val activity = context.findFragmentActivity()
        if (activity == null) {
            state = LockState.Error("Ошибка инициализации")
            return
        }
        biometricManager.authenticate(
            activity = activity,
            title = "MDAO Pay заблокирован",
            subtitle = "Подтверди личность для доступа",
            onResult = { result ->
                state = when (result) {
                    is Result.Success -> {
                        onUnlock()
                        LockState.Unlocked
                    }
                    is Result.Error -> when (result.error) {
                        AppError.BiometricCancelled -> LockState.Ready
                        AppError.BiometricNotAvailable -> LockState.Unavailable
                        else -> LockState.Error(result.error.toUserMessage())
                    }
                    else -> LockState.Ready
                }
            }
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnAuthenticate by rememberUpdatedState(::authenticate)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentOnAuthenticate()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            currentOnAuthenticate()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
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
                        .background(MDAOGlow, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(MDAOPurple.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Заблокировано",
                        tint = MDAOPurple,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "MDAO Pay заблокирован",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Подтверди личность, чтобы продолжить",
                style = MaterialTheme.typography.bodyLarge,
                color = DarkOnSurfaceMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            AnimatedVisibility(
                visible = state is LockState.Error,
                enter = fadeIn(), exit = fadeOut()
            ) {
                val errMsg = (state as? LockState.Error)?.message ?: ""
                Text(
                    text = errMsg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ErrorRed,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            MDAOButton(
                text = when (state) {
                    LockState.Authenticating -> "Проверка..."
                    is LockState.Error -> "Попробовать снова"
                    LockState.Unavailable -> "Продолжить"
                    else -> "Разблокировать"
                },
                onClick = {
                    when (state) {
                        LockState.Unavailable -> onUnlock()
                        else -> authenticate()
                    }
                },
                isLoading = state is LockState.Authenticating
            )
        }
    }
}

private sealed class LockState {
    data object Ready : LockState()
    data object Authenticating : LockState()
    data object Unlocked : LockState()
    data object Unavailable : LockState()
    data class Error(val message: String) : LockState()
}
