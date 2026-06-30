package com.mdaopay.app.feature.settings.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdaopay.app.core.ui.components.HapticManager
import com.mdaopay.app.core.ui.components.ListIconVariant
import com.mdaopay.app.core.ui.components.MDAOInputField
import com.mdaopay.app.core.ui.components.MDAOListItem
import com.mdaopay.app.core.ui.components.MDAOListSection
import com.mdaopay.app.core.ui.components.MDAOTopBar
import com.mdaopay.app.core.ui.theme.MDARadius
import com.mdaopay.app.core.ui.theme.MarsFont
import com.mdaopay.app.core.ui.theme.MarsMono
import com.mdaopay.app.core.ui.theme.*

private data class FaqItem(
    val question: String,
    val answer: String
)

private val faqItems = listOf(
    FaqItem("Как отправить криптовалюту?", "Откройте карту нужного токена на главном экране, нажмите \u00ABОтправить\u00BB. Введите адрес получателя (или отсканируйте QR), сумму и подтвердите. Транзакция отправится в течение нескольких секунд."),
    FaqItem("Что такое recovery phrase?", "Это 12 или 24 слова, которые восстанавливают полный доступ к кошельку. Никому не показывайте их. Храните в надёжном месте (желательно офлайн). Без фразы восстановить доступ невозможно."),
    FaqItem("Какая комиссия за отправку?", "Комиссия зависит от сети. Для BNB Chain обычно 0.1\u20130.5 USDT эквивалента. Для MDAO \u2014 0.5 MDAO. Точная сумма показывается перед подтверждением транзакции."),
    FaqItem("Можно ли отменить транзакцию?", "После подтверждения в блокчейне транзакция неотменяема. Внимательно проверяйте адрес и сумму перед отправкой. Pending-транзакции можно попытаться заменить (Replace-by-fee), но это работает не всегда."),
    FaqItem("Забыл PIN \u2014 что делать?", "Удалите приложение и переустановите его. При восстановлении введите recovery phrase \u2014 кошелёк вернётся, и вы сможете установить новый PIN. Без recovery phrase восстановление невозможно."),
    FaqItem("Как добавить кастомный токен?", "Настройки \u2192 Кошельки и токены \u2192 \u00ABДобавить кастомный токен\u00BB. Введите адрес контракта (на нужной сети). Токен подтянется автоматически, если он соответствует стандарту BEP-20 / ERC-20.")
)

@Composable
fun HelpScreen(onBack: () -> Unit) {
    val ext = MaterialTheme.extended
    val d = ext.themeColors
    var searchQuery by remember { mutableStateOf("") }
    var openFaqIndex by remember { mutableStateOf(-1) }

    Column(modifier = Modifier.fillMaxSize().background(d.bg)) {
        MDAOTopBar(title = "Помощь", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Search
            MDAOInputField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Поиск в FAQ",
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Quick actions
            MDAOListSection(header = "Быстрые действия") {
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                moveTo(size.width * 0.1f, size.height * 0.85f)
                                lineTo(size.width * 0.2f, size.height * 0.6f)
                                lineTo(size.width * 0.5f, size.height * 0.4f)
                                lineTo(size.width * 0.9f, size.height * 0.15f)
                            }
                            drawPath(p, color = Color.White, style = Stroke(width = 1.5f))
                        }
                    },
                    iconVariant = ListIconVariant.Accent,
                    title = "Написать в поддержку",
                    subtitle = "Ответ в течение 24 часов",
                    onClick = { HapticManager.light() }
                )
                MDAOListItem(
                    icon = {
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val p = Path().apply {
                                addRoundRect(androidx.compose.ui.geometry.RoundRect(
                                    androidx.compose.ui.geometry.Rect(size.width * 0.3f, size.height * 0.2f, size.width * 0.7f, size.height * 0.8f),
                                    4f, 4f
                                ))
                            }
                            drawPath(p, color = ext.danger, style = Stroke(width = 1.5f))
                            drawLine(color = ext.danger, start = androidx.compose.ui.geometry.Offset(size.width * 0.3f, size.height * 0.2f), end = androidx.compose.ui.geometry.Offset(size.width * 0.7f, size.height * 0.8f), strokeWidth = 1.5f)
                            drawLine(color = ext.danger, start = androidx.compose.ui.geometry.Offset(size.width * 0.7f, size.height * 0.2f), end = androidx.compose.ui.geometry.Offset(size.width * 0.3f, size.height * 0.8f), strokeWidth = 1.5f)
                        }
                    },
                    iconVariant = ListIconVariant.Danger,
                    title = "Сообщить о баге",
                    subtitle = "Скриншот + описание",
                    onClick = { HapticManager.light() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // FAQ
            MDAOListSection(header = "Частые вопросы") {
                faqItems.forEachIndexed { index, faq ->
                    val isOpen = openFaqIndex == index
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                openFaqIndex = if (isOpen) -1 else index
                                HapticManager.light()
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = faq.question,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = d.text,
                                fontFamily = MarsFont,
                                modifier = Modifier.weight(1f)
                            )
                            val rotation by animateFloatAsState(
                                targetValue = if (isOpen) 180f else 0f,
                                label = "chevronRot"
                            )
                            Canvas(modifier = Modifier.size(18.dp).rotate(rotation)) {
                                val p = Path().apply {
                                    moveTo(size.width * 0.25f, size.height * 0.3f)
                                    lineTo(size.width * 0.5f, size.height * 0.7f)
                                    lineTo(size.width * 0.75f, size.height * 0.3f)
                                }
                                drawPath(p, color = d.text2, style = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                        }
                        AnimatedVisibility(
                            visible = isOpen,
                            enter = slideInVertically { it / 2 },
                            exit = slideOutVertically { it / 2 }
                        ) {
                            Text(
                                text = faq.answer,
                                fontSize = 13.sp,
                                color = d.text2,
                                fontFamily = MarsFont,
                                lineHeight = 18.sp,
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Network Status
            MDAOListSection(header = "Статус сетей") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(ext.success)
                    )
                    Text(
                        text = "Все системы работают",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ext.success,
                        fontFamily = MarsFont
                    )
                }
                listOf(
                    "\u25C6" to "BNB Smart Chain" to "Высота блока: 38,452,108",
                    "\u039E" to "Ethereum" to "Высота блока: 19,872,341",
                    "\u25C6" to "Polygon" to "Задержка 12 сек",
                    "\u25C6" to "Arbitrum One" to "Высота блока: 187,234,108"
                ).forEachIndexed { index, pair ->
                    val (iconAndName, sub) = pair
                    val (icon, name) = iconAndName
                    val isSlow = name == "Polygon"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(MDARadius.sm))
                                .background(if (isSlow) ext.warning else ext.success),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = icon, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = d.text,
                                fontFamily = MarsFont
                            )
                            Text(
                                text = sub,
                                fontSize = 10.sp,
                                color = d.text2,
                                fontFamily = MarsFont
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (isSlow) ext.warning else ext.success)
                            )
                            Text(
                                text = if (isSlow) "Slow" else "OK",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSlow) ext.warning else ext.success,
                                fontFamily = MarsFont
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
