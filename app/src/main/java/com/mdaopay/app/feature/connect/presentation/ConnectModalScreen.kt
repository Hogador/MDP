package com.mdaopay.app.feature.connect.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mdaopay.app.core.ui.components.*
import com.mdaopay.app.core.ui.theme.*
import com.mdaopay.app.core.ui.theme.extended

@Composable
fun ConnectModalScreen(
    onDismiss: () -> Unit,
    viewModel: ConnectViewModel = hiltViewModel()
) {
    val dapp by viewModel.dapp.collectAsState()
    val state by viewModel.state.collectAsState()
    val d = MaterialTheme.extended.themeColors
    val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.fragment.app.FragmentActivity

    var showRevokeConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is ConnectState.Connected) {
            // auto-dismiss after short delay
            kotlinx.coroutines.delay(600)
            onDismiss()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(d.card)
            .padding(horizontal = 22.dp)
            .padding(top = 12.dp, bottom = 32.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Handle bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp, 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(d.text3.copy(alpha = 0.4f))
            )
        }

        // dApp header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.extended.accent.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dapp.name.first().toString(),
                    fontFamily = MarsFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.extended.accent
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dapp.name,
                    fontFamily = MarsFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = d.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dapp.url,
                    fontFamily = MarsFont,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = d.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Permissions section
        MDAOListSection(header = "Permissions") {
            dapp.permissions.forEach { perm ->
                MDAOListItem(
                    icon = {
                        Text(
                            text = "\u2713",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.extended.success
                        )
                    },
                    iconVariant = ListIconVariant.Success,
                    title = perm,
                    showChevron = false
                )
            }
        }

        // Spending limit
        if (dapp.spendingLimit != null) {
            Spacer(Modifier.height(12.dp))
            MDAOListSection(header = "Spending Limit") {
                MDAOListItem(
                    icon = {
                        Text(
                            text = "$",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.extended.accent
                        )
                    },
                    iconVariant = ListIconVariant.Accent,
                    title = dapp.spendingLimit!!,
                    showChevron = false
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Notification for connected state
        AnimatedVisibility(
            visible = state is ConnectState.Connected,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            MDAONotification(
                data = NotifData(
                    type = NotifType.SUCCESS,
                    title = "Connected",
                    sub = dapp.name
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Error notification
        if (state is ConnectState.Error) {
            MDAONotification(
                data = NotifData(
                    type = NotifType.ERROR,
                    title = "Connection failed",
                    sub = (state as ConnectState.Error).message,
                    actionLabel = "Dismiss",
                    onAction = { viewModel.dismissError() }
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(8.dp))

        // Action buttons
        if (state !is ConnectState.Connected) {
            MDAOButton(
                text = if (state is ConnectState.Authenticating) "Authenticating..." else "Connect",
                onClick = { activity?.let { viewModel.connect(it) } },
                isLoading = state is ConnectState.Authenticating,
                enabled = state !is ConnectState.Authenticating,
                variant = MDAOButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            MDAOButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = MDAOButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Revoke access (only when connected)
        if (state is ConnectState.Connected) {
            Spacer(Modifier.height(4.dp))
            MDAOButton(
                text = "Revoke Access",
                onClick = { showRevokeConfirm = true },
                variant = MDAOButtonVariant.Danger,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Revoke confirmation
        if (showRevokeConfirm) {
            Spacer(Modifier.height(12.dp))
            MDAOListSection {
                MDAOListItem(
                    icon = {
                        Text(
                            text = "!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.extended.danger
                        )
                    },
                    iconVariant = ListIconVariant.Danger,
                    title = "Revoke access?",
                    subtitle = "${dapp.name} will be disconnected",
                    showChevron = false
                )
            }
            Spacer(Modifier.height(10.dp))
            MDAOButton(
                text = "Confirm Revoke",
                onClick = {
                    showRevokeConfirm = false
                    viewModel.revokeAccess()
                },
                variant = MDAOButtonVariant.Danger,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            MDAOButton(
                text = "Cancel",
                onClick = { showRevokeConfirm = false },
                variant = MDAOButtonVariant.Ghost,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
