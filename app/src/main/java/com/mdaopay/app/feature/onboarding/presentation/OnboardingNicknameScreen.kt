package com.mdaopay.app.feature.onboarding.presentation

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mdaopay.app.core.ui.components.GradientBackground
import com.mdaopay.app.core.ui.components.MDAOButton
import com.mdaopay.app.core.ui.theme.DarkBorder
import com.mdaopay.app.core.ui.theme.DarkOnSurfaceMuted
import com.mdaopay.app.core.ui.theme.DarkSurface
import com.mdaopay.app.core.ui.theme.MDAOPurple

@Composable
fun OnboardingNicknameScreen(
    onContinue: () -> Unit,
    viewModel: OnboardingNicknameViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is NicknameUiState.Ready &&
            (uiState as NicknameUiState.Ready).isConfirmed
        ) {
            onContinue()
        }
    }

    GradientBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            Text(
                text = "Твоё имя\nв MDAO Pay",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 42.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Вместо длинных адресов — простое имя.\nЛюди будут переводить тебе деньги по нему.",
                style = MaterialTheme.typography.bodyLarge,
                color = DarkOnSurfaceMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            when (val state = uiState) {
                is NicknameUiState.Loading -> {
                    CircularProgressIndicator(
                        color = MDAOPurple,
                        modifier = Modifier.size(40.dp)
                    )
                }

                is NicknameUiState.WalletError -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Не удалось создать кошелёк",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = DarkOnSurfaceMuted,
                            textAlign = TextAlign.Center
                        )
                        MDAOButton(
                            text = "Попробовать снова",
                            onClick = { viewModel.generateOptions() }
                        )
                    }
                }

                is NicknameUiState.Ready -> {

                    // ─── Выбранный никнейм ────────────────
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MDAOPurple.copy(alpha = 0.12f)
                        ),
                        border = BorderStroke(1.dp, MDAOPurple.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = state.selected,
                                style = MaterialTheme.typography.displayMedium,
                                color = MDAOPurple,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "твой адрес для получения платежей",
                                style = MaterialTheme.typography.labelMedium,
                                color = DarkOnSurfaceMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // ─── Другие варианты ──────────────────
                    Text(
                        text = "Другие варианты:",
                        style = MaterialTheme.typography.labelMedium,
                        color = DarkOnSurfaceMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )

                    state.options.filter { it != state.selected }.forEach { nickname ->
                        OutlinedCard(
                            onClick = { viewModel.selectNickname(nickname) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.outlinedCardColors(
                                containerColor = DarkSurface
                            ),
                            border = BorderStroke(1.dp, DarkBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = nickname,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Выбрать",
                                    tint = DarkOnSurfaceMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ─── Обновить варианты ────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.generateOptions() }) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Новые варианты",
                                tint = DarkOnSurfaceMuted
                            )
                        }
                        Text(
                            text = "Показать другие варианты",
                            style = MaterialTheme.typography.bodyMedium,
                            color = DarkOnSurfaceMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // ─── Сценарий восстановления ──────────
                    val scenario by viewModel.scenario.collectAsState()
                    Text(
                        text = "Сценарий восстановления",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ScenarioCard(
                        selected = scenario == "standard",
                        icon = { Icon(Icons.Rounded.People, "С guardian'ами", tint = MDAOPurple, modifier = Modifier.size(20.dp)) },
                        title = "С guardian'ами",
                        subtitle = "SSS 3-of-4. Доверенные лица помогут восстановить кошелёк.",
                        onClick = { viewModel.setScenario("standard") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ScenarioCard(
                        selected = scenario == "hermit",
                        icon = { Icon(Icons.Rounded.PersonOff, "Без guardian'ов", tint = MDAOPurple, modifier = Modifier.size(20.dp)) },
                        title = "Без guardian'ов",
                        subtitle = "SSS 2-of-3. Только телефон + passkey + холодное устройство.",
                        onClick = { viewModel.setScenario("hermit") }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    MDAOButton(
                        text = "Выбрать ${state.selected}",
                        onClick = { viewModel.confirmNickname() }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Никнейм можно изменить позже в настройках",
                        style = MaterialTheme.typography.labelMedium,
                        color = DarkOnSurfaceMuted,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun ScenarioCard(
    selected: Boolean,
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MDAOPurple.copy(alpha = 0.12f)
            else DarkSurface
        ),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MDAOPurple.copy(alpha = 0.6f) else DarkBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(MDAOPurple.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) { icon() }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = DarkOnSurfaceMuted
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Выбрано",
                    tint = MDAOPurple,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}