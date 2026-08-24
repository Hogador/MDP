package com.mdaopay.app.feature.settings.presentation

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdaopay.app.core.ui.components.HapticManager
import com.mdaopay.app.core.ui.components.MDAOTopBar
import com.mdaopay.app.core.ui.theme.MDARadius
import com.mdaopay.app.core.ui.theme.MarsFont
import com.mdaopay.app.core.ui.theme.*

private data class LangItem(val code: String, val name: String, val native: String, val flag: String)

private val languages = listOf(
    LangItem("ru", "Русский", "Russian", "\uD83C\uDDF7\uD83C\uDDFA"),
    LangItem("en", "English", "English", "\uD83C\uDDEC\uD83C\uDDE7"),
    LangItem("zh", "中文", "Chinese (Simplified)", "\uD83C\uDDE8\uD83C\uDDF3"),
    LangItem("es", "Español", "Spanish", "\uD83C\uDDEA\uD83C\uDDF8"),
    LangItem("fr", "Français", "French", "\uD83C\uDDEB\uD83C\uDDF7"),
    LangItem("de", "Deutsch", "German", "\uD83C\uDDE9\uD83C\uDDEA"),
    LangItem("ja", "日本語", "Japanese", "\uD83C\uDDEF\uD83C\uDDF5"),
    LangItem("ko", "한국어", "Korean", "\uD83C\uDDF0\uD83C\uDDF7")
)

@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val d = MaterialTheme.extended.themeColors
    var selectedIndex by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().background(d.bg)) {
        MDAOTopBar(title = "Язык", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MDARadius.xxl))
                    .background(d.card)
                    .drawBehind {
                        drawRoundRect(color = d.softBorder, cornerRadius = androidx.compose.ui.geometry.CornerRadius(MDARadius.xxl.toPx()), style = Stroke(width = 1.dp.toPx()))
                    }
                    .padding(20.dp, 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "ТЕКУЩИЙ ЯЗЫК",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = d.text2,
                        fontFamily = MarsFont,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = languages[selectedIndex].name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = d.text,
                        fontFamily = MarsFont,
                        letterSpacing = (-0.3).sp
                    )
                    Text(
                        text = languages[selectedIndex].native,
                        fontSize = 12.sp,
                        color = d.text2,
                        fontFamily = MarsFont
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MDARadius.xxl))
                    .background(d.card)
                    .drawBehind {
                        drawRoundRect(color = d.softBorder, cornerRadius = androidx.compose.ui.geometry.CornerRadius(MDARadius.xxl.toPx()), style = Stroke(width = 1.dp.toPx()))
                    }
            ) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
                        Text(
                            text = "ВЫБЕРИТЕ ЯЗЫК",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = d.text2,
                            fontFamily = MarsFont,
                            letterSpacing = 1.sp
                        )
                    }
                    languages.forEachIndexed { index, lang ->
                        val sel = selectedIndex == index
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedIndex = index
                                    HapticManager.light()
                                }
                                .background(if (sel || index % 2 == 0) Color.Transparent else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = lang.flag,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = lang.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = d.text,
                                    fontFamily = MarsFont
                                )
                                Text(
                                    text = lang.native,
                                    fontSize = 12.sp,
                                    color = d.text2,
                                    fontFamily = MarsFont
                                )
                            }
                            if (sel) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.extended.accent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.size(14.dp)) {
                                        val p = Path().apply {
                                            moveTo(size.width * 0.15f, size.height * 0.5f)
                                            lineTo(size.width * 0.4f, size.height * 0.78f)
                                            lineTo(size.width * 0.88f, size.height * 0.25f)
                                        }
                                        drawPath(p, color = Color.White, style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        text = "Язык интерфейса изменится немедленно. Названия токенов и сети останутся на языке оригинала.",
                        fontSize = 11.sp,
                        color = d.text2,
                        fontFamily = MarsFont,
                        lineHeight = 16.sp,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
