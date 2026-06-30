package com.mdaopay.app.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.mdaopay.app.core.ui.theme.MDAOPayTheme
import com.mdaopay.app.feature.home.domain.model.WalletState
import com.mdaopay.app.feature.home.presentation.HomeScreen
import com.mdaopay.app.feature.home.presentation.HomeUiState
import com.mdaopay.app.feature.home.presentation.HomeViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun fakeReadyState(
        displayName: String = "Test User",
        balanceMdao: BigDecimal = BigDecimal("100.50"),
        balanceUsdt: BigDecimal = BigDecimal("250.00")
    ) = HomeUiState.Ready(
        wallet = WalletState(
            nickname = "testuser",
            address = "0x1234",
            balanceMdao = balanceMdao,
            balanceUsdt = balanceUsdt,
            balanceEth = BigDecimal.ZERO,
            isOnline = true
        ),
        recentTransactions = emptyList(),
        displayName = displayName
    )

    private fun setupScreen(state: HomeUiState) {
        val viewModel = mockk<HomeViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(state)
        composeTestRule.setContent {
            MDAOPayTheme {
                HomeScreen(
                    onSendClick = {},
                    onReceiveClick = {},
                    onHistoryClick = {},
                    onSettingsClick = {},
                    viewModel = viewModel
                )
            }
        }
    }

    @Test
    fun test_HomeScreen_Loads() {
        setupScreen(fakeReadyState())
        composeTestRule.onNodeWithText("Привет,").assertIsDisplayed()
    }

    @Test
    fun test_HomeScreen_ShowsBalance() {
        setupScreen(fakeReadyState())
        composeTestRule.onNodeWithText("Balance").assertIsDisplayed()
    }

    @Test
    fun test_HomeScreen_ShowsActions() {
        setupScreen(fakeReadyState())
        composeTestRule.onNodeWithText("Отправить MDAO").assertIsDisplayed()
        composeTestRule.onNodeWithText("Получить MDAO").assertIsDisplayed()
    }
}
