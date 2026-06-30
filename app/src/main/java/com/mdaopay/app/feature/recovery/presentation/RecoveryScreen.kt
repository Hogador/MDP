package com.mdaopay.app.feature.recovery.presentation

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mdaopay.app.core.common.Result
import com.mdaopay.app.core.common.copyToClipboard
import com.mdaopay.app.core.security.BiometricAuthManager
import com.mdaopay.app.core.ui.components.MDAOButton
import com.mdaopay.app.core.ui.components.MDAOInputField
import com.mdaopay.app.core.ui.components.SecureScreen
import com.mdaopay.app.core.ui.components.findFragmentActivity
import com.mdaopay.app.core.ui.theme.MarsFont
import com.mdaopay.app.core.ui.theme.MarsMono
import com.mdaopay.app.core.ui.theme.extended
import com.mdaopay.app.ui.recovery.TrustConstellation

@Composable
fun RecoveryScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit = {},
    biometricManager: BiometricAuthManager,
    inviteId: String? = null,
    viewModel: RecoveryViewModel = hiltViewModel()
) {
    val mnemonic by viewModel.mnemonic.collectAsState()
    val revealed by viewModel.revealed.collectAsState()
    val tab by viewModel.tab.collectAsState()
    val importError by viewModel.importError.collectAsState()
    val importSuccess by viewModel.importSuccess.collectAsState()
    val hasShare1 by viewModel.hasShare1.collectAsState()
    val hasShare2 by viewModel.hasShare2.collectAsState()
    val hasShare3 by viewModel.hasShare3.collectAsState()
    val hasShare4 by viewModel.hasShare4.collectAsState()
    val hasPasskey by viewModel.hasPasskey.collectAsState()
    val share4ExportHex by viewModel.share4ExportHex.collectAsState()
    val share4ImportError by viewModel.share4ImportError.collectAsState()
    val share4ImportSuccess by viewModel.share4ImportSuccess.collectAsState()
    val backupState by viewModel.backupState.collectAsState()
    val isHermitMode by viewModel.isHermitMode.collectAsState()
    val share3ExportHex by viewModel.share3ExportHex.collectAsState()
    val share3ImportError by viewModel.share3ImportError.collectAsState()
    val share3ImportSuccess by viewModel.share3ImportSuccess.collectAsState()
    val coldDevicePinSet by viewModel.coldDevicePinSet.collectAsState()
    val context = LocalContext.current
    val extended = MaterialTheme.extended
    val tc = extended.themeColors
    var copied by remember { mutableStateOf(false) }

    SecureScreen()

    Box(modifier = Modifier.fillMaxSize().background(tc.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(tc.tile),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "\u2190", fontSize = 20.sp, color = tc.text)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Безопасность",
                    fontFamily = MarsFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = tc.text,
                    letterSpacing = (-0.5).sp
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // ponytail: SHOW tab removed — seed phrase contradicts PRD §3.2/ADR 001. Recovery via SSS only.
                listOf("Импорт", "Бекап").plus(
                    if (!isHermitMode) listOf("Guardians") else emptyList()
                ).forEach { label ->
                    val isActive = when (label) {
                        "Импорт" -> tab == RecoveryViewModel.Tab.IMPORT
                        "Бекап" -> tab == RecoveryViewModel.Tab.BACKUP
                        "Guardians" -> tab == RecoveryViewModel.Tab.GUARDIANS
                        else -> false
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isActive) extended.accent else tc.surface)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontFamily = MarsFont,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp,
                            color = if (isActive) Color.White else tc.text2
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                WarningCard()

                Spacer(modifier = Modifier.height(24.dp))

                when (tab) {
                    RecoveryViewModel.Tab.IMPORT -> ImportTab(
                        error = importError,
                        success = importSuccess,
                        onImport = { viewModel.importMnemonic(it) },
                        biometricManager = biometricManager
                    )
                    RecoveryViewModel.Tab.BACKUP -> BackupTab(
                        hasShare1 = hasShare1,
                        hasShare2 = hasShare2,
                        hasShare3 = hasShare3,
                        hasShare4 = hasShare4,
                        hasPasskey = hasPasskey,
                        isHermitMode = isHermitMode,
                        share4ExportHex = share4ExportHex,
                        share4ImportError = share4ImportError,
                        share4ImportSuccess = share4ImportSuccess,
                        share3ExportHex = share3ExportHex,
                        share3ImportError = share3ImportError,
                        share3ImportSuccess = share3ImportSuccess,
                        coldDevicePinSet = coldDevicePinSet,
                        state = backupState,
                        onCreateBackup = { viewModel.createBackup() },
                        onCreatePasskeyBackup = { viewModel.setupPasskeyBackup() },
                        onExportShare4 = { viewModel.exportShare4() },
                        onHideShare4Export = { viewModel.hideShare4Export() },
                        onImportShare4 = { viewModel.importShare4FromHex(it) },
                        onExportShare3 = { viewModel.exportShare3() },
                        onHideShare3Export = { viewModel.hideShare3Export() },
                        onImportShare3 = { viewModel.importShare3FromHex(it) },
                        onStartRecovery = { viewModel.startRecovery() },
                        onBiometricSuccess = { viewModel.onBiometricSuccess() },
                        onReset = { viewModel.resetBackupState() },
                        onComplete = onComplete,
                        onSetColdDevicePin = { viewModel.setColdDevicePin(it) },
                        biometricManager = biometricManager
                    )
                    RecoveryViewModel.Tab.GUARDIANS -> GuardiansTab(
                        myGuardians = viewModel.myGuardians.collectAsState().value,
                        invites = viewModel.myInvites.collectAsState().value,
                        error = viewModel.guardianError.collectAsState().value,
                        loading = viewModel.guardianLoading.collectAsState().value,
                        onInviteGuardian = { label, idx -> viewModel.inviteGuardian(label, idx) },
                        onRemoveGuardian = { viewModel.removeGuardian(it) }
                    )
                    else -> BackupTab(
                        hasShare1 = hasShare1,
                        hasShare2 = hasShare2,
                        hasShare3 = hasShare3,
                        hasShare4 = hasShare4,
                        hasPasskey = hasPasskey,
                        isHermitMode = isHermitMode,
                        share4ExportHex = share4ExportHex,
                        share4ImportError = share4ImportError,
                        share4ImportSuccess = share4ImportSuccess,
                        share3ExportHex = share3ExportHex,
                        share3ImportError = share3ImportError,
                        share3ImportSuccess = share3ImportSuccess,
                        coldDevicePinSet = coldDevicePinSet,
                        state = backupState,
                        onCreateBackup = { viewModel.createBackup() },
                        onCreatePasskeyBackup = { viewModel.setupPasskeyBackup() },
                        onExportShare4 = { viewModel.exportShare4() },
                        onHideShare4Export = { viewModel.hideShare4Export() },
                        onImportShare4 = { viewModel.importShare4FromHex(it) },
                        onExportShare3 = { viewModel.exportShare3() },
                        onHideShare3Export = { viewModel.hideShare3Export() },
                        onImportShare3 = { viewModel.importShare3FromHex(it) },
                        onStartRecovery = { viewModel.startRecovery() },
                        onBiometricSuccess = { viewModel.onBiometricSuccess() },
                        onReset = { viewModel.resetBackupState() },
                        onComplete = onComplete,
                        onSetColdDevicePin = { viewModel.setColdDevicePin(it) },
                        biometricManager = biometricManager
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun BackupTab(
    hasShare1: Boolean,
    hasShare2: Boolean,
    hasShare3: Boolean,
    hasShare4: Boolean,
    hasPasskey: Boolean,
    isHermitMode: Boolean,
    share4ExportHex: String?,
    share4ImportError: String?,
    share4ImportSuccess: Boolean,
    share3ExportHex: String?,
    share3ImportError: String?,
    share3ImportSuccess: Boolean,
    coldDevicePinSet: Boolean,
    state: RecoveryViewModel.BackupState,
    onCreateBackup: () -> Unit,
    onCreatePasskeyBackup: () -> Unit,
    onExportShare4: () -> Unit,
    onHideShare4Export: () -> Unit,
    onImportShare4: (String) -> Unit,
    onExportShare3: () -> Unit,
    onHideShare3Export: () -> Unit,
    onImportShare3: (String) -> Unit,
    onStartRecovery: () -> Unit,
    onBiometricSuccess: () -> Unit,
    onReset: () -> Unit,
    onComplete: () -> Unit,
    onSetColdDevicePin: (String) -> Unit,
    biometricManager: BiometricAuthManager
) {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors
    val context = LocalContext.current

    val shareCount = if (isHermitMode) {
        listOf(hasShare1, hasShare2, hasShare3).count { it }
    } else {
        listOf(hasShare1, hasShare2, hasShare3, hasShare4).count { it }
    }
    val totalShares = if (isHermitMode) 3 else 4
    val requiredShares = if (isHermitMode) 2 else 3

    if (isHermitMode && !coldDevicePinSet) {
        HermitWarningCard()
        Spacer(modifier = Modifier.height(16.dp))
        ColdDevicePinSetupCard(onSetPin = onSetColdDevicePin)
        Spacer(modifier = Modifier.height(16.dp))
    }

    ShareStatusRow(label = "Устройство (Keystore)", exists = hasShare1)
    Spacer(modifier = Modifier.height(8.dp))
    ShareStatusRow(label = "Устройство (Passkey PRF)", exists = hasShare2)
    Spacer(modifier = Modifier.height(8.dp))
    ShareStatusRow(
        label = if (isHermitMode) "Холодное устройство (PIN)" else "Устройство (Keystore + Bio)",
        exists = hasShare3
    )
    if (!isHermitMode) {
        Spacer(modifier = Modifier.height(8.dp))
        ShareStatusRow(label = "Guardian (экспорт)", exists = hasShare4)
    }
    if (hasPasskey) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "\u2713 Passkey настроен",
            fontFamily = MarsFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = extended.success
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    when (state) {
        is RecoveryViewModel.BackupState.Idle -> {
            if (shareCount >= requiredShares) {
                Text(
                    text = "Резервная копия создана ($shareCount/$totalShares). Любые $requiredShares из $totalShares частей восстановят кошелёк.",
                    fontFamily = MarsFont,
                    fontSize = 13.sp,
                    color = tc.text2,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (isHermitMode) {
                    if (share3ExportHex == null) {
                        MDAOButton(text = "Экспортировать ключ холодного устройства", onClick = onExportShare3)
                    }
                } else {
                    if (share4ExportHex == null) {
                        MDAOButton(text = "Экспортировать guardian-ключ", onClick = onExportShare4)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                MDAOButton(text = "Восстановить кошелёк", onClick = onStartRecovery)
                if (!hasPasskey) {
                    Spacer(modifier = Modifier.height(12.dp))
                    MDAOButton(text = "Настроить Passkey (s2)", onClick = onCreatePasskeyBackup)
                }
            } else {
                Text(
                    text = if (isHermitMode) {
                        "Создайте резервную копию. SSS 2-of-3 — любые 2 части восстанавливают кошелёк."
                    } else {
                        "Создайте резервную копию. SSS 3-of-4 — любые 3 части восстанавливают кошелёк."
                    },
                    fontFamily = MarsFont,
                    fontSize = 13.sp,
                    color = tc.text2,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                SecurityExplanationCard(requiredShares = requiredShares, totalShares = totalShares)
                Spacer(modifier = Modifier.height(16.dp))
                MDAOButton(text = "Создать резервную копию", onClick = onCreateBackup)
            }

            if (!isHermitMode && hasShare4 && share4ExportHex != null) {
                Spacer(modifier = Modifier.height(16.dp))
                ShareExportCard(
                    title = "Доля Guardian (Shamir)",
                    description = "1 из 4 частей seed-фразы. Храните отдельно от телефона.",
                    hex = share4ExportHex,
                    onCopy = { context.copyToClipboard(share4ExportHex, "Guardian key") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                MDAOButton(text = "Скрыть", onClick = onHideShare4Export)
            }

            if (isHermitMode && hasShare3 && share3ExportHex != null) {
                Spacer(modifier = Modifier.height(16.dp))
                ShareExportCard(
                    title = "Ключ холодного устройства",
                    description = "Сохраните этот код на втором устройстве. Вместе с телефоном или passkey он восстановит кошелёк.",
                    hex = share3ExportHex,
                    onCopy = { context.copyToClipboard(share3ExportHex, "Cold device key") }
                )
                Spacer(modifier = Modifier.height(8.dp))
                MDAOButton(text = "Скрыть", onClick = onHideShare3Export)
            }

            if (isHermitMode) {
                ShareImportSection(
                    error = share3ImportError,
                    success = share3ImportSuccess,
                    label = "ключ холодного устройства",
                    onImport = onImportShare3
                )
            } else {
                ShareImportSection(
                    error = share4ImportError,
                    success = share4ImportSuccess,
                    label = "guardian-ключ",
                    onImport = onImportShare4
                )
            }
        }
        is RecoveryViewModel.BackupState.Creating -> {
            Text(text = "\u25CC", fontSize = 40.sp, color = extended.accent)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Создание резервной копии (SSS)...",
                fontFamily = MarsFont,
                fontSize = 13.sp,
                color = tc.text2
            )
        }
        is RecoveryViewModel.BackupState.CreatingPasskey -> {
            Text(text = "\u25CC", fontSize = 40.sp, color = extended.accent)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Создание Passkey...",
                fontFamily = MarsFont,
                fontSize = 13.sp,
                color = tc.text2
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Подтвердите создание passkey в системном диалоге",
                fontFamily = MarsFont,
                fontSize = 11.sp,
                color = tc.text2,
                textAlign = TextAlign.Center
            )
        }
        is RecoveryViewModel.BackupState.Created -> {
            Text(text = "\u2713", fontSize = 56.sp, color = extended.success)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Резервная копия создана!",
                fontFamily = MarsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = extended.success,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isHermitMode) {
                    "Любые 2 из 3 частей восстановят кошелёк. Храните ключ холодного устройства в надёжном месте."
                } else {
                    "Любые 3 из 4 частей восстановят кошелёк. Передайте guardian-ключ доверенному лицу."
                },
                fontFamily = MarsFont,
                fontSize = 12.sp,
                color = tc.text2,
                textAlign = TextAlign.Center
            )
            if (isHermitMode && share3ExportHex != null) {
                Spacer(modifier = Modifier.height(16.dp))
                ShareExportCard(
                    title = "Доля холодного устройства (Shamir)",
                    description = "1 из 3 частей seed-фразы. Храните отдельно от телефона.",
                    hex = share3ExportHex,
                    onCopy = { context.copyToClipboard(share3ExportHex, "Cold device key") }
                )
            }
            if (!isHermitMode && share4ExportHex != null) {
                Spacer(modifier = Modifier.height(16.dp))
                ShareExportCard(
                    title = "Доля Guardian (Shamir)",
                    description = "1 из 4 частей seed-фразы.",
                    hex = share4ExportHex,
                    onCopy = { context.copyToClipboard(share4ExportHex, "Guardian key") }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            MDAOButton(text = "Готово", onClick = onReset)
        }
        is RecoveryViewModel.BackupState.NeedBiometric -> {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Ожидание подтверждения...",
                fontFamily = MarsFont,
                fontSize = 13.sp,
                color = tc.text2
            )
        }
        is RecoveryViewModel.BackupState.AuthenticatingPasskey -> {
            Text(text = "\u25CC", fontSize = 40.sp, color = extended.accent)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Аутентификация Passkey...",
                fontFamily = MarsFont,
                fontSize = 13.sp,
                color = tc.text2
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Подтвердите аутентификацию в системном диалоге",
                fontFamily = MarsFont,
                fontSize = 11.sp,
                color = tc.text2,
                textAlign = TextAlign.Center
            )
        }
        is RecoveryViewModel.BackupState.Recovering -> {
            Text(text = "\u25CC", fontSize = 40.sp, color = extended.accent)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Восстановление кошелька...",
                fontFamily = MarsFont,
                fontSize = 13.sp,
                color = tc.text2
            )
        }
        is RecoveryViewModel.BackupState.Success -> {
            Text(text = "\u2713", fontSize = 56.sp, color = extended.success)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Кошелёк восстановлен!",
                fontFamily = MarsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = extended.success,
                letterSpacing = (-0.5).sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            MDAOButton(text = "Продолжить", onClick = onComplete)
        }
        is RecoveryViewModel.BackupState.Error -> {
            Text(text = "\u26A0", fontSize = 56.sp, color = extended.warning)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = state.message,
                fontFamily = MarsFont,
                fontSize = 14.sp,
                color = extended.danger,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            MDAOButton(text = "Попробовать снова", onClick = onReset)
        }
    }
}

@Composable
private fun ShareExportCard(title: String, description: String, hex: String, onCopy: () -> Unit) {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(extended.accentSoft)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                fontFamily = MarsFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = extended.accent
            )
            Text(
                text = description,
                fontFamily = MarsFont,
                fontSize = 11.sp,
                color = tc.text2
            )
            Text(
                text = hex,
                fontFamily = MarsMono,
                fontSize = 12.sp,
                color = tc.text,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(tc.surface).padding(12.dp),
                textAlign = TextAlign.Center
            )
            MDAOButton(text = "Копировать", onClick = onCopy)
        }
    }
}

@Composable
private fun ShareImportSection(
    error: String?,
    success: Boolean,
    label: String,
    onImport: (String) -> Unit
) {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors
    var input by remember { mutableStateOf("") }

    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "Импортировать $label",
        fontFamily = MarsFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = tc.text
    )
    Spacer(modifier = Modifier.height(8.dp))
    MDAOInputField(
        value = input,
        onValueChange = { input = it },
        placeholder = "Вставьте $label",
        error = error ?: ""
    )
    Spacer(modifier = Modifier.height(8.dp))
    MDAOButton(
        text = "Импортировать",
        onClick = { onImport(input) },
        enabled = input.isNotBlank()
    )
    if (success) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "$label импортирован",
            fontFamily = MarsFont,
            fontSize = 13.sp,
            color = extended.success,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ShareStatusRow(label: String, exists: Boolean) {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tc.card)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontFamily = MarsFont,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = tc.text
            )
        }
        Text(
            text = if (exists) "\u2713" else "\u26A0",
            fontSize = 18.sp,
            color = if (exists) extended.success else tc.text2
        )
    }
}

@Composable
private fun SecurityExplanationCard(requiredShares: Int = 3, totalShares: Int = 4) {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(tc.card)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Как работает восстановление?",
                fontFamily = MarsFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = tc.text
            )
            Text(
                text = "Ваша seed-фраза делится на части алгоритмом Шамира (SSS). Никто не видит целую фразу — только отдельные части.",
                fontFamily = MarsFont,
                fontSize = 11.sp,
                color = tc.text2,
                lineHeight = 16.sp
            )
            Text(
                text = "Каждая часть хранится отдельно: на устройстве, в passkey, на холодном устройстве и у guardian'ов.",
                fontFamily = MarsFont,
                fontSize = 11.sp,
                color = tc.text2,
                lineHeight = 16.sp
            )
            Text(
                text = "Для восстановления нужно собрать $requiredShares из $totalShares частей.",
                fontFamily = MarsFont,
                fontSize = 11.sp,
                color = tc.text2,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun WarningCard() {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(extended.warningSoft)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = "\u26A0", fontSize = 24.sp)
            Column {
                Text(
                    text = "Никому не показывайте seed-фразу",
                    fontFamily = MarsFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = extended.warning
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Кто владеет seed-фразой — владеет кошельком.",
                    fontFamily = MarsFont,
                    fontSize = 11.sp,
                    color = tc.text2
                )
            }
        }
    }
}

@Composable
private fun HermitWarningCard() {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(extended.warningSoft)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "\u26A0", fontSize = 24.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Нет guardian'ов — повышенный риск",
                    fontFamily = MarsFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = extended.warning
                )
            }
            Text(
                text = "Если потеряете телефон и passkey — кошелёк не восстановить. Установите PIN для холодного устройства и сохраните ключ отдельно.",
                fontFamily = MarsFont,
                fontSize = 11.sp,
                color = tc.text2
            )
        }
    }
}

@Composable
private fun ColdDevicePinSetupCard(onSetPin: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(tc.card)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "\uD83D\uDD12", fontSize = 24.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Установить PIN холодного устройства",
                    fontFamily = MarsFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = tc.text
                )
            }
            Text(
                text = "PIN-код защищает ключ холодного устройства. Запомните его — сброс невозможен.",
                fontFamily = MarsFont,
                fontSize = 11.sp,
                color = tc.text2
            )
            MDAOInputField(
                value = pin,
                onValueChange = { pin = it.take(6); error = null },
                placeholder = "PIN (4-6 цифр)",
                error = error ?: ""
            )
            MDAOInputField(
                value = confirmPin,
                onValueChange = { confirmPin = it.take(6) },
                placeholder = "Повторите PIN"
            )
            MDAOButton(
                text = "Установить PIN",
                onClick = {
                    if (pin.length < 4) error = "PIN должен быть 4-6 цифр"
                    else if (pin != confirmPin) error = "PIN не совпадает"
                    else onSetPin(pin)
                },
                enabled = pin.isNotBlank() && confirmPin.isNotBlank()
            )
        }
    }
}

@Composable
private fun ImportTab(
    error: String?,
    success: Boolean,
    onImport: (String) -> Unit,
    biometricManager: BiometricAuthManager
) {
    var input by remember { mutableStateOf("") }
    val context = LocalContext.current
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Вставьте seed-фразу для восстановления кошелька",
            fontFamily = MarsFont,
            fontSize = 13.sp,
            color = tc.text2
        )

        Spacer(modifier = Modifier.height(16.dp))

        MDAOInputField(
            value = input,
            onValueChange = { input = it },
            placeholder = "12 или 24 слова через пробел",
            error = error ?: "",
            singleLine = false
        )

        Spacer(modifier = Modifier.height(16.dp))

        MDAOButton(
            text = "Восстановить кошелёк",
            onClick = {
                val activity = context.findFragmentActivity()
                if (activity != null) {
                    biometricManager.authenticateHighRisk(
                        activity = activity,
                        title = "Импорт seed-фразы",
                        subtitle = "Высокорисковая операция — seed-фраза даёт полный доступ к кошельку",
                        onResult = { result ->
                            if (result is Result.Success) {
                                onImport(input)
                            }
                        }
                    )
                }
            },
            enabled = input.isNotBlank()
        )

        if (success) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Кошелёк восстановлен! Перезапустите приложение.",
                fontFamily = MarsFont,
                fontSize = 13.sp,
                color = extended.success,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun GuardiansTab(
    myGuardians: List<com.mdaopay.app.core.guardian.GuardianInfo>,
    invites: List<com.mdaopay.app.core.guardian.GuardianInvite>,
    error: String?,
    loading: Boolean,
    onInviteGuardian: (String, Int) -> Unit,
    onRemoveGuardian: (String) -> Unit
) {
    var guardianLabel by remember { mutableStateOf("") }
    var shareIndex by remember { mutableStateOf(3) }
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    if (loading) {
        Text(text = "\u25CC", fontSize = 40.sp, color = extended.accent)
        return
    }

    error?.let {
        Text(
            text = it,
            fontFamily = MarsFont,
            fontSize = 13.sp,
            color = extended.danger,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    GuardianExplanationCard()
    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Мои guardian'ы",
        fontFamily = MarsFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = tc.text
    )
    Spacer(modifier = Modifier.height(8.dp))

    TrustConstellation(
        guardians = myGuardians,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    Spacer(modifier = Modifier.height(24.dp))

    Text(
        text = "Пригласить guardian'a",
        fontFamily = MarsFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = tc.text
    )
    Spacer(modifier = Modifier.height(8.dp))

    MDAOInputField(
        value = guardianLabel,
        onValueChange = { guardianLabel = it },
        placeholder = "Имя guardian'a",
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MDAOButton(text = "s3 (Guardian A)", onClick = { onInviteGuardian(guardianLabel, 3) }, enabled = guardianLabel.isNotBlank())
        MDAOButton(text = "s4 (Guardian B)", onClick = { onInviteGuardian(guardianLabel, 4) }, enabled = guardianLabel.isNotBlank())
    }

    if (invites.isNotEmpty()) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Исходящие приглашения",
            fontFamily = MarsFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = tc.text2
        )
        invites.forEach { invite ->
            Text(
                text = "${invite.guardianLabel} — ${invite.status}",
                fontFamily = MarsFont,
                fontSize = 12.sp,
                color = tc.text2
            )
        }
    }
}

@Composable
private fun GuardianExplanationCard() {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(tc.card)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Зачем нужны guardian'ы?",
                fontFamily = MarsFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = tc.text
            )
            Text(
                text = "Guardian — это доверенное лицо, которое хранит одну из частей вашего ключа восстановления.",
                fontFamily = MarsFont,
                fontSize = 11.sp,
                color = tc.text2,
                lineHeight = 16.sp
            )
            Text(
                text = "Вы можете пригласить до 2 guardian'ов. Каждый получит свою часть (s3 или s4). Guardian не видит ваш кошелёк и не может переводить средства.",
                fontFamily = MarsFont,
                fontSize = 11.sp,
                color = tc.text2,
                lineHeight = 16.sp
            )
            Text(
                text = "Если вы потеряете телефон, guardian'ы помогут восстановить доступ к кошельку.",
                fontFamily = MarsFont,
                fontSize = 11.sp,
                color = tc.text2,
                lineHeight = 16.sp
            )
        }
    }
}
