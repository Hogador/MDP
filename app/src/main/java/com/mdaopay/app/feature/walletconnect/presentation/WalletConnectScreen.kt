package com.mdaopay.app.feature.walletconnect.presentation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mdaopay.app.core.ui.components.MDAOButton
import com.mdaopay.app.core.ui.theme.MarsFont
import com.mdaopay.app.core.ui.theme.extended

@Composable
fun WalletConnectScreen(
    onBack: () -> Unit,
    viewModel: WalletConnectViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsState()
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tc.bg)
            .padding(horizontal = 18.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
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
                text = "WalletConnect",
                fontFamily = MarsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = tc.text,
                letterSpacing = (-0.5).sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Подключитесь к dApp экосистемы MarsDAO через WalletConnect",
            fontFamily = MarsFont,
            fontSize = 13.sp,
            color = tc.text2
        )

        Spacer(modifier = Modifier.height(40.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "\u26D4", fontSize = 64.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "WalletConnect временно недоступен",
                fontFamily = MarsFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = tc.text2
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Подключите интернет для загрузки библиотек",
                fontFamily = MarsFont,
                fontSize = 12.sp,
                color = tc.text3,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            MDAOButton(text = "Повторить", onClick = { })
        }

        if (sessions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "ПОДКЛЮЧЕНИЯ",
                fontFamily = MarsFont,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = tc.text2,
                letterSpacing = 1.sp
            )
        }
    }
}
