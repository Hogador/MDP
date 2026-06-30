package com.mdaopay.app.feature.exchanger.presentation

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdaopay.app.core.ui.components.MDAOButton
import com.mdaopay.app.core.ui.components.MDAOWebView
import com.mdaopay.app.core.ui.theme.MarsFont
import com.mdaopay.app.core.ui.theme.extended

@Composable
fun OnrampScreen(
    onBack: () -> Unit,
    url: String = "https://t.me/mommys_trader_bot?start=buy_usdt",
    title: String = "Купить USDT"
) {
    val context = LocalContext.current
    val isTelegramUrl = url.startsWith("https://t.me/")
    var webViewFailed by remember { mutableStateOf(false) }
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Column(modifier = Modifier.fillMaxSize().background(tc.bg)) {
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
                text = title,
                fontFamily = MarsFont,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = tc.text
            )
        }

        if (isTelegramUrl || webViewFailed) {
            OnrampFallback(
                url = url,
                onOpenInTelegram = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                }
            )
        } else {
            MDAOWebView(
                url = url,
                modifier = Modifier.fillMaxSize(),
                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: android.webkit.WebResourceError?
                    ) {
                        webViewFailed = true
                    }
                }
            )
        }
    }
}

@Composable
private fun OnrampFallback(
    url: String,
    onOpenInTelegram: () -> Unit
) {
    val extended = MaterialTheme.extended
    val tc = extended.themeColors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Покупка и продажа USDT",
            fontFamily = MarsFont,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = tc.text,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Покупка и продажа USDT за рубли через СБП",
            fontFamily = MarsFont,
            fontSize = 13.sp,
            color = tc.text2
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(tc.card)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "\uD83D\uDCE8", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Обмен через Telegram",
                    fontFamily = MarsFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = tc.text
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Для покупки/продажи USDT вы будете перенаправлены в Telegram бота Mommy's Trader. Сделки проходят через СБП.",
                    fontFamily = MarsFont,
                    fontSize = 12.sp,
                    color = tc.text2
                )
                Spacer(modifier = Modifier.height(16.dp))
                MDAOButton(
                    text = "Открыть в Telegram",
                    onClick = onOpenInTelegram,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
