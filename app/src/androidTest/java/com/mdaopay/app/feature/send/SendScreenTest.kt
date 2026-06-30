package com.mdaopay.app.feature.send

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.mdaopay.app.core.security.BiometricAuthManager
import com.mdaopay.app.core.ui.theme.MDAOPayTheme
import com.mdaopay.app.feature.send.presentation.SendScreen
import com.mdaopay.app.feature.send.presentation.SendState
import com.mdaopay.app.feature.send.presentation.SendViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class SendScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setupScreen(state: SendState = SendState.Idle) {
        val viewModel = mockk<SendViewModel>(relaxed = true)
        every { viewModel.state } returns MutableStateFlow(state)
        val biometricManager = mockk<BiometricAuthManager>(relaxed = true)
        composeTestRule.setContent {
            MDAOPayTheme {
                SendScreen(
                    onBack = {},
                    onSuccess = {},
                    biometricManager = biometricManager,
                    viewModel = viewModel
                )
            }
        }
    }

    @Test
    fun test_SendScreen_Loads() {
        setupScreen()
        composeTestRule.onNodeWithText("Кому отправляем?").assertIsDisplayed()
    }

    @Test
    fun test_SendScreen_ShowsRecipientInput() {
        setupScreen()
        composeTestRule.onNodeWithText("Введите имя пользователя или адрес кошелька").assertIsDisplayed()
    }
}
